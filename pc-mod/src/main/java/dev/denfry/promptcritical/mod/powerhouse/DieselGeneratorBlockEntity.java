package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.automation.StationBatteryBlockEntity;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.GridDeviceBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.production.FeedTank;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Аварийный дизель-генератор 3×3×6 (07 §7.11): дизель спереди, явнополюсный генератор 750 об/мин сзади. Двойник —
 * {@link GeneratorUnit} с двигателем {@link DieselEngine}. Дизтопливо — трубой к любой части (встроенный расходный бак
 * на 8 ч работы с номинальной мощностью, §7.11.2); кабель — к верху, бокам или торцу генератора. Оперативный ток для
 * пуска и начального возбуждения — от аккумулятора {@code station_battery}, который касается агрегата (§7.11.5); пока
 * дизель работает, батарея подзаряжается. В шкаф агрегата ставятся подогреватель рубашки ({@code jacket_heater}) и
 * секвенсор нагрузки ({@code load_sequencer}). Щит — правым кликом; с Shift — состояние в чат.
 */
public class DieselGeneratorBlockEntity extends GridDeviceBlockEntity<GeneratorUnit> implements PanelHost {
    /** 1,25 МВ·А — двигатель 1 МВт [BALANCE: GDD 0,5–6,3 МВт, §7.11.2]. */
    public static final double RATED_VA = 1.25e6;
    /** Расходный бак: 8 ч при номинальной мощности, ≈ 1950 л (§7.11.2), с запасом до 2 м³. */
    public static final int TANK_MB = 2000;
    /** Плотность летнего дизтоплива, кг/л (ГОСТ 305: не более 0,863; типично 0,84). */
    static final double DENSITY_KG_PER_L = 0.84;
    /** Цетановое число летнего дизтоплива по ГОСТ 305 — не ниже 45; качество топлива как компонента пока не ведётся. */
    static final double CETANE = 45;
    /** Сера в дизтопливе, доля (07 §7.11.7: S 0,1 %); углерод 86,5 %. */
    static final double SULFUR = 0.001, CARBON = 0.865;
    /** NOx дизеля без очистки, кг/(кВт·ч) (§7.11.7: 8–12 г). */
    static final double NOX_KG_PER_KWH = 0.010;
    /** Ток аппаратуры управления от АКБ: в резерве и на пуске, А [моё]. */
    static final double STANDBY_DC_A = 2, START_DC_A = 20;
    private static final double DT = 0.05;

    private final FeedTank fuel = new FeedTank(PcFluids.DIESEL_FUEL.source.get(), TANK_MB);
    private boolean jacketHeater, sequencer, configured;
    private double airC = 15;
    @Nullable
    private BlockPos batteryPos;

