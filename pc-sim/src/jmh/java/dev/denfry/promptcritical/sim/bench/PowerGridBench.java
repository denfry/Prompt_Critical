package dev.denfry.promptcritical.sim.bench;

import dev.denfry.promptcritical.sim.grid.GridInputs;
import dev.denfry.promptcritical.sim.grid.GridOutputs;
import dev.denfry.promptcritical.sim.grid.ReferenceGrid;
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

/** Эталонная сеть 07 §7.20 (T7.21): 60 шин, 80 ветвей, 12 машин; цель p95 ≤ 150 мкс, 0 Б/оп. */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class PowerGridBench {
    ReferenceGrid grid;
    final GridOutputs out = new GridOutputs();
    long tick;

    @Setup
    public void setUp() {
        grid = ReferenceGrid.build();
    }

    @Benchmark
    public double reference() {
        // качания не затухают до нуля: раз в 10 с переключается нагрузка
        if (++tick % 200 == 0) grid.grid.setLoadConnected(grid.loads.get(7), (tick / 200) % 2 == 0);
        grid.grid.stepFast(0.05, GridInputs.NONE, out);
        return out.generationW();
    }
}
