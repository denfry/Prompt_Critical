package dev.denfry.promptcritical.sim.accident;

/**
 * Passive ECCS accumulator: borated water under a nitrogen cushion behind a check valve.
 * The gas expands polytropically (n = 1.3) as water leaves; injection runs while the tank
 * pressure exceeds the cold-leg pressure and water remains. Flow through the line is
 * {@code Cd·A·√(2ρΔp)}.
 */
public final class Accumulator {
    private static final double N = 1.3;
    private static final double RHO = 1000;

    private final double tankM3;
    private final double lineAreaM2;
    private final double p0Pa;
    private final double gas0M3;
    private double waterKg;
    private double startedS = Double.NaN;

    /** Typical Westinghouse tank: 38 m³, 26 m³ water, 4.2 MPa, 0.07 m² line. */
    public static Accumulator westinghouse() {
        return new Accumulator(38, 26, 4.2e6, 0.07);
    }

    public Accumulator(double tankM3, double waterM3, double p0Pa, double lineAreaM2) {
        if (!(waterM3 > 0 && waterM3 < tankM3) || !(p0Pa > 0) || !(lineAreaM2 > 0))
            throw new IllegalArgumentException("bad accumulator geometry");
        this.tankM3 = tankM3;
        this.lineAreaM2 = lineAreaM2;
        this.p0Pa = p0Pa;
        this.gas0M3 = tankM3 - waterM3;
        this.waterKg = waterM3 * RHO;
    }

    public double pressurePa() {
        double gas = tankM3 - waterKg / RHO;
        return p0Pa * Math.pow(gas0M3 / gas, N);
    }

    /** Injects into a system at {@code systemPa} for {@code dt}; returns injected kg. */
    public double step(double systemPa, double dtS, double timeS) {
        double dp = pressurePa() - systemPa;
        if (dp <= 0 || waterKg <= 0) return 0;
        if (Double.isNaN(startedS)) startedS = timeS;
        double kg = Math.min(waterKg, 0.7 * lineAreaM2 * Math.sqrt(2 * RHO * dp) * dtS);
        waterKg -= kg;
        return kg;
    }

    public double waterKg() {
        return waterKg;
    }

    /** Time the check valve first opened, s after the break (NaN if never). */
    public double injectionStartS() {
        return startedS;
    }
}
