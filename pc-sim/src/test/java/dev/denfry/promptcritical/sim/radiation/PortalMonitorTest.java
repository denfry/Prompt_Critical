package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Портальный монитор 04b §4b.7.2: порог фон + 5σ за 2 с, чувствительность порядка 1 мкКи Cs-137 на 0,5 м, рост
 * порога с фоном.
 */
class PortalMonitorTest {
    private static final double BG = 0.1;

    private static double cs137USvH(double bq) {
        return DoseRate.totalSvH(Emitter.CS137.source(bq), PortalMonitor.DETECTOR_M, List.of()) * 1e6;
    }

    @Test
    void catchesAMicrocurieOfCaesiumAtHalfAMetre() {
        PortalMonitor m = PortalMonitor.STANDARD;
        assertThat(m.ratio(BG, cs137USvH(37e3))).isBetween(1.1, 1.6);
        // порог — около 30 кБк, как пишет справочник
        assertThat(m.alarms(BG, cs137USvH(34e3))).isTrue();
        assertThat(m.alarms(BG, cs137USvH(24e3))).isFalse();
        // десятая доля микрокюри проходит незамеченной
        assertThat(m.alarms(BG, cs137USvH(3.7e3))).isFalse();
        assertThat(m.alarms(BG, 0)).isFalse();
    }

    @Test
    void thresholdIsFiveSigmaOfTheBackgroundCount() {
        PortalMonitor m = PortalMonitor.STANDARD;
        // 0,1 мкЗв/ч → 1500 имп/с → 3000 отсчётов за 2 с, σ ≈ 54,8
        assertThat(m.thresholdCounts(BG)).isCloseTo(5 * Math.sqrt(3000), within(1e-9));
        assertThat(m.thresholdUSvH(BG)).isCloseTo(5 * Math.sqrt(3000) / 3e4, within(1e-12));
        assertThat(m.ratio(BG, m.thresholdUSvH(BG))).isCloseTo(1, within(1e-12));
    }

    @Test
    void highBackgroundRaisesTheThresholdAsItsSquareRoot() {
        PortalMonitor m = PortalMonitor.STANDARD;
        assertThat(m.thresholdUSvH(10) / m.thresholdUSvH(BG)).isCloseTo(10, within(1e-9));
        double source = cs137USvH(37e3);
        assertThat(m.alarms(BG, source)).isTrue();
        assertThat(m.alarms(10, source)).isFalse();
        // без фона любой отсчёт — тревога, но порог не нулевой
        assertThat(m.thresholdCounts(0)).isEqualTo(1);
    }

    @Test
    void rejectsNonPhysicalSettings() {
        assertThatThrownBy(() -> new PortalMonitor(0, 2, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PortalMonitor(1e4, 2, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    }
}
