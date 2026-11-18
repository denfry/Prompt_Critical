package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.PlcBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.scada.OperatorLicenseItem;
import dev.denfry.promptcritical.mod.scada.SimulatorDeskBlockEntity;
import dev.denfry.promptcritical.mod.scada.TrainingSimulatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.TwinPanels;
import dev.denfry.promptcritical.mod.scada.TwinSessions;
import dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TankTwinPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Цифровой двойник и тренажёр (03d §3d.9.1, 11 §11.3): FAT программы настоящего ПЛК на копии и загрузка прошедшей
 * программы в ПЛК; урок за пультом с допуском по журналу шкафа; предел тренажёров на сервер (T11.8).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TwinGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** P-регулятор уровня на ST (как в TwinSessionTest). */
    private static final String TANK_P = "PROGRAM level\n%QX0.0 := TRUE;\n%QW0 := 5.0 - (%IW0 - 6.25) * 4.0;\nEND_PROGRAM\n";

    private TwinGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Прогнать сеанс до итога шагами двойника прямо в тесте (быстрее, чем ждать скорость ×10). */
    private static void runToEnd(ScenarioRun run) {
        while (run.status() == ScenarioRun.Status.RUNNING) run.step();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void workstationRunsFatOnTheTwinAndDeploysThePassedProgram(GameTestHelper h) {
        BlockPos ws = new BlockPos(3, 2, 3), plc = new BlockPos(7, 2, 3);
        place(h, ws, PcBlocks.SIMULATION_WORKSTATION.get());
        place(h, plc, PcBlocks.PLC.get());
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((PlcBlockEntity) h.getBlockEntity(plc)).unit() != null, "PLC in the topology"))
                .thenExecute(() -> {
                    var cpu = ((PlcBlockEntity) h.getBlockEntity(plc)).unit().plc();
                    cpu.setRunKey(false);
                    cpu.load(TANK_P);
                    TwinWorkstationBlockEntity w = h.getBlockEntity(ws);
                    h.assertFalse(w.deploy(player), "untested program is not deployed");
                    w.startFat(player);
                    h.assertTrue(w.run() != null, "FAT running");
                    runToEnd(w.run());
                })
                .thenWaitUntil(() -> {
                    TwinWorkstationBlockEntity w = h.getBlockEntity(ws);
                    h.assertTrue(w.run() == null && w.status() == TwinPanels.PASSED, "FAT passed, status " + w.status());
                })
                .thenExecute(() -> {
                    TwinWorkstationBlockEntity w = h.getBlockEntity(ws);
                    h.assertTrue(w.deploy(player), "passed program deployed");
                    var cpu = ((PlcBlockEntity) h.getBlockEntity(plc)).unit().plc();
                    h.assertTrue(cpu.runKey() && cpu.program().source().equals(TANK_P), "PLC key in RUN with the tested program");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void simulatorLessonAtTheConsoleEarnsALicense(GameTestHelper h) {
        BlockPos sim = new BlockPos(3, 2, 3), console = new BlockPos(5, 2, 3), desk = new BlockPos(7, 2, 3);
        place(h, sim, PcBlocks.TRAINING_SIMULATOR.get());
        place(h, console, PcBlocks.SIMULATOR_CONSOLE.get());
        place(h, desk, PcBlocks.INSTRUCTOR_STATION.get());
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        h.startSequence()
                .thenExecute(() -> {
                    SimulatorDeskBlockEntity instructor = h.getBlockEntity(desk);
                    h.assertFalse(instructor.issueLicense(player, instructor.simulator()), "no license before any lesson");
                    instructor.onPanelButton(player, TwinPanels.I_START);
                    TrainingSimulatorBlockEntity t = h.getBlockEntity(sim);
                    h.assertTrue(t.lessonId().equals("tank_overfill") && t.run() != null, "first lesson running");
                    ScenarioRun run = t.run();
                    SimulatorDeskBlockEntity c = h.getBlockEntity(console);
                    // уровень «стоит», но приход больше расхода: оператор прикрывает приём кнопкой «1 −»
                    while (run.session().timeS() < 120) run.step();
                    for (int k = 0; k < 5; k++) c.onPanelButton(player, 0);
                    h.assertTrue(Math.abs(run.session().operatorCommand(TankTwinPlant.INLET) - 0.5) < 1e-9, "inlet throttled to 0.5");
                    runToEnd(run);
                    h.assertTrue(run.status() == ScenarioRun.Status.PASSED, "lesson passed: " + run.reason());
                })
                .thenWaitUntil(() -> {
                    TrainingSimulatorBlockEntity t = h.getBlockEntity(sim);
                    h.assertTrue(t.run() == null && t.bestOf(player.getUUID()).getOrDefault("tank_overfill", 0.0) >= 0.75,
                            "score in the cabinet's record");
                    h.assertTrue(h.getBlockState(sim).getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING) == false,
                            "cabinet idle after the lesson");
                })
                .thenExecute(() -> {
                    SimulatorDeskBlockEntity instructor = h.getBlockEntity(desk);
                    player.getInventory().clearContent();
                    h.assertTrue(instructor.issueLicense(player, instructor.simulator()), "license issued");
                    ItemStack card = player.getInventory().items.stream().filter(s -> s.is(PcItems.OPERATOR_LICENSE.get())).findFirst()
                            .orElseThrow();
                    var spec = card.get(OperatorLicenseItem.LICENSE_SPEC.get());
                    h.assertTrue(spec != null && spec.levelEnum() == OperatorLicenseItem.Level.TRAINEE && spec.plant().equals(TankTwinPlant.ID),
                            "trainee license for the tank farm: " + spec);
                    player.getInventory().clearContent();
                })
                .thenSucceed();
    }

    /**
     * T11.8: тренажёров не больше {@code content.max_simulators_running} = 2 — лишний не запускается, запущенные
     * работают. Другие тесты партии могут держать место, поэтому проверяется предел, а не номер отказавшего.
     */
    @GameTest(template = LARGE, timeoutTicks = 100, batch = "simulator_limit")
    public static void simulatorsBeyondTheServerLimitAreRefused(GameTestHelper h) {
        BlockPos[] at = {new BlockPos(2, 2, 2), new BlockPos(5, 2, 2), new BlockPos(8, 2, 2)};
        for (BlockPos p : at) place(h, p, PcBlocks.TRAINING_SIMULATOR.get());
        h.startSequence()
                .thenExecute(() -> {
                    TrainingSimulatorBlockEntity[] sims = new TrainingSimulatorBlockEntity[at.length];
                    for (int i = 0; i < at.length; i++) sims[i] = h.getBlockEntity(at[i]);
                    try {
                        int refused = 0;
                        for (TrainingSimulatorBlockEntity s : sims) {
                            boolean ok = s.start(null);
                            if (!ok) refused++;
                            h.assertTrue(ok == (s.run() != null), "a started simulator runs, a refused one does not");
                        }
                        h.assertTrue(refused >= 1, "the third simulator is refused");
                        h.assertTrue(TwinSessions.running(TwinSessions.Kind.SIMULATOR) == 2, "two simulators run");
                    } finally {
                        for (TrainingSimulatorBlockEntity s : sims) s.abort();
                    }
                })
                .thenSucceed();
    }
}
