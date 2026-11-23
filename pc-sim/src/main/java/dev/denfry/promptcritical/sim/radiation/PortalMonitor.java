package dev.denfry.promptcritical.sim.radiation;

/**
 * Портальный γ-монитор на выходе из зоны (04b §4b.7.2): колонна с пластиковым сцинтиллятором большого объёма у
 * прохода. Пока в проходе никого нет, монитор копит фон; когда человек входит, счёт за окно {@code windowS}
 * сравнивается с порогом «фон + {@code sigmas}·σ», где σ = √(N_фон) — пуассоновский разброс фонового счёта за то же
 * окно. Счёт берётся ожидаемым (без розыгрыша), поэтому решение детерминировано.
 * <p>
 * Чувствительность {@code cpsPerUSvH} — скорость счёта на 1 мкЗв/ч у детектора. При 1,5·10⁴ имп/с на мкЗв/ч (фон
 * 0,1 мкЗв/ч даёт 1500 имп/с — порядок для рамы из пластика в десятки литров) порог за 2 с — около 0,009 мкЗв/ч на
 * детекторе, то есть Cs-137 активностью ≈ 30 кБк на {@value #DETECTOR_M} м: 1 мкКи (37 кБк), заявленный для
 * порталов в 04b, ловится с запасом.
 *
 * @param cpsPerUSvH скорость счёта на 1 мкЗв/ч, имп/с
 * @param windowS    окно счёта, с
 * @param sigmas     порог в σ фона
 */
public record PortalMonitor(double cpsPerUSvH, double windowS, double sigmas) {
    /** Расстояние от несомого источника до детектора колонны, м. */
    public static final double DETECTOR_M = 0.5;
    /** Монитор по 04b §4b.7.2: окно 2 с, порог фон + 5σ. */
    public static final PortalMonitor STANDARD = new PortalMonitor(1.5e4, 2, 5);

    public PortalMonitor {
        if (!(cpsPerUSvH > 0) || !(windowS > 0) || !(sigmas > 0))
            throw new IllegalArgumentException("portal monitor needs positive sensitivity, window and threshold");
    }

    /** Порог чистого счёта (сверх фона) за окно при фоне {@code ambientUSvH}, отсчётов. */
    public double thresholdCounts(double ambientUSvH) {
        double bg = Math.max(ambientUSvH, 0) * cpsPerUSvH * windowS;
        // при нулевом фоне один отсчёт уже не случайность
        return Math.max(sigmas * Math.sqrt(bg), 1);
    }

    /** Чистый счёт за окно от несомого источника, дающего {@code sourceUSvH} на детекторе, отсчётов. */
    public double netCounts(double sourceUSvH) {
        return Math.max(sourceUSvH, 0) * cpsPerUSvH * windowS;
    }

    /** Чистый счёт в долях порога: больше 1 — тревога. */
    public double ratio(double ambientUSvH, double sourceUSvH) {
        return netCounts(sourceUSvH) / thresholdCounts(ambientUSvH);
    }

    public boolean alarms(double ambientUSvH, double sourceUSvH) {
        return ratio(ambientUSvH, sourceUSvH) > 1;
    }

    /** Мощность дозы на детекторе, при которой монитор срабатывает, мкЗв/ч. */
    public double thresholdUSvH(double ambientUSvH) {
        return thresholdCounts(ambientUSvH) / (cpsPerUSvH * windowS);
    }
}
