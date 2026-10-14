package dev.denfry.promptcritical.sim.bench;

import dev.denfry.promptcritical.sim.mining.MineGas;
import dev.denfry.promptcritical.sim.mining.Mines;
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
 * Эталонная шахта 08 §8.18 (45 узлов, 64 ветви, 2000 обнажений): один шаг SLOW — вентиляция и газы. Бюджет
 * строк «вентиляция» и «газы шахты» — (5,4 + 1,8) мкс/тик × 20 тиков ≈ 144 мкс на шаг; 0 Б/оп.
 */
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(1)
public class MineAtmosphereBench {
    Mines.Reference mine;
    long step;

    @Setup
    public void setUp() {
        mine = new Mines.Reference();
    }

    @Benchmark
    public double slowStep() {
        // раз в минуту реального времени регулятор лавы перекрывается и открывается: сеть не застывает
        if (++step % 60 == 0) mine.ventilation.setRegulatorArea(0, (step / 60) % 2 == 0 ? 3 : 1);
        mine.tick();
        return mine.field.concentration(mine.ret[0], MineGas.CH4);
    }
}
