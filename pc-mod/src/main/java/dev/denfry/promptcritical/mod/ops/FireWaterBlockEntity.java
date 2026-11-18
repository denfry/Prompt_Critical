package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridLoad;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Agent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Противопожарный водопровод (13 §13.5.3): кольцевая сеть в радиусе {@value #MAIN_RADIUS} блоков вокруг пожарного
 * насоса. Вид — по блоку:
 * <ul>
 * <li>{@code fire_water_pump} — электронасос 6,3 кВ {@value #PUMP_W} Вт с забором из воды рядом (источник воды на
 * соседней клетке); пускается сам, когда кто-то в сети берёт воду (автопуск по падению давления), и стоит через
 * {@value #RUN_ON_S} с после последнего разбора; подача до {@value #PUMP_LPM} л/мин на всю сеть;</li>
 * <li>{@code fire_hydrant} — ПКМ открывает рукав со стволом: {@value #HYDRANT_LPM} л/мин на очаги в радиусе
 * {@value #HYDRANT_RADIUS}; с дозатором пенообразователя рядом — пена, иначе вода;</li>
 * <li>{@code foam_proportioner} — бак пенообразователя (по трубе Create), дозирует 3 % или 6 % (ПКМ) в раствор
 * гидрантов, пенокамер и лафетных стволов в {@value #PROPORTIONER_RADIUS} блоках;</li>
 * <li>{@code foam_chamber} — пенокамера на резервуаре: от ППК зоны или ПКМ, {@value #CHAMBER_LPM} л/мин раствора на
 * зеркало в радиусе {@value #CHAMBER_RADIUS}.</li>
 * </ul>
 * Пена тушит пролив и резервуар (класс B) только при интенсивности не ниже 4,1 л/(мин·м²) — за время
 * {@code 300 с · 4,1 / r}; вода без пены класс B не тушит и разбрызгивает горящую жидкость (§13.5.4). Мощность насоса
 * [BALANCE]: Q·p/η = 4000 л/мин · 0,8 МПа / 0,7 ≈ 76 кВт.
 */
public class FireWaterBlockEntity extends DeviceBlockEntity implements FoundryDevice, GridDevice {
    public enum Kind { PUMP, HYDRANT, PROPORTIONER, CHAMBER }

    public static final int MAIN_RADIUS = 24, HYDRANT_RADIUS = 6, CHAMBER_RADIUS = 4, PROPORTIONER_RADIUS = 4, PANEL_RADIUS = 16;
    public static final double PUMP_LPM = 4000, HYDRANT_LPM = 1000, CHAMBER_LPM = 400, PUMP_W = 76e3, RUN_ON_S = 10;

    private final FeedTank concentrate = new FeedTank(PcFluids.FOAM_CONCENTRATE.source.get(), 4000);
    private boolean open, ratio6, benchPower;
    /** Насос: до какой секунды держать пуск; сколько литров уже отдано в эту секунду. */
    private long demandUntilS = -1, budgetS = -1;
    private double givenL;
    private int ticks;

    public FireWaterBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.FIRE_WATER.get(), pos, state);
    }

    public Kind kind() {
        BlockState s = getBlockState();
        if (s.is(PcBlocks.FIRE_WATER_PUMP.get())) return Kind.PUMP;
        if (s.is(PcBlocks.FOAM_PROPORTIONER.get())) return Kind.PROPORTIONER;
        if (s.is(PcBlocks.FOAM_CHAMBER.get())) return Kind.CHAMBER;
        return Kind.HYDRANT;
    }

    public FeedTank concentrate() {
        return concentrate;
    }

    public boolean open() {
        return open;
    }

    public void setOpen(boolean on) {
        open = on;
        setChanged();
    }

    /** Стенд (GameTest): питание насоса без сети. */
    public void setBenchPower(boolean on) {
        benchPower = on;
    }

    public boolean running() {
        BlockState s = getBlockState();
        return s.hasProperty(PcProperties.RUNNING) && s.getValue(PcProperties.RUNNING);
    }

    private long nowS() {
        return level == null ? 0 : level.getGameTime() / 20;
    }

    // ------------------------------------------------------------------ сеть

    /** Забор: вода на соседней клетке. */
    boolean intake() {
        if (level == null) return false;
        for (Direction d : Direction.values()) {
            FluidState f = level.getFluidState(worldPosition.relative(d));
            if (f.is(Fluids.WATER) || f.is(Fluids.FLOWING_WATER)) return true;
        }
        return false;
    }

    /** Отдать из сети до {@code litres} за эту секунду; насос запоминает разбор для автопуска. */
    private double give(double litres) {
        demandUntilS = nowS() + (long) RUN_ON_S;
        if (!running()) return 0;
        if (budgetS != nowS()) {
            budgetS = nowS();
            givenL = 0;
        }
        double t = Math.max(0, Math.min(litres, PUMP_LPM / 60 - givenL));
        givenL += t;
        return t;
    }

    /** Взять из пожарной сети у {@code pos} до {@code litres} за секунду; 0 — сеть без давления. */
    public static double draw(Level level, BlockPos pos, double litres) {
        double got = 0;
        for (FireWaterBlockEntity p : Ironworks.blockEntities(level, new AABB(pos).inflate(MAIN_RADIUS), FireWaterBlockEntity.class)) {
            if (p.kind() != Kind.PUMP || got >= litres) continue;
            got += p.give(litres - got);
        }
        return got;
    }

    /** Дозатор пенообразователя рядом с {@code pos} отдал концентрат на {@code litres} раствора. */
    public static boolean foam(Level level, BlockPos pos, double litres) {
        for (FireWaterBlockEntity p : Ironworks.blockEntities(level, new AABB(pos).inflate(PROPORTIONER_RADIUS), FireWaterBlockEntity.class)) {
            if (p.kind() != Kind.PROPORTIONER) continue;
            double need = litres * (p.ratio6 ? 0.06 : 0.03);
            if (p.concentrate.amount() >= need) {
                p.concentrate.take(need);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ тик

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel sl) || ++ticks % 20 != 0) return;
        switch (kind()) {
            case PUMP -> {
                boolean want = nowS() <= demandUntilS && intake();
                boolean on = want && (benchPower || GridLoad.draw(level, simId(), PUMP_W));
                if (!want && !benchPower) GridLoad.draw(level, simId(), 0);
                setFlag(on);
            }
            case HYDRANT -> {
                if (open) discharge(sl, worldPosition, HYDRANT_RADIUS, HYDRANT_LPM);
            }
            case CHAMBER -> {
                boolean alarm = false;
                for (FireDeviceBlockEntity d : Ironworks.blockEntities(sl, new AABB(worldPosition).inflate(PANEL_RADIUS), FireDeviceBlockEntity.class))
                    alarm |= d.kind() == FireDeviceBlockEntity.Kind.ALARM_PANEL && d.fire();
                if (open || alarm) discharge(sl, worldPosition, CHAMBER_RADIUS, CHAMBER_LPM);
            }
            case PROPORTIONER -> setFlag(ratio6);
        }
        if (ticks % 200 == 0) setChanged();
    }

    private void setFlag(boolean v) {
        BlockState s = getBlockState();
        var p = kind() == Kind.PROPORTIONER ? BlockStateProperties.POWERED : PcProperties.RUNNING;
        if (s.hasProperty(p) && s.getValue(p) != v) level.setBlock(worldPosition, s.setValue(p, v), Block.UPDATE_CLIENTS);
    }

    /** Ствол или пенокамера: раствор за секунду из сети, с дозатором — пена. */
    private void discharge(ServerLevel sl, BlockPos center, int r, double lpm) {
        double litres = draw(sl, worldPosition, lpm / 60);
        if (litres < lpm / 60 * 0.99) return; // сеть без давления
        boolean foam = foam(sl, worldPosition, litres);
        sl.sendParticles(foam ? ParticleTypes.CLOUD : ParticleTypes.SPLASH, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5,
                20, r / 2.0, 0.5, r / 2.0, 0.02);
        if (foam) FireService.foam(sl, center, r, lpm);
        else FireService.apply(sl, center, r, Agent.WATER, null);
    }

    // ------------------------------------------------------------------ руки

    @Override
    public InteractionResult use(Player player) {
        switch (kind()) {
            case HYDRANT, CHAMBER -> {
                open = !open;
                player.displayClientMessage(OpsFireText.msg(open ? "hydrant_open" : "hydrant_closed"), true);
            }
            case PROPORTIONER -> {
                ratio6 = !ratio6;
                player.displayClientMessage(OpsFireText.msg("proportioner", ratio6 ? 6 : 3,
                        (int) concentrate.amount()), true);
            }
            case PUMP -> player.displayClientMessage(OpsFireText.msg(running() ? "pump_running" : intake() ? "pump_standby" : "pump_dry"), true);
        }
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return kind() == Kind.PROPORTIONER ? concentrate : null;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    // ------------------------------------------------------------------ сетевой ввод насоса (07)

    @Override
    public List<List<GridDevice.Face>> terminals() {
        return kind() == Kind.PUMP ? List.of(GridDevice.allFaces(worldPosition)) : List.of();
    }

    @Override
    @Nullable
    public GridUnit gridUnit(GridSubsystem grid) {
        return kind() != Kind.PUMP || simId().isNone() ? null : grid.consumer(simId());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (kind() == Kind.PUMP) GridLoad.register(level, worldPosition);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        GridLoad.unregister(level, worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("open", open);
        tag.putBoolean("ratio6", ratio6);
        concentrate.save(tag, "concentrate");
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        open = tag.getBoolean("open");
        ratio6 = tag.getBoolean("ratio6");
        concentrate.load(tag, "concentrate");
    }
}
