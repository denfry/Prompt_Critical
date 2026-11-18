package dev.denfry.promptcritical.sim.enrichment;

/**
 * Газовая центрифуга с противоточной циркуляцией. Предельная разделительная мощность — формула Дирака
 * δU_max = (π/2)·Z·ρD·(Δm·v²/(2RT))², Z — длина ротора, v — окружная скорость, ρD ≈ 2,2·10⁻⁵ кг/(м·с) для UF₆,
 * Δm = 3 г/моль (Cohen, «The Theory of Isotope Separation», 1951; Olander, «The Theory of Uranium Enrichment by the Gas
 * Centrifuge», Prog. Nucl. Energy 8 (1981) 1). Реальная машина достигает доли E от неё: КПД течения (циркуляция,
 * оптимальная при c = 1, E_c = 2c/(1 + c²)) на опытный КПД 0,5 (концевые эффекты, профиль циркуляции, Olander, §6).
 * δU растёт как v⁴: скорость ограничена прочностью ротора — тонкостенная оболочка рвётся, когда окружное напряжение
 * ρv² достигает предела прочности, v_разр = √(σ/ρ). Ротор длиннее подкритического проходит изгибные критические
 * скорости (первая — 22,37/Z²·r·√(E/2ρ) для свободного трубчатого ротора), это опасно. Разделительная мощность — в кг
 * ЕРР урана (на UF₆ — множитель 0,676).
 *
 * <p>Пропускная способность машины — 0,1 г UF₆/с на метр ротора [оценка]; коэффициент разделения ступени —
 * из δU = L(α − 1)²/8 при срезе ½ (Benedict, ур. 12.111).
 */
public final class Centrifuge {
    public static final double RHO_D = 2.2e-5, DELTA_M = 0.003, R = 8.314462618, GAS_K = 310, U_PER_UF6 = 0.676,
            EFFICIENCY = 0.5, FEED_PER_M = 1.0e-4, RADIUS_M = 0.075, YEAR_S = 365.25 * 86400;
    /** Отказы: λ = λ₀·exp((v/v_разр − 0,8)/0,04), λ₀ = 1 % в год; сверхкритический ротор — ×5. */
    public static final double BASE_FAILURES_PER_YEAR = 0.01, SUPERCRITICAL_FACTOR = 5;

    /** Материал ротора: допустимое напряжение (Па), плотность (кг/м³), модуль упругости (Па). */
    public enum Rotor {
        ALUMINIUM(0.50e9, 2800, 72e9), MARAGING_STEEL(2.0e9, 8000, 190e9), CARBON_FIBRE(0.80e9, 1600, 150e9);

        public final double strengthPa, density, modulusPa;

        Rotor(double strengthPa, double density, double modulusPa) {
            this.strengthPa = strengthPa;
            this.density = density;
            this.modulusPa = modulusPa;
        }

        /** Скорость разрыва оболочки, м/с. */
        public double burstSpeed() {
            return Math.sqrt(strengthPa / density);
        }
    }

    private Centrifuge() {
    }

    /** Предел Дирака, кг ЕРР (урана) в секунду. */
    public static double diracSwuPerS(double speed, double lengthM) {
        double a = DELTA_M * speed * speed / (2 * R * GAS_K);
        return Math.PI / 2 * lengthM * RHO_D * a * a * U_PER_UF6;
    }

    /** КПД циркуляции при её доле {@code c} от оптимальной. */
    public static double circulationEfficiency(double c) {
        return c <= 0 ? 0 : 2 * c / (1 + c * c);
    }

    /** Разделительная мощность машины, кг ЕРР/с. */
    public static double swuPerS(double speed, double lengthM, double circulation) {
        return diracSwuPerS(speed, lengthM) * EFFICIENCY * circulationEfficiency(circulation);
    }

    /** Питание машины, кг урана/с. */
    public static double feedPerS(double lengthM) {
        return FEED_PER_M * lengthM * U_PER_UF6;
    }

    /** Коэффициент разделения ступени α (отбор/отвал) при срезе ½. */
    public static double alpha(double swuPerS, double feedPerS) {
        return 1 + Math.sqrt(8 * swuPerS / feedPerS);
    }

    /** Отношение рабочей угловой скорости к первой изгибной критической. */
    public static double criticalRatio(Rotor rotor, double speed, double lengthM) {
        double omega = speed / RADIUS_M;
        double omega1 = 22.37 / (lengthM * lengthM) * RADIUS_M * Math.sqrt(rotor.modulusPa / (2 * rotor.density));
        return omega / omega1;
    }

    /** Частота отказов ротора, в год (бесконечность — разрыв сразу). */
    public static double failuresPerYear(Rotor rotor, double speed, double lengthM) {
        double s = speed / rotor.burstSpeed();
        if (s >= 1) return Double.POSITIVE_INFINITY;
        double l = BASE_FAILURES_PER_YEAR * Math.exp((s - 0.8) / 0.04);
        return criticalRatio(rotor, speed, lengthM) > 0.8 ? l * SUPERCRITICAL_FACTOR : l;
    }
}
