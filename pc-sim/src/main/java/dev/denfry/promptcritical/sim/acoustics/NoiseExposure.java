package dev.denfry.promptcritical.sim.acoustics;

/**
 * Шумовая нагрузка на слух (ISO 9612:2009, ISO 1999:2013; NIOSH Criteria for a Recommended Standard: Occupational Noise
 * Exposure, 1998; Директива 2003/10/EC).
 *
 * <ul>
 *   <li>Доза — доля допустимой: D = Σ t_i/T(L_i), T(L) = 8 ч·2^(−(L − 85)/3): критерий 85 дБ(А) за 8 ч, правило равной
 *       энергии (3 дБ — вдвое меньше времени). Ниже 80 дБ(А) в дозу не идёт (NIOSH).</li>
 *   <li>Эквивалентный уровень за смену L_EX,8h = 85 + 10·lg D.</li>
 *   <li>Пороги 2003/10/EC: 80 дБ(А) — нижний уровень действия, 85 — верхний (СИЗ обязательны), 87 — предел с учётом
 *       СИЗ.</li>
 *   <li>Временный сдвиг порога слуха проходит за часы покоя; в игре доза спадает экспоненциально с периодом
 *       {@code halfLifeS} [BALANCE].</li>
 *   <li>Противошумные наушники: −{@value #PROTECTOR_DB} дБ (13 §13.7: hearing_protection; SNR 30–35 дБ по EN 352-1 с
 *       поправкой HSE на реальное ношение — 4 дБ и больше).</li>
 * </ul>
 */
public final class NoiseExposure {
    public static final double CRITERION_DB = 85, EXCHANGE_DB = 3, THRESHOLD_DB = 80, REFERENCE_S = 8 * 3600;
    public static final double LOWER_ACTION_DB = 80, UPPER_ACTION_DB = 85, LIMIT_DB = 87;
    public static final double PROTECTOR_DB = 25;
    /** Порог дискомфорта: громче — оглушение сразу, без дозы [BALANCE; болевой порог ≈ 130 дБ]. */
    public static final double STUN_DB = 120;

    private NoiseExposure() {
    }

    /** Допустимое время при уровне L, с. */
    public static double allowedSeconds(double levelDb) {
        return REFERENCE_S * Math.pow(2, -(levelDb - CRITERION_DB) / EXCHANGE_DB);
    }

    /** Прирост дозы в секунду при уровне L, 1/с. */
    public static double doseRate(double levelDb) {
        return levelDb < THRESHOLD_DB ? 0 : 1 / allowedSeconds(levelDb);
    }

    /** L_EX,8h по дозе, дБ(А); нулевая доза — −∞. */
    public static double lex8h(double dose) {
        return dose > 0 ? CRITERION_DB + 10 * Math.log10(dose) : Double.NEGATIVE_INFINITY;
    }

    /** Уровень под ухом: в наушниках — на {@value #PROTECTOR_DB} дБ ниже. */
    public static double atEar(double levelDb, boolean protector) {
        return protector ? levelDb - PROTECTOR_DB : levelDb;
    }

    /**
     * Шаг дозы: спад с периодом halfLife и прирост за Δt.
     *
     * @return новая доза
     */
    public static double step(double dose, double dtS, double levelAtEarDb, double halfLifeS) {
        double decayed = halfLifeS > 0 ? dose * Math.pow(2, -dtS / halfLifeS) : dose;
        return decayed + dtS * doseRate(levelAtEarDb);
    }
}
