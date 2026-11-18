package dev.denfry.promptcritical.sim.accident;

import dev.denfry.promptcritical.sim.neutronics.DecayHeat;

/**
 * Station blackout: offsite power and diesels lost, reactor coolant pumps and pumped ECCS stop,
 * batteries keep only instrumentation. The primary sits at the safety-valve setpoint and decay
 * heat boils the water above the core off through the valves: {@code dM/dt = −P_decay / h_fg}.
 * An emergency diesel, if present, restores injection after its start delay and stops the
 * boil-off clock.
 */
public final class StationBlackout {
    /** h_fg at ~17 MPa (safety-valve setpoint), J/kg. */
    public static final double HFG_17MPA = 0.9e6;

    private final double thermalPowerW;
    private final double hfg;
    private double waterAboveCoreKg;
    private double t;
    private double uncoveredAtS = Double.NaN;
    private final double dieselRestoreS;

    /**
     * @param waterAboveCoreKg primary water above the top of active fuel when the pumps stop
     * @param dieselRestoreS   seconds until an emergency diesel restores injection, or +∞ without one
     */
    public StationBlackout(double thermalPowerW, double waterAboveCoreKg, double dieselRestoreS) {
        if (!(thermalPowerW > 0) || !(waterAboveCoreKg >= 0)) throw new IllegalArgumentException("bad SBO parameters");
        this.thermalPowerW = thermalPowerW;
        this.waterAboveCoreKg = waterAboveCoreKg;
        this.dieselRestoreS = dieselRestoreS;
        this.hfg = HFG_17MPA;
    }

    public void step(double dt) {
        t += dt;
        if (restored()) return;
        double boil = thermalPowerW * DecayHeat.afterInfiniteOperation(t) * dt / hfg;
        waterAboveCoreKg = Math.max(0, waterAboveCoreKg - boil);
        if (waterAboveCoreKg == 0 && Double.isNaN(uncoveredAtS)) uncoveredAtS = t;
    }

    public boolean restored() {
        return t >= dieselRestoreS && Double.isNaN(uncoveredAtS);
    }

    public boolean coreUncovered() {
        return !Double.isNaN(uncoveredAtS);
    }

    public double uncoveredAtS() { return uncoveredAtS; }
    public double waterAboveCoreKg() { return waterAboveCoreKg; }
    public double timeS() { return t; }
}
