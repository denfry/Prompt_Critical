package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Шихтованный пакет статора (18 §18.6.8): {@code count} одинаковых пластин. Заусенец и изоляция задают коэффициент
 * заполнения пакета и долю замкнутых пластин; замкнутая пара работает как лист двойной толщины, и вихревые потери в
 * ней вчетверо выше. Наклёп кромок без отжига поднимает гистерезис в полосе шириной с толщину листа вдоль реза.
 */
public record CoreStack(Lamination lamination, int count) {
    /** Заусенец раздвигает пластины на эту долю своей высоты [моё: пакет прессуется]. */
    public static final double BURR_GAP = 0.5;
    /** Заусенец выше изоляции на столько, м, замыкает пластины наверняка [моё]. */
    public static final double SHORT_SPAN_M = 40e-6;
    /** Лак, нанесённый после вырубки, обволакивает заусенец: изоляция над ним — во столько раз толще слоя [моё]. */
    public static final double COAT_OVER_BURR = 4;
    /** Гистерезис в полосе наклёпа кромок выше вдвое [сверить: Schoppa et al., JMMM 2000 — 1,5–3]. */
    public static final double EDGE_HYSTERESIS = 1.0;
    /** Технологический коэффициент потерь в машине: зубцы, вращающееся поле, сборка [сверить: 1,5–2,5]. */
    public static final double BUILD_FACTOR = 2.0;

    public CoreStack {
        Objects.requireNonNull(lamination, "lamination");
        if (count < 1) throw new IllegalArgumentException("count must be >= 1");
    }

    /** Пластин на пакет длиной {@code lengthM}. */
    public static int countFor(Lamination l, double lengthM) {
        return (int) Math.ceil(lengthM * stackingFactor(l) / l.sheet().thicknessM());
    }

    /** Коэффициент заполнения пакета сталью. */
    public static double stackingFactor(Lamination l) {
        double s = l.sheet().thicknessM();
        return s / (s + l.sheet().coating().layerM + BURR_GAP * l.burrM());
    }

    public double stackingFactor() {
        return stackingFactor(lamination);
    }

    /** Доля замкнутых заусенцем межлистовых промежутков, 0..1. */
    public double shortedFraction() {
        double insulation = lamination.sheet().coating().layerM * (lamination.coatedAfterPunch() ? COAT_OVER_BURR : 1);
        return Math.max(0, Math.min(1, (lamination.burrM() - insulation) / SHORT_SPAN_M));
    }

    /** Множитель вихревых потерь: замкнутая пара — лист двойной толщины, ×4. */
    public double eddyFactor() {
        return 1 + 3 * shortedFraction();
    }

    /** Множитель гистерезиса: наклёп листа и, без отжига после вырубки, полоса у реза. */
    public double hysteresisFactor() {
        var shape = lamination.shape();
        double edge = lamination.edgesRelieved() ? 0
                : EDGE_HYSTERESIS * Math.min(1, shape.cutLengthM() * lamination.sheet().thicknessM() / shape.areaM2());
        return lamination.sheet().hysteresisFactor() * (1 + edge);
    }

    public double lengthM() {
        return count * lamination.sheet().thicknessM() / stackingFactor();
    }

    public double massKg() {
        return count * lamination.massKg();
    }

    /** Удельные потери пакета, Вт/кг. */
    public double lossWPerKg(double bT, double fHz) {
        var sh = lamination.sheet();
        return ElectricalSheet.lossWPerKg(sh.siFrac(), sh.thicknessM(), hysteresisFactor(), eddyFactor(), bT, fHz);
    }

    /** Потери в стали пакета в машине, Вт. */
    public double coreLossW(double bT, double fHz) {
        return massKg() * lossWPerKg(bT, fHz) * BUILD_FACTOR;
    }
}
