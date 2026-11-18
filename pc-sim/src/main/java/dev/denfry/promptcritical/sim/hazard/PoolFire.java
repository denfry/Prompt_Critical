package dev.denfry.promptcritical.sim.hazard;

/**
 * Пожар пролива (09 §9.11.4): скорость выгорания по Бабраускасу m″ = m″∞(1 − e^{−kβD}), мощность Q = m″·A·ΔHc·χ,
 * высота пламени по Хескестаду L = 0,235·Q^{2/5} − 1,02·D (Q в кВт), излучение — точечный источник с χr = 0,3
 * (0,15 для дымных пожаров диаметром больше 10 м).
 */
public final class PoolFire {
    /** Полнота сгорания χ. */
    public static final double COMBUSTION_EFFICIENCY = 0.85;
    public static final double RADIANT_FRACTION = 0.3, SMOKY_RADIANT_FRACTION = 0.15, SMOKY_DIAMETER_M = 10;

    /** Топливо пролива: m″∞, кг/(м²·с); kβ, м⁻¹; ΔHc, Дж/кг (таблица §9.11.4). */
    public enum Fuel {
        GASOLINE(0.055, 2.1, 43.7e6),
        KEROSENE(0.039, 3.5, 43.2e6),
        DIESEL(0.045, 2.1, 44.4e6),
        /** Нефть: m″∞ 0,022–0,045 — берём середину диапазона, 0,0335 [моё]. */
        CRUDE(0.0335, 2.8, 42.6e6),
        FUEL_OIL(0.035, 1.7, 39.7e6);

        public final double burnRateInfKgM2S, kBeta, heatOfCombustionJkg;

        Fuel(double burnRateInfKgM2S, double kBeta, double heatOfCombustionJkg) {
            this.burnRateInfKgM2S = burnRateInfKgM2S;
            this.kBeta = kBeta;
            this.heatOfCombustionJkg = heatOfCombustionJkg;
        }

        /** Массовая скорость выгорания пролива диаметром {@code dM}, кг/(м²·с). */
        public double burnRate(double dM) {
            return burnRateInfKgM2S * -Math.expm1(-kBeta * dM);
        }
    }

    private PoolFire() {
    }

    /** Диаметр круга площадью {@code areaM2}. */
    public static double diameter(double areaM2) {
        return Math.sqrt(4 * areaM2 / Math.PI);
    }

    /** Мощность пожара, Вт. */
    public static double heatReleaseW(Fuel f, double dM, double chi) {
        return f.burnRate(dM) * Math.PI * dM * dM / 4 * f.heatOfCombustionJkg * chi;
    }

    /** Высота пламени по Хескестаду, м (не меньше нуля). */
    public static double flameHeightM(double qW, double dM) {
        return Math.max(0, 0.235 * Math.pow(qW / 1000, 0.4) - 1.02 * dM);
    }

    /** Доля излучения: дымное пламя больших проливов излучает вдвое меньше. */
    public static double radiantFraction(double dM) {
        return dM > SMOKY_DIAMETER_M ? SMOKY_RADIANT_FRACTION : RADIANT_FRACTION;
    }

    /** Плотность потока на расстоянии {@code rM}, кВт/м². */
    public static double fluxKwM2(Fuel f, double dM, double rM) {
        return ThermalRadiation.pointSourceKwM2(radiantFraction(dM), heatReleaseW(f, dM, COMBUSTION_EFFICIENCY), rM);
    }
}
