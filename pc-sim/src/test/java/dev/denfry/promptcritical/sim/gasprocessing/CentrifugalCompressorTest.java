package dev.denfry.promptcritical.sim.gasprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Центробежный компрессор 09 §9.8.5: напор по примеру GDD, линия помпажа, глубокий помпаж и его последствия. */
class CentrifugalCompressorTest {
    static final double DT = 0.05;

    @Test
    void polytropicHeadMatchesTheGddExample() {
        // 09 §9.8.5: M = 17,4, k = 1,3, Z₁ = 0,93, 3 → 7,5 МПа, 300 К, η_p = 0,8: H_p = 139,8 кДж/кг, T₂ = 390,8 К, P = 1,75 МВт
        double hp = CentrifugalCompressor.polytropicHead(0.93, 0.0174, 300, 1.3, 0.8, 2.5);
        assertThat(hp / 1e3).isCloseTo(139.8, within(0.2));
        assertThat(CentrifugalCompressor.dischargeTemp(300, 1.3, 0.8, 2.5)).isCloseTo(390.8, within(0.2));
        assertThat(10 * hp / 0.8 / 1e6).isCloseTo(1.75, within(0.01));
    }

    @Test
    void surgeLineIsThePeakOfEverySpeedLineAndAStraightLineInReducedCoordinates() {
        CentrifugalCompressor c = new CentrifugalCompressor();
        for (double n : new double[] {0.6, 0.8, 1.0, 1.1}) {
            double qs = CentrifugalCompressor.surgeFlow(n);
            double peak = CentrifugalCompressor.headCoefficient(qs, n);
            assertThat(CentrifugalCompressor.headCoefficient(qs * 0.97, n)).isLessThan(peak);
            assertThat(CentrifugalCompressor.headCoefficient(qs * 1.03, n)).isLessThan(peak);
            double q2 = CentrifugalCompressor.DP_DESIGN_KPA * qs * qs / (CentrifugalCompressor.P_SUCTION / 1e5);
            assertThat(c.surgeQ2(1 + peak)).isCloseTo(q2, within(1e-9));
        }
        assertThat(CentrifugalCompressor.SURGE_KS).isCloseTo(2.489, within(0.001));
    }

    /** Пуск на 20 с и 100 с работы на заданных клапанах. */
    static CentrifugalCompressor run(double recycle, double line) {
        CentrifugalCompressor c = new CentrifugalCompressor();
        double n = 0;
        for (int k = 0; k < 2400; k++) {
            n = Math.min(1, n + DT / 20);
            c.step(DT, n, recycle, k * DT > 40 ? line : 0);
        }
        return c;
    }

    @Test
    void startingAgainstAClosedRecycleSurgesWithFlowReversalVibrationAndDamage() {
        CentrifugalCompressor c = new CentrifugalCompressor();
        double n = 0, minQ = 0, maxVib = 0;
        long firstCycleStep = -1;
        for (int k = 0; k < 2400; k++) {
            n = Math.min(1, n + DT / 20);
            c.step(DT, n, 0, 0);
            minQ = Math.min(minQ, c.flowM3s());
            maxVib = Math.max(maxVib, c.vibrationMmS());
            if (firstCycleStep < 0 && c.surgeCycles() > 0) firstCycleStep = k;
        }
        assertThat(minQ).as("flow reversal").isLessThan(-0.1);
        // период 0,5–2 с (09 §9.8.5): за минуту установившегося помпажа — от 30 до 120 циклов
        long before = c.surgeCycles();
        for (int k = 0; k < 1200; k++) c.step(DT, 1, 0, 0);
        assertThat(c.surgeCycles() - before).isBetween(30L, 120L);
        assertThat(maxVib).as("ISO 10816 zone D").isGreaterThan(11.2);
        assertThat(c.damage()).isGreaterThan(0.5);
        assertThat(c.surging()).isTrue();
        for (int k = 0; k < 2400; k++) c.step(DT, 1, 0, 0);
        assertThat(c.destroyed()).as("prolonged surge destroys the machine").isTrue();
    }

    @Test
    void openRecycleKeepsTheMachineOutOfSurgeAndTheOperatingPointRightOfTheLine() {
        CentrifugalCompressor c = run(1, 0);
        assertThat(c.surgeCycles()).isZero();
        assertThat(c.surgeRatio()).isLessThan(1);
        assertThat(c.vibrationMmS()).isCloseTo(CentrifugalCompressor.VIB_BASE_MM_S, within(0.01));
        assertThat(c.damage()).isZero();
        assertThat(c.powerW()).isBetween(1e5, 1e6);
    }

    @Test
    void deliversTheDesignFlowIntoTheLineOnceTheRecycleIsClosedAtSpeed() {
        CentrifugalCompressor c = new CentrifugalCompressor();
        double n = 0;
        for (int k = 0; k < 4000; k++) {
            n = Math.min(1, n + DT / 20);
            double t = k * DT;
            // рецикл открыт на пуске, линия открывается на 40-й секунде, рецикл закрывается медленно с 45-й
            c.step(DT, n, t < 45 ? 1 : Math.max(0, 1 - (t - 45) / 100), t > 40 ? 0.85 : 0);
        }
        assertThat(c.surgeCycles()).isZero();
        assertThat(c.lineFlowM3s()).isCloseTo(CentrifugalCompressor.Q_DESIGN, within(0.05));
        assertThat(c.dischargePa() / 1e5).isBetween(8.3, 9.0);
        assertThat(c.surgeRatio()).isLessThan(0.5);
    }

    @Test
    void sameInputsGiveTheSameRunAndStateRoundTrips() {
        CentrifugalCompressor a = run(0, 0.5), b = run(0, 0.5);
        assertThat(a.surgeCycles()).isEqualTo(b.surgeCycles());
        assertThat(a.dischargePa()).isEqualTo(b.dischargePa());
        CentrifugalCompressor c = new CentrifugalCompressor();
        c.restore(a.state());
        a.step(DT, 1, 0.3, 0.5);
        c.step(DT, 1, 0.3, 0.5);
        assertThat(c.flowM3s()).isEqualTo(a.flowM3s());
        assertThat(c.surgeCycles()).isEqualTo(a.surgeCycles());
    }
}
