package dev.denfry.promptcritical.sim.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GridBatteryTest {
    private static final double DT = 1;

    @Test void roundTripEfficiencyIsAboutSeventyEightPercent() {
        // разряд на 40 % и заряд обратно до той же степени: КПД цикла = отдано / принято
        double back = 0;
        var c = new GridBattery(1);
        while (c.soc() < 0.9) c.step(DT, -20e3);
        double e0 = c.soc();
        double given = 0;
        while (c.soc() > e0 - 0.4) given += c.step(DT, 20e3) * DT;
        while (c.soc() < e0) back -= c.step(DT, -20e3) * DT;
        assertThat(given / back).isBetween(0.74, 0.82);
    }

    @Test void dischargePowerIsLimitedToQuarterC() {
        var b = new GridBattery(2);
        assertThat(b.step(DT, 1e9)).isCloseTo(2 * 250e3 / 4, within(1e-6));
    }

    @Test void deepCyclesAgeFasterThanShallowOnes() {
        var deep = new GridBattery(1);
        var shallow = new GridBattery(1);
        for (int k = 0; k < 4; k++) cycle(deep, 0.95, 0.15);
        for (int k = 0; k < 16; k++) cycle(shallow, 0.95, 0.75);
        // одинаковая прокачанная энергия (4 × 0,8 = 16 × 0,2), глубокие циклы старят сильнее
        assertThat(deep.cycles()).isGreaterThan(shallow.cycles());
        assertThat(deep.health()).isLessThan(1);
    }

    @Test void chargingNearFullReleasesHydrogen() {
        var b = new GridBattery(1);
        while (b.soc() < 0.95) b.step(DT, -25e3);
        assertThat(b.takeHydrogenKg()).isPositive();
        assertThat(b.takeHydrogenKg()).isZero();
    }

    @Test void emptyBatteryGivesNothing() {
        var b = new GridBattery(1);
        while (b.step(DT, 60e3) > 0) { }
        assertThat(b.step(DT, 10e3)).isZero();
    }

    private static void cycle(GridBattery b, double top, double bottom) {
        while (b.soc() < top) b.step(DT, -25e3);
        while (b.soc() > bottom) b.step(DT, 25e3);
        b.step(DT, -1); // начало заряда закрывает полуцикл
    }
}
