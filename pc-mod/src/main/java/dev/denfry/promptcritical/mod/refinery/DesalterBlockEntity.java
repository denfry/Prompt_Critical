package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridLoad;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.production.ProductTank;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.uranium.UraniumProcessBlockEntity;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.refining.Desalting;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Электрообессоливающая установка ЭЛОУ ({@link Desalting}, 09 §9.7.3): две ступени электродегидраторов перед
 * атмосферной трубчаткой. Нефть и промывную воду ({@code raw_water}) подают трубами к боковым граням и верху; солёная
 * вода уходит снизу. Обессоленная нефть идёт прямо в бак ближайшей трубчатой печи в {@link #FURNACE_RADIUS} блоках —
 * печь знает её солёность (нефть, налитая в печь трубой мимо ЭЛОУ, несёт {@link TubeFurnaceBlockEntity#RAW_SALT_MG_L}
 * мг/л), а колонна разъедает конденсатор верха пропорционально соли.
 *
 * <p>Поле электродов питает сеть 07 ({@value #POWER_W} Вт, ввод 6,3 кВ к любой грани): без напряжения капли не
 * сливаются и соль почти не уходит. Перепад смесительного клапана — кнопками щита. Нефть приходит подогретой
 * теплообменниками продуктов до {@value #CRUDE_C} °C [допущение: теплообмен не моделируется].
 */
public final class DesalterBlockEntity extends UraniumProcessBlockEntity implements GridDevice {
    /** Электрическое поле и насосы — от своего ввода 6,3 кВ, щит 0,4 кВ не нужен. */
    @Override
    protected double ratedPowerW() {
        return 0;
    }

    public static final int MB = 8_000, FURNACE_RADIUS = 6;
    /** Производительность — 3 л/с (10,8 м³/ч), чуть больше атмосферной колонны 10 м³/ч [BALANCE]. */
    public static final double FEED_L_S = 3, POWER_W = 100e3, CRUDE_C = 120;
    /** Пластовая вода в нефти и доля промывной воды на ступень — значения примера 09 §9.7.3. */
    public static final double WATER_IN = 0.005, WASH_RATIO = 0.06, DP_STEP_MPA = 0.01;

    private final FeedTank crude = new FeedTank(PcFluids.CRUDE_OIL.source.get(), MB);
    private final FeedTank water = new FeedTank(PcFluids.RAW_WATER.source.get(), MB);
    private final IFluidHandler inlet = new PyrolysisFurnaceBlockEntity.MultiInlet(crude, water);
    private final ProductTank brine = new ProductTank(PcFluids.PRODUCED_WATER.source.get(), MB);
    private double dpMpa = Desalting.DP_REF_MPA, feedLs;
    private boolean powered, benchPower, furnace;
    private Desalting.Result last = Desalting.twoStage(TubeFurnaceBlockEntity.RAW_SALT_MG_L, WATER_IN, WASH_RATIO,
            Desalting.DP_REF_MPA, 0, CRUDE_C + 273.15);

    public DesalterBlockEntity(BlockPos pos, BlockState state) {
        super(RefineryBlockEntities.DESALTER.get(), pos, state, 0, 0, s -> false);
    }

    public FeedTank crude() {
        return crude;
    }

    public FeedTank water() {
        return water;
    }

    public ProductTank brine() {
        return brine;
    }

    public Desalting.Result last() {
        return last;
    }

    public double dpMpa() {
        return dpMpa;
    }

    public void setDpMpa(double dp) {
        dpMpa = Math.clamp(Math.round(dp * 100) / 100.0, Desalting.DP_MIN_MPA, Desalting.DP_MAX_MPA);
        setChanged();
    }

    /** Стенд (GameTest): поле под напряжением без сети. */
    public void setBenchPower(boolean on) {
        benchPower = on;
    }

    public boolean powered() {
        return powered;
    }

    @Nullable
    private TubeFurnaceBlockEntity furnace(ServerLevel level) {
        List<TubeFurnaceBlockEntity> all = Ironworks.blockEntities(level, new AABB(worldPosition).inflate(FURNACE_RADIUS),
                TubeFurnaceBlockEntity.class);
        TubeFurnaceBlockEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (TubeFurnaceBlockEntity f : all) {
            double d = f.getBlockPos().distSqr(worldPosition);
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    @Override
    protected void process(ServerLevel level, double dt) {
        feedLs = 0;
        powered = benchPower || GridLoad.draw(level, simId(), on ? POWER_W : 0);
        TubeFurnaceBlockEntity f = furnace(level);
        furnace = f != null;
        last = Desalting.twoStage(TubeFurnaceBlockEntity.RAW_SALT_MG_L, WATER_IN, WASH_RATIO, dpMpa,
                powered ? Desalting.FIELD_KV_CM : 0, CRUDE_C + 273.15);
        if (!on || f == null) return;
        double l = Math.min(FEED_L_S * dt, Math.min(crude.amount(), f.crude().room()));
        l = Math.min(l, water.amount() / last.washWater());
        l = Math.min(l, brine.room() / Math.max(1e-9, last.brine()));
        if (l <= 0) return;
        crude.take(l);
        water.take(l * last.washWater());
        brine.add(l * last.brine());
        f.acceptDesalted(l, last.saltOut());
        feedLs = l / dt;
    }

    @Override
    protected boolean running() {
        return feedLs > 0;
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return side == Direction.DOWN ? brine : inlet;
    }

    @Override
    public PanelLayout panelLayout() {
        return ConversionPanels.DESALTER;
    }

    @Override
    public void readPanel(int[] v) {
        Arrays.fill(v, 0);
        v[ConversionPanels.DS_SALT_X100] = (int) Math.round(Math.min(9999, last.saltOut() * 100));
        v[ConversionPanels.DS_WATER_X100] = (int) Math.round(last.waterOut() * 1e4);
        v[ConversionPanels.DS_DP_KPA] = (int) Math.round(dpMpa * 1000);
        v[ConversionPanels.DS_FEED_LH] = (int) Math.round(feedLs * 3600);
        v[ConversionPanels.DS_TANK] = (int) Math.round(brine.fill() * 1000);
        v[ConversionPanels.DS_STAGE1_X10] = (int) Math.round(Math.min(9999, last.stage1Salt() * 10));
        v[ConversionPanels.DS_FLAGS] = (on ? 1 << ConversionPanels.F_ON : 0) | (running() ? 1 << ConversionPanels.F_RUN : 0)
                | (powered ? 1 << ConversionPanels.F_AUX : 0)
                | (last.saltOut() > Desalting.REFINERY_SALT_MAX_MG_L ? 1 << ConversionPanels.F_WARN : 0)
                | (!furnace ? 1 << ConversionPanels.F_ALARM : 0);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case ConversionPanels.B_RUN -> use(player);
            case ConversionPanels.B_DOWN -> setDpMpa(dpMpa - DP_STEP_MPA);
            case ConversionPanels.B_UP -> setDpMpa(dpMpa + DP_STEP_MPA);
            default -> {
            }
        }
        setChanged();
    }

    @Override
    protected void saveExtra(CompoundTag tag, HolderLookup.Provider registries) {
        crude.save(tag, "crude");
        water.save(tag, "water");
        brine.save(tag, "brine");
        tag.putDouble("dp", dpMpa);
    }

    @Override
    protected void loadExtra(CompoundTag tag, HolderLookup.Provider registries) {
        crude.load(tag, "crude");
        water.load(tag, "water");
        brine.load(tag, "brine");
        if (tag.contains("dp")) dpMpa = tag.getDouble("dp");
    }

    // --- сетевой ввод (07): потребитель 6,3 кВ, кабель к любой грани ---

    @Override
    public List<List<Face>> terminals() {
        return List.of(GridDevice.allFaces(worldPosition));
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return simId().isNone() ? null : grid.consumer(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        GridLoad.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        GridLoad.unregister(level, worldPosition);
    }
}
