package dev.denfry.promptcritical.sim.geothermal;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.SplittableRandom;

/**
 * Наведённая сейсмичность закачки (15 §15.6.4). Пока забойное давление закачки ниже порога
 * {@code geothermal.seismic_threshold_frac} литостатического, событий нет. Выше порога микроземлетрясения идут
 * пуассоновским потоком с интенсивностью, растущей с превышением, магнитуды — по Гутенбергу — Рихтеру с b = 1.
 *
 * <p>«Светофор» закачки — как на скважине Basel-1 (Häring et al., 2008, Geothermics 37): по наибольшей магнитуде
 * за окно {@value #WINDOW_S} с — зелёный ниже ML {@value #AMBER_ML}, жёлтый до ML {@value #RED_ML}, красный — стоп
 * закачки. Событие от ML {@value #DAMAGE_ML} — повреждение построек через {@code DamageBudget} на стороне мода.
 *
 * <p>Детерминизм: случайность — {@link SplittableRandom} от зерна мира и номера жребия, номер сохраняется.
 */
public final class InducedSeismicity {
    public enum Light { GREEN, AMBER, RED }

    public static final double DEFAULT_THRESHOLD_FRAC = 0.6;
    /** [BALANCE] Событий в секунду на единицу относительного превышения порога (10 % сверх порога — раз в час). */
    public static final double RATE_PER_S = 1.0 / 360;
    public static final double MIN_ML = 0.5, MAX_ML = 4.0, B_VALUE = 1.0;
    public static final double AMBER_ML = 2.3, RED_ML = 2.9, DAMAGE_ML = 3.4;
    /** Окно светофора, с (6 ч). */
    public static final double WINDOW_S = 6 * 3600;
    static final int RING = 16;

    private final long seed;
    private long draws;
    private double timeS;
    private final double[] evT = new double[RING];
    private final double[] evM = new double[RING];
    private int evCount;
    private int evHead;

    public InducedSeismicity(long seed) {
        this.seed = seed;
    }

    /**
     * Шаг: забойное давление закачки {@code injectionPa} против порога {@code thresholdFrac·lithostaticPa}.
     *
     * @return магнитуда события на этом шаге или NaN
     */
    public double step(double dt, double injectionPa, double lithostaticPa, double thresholdFrac) {
        if (!(dt > 0)) return Double.NaN;
        timeS += dt;
        double thr = thresholdFrac * lithostaticPa;
        if (!(thr > 0) || !(injectionPa > thr)) return Double.NaN;
        double rate = RATE_PER_S * (injectionPa / thr - 1);
        double p = 1 - Math.exp(-rate * dt);
        SplittableRandom r = new SplittableRandom(seed ^ (0x9E3779B97F4A7C15L * ++draws));
        if (r.nextDouble() >= p) return Double.NaN;
        double u = Math.max(1e-12, r.nextDouble());
        double ml = Math.min(MAX_ML, MIN_ML - Math.log10(u) / B_VALUE);
        evT[evHead] = timeS;
        evM[evHead] = ml;
        evHead = (evHead + 1) % RING;
        evCount = Math.min(RING, evCount + 1);
        return ml;
    }

    /** Наибольшая магнитуда за окно светофора или NaN. */
    public double peakMl() {
        double peak = Double.NaN;
        for (int i = 0; i < evCount; i++)
            if (timeS - evT[i] <= WINDOW_S && !(evM[i] <= peak)) peak = evM[i];
        return peak;
    }

    /** Событий за окно светофора. */
    public int eventsInWindow() {
        int n = 0;
        for (int i = 0; i < evCount; i++) if (timeS - evT[i] <= WINDOW_S) n++;
        return n;
    }

    public Light light() {
        double m = peakMl();
        if (Double.isNaN(m) || m < AMBER_ML) return Light.GREEN;
        return m < RED_ML ? Light.AMBER : Light.RED;
    }

    public void save(StateWriter out) {
        out.putLong("draws", draws);
        out.putDouble("t_s", timeS);
        out.putDoubles("ev_t", evT, evCount);
        out.putDoubles("ev_m", evM, evCount);
        out.putInt("ev_head", evHead);
    }

    public void load(StateReader in) {
        draws = in.optLong("draws", 0);
        double t = in.optDouble("t_s", 0);
        timeS = Double.isFinite(t) ? t : 0;
        double[] ts = in.has("ev_t") ? in.getDoubles("ev_t") : new double[0];
        double[] ms = in.has("ev_m") ? in.getDoubles("ev_m") : new double[0];
        evCount = Math.min(RING, Math.min(ts.length, ms.length));
        System.arraycopy(ts, 0, evT, 0, evCount);
        System.arraycopy(ms, 0, evM, 0, evCount);
        int h = in.optInt("ev_head", evCount % RING);
        evHead = h >= 0 && h < RING ? h : evCount % RING;
    }
}
