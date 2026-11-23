package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.acoustics.MachineAcoustics;
import dev.denfry.promptcritical.mod.automation.PlcBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.gas.CentrifugalCompressorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.TunerChallenges;
import dev.denfry.promptcritical.mod.scada.TwinPanels;
import dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity;
import dev.denfry.promptcritical.sim.automation.twin.ChallengeCatalog;
import dev.denfry.promptcritical.sim.automation.twin.CompressorTwinPlant;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlants;
import dev.denfry.promptcritical.sim.gasprocessing.CentrifugalCompressor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Центробежный компрессор и антипомпаж (09 §9.8.5, 03d §3d.5.12) и задача наладчика на инженерной станции
 * (03d §3d.11): вал Create разгоняет ротор; пуск со встроенным регулятором выходит в линию без помпажа; закрытый вручную
 * рецикл — помпаж с износом подшипника и разрушением машины; решение задачи из справочника попадает в таблицу лидеров.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompressorSurgeGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CC = new BlockPos(5, 2, 5);
    private static final int STEPS_PER_S = 20;

    private CompressorSurgeGameTests() {
    }

    private static void place(GameTestHelper h) {
        h.setBlock(CC, PcBlocks.CENTRIFUGAL_COMPRESSOR.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void createShaftSpinsTheRotorUp(GameTestHelper h) {
        place(h);
        BlockPos motor = CC.north();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.startSequence()
                .thenWaitUntil(() -> {
                    CentrifugalCompressorBlockEntity be = h.getBlockEntity(CC);
                    h.assertTrue(be.compressor().speed() > 0.3, "rotor follows the shaft, speed " + be.compressor().speed());
                    h.assertTrue(h.getBlockState(CC).getValue(PcProperties.RUNNING), "running flag");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void antiSurgeControllerPutsTheCompressorOnLineWithoutSurge(GameTestHelper h) {
        place(h);
        h.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    CentrifugalCompressorBlockEntity be = h.getBlockEntity(CC);
                    for (int k = 0; k < 40 * STEPS_PER_S; k++) be.step(1.0);
                    be.setLine(0.85);
                    for (int k = 0; k < 150 * STEPS_PER_S; k++) be.step(1.0);
                    CentrifugalCompressor c = be.compressor();
                    h.assertTrue(c.surgeCycles() == 0, "no surge cycles, got " + c.surgeCycles());
                    h.assertTrue(c.lineFlowM3s() >= 0.9, "on line, flow " + c.lineFlowM3s());
                    h.assertTrue(be.cleanStart(), "clean start recorded for no_surge");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void closedRecycleSurgesWearsTheBearingAndWrecksTheMachine(GameTestHelper h) {
        place(h);
        h.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    CentrifugalCompressorBlockEntity be = h.getBlockEntity(CC);
                    BlockPos abs = h.absolutePos(CC);
                    be.setAuto(false);
                    be.setManualRecycle(0);
                    for (int k = 0; k < 60 * STEPS_PER_S; k++) be.step(1.0);
                    h.assertTrue(be.compressor().surgeCycles() > 10, "surge cycles " + be.compressor().surgeCycles());
                    h.assertTrue(MachineAcoustics.bearingDamage(h.getLevel(), abs) > 0.02, "bearing worn by surge");
                    h.assertFalse(be.cleanStart(), "no clean start");
                    for (int k = 0; k < 300 * STEPS_PER_S && !h.getLevel().getBlockState(abs).isAir(); k++) be.step(1.0);
                    h.assertTrue(h.getLevel().getBlockState(abs).isAir(), "prolonged surge wrecks the compressor");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void tunerTaskOnTheWorkstationEntersTheLeaderboard(GameTestHelper h) {
        BlockPos ws = new BlockPos(3, 2, 3), plc = new BlockPos(7, 2, 3);
        h.setBlock(ws, PcBlocks.SIMULATION_WORKSTATION.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(plc, PcBlocks.PLC.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var player = TestPlayers.of(h);
        String id = "compressor_startup_no_surge";
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((PlcBlockEntity) h.getBlockEntity(plc)).unit() != null, "PLC in the topology"))
                .thenExecute(() -> {
                    TunerChallenges.reset(h.getLevel().getServer(), id);
                    var cpu = ((PlcBlockEntity) h.getBlockEntity(plc)).unit().plc();
                    cpu.setRunKey(false);
                    cpu.load(ChallengeCatalog.COMPRESSOR_REFERENCE);
                    TwinWorkstationBlockEntity w = h.getBlockEntity(ws);
                    while (!w.template().equals(CompressorTwinPlant.ID)) w.onPanelButton(player, TwinPanels.W_PLANT);
                    h.assertTrue(TwinPlants.IDS.contains(w.template()), "compressor station selectable");
                    w.startChallenge(player);
                    h.assertTrue(w.challengeRunning() && w.run() != null, "task running");
                    ScenarioRun run = w.run();
                    while (run.status() == ScenarioRun.Status.RUNNING) run.step();
                    h.assertTrue(run.status() == ScenarioRun.Status.PASSED, "reference solution passes: " + run.reason());
                })
                .thenWaitUntil(() -> {
                    TwinWorkstationBlockEntity w = h.getBlockEntity(ws);
                    h.assertTrue(w.run() == null && w.challengeRank() > 0, "placed on the leaderboard, rank " + w.challengeRank());
                    h.assertTrue(!TunerChallenges.top(h.getLevel().getServer(), id, "startup_s").isEmpty(), "leaderboard row");
                })
                .thenSucceed();
    }
}
