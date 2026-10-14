package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.HashMap;
import java.util.Map;

/** Помощники тестов гидросети. */
final class Nets {
    static final WaterProperties W = WaterProperties.tables();
    static final double DT = 0.05;

    private Nets() {}

    static FluidNetwork network() {
        return FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
    }

    static double area(double d) {
        return Math.PI * d * d / 4;
    }

    /** Связь без трения по длине: только инерционность и местные сопротивления. */
    static FlowLinkSpec link(long id, LinkKind kind, long from, long to, double inertia, double area, double zeta,
            double zFrom, double zTo, PumpModel.Spec pump, ValveModel.Spec valve) {
        return new FlowLinkSpec(id, kind, from, to, inertia, 0, Math.sqrt(4 * area / Math.PI), area, zeta, 0, zFrom, zTo,
                pump, valve);
    }

    static FlowLinkSpec pipe(long id, long from, long to, double inertia, double area, double zeta, double zFrom,
            double zTo) {
        return link(id, LinkKind.PIPE, from, to, inertia, area, zeta, zFrom, zTo, null, null);
    }

    /** Энтальпия двухфазного узла объёмом V с объёмом жидкости vLiquid при давлении p. */
    static double enthalpyWithLiquidVolume(double p, double volume, double vLiquid) {
        SaturationState s = W.saturationP(p);
        double mf = vLiquid / s.vF(), mg = (volume - vLiquid) / s.vG();
        return s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
    }

    /** Начальные условия по узлам; остальные — default. */
    static final class Init implements StateCarryOver {
        private final double p, h;
        private final Map<Long, double[]> nodes = new HashMap<>();
        private final Map<Long, Double> flows = new HashMap<>(), speeds = new HashMap<>();

        Init(double p, double h) {
            this.p = p;
            this.h = h;
        }

        Init node(long id, double pressure, double enthalpy) {
            nodes.put(id, new double[] {pressure, enthalpy});
            return this;
        }

        Init flow(long id, double mdot) {
            flows.put(id, mdot);
            return this;
        }

        Init speed(long id, double omega) {
            speeds.put(id, omega);
            return this;
        }

        @Override
        public double initialPressure(long nodeId) {
            double[] v = nodes.get(nodeId);
            return v != null ? v[0] : p;
        }

        @Override
        public double initialEnthalpy(long nodeId) {
            double[] v = nodes.get(nodeId);
            return v != null ? v[1] : h;
        }

        @Override
        public double initialMassFlow(long linkId) {
            return flows.getOrDefault(linkId, 0.0);
        }

        @Override
        public double initialPumpSpeed(long linkId) {
            return speeds.getOrDefault(linkId, 0.0);
        }
    }
}
