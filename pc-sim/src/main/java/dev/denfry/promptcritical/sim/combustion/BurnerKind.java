package dev.denfry.promptcritical.sim.combustion;

/**
 * Тип горелки или топки (07 §7.8.3). Задаёт параметры по умолчанию, которые зависят от способа сжигания, а не от
 * топлива: порог полноты сгорания α_кр, механический недожог q₄, долю золы в уносе и полноту сгорания факела.
 */
public enum BurnerKind {
    /** слоевая топка на решётке: α 1,3–1,5, q₄ 3–8 %, унос 0,15–0,25 */
    GRATE(1.25, 0.05, 0.20, 1.0),
    /** пылеугольная топка: α 1,15–1,25, q₄ 0,5–2 %, унос 0,8–0,9 */
    PULVERIZED_COAL(1.12, 0.01, 0.85, 1.0),
    GAS(1.02, 0, 0, 1.0),
    OIL(1.05, 0, 0, 1.0),
    /** доменный и прочие низкокалорийные газы */
    LOW_BTU_GAS(1.02, 0, 0, 1.0),
    GT_DIFFUSION(1.02, 0, 0, 1.0),
    GT_DLN(1.02, 0, 0, 1.0),
    /** факел (13, 09): полнота сгорания 0,98 */
    FLARE(1.02, 0, 0, 0.98);

    /** Порог полноты сгорания α_кр по умолчанию (если в {@link FuelSpec} не задан свой). */
    public final double alphaCrit;
    /** Механический недожог q₄, доля тепла топлива. */
    public final double mechanicalLoss;
    /** Доля золы, уходящая с газами (a_ун). */
    public final double flyAshFraction;
    /** Доля горючего, которая сгорает при достатке воздуха; остаток уходит несгоревшим. */
    public final double completeness;

    BurnerKind(double alphaCrit, double mechanicalLoss, double flyAshFraction, double completeness) {
        this.alphaCrit = alphaCrit;
        this.mechanicalLoss = mechanicalLoss;
        this.flyAshFraction = flyAshFraction;
        this.completeness = completeness;
    }
}
