package dev.denfry.promptcritical.sim.enrichment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Обогащение урана: разделительная работа, центрифуга, идеальный каскад, ядерная безопасность. */
class EnrichmentTest {
    private static final double XF = 0.00711;

    /**
     * ЕРР и питание на кг продукта из природного урана (Benedict, Pigford, Levi, 1981, табл. 12.3; таблицы ЕРР DOE):
     * 4,5 % при отвале 0,25 % — 6,87 ЕРР и 9,22 кг питания; 3 % при 0,2 % — 4,31 ЕРР и 5,48 кг; 90 % при 0,3 % —
     * 193 ЕРР и 218 кг.
     */
    @ParameterizedTest
    @CsvSource({"0.045, 0.0025, 6.87, 9.22", "0.03, 0.002, 4.31, 5.48", "0.90, 0.003, 193, 218.2"})
    void separativeWorkMatchesTables(double xp, double xw, double swu, double feed) {
        assertEquals(swu, Separation.swuPerProduct(xp, XF, xw), swu * 0.005);
        assertEquals(feed, Separation.feedPerProduct(xp, XF, xw), feed * 0.003);
        assertEquals(Separation.feedPerProduct(xp, XF, xw) - 1, Separation.tailsPerProduct(xp, XF, xw), 1e-12);
    }

    @Test
    void valueFunctionAndMaterialBalance() {
        assertEquals(0, Separation.value(0.5), 1e-12);
        assertEquals(Separation.value(0.2), Separation.value(0.8), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> Separation.swuPerProduct(0.005, XF, 0.0025));
    }

    /** Оптимальный отвал: при равных ценах кг урана и ЕРР — около 0,3 %; дешёвое сырьё — отвал богаче. */
    @Test
    void optimalTailsFollowsPrices() {
        double even = Separation.optimalTails(0.045, XF, 100, 100), cheapFeed = Separation.optimalTails(0.045, XF, 30, 100);
        assertTrue(even > 0.0022 && even < 0.0034, "tails at equal prices " + even);
        assertTrue(cheapFeed > even + 0.0005, "cheap feed tails " + cheapFeed);
        double c = Separation.productCost(0.045, XF, even, 100, 100);
        assertTrue(c <= Separation.productCost(0.045, XF, even * 1.1, 100, 100));
        assertTrue(c <= Separation.productCost(0.045, XF, even * 0.9, 100, 100));
    }

    // --- центрифуга ---

    /** Скорость разрыва √(σ/ρ): алюминий ≈ 420, сталь ≈ 500, углепластик ≈ 700 м/с. */
    @Test
    void rotorBurstSpeeds() {
        assertEquals(423, Centrifuge.Rotor.ALUMINIUM.burstSpeed(), 5);
        assertEquals(500, Centrifuge.Rotor.MARAGING_STEEL.burstSpeed(), 5);
        assertEquals(707, Centrifuge.Rotor.CARBON_FIBRE.burstSpeed(), 5);
    }

    /** Дирак: δU ∝ Z·v⁴; машины — от ~1 ЕРР/год (ранние алюминиевые) до 10–25 (углепластик), α 1,1–1,6. */
    @Test
    void diracSeparativePowerAndRealMachines() {
        assertEquals(16, Centrifuge.diracSwuPerS(800, 1) / Centrifuge.diracSwuPerS(400, 1), 1e-9);
        assertEquals(2, Centrifuge.diracSwuPerS(500, 2) / Centrifuge.diracSwuPerS(500, 1), 1e-9);
        double year = Centrifuge.YEAR_S;
        CentrifugeHall h = new CentrifugeHall();
        h.setDesign(CentrifugeHall.Design.ALUMINIUM);
        double al = h.machineSwuPerS() * year;
        h.setDesign(CentrifugeHall.Design.CARBON);
        double cf = h.machineSwuPerS() * year;
        assertTrue(al > 0.5 && al < 3, "aluminium machine SWU/yr " + al);
        assertTrue(cf > 10 && cf < 25, "carbon fibre machine SWU/yr " + cf);
        double alpha = Centrifuge.alpha(h.machineSwuPerS(), Centrifuge.feedPerS(1));
        assertTrue(alpha > 1.1 && alpha < 1.6, "alpha " + alpha);
        assertEquals(1, Centrifuge.circulationEfficiency(1), 1e-12);
        assertTrue(Centrifuge.circulationEfficiency(0.3) < 0.6 && Centrifuge.circulationEfficiency(3.5) < 0.6);
    }

