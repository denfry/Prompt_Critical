package dev.denfry.promptcritical.sim.mes;

/**
 * Генератор для экономического распределения нагрузки: квадратичная кривая затрат на топливо
 * {@code F(P) = a + b·P + c·P²} (у.е./ч при P в МВт) и пределы {@code pMin..pMax}, когда агрегат в работе
 * (Wood &amp; Wollenberg, Power Generation, Operation and Control, гл. 3). Удельный выброс — тонн CO₂ на МВт·ч.
 *
 * @param name     имя агрегата
 * @param a        постоянная часть затрат (холостой ход), у.е./ч
 * @param b        линейный член, у.е./МВт·ч
 * @param c        квадратичный член, у.е./(МВт²·ч), не меньше нуля
 * @param pMin     технический минимум, МВт
 * @param pMax     располагаемая мощность, МВт
 * @param emission выброс, т/МВт·ч
 */
public record GeneratingUnit(String name, double a, double b, double c, double pMin, double pMax, double emission) {
    public GeneratingUnit {
        if (!(c >= 0) || !(pMin >= 0) || !(pMax >= pMin) || !(emission >= 0) || !Double.isFinite(a) || !Double.isFinite(b)
                || !Double.isFinite(c) || !Double.isFinite(pMax))
            throw new IllegalArgumentException("bad unit " + name);
    }

    /** Агрегат без выбросов. */
    public static GeneratingUnit of(String name, double a, double b, double c, double pMin, double pMax) {
        return new GeneratingUnit(name, a, b, c, pMin, pMax, 0);
    }

    /** Кривая расхода тепла {@code H(P)} (МБТЕ/ч), умноженная на цену топлива (у.е./МБТЕ). */
    public static GeneratingUnit fromHeatRate(String name, double h0, double h1, double h2, double fuelPrice, double pMin, double pMax) {
        return of(name, h0 * fuelPrice, h1 * fuelPrice, h2 * fuelPrice, pMin, pMax);
    }

    /** Затраты на топливо при мощности {@code p}, у.е./ч. */
    public double cost(double p) {
        return a + b * p + c * p * p;
    }

    /** Приростная стоимость {@code dF/dP}, у.е./МВт·ч. */
    public double incremental(double p) {
        return b + 2 * c * p;
    }

    /**
     * Мощность, при которой приростная стоимость (с надбавкой {@code adder} на МВт·ч) равна {@code lambda}, в
     * пределах агрегата. У линейной кривой ({@code c = 0}) — край диапазона.
     */
    double outputAt(double lambda, double adder) {
        double margin = lambda - b - adder;
        if (c <= 0) return margin > 0 ? pMax : pMin;
        return Math.max(pMin, Math.min(pMax, margin / (2 * c)));
    }
}
