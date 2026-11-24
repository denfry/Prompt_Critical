package dev.denfry.promptcritical.sim.hydrogen;

/**
 * Водород как реальный газ (17 §17.3.8): уравнение состояния с вторым вириальным коэффициентом
 * {@code p = c·R·T·(1 + B(T)·c)}, c — моль/м³. До 25 МПа и 250–350 K это в пределах 1 % от NIST (Leachman 2009):
 * при 20 МПа и 20 °C даёт 14,9 кг/м³, как в GDD (идеальный газ — 16,5).
 *
 * <p>B(T) — линия через B(273 K) = 13,7 и B(300 K) = 14,9 см³/моль (Dymond, Smith, «The Virial Coefficients of Pure
 * Gases», 1980), вне 250–350 K температура зажимается.
 */
public final class HydrogenGas {
    /** Универсальная газовая постоянная, Дж/(моль·К). */
    public static final double R = 8.314462618;
    /** Постоянная Фарадея, Кл/моль. */
    public static final double F = 96485.33;
    /** Молярная масса H₂, кг/моль; O₂ и H₂O — для стехиометрии. */
    public static final double M = 2.01588e-3, M_O2 = 31.998e-3, M_H2O = 18.015e-3;
    /** Нормальный кубометр (0 °C, 101,325 кПа), моль. */
    public static final double MOL_PER_NM3 = 1000 / 22.414;
    /** Низшая и высшая теплота сгорания, Дж/кг (120 и 141,8 МДж/кг). */
    public static final double LHV = 120.0e6, HHV = 141.8e6;
    /** Показатель адиабаты и теплоёмкости идеального газа при 300 K, Дж/(моль·К). */
    public static final double GAMMA = 1.405, CP = 28.84, CV = CP - R;

    private HydrogenGas() {
    }

    /** Второй вириальный коэффициент, м³/моль. */
    public static double virialB(double tempK) {
        double t = Math.max(250, Math.min(350, tempK));
        return (27.03 - 3640 / t) * 1e-6;
    }

    /** Молярная плотность при давлении {@code pa} и температуре, моль/м³ (корень квадратного уравнения состояния). */
    public static double molarDensity(double pa, double tempK) {
        if (pa <= 0) return 0;
        double rt = R * tempK, b = virialB(tempK);
        return (-rt + Math.sqrt(rt * rt + 4 * b * rt * pa)) / (2 * b * rt);
    }

    /** Плотность, кг/м³. */
    public static double density(double pa, double tempK) {
        return molarDensity(pa, tempK) * M;
    }

    /** Давление по молярной плотности, Па. */
    public static double pressure(double molPerM3, double tempK) {
        return molPerM3 * R * tempK * (1 + virialB(tempK) * molPerM3);
    }

    /** Коэффициент сжимаемости Z = p/(cRT). */
    public static double z(double pa, double tempK) {
        double c = molarDensity(pa, tempK);
        return c > 0 ? 1 + virialB(tempK) * c : 1;
    }

    /**
     * Критическое (звуковое) истечение через отверстие, моль/с: ṁ = Cd·A·p·√(γM/(RT))·(2/(γ+1))^((γ+1)/(2(γ−1))).
     * Для давлений выше 1,9 атм над наружным; ниже — дозвуковое, здесь грубо по корню из перепада.
     */
    public static double orificeMolS(double areaM2, double pa, double outsidePa, double tempK) {
        if (areaM2 <= 0 || pa <= outsidePa) return 0;
        double crit = Math.pow(2 / (GAMMA + 1), (GAMMA + 1) / (2 * (GAMMA - 1)));
        double choked = 0.62 * areaM2 * pa * Math.sqrt(GAMMA / (M * R * tempK)) * crit;
        double ratio = outsidePa / pa;
        double critRatio = Math.pow(2 / (GAMMA + 1), GAMMA / (GAMMA - 1));
        return ratio <= critRatio ? choked : choked * Math.sqrt((1 - ratio) / (1 - critRatio));
    }

    public static double nm3(double mol) {
        return mol / MOL_PER_NM3;
    }
}
