package dev.denfry.promptcritical.sim.bench;

import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
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

/** Точечная кинетика, 6 групп, один тик с подшагами (05 §5.7.6): цель ≤ 5 мкс. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class PointKineticsBench {
    PointKinetics kinetics;
    double rho = 1e-4;

    @Setup
    public void setUp() {
        kinetics = new PointKinetics(DelayedNeutronData.u235(0.0068), 2e-5, 1.0);
    }

    @Benchmark
    public double step() {
        double n = kinetics.step(0.05, rho, 0);
        if (n > 2 || n < 0.5) rho = -rho; // мощность ходит в пределах ×2
        return n;
    }
}
