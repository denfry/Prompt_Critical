package dev.denfry.promptcritical.sim.automation.twin;

/**
 * Показатели качества контура на двойнике (03d §3d.9.1, §3d.11.3): интегральные ошибки IAE = ∫|e|dt,
 * ISE = ∫e²dt, ITAE = ∫t·|e|dt, перерегулирование (доля размаха ступени от начального значения к заданию) и время
 * регулирования — после него ошибка не выходит из полосы {@value #BAND} задания (не меньше {@value #BAND_MIN} единиц).
 * Ход клапана — сумма |Δ положения| за прогон.
 */
public final class LoopMetrics {
    public static final double BAND = 0.02, BAND_MIN = 0.01;

    private final String pv, mv;
    private final double sp;
    private double iae, ise, itae, t, pv0 = Double.NaN, peak = Double.NaN, settledAt, travel, lastMv = Double.NaN;

    public LoopMetrics(String pv, double sp, String mv) {
        this.pv = pv;
        this.sp = sp;
        this.mv = mv;
    }

    public String pv() {
        return pv;
    }

    public double sp() {
        return sp;
    }

    public String mv() {
        return mv;
    }

    void record(double dt, double value, double mvPosition) {
        t += dt;
        if (Double.isNaN(value)) return;
        if (Double.isNaN(pv0)) pv0 = value;
        double e = sp - value;
        iae += Math.abs(e) * dt;
        ise += e * e * dt;
        itae += t * Math.abs(e) * dt;
        // пик за заданием в сторону движения от начального значения
        double beyond = sp >= pv0 ? value - sp : sp - value;
        if (Double.isNaN(peak) || beyond > peak) peak = beyond;
        if (Math.abs(e) > Math.max(BAND_MIN, BAND * Math.abs(sp))) settledAt = t;
        if (!Double.isNaN(mvPosition)) {
            if (!Double.isNaN(lastMv)) travel += Math.abs(mvPosition - lastMv);
            lastMv = mvPosition;
        }
    }

    public double iae() {
        return iae;
    }

    public double ise() {
        return ise;
    }

    public double itae() {
        return itae;
    }

    /** Перерегулирование, доля размаха ступени; 0 — без выхода за задание. */
    public double overshoot() {
        double span = Math.abs(sp - pv0);
        if (Double.isNaN(peak) || !(span > 0)) return 0;
        return Math.max(0, peak) / span;
    }

    /** Время регулирования, с от начала записи. */
    public double settlingTimeS() {
        return settledAt;
    }

    public double valveTravel() {
        return travel;
    }

    public double durationS() {
        return t;
    }
}
