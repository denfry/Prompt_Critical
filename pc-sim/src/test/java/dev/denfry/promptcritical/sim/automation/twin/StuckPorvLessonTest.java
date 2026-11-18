package dev.denfry.promptcritical.sim.automation.twin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun.Status;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/**
 * Урок {@code stuck_porv} (11 §11.3.4, достижение {@code stuck_valve}): клапан КД заклинило открытым, лампа показывает
 * команду «закрыт». Пройти можно, только поверив датчику положения и закрыв запорную задвижку.
 */
class StuckPorvLessonTest {
    static final String P = PressurizerTwinPlant.ID;

    static ScenarioRun play(String id, String program, Consumer<ScenarioRun> operator) {
        ScenarioRun run = ScenarioRun.start(ScenarioCatalog.get(id), program, 7);
        while (run.status() == Status.RUNNING) {
            operator.accept(run);
            run.step();
        }
        return run;
    }

    @Test
    void lampShowsTheCommandWhileThePositionSensorShowsTheValveOpen() {
        try (ScenarioRun run = ScenarioRun.start(ScenarioCatalog.get("stuck_porv"), null, 7)) {
            while (run.session().timeS() < 40) run.step();
            TwinSession s = run.session();
            assertThat(s.measured(PressurizerTwinPlant.PORV_LAMP)).isEqualTo(0.0);
            assertThat(s.measured(PressurizerTwinPlant.PORV_POSITION)).isEqualTo(1.0);
            assertThat(s.plant().read(PressurizerTwinPlant.RELIEF_FLOW)).isPositive();
            while (run.session().timeS() < 60) run.step();
            assertThat(s.measured(PressurizerTwinPlant.TAILPIPE)).isGreaterThan(100);
            assertThat(s.measured(PressurizerTwinPlant.SUBCOOLING)).isLessThan(1);
        }
    }

    @Test
    void levelRisesWhileTheInventoryFalls() {
        try (ScenarioRun run = ScenarioRun.start(ScenarioCatalog.get("stuck_porv"), null, 7)) {
            while (run.session().timeS() < 90) run.step();
            TwinPlant p = run.session().plant();
            double level90 = p.read(PressurizerTwinPlant.LEVEL), inv90 = p.read(PressurizerTwinPlant.INVENTORY);
            while (run.session().timeS() < 200) run.step();
            assertThat(p.read(PressurizerTwinPlant.INVENTORY)).isLessThan(inv90);
            assertThat(p.read(PressurizerTwinPlant.LEVEL)).isGreaterThan(level90);
        }
    }

    @Test
    void trustingTheLampUncoversTheCore() {
        try (ScenarioRun run = play("stuck_porv", null, r -> {
        })) {
            assertThat(run.status()).isEqualTo(Status.FAILED);
            assertThat(run.reason()).isEqualTo("core_uncovered");
        }
    }

    @Test
    void cuttingMakeupBecauseTheLevelIsHighFailsFaster() {
        double[] failedAt = new double[1];
        try (ScenarioRun run = play("stuck_porv", null, r -> {
            if (r.session().plant().read(PressurizerTwinPlant.LEVEL) > 0.6) r.session().command(PressurizerTwinPlant.MAKEUP, 0);
            failedAt[0] = r.session().timeS();
        })) {
            assertThat(run.status()).isEqualTo(Status.FAILED);
        }
        try (ScenarioRun idle = play("stuck_porv", null, r -> {
        })) {
            assertThat(failedAt[0]).isLessThan(idle.session().timeS());
        }
    }

    @Test
    void closingTheBlockValveByThePositionSensorPasses() {
        try (ScenarioRun run = play("stuck_porv", null, r -> {
            TwinSession s = r.session();
            if (s.measured(PressurizerTwinPlant.PORV_POSITION) > 0.5 && s.measured(PressurizerTwinPlant.PORV_LAMP) < 0.5)
                s.command(PressurizerTwinPlant.BLOCK_VALVE, 0);
        })) {
            assertThat(run.status()).isEqualTo(Status.PASSED);
            assertThat(run.score()).isGreaterThan(0.5);
            assertThat(run.session().plant().read(PressurizerTwinPlant.PRESSURE)).isGreaterThan(14);
        }
    }

    @Test
    void lateIsolationStillLosesTooMuchCoolant() {
        try (ScenarioRun run = play("stuck_porv", null, r -> {
            if (r.session().timeS() > 200) r.session().command(PressurizerTwinPlant.BLOCK_VALVE, 0);
        })) {
            assertThat(run.status()).isEqualTo(Status.FAILED);
        }
    }

    /** FAT установки: программа ПЛК закрывает задвижку при расхождении положения и команды. */
    @Test
    void fatPassesWithAPositionDisagreementInterlock() {
        String il = """
                (* задвижка открыта, пока шток клапана не расходится с командой *)
                LD %IX0.0
                ANDN %IX0.1
                STN %QX0.0
                """;
        try (ScenarioRun run = play("fat_pressurizer", il, r -> {
        })) {
            assertThat(run.program().ok()).isTrue();
            assertThat(run.status()).isEqualTo(Status.PASSED);
        }
        try (ScenarioRun run = play("fat_pressurizer", "LD TRUE\nST %QX0.0\n", r -> {
        })) {
            assertThat(run.status()).isEqualTo(Status.FAILED);
        }
    }

    @Test
    void lessonIsDeterministic() {
        double a, b;
        try (ScenarioRun r1 = play("stuck_porv", null, r -> {
        })) {
            a = r1.session().timeS();
        }
        try (ScenarioRun r2 = play("stuck_porv", null, r -> {
        })) {
            b = r2.session().timeS();
        }
        assertThat(a).isEqualTo(b);
        assertThat(TwinPlants.IDS).contains(P);
    }
}
