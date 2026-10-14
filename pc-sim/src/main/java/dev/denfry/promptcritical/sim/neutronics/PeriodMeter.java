package dev.denfry.promptcritical.sim.neutronics;

/**
 * Период «как на реальном пульте» (01 §1.5.5): фильтр первого порядка τ = 1 с и окно
 * Δt_w = 1 с, T = Δt_w / ln(n_f(t)/n_f(t−Δt_w)); |T| &gt; 1000 с — ∞. Значение идёт в тег
 * {@code core.period} и канал {@code SHORT_PERIOD}.
 */
public final class PeriodMeter {
    public static final double FILTER_TAU = 1, WINDOW = 1, INFINITE_ABOVE = 1000;

    private final double dt, alpha;
    private final double[] ring;
    private int head, filled;
    private double filtered = Double.NaN, period = Double.POSITIVE_INFINITY;

    /** @param dt шаг выборки, с (FAST-тик) */
    public PeriodMeter(double dt) {
        if (!(dt > 0 && dt <= WINDOW)) throw new IllegalArgumentException("dt");
        this.dt = dt;
        alpha = -Math.expm1(-dt / FILTER_TAU);
        ring = new double[(int) Math.round(WINDOW / dt)];
    }

    public double sample(double n) {
        filtered = Double.isNaN(filtered) ? n : filtered + alpha * (n - filtered);
        double old = ring[head];
        ring[head] = filtered;
        head = (head + 1) % ring.length;
        if (filled < ring.length) {
            filled++;
            return period;
        }
        double t = ring.length * dt / Math.log(filtered / old);
        period = Double.isFinite(t) && Math.abs(t) <= INFINITE_ABOVE ? t : Double.POSITIVE_INFINITY;
        return period;
    }

    public double period() {
        return period;
    }

    public void reset() {
        filtered = Double.NaN;
        filled = 0;
        head = 0;
        period = Double.POSITIVE_INFINITY;
    }
}
