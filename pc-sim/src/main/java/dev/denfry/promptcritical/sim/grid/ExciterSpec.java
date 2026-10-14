package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Упрощённый IEEE SEXS (07 §7.3.3):
 * E_fd = clamp(K/(1+sT_E)·(1+sT_A)/(1+sT_B)·(V_ref − V_t + V_s), E_min, E_max).
 */
public record ExciterSpec(double k, double teS, double taOverTb, double tbS, double efdMin, double efdMax,
        ExcitationMode defaultMode) {
    public ExciterSpec {
        if (!(k > 0)) throw new IllegalArgumentException("k");
        if (!(teS > 0)) throw new IllegalArgumentException("teS");
        if (!(taOverTb > 0)) throw new IllegalArgumentException("taOverTb");
        if (!(tbS > 0)) throw new IllegalArgumentException("tbS");
        if (!(efdMax > efdMin)) throw new IllegalArgumentException("efd limits");
        Objects.requireNonNull(defaultMode, "defaultMode");
    }

    /** Машинный возбудитель эпохи I1. */
    public static ExciterSpec rotating() {
        return new ExciterSpec(20, 0.5, 1, 10, 0, 3, ExcitationMode.VOLTAGE);
    }

    /** Статический тиристорный (A2+), с форсировкой. */
    public static ExciterSpec thyristor() {
        return new ExciterSpec(200, 0.05, 0.1, 10, -4, 5, ExcitationMode.VOLTAGE);
    }

    /** Ручное возбуждение без АРВ: E_fd держится на значении, найденном при инициализации. */
    public static ExciterSpec manual() {
        return new ExciterSpec(1, 1, 1, 1, -10, 10, ExcitationMode.MANUAL);
    }
}
