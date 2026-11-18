package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.SplittableRandom;

/**
 * Steam-generator tube rupture (04c §4c.6): the damaged generator's side of a primary-to-secondary leak. The reactor
 * computes the leak itself ({@code PressurizedWaterReactor#setTubeRupture}, §4c.6.1); this model keeps what the leak
 * does to the secondary — the containment bypass of §4c.6.2.
 *
 * <p><b>Not isolated.</b> The feedwater controller holds the level: the leaked coolant leaves with the steam to the
 * turbine and the condenser, whose ejectors vent the noble gases and some iodine to the stack. The N-16 of the
 * coolant (T½ = 7.13 s) reaches the steam-line monitor after the 2–5 s transit from the core, so it is the first sign
 * (§4c.6.3, T-4c-09: under 10 s).
 *
 * <p><b>Isolated</b> (main steam isolation and feed closed): the leak fills the generator, and its pressure follows the
 * saturation of the primary water flowing in, towards {@code P_sat(T_cold)} [оценка: τ = 5 min, the tube bundle's
 * time constant]. At the relief setpoint the SG relief valve lifts and vents straight to the atmosphere — steam at
 * first, water once the generator is full. A valve passing water sticks open with probability 0.3
 * ({@code accident.sg_relief_stuck_open_prob}, §4c.6.4 [BALANCE]); a stuck valve takes the generator down to the
 * atmosphere and the leak becomes an unisolable release. The way out (§4c.6.4): cool the primary through the intact
 * generator until its cold leg is below the saturation temperature of the relief setpoint, then bring the primary
 * pressure down to the damaged generator's — the leak stops.
 */
public final class SteamGeneratorTubeRupture {
    /** One tube ⌀16×1.5 mm torn through, both ends: 2·π·0.013²/4 = 2.65 cm² (§4c.6.1). */
    public static final double TUBE_BREAK_M2 = 2 * Math.PI * 0.013 * 0.013 / 4;
    /** N-16 half-life, s, and the core-to-steam-line transit, s (§4c.6.3: 2–5 s). */
    public static final double N16_HALF_LIFE_S = 7.13, TRANSIT_S = 3.5;
    /**
     * Steam-line N-16 monitor alarm: primary water carried over in the steam above this, kg/s at full power [оценка: ≈ 3 %
     * of a tube; at 10 % power one tube still trips it].
     */
    public static final double N16_ALARM_KG_S = 0.5;
    /** Detector and channel time constant, s [оценка]. */
    public static final double N16_LAG_S = 2;
    /** Damaged SG relief setpoint and reseat, Pa (VVER SG safety valves ≈ 8.0 MPa) [оценка]. */
    public static final double RELIEF_PA = 8.0e6, RESEAT_PA = 7.5e6;
    /** Relief valve capacity, kg/s of steam at the setpoint, scaled from a VVER-1000 SG (≈ 50 kg/s per valve) by power [оценка]. */
    public static final double RELIEF_KG_S = 5;
    /** Steam space of one game SG: coolant it takes before it is full of water, kg [оценка: half of 6 m³ steam dome at 6.4 MPa liquid]. */
    public static final double STEAM_SPACE_KG = 2_200;
    /** Stuck-open probability of a relief valve passing water ({@code accident.sg_relief_stuck_open_prob}) [BALANCE]. */
    public static final double STUCK_OPEN_PROB = 0.3;
    /** Approach of the isolated generator to the saturation of the incoming water, s [оценка]. */
    public static final double TAU_ISOLATED_S = 300;
    /** Blowdown of a stuck-open generator to the atmosphere, s [оценка]. */
    public static final double TAU_BLOWDOWN_S = 120;
    private static final double ATMOSPHERE_PA = 101_325;
    private static final WaterProperties W = WaterProperties.tables();

    private final double nominalPa;
    private boolean ruptured, isolated, reliefOpen, stuckOpen, waterRelief, alarm;
    private double sgPa, excessKg, signal, timeS, alarmAtS = -1;
    private double toCondenserKg, toAtmosphereKg, lastCondenserKgS, lastAtmosphereKgS;
    private boolean stuckEvent, alarmEvent;

    /** @param nominalPa operating pressure of the generator's secondary */
    public SteamGeneratorTubeRupture(double nominalPa) {
        this.nominalPa = nominalPa;
        this.sgPa = nominalPa;
    }

    /** Tubes torn (the drill or wear): the reactor gets {@link #breakAreaM2()}. */
    public void rupture(boolean on) {
        if (on && !ruptured) timeS = 0;
        ruptured = on;
        if (!on) {
            // tubes plugged: the generator is drained and reset
            excessKg = 0;
            reliefOpen = stuckOpen = waterRelief = alarm = false;
            signal = 0;
            alarmAtS = -1;
            sgPa = nominalPa;
        }
    }

    public boolean ruptured() {
        return ruptured;
    }

    /** Main steam isolation and feed of the damaged generator closed. Reopening returns it to the plant. */
    public void setIsolated(boolean on) {
        isolated = on;
        if (!on && !stuckOpen) {
            excessKg = 0;
            sgPa = nominalPa;
        }
    }

    public boolean isolated() {
        return isolated;
    }

    /** Break area the reactor sees: one tube, or none. */
    public double breakAreaM2() {
        return ruptured ? TUBE_BREAK_M2 : 0;
    }

