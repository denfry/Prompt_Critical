package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.AccidentPanels;
import dev.denfry.promptcritical.mod.npp.BwrPanels;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrPanels;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.BoilingWaterReactor;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Accident layer in the world: a small break on a scrammed VVER is held by one ECCS train with the core under water;
 * losing every turning shaft is a station blackout that kills the ECCS and sends the loop to its relief valves, and a
 * shaft back on the ECCS pump (the diesel) ends it; a coolant pump torn off a loaded loop is a guillotine break; the
 * boiling water reactor runs the same drill on its high-pressure core spray.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccidentGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6), PUMP_A = new BlockPos(1, 2, 1), PUMP_B = new BlockPos(3, 2, 1),
            ECCS = new BlockPos(9, 2, 1), PZR = new BlockPos(11, 2, 3), ACCUMULATOR = new BlockPos(10, 2, 10);

    private AccidentGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** A block driven from the back (south) by a creative motor — the grid motor or the diesel. */
    private static void driven(GameTestHelper h, BlockPos pos, Block block) {
        place(h, pos, block);
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    /** VVER plant: vessel, pressurizer, two coolant pumps and one ECCS train on motors, one hydroaccumulator. */
    private static void vverPlant(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        place(h, PZR, PcBlocks.PRESSURIZER.get());
        driven(h, PUMP_A, PcBlocks.PRIMARY_PUMP.get());
        driven(h, PUMP_B, PcBlocks.PRIMARY_PUMP.get());
        driven(h, ECCS, PcBlocks.ECCS_PUMP.get());
        h.setBlock(ACCUMULATOR, PcBlocks.ECCS_ACCUMULATOR.get().defaultBlockState());
    }

    /** Full core, scrammed after long operation at half power: the decay heat of a real shutdown (≈ 3 MW). */
    private static void fuelScrammedFromHalfPower(GameTestHelper h) {
        PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
        double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
        r.setRods(1, 1);
        for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
        r.setThermalState(600, 573, 563);
        r.equilibrate(0.5);
    }

    private static PwrReactorBlockEntity pwr(GameTestHelper h) {
        return h.getBlockEntity(REACTOR);
    }

    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void smallLocaHeldByOneEccsTrain(GameTestHelper h) {
        vverPlant(h);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> fuelScrammedFromHalfPower(h))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    h.assertTrue(r.accident().eccsTrains() == 1, "ECCS train linked: " + r.accident().eccsTrains());
                    h.assertTrue(r.accident().accumulators() == 1, "accumulator found: " + r.accident().accumulators());
                    h.assertTrue(r.accident().accident().acPower(), "AC from the turning shafts");
                })
                .thenExecute(() -> pwr(h).onPanelButton(TestPlayers.of(h), PwrPanels.B_LOCA))
                .thenWaitUntil(() -> {
                    PlantAccident a = pwr(h).accident().accident();
                    h.assertTrue(a.active(), "LOCA under way");
                    h.assertTrue(a.breakArea() == PlantAccident.PWR_T2A.smallBreakM2(), "small break " + a.breakArea());
                    h.assertTrue(a.eccsFlowKgS() > 1, "ECCS injecting " + a.eccsFlowKgS());
                })
                .thenIdle(600)
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    PlantAccident a = r.accident().accident();
                    h.assertTrue(r.reactor().protection().isTripped(), "reactor held in scram");
                    h.assertTrue(a.breakFlowKgS() > 0, "break still flowing " + a.breakFlowKgS());
                    h.assertFalse(a.coreUncovered(), "core stays covered, level " + a.levelFraction());
                    h.assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
                    h.assertTrue(r.reactor().failedAssemblies() == 0, "no failed cladding: " + r.reactor().failedAssemblies());
                    int[] v = new int[PwrPanels.CHANNELS];
                    r.readPanel(v);
                    h.assertTrue((v[PwrPanels.ACCIDENT + AccidentPanels.FLAGS] & 1 << AccidentPanels.F_ACTIVE) != 0, "panel shows the accident");
                    h.assertTrue((v[PwrPanels.FLAGS] & 1 << PwrPanels.F_SI) != 0, "SI lamp lit");
                    h.assertTrue(v[PwrPanels.ACCIDENT + AccidentPanels.BREAK_CM2] == 1, "break 1 cm² on the panel");
                })
                // the drill escalates to the guillotine break and then isolates it
                .thenExecute(() -> {
                    var player = TestPlayers.of(h);
                    pwr(h).onPanelButton(player, PwrPanels.B_LOCA);
                    pwr(h).onPanelButton(player, PwrPanels.B_LOCA);
                })
                .thenWaitUntil(() -> h.assertTrue(pwr(h).accident().accident().breakArea() == 0, "drill break isolated"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void stationBlackoutLosesEccsUntilTheDieselTurns(GameTestHelper h) {
        vverPlant(h);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> fuelScrammedFromHalfPower(h))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    h.assertTrue(r.accident().eccsTrains() == 1, "ECCS train linked");
                    h.assertTrue(r.accident().accident().acPower(), "AC available");
                    h.assertFalse(r.accident().accident().active(), "no accident with AC");
                })
                // grid lost, no diesel: every shaft stops
                .thenExecute(() -> {
                    h.destroyBlock(PUMP_A.south());
                    h.destroyBlock(PUMP_B.south());
                    h.destroyBlock(ECCS.south());
                })
                .thenWaitUntil(() -> {
                    PlantAccident a = pwr(h).accident().accident();
                    h.assertTrue(a.stationBlackout(), "station blackout");
                    h.assertTrue(a.active(), "accident layer took over");
                    h.assertTrue(a.eccsTrains() == 0, "no ECCS train without AC");
                })
                .thenIdle(400)
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    PlantAccident a = r.accident().accident();
                    h.assertTrue(r.reactor().protection().isTripped(), "reactor scrammed");
                    h.assertTrue(a.eccsFlowKgS() == 0, "no injection " + a.eccsFlowKgS());
                    h.assertTrue(a.pressurePa() > 16e6, "loop heading for the relief valves: " + a.pressurePa());
                    h.assertTrue(a.breakArea() == 0, "no break in a blackout");
                    int[] v = new int[PwrPanels.CHANNELS];
                    r.readPanel(v);
                    h.assertTrue((v[PwrPanels.ACCIDENT + AccidentPanels.FLAGS] & 1 << AccidentPanels.F_SBO) != 0, "blackout on the panel");
                })
                // the emergency diesel picks up the ECCS bus
                .thenExecute(() -> {
                    BlockPos motor = ECCS.south();
                    h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
                    h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
                })
                .thenWaitUntil(() -> {
                    PlantAccident a = pwr(h).accident().accident();
                    h.assertTrue(a.acPower(), "AC back from the diesel");
                    h.assertTrue(a.eccsTrains() == 1, "ECCS train available again");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void tornCoolantPumpIsAGuillotineBreak(GameTestHelper h) {
        vverPlant(h);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> fuelScrammedFromHalfPower(h))
                .thenIdle(40)
                .thenExecute(() -> h.destroyBlock(PUMP_A))
                .thenWaitUntil(() -> {
                    PlantAccident a = pwr(h).accident().accident();
                    h.assertTrue(pwr(h).accident().brokenLoop().size() == 1, "torn pump recorded");
                    h.assertTrue(a.breakArea() >= PlantAccident.PWR_T2A.largeBreakM2(), "guillotine break " + a.breakArea());
                    h.assertTrue(a.accumulatorFlowKgS() > 0 || a.accumulatorWaterKg() < 1600, "accumulator injecting in the blowdown");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void bwrSmallLocaHeldByCoreSpray(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.BWR_REACTOR.get());
        driven(h, PUMP_A, PcBlocks.PRIMARY_PUMP.get());
        driven(h, PUMP_B, PcBlocks.PRIMARY_PUMP.get());
        driven(h, ECCS, PcBlocks.ECCS_PUMP.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    BoilingWaterReactor r = ((BwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
                    r.setRods(1, 1);
                    for (int i = 0; i < BoilingWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
                    r.hotStart(0.5);
                })
                .thenWaitUntil(() -> h.assertTrue(((BwrReactorBlockEntity) h.getBlockEntity(REACTOR)).accident().eccsTrains() == 1,
                        "ECCS train linked"))
                .thenExecute(() -> ((BwrReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(TestPlayers.of(h), BwrPanels.B_LOCA))
                .thenIdle(400)
                .thenExecute(() -> {
                    BwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    PlantAccident a = r.accident().accident();
                    h.assertTrue(a.active(), "LOCA under way");
                    h.assertTrue(r.reactor().trip() != BoilingWaterReactor.Trip.NONE, "scrammed: " + r.reactor().trip());
                    h.assertTrue(a.eccsFlowKgS() > 1, "core spray injecting " + a.eccsFlowKgS());
                    h.assertFalse(a.coreUncovered(), "core covered, level " + a.levelFraction());
                    h.assertFalse(a.pctLimitExceeded(), "PCT " + a.peakCladK());
                })
                .thenSucceed();
    }
}