    public DieselGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(PowerhouseBlockEntities.DIESEL_GENERATOR.get(), pos, state);
    }

    /** Агрегат, которому принадлежит клетка {@code pos} (контроллер, якорь секции или часть). */
    @Nullable
    public static DieselGeneratorBlockEntity at(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos anchor = s.getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.anchor(s, pos) : pos;
        BlockPos c = PcBlock.structureController(level, anchor);
        if (c == null) c = anchor;
        return level.getBlockEntity(c) instanceof DieselGeneratorBlockEntity d ? d : null;
    }

    @Override
    protected GeneratorUnit unitOf(GridSubsystem grid) {
        return grid.dieselGenerator(simId(), RATED_VA);
    }

    /** Выводы — верх, бока и задний торец генератора (клетки секции 0, 0, 3 модели). */
    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        GridDevice.faces(out, worldPosition, facing(), 0, 1, 3, Direction.UP);
        GridDevice.faces(out, worldPosition, facing(), -1, 0, 3, Direction.WEST);
        GridDevice.faces(out, worldPosition, facing(), 1, 0, 3, Direction.EAST);
        GridDevice.faces(out, worldPosition, facing(), 0, 0, 4, Direction.SOUTH);
        return List.of(out);
    }

    public FeedTank fuel() {
        return fuel;
    }

    public boolean jacketHeater() {
        return jacketHeater;
    }

    @Override
    @Nullable
    public IFluidHandler fluids(@Nullable Direction side) {
        return fuel;
    }

    @Override
    protected void serverTick(GeneratorUnit u) {
        DieselEngine e = u.diesel();
        if (e == null) return;
        if (!configured) {
            configured = true;
            u.setSequencer(sequencer);
        }
        if (ticks % 100 == 1 && level instanceof ServerLevel sl) airC = EnvironmentService.sample(sl, worldPosition, 2).airTempC();
        if (ticks % 20 == 1) batteryPos = findBattery();
        StationBatteryBlockEntity battery = batteryPos != null && level.getBlockEntity(batteryPos) instanceof StationBatteryBlockEntity b ? b : null;
        boolean dc = battery != null && !battery.battery().cutOff();
        if (battery != null) {
            if (e.fired()) battery.floatCharge(DT);
            else battery.supply(e.status() == DieselEngine.Status.CRANKING || e.status() == DieselEngine.Status.RETRY_WAIT
                    ? START_DC_A : STANDBY_DC_A, DT);
        }
        e.setFailToStartBase(ServerConfig.DIESEL_FAIL_TO_START_BASE.get());
        e.setConditions(fuel.amount() > 0.01, dc, airC, jacketHeater, CETANE);
        if (e.fired()) fuel.take(e.fuelKgS() * DT / DENSITY_KG_PER_L);
        setFlag(PcProperties.RUNNING, e.fired());
        if (ticks % 20 == 0 && e.fired() && level instanceof ServerLevel sl) emit(sl, e, u);
        if (ticks % 100 == 0) setChanged();
    }

    /** Выхлоп (§7.11.7): NOx 10 г/(кВт·ч), SO₂ = 2·S·ṁ, CO₂ по углероду; воздух α ≈ 2 — газов ≈ 30 кг на кг топлива. */
    private void emit(ServerLevel sl, DieselEngine e, GeneratorUnit u) {
        double f = e.fuelKgS();
        double kw = Math.max(0, e.torquePu() * u.speedPu() * RATED_VA / 1e3);
        AirQualityService.emit(sl, worldPosition.above(2), 6, 700, 30 * f, 2 * SULFUR * f, NOX_KG_PER_KWH * kw / 3600, 0,
                CARBON * f * 44 / 12, 0, "diesel_generator@" + worldPosition.toShortString(), EmissionLedger.Category.ENGINE);
    }

    /** Аккумулятор, касающийся агрегата гранью. */
    @Nullable
    private BlockPos findBattery() {
        var rot = Footprint.rotation(facing());
        BlockPos a = worldPosition.offset(new BlockPos(-2, -2, -2).rotate(rot)), b = worldPosition.offset(new BlockPos(2, 2, 5).rotate(rot));
        for (BlockPos p : BlockPos.betweenClosed(a, b))
            if (level.getBlockEntity(p) instanceof StationBatteryBlockEntity) {
                for (Direction d : Direction.values()) if (at(level, p.relative(d)) == this) return p.immutable();
            }
        return null;
    }

    // --- руки ---

    @Override
    public boolean accepts(ItemStack stack) {
        return stack.is(PcItems.JACKET_HEATER.get()) || stack.is(PcItems.LOAD_SEQUENCER.get());
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        boolean heater = stack.is(PcItems.JACKET_HEATER.get());
        if (heater ? jacketHeater : sequencer) {
            player.displayClientMessage(PowerhouseText.msg(heater ? "dg.heater_present" : "dg.sequencer_present"), true);
            return ItemInteractionResult.SUCCESS;
        }
        if (heater) jacketHeater = true;
        else {
            sequencer = true;
            GeneratorUnit u = unit();
            if (u != null) u.setSequencer(true);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 1.2f);
        player.displayClientMessage(PowerhouseText.msg(heater ? "dg.heater_installed" : "dg.sequencer_installed"), true);
        setChanged();
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Player player) {
        GeneratorUnit u = unit();
        if (u == null || u.diesel() == null) return InteractionResult.PASS;
        player.displayClientMessage(status(u), false);
        return InteractionResult.SUCCESS;
    }

    private Component status(GeneratorUnit u) {
        DieselEngine e = u.diesel();
        return PowerhouseText.msg("dg.status", PowerhouseText.msg("dg.state." + e.status().name().toLowerCase(Locale.ROOT)),
                f(u.frequencyHz(), 2), f(u.electricalPowerW() / 1e3, 0), f(fuel.amount(), 0), e.airStarts(),
                PowerhouseText.msg("dg.cause." + e.cause().name().toLowerCase(Locale.ROOT)));
    }

    private static String f(double v, int d) {
        return String.format(Locale.ROOT, "%." + d + "f", v);
    }

    // --- щит ---

    @Override
    public PanelLayout panelLayout() {
        return DieselPanels.DG;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        GeneratorUnit u = unit();
        if (u == null || u.diesel() == null) return;
        DieselEngine e = u.diesel();
        out[DieselPanels.KW10] = (int) Math.round(Math.max(0, u.electricalPowerW()) / 100);
        out[DieselPanels.HZ100] = (int) Math.round(u.frequencyHz() * 100);
        out[DieselPanels.V10] = (int) Math.round(u.terminalVoltageV() / 10);
        out[DieselPanels.FUEL_PERMIL] = (int) Math.round(fuel.fill() * 1000);
        out[DieselPanels.AIR] = e.airStarts();
        out[DieselPanels.BOOST] = (int) Math.round(e.boost() * 100);
        out[DieselPanels.STATUS] = e.status().ordinal();
        out[DieselPanels.CAUSE] = e.cause().ordinal();
        out[DieselPanels.PENDING] = u.pendingSteps();
        out[DieselPanels.DAYS_SINCE_TEST] = (int) Math.floor(e.secondsSinceTest() / 86_400);
        out[DieselPanels.P_FS] = (int) Math.round(e.failToStartProbability() * 10_000);
        out[DieselPanels.FUEL_KGH] = (int) Math.round(e.fuelKgS() * 3600);
        out[DieselPanels.MV] = u.voltageClass() == VoltageClass.MV_6_3 ? 1 : 0;
        out[DieselPanels.FLAGS] = bit(u.dieselReady(), DieselPanels.F_READY) | bit(e.fired(), DieselPanels.F_RUN)
                | bit(u.breakerClosed(), DieselPanels.F_BREAKER) | bit(u.autoStart(), DieselPanels.F_AUTO)
                | bit(e.status() == DieselEngine.Status.LOCKED_OUT || e.cause() == DieselEngine.Cause.FAIL_TO_START, DieselPanels.F_FAULT)
                | bit(batteryPos != null, DieselPanels.F_DC) | bit(u.sequencerInstalled(), DieselPanels.F_SEQ)
                | bit(jacketHeater, DieselPanels.F_HEATER) | bit(e.governor() == DieselEngine.Governor.DROOP, DieselPanels.F_DROOP);
    }

    private static int bit(boolean on, int b) {
        return on ? 1 << b : 0;
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        GeneratorUnit u = unit();
        if (u == null || u.diesel() == null) return;
        DieselEngine e = u.diesel();
        switch (id) {
            case DieselPanels.BUTTON_START -> {
                if (!e.start()) player.displayClientMessage(PowerhouseText.msg("dg.cause." + e.cause().name().toLowerCase(Locale.ROOT))
                        .withStyle(ChatFormatting.RED), true);
            }
            case DieselPanels.BUTTON_STOP -> {
                u.openBreaker();
                e.stop();
            }
            case DieselPanels.BUTTON_RESET -> e.reset();
            case DieselPanels.BUTTON_AUTO -> u.setAutoStart(!u.autoStart());
            case DieselPanels.BUTTON_BREAKER -> {
                if (u.breakerClosed()) u.openBreaker();
                else if (!u.closeBreaker()) player.displayClientMessage(PowerhouseText.msg(u.dieselReady() ? "dg.bus_live" : "dg.not_ready")
                        .withStyle(ChatFormatting.RED), true);
            }
            case DieselPanels.BUTTON_GOVERNOR -> e.setGovernor(e.governor() == DieselEngine.Governor.DROOP
                    ? DieselEngine.Governor.ISOCHRONOUS : DieselEngine.Governor.DROOP);
            case DieselPanels.BUTTON_LOWER -> e.setSpeedReference(e.speedReference() - 0.002);
            case DieselPanels.BUTTON_RAISE -> e.setSpeedReference(e.speedReference() + 0.002);
            case DieselPanels.BUTTON_VOLTAGE -> {
                if (e.status() == DieselEngine.Status.STANDBY || e.status() == DieselEngine.Status.LOCKED_OUT)
                    u.setVoltageClass(u.voltageClass() == VoltageClass.MV_6_3 ? VoltageClass.LV_0_4 : VoltageClass.MV_6_3);
            }
            default -> {
                return;
            }
        }
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6f, 0.9f);
        setChanged();
    }

    // --- сохранение ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        fuel.save(tag, "fuel");
        tag.putBoolean("heater", jacketHeater);
        tag.putBoolean("sequencer", sequencer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuel.load(tag, "fuel");
        jacketHeater = tag.getBoolean("heater");
        sequencer = tag.getBoolean("sequencer");
    }
}
