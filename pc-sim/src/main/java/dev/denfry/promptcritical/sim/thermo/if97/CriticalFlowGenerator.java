package dev.denfry.promptcritical.sim.thermo.if97;

import static dev.denfry.promptcritical.sim.thermo.CriticalFlowFormat.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * Генератор {@code critical_flow.bin} из эталонной IF97 (01 §1.3.9): задача
 * {@code :pc-sim:generateCriticalFlowTables}. Аргумент — путь выходного файла.
 *
 * <p>Для состояния торможения (p₀, h₀) — изоэнтропическое расширение s = s₀ вниз
 * по давлению; G_c — максимум G(p) по давлению в горле. HEM:
 * G = ρ(p, s₀)·√(2(h₀ − h(p, s₀))). Муди (1965): в двухфазной области скольжение
 * S = (ρ_f/ρ_g)^{1/3} и G² = 2(h₀ − h) / ([x v_g + S(1 − x) v_f]²·[x + (1 − x)/S²]),
 * в однофазной — как HEM.
 */
public final class CriticalFlowGenerator {
    static final int NP = 96;
    /** Интервалы по h₀ в сегментах недогрев | смесь | перегрев; узлов NH = 200. */
    static final int[] INTERVALS = {63, 80, 56};
    static final int NH = INTERVALS[0] + INTERVALS[1] + INTERVALS[2] + 1;
    static final double P_MIN = 0.15e6, P_MAX = 22e6, H_MIN = 400e3, H_MAX = 2900e3;
    /** Модели в порядке {@code CriticalFlowTable.Model}: HEM, MOODY. */
    static final int MODELS = 2;
    /** Поиск горла: от p₀ вниз до p₀·RATIO_MIN; узлов перебора на гладкий кусок, шагов золотого сечения. */
    static final double RATIO_MIN = 0.01;
    static final int SCAN = 60, GOLDEN = 36;

    /** Критический поток, кг/(м²·с), и давление в горле, Па. */
    public record Choke(double massFlux, double throatPressure) {}

