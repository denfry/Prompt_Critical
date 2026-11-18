package dev.denfry.promptcritical.sim.gear;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * Снаряжение §15.8 против чисел: поясной блок PAPR работает смену, фильтр класса 1 выдерживает расчётное время,
 * коэффициенты защиты OSHA; тепловой баланс на морозе, бюджет обогрева костюма и время его батареи.
 */
class ElectricGearTest {
    private static final double AREA = 1.9, MASS = 75;

    @Test
    void respiratorBlowerRunsAboutAShiftOnFourCells() {
        PoweredRespirator r = PoweredRespirator.standard();
        double t = 0, dt = 30;
        while (t < 48 * 3600) {
            r.breathe(dt, 0, 20);
            if (!r.running()) break;
            t += dt;
        }
        // 2,4 Вт с 4,8 В — 0,5 А, около C/8: 4 А·ч на ~8 ч
        assertThat(t / 3600).isBetween(7.0, 9.5);
        assertThat(r.pack().ratedWh()).isCloseTo(19.2, within(0.01));
    }

    @Test
    void poweredMaskCutsTheDoseByAThousandAndUnpoweredByFifty() {
        PoweredRespirator r = PoweredRespirator.standard();
        assertThat(r.breathe(1, 50, 20)).isCloseTo(0.05, within(1e-9));
        r.restore(r.pack().battery().capacityAh(), 0, 20, 0); // батарея села
        assertThat(r.breathe(1, 50, 20)).isCloseTo(1.0, within(1e-9));
        assertThat(r.running()).isFalse();
    }

    @Test
    void classOneFilterHoldsChlorineForTheDesignTime() {
        // EN 14387 класс 1: 1000 ppm при 30 л/мин — 20 мин; при 115 л/мин вентилятора — 20·30/115 ≈ 5,2 мин
        PoweredRespirator r = PoweredRespirator.standard();
        double t = 0;
        while (!r.filterSpent()) {
            r.breathe(1, 1000, 20);
            t++;
        }
        assertThat(t / 60).isCloseTo(20 * 30 / 115.0, within(0.1));
        // после проскока газ идёт под маску
        assertThat(r.breathe(1, 1000, 20)).isEqualTo(1000);
        r.replaceFilter();
        assertThat(r.filterLoad()).isZero();
    }

    @Test
    void workClothesAtMinusTwentyCoolTheCoreIntoMildHypothermiaWithinHours() {
        double d = HeatedSuit.deficitW(AREA, 2, -20, 5, HeatedSuit.WORK_CLOTHES_CLO);
        // R = 0,12 + 0,7·0,155 + 1/(8,3·√5 + 4,7) = 0,2715; сухие 1,9·57/0,2715 ≈ 399 Вт, дыхание 17, обмен 221
        assertThat(d).isCloseTo(194, within(3.0));
        double core = HeatedSuit.CORE_C;
        for (int s = 0; s < 3600; s++) core = HeatedSuit.coreAfter(core, d, MASS, 1);
        assertThat(core).isCloseTo(37 - 194 * 3600 / (MASS * 3470), within(0.05));
        assertThat(HeatedSuit.hypothermiaStage(core)).isEqualTo(1);
        // в тёплой комнате тепло в избытке: ядро не перегревается
        double warm = HeatedSuit.deficitW(AREA, 2, 22, 0.2, HeatedSuit.WORK_CLOTHES_CLO);
        assertThat(warm).isNegative();
        assertThat(HeatedSuit.coreAfter(36, warm, MASS, 36000)).isEqualTo(37);
    }

    @Test
    void heatedSuitCoversTheDeficitForAboutAnHourAndAHalf() {
        double d = HeatedSuit.deficitW(AREA, 2, -20, 5, HeatedSuit.SUIT_CLO);
        assertThat(d).isCloseTo(136, within(3.0)).isLessThan(HeatedSuit.HEATER_MAX_W);
        HeatedSuit s = HeatedSuit.standard();
        double t = 0, dt = 10;
        while (t < 10 * 3600 && s.heat(dt, d, -20) > 0) t += dt;
        // 240 Вт·ч на 24 В, ток ≈ 5,7 А выше паспортного C/5 — Пейкерт отнимает несколько процентов
        assertThat(t / 3600).isBetween(1.2, 1.9);
        // в мягкую погоду обогрев не включается
        assertThat(HeatedSuit.standard().heat(dt, HeatedSuit.deficitW(AREA, 2, 15, 1, HeatedSuit.SUIT_CLO), 15)).isZero();
    }
}
