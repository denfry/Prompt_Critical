package dev.denfry.promptcritical.sim.metallurgy;

import java.util.Objects;

/**
 * Дефект трубы (08 §8.7.4).
 *
 * @param depthFrac глубина d/t; 1 — сквозной
 * @param lengthM   осевая длина L, м
 */
public record PipeDefect(DefectKind kind, double depthFrac, double lengthM) {
    public static final PipeDefect NONE = new PipeDefect(DefectKind.NONE, 0, 0);

    public PipeDefect {
        Objects.requireNonNull(kind, "kind");
        if (!(depthFrac >= 0 && depthFrac <= 1) || !(lengthM >= 0)) throw new IllegalArgumentException("defect size");
    }

    public boolean present() {
        return kind != DefectKind.NONE && depthFrac > 0;
    }
}
