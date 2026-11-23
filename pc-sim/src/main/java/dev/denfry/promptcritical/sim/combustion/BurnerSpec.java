package dev.denfry.promptcritical.sim.combustion;

/**
 * Горелка или топка (07 §7.19.2).
 *
 * @param kind           способ сжигания
 * @param ratedFuelKgS   номинальный расход топлива, кг/с
 * @param turndown       диапазон регулирования (номинал / минимум), ≥ 1
 * @param noxFactor      множитель выбросов NOx: 1 — без подавления, 0,5–0,6 — малотоксичная горелка (07 §7.8.4)
 * @param mixingQuality  качество смешения k_mix: 1 — исправная горелка, до 3 — изношенная (07 §7.8.3)
 */
public record BurnerSpec(BurnerKind kind, double ratedFuelKgS, double turndown, double noxFactor, double mixingQuality) {
    public BurnerSpec {
        if (kind == null) throw new IllegalArgumentException("kind");
        if (!(ratedFuelKgS > 0)) throw new IllegalArgumentException("ratedFuelKgS: " + ratedFuelKgS);
        if (!(turndown >= 1)) throw new IllegalArgumentException("turndown: " + turndown);
        if (!(noxFactor >= 0)) throw new IllegalArgumentException("noxFactor: " + noxFactor);
        if (!(mixingQuality >= 1)) throw new IllegalArgumentException("mixingQuality: " + mixingQuality);
    }

    /**
     * Множитель NOx малотоксичных горелок со ступенчатым подводом воздуха (комплект {@code low_nox_burner_kit},
     * 07 §7.8.4: 0,5–0,6 — середина диапазона).
     */
    public static final double LOW_NOX_FACTOR = 0.55;

    /** Та же горелка с другим множителем NOx. */
    public BurnerSpec withNoxFactor(double factor) {
        return new BurnerSpec(kind, ratedFuelKgS, turndown, factor, mixingQuality);
    }

    /** Исправная горелка без подавления NOx. */
    public static BurnerSpec of(BurnerKind kind, double ratedFuelKgS) {
        return new BurnerSpec(kind, ratedFuelKgS, 3, 1, 1);
    }
}
