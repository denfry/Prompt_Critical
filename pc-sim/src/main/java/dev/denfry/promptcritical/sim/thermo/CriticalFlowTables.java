package dev.denfry.promptcritical.sim.thermo;

import static dev.denfry.promptcritical.sim.thermo.CriticalFlowFormat.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Табличная {@link CriticalFlowTable} поверх ресурса {@link CriticalFlowFormat#RESOURCE}:
 * билинейная интерполяция G_c² по (ln p₀, ξ), где ξ — дробный номер узла в строке
 * (раскладка узлов — {@link CriticalFlowFormat}), а границы сегментов h_f(p₀), h_g(p₀)
 * интерполируются по ln p₀ линейно. Билинейная,
 * а не бикубическая: G_c имеет излом на линии насыщения, и кубика дала бы выбросы.
 * За пределами сетки h₀ прижимается к краю, p₀ выше 22 МПа — тоже; ниже 0,15 МПа
 * поток масштабируется ∝ p₀ (как у газа при фиксированной температуре торможения).
 */
final class CriticalFlowTables implements CriticalFlowTable {
    private static final CriticalFlowTables[] BY_MODEL = load();

    private final Model model;
    private final int np, nh, k0, k1, k2;
    private final double x0, dx, hMin, hMax;
    private final double[] hf, hg;
    private final float[] g2;

    private CriticalFlowTables(Model model, int np, double x0, double x1, double hMin, double hMax, int[] intervals,
            double[] hf, double[] hg, float[] g2) {
        this.model = model;
        this.np = np;
        this.x0 = x0;
        this.dx = (x1 - x0) / (np - 1);
        this.hMin = hMin;
        this.hMax = hMax;
        this.k0 = intervals[0];
        this.k1 = intervals[1];
        this.k2 = intervals[2];
        this.nh = k0 + k1 + k2 + 1;
        this.hf = hf;
        this.hg = hg;
        this.g2 = g2;
    }

    static CriticalFlowTables get(Model model) {
        return BY_MODEL[model.ordinal()];
    }

    @Override
    public Model model() {
        return model;
    }

    @Override
    public double criticalMassFlux(double p0, double h) {
        double x = Math.log(p0), scale = 1.0;
        if (x < x0) {
            scale = p0 / Math.exp(x0);
            x = x0;
        }
        double fi = Math.min((x - x0) / dx, np - 1.0);
        int i = Math.min((int) fi, np - 2);
        double a = fi - i;
        double f = hf[i] + a * (hf[i + 1] - hf[i]), v = hg[i] + a * (hg[i + 1] - hg[i]);
        double u;
        if (h < f) u = (1 - Math.sqrt(Math.min((f - h) / (f - hMin), 1.0))) * k0;
        else if (h <= v) u = k0 + Math.sqrt((h - f) / (v - f)) * k1;
        else u = k0 + k1 + Math.min((h - v) / (hMax - v), 1.0) * k2;
        int j = Math.min((int) u, nh - 2);
        double b = u - j;
        int k = i * nh + j;
        double lo = g2[k] + b * (g2[k + 1] - g2[k]);
        double hi = g2[k + nh] + b * (g2[k + nh + 1] - g2[k + nh]);
        return scale * Math.sqrt(Math.max(lo + a * (hi - lo), 0.0));
    }

    private static CriticalFlowTables[] load() {
        try (InputStream in = CriticalFlowTables.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
            byte[] all = in.readAllBytes();
            CRC32 crc = new CRC32();
            crc.update(all, 0, all.length - 4);
            if ((int) crc.getValue() != ByteBuffer.wrap(all, all.length - 4, 4).getInt()) {
                throw new IllegalStateException("critical flow table checksum mismatch");
            }
            ByteBuffer b = ByteBuffer.wrap(all, 0, all.length - 4);
            if (b.getInt() != MAGIC) throw new IllegalStateException("not a critical flow table file");
            int version = b.getInt();
            if (version != VERSION) {
                throw new IllegalStateException("critical flow table version " + version + ", expected " + VERSION);
            }
            int np = b.getInt();
            double x0 = b.getDouble(), x1 = b.getDouble();
            double hMin = b.getDouble(), hMax = b.getDouble();
            int[] intervals = new int[SEGMENTS];
            int nh = 1;
            for (int s = 0; s < SEGMENTS; s++) nh += intervals[s] = b.getInt();
            double[] hf = new double[np], hg = new double[np];
            for (int i = 0; i < np; i++) hf[i] = b.getDouble();
            for (int i = 0; i < np; i++) hg[i] = b.getDouble();
            Model[] models = Model.values();
            if (b.getInt() != models.length) throw new IllegalStateException("critical flow model count mismatch");
            var out = new CriticalFlowTables[models.length];
            for (Model m : models) {
                float[] g2 = new float[np * nh];
                b.asFloatBuffer().get(g2);
                b.position(b.position() + 4 * g2.length);
                out[m.ordinal()] = new CriticalFlowTables(m, np, x0, x1, hMin, hMax, intervals, hf, hg, g2);
            }
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
