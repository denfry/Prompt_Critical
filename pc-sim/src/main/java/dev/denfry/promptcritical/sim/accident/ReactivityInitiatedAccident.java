package dev.denfry.promptcritical.sim.accident;

import java.util.SplittableRandom;

/**
 * Reactivity-initiated accident (04c §4c.5): the prompt burst after a rod-cluster ejection and what it does to the fuel.
 *
 * <p><b>Burst</b> (§4c.5.2). Above prompt criticality the delayed neutrons and the heat leaving the fuel do not matter
 * on the millisecond scale of the burst, and the kinetics reduces to the adiabatic Nordheim–Fuchs model
 * {@code dP/dt = (ρ₀ − β − γE)/Λ · P}, {@code dE/dt = P}, {@code γ = |α_D|/C_f}. Its integrals are the oracle the
 * reactor uses instead of resolving the burst on its 50 ms step: {@code E = 2(ρ₀ − β)/γ},
 * {@code P_max = (ρ₀ − β)²/(2γΛ)}, {@code FWHM ≈ 3.52Λ/(ρ₀ − β)}. After the burst the Doppler feedback leaves the core
 * as far below prompt critical as it was above: {@code ρ = β − (ρ₀ − β)}.
 *
 * <p><b>Fuel</b> (§4c.5.3). The damage measure is the peak radially averaged fuel enthalpy at the hot spot, cal/g:
 * the starting enthalpy over 298 K plus {@link #F_Q} times the core-average rise (the field is strongly tilted towards
 * the empty rod position). Thresholds: PCMI failure {@code max(60, 150 − 1.6·Bu)} cal/g (form of US NRC RG 1.236),
 * high-temperature failure 170 cal/g, fragmentation and dispersal into the coolant 230 cal/g — a local steam explosion
 * ({@link #fciEnergyJ}, §4c.10.4).
 *
 * <p>How much of the core passes a threshold needs a distribution of the local peaking around the core average. The
 * lumped core has none, so the share of fuel with local factor above f is taken as {@code (1 − f/F_q)³}: zero at the
 * hot spot F_q = 4, the whole core at f = 0, mean 1 [оценка: one cubic tail that keeps the mean of the field at the
 * core average].
 */
public final class ReactivityInitiatedAccident {
    /** 1 cal/g in J/g. */
    public static final double CAL = 4.184;
    /** Hot-spot factor after the ejection (§4c.5.2: characteristic field tilt after a cluster ejection). */
    public static final double F_Q = 4;
    /** PCMI failure threshold {@code max(floor, base − slope·Bu)}, cal/g (§4c.5.3, {@code accident.ria_pcmi_*}) [BALANCE]. */
    public static final double PCMI_BASE_CAL_G = 150, PCMI_SLOPE = 1.6, PCMI_FLOOR_CAL_G = 60;
    /** High-temperature failure and fragmentation thresholds, cal/g ({@code accident.ria_high_temp_cal_g}, {@code _fragmentation_cal_g}). */
    public static final double HIGH_TEMP_CAL_G = 170, FRAGMENTATION_CAL_G = 230;
    /**
     * UO₂ enthalpy over 298 K per kelvin, J/(g·K): §4c.5.2 puts the hot-zero-power enthalpy at ≈ 18 cal/g for 290 °C,
     * 18·4.184/(563 − 298) = 0.284 J/(g·K).
     */
    public static final double UO2_ENTHALPY_J_GK = 18 * CAL / (563.15 - 298.15);
    /** UO₂ per kg of heavy metal (270.03/238.03). */
    public static final double UO2_PER_HM = 270.03 / 238.03;
    /** Thermal energy of molten oxide fuel over water, J/kg: c_p ≈ 500 J/(kg·K) × 2700 K + 0.36 MJ/kg (§4c.10.4). */
    public static final double MELT_HEAT_J_KG = 1.3e6;
    /** Conversion of melt heat into work, log-uniform between these (KROTOS/FARO range, {@code accident.fci_efficiency_*}) [BALANCE]. */
    public static final double FCI_EFFICIENCY_MIN = 0.001, FCI_EFFICIENCY_MAX = 0.02;
    /** Trigger probability of a melt pour into water (§4c.10.4, {@code accident.fci_trigger_prob}) [BALANCE]. */
    public static final double FCI_TRIGGER_PROB = 0.1;
    /** FWHM of the sech² burst in units of Λ/(ρ₀ − β): 2·arccosh(√2)·2 = 3.525. */
    static final double FWHM_FACTOR = 4 * Math.log(1 + Math.sqrt(2));

    private ReactivityInitiatedAccident() {
    }

    /** How the fuel failed in a burst ({@code FailureMode} of 04c §4c.14, the RIA part). */
    public enum Failure { NONE, RIA_PCMI, RIA_HIGH_TEMP }

