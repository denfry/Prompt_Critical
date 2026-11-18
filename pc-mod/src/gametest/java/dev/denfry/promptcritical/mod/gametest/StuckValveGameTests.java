package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity;
import dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.TwinPanels;
import dev.denfry.promptcritical.sim.automation.twin.PressurizerTwinPlant;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
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
 * Урок тренажёра {@code stuck_porv} (11 §11.3.4) на пульте: лампа клапана КД показывает «закрыт» (команду), датчик
 * положения — «открыт»; оператор закрывает запорную задвижку кнопкой «1 −» и проходит урок. Прохождение урока даёт
 * скрытое достижение {@code stuck_valve}, другой урок — нет.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StuckValveGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private StuckValveGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void positionSensorFindsTheStuckReliefValve(GameTestHelper h) {
        BlockPos sim = new BlockPos(3, 2, 3), console = new BlockPos(5, 2, 3);
        place(h, sim, PcBlocks.TRAINING_SIMULATOR.get());
        place(h, console, PcBlocks.SIMULATOR_CONSOLE.get());
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    TrainingSimulatorBlockEntity t = h.getBlockEntity(sim);
                    for (int i = 0; i < 10 && !t.lessonId().equals("stuck_porv"); i++) t.nextLesson();
                    h.assertTrue(t.lessonId().equals("stuck_porv"), "the lesson is in the simulator's list");
                    h.assertTrue(t.start(null), "lesson started");
                    ScenarioRun run = t.run();
                    h.assertTrue(run.session().plant().template().equals(PressurizerTwinPlant.ID), "on the pressurizer copy");
                    while (run.session().timeS() < 40) run.step();
                    SimulatorDeskBlockEntity c = h.getBlockEntity(console);
                    int[] ch = new int[TwinPanels.CHANNELS];
                    c.readPanel(ch);
                    h.assertTrue(ch[TwinPanels.SIG0 + 2] == 0, "the lamp shows the command: closed");
                    h.assertTrue(ch[TwinPanels.SIG0 + 3] == 1000, "the position sensor shows the valve open");
                    // оператор верит датчику положения: «1 −» закрывает запорную задвижку
                    c.onPanelButton(player, 0);
                    while (run.status() == ScenarioRun.Status.RUNNING) run.step();
                    h.assertTrue(run.status() == ScenarioRun.Status.PASSED, "lesson passed: " + run.reason());
                })
                .thenWaitUntil(() -> {
                    TrainingSimulatorBlockEntity t = h.getBlockEntity(sim);
                    h.assertTrue(t.run() == null && t.status() == TwinPanels.PASSED, "cabinet reports the pass");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 50)
    public static void passingTheLessonEarnsStuckValve(GameTestHelper h) {
        var p = ContentGameTests.player(h, new BlockPos(2, 2, 2));
        SimAchievements.direct(p, TrainingSimulatorBlockEntity.SCENARIO_PASSED, "tank_overfill", 0.9);
        h.assertFalse(ContentGameTests.done(p, "stuck_valve"), "another lesson does not count");
        SimAchievements.direct(p, TrainingSimulatorBlockEntity.SCENARIO_PASSED, "stuck_porv", 0.8);
        h.assertTrue(ContentGameTests.done(p, "stuck_valve"), "stuck_porv passed: Don't Trust the Lamp");
        h.succeed();
    }
}
