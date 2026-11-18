package dev.denfry.promptcritical.sim.pipeline;

/**
 * Резервуар с нефтью или нефтепродуктом (09 §9.8.4): потери от испарения (API MPMS 19.1/19.2, упрощённо) и
 * сигнализация уровня. Малые дыхания — суточный цикл температуры газового пространства:
 * ṁ = k_b·V_v·(p_v·μ/RT)·ΔT_d/T за сутки; большие — вытеснение паров при наливе: ṁ = (p_v·μ/RT)·V̇_in. Плавающая
 * крыша снижает потери на 90–98 %.
 */
public final class StorageTank {
    /** Продукт: давление насыщенных паров при 20 °C, Па; молярная масса паров, кг/моль (API MPMS 19.1, типовые) [сверить]. */
    public enum Product {
        CRUDE(25e3, 0.050), GASOLINE(55e3, 0.066), KEROSENE(1.5e3, 0.130), DIESEL(0.5e3, 0.130), FUEL_OIL(0.1e3, 0.190);

        public final double vapourPressurePa, vapourMolarKgMol;

        Product(double vapourPressurePa, double vapourMolarKgMol) {
            this.vapourPressurePa = vapourPressurePa;
            this.vapourMolarKgMol = vapourMolarKgMol;
        }

        /** Плотность паров в газовом пространстве при {@code tK}, кг/м³. */
        public double vapourDensity(double tK) {
            return vapourPressurePa * vapourMolarKgMol / (Pipeline.R * tK);
        }
    }

    /** Коэффициент малых дыханий k_b: в GDD не задан, 1 [BALANCE]. */
    public static final double KB = 1;
    /** Суточный перепад температуры газового пространства, К [моё: ясный день]. */
    public static final double DAILY_SWING_K = 10;
    /** Снижение потерь плавающей крышей: середина 90–98 % [моё]. */
    public static final double FLOATING_ROOF_REDUCTION = 0.94;
    /** Верхний и верхний аварийный уровни, доли высоты [моё]. */
    public static final double HIGH = 0.90, HIGH_HIGH = 0.95;
    public static final double SECONDS_PER_DAY = 86_400;

    private StorageTank() {
    }

    /** Малые дыхания, кг/с (средние за сутки). */
    public static double standingLossKgS(Product p, double vapourSpaceM3, double tK, boolean floatingRoof) {
        double perDay = KB * Math.max(0, vapourSpaceM3) * p.vapourDensity(tK) * DAILY_SWING_K / tK;
        return perDay / SECONDS_PER_DAY * (floatingRoof ? 1 - FLOATING_ROOF_REDUCTION : 1);
    }

    /** Большие дыхания при наливе {@code inflowM3S}, кг/с. */
    public static double workingLossKgS(Product p, double inflowM3S, double tK, boolean floatingRoof) {
        return p.vapourDensity(tK) * Math.max(0, inflowM3S) * (floatingRoof ? 1 - FLOATING_ROOF_REDUCTION : 1);
    }

    public static boolean high(double fill) {
        return fill >= HIGH;
    }

    public static boolean highHigh(double fill) {
        return fill >= HIGH_HIGH;
    }
}
