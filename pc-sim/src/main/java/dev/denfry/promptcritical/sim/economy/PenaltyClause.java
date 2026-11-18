package dev.denfry.promptcritical.sim.economy;

/** Условия неустойки контракта (14 §14.6.6): доля цены недопоставки, пени в сутки, потолок (депозит). */
public record PenaltyClause(double shortfallRate, double dailyLateRate, long capCents) {
    /** market.penalty_shortfall = 0,2, market.penalty_daily = 0,01 (§14.13). */
    public static PenaltyClause standard(long capCents) {
        return new PenaltyClause(0.2, 0.01, capCents);
    }
}
