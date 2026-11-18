package dev.denfry.promptcritical.sim.economy;

/**
 * Ключи {@code market.*} (14 §14.13): спред 4 %, рассасывание объёма 2 сут, дневной лимит выручки 50 000 CR, период квот
 * 7 сут (база тренда §14.6.2), множитель σ режима сервера (§14.8.3: TRAINING 0, HARDCORE 1,5).
 */
public record MarketParams(double spread, double recoveryS, long maxSalesPerDayCents, double quotaPeriodS,
                           double sigmaMultiplier) {
    public static final MarketParams DEFAULTS =
            new MarketParams(0.04, ElasticDemand.DEFAULT_RECOVERY_S, 5_000_000L, 7 * Commodity.DAY_S, 1.0);

    public MarketParams {
        if (spread < 0 || !(recoveryS > 0) || maxSalesPerDayCents < 0 || !(quotaPeriodS > 0) || sigmaMultiplier < 0)
            throw new IllegalArgumentException("invalid market params");
    }
}