    /**
     * Nordheim–Fuchs burst.
     *
     * @param rho0        reactivity right after the insertion, Δk/k
     * @param beta        effective delayed neutron fraction
     * @param generationS prompt neutron generation time Λ, s
     * @param gammaPerJ   γ = |α_D|/C_f, 1/J
     * @param prompt      ρ₀ &gt; β: a prompt burst happened; otherwise the delayed kinetics carry the transient
     * @param energyJ     energy of the burst E
     * @param peakW       peak power of the burst
     * @param fwhmS       full width at half maximum
     */
    public record Pulse(double rho0, double beta, double generationS, double gammaPerJ, boolean prompt, double energyJ,
            double peakW, double fwhmS) {
        /** Reactivity once the burst has turned itself over: β − (ρ₀ − β). */
        public double rhoAfter() {
            return prompt ? 2 * beta - rho0 : rho0;
        }

        /** Burst power {@code t} seconds from its peak: P_max / cosh²(ω₀t/2), ω₀ = (ρ₀ − β)/Λ. */
        public double powerAt(double t) {
            if (!prompt) return 0;
            double c = Math.cosh((rho0 - beta) / generationS * t / 2);
            return peakW / (c * c);
        }

        /** ρ₀ in dollars. */
        public double dollars() {
            return beta > 0 ? rho0 / beta : 0;
        }
    }

    public static Pulse nordheimFuchs(double rho0, double beta, double generationS, double gammaPerJ) {
        if (!(beta > 0) || !(generationS > 0) || !(gammaPerJ > 0)) throw new IllegalArgumentException("β, Λ and γ must be > 0");
        double excess = rho0 - beta;
        if (!(excess > 0)) return new Pulse(rho0, beta, generationS, gammaPerJ, false, 0, 0, 0);
        return new Pulse(rho0, beta, generationS, gammaPerJ, true, 2 * excess / gammaPerJ, excess * excess / (2 * gammaPerJ * generationS),
                FWHM_FACTOR * generationS / excess);
    }

    /** PCMI failure threshold at burnup {@code bu} MWd/kg, cal/g. */
    public static double pcmiThresholdCalG(double bu) {
        return Math.max(PCMI_FLOOR_CAL_G, PCMI_BASE_CAL_G - PCMI_SLOPE * Math.max(0, bu));
    }

    /** Radially averaged UO₂ enthalpy over 298 K at fuel temperature {@code tK}, cal/g. */
    public static double enthalpyCalG(double tK) {
        return Math.max(0, tK - 298.15) * UO2_ENTHALPY_J_GK / CAL;
    }

    /**
     * Fuel response to a burst.
     *
     * @param peakCalG           hot-spot peak enthalpy
     * @param averageRiseCalG    core-average enthalpy deposited by the burst
     * @param cladFailedFraction share of the fuel whose cladding failed (PCMI or high temperature, whichever is lower)
     * @param fragmentedFraction share of the fuel above the fragmentation threshold, dispersed into the coolant
     * @param failure            the mechanism at the hot spot
     */
    public record Damage(double peakCalG, double averageRiseCalG, double cladFailedFraction, double fragmentedFraction, Failure failure) {
        public static final Damage NONE = new Damage(0, 0, 0, 0, Failure.NONE);
    }

    /**
     * @param uo2Kg     UO₂ in the core
     * @param fuelK     fuel temperature before the burst (the start enthalpy)
     * @param burnupMwd highest assembly burnup, MWd/kg (the PCMI threshold falls with it)
     */
    public static Damage assess(Pulse p, double uo2Kg, double fuelK, double burnupMwd) {
        if (p == null || !p.prompt() || !(uo2Kg > 0)) return Damage.NONE;
        double h0 = enthalpyCalG(fuelK);
        double rise = p.energyJ() / (uo2Kg * 1000) / CAL;
        double peak = h0 + F_Q * rise;
        double pcmi = pcmiThresholdCalG(burnupMwd);
        double failAt = Math.min(pcmi, HIGH_TEMP_CAL_G);
        Failure mode = peak < failAt ? Failure.NONE : pcmi <= HIGH_TEMP_CAL_G ? Failure.RIA_PCMI : Failure.RIA_HIGH_TEMP;
        return new Damage(peak, rise, shareAbove(failAt, h0, rise), shareAbove(FRAGMENTATION_CAL_G, h0, rise), mode);
    }

    /** Share of the fuel whose local enthalpy {@code h0 + f·rise} exceeds {@code threshold}, P(F &gt; f) = (1 − f/F_q)³. */
    static double shareAbove(double threshold, double h0, double rise) {
        if (h0 >= threshold) return 1;
        if (!(rise > 0)) return 0;
        double f = (threshold - h0) / rise;
        if (f >= F_Q) return 0;
        double s = 1 - f / F_Q;
        return s * s * s;
    }

    /**
     * Work of a steam explosion of {@code meltKg} of oxide melt or fragmented fuel: {@code η·m·1.3 MJ/kg}, η log-uniform
     * in [0.1 %, 2 %] (§4c.10.4).
     */
    public static double fciEnergyJ(double meltKg, SplittableRandom random) {
        if (!(meltKg > 0)) return 0;
        double lo = Math.log(FCI_EFFICIENCY_MIN), hi = Math.log(FCI_EFFICIENCY_MAX);
        double eta = Math.exp(lo + random.nextDouble() * (hi - lo));
        return eta * meltKg * MELT_HEAT_J_KG;
    }
}
