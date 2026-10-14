package dev.denfry.promptcritical.sim.bench;

import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HomologousCurve;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.PumpModel;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Гидросеть 400 узлов, 450 связей (05 §5.7.6 {@code nodes400}): 8 колец по 50 узлов с насосом в
 * каждом и 50 перемычек между соседними кольцами; с подогревом, чтобы сеть не засыпала. Цель ≤ 500 мкс.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class FluidNetworkBench {
    static final int RINGS = 8, PER_RING = 50;
    FluidNetwork net;
    final FluidNode[] heated = new FluidNode[RINGS], cooled = new FluidNode[RINGS];
    long tick;

    @Setup
    public void setUp() {
        double p = 1e6, rho = 900, mdot = 20, area = 0.01;
        net = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        PumpModel.Spec pump = new PumpModel.Spec(157, mdot / rho, 0.1e6 / (rho * 9.80665), 3e3 / 157, rho, 5,
                HomologousCurve.DEFAULT_ID);
        List<FluidNodeSpec> nodes = new ArrayList<>();
        List<FlowLinkSpec> links = new ArrayList<>();
        double d = Math.sqrt(4 * area / Math.PI);
        for (int r = 0; r < RINGS; r++) {
            for (int i = 0; i < PER_RING; i++) {
                long id = node(r, i);
                nodes.add(FluidNodeSpec.hem(id, 0.2, 0.3 * (i % 5), 0.3));
                long next = node(r, (i + 1) % PER_RING);
                boolean isPump = i == 0;
                links.add(new FlowLinkSpec(10_000 + id, isPump ? LinkKind.PUMP : LinkKind.PIPE, id, next, 50, 3, d, area,
                        1, 4.5e-5, 0.3 * (i % 5) + 0.15, 0.3 * ((i + 1) % 5) + 0.15, isPump ? pump : null, null));
            }
        }
        for (int k = 0; k < 50; k++) {
            int r = k % (RINGS - 1), i = (k * 7) % PER_RING;
            links.add(new FlowLinkSpec(20_000 + k, LinkKind.PIPE, node(r, i), node(r + 1, i), 100, 5, d / 2,
                    area / 4, 2, 4.5e-5, 0.3 * (i % 5) + 0.15, 0.3 * (i % 5) + 0.15, null, null));
        }
        double h = WaterProperties.tables().enthalpyPT(p, 400);
        net.rebuild(nodes, links, new StateCarryOver() {
            @Override
            public double initialPressure(long id) {
                return p;
            }

            @Override
            public double initialEnthalpy(long id) {
                return h;
            }

            @Override
            public double initialPumpSpeed(long id) {
                return 157;
            }
        });
        for (int r = 0; r < RINGS; r++) {
            net.link(10_000 + node(r, 0)).pump().setDrivingTorque(3e3 / 157);
            heated[r] = net.node(node(r, 10));
            cooled[r] = net.node(node(r, 35));
        }
    }

    private static long node(int ring, int i) {
        return 1 + ring * PER_RING + i;
    }

    @Benchmark
    public double nodes400() {
        // ±200 кВт в противофазе: тепло не копится, а сеть не засыпает
        double q = (++tick / 100) % 2 == 0 ? 2e5 : -2e5;
        for (int r = 0; r < RINGS; r++) {
            heated[r].addHeat(q);
            cooled[r].addHeat(-q);
        }
        net.stepFast(0.05);
        return net.totalEnergy();
    }
}
