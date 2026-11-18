package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.sim.combustion.GasTurbine;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Газотурбинная установка 3×3×9 ({@link GasTurbine}): компрессор, камера сгорания, турбина. Топливо — горючий газ
 * по трубе (шахтный метан, коксовый газ), расход — по теплоте сгорания газа. Воздух — погода у агрегата: жарким
 * днём мощность ниже. Вал выходит через редуктор {@link TurbineGearboxBlockEntity}, поставленный вплотную к агрегату.
 * Правый клик — пуск и останов; с Shift — взвод автомата безопасности.
 */
public final class GasTurbineBlockEntity extends IronworksBlockEntity {
    public static final int GAS_TANK_MB = 4000;
    /** 3 МВт ISO, 3000 об/мин. */
    public static final double RATED_W = 3e6, NOMINAL_OMEGA = 2 * Math.PI * 3000 / 60;
    private static final double DT = 0.05;

    private final GasTurbine turbine = new GasTurbine(RATED_W, NOMINAL_OMEGA);
    private final GasBuffer gas = new GasBuffer(GAS_TANK_MB, GasBuffer::isProcessGas, this::setChanged);
    private double loadW, airK = GasTurbine.ISO_K, airPa = GasTurbine.ISO_PA;
    private int ticks;

    public GasTurbineBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.GAS_TURBINE.get(), pos, state);
    }

    public GasTurbine turbine() {
        return turbine;
    }

    /** Газотурбинная установка, которой принадлежит клетка {@code pos} (контроллер, якорь секции или часть). */
    @Nullable
    public static GasTurbineBlockEntity at(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos anchor = s.getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.anchor(s, pos) : pos;
        BlockPos c = PcBlock.structureController(level, anchor);
        if (c == null) c = anchor;
        return level.getBlockEntity(c) instanceof GasTurbineBlockEntity g ? g : null;
    }

    /** Расход выхлопных газов при нынешнем воздухе, кг/с (котлу-утилизатору). */
    public double exhaustKgS() {
        return turbine.exhaustKgS(airK, airPa);
    }

    /** Температура выхлопа, К. */
    public double exhaustK() {
        return turbine.exhaustK(airK);
    }

    /** Тепло сожжённого газа, Вт. */
    public double fuelHeatW() {
        return turbine.heatInputW();
    }

    /** Нагрузка вала от редуктора, Вт. */
    void setLoadW(double w) {
        loadW = Math.max(0, w);
    }

    /** Располагаемая мощность при нынешнем воздухе, Вт. */
    double capacityW() {
        return turbine.capacityW(airK, airPa);
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        ticks++;
        if (ticks % 100 == 1 && level instanceof ServerLevel sl) {
            Environment.Sample e = EnvironmentService.sample(sl, worldPosition, 2);
            airK = e.airTempC() + 273.15;
            airPa = e.pressurePa();
        }
        ProcessGas g = gas.gas();
        boolean fuel = g != null && gas.amount() > 0;
        turbine.step(DT, loadW / Math.max(turbine.omega(), 0.2 * NOMINAL_OMEGA), airK, airPa, fuel);
        if (g != null && turbine.lit()) gas.draw(turbine.heatInputW() * DT / g.fuel.lhv() / g.densityKgNm3);
        setStateFlag(PcProperties.RUNNING, turbine.lit());
        if (ticks % 100 == 0) setChanged();
    }

    @Override
    public IFluidHandler fluids(@Nullable Direction side) {
        return gas.input();
    }

    @Override
    public InteractionResult use(Player player) {
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(PowerhouseText.msg(turbine.resetTrip() ? "gt.reset" : "turbine.not_tripped"), true);
            return InteractionResult.SUCCESS;
        }
        boolean start = !turbine.running();
        if (start && (gas.gas() == null || gas.amount() <= 0)) {
            player.displayClientMessage(PowerhouseText.msg("gt.no_fuel").withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        turbine.setRun(start);
        level.playSound(null, worldPosition, start ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 0.7f);
        player.displayClientMessage(PowerhouseText.msg(start ? "gt.start" : "gt.stop",
                f(turbine.speedPu() * 100, 0), f(turbine.shaftPowerW() / 1e3, 0), f(capacityW() / 1e3, 0),
                f(airK - 273.15, 1), f(turbine.exhaustK(airK) - 273.15, 0)), true);
        return InteractionResult.SUCCESS;
    }

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        MutableStateTree tree = new MutableStateTree();
        turbine.save(new TreeStateWriter(tree));
        tag.put("gt", NbtStateTrees.toNbt(tree));
        tag.put("gas", gas.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("gt")) turbine.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("gt"))));
        if (tag.contains("gas")) gas.load(registries, tag.getCompound("gas"));
    }
}