    /**
     * @param leakKgS    primary-to-secondary flow the reactor computed
     * @param coldLegK   primary cold-leg temperature (the water the leak brings in)
     * @param powerFrac  fission power, share of nominal: N-16 is made by O-16(n,p) in the core only while it runs
     * @param random     the stuck-open draw (deterministic per world)
     */
    public void step(double dt, double leakKgS, double coldLegK, double powerFrac, SplittableRandom random) {
        stuckEvent = alarmEvent = false;
        lastCondenserKgS = lastAtmosphereKgS = 0;
        if (!(dt > 0)) return;
        timeS += dt;
        leakKgS = Math.max(0, leakKgS);
        // N-16 at the steam-line monitor: the leak decayed over the transit, through the channel lag
        double carried = isolated ? 0 : leakKgS * Math.min(1, Math.max(0, powerFrac)) * Math.exp(-Math.log(2) / N16_HALF_LIFE_S * TRANSIT_S);
        double ready = timeS > TRANSIT_S ? carried : 0;
        signal += (ready - signal) * Math.min(1, dt / N16_LAG_S);
        boolean on = signal > N16_ALARM_KG_S;
        if (on && !alarm) {
            alarmEvent = true;
            alarmAtS = timeS;
        }
        alarm = on || alarm && ruptured;
        if (!isolated && !stuckOpen) {
            sgPa = nominalPa;
            excessKg = 0;
            toCondenserKg += leakKgS * dt;
            lastCondenserKgS = leakKgS;
            return;
        }
        double in = leakKgS * dt;
        excessKg += in;
        if (stuckOpen) {
            // the open valve blows the generator down; what comes in goes out
            sgPa = ATMOSPHERE_PA + (sgPa - ATMOSPHERE_PA) * Math.exp(-dt / TAU_BLOWDOWN_S);
            double out = Math.min(excessKg, in + RELIEF_KG_S * dt * sgPa / RELIEF_PA);
            excessKg -= out;
            vent(out, dt);
            return;
        }
        double target = Math.max(ATMOSPHERE_PA, W.saturationPressure(Math.max(373.15, Math.min(640, coldLegK))));
        // a generator full of water is hydraulically solid: the leak pushes it straight up to its valves
        if (excessKg >= STEAM_SPACE_KG) sgPa = Math.max(sgPa, RELIEF_PA);
        else sgPa += (target - sgPa) * Math.min(1, dt / TAU_ISOLATED_S);
        if (sgPa >= RELIEF_PA) reliefOpen = true;
        else if (sgPa < RESEAT_PA && excessKg < STEAM_SPACE_KG) reliefOpen = false;
        if (reliefOpen) {
            sgPa = Math.min(sgPa, RELIEF_PA);
            boolean full = excessKg >= STEAM_SPACE_KG;
            // the valve passes what keeps the generator at the setpoint: the leak's volume, as steam or as water
            double out = Math.min(excessKg, full ? in : Math.min(in, RELIEF_KG_S * dt));
            excessKg -= out;
            vent(out, dt);
            if (full && !waterRelief) {
                waterRelief = true;
                if (random.nextDouble() < STUCK_OPEN_PROB) {
                    stuckOpen = true;
                    stuckEvent = true;
                }
            }
        }
    }

    private void vent(double kg, double dt) {
        toAtmosphereKg += kg;
        lastAtmosphereKgS = kg / dt;
    }

    /** Pressure the reactor's leak works against. */
    public double sgPressurePa() {
        return sgPa;
    }

    /** Coolant gained by the isolated generator, kg (full at {@link #STEAM_SPACE_KG}). */
    public double excessWaterKg() {
        return excessKg;
    }

    public double levelExcessFraction() {
        return Math.min(1.5, excessKg / STEAM_SPACE_KG);
    }

    public boolean n16Alarm() {
        return alarm;
    }

    /** Seconds from the rupture to the N-16 alarm (−1 — not yet). */
    public double alarmDelayS() {
        return alarmAtS;
    }

    public boolean reliefOpen() {
        return reliefOpen || stuckOpen;
    }

    public boolean stuckOpen() {
        return stuckOpen;
    }

    /** The relief valve passed water this step and stuck open. */
    public boolean stuckEvent() {
        return stuckEvent;
    }

    public boolean alarmEvent() {
        return alarmEvent;
    }

    /** The relief vents water (the generator is full). */
    public boolean waterRelief() {
        return waterRelief && reliefOpen();
    }

    /** Primary coolant carried to the condenser with the steam this step, kg/s. */
    public double condenserKgS() {
        return lastCondenserKgS;
    }

    /** Primary coolant vented to the atmosphere through the SG relief this step, kg/s. */
    public double atmosphereKgS() {
        return lastAtmosphereKgS;
    }

    public double toCondenserKg() {
        return toCondenserKg;
    }

    public double toAtmosphereKg() {
        return toAtmosphereKg;
    }

    public void save(StateWriter out) {
        out.putBoolean("rup", ruptured);
        out.putBoolean("iso", isolated);
        out.putBoolean("relief", reliefOpen);
        out.putBoolean("stuck", stuckOpen);
        out.putBoolean("water", waterRelief);
        out.putBoolean("alarm", alarm);
        out.putDoubles("s", new double[] {sgPa, excessKg, signal, timeS, alarmAtS, toCondenserKg, toAtmosphereKg}, 7);
    }

    public void load(StateReader in) {
        ruptured = in.optInt("rup", 0) != 0;
        isolated = in.optInt("iso", 0) != 0;
        reliefOpen = in.optInt("relief", 0) != 0;
        stuckOpen = in.optInt("stuck", 0) != 0;
        waterRelief = in.optInt("water", 0) != 0;
        alarm = in.optInt("alarm", 0) != 0;
        if (in.has("s")) {
            double[] s = in.getDoubles("s");
            if (s.length >= 7) {
                sgPa = s[0];
                excessKg = s[1];
                signal = s[2];
                timeS = s[3];
                alarmAtS = s[4];
                toCondenserKg = s[5];
                toAtmosphereKg = s[6];
            }
        }
    }
}
