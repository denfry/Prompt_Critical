package dev.denfry.promptcritical.sim.accident;

/**
 * Combustion regime of a hydrogen–air–steam mixture (04c §4c.7.3): a fit of the Shapiro–Moffette diagram (WAPD-SC-545,
 * 1957) and Dorofeev's flame acceleration criteria (NEA/CSNI/R(2000)7). Fractions are mole fractions of the whole gas.
 *
 * <ul>
 * <li>{@code NONE}: below the lower flammability limit {@code LFL = 4 % + 0.11·s}, more than 55 % steam, or under 5 %
 * oxygen;</li>
 * <li>{@code SLOW_DEFLAGRATION}: LFL to 8 %, upward burning, incomplete: {@code η_b = (x − LFL)/(8 % − LFL)};</li>
 * <li>{@code COMPLETE_DEFLAGRATION}: 8–10 %, quasi-static, up to the AICC pressure;</li>
 * <li>{@code FAST_DEFLAGRATION}: from 10 % the expansion ratio exceeds σ* ≈ 3.75 and the flame accelerates;</li>
 * <li>{@code DETONATION}: {@code 18 % + 0.3·s ≤ x ≤ 59 % − 0.6·s}, or deflagration-to-detonation transition from 13 %
 * when the volume is more than seven detonation cells λ(x) across. λ: 1 m at 13 %, 0.3 m at 15 %, 5 cm at 20 %, 1 cm at
 * 30 % (Ciccarelli et al., log-interpolated) [BALANCE-аппроксимация].</li>
 * </ul>
 * A detonation loads the walls with the Chapman–Jouguet pressure, about twice the AICC pressure.
 */
public final class HydrogenCombustion {
    /** Regimes of §4c.7.3 in order of severity. */
    public enum Regime { NONE, SLOW_DEFLAGRATION, COMPLETE_DEFLAGRATION, FAST_DEFLAGRATION, DETONATION }

    public static final double STEAM_INERT = 0.55, OXYGEN_MIN = 0.05, COMPLETE = 0.08, FAST = 0.10, DDT_MIN = 0.13;
    /** Chapman–Jouguet over AICC pressure (§4c.7.3). */
    public static final double CJ_OVER_AICC = 2;
    private static final double[] CELL_X = {0.13, 0.15, 0.20, 0.30}, CELL_M = {1.0, 0.3, 0.05, 0.01};

    private HydrogenCombustion() {
    }

    /** Lower flammability limit at steam fraction {@code s}. */
    public static double lowerLimit(double s) {
        return 0.04 + 0.11 * Math.max(0, s);
    }

    /** Detonation cell width λ(x), m (log-interpolated; outside 13–30 % held at the ends). */
    public static double cellWidthM(double x) {
        if (x <= CELL_X[0]) return CELL_M[0];
        for (int i = 1; i < CELL_X.length; i++)
            if (x <= CELL_X[i]) {
                double f = (x - CELL_X[i - 1]) / (CELL_X[i] - CELL_X[i - 1]);
                return Math.exp(Math.log(CELL_M[i - 1]) + f * (Math.log(CELL_M[i]) - Math.log(CELL_M[i - 1])));
            }
        return CELL_M[CELL_M.length - 1];
    }

    /**
     * @param x            hydrogen mole fraction
     * @param s            steam mole fraction
     * @param o            oxygen mole fraction
     * @param charLengthM  characteristic size of the gas volume (DDT needs more than 7λ)
     */
    public static Regime classify(double x, double s, double o, double charLengthM) {
        double lfl = lowerLimit(s);
        if (x < lfl || s > STEAM_INERT || o < OXYGEN_MIN) return Regime.NONE;
        boolean detonable = x >= 0.18 + 0.3 * s && x <= 0.59 - 0.6 * s;
        boolean ddt = x >= DDT_MIN && charLengthM > 7 * cellWidthM(x);
        if (detonable || ddt) return Regime.DETONATION;
        if (x >= FAST) return Regime.FAST_DEFLAGRATION;
        if (x >= COMPLETE) return Regime.COMPLETE_DEFLAGRATION;
        return Regime.SLOW_DEFLAGRATION;
    }

    /** Burn completeness η_b: partial for a slow deflagration, complete above 8 %. */
    public static double completeness(Regime r, double x, double s) {
        return switch (r) {
            case NONE -> 0;
            case SLOW_DEFLAGRATION -> {
                double lfl = lowerLimit(s);
                yield Math.max(0, Math.min(1, (x - lfl) / Math.max(1e-9, COMPLETE - lfl)));
            }
            default -> 1;
        };
    }

    /**
     * AICC pressure ratio (§4c.7.5): the burn heat {@code η·n_H₂·241.8 kJ/mol} into a gas of constant c_v = 25 J/(mol·K)
     * whose moles drop by {@code η·n_H₂/2}; {@code P/P₀ = (n'/n)(T/T₀)}. Per mole of initial gas.
     */
    public static double aiccRatio(double x, double eta, double t0K) {
        double nPrime = 1 - eta * x / 2;
        double t = t0K + eta * x * 241.8e3 / (nPrime * 25);
        return nPrime * t / t0K;
    }
}
