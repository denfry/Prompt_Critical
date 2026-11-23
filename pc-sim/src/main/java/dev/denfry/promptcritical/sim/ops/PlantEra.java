package dev.denfry.promptcritical.sim.ops;

/** Эпоха комплекса (00 §0.5.1) как ступень лицензии: I0 открыта всегда, ворота начинаются с I1. */
public enum PlantEra {
    I0, I1, I2, I3, I4;

    private static final PlantEra[] VALUES = values();

    /** Предыдущая эпоха; у I0 — {@code null}. */
    public PlantEra previous() {
        return ordinal() == 0 ? null : VALUES[ordinal() - 1];
    }

    /** {@code "I1"} → {@link #I1}; неизвестный код — {@link IllegalArgumentException}. */
    public static PlantEra of(String id) {
        for (PlantEra e : VALUES) if (e.name().equals(id)) return e;
        throw new IllegalArgumentException("unknown era: " + id);
    }
}
