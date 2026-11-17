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
    /** Газ, слишком разбавленный инертами: не горит в смеси с воздухом ни при какой доле. */
    public static final FlammabilityLimits NOT_FLAMMABLE = new FlammabilityLimits(Double.MAX_VALUE / 2, Double.MAX_VALUE);
    private static final double REF_K = 293.15, AIR_O2 = 0.2095;

    public FlammabilityLimits {
        if (!(lowerKgM3 > 0 && upperKgM3 > lowerKgM3)) throw new IllegalArgumentException("limits");
    }

    /** Концентрация {@code kgM3} при 20 °C между пределами. */
    public boolean flammable(double kgM3) {
        return kgM3 >= lowerKgM3 && kgM3 <= upperKgM3;
    }

    /**
     * Пределы газовой смеси по объёмным долям ({@link FuelGas}): горючая часть — по Ле Шателье, инертные (N₂, CO₂,
     * H₂O) разбавляют газ — его пределы делятся на долю горючих. Сверху смесь газа с воздухом горит, лишь пока O₂ в
     * ней не ниже предельной концентрации кислорода LOC горючих (Zabetakis, USBM Bulletin 627, разбавитель N₂;
     * среднее по долям горючих): у доменного газа 36–74 %, а не до 100 %. Если сверху не остаётся места —
     * {@link #NOT_FLAMMABLE}.
     */
    public static FlammabilityLimits ofGas(double[] molFractions) {
        FuelGas[] all = FuelGas.values();
        if (molFractions.length != all.length) throw new IllegalArgumentException("expected " + all.length + " fractions");
        double total = 0, comb = 0, sumL = 0, sumU = 0, sumLoc = 0;
        for (int i = 0; i < all.length; i++) {
            double x = molFractions[i];
            if (!(x >= 0)) throw new IllegalArgumentException("negative fraction of " + all[i]);
            total += x;
            double[] lim = volumeLimits(all[i]);
            if (lim == null) continue;
            comb += x;
            sumL += x / lim[0];
            sumU += x / lim[1];
            sumLoc += x * lim[2];
        }
        if (!(comb > 0)) throw new IllegalArgumentException("no combustible components");
        double lower = comb / sumL * total / comb, loc = sumLoc / comb;
        double o2 = molFractions[FuelGas.O2.ordinal()] / total;
        double upperLoc = o2 >= loc ? 1 : (AIR_O2 - loc) / (AIR_O2 - o2); // 0,2095·(1 − u) + u·x_O₂ = LOC
        double upper = Math.min(1, Math.min(comb / sumU * total / comb, upperLoc));
        if (!(upper > lower)) return NOT_FLAMMABLE;
        double rho = FuelGas.mixtureDensityKgPerNm3(molFractions) * 273.15 / REF_K;
        return new FlammabilityLimits(lower * rho, upper * rho);
    }

    /** Объёмные пределы компонента в воздухе и его LOC (справочные), null — негорючий. */
    private static double[] volumeLimits(FuelGas g) {
        return switch (g) {
            case CH4 -> new double[] {0.05, 0.15, 0.12};
            case C2H6 -> new double[] {0.03, 0.124, 0.11};
            case C3H8 -> new double[] {0.021, 0.095, 0.115};
            case C4H10 -> new double[] {0.018, 0.084, 0.12};
            case C2H4 -> new double[] {0.027, 0.36, 0.10};
            case H2 -> new double[] {0.04, 0.75, 0.05};
            case CO -> new double[] {0.125, 0.74, 0.055};
            case H2S -> new double[] {0.04, 0.44, 0.075}; // LOC H₂S [допущение]
            case CO2, N2, O2, H2O -> null;
        };
    }
}