    private CriticalFlowGenerator() {}

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        byte[] body = build();
        CRC32 crc = new CRC32();
        crc.update(body);
        try (var o = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))) {
            o.write(body);
            o.writeInt((int) crc.getValue());
        }
    }

    static byte[] build() throws IOException {
        double[] hf = new double[NP], hg = new double[NP];
        for (int i = 0; i < NP; i++) {
            double p = Math.exp(lnP(i));
            hf[i] = If97.satLiquid(p).h();
            hg[i] = If97.satVapor(p).h();
        }
        float[][] g = new float[MODELS][NP * NH];
        IntStream.range(0, NP * NH).parallel().forEach(k -> {
            int i = k / NH;
            Choke[] c = solve(Math.exp(lnP(i)), h(k % NH, hf[i], hg[i]));
            for (int m = 0; m < MODELS; m++) g[m][k] = (float) (c[m].massFlux() * c[m].massFlux());
        });
        var bytes = new ByteArrayOutputStream(64 + 16 * NP + MODELS * NP * NH * 4);
        var o = new DataOutputStream(bytes);
        o.writeInt(MAGIC);
        o.writeInt(VERSION);
        o.writeInt(NP);
        o.writeDouble(Math.log(P_MIN));
        o.writeDouble(Math.log(P_MAX));
        o.writeDouble(H_MIN);
        o.writeDouble(H_MAX);
        for (int n : INTERVALS) o.writeInt(n);
        for (double v : hf) o.writeDouble(v);
        for (double v : hg) o.writeDouble(v);
        o.writeInt(MODELS);
        for (float[] m : g) {
            for (float v : m) o.writeFloat(v);
        }
        o.flush();
        return bytes.toByteArray();
    }

    static double lnP(int i) {
        return Math.log(P_MIN) + (Math.log(P_MAX) - Math.log(P_MIN)) * i / (NP - 1);
    }

    /** h₀ узла j в строке с границами сегментов h_f, h_g; в недогреве и смеси узлы сгущены к h_f квадратично. */
    static double h(int j, double hf, double hg) {
        if (j <= INTERVALS[0]) {
            double t = 1 - j / (double) INTERVALS[0];
            return hf - (hf - H_MIN) * t * t;
        }
        j -= INTERVALS[0];
        if (j <= INTERVALS[1]) {
            double t = j / (double) INTERVALS[1];
            return hf + (hg - hf) * t * t;
        }
        return hg + (H_MAX - hg) * (j - INTERVALS[1]) / INTERVALS[2];
    }

    /** Эталонный HEM-поток по параметрам торможения. */
    public static Choke hem(double p0, double h0) {
        return solve(p0, h0)[0];
    }

    /** Эталонный поток по Муди по параметрам торможения. */
    public static Choke moody(double p0, double h0) {
        return solve(p0, h0)[1];
    }

    /**
     * {HEM, MOODY}. G(p) на изоэнтропе гладкая по кускам: излом там, где изоэнтропа
     * пересекает линию насыщения (у слабо недогретой жидкости здесь острый пик —
     * горло на линии вскипания). Поэтому максимум ищется на каждом гладком куске
     * (перебор по ln p и золотое сечение у лучшего узла) и в самой точке пересечения.
     */
    static Choke[] solve(double p0, double h0) {
        double s0 = If97.ph(p0, h0).s();
        double hi = Math.log(p0), lo = Math.log(p0 * RATIO_MIN);
        double cross = saturationCrossing(p0, s0, lo, hi);
        Choke[] best = new Choke[MODELS];
        double[] f = new double[MODELS];
        if (Double.isNaN(cross)) {
            piece(h0, s0, lo, hi, best);
        } else {
            piece(h0, s0, lo, cross, best);
            piece(h0, s0, cross, hi, best);
            flux(h0, s0, Math.exp(cross), f);
            for (int m = 0; m < MODELS; m++) {
                if (f[m] > best[m].massFlux()) best[m] = new Choke(f[m], Math.exp(cross));
            }
        }
        return best;
    }

    /** ln p пересечения изоэнтропы s₀ с линией насыщения на (lo, hi) или NaN, если его нет. */
    static double saturationCrossing(double p0, double s0, double lo, double hi) {
        boolean liquid;
        if (s0 < If97.satLiquid(p0).s()) liquid = true;
        else if (s0 > If97.satVapor(p0).s()) liquid = false;
        else return Double.NaN; // смесь при p₀ остаётся смесью при расширении
        // s_f(p) растёт с давлением, s_g(p) падает: знак (s_sat(p) − s₀) меняется один раз
        double a = lo, b = hi;
        double fa = satEntropy(Math.exp(a), liquid) - s0;
        if (liquid ? fa > 0 : fa < 0) return Double.NaN;
        for (int it = 0; it < 60; it++) {
            double c = 0.5 * (a + b);
            double fc = satEntropy(Math.exp(c), liquid) - s0;
            if ((fc < 0) == (fa < 0)) {
                a = c;
                fa = fc;
            } else {
                b = c;
            }
        }
        return 0.5 * (a + b);
    }

    private static double satEntropy(double p, boolean liquid) {
        return liquid ? If97.satLiquid(p).s() : If97.satVapor(p).s();
    }

    /** Максимумы обеих моделей на куске [lo, hi] по ln p; улучшает {@code best}. */
    private static void piece(double h0, double s0, double lo, double hi, Choke[] best) {
        double[] top = new double[MODELS];
        int[] at = new int[MODELS];
        double[] f = new double[MODELS];
        for (int i = 0; i <= SCAN; i++) {
            flux(h0, s0, Math.exp(hi - (hi - lo) * i / SCAN), f);
            for (int m = 0; m < MODELS; m++) {
                if (f[m] > top[m]) {
                    top[m] = f[m];
                    at[m] = i;
                }
            }
        }
        for (int m = 0; m < MODELS; m++) {
            double a = hi - (hi - lo) * Math.max(at[m] - 1, 0) / SCAN;
            double b = hi - (hi - lo) * Math.min(at[m] + 1, SCAN) / SCAN;
            Choke c = golden(h0, s0, b, a, m);
            if (c.massFlux() < top[m]) c = new Choke(top[m], Math.exp(hi - (hi - lo) * at[m] / SCAN));
            if (best[m] == null || c.massFlux() > best[m].massFlux()) best[m] = c;
        }
    }

    /** Максимум G на [lo, hi] по ln p. */
    private static Choke golden(double h0, double s0, double lo, double hi, int model) {
        final double r = (Math.sqrt(5) - 1) / 2;
        double[] f = new double[MODELS];
        double c = hi - r * (hi - lo), d = lo + r * (hi - lo);
        double fc = flux(h0, s0, Math.exp(c), f)[model], fd = flux(h0, s0, Math.exp(d), f)[model];
        for (int it = 0; it < GOLDEN; it++) {
            if (fc > fd) {
                hi = d;
                d = c;
                fd = fc;
                c = hi - r * (hi - lo);
                fc = flux(h0, s0, Math.exp(c), f)[model];
            } else {
                lo = c;
                c = d;
                fc = fd;
                d = lo + r * (hi - lo);
                fd = flux(h0, s0, Math.exp(d), f)[model];
            }
        }
        double x = fc > fd ? c : d;
        return new Choke(Math.max(fc, fd), Math.exp(x));
    }

    /** G(p) на изоэнтропе s₀ для обеих моделей; записывает в {@code out} и возвращает его. */
    private static double[] flux(double h0, double s0, double p, double[] out) {
        If97Point st = If97.ps(p, s0);
        double dh = h0 - st.h();
        if (dh <= 0) {
            out[0] = out[1] = 0;
            return out;
        }
        out[0] = st.rho() * Math.sqrt(2 * dh);
        out[1] = out[0];
        if (st.region() == 4) {
            double x = st.x(), vf = If97.satLiquid(p).v(), vg = If97.satVapor(p).v();
            double slip = Math.cbrt(vg / vf);
            double a = x * vg + slip * (1 - x) * vf, b = x + (1 - x) / (slip * slip);
            out[1] = Math.sqrt(2 * dh / (a * a * b));
        }
        return out;
    }
}
