package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Быстрый натриевый реактор T4 против литературы: воспроизводство и коэффициенты реактивности. */
class SodiumFastReactorTest {
    static final double PU = 0.18, HM_KG = 3_000;

    static void run(SodiumFastReactor r, double s) {
        for (int i = 0; i < Math.round(s / SodiumFastReactor.DT); i++) r.step();
    }

    /**
     * КВ МОКС-зоны с экраном из U-238 — 1,1…1,3 (Waltar &amp; Reynolds, гл. 4; БН-600 ≈ 1,2 по проекту с экранами), а
     * внутренний КВ зоны без экрана меньше 1: Pu нарабатывается в экране, а в зоне выгорает.
     */
    @Test
    void breedingRatioInBandAndBlanketBreedsPlutonium() {
        double br = SodiumFastReactor.breedingRatio(PU);
        assertTrue(br > 1.1 && br < 1.3, "BR " + br);
        assertTrue(SodiumFastReactor.internalBreedingRatio(PU) < 1);

        SodiumFastReactor r = new SodiumFastReactor(HM_KG, PU);
        r.hotStart(1);
        run(r, 60);
        assertEquals(1.0, r.power(), 0.05, "критичен и устойчив на номинале");
        assertEquals(SodiumFastReactor.T_OUT_NOM_K, r.outletK(), 15);
        assertTrue(r.blanketPuKg() > 0 && r.corePuKg() < HM_KG * PU, "экран нарабатывает, зона выжигает");
        // 100 МВт(т) — ≈ 0,1 кг делений в сутки; Pu-239 экрана за 60 с ≈ КВ_экр × это × 60/86400.
        double perDay = r.blanketPuKg() * 86_400 / 60;
        assertTrue(perDay > 0.05 && perDay < 0.2, "Pu в экране, кг/сут " + perDay);
    }

    /**
     * β_eff МОКС ≈ 0,0035 и Λ ≈ 4·10⁻⁷ с; Доплер отрицателен, но в несколько раз слабее, чем у ВВЭР (−2…−3 pcm/К);
     * изотермический коэффициент −1…−3 pcm/К [оценка порядка для БН]; полное обезвоживание малой зоны
     * отрицательно и меньше β, а только центра — положительно (Waltar &amp; Reynolds, гл. 13).
     */
    @Test
    void reactivityCoefficientsMatchFastSpectrum() {
        SodiumFastReactor r = new SodiumFastReactor(HM_KG, PU);
        assertEquals(0.0035, r.betaEff(), 0.0006, "β_eff");
        assertEquals(4e-7, r.generationTime(), 1e-9);

        double doppler = SodiumFastReactor.dopplerCoefficient(SodiumFastReactor.FUEL_NOM_K);
        assertTrue(doppler < 0 && doppler > -1e-5, "Доплер " + doppler);
        double iso = SodiumFastReactor.isothermalCoefficient(523);
        assertTrue(iso < -1e-5 && iso > -3e-5, "изотермический " + iso);

        double whole = SodiumFastReactor.voidReactivity(1, 1), centre = SodiumFastReactor.voidReactivity(1, 0);
        assertTrue(whole < 0 && whole > -r.betaEff(), "полное обезвоживание " + whole);
        assertTrue(centre > 0, "центр " + centre);

        // Мощностной эффект (горячая номинальная зона против холодной) отрицателен, порядка 1–3 β [оценка].
        double defect = SodiumFastReactor.feedback(SodiumFastReactor.FUEL_NOM_K,
                (SodiumFastReactor.T_IN_NOM_K + SodiumFastReactor.T_OUT_NOM_K) / 2, SodiumFastReactor.T_IN_NOM_K);
        assertTrue(defect < -r.betaEff() && defect > -3 * r.betaEff(), "мощностной эффект " + defect);

        // Потеря половины отбора тепла: вход греется, выход доходит до уставки 580 °C и АЗ глушит реактор.
        r.hotStart(1);
        r.setSteamDemand(0.5 * SodiumFastReactor.NOMINAL_W);
        run(r, 300);
        assertEquals(SodiumFastReactor.Trip.OUTLET_TEMP, r.trip());
        assertTrue(r.power() < 0.01, "мощность " + r.power());
    }
}
