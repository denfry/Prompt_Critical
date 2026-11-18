package dev.denfry.promptcritical.sim.automation.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.automation.twin.Fault.SensorFault;
import dev.denfry.promptcritical.sim.automation.twin.Fault.SensorMode;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun.Status;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Цифровой двойник и тренажёр (03d §3d.9.1, 11 §11.3): уроки, испытания FAT, детерминизм, показатели контура. */
class TwinSessionTest {
    /** P-регулятор уровня резервуара на ST: задание 5 м (6,25 В при шкале 0–8 м), насос всегда в работе. */
    static final String TANK_P = """
            PROGRAM level
            %QX0.0 := TRUE;
            %QW0 := 5.0 - (%IW0 - 6.25) * 4.0;
            END_PROGRAM
            """;

    /** Прогнать сценарий; {@code operator} вызывается перед каждым шагом. */
    static ScenarioRun play(String id, String program, Consumer<ScenarioRun> operator) {
        ScenarioRun run = ScenarioRun.start(ScenarioCatalog.get(id), program, 42);
        while (run.status() == Status.RUNNING) {
            operator.accept(run);
            run.step();
        }
        return run;
    }

    static boolean at(ScenarioRun r, double tS) {
        return Math.abs(r.session().timeS() - tS) < TwinPlant.DT / 2;
    }

    @Test
    void tankOverfillFailsUnattendedAndPassesWhenTheOperatorReadsTheFlows() {
        try (ScenarioRun idle = play("tank_overfill", null, r -> {
        })) {
            assertThat(idle.status()).isEqualTo(Status.FAILED);
            assertThat(idle.reason()).isEqualTo("tank_overflow");
            assertThat(idle.session().measured(TankTwinPlant.LEVEL)).as("уровнемер застыл").isCloseTo(5.0, within(0.05));
            assertThat(idle.session().log()).anyMatch(l -> l.contains("FROZEN level_m"));
        }
        try (ScenarioRun ok = play("tank_overfill", null, r -> {
            // приём 360 м³/ч против откачки 200 при «неподвижном» уровне — задвижку прикрыть
            if (at(r, 120)) r.session().command(TankTwinPlant.INLET, 0.5);
        })) {
            assertThat(ok.status()).isEqualTo(Status.PASSED);
            assertThat(ok.score()).isBetween(0.75, 1.0);
        }
    }

    @Test
    void forcedBitDefeatsTheInterlockUntilTheForceIsRemoved() {
        try (ScenarioRun idle = play("forced_bit", null, r -> {
        })) {
            assertThat(idle.status()).isEqualTo(Status.FAILED);
            assertThat(idle.session().forceCount()).isEqualTo(1);
        }
        try (ScenarioRun ok = play("forced_bit", null, r -> {
            if (at(r, 60)) assertThat(r.session().clearForces()).isTrue();
        })) {
            assertThat(ok.status()).as(ok.reason()).isEqualTo(Status.PASSED);
            assertThat(ok.session().plant().read(TankTwinPlant.LEVEL)).as("ПАЗ закрыла приём у сигнализатора")
                    .isCloseTo(TankTwinPlant.LSH_M, within(0.05));
        }
    }

    @Test
    void outOfPhaseClosingShearsTheCouplingAndASynchronousClosingPasses() {
        try (ScenarioRun bad = play("out_of_phase_sync", null, r -> {
            if (Math.abs(r.session().measured(SyncTwinPlant.ANGLE)) > 110 && r.session().timeS() > 1)
                r.session().command(SyncTwinPlant.BREAKER, 1);
        })) {
            assertThat(bad.status()).isEqualTo(Status.FAILED);
            assertThat(bad.reason()).startsWith("out_of_phase");
        }
        try (ScenarioRun ok = play("out_of_phase_sync", null, r -> {
            TwinSession s = r.session();
            double slip = s.measured(SyncTwinPlant.SLIP);
            // задатчик: скольжение к +0,05 Гц (генератор чуть быстрее сети), включать у нуля угла
            if (s.tick() % 10 == 0 && Math.abs(slip - 0.05) > 0.01) s.pulse(SyncTwinPlant.SPEEDER, slip > 0.05 ? -1 : 1);
            if (Math.abs(slip - 0.05) < 0.02 && Math.abs(s.measured(SyncTwinPlant.ANGLE) + 3) < 2) s.command(SyncTwinPlant.BREAKER, 1);
        })) {
            assertThat(ok.status()).as(ok.reason()).isEqualTo(Status.PASSED);
            assertThat(ok.session().plant().read(SyncTwinPlant.TORQUE)).isLessThan(1);
        }
    }

