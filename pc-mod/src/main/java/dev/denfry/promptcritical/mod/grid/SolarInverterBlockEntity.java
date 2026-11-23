package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.InverterUnit;
import dev.denfry.promptcritical.sim.solar.PvArray;
import dev.denfry.promptcritical.sim.solar.SolarSky;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Солнечный инвертор {@code solar_inverter} (15 §15.5.4): сетевой инвертор 100 кВт на шине 0,4 кВ, ведомый сетью
 * ({@link dev.denfry.promptcritical.sim.grid.InverterSource}). Строка — панели {@code solar_panel} в
 * {@value #RANGE} блоках вокруг. Панель без открытого неба над собой затенена; при снегопаде панели под снегом, пока
 * воздух не теплее 2 °C. Пыль в пустыне и бэдлендс копится по 0,2 %/сут, в остальных биомах по 0,05 %, дождь её смывает.
 * MPPT идеален с запаздыванием 1 с. Кабель — к любой грани, кроме фасада. Shift + ПКМ — состояние.
 *
 * <p>Панели {@code solar_panel_tracker} следят за солнцем восток–запад, если в том же радиусе крутится
 * {@code solar_tracker_drive} (до {@value SolarTrackerDriveBlockEntity#PANELS_PER_DRIVE} панелей на привод); без
 * привода они стоят горизонтально. В облачную погоду слежение ошибается на {@value #CLOUDY_TRACKING_LOSS}.
 */
public class SolarInverterBlockEntity extends GridDeviceBlockEntity<InverterUnit> {
    public static final double RATED_W = 100e3, EFFICIENCY = 0.96;
    /** Радиус строки, блоков по каждой оси. */
    public static final int RANGE = 8;
    static final int RESCAN_TICKS = 200, SKY_TICKS = 20;
    /** Запаздывание MPPT, с. */
    static final double MPPT_S = 1;

    private final SolarSky sky = new SolarSky();
    private int panels, shaded, trackerPanels, trackerShaded, drives;
    /** Потеря слежения при облаках (15 §15.5.4). */
    public static final double CLOUDY_TRACKING_LOSS = 0.02;
    private boolean snowCovered;
    private double soiling;
    private double dcW, acAvailW;

    public SolarInverterBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.SOLAR_INVERTER.get(), pos, state);
    }

    @Override
    protected InverterUnit unitOf(GridSubsystem grid) {
        InverterUnit u = grid.inverter(simId());
        u.configure(RATED_W, EFFICIENCY);
        return u;
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (Direction d : Direction.values()) if (d != facing()) out.add(new Face(worldPosition, d));
        return List.of(out);
    }

    public int panels() {
        return panels;
    }

    /** Панели, которые сейчас следят за солнцем. */
    public int trackingPanels() {
        return Math.min(trackerPanels, drives * SolarTrackerDriveBlockEntity.PANELS_PER_DRIVE);
    }

    /** Мощность панелей на стороне постоянного тока, Вт. */
    public double dcW() {
        return dcW;
    }

    private void scan(ServerLevel sl) {
        int n = 0, dark = 0, tn = 0, tdark = 0, d = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RANGE; dx <= RANGE; dx++)
            for (int dy = -RANGE; dy <= RANGE; dy++)
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    p.set(worldPosition.getX() + dx, worldPosition.getY() + dy, worldPosition.getZ() + dz);
                    if (!sl.isLoaded(p)) continue;
                    BlockState st = sl.getBlockState(p);
                    if (st.is(PcBlocks.SOLAR_TRACKER_DRIVE.get())) {
                        if (sl.getBlockEntity(p) instanceof SolarTrackerDriveBlockEntity drive && drive.tracking()) d++;
                        continue;
                    }
                    boolean tracker = st.is(PcBlocks.SOLAR_PANEL_TRACKER.get());
                    if (!tracker && !st.is(PcBlocks.SOLAR_PANEL.get())) continue;
                    boolean dk = !sl.canSeeSky(p.above());
                    n++;
                    if (dk) dark++;
                    if (tracker) {
                        tn++;
                        if (dk) tdark++;
                    }
                }
        panels = n;
        shaded = dark;
        trackerPanels = tn;
        trackerShaded = tdark;
        drives = d;
    }

    private static double clearMean(Holder<Biome> b) {
        if (b.is(Biomes.DESERT) || b.is(BiomeTags.IS_BADLANDS)) return 0.9;
        if (b.is(BiomeTags.IS_JUNGLE)) return 0.4;
        return 0.6;
    }

    @Override
    protected void serverTick(InverterUnit u) {
        if (!(level instanceof ServerLevel sl)) return;
        u.configure(RATED_W, EFFICIENCY);
        if (ticks % RESCAN_TICKS == 1 || panels == 0 && ticks % 40 == 1) scan(sl);
        if (ticks % SKY_TICKS == 0) {
            double dt = SKY_TICKS * 0.05;
            Holder<Biome> biome = sl.getBiome(worldPosition);
            sky.step(dt * 72, clearMean(biome), sl.random.nextGaussian()); // сутки MC — 20 мин, облака живут в часах дня
            double ambientC = EnvironmentService.sample(sl, worldPosition, 2).airTempC();
            boolean snowing = sl.isRaining() && biome.value().coldEnoughToSnow(worldPosition);
            if (snowing) snowCovered = true;
            else if (ambientC > 2) snowCovered = false;
            double perDay = biome.is(Biomes.DESERT) || biome.is(BiomeTags.IS_BADLANDS) ? 0.002 : 0.0005;
            soiling = sl.isRaining() && !snowing ? 0 : Math.min(0.5, soiling + perDay * dt / 1200);
            SolarSky.Weather w = sl.isThundering() ? SolarSky.Weather.THUNDER
                    : snowing ? SolarSky.Weather.SNOW : sl.isRaining() ? SolarSky.Weather.RAIN : SolarSky.Weather.CLEAR;
            SolarSky.Sample sun = sl.dimensionType().hasSkyLight() ? sky.sample(sl.getTimeOfDay(1f), w) : SolarSky.Sample.NIGHT;
            int tracked = trackingPanels();
            int trackedShaded = trackerPanels == 0 ? 0 : (int) Math.round((double) trackerShaded * tracked / trackerPanels);
            double trackW = PvArray.arrayW(PvArray.Spec.of(tracked, true), sun, 0, ambientC, soiling, trackedShaded, snowCovered);
            if (w != SolarSky.Weather.CLEAR || sky.clear() < 0.5) trackW *= 1 - CLOUDY_TRACKING_LOSS;
            dcW = trackW + PvArray.arrayW(PvArray.Spec.of(panels - tracked, false), sun, 0, ambientC, soiling, shaded - trackedShaded, snowCovered);
            setChanged();
        }
        acAvailW += (Math.min(RATED_W, dcW * EFFICIENCY) - acAvailW) * Math.min(1, 0.05 / MPPT_S);
        u.setDispatch(acAvailW, acAvailW, 0);
        if (ticks % 20 == 0) setFlag(BlockStateProperties.LIT, u.powerW() > 100);
    }

    @Override
    public InteractionResult use(Player player) {
        InverterUnit u = unit();
        if (u == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        player.displayClientMessage(GridText.msg(GridText.SOLAR_STATUS, panels, shaded, trackingPanels(), String.format("%.1f", dcW / 1e3),
                String.format("%.1f", u.powerW() / 1e3), Math.round(sky.clear() * 100), Math.round(soiling * 100),
                GridText.msg(u.source() != null && u.source().connected() ? GridText.INVERTER_ON : GridText.INVERTER_OFF)), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("soiling", soiling);
        tag.putBoolean("snow", snowCovered);
        tag.putDouble("clear", sky.clear());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        soiling = Math.max(0, Math.min(0.5, tag.getDouble("soiling")));
        snowCovered = tag.getBoolean("snow");
        if (tag.contains("clear")) sky.setClear(tag.getDouble("clear"));
    }
}
