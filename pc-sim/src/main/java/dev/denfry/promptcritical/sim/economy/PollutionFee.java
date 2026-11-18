package dev.denfry.promptcritical.sim.economy;

/**
 * Плата за загрязнение (14 §14.7.3): кг за период × m_method × ставка; масса, выброшенная при концентрации на трубе
 * выше ELV, — с надбавкой ×5 (env.fee.above_elv_multiplier). Ставки [BALANCE] в центах CR за кг.
 */
public enum PollutionFee {
    /** env.fee.so2 = 0,5 CR/кг. */
    SO2(50),
    /** env.fee.nox = 0,5 CR/кг. */
    NOX(50),
    /** env.fee.pm = 1,0 CR/кг. */
    PM(100),
    /** env.fee.voc = 0,3 CR/кг. */
    VOC(30);

    public static final double ABOVE_ELV_MULTIPLIER = 5;

    private final long centsPerKg;

    PollutionFee(long centsPerKg) {
        this.centsPerKg = centsPerKg;
    }

    public long centsPerKg() {
        return centsPerKg;
    }

    /** Плата за {@code totalKg}, из которых {@code aboveElvKg} выброшены сверх ELV. */
    public long feeCents(double totalKg, double aboveElvKg, double methodFactor) {
        double above = Math.min(Math.max(0, aboveElvKg), Math.max(0, totalKg));
        double base = Math.max(0, totalKg) - above + ABOVE_ELV_MULTIPLIER * above;
        return Math.round(base * methodFactor * centsPerKg);
    }
}
