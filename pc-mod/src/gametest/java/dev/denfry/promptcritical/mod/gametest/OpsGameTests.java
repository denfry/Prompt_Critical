package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.BoilerPanels;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.mine.MainMineFanBlockEntity;
import dev.denfry.promptcritical.mod.mine.MineDrainagePumpBlockEntity;
import dev.denfry.promptcritical.mod.ops.OpsActions;
import dev.denfry.promptcritical.mod.ops.OpsItem;
import dev.denfry.promptcritical.mod.ops.OpsMachine;
import dev.denfry.promptcritical.mod.ops.OpsWorld;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.ops.Baseline;
import dev.denfry.promptcritical.sim.ops.CheckInput;
import dev.denfry.promptcritical.sim.ops.CommissioningCheck;
import dev.denfry.promptcritical.sim.ops.CommissioningDefect;
import dev.denfry.promptcritical.sim.ops.CommissioningDefectKind;
import dev.denfry.promptcritical.sim.ops.CommissioningMode;
import dev.denfry.promptcritical.sim.ops.CommissioningState;
import dev.denfry.promptcritical.sim.ops.FaultView;
import dev.denfry.promptcritical.sim.ops.ImpellerWear;
import dev.denfry.promptcritical.sim.ops.MaintenanceFaultKind;
import dev.denfry.promptcritical.sim.ops.OpsLogEntry;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.UnitAssembly;
import dev.denfry.promptcritical.sim.ops.UnitComponent;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.utilities.WaterGrade;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Пусконаладка и обслуживание в мире (этап 11а M3, 13 §13.6.5–13.6.7): T-13-28 (прикипевший ПК и слабый шов — разрыв
 * раньше подрыва, пуск непринятого котла в журнале), T-13-31 (насос с обратным вращением), T-13-32 (замена ПК у
 * принятого котла), T-13-33 (режимы {@code ENFORCED}/{@code ADVISORY}); критерии среза «подшипник по панели» и
 * «накипь». Дефекты монтажа задаются явно ({@link #defects}), чтобы не зависеть от зерна мира.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OpsGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Котёл фасадом на север: клетки x 1…3, y 1…3, z 1…6 (как в {@link BoilerGameTests}). */
    private static final BlockPos BOILER = new BlockPos(2, 2, 2);
    private static final CommandSource TEST = new CommandSource.Automatic("gametest");

    private OpsGameTests() {
    }

    // --- общее для тестов мира с машинами I0 ---

    static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    static OpsSubsystem ops(GameTestHelper h) {
        return binding(h).world().subsystem(OpsSubsystem.class);
    }

    /**
     * Установка машины с заданным набором дефектов монтажа (без случайных): пусконаладка заново, насос залит, если
     * среди дефектов нет сухого насоса. Возвращает false, пока установки нет (ждать в {@code thenWaitUntil}).
     */
    static boolean defects(GameTestHelper h, OpsMachine m, CommissioningDefect... list) {
        OpsSubsystem ops = ops(h);
        if (m.simId().isNone() || ops.unit(m.simId()) == null) return false;
        var c = ops.commissioning();
        UnitAssembly asm = c.assembly(m.simId()).orElse(null);
        if (asm == null) return true;
        c.unregister(m.simId());
        c.register(asm, List.of(list), binding(h).world().clock().tick());
        boolean dry = false;
        for (CommissioningDefect d : list) dry |= d.kind() == CommissioningDefectKind.DRY_PUMP;
        if (!dry && asm.kind().checklist().contains(CommissioningCheck.PRIMING))
            ops.perform(m.simId(), new CheckInput.Priming(true), null);
        return true;
    }

    /** Установка без дефектов монтажа — для тестов, которым пусконаладка не нужна. */
    static void quiet(GameTestHelper h, OpsMachine m) {
        h.assertTrue(defects(h, m), "ops unit for " + m.opsKind());
    }

    private static SimId component(GameTestHelper h, SimId unit, UnitComponent role) {
        return ops(h).commissioning().assembly(unit).orElseThrow().components().get(role);
    }

    /** Котёл принят: все проверки пройдены (без дефектов). */
    private static void accept(GameTestHelper h, SimId id) {
        OpsSubsystem ops = ops(h);
        double pw = ops.commissioning().assembly(id).orElseThrow().workingPressurePa();
        ops.perform(id, new CheckInput.PressureTest(true, 290, ops.config().testPressurePa(pw), ops.config().hydrotestHoldPhysS()), null);
        ops.perform(id, new CheckInput.InstrumentCheck(), null);
        ops.perform(id, new CheckInput.ReliefValveSet(true), null);
        ops.perform(id, new CheckInput.TrialRun(ops.config().trialRunS(), 1, false, true, Baseline.NONE), null);
        h.assertTrue(ops.commissioning().state(id).orElseThrow() == CommissioningState.ACCEPTED, "boiler accepted");
    }

    private static void mode(GameTestHelper h, CommissioningMode mode) {
        OpsSubsystem ops = ops(h);
        ops.setConfig(ops.config().withCommissioning(mode));
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static FireTubeBoilerBlockEntity boiler(GameTestHelper h) {
        return h.getBlockEntity(BOILER);
    }

    private static BoilerUnit boilerUnit(GameTestHelper h) {
        BoilerUnit u = boiler(h).unit();
        h.assertTrue(u != null, "boiler twin");
        return u;
    }

    private static boolean boilerReady(GameTestHelper h) {
        FireTubeBoilerBlockEntity b = boiler(h);
        return !b.simId().isNone() && b.unit() != null && ops(h).unit(b.simId()) != null;
    }

    private static List<OpsLogEntry.Kind> log(GameTestHelper h, SimId id) {
        List<OpsLogEntry.Kind> out = new ArrayList<>();
        for (OpsLogEntry e : ops(h).log(id)) out.add(e.kind());
        return out;
    }

    private static Player player(GameTestHelper h) {
        return h.makeMockPlayer(GameType.SURVIVAL);
    }

    // --- T-13-28 ---

    /**
     * Котёл без испытаний с прикипевшим ПК (подрыв при 1,9 уставки = 2,51 МПа) и швом k_b = 2,0: пуск непринятого котла
     * записан {@code START_UNACCEPTED}; обечайка рвётся при 2,40 ± 0,02 МПа раньше подрыва ПК.
     */
    @GameTest(template = LARGE, timeoutTicks = 3600)
    public static void stuckReliefValveBurstsUnacceptedBoiler(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
        BoilerUnit[] unit = new BoilerUnit[1];
        double[] pMax = new double[1];
        h.onEachTick(() -> {
            if (unit[0] != null && unit[0].rupture() == BoilerRupture.NONE) pMax[0] = Math.max(pMax[0], unit[0].pressurePa());
        });
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(boilerReady(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    mode(h, CommissioningMode.ADVISORY);
                    SimId id = boiler(h).simId();
                    h.assertTrue(defects(h, boiler(h),
                            CommissioningDefect.of(CommissioningDefectKind.PSV_STUCK, component(h, id, UnitComponent.RELIEF_VALVE), 1.9),
                            new CommissioningDefect(CommissioningDefectKind.SEAM_WEEP, component(h, id, UnitComponent.SHELL), 1.2, 2.0,
                                    false)), "defects set");
                    h.assertTrue(OpsActions.startAllowed(null, h.getLevel(), boiler(h)), "ADVISORY lets an unaccepted boiler start");
                    h.assertTrue(log(h, id).contains(OpsLogEntry.Kind.START_UNACCEPTED), "START_UNACCEPTED journaled");
                    unit[0] = boilerUnit(h);
                })
                // котёл берёт подрыв ПК и k_b шва из пусконаладки в своём тике — до давления
                .thenWaitUntil(() -> h.assertTrue(unit[0].reliefLiftPa() > 2.5e6, "stuck relief valve applied: " + unit[0].reliefLiftPa()))
                .thenExecute(() -> {
                    SimId id = boiler(h).simId();
                    plant(h).presetWater(id, 2.35e6, 0.67);
                    binding(h).submit(new SimCommand.ChargeFuel(id, PlantFuels.COAL, 0.3 * 600), TEST);
                    binding(h).submit(new SimCommand.Operate(id, SteamPlant.IGNITE), TEST);
                })
                .thenWaitUntil(() -> h.assertTrue(unit[0].rupture() != BoilerRupture.NONE, "shell bursts, p = " + unit[0].pressurePa()))
                .thenExecute(() -> {
                    BoilerUnit u = unit[0];
                    h.assertTrue(u.rupture() == BoilerRupture.SHELL_OVERPRESSURE, "shell overpressure: " + u.rupture());
                    h.assertTrue(u.reliefLifts() == 0, "stuck relief valve never lifted");
                    double burst = 2.0 * u.spec().workingPressurePa();
                    h.assertTrue(Math.abs(pMax[0] - burst) <= 0.02e6, "burst at " + pMax[0] + " Pa, expected " + burst);
                })
                .thenSucceed();
    }

    private static SteamPlant plant(GameTestHelper h) {
        return binding(h).world().subsystem(SteamPlant.class);
    }

    // --- T-13-31 ---

    /** Насос водоотлива с обратным вращением вала Create: напор 0,40 ± 0,01 H; толчок находит {@code REVERSED_ROTATION}. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void reversedPumpLosesHeadAndBumpFindsIt(GameTestHelper h) {
        BlockPos pump = new BlockPos(5, 2, 5);
        h.setBlock(pump, north(PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState()));
        BlockPos motor = pump.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(-64));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(pump)), "pump ops unit"))
                .thenWaitUntil(() -> h.assertTrue(!h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).opsStopped(), "pump turns"))
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    double head = be.opsCurrent().headM(), rated = MineDrainagePumpBlockEntity.PUMP.ratedHeadM();
                    h.assertTrue(Math.abs(head / rated - ImpellerWear.REVERSED_HEAD_FACTOR) <= 0.01,
                            "reversed head " + head + " of " + rated);
                    MainMineFanBlockEntity.bump(player(h), be, be.shaftSignedRpm());
                    boolean found = false;
                    for (CommissioningDefect d : ops(h).commissioning().defects(be.simId()))
                        found |= d.kind() == CommissioningDefectKind.REVERSED_ROTATION && d.found();
                    h.assertTrue(found, "ROTATION_CHECK finds REVERSED_ROTATION");
                })
                .thenSucceed();
    }

    // --- T-13-32 ---

    /** Принятый котёл, замена ПК: состояние {@code MODIFIED}, сброшена только {@code RELIEF_VALVE_SET}. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void replacingReliefValveResetsOnlyItsCheck(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(boilerReady(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    mode(h, CommissioningMode.ADVISORY);
                    SimId id = boiler(h).simId();
                    quiet(h, boiler(h));
                    accept(h, id);
                    plant(h).presetWater(id, 0.11e6, 0.67);
                })
                .thenWaitUntil(() -> h.assertTrue(boilerUnit(h).pressurePa() < SteamPlant.OPEN_PRESSURE_PA, "no pressure in the boiler"))
                .thenExecute(() -> {
                    SimId id = boiler(h).simId();
                    OpsSubsystem ops = ops(h);
                    Player p = player(h);
                    ItemStack valve = new ItemStack(PcItems.SAFETY_VALVE.get());
                    OpsActions.instant(p, valve, OpsItem.Kind.SAFETY_VALVE, ops, ops.unit(id), boiler(h));
                    h.assertTrue(ops.commissioning().state(id).orElseThrow() == CommissioningState.MODIFIED,
                            "MODIFIED: " + ops.commissioning().state(id));
                    Set<CommissioningCheck> expected = EnumSet.of(CommissioningCheck.PRESSURE_TEST, CommissioningCheck.INSTRUMENT_CHECK,
                            CommissioningCheck.TRIAL_RUN);
                    h.assertValueEqual(ops.commissioning().passedChecks(id), expected, "only RELIEF_VALVE_SET reset");
                })
                .thenSucceed();
    }

    // --- T-13-33 ---

    /**
     * {@code ENFORCED}: розжиг нового котла огнивом — отказ с перечнем непройденных проверок; {@code ADVISORY} — котёл
     * разжигается, на щите горит «НЕ ПРИНЯТ». Свой батч: режим общий для уровня.
     */
    @GameTest(template = LARGE, timeoutTicks = 400, batch = "ops_enforced")
    public static void enforcedModeRefusesIgnition(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
        Player[] p = new Player[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(boilerReady(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    mode(h, CommissioningMode.ENFORCED);
                    SimId id = boiler(h).simId();
                    quiet(h, boiler(h));
                    plant(h).presetWater(id, 0.3e6, 0.67);
                    binding(h).submit(new SimCommand.ChargeFuel(id, PlantFuels.COAL, 0.3 * 600), TEST);
                    p[0] = player(h);
                    p[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
                    boiler(h).useItem(p[0], InteractionHand.MAIN_HAND, p[0].getMainHandItem());
                    var permit = ops(h).canStart(id);
                    h.assertTrue(!permit.allowed(), "ENFORCED refuses");
                    h.assertValueEqual(permit.missing(), EnumSet.of(CommissioningCheck.PRESSURE_TEST, CommissioningCheck.INSTRUMENT_CHECK,
                            CommissioningCheck.RELIEF_VALVE_SET, CommissioningCheck.TRIAL_RUN), "missing checks listed");
                    h.assertTrue(log(h, id).contains(OpsLogEntry.Kind.START_REFUSED), "START_REFUSED journaled");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(!boilerUnit(h).lit(), "furnace not lit under ENFORCED");
                    mode(h, CommissioningMode.ADVISORY);
                    p[0].setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL));
                    boiler(h).useItem(p[0], InteractionHand.MAIN_HAND, p[0].getMainHandItem());
                })
                .thenWaitUntil(() -> h.assertTrue(boilerUnit(h).lit(), "lit under ADVISORY"))
                .thenWaitUntil(() -> {
                    int[] out = new int[BoilerPanels.B_CHANNELS];
                    boiler(h).readPanel(out);
                    h.assertTrue((out[BoilerPanels.B_FLAGS] & 1 << BoilerPanels.F_UNACCEPTED) != 0, "NOT ACCEPTED lamp on");
                })
                .thenSucceed();
    }

    // --- критерии среза ---

    /**
     * «Подшипник по панели»: износ вкладыша не виден, пока его не послушают стетоскопом; после замера щит
     * обслуживания на насосе горит жёлтым, на остановленном насосе вкладыш меняют — строка гаснет.
     */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void bearingWearShowsOnPanelAndShellFixesIt(GameTestHelper h) {
        BlockPos pump = new BlockPos(5, 2, 5);
        h.setBlock(pump, north(PcBlocks.MINE_DRAINAGE_PUMP.get().defaultBlockState()));
        BlockPos panel = pump.north();
        h.setBlock(panel, north(PcBlocks.MAINTENANCE_PANEL.get().defaultBlockState()));
        BlockPos motor = pump.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        Player[] p = new Player[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(defects(h, h.<MineDrainagePumpBlockEntity>getBlockEntity(pump)), "pump ops unit"))
                .thenWaitUntil(() -> h.assertTrue(!h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).opsStopped(), "pump turns"))
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    OpsSubsystem.Unit u = ops(h).unit(be.simId());
                    u.wear().bearing().setDamage(0.75);
                    h.assertTrue(row(u, MaintenanceFaultKind.BEARING_WEAR).severity() == 0, "unmeasured wear hidden");
                    p[0] = player(h);
                    OpsActions.instant(p[0], new ItemStack(PcItems.MECHANIC_STETHOSCOPE.get()), OpsItem.Kind.STETHOSCOPE, ops(h), u, be);
                    h.assertTrue(row(u, MaintenanceFaultKind.BEARING_WEAR).severity() == 2, "stethoscope reveals the bearing");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(panel).getValue(PcProperties.SEVERITY) >= 2, "panel lamp shows the fault"))
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(0))
                .thenWaitUntil(() -> h.assertTrue(h.<MineDrainagePumpBlockEntity>getBlockEntity(pump).opsStopped(), "pump stopped"))
                .thenExecute(() -> {
                    MineDrainagePumpBlockEntity be = h.getBlockEntity(pump);
                    OpsSubsystem.Unit u = ops(h).unit(be.simId());
                    ItemStack shell = new ItemStack(PcItems.BRONZE_BEARING_SHELL.get());
                    h.assertTrue(OpsActions.complete(p[0], shell, OpsItem.Kind.BEARING_SHELL, false, ops(h), u, be), "shell fitted");
                    h.assertTrue(shell.isEmpty(), "shell consumed");
                    h.assertTrue(u.wear().bearing().damage() < 0.05, "new bearing");
                    h.assertTrue(row(u, MaintenanceFaultKind.BEARING_WEAR).severity() == 0, "row cleared");
                    h.assertTrue(log(h, be.simId()).contains(OpsLogEntry.Kind.MAINTENANCE_DONE), "MAINTENANCE_DONE journaled");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(panel).getValue(PcProperties.SEVERITY) < 2, "panel lamp off"))
                .thenSucceed();
    }

    private static FaultView row(OpsSubsystem.Unit u, MaintenanceFaultKind kind) {
        for (FaultView f : u.panel().faults()) if (f.kind() == kind) return f;
        throw new AssertionError("no panel row " + kind);
    }

    /**
     * «Накипь»: жёсткость питательной воды котла — по сорту жидкости в баке (сырая против умягчённой); накипь видна
     * на щите обслуживания принятого котла и снимается скребком на спущенном холодном котле.
     */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void scaleShowsOnPanelAndScraperRemovesIt(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
        BlockPos panel = new BlockPos(2, 2, 0);                  // на фасаде котла (клетка z = 1)
        h.setBlock(panel, north(PcBlocks.MAINTENANCE_PANEL.get().defaultBlockState()));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(boilerReady(h), "boiler and its ops unit"))
                .thenExecute(() -> {
                    mode(h, CommissioningMode.ADVISORY);
                    quiet(h, boiler(h));
                    accept(h, boiler(h).simId());
                    boiler(h).feedTank().fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> h.assertTrue(boilerUnit(h).feedHardness() == BoilerUnit.RAW_HARDNESS, "raw water is hard"))
                .thenExecute(() -> {
                    var tank = boiler(h).feedTank();
                    tank.drain(tank.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
                    tank.fill(new FluidStack(PcFluids.SOFTENED_WATER.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
                })
                .thenWaitUntil(() -> h.assertTrue(boilerUnit(h).feedHardness() == WaterGrade.SOFTENED.quality().hardnessMolEqM3(),
                        "softened water: " + boilerUnit(h).feedHardness()))
                .thenExecute(() -> {
                    var tank = boiler(h).feedTank();
                    tank.drain(tank.getFluidAmount(), IFluidHandler.FluidAction.EXECUTE);
                    boilerUnit(h).boiler().setScale(0.6e-3);
                    if (!OpsActions.drainedAndCold(boiler(h)))
                        binding(h).submit(new SimCommand.Operate(boiler(h).simId(), SteamPlant.DRAIN), TEST);
                })
                .thenWaitUntil(() -> {
                    h.assertTrue(row(ops(h).unit(boiler(h).simId()), MaintenanceFaultKind.HX_FOULING).severity() == 2, "scale on the panel");
                    h.assertTrue(h.getBlockState(panel).getValue(PcProperties.SEVERITY) >= 2, "panel lamp shows the scale");
                })
                .thenWaitUntil(() -> h.assertTrue(OpsActions.drainedAndCold(boiler(h)), "drained: " + boilerUnit(h).waterMassKg() + " kg"))
                .thenExecute(() -> {
                    FireTubeBoilerBlockEntity b = boiler(h);
                    OpsSubsystem.Unit u = ops(h).unit(b.simId());
                    ItemStack scraper = new ItemStack(PcItems.BOILER_SCRAPER.get());
                    h.assertTrue(OpsActions.complete(player(h), scraper, OpsItem.Kind.BOILER_SCRAPER, false, ops(h), u, b), "scraped");
                    h.assertTrue(boilerUnit(h).boiler().scaleM() == 0, "scale removed");
                    h.assertTrue(!scraper.isEmpty(), "scraper is a tool");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(panel).getValue(PcProperties.SEVERITY) == 0, "panel lamp off"))
                .thenSucceed();
    }
}