    /** Длинный стальной ротор надкритичен и ломается чаще; у разрывной скорости отказы растут лавинно. */
    @Test
    void failuresGrowTowardsBurst() {
        CentrifugeHall.Design st = CentrifugeHall.Design.MARAGING, cf = CentrifugeHall.Design.CARBON;
        assertTrue(Centrifuge.criticalRatio(st.rotor, 0.85 * st.rotor.burstSpeed(), st.lengthM) > 1, "maraging rotor is supercritical");
        assertTrue(Centrifuge.criticalRatio(cf.rotor, 0.85 * cf.rotor.burstSpeed(), cf.lengthM) < 0.8, "carbon rotor is subcritical");
        double slow = Centrifuge.failuresPerYear(cf.rotor, 0.80 * cf.rotor.burstSpeed(), cf.lengthM);
        double fast = Centrifuge.failuresPerYear(cf.rotor, 0.95 * cf.rotor.burstSpeed(), cf.lengthM);
        assertEquals(0.01, slow, 1e-9);
        assertTrue(fast > 20 * slow, "near burst " + fast);
        assertTrue(Double.isInfinite(Centrifuge.failuresPerYear(cf.rotor, cf.rotor.burstSpeed(), cf.lengthM)));
    }

    // --- каскад ---

    /** Сумма L(α − 1)²/8 по ступеням идеального каскада — его разделительная работа. */
    @Test
    void idealCascadeFlowsCarryTheSeparativeWork() {
        IdealCascade c = new IdealCascade(1.05, 0.045, XF, 0.0025);
        // ступени дискретны (округление вверх) — сходится в пределах 7 %
        assertEquals(Separation.swuPerProduct(0.045, XF, 0.0025), c.swuPerProductFromFlows(), 0.07 * 6.87);
        IdealCascade d = new IdealCascade(1.3, 0.045, XF, 0.0025);
        assertEquals(13.4, d.enrichingStages(), 0.3);
        assertEquals(8.0, d.strippingStages(), 0.3);
        assertEquals(d.stages(), 14 + 8 + 1, 1);
        // самая широкая ступень — ступень питания
        double max = 0;
        int at = -1;
        for (int i = 0; i < d.stages(); i++)
            if (d.flowPerProduct(i) > max) {
                max = d.flowPerProduct(i);
                at = i;
            }
        assertEquals(d.feedStage(), at, 1);
        assertTrue(d.productFor(d.stages() - 1, 1e-4) == 0, "fewer machines than stages");
    }

    @Test
    void uranium234IsEnrichedFasterThan235() {
        IdealCascade c = new IdealCascade(1.3, 0.045, XF, 0.0025);
        double p = c.productU234(0.000054), w = c.tailsU234(0.000054);
        assertTrue(p / 0.000054 > 0.045 / XF, "U-234 enriches more: " + p);
        assertTrue(p > 3e-4 && p < 5e-4, "about 0.04 % U-234 in 4.5 % LEU, below the ASTM C996 limit: " + p);
        assertTrue(w < 0.000054 && w > 0);
        double f = Separation.feedPerProduct(0.045, XF, 0.0025);
        assertEquals(f * 0.000054, p + (f - 1) * w, 1e-12);
    }

    // --- ядерная безопасность ---

