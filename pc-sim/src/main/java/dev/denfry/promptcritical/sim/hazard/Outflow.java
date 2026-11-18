package dev.denfry.promptcritical.sim.hazard;

/**
 * Истечение через отверстие (09 §9.11.2). Газ — критическое истечение при p₀/pₐ выше ((k+1)/2)^{k/(k−1)} (≈ 1,85),
 * иначе дозвуковое (та же формула сопла, CCPS «Guidelines for Consequence Analysis», 1999, ур. 2.1.16); жидкость — по
 * Бернулли с Cd = {@value #LIQUID_CD}.
 */
public final class Outflow {
    /** Коэффициент расхода газа: 0,62 (острая кромка) … 0,85 (разрыв трубы); берём середину 0,62–0,85 [моё]. */
    public static final double GAS_CD = 0.75;
    public static final double LIQUID_CD = 0.61;

    private Outflow() {
    }

    /** Отношение давлений, выше которого истечение критическое. */
    public static double criticalPressureRatio(double k) {
        return Math.pow((k + 1) / 2, k / (k - 1));
    }

    /**
     * Массовый расход газа, кг/с.
     *
     * @param areaM2     площадь отверстия
     * @param p0Pa       давление в аппарате (абс.)
     * @param paPa       давление снаружи (абс.)
     * @param k          показатель адиабаты
     * @param molarKgMol молярная масса, кг/моль
     * @param z          коэффициент сжимаемости
     * @param t0K        температура в аппарате
     */
    public static double gasKgS(double cd, double areaM2, double p0Pa, double paPa, double k, double molarKgMol, double z, double t0K) {
        if (areaM2 <= 0 || p0Pa <= paPa) return 0;
        // μ/(Z R T₀): ρ₀/p₀ — плотность газа в аппарате, делённая на давление
        double rhoOverP = molarKgMol / (z * Flammable.R * t0K);
        if (p0Pa / paPa >= criticalPressureRatio(k))
            return cd * areaM2 * p0Pa * Math.sqrt(k * rhoOverP * Math.pow(2 / (k + 1), (k + 1) / (k - 1)));
        double r = paPa / p0Pa;
        return cd * areaM2 * p0Pa * Math.sqrt(2 * rhoOverP * k / (k - 1) * (Math.pow(r, 2 / k) - Math.pow(r, (k + 1) / k)));
    }

    /** Расход жидкости через отверстие при перепаде {@code dpPa}, кг/с. */
    public static double liquidKgS(double areaM2, double densityKgM3, double dpPa) {
        if (areaM2 <= 0 || dpPa <= 0) return 0;
        return LIQUID_CD * areaM2 * Math.sqrt(2 * densityKgM3 * dpPa);
    }
}
