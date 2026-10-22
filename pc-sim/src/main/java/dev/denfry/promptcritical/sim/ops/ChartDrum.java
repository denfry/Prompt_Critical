package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Arrays;

/**
 * Самопишущий прибор {@code recording_gauge} (13 §13.6.7): барабан с часовым механизмом, одна величина, точка раз в
 * {@code sample_s}. Положение пера — номер отсчёта по модулю длины ленты: барабан вращается и после снятия ленты.
 * Значения квантуются в short по шкале прибора, пустая точка — {@link #EMPTY}.
 */
public final class ChartDrum {
    public static final short EMPTY = Short.MIN_VALUE;
    private static final int RANGE = Short.MAX_VALUE;

    private final short[] trace;
    private final double min, max;
    private long samples;

    public ChartDrum(int points, double min, double max) {
        if (points <= 0 || !(max > min)) throw new IllegalArgumentException("chart");
        this.trace = new short[points];
        this.min = min;
        this.max = max;
        Arrays.fill(trace, EMPTY);
    }

    public static ChartDrum of(OpsConfig cfg, double min, double max) {
        return new ChartDrum(cfg.recordingPoints(), min, max);
    }

    /** Раз в {@code sample_s}. */
    public void sample(double value) {
        trace[(int) (samples % trace.length)] = encode(value);
        samples++;
    }

    /** Индекс, куда ляжет следующая точка. */
    public int position() {
        return (int) (samples % trace.length);
    }

    public short[] trace() {
        return trace.clone();
    }

    /** Снять ленту ({@code chart_trace}); на барабане чистая бумага. */
    public short[] tearOff() {
        short[] out = trace.clone();
        Arrays.fill(trace, EMPTY);
        return out;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    /** Лента и положение пера из сохранения владельца; лента другой длины не восстанавливается. */
    public void restore(short[] points, long sampleCount) {
        samples = sampleCount;
        if (points.length == trace.length) System.arraycopy(points, 0, trace, 0, trace.length);
        else Arrays.fill(trace, EMPTY);
    }

    public void save(StateWriter out) {
        out.putLong("samples", samples);
        int[] t = new int[trace.length];
        for (int i = 0; i < t.length; i++) t[i] = trace[i];
        out.putInts("trace", t, t.length);
    }

    /** Лента другой длины (сменился {@code period_s}/{@code sample_s}) не восстанавливается. */
    public void load(StateReader in) {
        samples = in.getLong("samples");
        int[] t = in.getInts("trace");
        if (t.length != trace.length) {
            Arrays.fill(trace, EMPTY);
            return;
        }
        for (int i = 0; i < t.length; i++) trace[i] = (short) t[i];
    }

    public short encode(double v) {
        if (Double.isNaN(v)) return EMPTY;
        double f = Math.clamp((v - min) / (max - min), 0, 1);
        return (short) Math.round(-RANGE + f * 2 * RANGE);
    }

    /** Значение точки; NaN — пусто. */
    public double decode(short q) {
        return q == EMPTY ? Double.NaN : min + (q + RANGE) / (2.0 * RANGE) * (max - min);
    }
}
