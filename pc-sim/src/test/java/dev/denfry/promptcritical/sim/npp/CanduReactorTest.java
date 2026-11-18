package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import org.junit.jupiter.api.Test;

/** CANDU T3 против литературы: критичность на природном уране, положительный пустотный коэффициент, перегрузка, тритий. */
class CanduReactorTest {
    static final double BUNDLE_KG = FuelDesign.CANDU_37.uKgPerAssembly(0.96, 0.00711);

    static CanduReactor loaded() {
        CanduReactor r = new CanduReactor();
        for (int i = 0; i < CanduReactor.CHANNELS; i++) assertTrue(r.loadChannel(i, BUNDLE_KG, 0.00711, 0.000054));
        return r;
    }

    /**
     * Природный уран в D₂O критичен (01 §1.7.5, T3): горячая зона без ксенона с запасом 2–7 % — его съедают ксенон (≈ 28 mk)
     * и регуляторы; время генерации ~10⁻³ с против ~2·10⁻⁵ у ВВЭР; βeff ≈ 0,006–0,007 (U-235 без Pu).
     */
    @Test
    void naturalUraniumGoesCritical() {
        CanduReactor r = loaded();
        r.setRods(0, 0);
        r.hotStart(1);
        r.evaluateReactivity();
        double k = r.core().keffStatic();
        assertTrue(k > 1.02 && k < 1.08, "keff " + k);
        assertTrue(r.core().generationTime() > 4e-4 && r.core().generationTime() < 1.5e-3, "Λ " + r.core().generationTime());
        assertTrue(r.core().betaEff() > 0.0055 && r.core().betaEff() < 0.0075, "β " + r.core().betaEff());
        // один пучок ≈ 19 кг урана (CANDU 6 Technical Summary)
        assertTrue(BUNDLE_KG > 18.5 && BUNDLE_KG < 20, "bundle " + BUNDLE_KG);
    }

    /** Полное осушение каналов вносит +10…+15 mk (Rouben, 1999), у свежего топлива — к верхней границе. */
    @Test
    void coolantVoidAddsPositiveReactivity() {
        CanduReactor r = loaded();
        r.setRods(0, 0.5);
        r.hotStart(1);
        double rho = r.evaluateReactivity();
        r.setCoolantVoid(1);
        double voided = (r.evaluateReactivity() - rho) * 1e3;
        assertTrue(voided > 8 && voided < 20, "void reactivity " + voided + " mk");
    }

    /** Перегрузка на ходу: восемь свежих пучков вместо выгоревших поднимают реактивность; работает без останова. */
    @Test
    void onPowerRefuellingAddsReactivity() {
        CanduReactor r = new CanduReactor();
        // зона «выгоревшего» состава: 0,4 % U-235 вместо 0,711 %
        for (int i = 0; i < CanduReactor.CHANNELS; i++) assertTrue(r.loadChannel(i, BUNDLE_KG, 0.004, 0.00005));
        r.setRods(0, 0);
        r.hotStart(1);
        double before = r.evaluateReactivity();
        for (int ch = 0; ch < 12; ch++) {
            var out = r.refuel(ch, CanduReactor.SHIFT_BUNDLES, BUNDLE_KG, 0.00711, 0.000054);
            assertTrue(out != null && Math.abs(out.hm0Kg() - CanduReactor.SHIFT_BUNDLES * BUNDLE_KG) < 1e-6, "discharged 8 bundles");
        }
        assertTrue(r.evaluateReactivity() > before + 1e-3, "refuelling adds reactivity");
    }

    /**
     * Тритий копится в замедлителе как σφN_D·t (t ≪ T½): наработка за час пропорциональна потоку, то есть мощности.
     * У CANDU-6 ≈ 0,05 г/(МВт·год); здесь на мегаватт втрое-впятеро больше D₂O и ниже плотность мощности, поэтому выходит
     * десятки граммов в год на 100 МВт — порядок проверяется широким коридором.
     */
    @Test
    void tritiumBuildsUpWithFlux() {
        CanduReactor full = loaded(), half = loaded();
        full.setRods(0, 0.5);
        half.setRods(0, 0.5);
        full.hotStart(1);
        half.hotStart(0.5);
        full.step(3600);
        half.step(3600);
        assertTrue(full.tritiumBq() > 0, "tritium " + full.tritiumBq());
        assertTrue(Math.abs(full.tritiumBq() / half.tritiumBq() - 2) < 0.1, "∝ flux: " + full.tritiumBq() / half.tritiumBq());
        double gramsPerYear = full.tritiumGrams() * 365.25 * 24;
        assertTrue(gramsPerYear > 1 && gramsPerYear < 200, "g/yr " + gramsPerYear);
    }
}
