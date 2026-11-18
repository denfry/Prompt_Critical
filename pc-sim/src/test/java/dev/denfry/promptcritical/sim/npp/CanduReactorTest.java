package dev.denfry.promptcritical.sim.npp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
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

    /** Каландр и контур: ~сотня тонн замедлителя на 97 каналов (у CANDU 6 — 265 т на 380), несколько тонн в контуре. */
    @Test
    void heavyWaterInventoriesHaveRealisticSize() {
        CanduReactor r = new CanduReactor();
        assertTrue(r.moderatorCapacityKg() > 60e3 && r.moderatorCapacityKg() < 200e3, "moderator kg " + r.moderatorCapacityKg());
        assertTrue(r.coolantCapacityKg() > 1e3 && r.coolantCapacityKg() < 10e3, "coolant kg " + r.coolantCapacityKg());
        assertEquals(1, r.moderatorLevel(), 1e-12);
    }

    /** Без замедлителя зона из природного урана глубоко подкритична; залитая — критична. */
    @Test
    void dryCalandriaIsSubcritical() {
        CanduReactor r = CanduReactor.dry();
        for (int i = 0; i < CanduReactor.CHANNELS; i++) assertTrue(r.loadChannel(i, BUNDLE_KG, 0.00711, 0.000054));
        r.setRods(0, 0);
        assertTrue(r.evaluateReactivity() < -0.2, "dry rho " + r.evaluateReactivity());
        assertEquals(r.coolantCapacityKg(), r.addCoolant(1e9, HeavyWater.REACTOR_GRADE_X), 1e-6, "fills up to the brim");
        r.addModerator(0.5 * r.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        double half = r.evaluateReactivity();
        r.addModerator(1e9, HeavyWater.REACTOR_GRADE_X);
        double full = r.evaluateReactivity();
        assertTrue(half < full - 0.01, "half level " + half + " vs full " + full);
        assertTrue(full > 0, "full calandria is critical without rods: " + full);
    }

    /**
     * Лёгкая вода в замедлителе — сильный поглотитель: протий захватывает в 640 раз сильнее дейтерия, и потеря одного
     * атомного процента чистоты втрое увеличивает поглощение замедлителя — около десятка мк, больше запаса на
     * перегрузку. Теплоноситель занимает малую долю ячейки, его чистота весит много меньше.
     */
    @Test
    void moderatorPurityCostsAboutTenMilliK() {
        CanduReactor r = loaded();
        r.setRods(0, 0.5);
        r.hotStart(1);
        double rho = r.evaluateReactivity();
        r.moderator().set(r.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X - 0.01);
        double modLoss = (rho - r.evaluateReactivity()) * 1e3;
        assertTrue(modLoss > 8 && modLoss < 40, "moderator purity coefficient " + modLoss + " mk/at.%");
        r.moderator().set(r.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        r.coolant().set(r.coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X - 0.01);
        double coolLoss = (rho - r.evaluateReactivity()) * 1e3;
        assertTrue(coolLoss > 0 && coolLoss < modLoss / 3, "coolant purity coefficient " + coolLoss + " mk/at.%");
    }

    /** Течь лёгкой воды разбавляет замедлитель при постоянном уровне, реактивность падает. */
    @Test
    void lightWaterIngressDowngradesTheModerator() {
        CanduReactor r = loaded();
        r.setRods(0, 0.5);
        r.hotStart(1);
        double rho = r.evaluateReactivity();
        r.setLightWaterIngress(1);
        r.step(600);
        assertTrue(r.moderator().purity() < HeavyWater.REACTOR_GRADE_X - 0.004, "purity " + r.moderator().purity());
        assertEquals(r.moderatorCapacityKg(), r.moderatorKg(), 1e-6, "overflow keeps the level");
        assertTrue(r.evaluateReactivity() < rho - 0.005, "downgrading costs reactivity");
    }

    /** Слив уносит тритий с водой; состояние D₂O переживает сохранение, старые сохранения считаются залитыми. */
    @Test
    void drainCarriesTritiumAndStateSurvivesSaveLoad() {
        CanduReactor r = loaded();
        r.setRods(0, 0.5);
        r.hotStart(1);
        r.step(3600);
        double perKg = r.moderatorTritiumBqPerKg(), cap = r.moderatorCapacityKg();
        assertTrue(r.coolantTritiumBq() > 0 && r.coolantTritiumBq() < r.tritiumBq(), "coolant tritium " + r.coolantTritiumBq());
        assertEquals(1000, r.drainModerator(1000), 1e-9);
        assertEquals(perKg, r.moderatorTritiumBqPerKg(), perKg * 1e-9, "specific activity is unchanged by draining");
        assertEquals(perKg * (cap - 1000), r.tritiumBq(), perKg * cap * 1e-9);
        r.addModerator(1000, 0.9);
        TreeStateWriter w = new TreeStateWriter();
        r.save(w);
        CanduReactor copy = CanduReactor.dry();
        copy.load(new TreeStateReader(w.root()));
        assertEquals(r.moderatorKg(), copy.moderatorKg(), 1e-6);
        assertEquals(r.moderator().purity(), copy.moderator().purity(), 1e-12);
        assertEquals(r.tritiumBq(), copy.tritiumBq(), r.tritiumBq() * 1e-9);
        CanduReactor legacy = CanduReactor.dry();
        legacy.load(new TreeStateReader(new TreeStateWriter().root()));
        assertEquals(legacy.moderatorCapacityKg(), legacy.moderatorKg(), 1e-6, "pre-D2O saves load full");
    }
}
