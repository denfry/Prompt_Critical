package dev.denfry.promptcritical.sim.core;

/**
 * Уровни деградации (05 §5.4.6): вход и выход — доли бюджета {@code perf.budget_ms} по скользящему p95 времени
 * {@code tick()}. АЗ, кинетика, привод СУЗ, {@code DamageBudget} и сохранение не деградируют никогда.
 */
public enum DegradationLevel {
    L0(0.0, 0.0),
    L1(1.0, 0.7),
    L2(1.5, 1.0),
    L3(2.5, 1.5);

    /** Вход на уровень: p95 больше этой доли бюджета. */
    public final double enterFraction;
    /** Выход с уровня: p95 меньше этой доли бюджета. */
    public final double exitFraction;

    DegradationLevel(double enterFraction, double exitFraction) {
        this.enterFraction = enterFraction;
        this.exitFraction = exitFraction;
    }
}
