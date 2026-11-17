package dev.denfry.promptcritical.sim.automation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LeadAcidBatteryTest {
    private static double hoursToCutoff(double currentA) {
        LeadAcidBattery b = new LeadAcidBattery(100);
        int s = 0;
        while (!b.cutOff() && s < 100 * 3600) {
            b.discharge(currentA, 1);
            s++;
        }
        return s / 3600.0;
    }

    /** Пейкерт, k = 1,25: 100 А·ч током 10 А — 10 ч, током 50 А — 10·(1/5)^1,25 ≈ 1,34 ч, а не 2. */
    @Test
    void peukertShortensHighRateDischarge() {
        assertThat(hoursToCutoff(10)).isCloseTo(10, within(0.1));
        assertThat(hoursToCutoff(50)).isCloseTo(1.34, within(0.03));
    }

    /** Под током напряжение ниже ЭДС; полная — 25,4 В без тока; разряженная отключается и снова включается после заряда. */
    @Test
    void voltageChargeAndCutoff() {
        LeadAcidBattery b = new LeadAcidBattery(100);
        assertThat(b.terminalV(0)).isCloseTo(12 * 2.12, within(1e-9));
        assertThat(b.terminalV(50)).isLessThan(b.terminalV(0));
        while (!b.cutOff()) b.discharge(20, 60);
        assertThat(b.terminalV(1)).isZero();
        double t = 0;
        while (b.soc() < 0.95 && t < 30 * 3600) {
            b.charge(60);
            t += 60;
        }
        assertThat(b.cutOff()).isFalse();
        assertThat(t / 3600).isBetween(10.0, 16.0); // I₁₀ = 10 А, КПД 0,9, ток спадает к 2,25 В/эл.
    }
}
