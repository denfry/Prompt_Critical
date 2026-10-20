package dev.denfry.promptcritical.sim.grid;

/** Характеристики МТЗ IEC 60255 (07 §7.5.2); {@code DEFINITE} — независимая выдержка, равная TMS в секундах. */
public enum IdmtCurve {
    DEFINITE, IEC_SI, IEC_VI, IEC_EI;

    /** Время срабатывания при кратности M = I/I_s > 1, с; при M ≤ 1 — бесконечность. */
    public double tripTimeS(double multiple, double tms) {
        if (!(multiple > 1)) return Double.POSITIVE_INFINITY;
        return switch (this) {
            case DEFINITE -> tms;
            case IEC_SI -> tms * 0.14 / (Math.pow(multiple, 0.02) - 1);
            case IEC_VI -> tms * 13.5 / (multiple - 1);
            case IEC_EI -> tms * 80 / (multiple * multiple - 1);
        };
    }
}
