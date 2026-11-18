package dev.denfry.promptcritical.sim.heavywater;

/**
 * Вакуумная ректификация воды — доводка обогащённой воды GS-завода и разбавленной D₂O станции до реакторной чистоты
 * 99,75 % (так работали доводочные колонны Брюса и станционные upgrader'ы CANDU, Miller, CNS Bulletin 22 (2001) 1).
 * Коэффициент разделения мал, α ≈ 1,05 при 60 °C ({@link HeavyWater#distillationAlpha}), поэтому колонна высокая —
 * {@value #IDEAL_PLATES} идеальных тарелок насадки — и работает под вакуумом ~20 кПа: кипение при 60 °C вместо
 * 100 °C даёт α 1,05 против 1,026. D₂O менее летуча и копится в кубе (продукт), сверху уходит обеднённая вода.
 *
 * <p>Производительность блока — {@value #FEED_KG_S} кг/с питания [BALANCE]: по пару куба колонна ограничена
 * флегмой, которая при α ≈ 1,05 в десятки раз больше отбора.
 */
public final class WaterDistillationColumn {
    public static final double IDEAL_PLATES = 400, TOP_K = 333.15, FEED_KG_S = 0.3;

    private double productKg, lastRecovery, lastWasteX = Double.NaN;

    public double separation() {
        return IsotopeColumn.separation(HeavyWater.distillationAlpha(TOP_K), IDEAL_PLATES);
    }

    public IsotopeColumn.Split split(double xf) {
        return IsotopeColumn.split(xf, HeavyWaterGrade.REACTOR.x, separation(), 0);
    }

    /** Переработать {@code feedKg} кг питания с долей D {@code xf}; возвращает массу реакторной D₂O, кг. */
    public double process(double feedKg, double xf) {
        if (!(feedKg > 0)) return 0;
        IsotopeColumn.Split s = split(xf);
        lastRecovery = s.recovery();
        lastWasteX = s.wasteX();
        if (!s.works()) return 0;
        double out = HeavyWater.kg(HeavyWater.moles(feedKg, xf) * s.productPerFeed(), HeavyWaterGrade.REACTOR.x);
        productKg += out;
        return out;
    }

    public double lastRecovery() {
        return lastRecovery;
    }

    public double lastWasteX() {
        return lastWasteX;
    }

    public double totalProductKg() {
        return productKg;
    }

    public void restoreTotal(double kg) {
        productKg = Math.max(0, kg);
    }
}
