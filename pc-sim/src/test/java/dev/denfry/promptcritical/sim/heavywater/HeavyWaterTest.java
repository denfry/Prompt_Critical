package dev.denfry.promptcritical.sim.heavywater;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Тяжёлая вода: свойства, смешение по атомам, GS-завод и вакуумная перегонка против литературы. */
class HeavyWaterTest {
    @Test
    void naturalAbundanceAndDensities() {
        // VSMOW: 155,76 ppm D/H — одна молекула HDO на ~3200
        assertEquals(1.5574e-4, HeavyWater.NATURAL_X, 1e-7);
        assertEquals(998.2, HeavyWater.density20(0), 1e-9);
        assertEquals(1105.3, HeavyWater.density20(1), 1e-9);
        assertEquals(20.028e-3, HeavyWater.molarMassKg(1), 1e-5);
        assertEquals(18.015e-3, HeavyWater.molarMassKg(0), 1e-5);
    }

    /** α обмена вода — H₂S проходит через опорные точки, на холоде больше; α перегонки ≈ 1,05 при 60 °C. */
    @Test
    void separationFactors() {
        assertEquals(2.32, HeavyWater.gsAlpha(303.15), 1e-9);
        assertEquals(1.80, HeavyWater.gsAlpha(403.15), 1e-9);
        assertTrue(HeavyWater.gsAlpha(373.15) > 1.80 && HeavyWater.gsAlpha(373.15) < 2.32);
        double a60 = HeavyWater.distillationAlpha(333.15), a100 = HeavyWater.distillationAlpha(373.15);
        assertTrue(a60 > 1.04 && a60 < 1.06, "α(60 °C) " + a60);
        assertTrue(a100 > 1.02 && a100 < 1.03, "α(100 °C) " + a100);
    }

    /** Литр D₂O и литр H₂O дают воду ~47 % D (по атомам водорода, а не по массе); дейтерий сохраняется. */
    @Test
    void mixingConservesDeuterium() {
        HeavyWaterInventory inv = new HeavyWaterInventory();
        inv.set(1, 1);
        double d0 = inv.deuteriumAtoms();
        inv.add(1, 0);
        double expect = (2 / 20.028) / (2 / 20.028 + 2 / 18.015);
        assertEquals(expect, inv.purity(), 1e-4);
        assertEquals(d0, inv.deuteriumAtoms(), d0 * 1e-9);
        inv.setTritiumAtoms(1e20);
        double mixed = inv.purity();
        inv.remove(0.5);
        assertEquals(1.5, inv.kg(), 1e-12);
        assertEquals(0.75e20, inv.tritiumAtoms(), 1e9, "tritium leaves with the water");
        assertEquals(mixed, inv.purity(), 1e-15, "draining does not change purity");
    }

    @Test
    void inventorySurvivesSaveLoad() {
        HeavyWaterInventory inv = new HeavyWaterInventory();
        inv.set(1234, 0.98);
        inv.setTritiumAtoms(5e19);
        TreeStateWriter w = new TreeStateWriter();
        inv.save(w, "m");
        HeavyWaterInventory copy = new HeavyWaterInventory();
        copy.load(new TreeStateReader(w.root()), "m");
        assertEquals(1234, copy.kg(), 1e-9);
        assertEquals(0.98, copy.purity(), 1e-12);
        assertEquals(5e19, copy.tritiumAtoms(), 1);
    }

    @Test
    void gradesNeverRoundUp() {
        assertEquals(HeavyWaterGrade.REACTOR, HeavyWaterGrade.atMost(0.9975));
        assertEquals(HeavyWaterGrade.DOWNGRADED, HeavyWaterGrade.atMost(0.997));
        assertEquals(HeavyWaterGrade.ENRICHED, HeavyWaterGrade.atMost(0.5));
        assertEquals(HeavyWaterGrade.LIGHT, HeavyWaterGrade.atMost(0.1));
    }

    /** Колонна: материальный баланс по молям и дейтерию точный. */
    @Test
    void columnBalances() {
        IsotopeColumn.Split s = IsotopeColumn.split(0.01, 0.5, 1e4, 0);
        assertTrue(s.works());
        double p = s.productPerFeed(), w = 1 - p;
        assertEquals(0.01, p * 0.5 + w * s.wasteX(), 1e-12);
        assertEquals(p * 0.5 / 0.01, s.recovery(), 1e-12);
        assertFalse(IsotopeColumn.split(0.01, 0.5, 10, 0).works(), "too few plates: the waste would be richer than the feed");
    }

    /**
     * GS: предел извлечения 1 − α_г/α_х ≈ 22 %, реальные заводы — 18–20 % (Miller 2001); продукт — 15 % D; завод на
     * 1000 кг/с питания даёт порядка тысячи тонн D₂O в год, как Брюс (проект 800 т/год).
     */
    @Test
    void girdlerSulfideRecoversAFifthOfTheDeuterium() {
        GirdlerSulfidePlant gs = new GirdlerSulfidePlant();
        assertEquals(0.224, gs.maxRecovery(), 0.002);
        double feed = 1000;
        double product = gs.process(feed, HeavyWater.NATURAL_X);
        assertTrue(gs.lastRecovery() > 0.18 && gs.lastRecovery() <= gs.maxRecovery() * 1.001, "recovery " + gs.lastRecovery());
        double dIn = 2 * HeavyWater.moles(feed, HeavyWater.NATURAL_X) * HeavyWater.NATURAL_X;
        double dOut = 2 * HeavyWater.moles(product, HeavyWaterGrade.ENRICHED.x) * HeavyWaterGrade.ENRICHED.x;
        assertEquals(gs.lastRecovery(), dOut / dIn, 1e-9);
        double d2oTonnesPerYear = dOut / 2 * 20.028e-3 * GirdlerSulfidePlant.FEED_KG_S / feed * 365.25 * 86400 / 1000;
        assertTrue(d2oTonnesPerYear > 600 && d2oTonnesPerYear < 1500, "D2O t/yr " + d2oTonnesPerYear);
    }

    /** Чем холоднее горячая колонна, тем ближе α_г к α_х и тем меньше извлечение. */
    @Test
    void coolerHotTowerRecoversLess() {
        GirdlerSulfidePlant hot = new GirdlerSulfidePlant(), cool = new GirdlerSulfidePlant();
        cool.setHotK(GirdlerSulfidePlant.HOT_MIN_K);
        hot.process(1000, HeavyWater.NATURAL_X);
        cool.process(1000, HeavyWater.NATURAL_X);
        assertTrue(cool.lastRecovery() < hot.lastRecovery(), cool.lastRecovery() + " vs " + hot.lastRecovery());
    }

    /** Доводка: из 15 % и из разбавленной 90 % — реакторная D₂O; из природной воды колонна ничего не даёт. */
    @Test
    void vacuumDistillationFinishesToReactorGrade() {
        WaterDistillationColumn col = new WaterDistillationColumn();
        double fromEnriched = col.process(100, HeavyWaterGrade.ENRICHED.x);
        assertTrue(col.lastRecovery() > 0.8, "recovery from 15 %: " + col.lastRecovery());
        assertTrue(fromEnriched > 10 && fromEnriched < 17, "kg from 100 kg of 15 %: " + fromEnriched);
        col.process(100, HeavyWaterGrade.DOWNGRADED.x);
        assertTrue(col.lastRecovery() > 0.99, "upgrader recovery " + col.lastRecovery());
        assertEquals(0, col.process(100, HeavyWater.NATURAL_X), "natural water needs the GS plant first");
    }
}
