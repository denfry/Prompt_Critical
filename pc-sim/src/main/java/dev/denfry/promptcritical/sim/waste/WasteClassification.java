package dev.denfry.promptcritical.sim.waste;

import dev.denfry.promptcritical.sim.waste.WasteInventory.Group;

/**
 * Классификация радиоактивных отходов.
 *
 * <p><b>Категория по удельной активности</b> — ОСПОРБ-99/2010 (СП 2.6.1.2612-10), п. 3.12.2, табл. 3.7: для твёрдых
 * отходов верхние границы, кБк/кг —
 * <pre>
 *                     β (кроме ³H)   α (кроме ТУЭ)   трансурановые
 *   очень низкоакт.        10³            10²             10¹
 *   низкоактивные          10⁴            10³             10²
 *   среднеактивные         10⁷            10⁶             10⁵
 *   высокоактивные         выше           выше            выше
 * </pre>
 * Категорию даёт наихудшая из колонок. Для жидких отходов категории ОНАО нет; низкоактивные — до 10³ / 10² / 10¹,
 * среднеактивные — до 10⁷ / 10⁶ / 10⁵ кБк/кг.
 *
 * <p><b>Тепловыделение</b>: IAEA GSG-1 (2009) §2.2(f) — высокоактивные отходы обычно выделяют больше ≈ 2 кВт/м³;
 * такие отходы тепловыделяющие при любой удельной активности. <b>Долгоживущие</b> — α-излучатели с T½ &gt; 31 года выше
 * 400 Бк/г в среднем (GSG-1 §2.2(d), граница приповерхностного захоронения).
 *
 * <p><b>Класс удаляемых РАО</b> — постановление Правительства РФ № 1069 от 19.10.2012: класс 1 — твёрдые
 * тепловыделяющие ВАО (пункт глубинного захоронения после выдержки); класс 2 — твёрдые ВАО и долгоживущие САО (ПГЗРО);
 * класс 3 — твёрдые САО и долгоживущие НАО (приповерхностное захоронение на глубине до 100 м); класс 4 — твёрдые НАО,
 * короткоживущие САО и ОНАО (приповерхностное у поверхности земли); класс 5 — жидкие САО и НАО. Жидкие отходы здесь
 * не захораниваются — их кондиционируют (упаривание, цементирование, битумирование, остекловывание).
 */
public final class WasteClassification {
    public enum Category { VLLW, LLW, ILW, HLW }

    public enum Route { NEAR_SURFACE, DEEP_GEOLOGICAL, CONDITIONING }

    public record Result(Category category, int rfClass, Route route, boolean longLived, boolean heatGenerating) {
    }

    /** Верхние границы ОНАО, НАО, САО твёрдых отходов, Бк/кг: строки — β, α, ТУЭ. */
    static final double[][] SOLID_BQ_KG = {{1e6, 1e7, 1e10}, {1e5, 1e6, 1e9}, {1e4, 1e5, 1e8}};
    /** То же для жидких: ОНАО нет, первая граница — НАО. */
    static final double[][] LIQUID_BQ_KG = {{1e6, 1e6, 1e10}, {1e5, 1e5, 1e9}, {1e4, 1e4, 1e8}};
    public static final double HEAT_W_M3 = 2000, LONG_LIVED_ALPHA_BQ_G = 400;

    private WasteClassification() {
    }

    public static Category category(WasteInventory w, boolean liquid) {
        double[][] t = liquid ? LIQUID_BQ_KG : SOLID_BQ_KG;
        double[] a = {w.specificBqKg(Group.BETA), w.specificBqKg(Group.ALPHA), w.specificBqKg(Group.TRANSURANIC)};
        int worst = 0;
        for (int g = 0; g < 3; g++) {
            int c = 0;
            while (c < 3 && a[g] > t[g][c]) c++;
            worst = Math.max(worst, c);
        }
        if (liquid && worst == 0) worst = 1;
        return Category.values()[worst];
    }

    public static Result classify(WasteInventory w, boolean liquid) {
        boolean heat = w.heatDensityWm3() > HEAT_W_M3;
        boolean longLived = w.longLivedAlphaBqG() > LONG_LIVED_ALPHA_BQ_G;
        Category c = heat ? Category.HLW : category(w, liquid);
        if (liquid) return new Result(c, 5, Route.CONDITIONING, longLived, heat);
        int cls = switch (c) {
            case HLW -> heat ? 1 : 2;
            case ILW -> longLived ? 2 : 3;
            case LLW -> longLived ? 3 : 4;
            case VLLW -> longLived ? 3 : 4;
        };
        return new Result(c, cls, cls <= 2 ? Route.DEEP_GEOLOGICAL : Route.NEAR_SURFACE, longLived, heat);
    }
}
