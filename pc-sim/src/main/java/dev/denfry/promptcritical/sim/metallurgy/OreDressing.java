package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Подготовка железорудного сырья (08 §8.5.1): магнитная сепарация {@code magnetic_separator} и агломерация
 * {@code sinter_strand}. Состав — Fe, SiO₂, CaO массовыми долями; пустая порода концентрата — SiO₂.
 */
public final class OreDressing {
    /** Fe в магнетите Fe₃O₄. */
    public static final double MAGNETITE_FE = 3 * 55.845 / (3 * 55.845 + 4 * 15.999);
    /** Fe концентрата и извлечение магнитной фракции (64–68 %, 80–90 %). */
    public static final double CONCENTRATE_FE = 0.66, MAGNETIC_RECOVERY = 0.85;
    /** CaO из известняка CaCO₃. */
    public static final double LIMESTONE_CAO = 56.077 / 100.087;
    /** Коксовая мелочь: зола и SiO₂ в золе. */
    public static final double BREEZE_ASH = 0.12, ASH_SIO2 = 0.5;
    /** Окно мелочи, кг/т концентрата (50–60 по GDD; спекается при 45–70 [BALANCE]). */
    public static final double BREEZE_MIN_KG_T = 45, BREEZE_MAX_KG_T = 70;

    private OreDressing() {}

    /**
     * Магнитная сепарация: в концентрат идёт 85 % железа магнитных минералов; гематит не магнитен.
     *
     * @param feFrac        Fe питания
     * @param magneticShare доля Fe в магнетите (1 — магнетитовый кварцит, 0 — гематит)
     */
    public static Split magneticSeparation(double feedKg, double feFrac, double magneticShare) {
        if (!(feedKg >= 0 && feFrac >= 0 && feFrac <= MAGNETITE_FE && magneticShare >= 0 && magneticShare <= 1))
            throw new IllegalArgumentException("feed");
        double fe = MAGNETIC_RECOVERY * magneticShare * feFrac * feedKg;
        double conc = Math.min(feedKg, fe / CONCENTRATE_FE);
        double concFe = conc > 0 ? fe / conc : 0;
        double tail = feedKg - conc;
        double tailFe = tail > 0 ? (feFrac * feedKg - fe) / tail : 0;
        return new Split(conc, concFe, Math.max(0, 1 - concFe / MAGNETITE_FE), tail, tailFe);
    }

    /** Известняк для основности CaO/SiO₂ = B агломерата (мелочь не учитывается), кг. */
    public static double limestoneFor(double oreKg, double sio2Frac, double caoFrac, double basicity) {
        return Math.max(0, (basicity * sio2Frac - caoFrac) * oreKg / LIMESTONE_CAO);
    }

    /**
     * Агломерация: известняк обжигается (CO₂ уходит), мелочь выгорает, её зола остаётся.
     *
     * @return агломерат; {@code weak} — мелочи вне окна (недопёк или оплавление: много возврата)
     */
    public static Sinter sinter(double oreKg, double feFrac, double sio2Frac, double caoFrac, double breezeKg,
            double limestoneKg) {
        if (!(oreKg > 0 && breezeKg >= 0 && limestoneKg >= 0)) throw new IllegalArgumentException("sinter charge");
        double ash = BREEZE_ASH * breezeKg;
        double cao = caoFrac * oreKg + LIMESTONE_CAO * limestoneKg;
        double sio2 = sio2Frac * oreKg + ASH_SIO2 * ash;
        double m = oreKg + LIMESTONE_CAO * limestoneKg + ash;
        double perT = breezeKg / oreKg * 1000;
        return new Sinter(m, feFrac * oreKg / m, sio2 / m, cao / m, sio2 > 0 ? cao / sio2 : 0,
                perT < BREEZE_MIN_KG_T || perT > BREEZE_MAX_KG_T);
    }

    /** Продукты сепарации; SiO₂ концентрата — остаток до Fe₃O₄. */
    public record Split(double concentrateKg, double concentrateFe, double concentrateSiO2, double tailingsKg,
            double tailingsFe) {}

    /** Агломерат {@code sinter}: 52–58 % Fe, основность 1,2–1,8. */
    public record Sinter(double sinterKg, double feFrac, double sio2Frac, double caoFrac, double basicity,
            boolean weak) {}
}
