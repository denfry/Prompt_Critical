package dev.denfry.promptcritical.sim.scenario;

import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.NodeKind;
import dev.denfry.promptcritical.sim.hydraulics.PassiveScalar;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/** Межтрубье ПГ при постоянном давлении насыщения: второй контур (03a) в M1 не моделируется. */
final class SaturatedPool implements FluidNode {
    private final ThermoState s = new ThermoState();

    SaturatedPool(WaterProperties w, double p) {
        w.stateFromPressureEnthalpy(p, w.saturationP(p).hF(), s);
    }

    @Override public long id() { return 0; }
    @Override public NodeKind kind() { return NodeKind.BOUNDARY; }
    @Override public double mass() { return Double.POSITIVE_INFINITY; }
    @Override public double internalEnergy() { return Double.POSITIVE_INFINITY; }
    @Override public double volume() { return Double.POSITIVE_INFINITY; }
    @Override public double pressure() { return s.pressure; }
    @Override public double temperature() { return s.temperature; }
    @Override public double enthalpy() { return s.enthalpy; }
    @Override public double density() { return s.density; }
    @Override public double quality() { return s.quality; }
    @Override public double voidFraction() { return s.voidFraction; }
    @Override public double mixtureLevel() { return Double.NaN; }
    @Override public double collapsedLevel() { return Double.NaN; }
    @Override public double scalar(PassiveScalar sc) { return 0; }
    @Override public void setScalar(PassiveScalar sc, double amount) {}
    @Override public double boronPpm() { return 0; }
    @Override public void addSource(double massRate, double enthalpy) {}
    @Override public void addHeat(double watts) { throw new IllegalStateException("boundary"); }
    @Override public void setBoundaryState(double pressure, double enthalpy) {}
    @Override public boolean isVoided() { return false; }
}
