package dev.denfry.promptcritical.sim.isotopes;

import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.Map;

/**
 * Категория закрытого источника по МАГАТЭ (RS-G-1.9, 12 §12.3.2): отношение активности к D-значению — активности,
 * которая без защиты может причинить тяжёлый детерминированный эффект (EPR-D-Values 2006). Для смеси нуклидов —
 * сумма A_i/D_i. Категория 1 — ≥ 1000 (облучатель Co-60, РИТЭГ), 2 — 10…1000 (дефектоскоп Ir-192), 3 — 1…10
 * (каротажный Am-Be), 4 — 0,01…1, 5 — меньше.
 */
public final class IaeaCategory {
    private IaeaCategory() {
    }

    /** D-значение нуклида, Бк; {@code +∞} — нуклид не входит в таблицу (не влияет на категорию). */
    public static double dValueBq(Emitter e) {
        double tbq = switch (e) {
            case CO60 -> 0.03;
            case IR192 -> 0.08;
            case CS137 -> 0.1;
            case CS134 -> 0.04;
            case PU238 -> 0.06;
            case AM241 -> 0.06;
            case CM244 -> 0.05;
            // Sr-90 считается вместе с Y-90: D относится к паре, Y-90 отдельно не учитывается
            case SR90 -> 1;
            case I131 -> 0.2;
            case RU106 -> 0.3;
            case ZR95 -> 0.04;
            default -> Double.POSITIVE_INFINITY;
        };
        return tbq * 1e12;
    }

    /** Σ A_i/D_i. */
    public static double ratio(ActivityInventory inv) {
        double r = 0;
        for (Map.Entry<Emitter, Double> e : inv.activities().entrySet()) r += e.getValue() / dValueBq(e.getKey());
        return r;
    }

    /** Категория 1…5 по отношению A/D. */
    public static int of(double ratio) {
        if (ratio >= 1000) return 1;
        if (ratio >= 10) return 2;
        if (ratio >= 1) return 3;
        if (ratio >= 0.01) return 4;
        return 5;
    }

    public static int of(ActivityInventory inv) {
        return of(ratio(inv));
    }
}
