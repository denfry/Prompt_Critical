package dev.denfry.promptcritical.sim.bench;

import dev.denfry.promptcritical.sim.scenario.Pr300Plant;
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

/** Горячий цикл одного ПР-300 (05 §5.7.6 {@code referenceCore}, 01 §1.12.5): цель ≤ 150 мкс, 0 Б/оп. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class ReactorFastBench {
    Pr300Plant plant;

    @Setup
    public void setUp() {
        plant = new Pr300Plant();
    }

    @Benchmark
    public double referenceCore() {
        plant.tick();
        return plant.power();
    }
}