    @Test
    void minimumCriticalMassFallsWithEnrichment() {
        assertTrue(Double.isInfinite(Criticality.minCriticalU235Kg(XF)), "natural uranium is never critical");
        assertEquals(2.1, Criticality.minCriticalU235Kg(0.05), 1e-9);
        assertEquals(0.78, Criticality.minCriticalU235Kg(0.93), 1e-9);
        double prev = Double.POSITIVE_INFINITY;
        for (double x = 0.01; x < 1; x += 0.01) {
            double m = Criticality.minCriticalU235Kg(x);
            assertTrue(m <= prev, "monotonic at " + x);
            prev = m;
        }
        assertEquals(0, Criticality.excursionFissions(0.5, 0.9));
        assertTrue(Criticality.excursionFissions(1.0, 0.9) >= 1e17);
        assertEquals(4 * Criticality.doseGy(1e17, 2), Criticality.doseGy(1e17, 1), 1e-12);
    }

    // --- цех ---

    private static CentrifugeHall carbonHall() {
        CentrifugeHall h = new CentrifugeHall();
        h.setDesign(CentrifugeHall.Design.CARBON);
        h.setSpeedFraction(0.8);
        h.setRunning(true);
        return h;
    }

    /** 1000 углепластиковых машин дают 4,5 % продукт; ЕРР по потокам сходится с паспортной мощностью. */
    @Test
    void hallProducesLowEnrichedUranium() {
        CentrifugeHall h = carbonHall();
        Random rng = new Random(1);
        CentrifugeHall.Result r = h.step(3.6, 1000, 1e6, XF, 0.000054, rng);
        assertTrue(r.productKgS() > 0 && !r.tooFewMachines());
        double swuFromProduct = r.productKgS() * Separation.swuPerProduct(0.045, XF, 0.0025);
        assertEquals(r.swuPerS(), swuFromProduct, 0.2 * r.swuPerS());
        assertEquals(r.feedKgS(), r.productKgS() + r.tailsKgS(), 1e-12);
        assertEquals(0.045, h.trapX(), 1e-12);
        assertEquals(0.0025, h.tailsHeldX(), 1e-12);
    }

    /** Блокировка СЦР останавливает отбор у безопасной массы; без неё десублиматор доходит до вспышки. */
    @Test
    void interlockStopsTheTrapAtTheSafeMass() {
        CentrifugeHall safe = carbonHall();
        safe.setProductX(0.20);
        Random rng = new Random(2);
        boolean locked = false;
        for (int i = 0; i < 10000 && !locked; i++) {
            safe.drainTails(1e9);
            locked = safe.step(360, 2000, 1e6, XF, 0.000054, rng).interlocked();
        }
        assertTrue(locked, "interlock engaged");
        assertTrue(safe.trapU235Kg() <= Criticality.safeU235Kg(0.20) + 1e-9, "trap U-235 " + safe.trapU235Kg());
        CentrifugeHall reckless = carbonHall();
        reckless.setProductX(0.20);
        reckless.setInterlock(false);
        double fissions = 0;
        for (int i = 0; i < 10000 && fissions == 0; i++) {
            reckless.drainTails(1e9);
            fissions = reckless.step(360, 2000, 1e6, XF, 0.000054, rng).excursionFissions();
        }
        assertTrue(fissions >= 1e17, "criticality excursion");
        assertFalse(reckless.running(), "the hall stops");
        assertEquals(0, reckless.trapKg());
    }

    @Test
    void overspeedWrecksTheHall() {
        CentrifugeHall h = carbonHall();
        h.setSpeedFraction(1.05);
        CentrifugeHall.Result r = h.step(3.6, 100, 1e6, XF, 0.000054, new Random(3));
        assertEquals(100, r.crashes());
        assertEquals(0, r.productKgS());
    }

    @Test
    void hallStateRoundTrips() {
        CentrifugeHall a = carbonHall();
        a.step(3.6, 1000, 1e6, XF, 0.000054, new Random(4));
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        CentrifugeHall b = new CentrifugeHall();
        b.load(new TreeStateReader(tree));
        assertEquals(a.design(), b.design());
        assertEquals(a.trapKg(), b.trapKg(), 1e-12);
        assertEquals(a.tailsHeldX234(), b.tailsHeldX234(), 1e-15);
        assertTrue(b.running() && b.interlock());
    }
}
