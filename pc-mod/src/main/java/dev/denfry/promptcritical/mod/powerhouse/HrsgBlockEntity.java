package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.combustion.HeatRecoverySteamGenerator;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.WaterTubeUnit;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.Arrays;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Котёл-утилизатор ПГУ 3×6×3 ({@link HeatRecoverySteamGenerator}, одно давление, без дожига): нижний куб — газоход,
 * который должен касаться турбинной секции ГТУ; верхний — барабан. Пар он отдаёт в котёл, которого касается
 * (жаротрубный или барабан, экран, щит водотрубного), так же, как парогенератор АЭС и электродный котёл
 * ({@link SteamPlant#setElectricHeatW}): давление и температуру насыщения задаёт этот котёл, его пар идёт в
 * существующие паровые машины и турбины. Насыщенный пар — пароперегреватель котла-утилизатора отключён, перегрев
 * даёт сам котёл. Тепло — по пинч-точке: газ на выходе испарителя на 10 К горячее кипящей воды, питательная вода
 * 104 °C (после деаэратора). Холодный выхлоп (холостой ход ГТУ) пара не даёт.
 */
public final class HrsgBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final double FEED_K = 377.15;
    private static final WaterProperties WATER = WaterProperties.tables();

    @Nullable
    private BlockPos turbine;
    private SimId boiler = SimId.NONE;
    private HeatRecoverySteamGenerator.Result last = HeatRecoverySteamGenerator.Result.none(0);
    private double gasKgS, gasInK, gtShaftW, gtFuelW, drumPa;
    private boolean sink;
    private int ticks;

    public HrsgBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.HRSG.get(), pos, state);
    }

    public boolean linkedTurbine() {
        return turbine != null;
    }

    public boolean linkedBoiler() {
        return !boiler.isNone();
    }

    /** Тепло, переданное в котёл, Вт (0 — пара нет или котёл его не принимает). */
    public double deliveredW() {
        return sink ? last.dutyW() : 0;
    }

    public HeatRecoverySteamGenerator.Result result() {
        return last;
    }

    public double gasKgS() {
        return gasKgS;
    }

    public double gasInK() {
        return gasInK;
    }

    @Nullable
    private SteamPlant plant() {
        SimLevelBinding b = level instanceof ServerLevel s ? SimBindings.get(s) : null;
        return b == null ? null : b.world().subsystem(SteamPlant.class);
    }

    /** Работающий котёл (арт art/blockbench/boilers/hrsg.author.js): над трубой дрожит горячий выхлоп ГТУ (у верхнего сегмента якорь на три блока выше). */
    private void clientFx() {
        MachineFx fx = MachineFx.of(level, worldPosition, getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (fx == null || !getBlockState().getValue(PcProperties.RUNNING)) return;
        if (fx.chance(3)) fx.wisp(fx.at(8, 48 + 32, -6), 0.6);
    }

    @Override
    public void tickDevice() {
        if (level != null && level.isClientSide) {
            clientFx();
            return;
        }
        if (!(level instanceof ServerLevel sl)) return;
        if (ticks++ % 20 == 0) {
            turbine = findTurbine();
            boiler = findBoiler();
        }
        GasTurbineBlockEntity gt = turbine != null && level.getBlockEntity(turbine) instanceof GasTurbineBlockEntity g ? g : null;
        SteamPlant p = plant();
        gasKgS = gt != null ? gt.exhaustKgS() : 0;
        gasInK = gt != null ? gt.exhaustK() : 0;
        gtShaftW = gt != null ? gt.turbine().shaftPowerW() : 0;
        gtFuelW = gt != null ? gt.fuelHeatW() : 0;
        drumPa = drumPressure(p);
        last = gasKgS > 0 && Double.isFinite(drumPa) && drumPa > 0
                ? HeatRecoverySteamGenerator.solve(WATER, gasKgS, gasInK, drumPa, FEED_K, false)
                : HeatRecoverySteamGenerator.Result.none(gasInK);
        sink = p != null && !boiler.isNone() && p.setElectricHeatW(boiler, last.dutyW());
        if (ticks % 10 == 0) {
            setFlag(deliveredW() > 1e4);
            if (deliveredW() > 1e5)
                sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 5.2, worldPosition.getZ() + 0.5,
                        1, 0.3, 0.05, 0.3, 0.02);
        }
    }

    private void setFlag(boolean running) {
        BlockState st = getBlockState();
        if (st.hasProperty(PcProperties.RUNNING) && st.getValue(PcProperties.RUNNING) != running)
            level.setBlock(worldPosition, st.setValue(PcProperties.RUNNING, running), Block.UPDATE_CLIENTS);
    }

    /** Давление в барабане котла, Па; {@code NaN} — котла нет. */
    private double drumPressure(@Nullable SteamPlant p) {
        if (p == null || boiler.isNone()) return Double.NaN;
        BoilerUnit b = p.boiler(boiler);
        if (b != null) return b.pressurePa();
        WaterTubeUnit w = p.waterTube(boiler);
        return w != null ? w.drumPressurePa() : Double.NaN;
    }

    /** Клетки снаружи нижнего куба (газохода): соседи по граням. */
    private Iterable<BlockPos> ductNeighbours() {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (int x = -2; x <= 2; x++)
            for (int y = -1; y <= 1; y++)
                for (int z = -2; z <= 2; z++) {
                    int edge = (Math.abs(x) == 2 ? 1 : 0) + (Math.abs(z) == 2 ? 1 : 0);
                    if (edge == 1) out.add(worldPosition.offset(x, y, z));
                }
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) out.add(worldPosition.offset(x, -2, z));
        return out;
    }

    /** Контроллер ГТУ, чья турбинная секция касается газохода; {@code null} — нет. */
    @Nullable
    private BlockPos findTurbine() {
        for (BlockPos n : ductNeighbours()) {
            BlockState s = level.getBlockState(n);
            BlockPos anchor = s.getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.anchor(s, n) : n;
            BlockState a = level.getBlockState(anchor);
            if (!a.is(PcBlocks.GAS_TURBINE.get()) || a.getValue(PcProperties.GAS_TURBINE_SEGMENT) != PcProperties.GasTurbineSegment.TURBINE)
                continue;
            GasTurbineBlockEntity g = GasTurbineBlockEntity.at(level, n);
            if (g != null) return g.getBlockPos();
        }
        return null;
    }

    /** Котёл, которого касается котёл-утилизатор (любая клетка обоих кубов). */
    private SimId findBoiler() {
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 5; y++)
                for (int z = -2; z <= 2; z++) {
                    int out = (Math.abs(x) == 2 ? 1 : 0) + (Math.abs(z) == 2 ? 1 : 0) + (y == -2 || y == 5 ? 1 : 0);
                    if (out != 1) continue;
                    BlockPos n = worldPosition.offset(x, y, z);
                    FireTubeBoilerBlockEntity fire = FireTubeBoilerBlockEntity.at(level, n);
                    if (fire != null && !fire.simId().isNone()) return fire.simId();
                    BlockState s = level.getBlockState(n);
                    if (s.is(PcBlocks.STEAM_DRUM.get()) || s.is(PcBlocks.BOILER_WALL.get()) || s.is(PcBlocks.WATER_TUBE_BOILER.get())) {
                        WaterTubeBoilerBlockEntity wt = WaterTubeBoilerBlockEntity.of(level, n);
                        if (wt != null && wt.shape() != null && !wt.simId().isNone()) return wt.simId();
                    }
                }
        return SimId.NONE;
    }

    // --- руки ---

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

    /** Shift + ПКМ: сводка в строке состояния. */
    @Override
    public InteractionResult use(Player player) {
        if (turbine == null) {
            player.displayClientMessage(PowerhouseText.msg("hrsg.no_turbine"), true);
        } else if (boiler.isNone()) {
            player.displayClientMessage(PowerhouseText.msg("hrsg.no_boiler"), true);
        } else {
            player.displayClientMessage(PowerhouseText.msg("hrsg.state", f(gasInK - 273.15, 0), f(last.stackK() - 273.15, 0),
                    f(last.steamKgS() * 3600, 0), f(deliveredW() / 1e3, 0)), true);
        }
        return InteractionResult.SUCCESS;
    }

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return CombinedCyclePanels.HRSG;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        boolean gas = gasKgS > 0;
        out[CombinedCyclePanels.H_GAS_IN_C] = gas ? (int) Math.round(gasInK - 273.15) : 0;
        out[CombinedCyclePanels.H_STACK_C] = gas ? (int) Math.round(last.stackK() - 273.15) : 0;
        out[CombinedCyclePanels.H_DUTY_KW] = (int) Math.round(deliveredW() / 1e3);
        out[CombinedCyclePanels.H_STEAM_KGH] = sink ? (int) Math.round(last.steamKgS() * 3600) : 0;
        out[CombinedCyclePanels.H_SAT_C] = last.saturationK() > 0 ? (int) Math.round(last.saturationK() - 273.15) : 0;
        out[CombinedCyclePanels.H_PINCH_C] = last.steamKgS() > 0 ? (int) Math.round(last.pinchGasK() - 273.15) : 0;
        out[CombinedCyclePanels.H_GAS_KGS10] = (int) Math.round(gasKgS * 10);
        out[CombinedCyclePanels.H_GT_KW] = (int) Math.round(gtShaftW / 1e3);
        out[CombinedCyclePanels.H_DRUM_BAR10] = Double.isFinite(drumPa) ? (int) Math.round(drumPa / 1e4) : 0;
        out[CombinedCyclePanels.H_RECOVERY] = gtFuelW > 0 ? (int) Math.round(deliveredW() / gtFuelW * 1000) : 0;
        out[CombinedCyclePanels.H_FLAGS] = (deliveredW() > 1e4 ? 1 << CombinedCyclePanels.HF_RUN : 0)
                | (turbine == null ? 1 << CombinedCyclePanels.HF_NO_GT : 0) | (boiler.isNone() ? 1 << CombinedCyclePanels.HF_NO_BOILER : 0)
                | (!boiler.isNone() && !sink && last.dutyW() > 0 ? 1 << CombinedCyclePanels.HF_NO_SINK : 0)
                | (gas && last.steamKgS() <= 0 ? 1 << CombinedCyclePanels.HF_COLD : 0);
    }
}
