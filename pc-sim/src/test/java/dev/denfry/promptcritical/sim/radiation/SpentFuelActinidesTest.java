package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import org.junit.jupiter.api.Test;

/**
 * Актиниды ОЯТ (02 §2.8.1, 12 §12.2.5): через 5 лет выдержки при 45 МВт·сут/кг — Pu-238 0,31 кг/т, Am-241 ≈ 0,6 кг/т,
 * Cm-244 0,06 кг/т; их тепло — около 0,4 кВт/т; врастание Am-241 из Pu-241 — тест T12.11.
 */
class SpentFuelActinidesTest {
    private static final double YEAR = 365.25 * 86400, AVOGADRO = 6.02214076e23;

    private static double kg(ActivityInventory inv, Emitter e, double gPerMol) {
        return inv.bq(e) / e.lambda() / AVOGADRO * gPerMol / 1000;
    }

    @Test
    void fiveYearCooledFuelCarriesTheGddActinides() {
        ActivityInventory inv = SpentFuel.discharge(1000, 45, 38, 10.4, true).decayed(5 * YEAR);
        assertThat(kg(inv, Emitter.PU238, 238.05)).isCloseTo(0.31 * Math.exp(-Emitter.PU238.lambda() * 5 * YEAR), within(0.01));
        assertThat(kg(inv, Emitter.AM241, 241.06)).isCloseTo(0.6, within(0.03));
        assertThat(kg(inv, Emitter.CM244, 244.06)).isCloseTo(0.06, within(0.003));
        assertThat(kg(inv, Emitter.PU241, 241.06)).isCloseTo(1.44, within(0.02));
    }

    /** Тепло актинидов через 5 лет: Pu-238 ≈ 0,17, Cm-244 ≈ 0,17, Am-241 ≈ 0,07 кВт/т — около 0,4 кВт/т. */
    @Test
    void actinidesAddAboutFourHundredWattsPerTonneAfterFiveYears() {
        ActivityInventory inv = SpentFuel.discharge(1000, 45, 38, 10.4, true).decayed(5 * YEAR);
        EnumMap<Emitter, Double> act = new EnumMap<>(Emitter.class);
        for (Emitter e : new Emitter[] {Emitter.PU238, Emitter.PU241, Emitter.AM241, Emitter.CM244}) act.put(e, inv.bq(e));
        double w = new ActivityInventory(act, 1000, 10.4).decayHeatW();
        assertThat(w).isCloseTo(410, within(50.0));
        assertThat(w / inv.decayHeatW()).as("actinide share of the decay heat").isBetween(0.15, 0.45);
    }

    /** T12.11: 1 кг Pu с 10 % Pu-241, 5 лет — 21,4 г Am-241. */
    @Test
    void americiumGrowsFromPlutonium241() {
        EnumMap<Emitter, Double> a = new EnumMap<>(Emitter.class);
        a.put(Emitter.PU241, SpentFuel.bq(Emitter.PU241, 241.06, 0.1));
        ActivityInventory later = new ActivityInventory(a, 1, 11.5).decayed(5 * YEAR);
        assertThat(kg(later, Emitter.AM241, 241.06) * 1000).isCloseTo(21.4, within(0.3));
    }
}
