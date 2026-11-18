package dev.denfry.promptcritical.sim.environment;

import java.util.HashMap;
import java.util.Map;

/**
 * Слой B загрязнения (14 §14.3.3): осаждённые сера, азот и пыль по чанкам, мг/м². Разреженная сетка: ключ чанка
 * {@code long} (x, z) и массив из трёх веществ.
 *
 * <pre>dD/dt = v_d·c + Λ·h_mix·c·1_rain − (k₀ + k_r·1_rain + k_v·f_veg)·D + k_x·Σ_{N4} w_ij·(D_j − D_i)</pre>
 *
 * v_d: SO₂ 0,008, NO₂ 0,003, PM 0,010 м/с; Λ: 5·10⁻⁵, 2·10⁻⁵, 10⁻⁴ с⁻¹ (Seinfeld & Pandis, гл. 19–20); h_mix 500 м;
 * k₀ = 1/(24 ч), k_r = 1/(6 ч), k_v = 1/(12 ч), k_x = 1/(4 ч), w_ij = 1 ± 0,5·cosθ по ветру. Явная схема с подшагами,
 * чтобы Δt·(k₀ + k_r + k_v + 4k_x(1 + b)) ≤ 0,5. Ячейки слабее {@value #MIN_LOAD} мг/м² удаляются.
 */
public final class PollutionField {
    public static final double[] VD = {0.008, 0.003, 0.010}, WASHOUT = {5e-5, 2e-5, 1e-4};
    public static final double H_MIX = 500, K0 = 1 / (24 * 3600.0), K_RAIN = 1 / (6 * 3600.0), K_VEG = 1 / (12 * 3600.0),
            K_SPREAD = 1 / (4 * 3600.0), WIND_BIAS = 0.5, MIN_LOAD = 0.1, STABILITY = 0.5;
    public static final int MAX_CELLS = 4096;

    private final Map<Long, double[]> load = new HashMap<>();

    /** Отношение нагрузок соседних чанков вдали от источников в сухую погоду без растительности (§14.3.3): 2/3. */
    public static double decayRatio() {
        double a = K0 / (2 * K_SPREAD);
        return 1 + a - Math.sqrt((1 + a) * (1 + a) - 1);
    }

    /** Полная масса поля, мг/м²·чанк, по веществу. */
    public double total(int species) {
        double t = 0;
        for (double[] d : load.values()) t += d[species];
        return t;
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xffffffffL);
    }

    public static int cx(long key) {
        return (int) (key >> 32);
    }

    public static int cz(long key) {
        return (int) key;
    }

    /** Нагрузка чанка по веществам, мг/м² (копия; нули для чистого). */
    public double[] load(int cx, int cz) {
        double[] d = load.get(key(cx, cz));
        return d == null ? new double[3] : d.clone();
    }

    public void set(int cx, int cz, double[] d) {
        load.put(key(cx, cz), d.clone());
    }

    public int cells() {
        return load.size();
    }

    public Map<Long, double[]> view() {
        return java.util.Collections.unmodifiableMap(load);
    }

    /** Источник для шага: приземная концентрация в чанке, кг/м³, по веществам; доля растительности. */
    public interface Air {
        double concentration(int cx, int cz, int species);

        double vegetation(int cx, int cz);
    }

    /**
     * Шаг поля длиной {@code dtS} физических секунд.
     *
     * @param air      концентрации слоя A (по ключам {@code sources} — чанки, где она не нулевая)
     * @param sources  чанки с ненулевой концентрацией (в ядрах активных источников)
     * @param windDirRad направление, куда дует ветер (0 — на +x)
     */
    public void step(double dtS, Air air, Iterable<Long> sources, boolean raining, double windDirRad) {
        double rate = K0 + K_RAIN + K_VEG + 4 * K_SPREAD * (1 + WIND_BIAS);
        int sub = Math.max(1, (int) Math.ceil(dtS * rate / STABILITY));
        double h = dtS / sub;
        for (Long k : sources) load.computeIfAbsent(k, kk -> new double[3]);
        for (int s = 0; s < sub; s++) substep(h, air, raining, windDirRad);
        load.values().removeIf(d -> Math.max(d[0], Math.max(d[1], d[2])) < MIN_LOAD);
    }

    private static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private void substep(double h, Air air, boolean raining, double windDirRad) {
        Map<Long, double[]> next = new HashMap<>(load.size() * 2);
        double wx = Math.cos(windDirRad), wz = Math.sin(windDirRad);
        for (Map.Entry<Long, double[]> e : load.entrySet()) {
            int x = cx(e.getKey()), z = cz(e.getKey());
            double[] d = e.getValue();
            double veg = air.vegetation(x, z);
            double clean = K0 + (raining ? K_RAIN : 0) + K_VEG * veg;
            double[] out = next.computeIfAbsent(e.getKey(), k -> new double[3]);
            for (int s = 0; s < 3; s++) {
                double c = air.concentration(x, z, s) * 1e6; // кг/м³ → мг/м³
                double dep = VD[s] * c + (raining ? WASHOUT[s] * H_MIX * c : 0);
                out[s] += d[s] + h * (dep - clean * d[s]);
            }
            // перенос в соседей: поток из i в j с весом по ветру; масса сохраняется
            for (int[] n : N4) {
                double w = 1 + WIND_BIAS * (n[0] * wx + n[1] * wz);
                long nk = key(x + n[0], z + n[1]);
                double[] nd = next.computeIfAbsent(nk, k -> new double[3]);
                for (int s = 0; s < 3; s++) {
                    double flow = h * K_SPREAD * w * d[s];
                    out[s] -= flow;
                    nd[s] += flow;
                }
            }
        }
        load.clear();
        load.putAll(next);
    }
}
