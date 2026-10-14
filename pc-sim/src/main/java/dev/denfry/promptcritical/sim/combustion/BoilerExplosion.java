package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Энергия взрыва котла (07 §7.9.4): разрушение обечайки или жаровой трубы — мгновенное вскипание перегретой воды.
 * Работа изоэнтропического расширения содержимого до атмосферы w = u₁ − u₂(p_атм, s₁) (закрытая система,
 * равновесная смесь); доля в ударную волну f_blast переводится в ТНТ-эквивалент для {@code DamageRequest(BLAST)}
 * (04d §4d.2.4). Насыщенная вода 1,2 МПа даёт ≈ 64 кДж/кг: 10 т → 0,64 ГДж, при f_blast = 0,3 — 46 кг ТНТ.
 * Считается один раз в момент разрушения, не в горячем цикле.
 */
public final class BoilerExplosion {
    /** Теплота взрыва ТНТ, Дж/кг. */
    public static final double TNT_J_PER_KG = 4.184e6;
    /** Доля энергии в ударную волну, {@code boiler.explosion_blast_fraction} [BALANCE]. */
    public static final double BLAST_FRACTION = 0.3;
    public static final double ATMOSPHERE_PA = 101_325;

    private BoilerExplosion() {}

    /**
     * Удельная работа изоэнтропического расширения равновесной смеси, Дж/кг.
     *
     * @param quality массовое паросодержание x ∈ [0, 1] (0 — насыщенная вода)
     */
    public static double expansionWorkJPerKg(WaterProperties w, double pressurePa, double quality, double ambientPa) {
        if (!(pressurePa > ambientPa)) return 0;
        double x = Math.max(0, Math.min(1, quality));
        double[] s1 = new double[17], s2 = new double[17];
        w.saturationP(pressurePa, s1);
        w.saturationP(ambientPa, s2);
        double v1 = s1[1] + x * (s1[2] - s1[1]);
        double h1 = s1[3] + x * (s1[4] - s1[3]);
        double s = s1[7] + x * (s1[8] - s1[7]);
        double u1 = h1 - pressurePa * v1;
        double x2 = Math.max(0, Math.min(1, (s - s2[7]) / (s2[8] - s2[7])));
        double v2 = s2[1] + x2 * (s2[2] - s2[1]);
        double h2 = s2[3] + x2 * (s2[4] - s2[3]);
        return Math.max(0, u1 - (h2 - ambientPa * v2));
    }

    /** Полная энергия расширения содержимого массой {@code massKg} с паросодержанием {@code quality}, Дж. */
    public static double energyJ(WaterProperties w, double pressurePa, double massKg, double quality) {
        return massKg * expansionWorkJPerKg(w, pressurePa, quality, ATMOSPHERE_PA);
    }

    /** ТНТ-эквивалент ударной волны, кг. */
    public static double tntEquivalentKg(double energyJ, double blastFraction) {
        return energyJ * blastFraction / TNT_J_PER_KG;
    }
}
