package dev.denfry.promptcritical.sim.radiation;

/**
 * Спецфильтры вентиляции реакторного зала (03a §3a.9.3) на пути выброса к вентиляционной трубе: аэрозольный
 * {@code hepa_filter} (99,97 % при 0,3 мкм, DF {@value #HEPA_DF}) задерживает цезий, йодный {@code charcoal_filter}
 * (уголь с TEDA) — молекулярный йод I₂ на 99,9 % и органический CH₃I на 99 % при влажности ниже 70 %, на 90 % выше.
 * Благородные газы и тритий не задерживаются.
 *
 * <p>Йод выброса — смесь: доля органического {@value #ORGANIC_IODINE_FRACTION} [BALANCE; ориентир — доли процента
 * до нескольких процентов в выбросах АЭС, здесь консервативно]. Последовательные ступени перемножают DF. Задержанная
 * активность копится в фильтрах — фильтр сам становится источником (04b).
 */
public final class VentFilterBank {
    public static final double HEPA_DF = 1 / (1 - 0.9997);
    public static final double I2_EFFICIENCY = 0.999, CH3I_DRY_EFFICIENCY = 0.99, CH3I_WET_EFFICIENCY = 0.90;
    public static final double ORGANIC_IODINE_FRACTION = 0.10;

    private final int hepa, charcoal;
    private double retainedI131Bq, retainedCs137Bq;

    public VentFilterBank(int hepaStages, int charcoalStages) {
        this.hepa = Math.max(0, hepaStages);
        this.charcoal = Math.max(0, charcoalStages);
    }

    public int hepaStages() {
        return hepa;
    }

    public int charcoalStages() {
        return charcoal;
    }

    /** Коэффициент очистки йода одной ступенью угольного фильтра при влажности выше/ниже 70 %. */
    public static double iodineStageDf(boolean humid) {
        double org = humid ? CH3I_WET_EFFICIENCY : CH3I_DRY_EFFICIENCY;
        double through = (1 - ORGANIC_IODINE_FRACTION) * (1 - I2_EFFICIENCY) + ORGANIC_IODINE_FRACTION * (1 - org);
        return 1 / through;
    }

    /** DF банка фильтров для нуклида. */
    public double df(Emitter e, boolean humid) {
        return switch (e) {
            case I131 -> Math.pow(iodineStageDf(humid), charcoal);
            case CS137 -> Math.pow(HEPA_DF, hepa);
            default -> 1;
        };
    }

    /**
     * Пропустить через фильтры {@code bqS} Бк/с нуклида за {@code dtS} с.
     *
     * @return выброс после фильтров, Бк/с
     */
    public double pass(Emitter e, double bqS, double dtS, boolean humid) {
        if (!(bqS > 0)) return 0;
        double out = bqS / df(e, humid);
        double kept = (bqS - out) * dtS;
        if (e == Emitter.I131) retainedI131Bq += kept;
        else if (e == Emitter.CS137) retainedCs137Bq += kept;
        return out;
    }

    public double retainedI131Bq() {
        return retainedI131Bq;
    }

    public double retainedCs137Bq() {
        return retainedCs137Bq;
    }

    public void setRetained(double i131Bq, double cs137Bq) {
        retainedI131Bq = Math.max(0, i131Bq);
        retainedCs137Bq = Math.max(0, cs137Bq);
    }

    /** Распад задержанной активности за {@code dtS} с: I-131 — 8,02 сут, Cs-137 — 30,1 года. */
    public void decay(double dtS) {
        retainedI131Bq *= Math.exp(-Math.log(2) * dtS / (8.02 * 86400));
        retainedCs137Bq *= Math.exp(-Math.log(2) * dtS / (30.1 * 365.25 * 86400));
    }
}