    /** Котёл без ПЛК: слой горит, машина тянет пар — без питания пробка плавится раньше конца урока. */
    @Test
    void boilerLowWaterMeltsThePlugUnlessTheOperatorStopsFiringAndSteam() {
        try (ScenarioRun idle = play("boiler_low_water", null, r -> {
        })) {
            assertThat(idle.status()).isEqualTo(Status.FAILED);
            assertThat(idle.reason()).isEqualTo("fusible_plug_melted");
        }
        try (ScenarioRun ok = play("boiler_low_water", null, r -> {
            if (at(r, 60)) {
                r.session().command(BoilerTwinPlant.FIRING, 0);
                r.session().command(BoilerTwinPlant.STOP_VALVE, 0);
            }
        })) {
            assertThat(ok.status()).as(ok.reason()).isEqualTo(Status.PASSED);
            assertThat(ok.score()).isGreaterThan(0.75);
        }
    }

    /** ПАЗ котла на ST: низший уровень гасит топку и закрывает стопорный вентиль. */
    static final String BOILER_TRIP = """
            PROGRAM boiler
            %QX0.0 := TRUE;
            IF %IX0.0 THEN %MX0 := TRUE; END_IF;
            %QX0.1 := NOT %MX0;
            IF %MX0 THEN %QW0 := 0.0; ELSE %QW0 := 10.0; END_IF;
            END_PROGRAM
            """;

    @Test
    void fatBoilerPassesALowWaterTripAndFailsWithoutIt() {
        try (ScenarioRun ok = play("fat_boiler_i0", BOILER_TRIP, r -> {
        })) {
            assertThat(ok.program().ok()).as(ok.program().errors().toString()).isTrue();
            assertThat(ok.status()).as(ok.reason()).isEqualTo(Status.PASSED);
            assertThat(ok.session().plc().marker(0)).as("ПАЗ сработала").isTrue();
        }
        try (ScenarioRun bad = play("fat_boiler_i0", "PROGRAM boiler\n%QX0.0 := TRUE;\n%QX0.1 := TRUE;\n%QW0 := 10.0;\nEND_PROGRAM\n", r -> {
        })) {
            assertThat(bad.status()).isEqualTo(Status.FAILED);
        }
    }

    @Test
    void fatPassesALevelControllerAndFailsAnEmptyProgram() {
        try (ScenarioRun ok = play("fat_tank_farm", TANK_P, r -> {
        })) {
            assertThat(ok.program().ok()).as(ok.program().errors().toString()).isTrue();
            assertThat(ok.status()).as(ok.reason()).isEqualTo(Status.PASSED);
            LoopMetrics m = ok.session().loops().getFirst();
            assertThat(m.iae()).isPositive();
            assertThat(m.valveTravel()).isPositive();
        }
        try (ScenarioRun none = play("fat_tank_farm", "", r -> {
        })) {
            assertThat(none.status()).isEqualTo(Status.FAILED);
            assertThat(none.reason()).isEqualTo("program_not_loaded");
        }
    }

    @Test
    void sameSeedSameRunEvenWithNoisySensors() {
        List<Double> a = noisyTrace(7), b = noisyTrace(7), c = noisyTrace(8);
        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
    }

    private static List<Double> noisyTrace(long seed) {
        List<Double> out = new ArrayList<>();
        try (TwinSession s = new TwinSession(new TankTwinPlant(5, 0.5, true), IoMap.NONE, seed)) {
            s.inject(new SensorFault(TankTwinPlant.LEVEL, SensorMode.NOISY, 0.05));
            for (int k = 0; k < 200; k++) {
                s.step();
                out.add(s.measured(TankTwinPlant.LEVEL));
            }
        }
        return out;
    }

    @Test
    void sensorFaultsChangeOnlyTheReading() {
        try (TwinSession s = new TwinSession(new TankTwinPlant(5, 1, false), IoMap.NONE, 1)) {
            s.inject(new SensorFault(TankTwinPlant.LEVEL, SensorMode.DRIFT, -0.01));
            s.inject(new SensorFault(TankTwinPlant.INFLOW, SensorMode.OPEN_LOOP, 0));
            for (int k = 0; k < 200; k++) s.step();
            double truth = s.plant().read(TankTwinPlant.LEVEL);
            assertThat(truth).as("приём 360 м³/ч 10 с").isCloseTo(5 + 1.0 / 10, within(1e-6));
            assertThat(s.measured(TankTwinPlant.LEVEL)).isCloseTo(truth - 0.1, within(1e-6));
            assertThat(s.measured(TankTwinPlant.INFLOW)).isNaN();
        }
    }

    @Test
    void stepResponseMetrics() {
        LoopMetrics m = new LoopMetrics("pv", 1, null);
        // ступень 0 → 1 с выбросом до 1,2 и успокоением к 2 с
        double[] pv = {0, 0.5, 1.2, 1.1, 1.0, 1.0};
        for (double v : pv) m.record(0.5, v, Double.NaN);
        assertThat(m.overshoot()).isCloseTo(0.2, within(1e-9));
        assertThat(m.iae()).isCloseTo(0.5 * (1 + 0.5 + 0.2 + 0.1), within(1e-9));
        assertThat(m.settlingTimeS()).isCloseTo(2.0, within(1e-9));
    }
}
