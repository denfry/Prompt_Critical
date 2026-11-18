package dev.denfry.promptcritical.sim.electronics;

/**
 * Монокристалл по Чохральскому (11 §11.5.2, 15 §15.6).
 *
 * <p><b>Окно роста.</b> Бездислокационный рост держится при скорости вытягивания 0,5–2 мм/мин и вращении затравки 5–30
 * об/мин (Zulehner, Huber, «Czochralski-grown silicon», 1982; ключи {@code content.cz_*}). Быстрее — фронт
 * кристаллизации переохлаждается, кристалл срывается в поликристалл; медленнее — растёт диаметр и копятся примеси у
 * фронта; без вращения — асимметрия теплового поля.
 *
 * <p><b>Шейль.</b> Примесь с коэффициентом распределения k &lt; 1 оттесняется в расплав, и к хвосту слитка её больше:
 * C_s(f) = k·C₀·(1 − f)^{k−1}, f — закристаллизованная доля. Бор k = 0,8 — слиток почти однороден; фосфор k = 0,35
 * — сильный градиент (Trumbore 1960). Удельное сопротивление p-кремния — 1/(q·N·μ_p), подвижность дырок по
 * Caughey–Thomas: μ = μ_min + (μ_max − μ_min)/(1 + (N/N_ref)^α), μ_min 54,3, μ_max 406,9 см²/(В·с), N_ref 2,35·10¹⁷,
 * α 0,88 (Arora, Hauser, Roulston 1982).
 */
public final class Czochralski {
    public static final double V_MIN_MM_MIN = 0.5, V_MAX_MM_MIN = 2.0, RPM_MIN = 5, RPM_MAX = 30;
    public static final double K_BORON = 0.8, K_PHOSPHORUS = 0.35, Q = 1.602176634e-19;
    /** Плотность кремния, г/см³. */
    public static final double SI_DENSITY = 2.329;

    private Czochralski() {
    }

    /** Растёт ли монокристалл при этих скорости и вращении. */
    public static boolean singleCrystal(double pullMmMin, double rpm) {
        return pullMmMin >= V_MIN_MM_MIN && pullMmMin <= V_MAX_MM_MIN && rpm >= RPM_MIN && rpm <= RPM_MAX;
    }

    /** Концентрация примеси в кристалле на закристаллизованной доле {@code f} (Шейль). */
    public static double scheil(double k, double c0, double f) {
        return k * c0 * Math.pow(Math.max(1e-9, 1 - Math.min(f, 1 - 1e-9)), k - 1);
    }

    /** Подвижность дырок, см²/(В·с). */
    public static double holeMobility(double nCm3) {
        return 54.3 + (406.9 - 54.3) / (1 + Math.pow(nCm3 / 2.35e17, 0.88));
    }

    /** Удельное сопротивление p-кремния, Ом·см. */
    public static double resistivityP(double nCm3) {
        return nCm3 > 0 ? 1 / (Q * nCm3 * holeMobility(nCm3)) : Double.POSITIVE_INFINITY;
    }

    /** Длина слитка массой {@code kg} и диаметром {@code dMm}, мм. */
    public static double ingotLengthMm(double kg, double dMm) {
        double area = Math.PI * Math.pow(dMm / 20, 2);
        return kg * 1000 / SI_DENSITY / area * 10;
    }

    /**
     * Доля слитка, сопротивление которой в пределах ±{@code tolerance} от сопротивления в начале (голова идёт в дело,
     * хвост — в оборот): доля f, где (1 − f)^{k−1} ≤ 1 + tolerance.
     */
    public static double usableFraction(double k, double tolerance) {
        if (k >= 1) return 1;
        // ρ ∝ 1/C при постоянной подвижности: C(f)/C(0) = (1 − f)^{k−1} ≤ 1/(1 − tol)
        double ratio = 1 / (1 - Math.min(0.99, tolerance));
        return Math.min(1, 1 - Math.pow(ratio, 1 / (k - 1)));
    }
}
