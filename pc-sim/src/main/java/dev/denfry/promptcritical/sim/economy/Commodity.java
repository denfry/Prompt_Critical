package dev.denfry.promptcritical.sim.economy;

/**
 * Товар биржи (14 §14.6.1, §14.11). Цена в центах CR за единицу учёта; {@code depth} — глубина рынка D (ед. в сутки при
 * базовой цене), {@code elasticity} — ε, {@code sigma}/{@code theta} — параметры процесса Орнштейна — Уленбека в 1/с
 * физического времени, {@code trend} — g за период квот (§14.6.2).
 */
public record Commodity(String id, String unit, long basePriceCents, int minEpoch, boolean npcBuy,
                        boolean npcSell, double depth, double elasticity, double sigma, double theta,
                        double trend, Perishability perishable, boolean restricted) {
    /** Сутки физического времени, с. */
    public static final double DAY_S = 86_400;
    /** θ по умолчанию = 1/(3 сут) (§14.6.2). */
    public static final double DEFAULT_THETA = 1 / (3 * DAY_S);
    /** σ по умолчанию: стационарное отклонение σ/√(2θ) = 10 % (§14.6.2). */
    public static final double DEFAULT_SIGMA = 0.10 * Math.sqrt(2 * DEFAULT_THETA);

    public Commodity {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id");
        if (basePriceCents <= 0) throw new IllegalArgumentException("basePriceCents must be > 0");
        if (!(depth > 0)) throw new IllegalArgumentException("depth must be > 0");
        if (!(elasticity > 0)) throw new IllegalArgumentException("elasticity must be > 0");
        if (sigma < 0 || !(theta > 0)) throw new IllegalArgumentException("sigma >= 0, theta > 0");
        if (trend <= -1) throw new IllegalArgumentException("trend must be > -1");
        if (perishable == null) perishable = new Perishability.None();
    }

    /** Товар с параметрами цены по умолчанию §14.6.2 (θ = 1/3 сут, отклонение 10 %, без тренда). */
    public static Commodity standard(String id, String unit, long basePriceCents, int minEpoch, double depth,
                                     double elasticity, Perishability perishable) {
        return new Commodity(id, unit, basePriceCents, minEpoch, true, true, depth, elasticity, DEFAULT_SIGMA,
                DEFAULT_THETA, 0, perishable, false);
    }
}
