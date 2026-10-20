package dev.denfry.promptcritical.sim.combustion;

/**
 * Пределы воспламенения топлива в воздухе — массовая концентрация горючего в объёме, кг/м³ при 20 °C (07 §7.9.6:
 * топливо, накопленное в топке без факела). Для газов — пересчёт объёмных пределов, для пыли и тумана — нижний и
 * верхний концентрационные пределы.
 *
 * @param lowerKgM3 нижний предел
 * @param upperKgM3 верхний предел
 */
public record FlammabilityLimits(double lowerKgM3, double upperKgM3) {
    /** Угольная пыль: 60 г/м³ … 4 кг/м³ [сверить]. */
    public static final FlammabilityLimits COAL_DUST = new FlammabilityLimits(0.06, 4.0);
    /** Мазутный туман [сверить]. */
    public static final FlammabilityLimits OIL_MIST = new FlammabilityLimits(0.05, 0.5);
    private static final double REF_K = 293.15;

    public FlammabilityLimits {
        if (!(lowerKgM3 > 0 && upperKgM3 > lowerKgM3)) throw new IllegalArgumentException("limits");
    }

    /** Концентрация {@code kgM3} при 20 °C между пределами. */
    public boolean flammable(double kgM3) {
        return kgM3 >= lowerKgM3 && kgM3 <= upperKgM3;
    }

    /**
     * Пределы газовой смеси по объёмным долям ({@link FuelGas}): горючая часть — по Ле Шателье, инертные (N₂, CO₂,
     * H₂O) разбавляют газ — его пределы делятся на долю горючих (влияние инертов на сами пределы не учитывается).
     */
    public static FlammabilityLimits ofGas(double[] molFractions) {
        FuelGas[] all = FuelGas.values();
        if (molFractions.length != all.length) throw new IllegalArgumentException("expected " + all.length + " fractions");
        double total = 0, comb = 0, sumL = 0, sumU = 0;
        for (int i = 0; i < all.length; i++) {
            double x = molFractions[i];
            if (!(x >= 0)) throw new IllegalArgumentException("negative fraction of " + all[i]);
            total += x;
            double[] lim = volumeLimits(all[i]);
            if (lim == null) continue;
            comb += x;
            sumL += x / lim[0];
            sumU += x / lim[1];
        }
        if (!(comb > 0)) throw new IllegalArgumentException("no combustible components");
        double lower = comb / sumL * total / comb, upper = Math.min(1, comb / sumU * total / comb);
        double rho = FuelGas.mixtureDensityKgPerNm3(molFractions) * 273.15 / REF_K;
        return new FlammabilityLimits(lower * rho, upper * rho);
    }

    /** Объёмные пределы компонента в воздухе (справочные), null — негорючий. */
    private static double[] volumeLimits(FuelGas g) {
        return switch (g) {
            case CH4 -> new double[] {0.05, 0.15};
            case C2H6 -> new double[] {0.03, 0.124};
            case C3H8 -> new double[] {0.021, 0.095};
            case C4H10 -> new double[] {0.018, 0.084};
            case C2H4 -> new double[] {0.027, 0.36};
            case H2 -> new double[] {0.04, 0.75};
            case CO -> new double[] {0.125, 0.74};
            case H2S -> new double[] {0.04, 0.44};
            case CO2, N2, O2, H2O -> null;
        };
    }
}
