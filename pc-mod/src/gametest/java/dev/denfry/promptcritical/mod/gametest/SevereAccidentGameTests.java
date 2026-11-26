package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.AccidentPanels;
import dev.denfry.promptcritical.mod.npp.PwrPanels;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.accident.CoreMeltProgression;
import dev.denfry.promptcritical.sim.accident.HydrogenCombustion;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.accident.ReactivityInitiatedAccident;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import dev.denfry.promptcritical.sim.protection.TripReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Severe light-water accidents of 04c in the world, in short deterministic form: a rod ejection at hot zero power
 * (a mild prompt burst, a scram and a small break through the housing), the single-cluster design mistake that fails
 * and fragments fuel, a torn steam generator tube, and a core melt without ECCS that goes through a dry cavity's vessel
 * or stays in a flooded one.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SevereAccidentGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6), PUMP_A = new BlockPos(1, 2, 1), PUMP_B = new BlockPos(3, 2, 1),
            ECCS = new BlockPos(9, 2, 1), PZR = new BlockPos(11, 2, 3), SG = new BlockPos(1, 2, 10);

    private SevereAccidentGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void driven(GameTestHelper h, BlockPos pos, Block block) {
        place(h, pos, block);
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void vverPlant(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        place(h, PZR, PcBlocks.PRESSURIZER.get());
        driven(h, PUMP_A, PcBlocks.PRIMARY_PUMP.get());
        driven(h, PUMP_B, PcBlocks.PRIMARY_PUMP.get());
        driven(h, ECCS, PcBlocks.ECCS_PUMP.get());
    }

    private static PwrReactorBlockEntity pwr(GameTestHelper h) {
        return h.getBlockEntity(REACTOR);
    }

    /**
     * Fresh core, pumps at speed, trip reset, then hot zero power: 290 °C everywhere, scram bank cocked, control group
     * fully in and boron on criticality.
     */
    private static GameTestSequence hotZeroPower(GameTestHelper h) {
        return critical(h, 563.15, 563.15, 563.15, 1, 1e-4);
    }

    /** Same start-up to a critical core at {@code power} with the control group at {@code rg} (1 — fully in). */
    private static GameTestSequence critical(GameTestHelper h, double fuelK, double hotK, double coldK, double rg, double power) {
        vverPlant(h);
        return h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
                    r.setRods(1, 1);
                    for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
                })
                .thenWaitUntil(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    h.assertTrue(r.pumpSpeed(0) > 0.99 && r.pumpSpeed(1) > 0.99, "pumps at speed");
                })
                .thenIdle(240)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    h.assertTrue(r.resetTrip(), "trip reset: " + r.protection().activeReasons());
                    r.setThermalState(fuelK, hotK, coldK);
                    r.setRods(0, rg);
                    double lo = 0, hi = 30;
                    for (int i = 0; i < 40; i++) {
                        double m = (lo + hi) / 2;
                        r.setBoronGkg(m);
                        if (r.evaluateReactivity() > 0) lo = m;
                        else hi = m;
                    }
                    r.setBoronGkg((lo + hi) / 2);
                    r.equilibrate(power);
                });
    }

    /** §4c.5: one cluster of the fully inserted group is about 1.3β: a burst, a high-flux scram, the housing break. */
    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void rodEjectionAtHotZeroPowerIsAMildBurst(GameTestHelper h) {
        hotZeroPower(h)
                .thenExecute(() -> pwr(h).onPanelButton(TestPlayers.of(h), PwrPanels.B_RIA))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    ReactivityInitiatedAccident.Pulse p = r.reactor().lastPulse();
                    h.assertTrue(p != null && p.prompt(), "prompt burst");
                    h.assertTrue(r.reactor().protection().isTripped(), "scram");
                    // the burst's high flux and the accident layer's hold arrive on the same protection step
                    TripReason first = r.reactor().protection().firstOut();
                    h.assertTrue(first == TripReason.HIGH_FLUX || first == TripReason.LUA_REQUEST, "first-out " + first);
                    h.assertTrue(r.accident().accident().breakArea() >= PressurizedWaterReactor.ROD_HOUSING_BREAK_M2 * 0.99,
                            "housing break " + r.accident().accident().breakArea());
                    h.assertTrue(r.reactor().failedAssemblies() == 0, "fresh fuel holds: " + r.reactor().failedAssemblies());
                    int[] v = new int[PwrPanels.CHANNELS];
                    r.readPanel(v);
                    h.assertTrue((v[PwrPanels.RIA_FLAGS] & 1 << PwrPanels.R_PROMPT) != 0, "panel shows the burst");
                    h.assertTrue(v[PwrPanels.RIA_PEAK_CALG] > 18 && v[PwrPanels.RIA_PEAK_CALG] < v[PwrPanels.RIA_LIMIT_CALG],
                            "hot spot " + v[PwrPanels.RIA_PEAK_CALG] + " under " + v[PwrPanels.RIA_LIMIT_CALG]);
                })
                .thenSucceed();
    }

    /**
     * §4c.6: one torn tube at half power leaks about 17 kg/s, the N-16 monitor alarms within 10 s; isolating the
     * generator keeps the leak in it, plugging the tube ends it, and the PORV key opens the pressurizer relief.
     */
    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void tubeRuptureAlarmsOnNitrogenSixteenAndIsolates(GameTestHelper h) {
        place(h, SG, PcBlocks.STEAM_GENERATOR.get());
        critical(h, 800, 588, 563, 0.3, 0.5)
                .thenExecute(() -> pwr(h).onPanelButton(TestPlayers.of(h), PwrPanels.B_SGTR))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    h.assertTrue(r.tubeRuptureModel().ruptured(), "tube torn");
                    h.assertTrue(r.reactor().tubeLeakKgS() > 12, "leak " + r.reactor().tubeLeakKgS());
                    h.assertTrue(r.tubeRuptureModel().n16Alarm(), "N-16 alarm");
                })
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    double delay = r.tubeRuptureModel().alarmDelayS();
                    h.assertTrue(delay > 0 && delay < 10, "N-16 within 10 s: " + delay);
                    h.assertTrue(r.tubeRuptureModel().toCondenserKg() > 0, "coolant to the condenser");
                    int[] v = new int[PwrPanels.CHANNELS];
                    r.readPanel(v);
                    h.assertTrue((v[PwrPanels.SG_FLAGS] & 1 << PwrPanels.S_N16) != 0, "N-16 on the panel");
                    h.assertTrue(v[PwrPanels.SGTR_KGS10] > 100, "leak on the panel " + v[PwrPanels.SGTR_KGS10]);
                    var player = TestPlayers.of(h);
                    r.onPanelButton(player, PwrPanels.B_SG_ISOLATE);
                    r.onPanelButton(player, PwrPanels.B_PORV);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    h.assertTrue(r.tubeRuptureModel().isolated(), "isolated");
                    h.assertTrue(r.tubeRuptureModel().excessWaterKg() > 10, "leak fills the isolated generator "
                            + r.tubeRuptureModel().excessWaterKg());
                    h.assertTrue(r.reactor().porvOpen(), "PORV held open");
                    r.onPanelButton(TestPlayers.of(h), PwrPanels.B_SGTR);
                })
                .thenWaitUntil(() -> h.assertTrue(pwr(h).reactor().tubeLeakKgS() == 0, "tube plugged"))
                .thenSucceed();
    }

    /**
     * A VVER without ECCS on a guillotine break, scrammed from half power: no water ever comes back. When
     * {@code cavityWater}, the 3×3 under the vessel's footprint (two below the controller) is flooded.
     */
    private static GameTestSequence meltdown(GameTestHelper h, boolean cavityWater) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        place(h, PZR, PcBlocks.PRESSURIZER.get());
        driven(h, PUMP_A, PcBlocks.PRIMARY_PUMP.get());
        driven(h, PUMP_B, PcBlocks.PRIMARY_PUMP.get());
        if (cavityWater)
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++) h.setBlock(REACTOR.offset(x, -2, z), Blocks.WATER.defaultBlockState());
        return h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
                    r.setRods(1, 1);
                    for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
                    r.setThermalState(600, 573, 563);
                    r.equilibrate(0.5);
                })
                .thenIdle(110) // the cavity scan runs every 5 s
                .thenExecute(() -> {
                    h.assertTrue(pwr(h).accident().cavityFlooded() == cavityWater, "cavity scan: " + pwr(h).accident().cavityFlooded());
                    var player = TestPlayers.of(h);
                    pwr(h).onPanelButton(player, PwrPanels.B_LOCA);
                    pwr(h).onPanelButton(player, PwrPanels.B_LOCA);
                });
    }

    /** §4c.10: a dry cavity — the core melts, the head fails at low pressure and the corium lands under the vessel. */
    @GameTest(template = LARGE, timeoutTicks = 4000)
    public static void unCooledCoreMeltsThroughTheVessel(GameTestHelper h) {
        meltdown(h, false)
                .thenWaitUntil(() -> {
                    CoreMeltProgression m = pwr(h).accident().accident().melt();
                    h.assertTrue(m.vesselFailed(), "stage " + m.stage() + ", head " + m.lowerHeadK() + " K");
                })
                // the vessel's footprint reaches one block below the controller; the melt falls through the dry cavity
                // under it to the floor (the template's bottom layer is air too, the floor is the one under it)
                .thenWaitUntil(() -> h.assertBlockPresent(PcBlocks.CORIUM.get(), REACTOR.below(3)))
                .thenExecute(() -> {
                    // the rest of the melt has spread over the cavity floor around the pour
                    int pool = 0;
                    for (int x = -3; x <= 3; x++)
                        for (int z = -3; z <= 3; z++)
                            if (h.getBlockState(REACTOR.offset(x, -3, z)).is(PcBlocks.CORIUM_POOL.get())) pool++;
                    h.assertTrue(pool > 0, "corium spread around the pour");
                    PwrReactorBlockEntity r = pwr(h);
                    h.assertTrue(r.accident().coriumFront() != null, "corium tracked");
                    h.assertTrue(r.reactor().failedAssemblies() == PressurizedWaterReactor.POSITIONS, "every assembly failed");
                    int[] v = new int[PwrPanels.CHANNELS];
                    r.readPanel(v);
                    int flags = v[PwrPanels.ACCIDENT + AccidentPanels.MELT_FLAGS];
                    h.assertTrue((flags & 1 << AccidentPanels.M_FAILED) != 0, "vessel failure on the panel");
                    h.assertTrue(v[PwrPanels.ACCIDENT + AccidentPanels.CORIUM_KG] > 0, "corium mass on the panel");
                })
                .thenSucceed();
    }

    /** §4c.10.3: the same core with the cavity flooded — the melt reaches the lower plenum and the head holds. */
    @GameTest(template = LARGE, timeoutTicks = 4000)
    public static void floodedCavityHoldsTheMeltInTheVessel(GameTestHelper h) {
        meltdown(h, true)
                .thenWaitUntil(() -> {
                    CoreMeltProgression m = pwr(h).accident().accident().melt();
                    h.assertTrue(m.retainedInVessel(), "stage " + m.stage() + ", plenum " + m.lowerPlenumKg() + " kg");
                })
                .thenIdle(600)
                .thenExecute(() -> {
                    CoreMeltProgression m = pwr(h).accident().accident().melt();
                    h.assertFalse(m.vesselFailed(), "head holds at " + m.lowerHeadK() + " K");
                    h.assertTrue(m.lowerHeadK() < 600, "head cooled from outside: " + m.lowerHeadK());
                    h.assertBlockNotPresent(PcBlocks.CORIUM.get(), REACTOR.below(2));
                    h.assertBlockNotPresent(PcBlocks.CORIUM.get(), REACTOR.below(3));
                })
                .thenSucceed();
    }

    /**
     * §4c.7.3: hydrogen in a small closed containment (5×5×5 inside walls). 6 % is a slow-deflagration mixture that
     * nothing lights below 8 % without a hot surface: it waits. Brought to 15 %, a 5 m room is past the
     * deflagration-to-detonation size; it lights, detonates with twice the AICC pressure and tears the 1 MPa shell (a
     * deflagration alone would load it with about 0.66 MPa). Alone in its batch: the blast is real.
     */
    @GameTest(template = LARGE, timeoutTicks = 600, batch = "hydrogen_alone")
    public static void hydrogenWaitsThenDetonatesInASmallContainment(GameTestHelper h) {
        for (int x = 3; x <= 9; x++)
            for (int y = 1; y <= 7; y++)
                for (int z = 3; z <= 9; z++)
                    if (x == 3 || x == 9 || y == 1 || y == 7 || z == 3 || z == 9)
                        h.setBlock(new BlockPos(x, y, z), PcBlocks.CONTAINMENT_WALL.get().defaultBlockState());
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        // the blast may take the reactor block with it: keep the block entity to read the latched breach
        PwrReactorBlockEntity[] held = new PwrReactorBlockEntity[1];
        double airMol = 101_325 * 125 / (8.314 * 313);
        double lean = airMol * 0.06 / 0.94, rich = (0.15 * (airMol + lean) - lean) / 0.85;
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> held[0] = pwr(h))
                .thenWaitUntil(() -> h.assertTrue(held[0].accident().accident().hasContainment(), "containment found: "
                        + held[0].accident().containment()))
                .thenExecute(() -> {
                    h.assertTrue(held[0].accident().containment().volumeM3() == 125, "125 m³: " + held[0].accident().containment().volumeM3());
                    held[0].accident().accident().addContainmentHydrogen(lean * 0.002016);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    PlantAccident a = held[0].accident().accident();
                    h.assertTrue(a.containmentHydrogenFraction() > 0.055, "6 % waits unlit: " + a.containmentHydrogenFraction());
                    int[] v = new int[PwrPanels.CHANNELS];
                    held[0].readPanel(v);
                    h.assertTrue(v[PwrPanels.ACCIDENT + AccidentPanels.H2_REGIME] == HydrogenCombustion.Regime.SLOW_DEFLAGRATION.ordinal(),
                            "panel regime " + v[PwrPanels.ACCIDENT + AccidentPanels.H2_REGIME]);
                    a.addContainmentHydrogen(rich * 0.002016);
                    h.assertTrue(a.containmentRegime() == HydrogenCombustion.Regime.DETONATION, "regime " + a.containmentRegime());
                })
                .thenWaitUntil(() -> h.assertTrue(held[0].accident().accident().containmentBreached(), "detonation tore the shell"))
                .thenSucceed();
    }

    /**
     * 04c M10: only the containment wall is airtight, but the free volume is the gas space. A 5×5×5 room with its lower
     * two layers poured in concrete holds 75 m³ of gas, not 125; the same room without its walls is open.
     */
    @GameTest(template = LARGE)
    public static void solidConcreteInsideTheShellIsNotFreeVolume(GameTestHelper h) {
        for (int x = 3; x <= 9; x++)
            for (int y = 1; y <= 7; y++)
                for (int z = 3; z <= 9; z++)
                    if (x == 3 || x == 9 || y == 1 || y == 7 || z == 3 || z == 9)
                        h.setBlock(new BlockPos(x, y, z), PcBlocks.CONTAINMENT_WALL.get().defaultBlockState());
                    else if (y <= 3) h.setBlock(new BlockPos(x, y, z), Blocks.GRAY_CONCRETE.defaultBlockState());
        // the scan from a point of the room's air (the legacy reactor block is 3 x 3 x 6 and would not fit)
        BlockPos inside = new BlockPos(6, 5, 6);
        h.runAfterDelay(2, () -> {
            var scan = dev.denfry.promptcritical.mod.npp.ContainmentScan.scan(h.getLevel(), h.absolutePos(inside));
            h.assertTrue(scan != null && scan.intact(), "closed: " + scan);
            h.assertTrue(scan.volumeM3() == 75, "gas space 75 m³: " + scan.volumeM3());
            h.setBlock(new BlockPos(6, 7, 6), Blocks.AIR.defaultBlockState());
            var open = dev.denfry.promptcritical.mod.npp.ContainmentScan.scan(h.getLevel(), h.absolutePos(inside));
            h.assertTrue(open != null && !open.intact(), "a hole in the roof opens it: " + open);
            h.succeed();
        });
    }

    /** §4c.5.1 design mistake: a single cluster for the whole group — several β, failed and fragmented fuel. */
    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void singleClusterGroupFailsAndFragmentsFuel(GameTestHelper h) {
        hotZeroPower(h)
                .thenExecute(() -> h.assertTrue(pwr(h).ejectRod(1.0) != null, "cluster ejected"))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = pwr(h);
                    ReactivityInitiatedAccident.Damage d = r.reactor().lastRiaDamage();
                    h.assertTrue(r.reactor().lastPulse().dollars() > 3, "several β: " + r.reactor().lastPulse().dollars());
                    h.assertTrue(r.reactor().failedAssemblies() > 0, "failed cladding");
                    h.assertTrue(d.fragmentedFraction() > 0, "fragmented fuel " + d.peakCalG());
                    h.assertTrue(r.reactor().protection().isTripped(), "scram");
                })
                .thenSucceed();
    }
}
