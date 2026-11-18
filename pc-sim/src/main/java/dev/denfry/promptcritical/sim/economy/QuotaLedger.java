package dev.denfry.promptcritical.sim.economy;

/**
 * Квоты CO₂, cap-and-trade (14 §14.7.1): бесплатное выделение A_n = A_base·(1 − r)^n, в конце периода команда сдаёт
 * квоты на E_CO₂e·m_method плюс долг прошлого периода. Излишек переходит дальше и продаётся на бирже, при дефиците —
 * штраф p_pen·дефицит, а дефицит добавляется к обязательству следующего периода. АЭС, ГЭС и ВИЭ квот не тратят —
 * их выбросы сюда просто не попадают.
 */
public final class QuotaLedger {
    /** r = 0,05 (env.quota_decline_rate, §14.7.1 [BALANCE]). */
    public static final double DEFAULT_DECLINE = 0.05;
    /** p_pen = 100 CR/т (env.co2_penalty_per_t; по образцу ст. 16 Директивы 2003/87/ЕС), в центах. */
    public static final long DEFAULT_PENALTY_CENTS_PER_T = 100_00;
    /** GWP₁₀₀ метана = 28, как в примере §14.7.2. */
    public static final double GWP_CH4 = 28;
    /** m_method для {@code CALCULATED} = 1,2 (env.calculated_method_factor, §14.1.4); {@code MEASURED} — 1,0. */
    public static final double CALCULATED_FACTOR = 1.2;

    /** Итог периода: обязательство, сданные квоты, дефицит, штраф и счёт следующего периода. */
    public record Settlement(double obligationT, double surrenderedT, double deficitT, long fineCents, QuotaAccount next) {}

    private QuotaLedger() {}

    /** A_n = A_base·(1 − r)^n. */
    public static double allocation(double baseT, double declineRate, int n) {
        return baseT * Math.pow(1 - declineRate, n);
    }

    /** CO₂-экв., т, из CO₂ и CH₄. */
    public static double co2e(double co2T, double ch4T) {
        return co2T + GWP_CH4 * ch4T;
    }

    /**
     * Закрытие периода по выбросу {@code co2eT} с множителем метода учёта {@code methodFactor}; {@code nextAllocationT}
     * зачисляется на счёт следующего периода.
     */
    public static Settlement settle(QuotaAccount acc, double co2eT, double methodFactor, long penaltyCentsPerT,
                                    double nextAllocationT) {
        if (co2eT < 0 || methodFactor <= 0) throw new IllegalArgumentException("co2e >= 0, methodFactor > 0");
        double obligation = co2eT * methodFactor + acc.carriedDebtT();
        double surrendered = Math.min(acc.heldT(), obligation);
        double deficit = obligation - surrendered;
        double surplus = acc.heldT() - surrendered;
        long fine = Math.round(deficit * penaltyCentsPerT);
        QuotaAccount next = new QuotaAccount(acc.team(), nextAllocationT, surplus + nextAllocationT, deficit,
                acc.period() + 1);
        return new Settlement(obligation, surrendered, deficit, fine, next);
    }

    /** Купить (tonnes > 0) или продать (tonnes < 0) квоты; продать больше, чем на руках, нельзя. */
    public static QuotaAccount trade(QuotaAccount acc, double tonnes) {
        if (acc.heldT() + tonnes < 0) throw new IllegalArgumentException("not enough allowances");
        return new QuotaAccount(acc.team(), acc.allocatedT(), acc.heldT() + tonnes, acc.carriedDebtT(), acc.period());
    }
}
