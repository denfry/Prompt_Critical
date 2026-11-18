package dev.denfry.promptcritical.sim.wind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/**
 * Автоматика колеса без генератора: порог включения, запрет пуска без напряжения сети, запрет пуска в шторм до спада
 * 10-минутного среднего, защёлка разноса, поворот на ветер, сохранение. Работа под нагрузкой — в
 * {@code WindTurbineUnitTest}.
 */
class WindTurbineControlTest {
    private static final WindTurbineSpec D30 = WindTurbineSpec.balaklavaD30();
    private static final RotorPerformance PERF = new RotorPerformance(D30.geometry(), D30.airfoil());
    private static final double DT = 0.05;

    private static WindTurbine turbine() {
        WindTurbine t = new WindTurbine(D30, PERF, 50);
        t.setEnabled(true);
        return t;
    }

    private static void run(WindTurbine t, double windMs, double seconds) {
        run(t, windMs, seconds, true);
    }

    private static void run(WindTurbine t, double windMs, double seconds, boolean gridLive) {
        for (int i = 0; i < Math.round(seconds / DT); i++) {
            t.setWind(windMs, 0, 1.225);
            t.control(DT, 0, gridLive);
            t.stepFree(DT);
        }
    }

    @Test
    void belowCutInTheRotorStaysParkedOnTheBrake() {
        WindTurbine t = turbine();
        run(t, 3, 120);
        assertThat(t.state()).isEqualTo(WindTurbine.State.PARKED);
        assertThat(t.brakeApplied()).isTrue();
        assertThat(t.omegaRotor()).isZero();
        assertThat(t.pitchDeg()).isEqualTo(90);
    }

    @Test
    void withoutGridVoltageTheRotorDoesNotStart() {
        WindTurbine t = turbine();
        run(t, 9, 300, false);
        assertThat(t.state()).isEqualTo(WindTurbine.State.PARKED);
        assertThat(t.omegaRotor()).isZero();
    }

    @Test
    void aboveCutInTheBrakeReleasesAndTheRotorRunsUpOnTheStartPitch() {
        WindTurbine t = turbine();
        run(t, 8, 1);
        assertThat(t.state()).isEqualTo(WindTurbine.State.STARTING);
        assertThat(t.brakeApplied()).isFalse();
        // лопасти идут из флюгера на пусковой угол, колесо трогается и разгоняется; программа пуска сводит угол
        // к рабочему к половине синхронной скорости — без этого на пусковом угле колесо встаёт при λ ≈ 4
        while (t.rotorRpm() < 0.85 * 30 && t.state() == WindTurbine.State.STARTING) run(t, 8, 0.05);
        assertThat(t.state()).isEqualTo(WindTurbine.State.STARTING);
        assertThat(t.pitchDeg()).isLessThan(D30.startPitchDeg() / 2); // привод шага догоняет программу
    }

    @Test
    void stormHoldsTheParkedRotorUntilTheTenMinuteMeanFallsBelowRestart() {
        WindTurbine t = turbine();
        t.setWind(22, 0, 1.225);
        run(t, 22, 1200);
        assertThat(t.state()).isEqualTo(WindTurbine.State.PARKED);
        assertThat(t.meanLongMs()).isGreaterThan(D30.cutOutMs());
        run(t, 12, 300); // минутное среднее уже 12 м/с, 10-минутное ещё выше 20
        assertThat(t.state()).isEqualTo(WindTurbine.State.PARKED);
        double waited = 0;
        while (t.state() == WindTurbine.State.PARKED && waited < 1200) {
            run(t, 12, 1);
            waited++;
        }
        assertThat(t.state()).isEqualTo(WindTurbine.State.STARTING);
        assertThat(t.meanLongMs()).isLessThan(D30.restartMs());
    }

    @Test
    void overspeedTripsAndLatchesUntilReset() {
        WindTurbine t = turbine();
        run(t, 3, 10);
        t.setOmegaRotor(1.2 * t.syncOmegaRotor()); // как после сброса нагрузки
        run(t, 3, 0.05);
        assertThat(t.state()).isEqualTo(WindTurbine.State.FAULT);
        assertThat(t.fault()).isEqualTo(WindTurbine.Fault.OVERSPEED);
        assertThat(t.resetFault()).isFalse(); // колесо ещё крутится
        run(t, 3, 120);
        assertThat(t.rotorRpm()).isLessThan(1);
        assertThat(t.resetFault()).isTrue();
        assertThat(t.state()).isEqualTo(WindTurbine.State.PARKED);
    }

    @Test
    void yawDriveFollowsAWindShiftAtItsSlewRate() {
        WindTurbine t = turbine();
        run(t, 3, 1);
        for (int i = 0; i < 200; i++) { // 10 с при ветре, повернувшем на 45°
            t.setWind(3, Math.toRadians(45), 1.225);
            t.control(DT, 0, false);
        }
        assertThat(Math.toDegrees(t.yawRad())).isCloseTo(10 * D30.yawRateDegS(), within(0.1));
        for (int i = 0; i < 2000; i++) {
            t.setWind(3, Math.toRadians(45), 1.225);
            t.control(DT, 0, false);
        }
        assertThat(Math.toDegrees(Math.abs(t.yawErrorRad()))).isLessThan(2.01);
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        WindTurbine t = turbine();
        run(t, 9, 90);
        MutableStateTree tree = new MutableStateTree();
        t.save(new TreeStateWriter(tree));
        WindTurbine c = new WindTurbine(D30, PERF, 50);
        c.load(new TreeStateReader(tree));
        assertThat(c.state()).isEqualTo(t.state());
        assertThat(c.omegaRotor()).isEqualTo(t.omegaRotor());
        assertThat(c.pitchDeg()).isEqualTo(t.pitchDeg());
        assertThat(c.meanLongMs()).isEqualTo(t.meanLongMs());
        assertThat(c.enabled()).isTrue();
    }
}
