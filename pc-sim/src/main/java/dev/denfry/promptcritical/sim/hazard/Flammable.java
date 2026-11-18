package dev.denfry.promptcritical.sim.hazard;

/**
 * Горючий газ утечки (09 §9.11.2–9.11.7): молярная масса, концентрационные пределы воспламенения (объёмные доли в
 * воздухе), низшая теплота сгорания и признаки «тяжёлого газа» и детонационной способности.
 * <ul>
 *   <li>Пределы метана (4,4–17 %), пропана (1,7–10,9 %) и H₂S (4,0–44 %) — из 09 §9.11.2 (NFPA 497 [сверить]);
 *   бутана (1,8–8,4 %), водорода (4–75 %) и этилена (2,7–36 %) — справочные данные NFPA 497 [сверить].</li>
 *   <li>Теплота сгорания LHV — справочник (Perry, табл. 2-195) [сверить].</li>
 *   <li>{@code dense} — тяжелее воздуха (μ &gt; 29 г/моль: H₂S 1,19, пропан 1,52): σz шлейфа вдвое меньше, источник у
 *   земли (§9.11.2).</li>
 *   <li>{@code detonable} — водород, этилен (и ацетилен): в сильном загромождении класс TNO 10 (§9.11.7).</li>
 * </ul>
 */
public enum Flammable {
    METHANE(16.043, 0.044, 0.17, 50.0e6, false),
    PROPANE(44.097, 0.017, 0.109, 46.35e6, false),
    BUTANE(58.123, 0.018, 0.084, 45.75e6, false),
    HYDROGEN(2.016, 0.04, 0.75, 120.0e6, true),
    ETHYLENE(28.054, 0.027, 0.36, 47.2e6, true),
    HYDROGEN_SULFIDE(34.081, 0.040, 0.44, 15.2e6, false);

    /** Универсальная газовая постоянная, Дж/(моль·К). */
    public static final double R = 8.314462618;
    /** Молярная масса воздуха, г/моль. */
    public static final double AIR_MOLAR_MASS_G = 28.96;
    /** Опорные условия пересчёта долей в кг/м³: 25 °C, 1 атм. */
    public static final double REF_K = 298.15, REF_PA = 101_325;

    public final double molarMassG, lfl, ufl, lhvJkg;
    public final boolean detonable;

    Flammable(double molarMassG, double lfl, double ufl, double lhvJkg, boolean detonable) {
        this.molarMassG = molarMassG;
        this.lfl = lfl;
        this.ufl = ufl;
        this.lhvJkg = lhvJkg;
        this.detonable = detonable;
    }

    /** Тяжелее воздуха: облако стелется (§9.11.2). */
    public boolean dense() {
        return molarMassG > AIR_MOLAR_MASS_G;
    }

    /** Плотность чистого газа при 25 °C и 1 атм, кг/м³. */
    public double densityKgM3() {
        return REF_PA * molarMassG * 1e-3 / (R * REF_K);
    }

    /** Объёмная доля в воздухе при массовой концентрации {@code kgM3}. */
    public double volumeFraction(double kgM3) {
        return kgM3 / densityKgM3();
    }

    /** НКПР в кг/м³. */
    public double lflKgM3() {
        return lfl * densityKgM3();
    }

    /** ВКПР в кг/м³. */
    public double uflKgM3() {
        return ufl * densityKgM3();
    }

    /** Доля НКПР (как показывает термокаталитический газоанализатор): 1 — нижний предел. */
    public double lelFraction(double kgM3) {
        return volumeFraction(kgM3) / lfl;
    }

    /** Между пределами: смесь горит. */
    public boolean flammable(double kgM3) {
        double x = volumeFraction(kgM3);
        return x >= lfl && x <= ufl;
    }
}
