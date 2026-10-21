package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Самописец {@code recording_gauge} (13 §13.6.7). */
class ChartDrumTest {
    /** T-13-35: период 3600 с, шаг 5 с, ступенька давления в 1800 с — точка 360; снятая лента совпадает с тегом. */
    @Test
    void stepLandsAtPoint360() {
        OpsConfig cfg = OpsConfig.defaults();
        ChartDrum drum = ChartDrum.of(cfg, 0, 2.5e6);
        double[] tag = new double[cfg.recordingPoints()];
        for (int k = 0; k < tag.length; k++) {
            double t = k * cfg.recordingSampleS();
            tag[k] = t < 1800 ? 0.8e6 : 1.2e6;
            drum.sample(tag[k]);
        }
        short[] chart = drum.tearOff();
        assertThat(chart).hasSize(720);
        int step = -1;
        for (int k = 1; k < chart.length && step < 0; k++)
            if (drum.decode(chart[k]) - drum.decode(chart[k - 1]) > 1e5) step = k;
        assertThat(step).isCloseTo(360, within(1));
        for (int k = 0; k < chart.length; k++) assertThat(drum.decode(chart[k])).isCloseTo(tag[k], within(50.0));

        // Барабан вращается и после снятия: чистая лента, перо там же.
        assertThat(drum.trace()).containsOnly(ChartDrum.EMPTY);
        assertThat(drum.position()).isZero();
        drum.sample(Double.NaN);
        drum.sample(3e6);
        assertThat(drum.decode(drum.trace()[0])).isNaN();
        assertThat(drum.decode(drum.trace()[1])).isCloseTo(2.5e6, within(1.0));
    }
}
