package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import org.junit.jupiter.api.Test;

/** Кипящий реактор T2b против литературы: паровой коэффициент и регулирование мощности расходом рециркуляции. */
class BoilingWaterReactorTest {
    static final double DT = BoilingWaterReactor.DT;

    /** 19 ТВС 3,6 % на номинале, регулирующие стержни — на критику (бора у BWR нет). */
    static BoilingWaterReactor nominal() {
        BoilingWaterReactor r = new BoilingWaterReactor();
        double kg = FuelDesign.VVER_1000.uKgPerAssembly(0.96, 0.036);
        for (int i = 0; i < BoilingWaterReactor.POSITIONS; i++) assertTrue(r.load(i, FuelDesign.VVER_1000, kg, 0.036, 0.0003));
        r.hotStart(1);
        double lo = 0, hi = 1;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            r.setRods(0, m);
            if (r.evaluateReactivity() > 0) lo = m;
            else hi = m;
        }
        r.setRods(0, (lo + hi) / 2);
        r.hotStart(1);
        return r;
    }

    static void run(BoilingWaterReactor r, double s) {
        for (int i = 0; i < Math.round(s / DT); i++) r.step(0);
    }

    /**
     * Паровой коэффициент отрицательный, и именно он, а не Доплер, держит мощность: пар вытесняет замедлитель. Доля пара
     * в зоне и паросодержание на выходе — как у BWR (Lahey, Moody, гл. 1: средняя по зоне ≈ 0,4, на выходе x ≈ 0,13–0,15).
     * По величине коэффициент в несколько раз больше −80…−150 pcm/% GDD 01 §1.7.5: зона из 19 ТВС мала, и плотность воды
     * меняет утечку сильнее, чем k∞ ячейки (как MTC у ВВЭР, решение R46).
     */
    @Test
    void voidCoefficientIsNegativeAndDominant() {
        BoilingWaterReactor r = nominal();
        assertTrue(r.voidFraction() > 0.25 && r.voidFraction() < 0.5, "core void " + r.voidFraction());
        assertTrue(r.exitQuality() > 0.10 && r.exitQuality() < 0.18, "exit quality " + r.exitQuality());
        double perPercent = r.voidCoefficient() * 1e5;
        assertTrue(perPercent < -80 && perPercent > -1000, "void coefficient " + perPercent + " pcm/%");
        double doppler = r.core().dopplerCoefficient() * 1e5;
        assertTrue(doppler < -2 && doppler > -6, "Doppler " + doppler);
        // 1 % пара весит больше, чем 10 К топлива
        assertTrue(Math.abs(perPercent) > 10 * Math.abs(doppler));
    }

    /**
     * Мощность за расходом рециркуляции (Lahey, Moody, гл. 9; карта мощность–расход BWR): на одной стержневой линии
     * снижение расхода до 70 % уводит мощность в пределы W¹…W^0,5 — 0,70…0,84; возврат расхода возвращает мощность. Без
     * АЗ, давление держит регулятор турбины; расход поднимают плавно. N-16 в паре у турбины пропорционален мощности.
     */
    @Test
    void powerFollowsRecirculationFlow() {
        BoilingWaterReactor r = nominal();
        run(r, 30);
        double full = r.power(), n16Full = r.turbineN16Bq();
        assertEquals(1.0, full, 0.04);
        assertTrue(n16Full > 1e10, "N-16 at the turbine " + n16Full);
        for (int i = 0; i < BoilingWaterReactor.PUMPS; i++) r.setPumpDrive(i, 0.7);
        run(r, 120);
        double reduced = r.power() / full;
        assertTrue(reduced > 0.68 && reduced < Math.sqrt(0.7), "power at 70 % flow " + reduced);
        assertEquals(BoilingWaterReactor.P_NOMINAL, r.pressure(), 0.05e6);
        assertEquals(reduced, r.turbineN16Bq() / n16Full, 0.05);
        // обратно — как оператор, 0,5 %/с: рывок расхода даёт короткий период и АЗ (отказ регулятора рециркуляции)
        for (int k = 0; k < 600; k++) {
            for (int i = 0; i < BoilingWaterReactor.PUMPS; i++) r.setPumpDrive(i, Math.min(1, 0.7 + 0.005 * (k + 1) * 0.1));
            run(r, 0.1);
        }
        run(r, 60);
        assertEquals(full, r.power(), 0.03);
        assertEquals(BoilingWaterReactor.Trip.NONE, r.trip());
    }
}
