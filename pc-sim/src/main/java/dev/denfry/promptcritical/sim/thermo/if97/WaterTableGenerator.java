package dev.denfry.promptcritical.sim.thermo.if97;

import static dev.denfry.promptcritical.sim.thermo.WaterTableFormat.*;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * Генератор {@code if97_tables.bin} из эталонной IF97 (01 §1.3.2): задача
 * {@code :pc-sim:generateWaterTables}. Аргумент — путь выходного файла.
 */
public final class WaterTableGenerator {
    static final int SAT_N = 1024;
    static final int NP = 192, NH = 400;
    /** Полоса сгущения строк вокруг p_c и число подстрок в каждой грубой ячейке, попавшей в неё. */
    static final double REFINE_DP = 2e6;
    static final int REFINE = 8;
    static final double P_MAX = 25e6, H_STEP = 15e3;
    static final int NR = 2048;
    /** Шаги конечных разностей по h и ln p для производных, не известных аналитически. */
    static final double DH_FD = 200.0, DX_FD = 1e-4;

    private WaterTableGenerator() {}

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
        var bytes = new ByteArrayOutputStream(1 << 23);
        var o = new DataOutputStream(bytes);
        o.writeInt(MAGIC);
        o.writeInt(VERSION);
        writeSaturation(o);
        writePh(o);
        writeDomeBoundary(o);
        o.flush();
        return bytes.toByteArray();
    }

    // ---------------------------------------------------------------- насыщение

    static double satLnP(int i) {
        double a = Math.log(If97.P_MIN), b = Math.log(If97.P_C);
        double s = i / (double) (SAT_N - 1);
        return a + (b - a) * (1 - (1 - s) * (1 - s));
    }

    /** 17 величин насыщения; у самой критической точки — чуть ниже p_c, где они конечны. */
    static double[] sat17(double lnP) {
        double p = Math.min(Math.exp(lnP), If97.P_C * (1 - 1e-7));
        If97Point l = If97.satLiquid(p), g = If97.satVapor(p);
        double dp = p * 1e-6;
        double dtdp = (If97.tsat(p + dp) - If97.tsat(p - dp)) / (2 * dp);
        return new double[] {l.t(), l.v(), g.v(), l.h(), g.h(), l.u(), g.u(), l.s(), g.s(), l.cp(), g.cp(),
            l.mu(), g.mu(), l.k(), g.k(), Transport.surfaceTension(l.t()), dtdp};
    }

    private static void writeSaturation(DataOutputStream o) throws IOException {
        double[][] val = new double[SAT_N][];
        double[][] der = new double[SAT_N][];
        IntStream.range(0, SAT_N).parallel().forEach(i -> {
            double x = satLnP(i);
            val[i] = sat17(x);
            double gap = i == 0 ? satLnP(1) - x : i == SAT_N - 1 ? x - satLnP(i - 1)
                    : Math.min(x - satLnP(i - 1), satLnP(i + 1) - x);
            double d = 0.01 * gap;
            double[] r = new double[SAT_Q];
            if (i == SAT_N - 1) { // односторонняя разность второго порядка
                double[] m1 = sat17(x - d), m2 = sat17(x - 2 * d);
                for (int q = 0; q < SAT_Q; q++) r[q] = (3 * val[i][q] - 4 * m1[q] + m2[q]) / (2 * d);
            } else {
                double[] pl = sat17(x + d), mi = sat17(x - d);
                for (int q = 0; q < SAT_Q; q++) r[q] = (pl[q] - mi[q]) / (2 * d);
            }
            der[i] = r;
        });
        o.writeInt(SAT_N);
        o.writeDouble(satLnP(0));
        o.writeDouble(satLnP(SAT_N - 1));
        for (int q = 0; q < SAT_Q; q++) for (int i = 0; i < SAT_N; i++) o.writeDouble(val[i][q]);
        for (int q = 0; q < SAT_Q; q++) for (int i = 0; i < SAT_N; i++) o.writeDouble(der[i][q]);
    }

    // ---------------------------------------------------------------- (p, h)

    static double phLnPMin() {
        return Math.log(If97.P_MIN);
    }

    static double phDlnP() {
        return (Math.log(P_MAX) - phLnPMin()) / (NP - 1);
    }

    /**
     * Первая строка каждого грубого узла: грубые ячейки, задевающие полосу
     * p_c ± {@link #REFINE_DP}, делятся на {@link #REFINE} подстрок — шаг 1,2 МПа
     * там шире всей околокритической области.
     */
    static int[] firstRows() {
        double lo = Math.log(If97.P_C - REFINE_DP), hi = Math.log(If97.P_C + REFINE_DP);
        int[] first = new int[NP];
        int row = 0;
        for (int c = 0; c < NP; c++) {
            first[c] = row;
            double a = phLnPMin() + c * phDlnP();
            row += c < NP - 1 && a < hi && a + phDlnP() > lo ? REFINE : 1;
        }
        return first;
    }

    static double[] rowLnP() {
        int[] first = firstRows();
        double[] x = new double[first[NP - 1] + 1];
        for (int c = 0; c < NP - 1; c++) {
            int n = first[c + 1] - first[c];
            for (int k = 0; k < n; k++) x[first[c] + k] = phLnPMin() + (c + k / (double) n) * phDlnP();
        }
        x[x.length - 1] = phLnPMin() + (NP - 1) * phDlnP();
        return x;
    }

    private static void writePh(DataOutputStream o) throws IOException {
        double[] xs = rowLnP();
        double[][] rows = new double[xs.length][];
        IntStream.range(0, xs.length).parallel().forEach(i -> rows[i] = phRow(xs, i));
        o.writeInt(NP);
        o.writeDouble(phLnPMin());
        o.writeDouble(phDlnP());
        for (int c : firstRows()) o.writeInt(c);
        o.writeInt(NH);
        o.writeDouble(0.0);
        o.writeDouble(H_STEP);
        for (double[] row : rows) {
            for (int j = 0; j < NH; j++) {
                for (int q = 0; q < PH_Q; q++) {
                    double d = row[j * PH_Q + q];
                    if (q < PH_T) o.writeDouble(d); // v: float32 не различает сжимаемость жидкости
                    else o.writeFloat((float) d);
                }
            }
        }
    }

    static double[] phRow(double[] xs, int i) {
        double p = Math.max(Math.exp(xs[i]), If97.P_MIN);
        boolean sub = p < If97.P_C;
        double hf = sub ? If97.satLiquid(p).h() : Double.NaN;
        double hg = sub ? If97.satVapor(p).h() : Double.NaN;
        double[] row = new double[NH * PH_Q];
        // Узлы глубже порога внутри купола не считаются, а копируются с соседних. Порог —
        // два шага по h плюс сдвиг h', h'' к соседним строкам: ячейка между строками
        // должна целиком лежать на посчитанных узлах своей ветви.
        double deep = 2 * H_STEP;
        for (int k = Math.max(i - 1, 0); sub && k <= Math.min(i + 1, xs.length - 1); k++) {
            double pk = Math.min(Math.max(Math.exp(xs[k]), If97.P_MIN), If97.P_C * (1 - 1e-7));
            deep = Math.max(deep, 2 * H_STEP + Math.max(Math.abs(If97.satLiquid(pk).h() - hf),
                    Math.abs(If97.satVapor(pk).h() - hg)));
        }
        double cpf = sub ? If97.satLiquid(p).cp() : Double.NaN;
        double cpg = sub ? If97.satVapor(p).cp() : Double.NaN;
        boolean[] done = new boolean[NH];
        for (int j = 0; j < NH; j++) {
            double h = j * H_STEP;
            Boolean liquid = null; // null — над критическим давлением ветвь не выбирается
            double cpLimit = Double.POSITIVE_INFINITY;
            if (sub) {
                if (h - hf > deep && hg - h > deep) continue; // глубоко в куполе
                liquid = h - hf <= hg - h;
                // у спинодали c_p уходит в бесконечность: такие узлы не годятся для интерполяции
                if (h > hf && h < hg) cpLimit = 3 * (liquid ? cpf : cpg);
            }
            done[j] = node(p, h, liquid, cpLimit, row, j * PH_Q);
        }
        // глубокие узлы купола: копия ближайшего посчитанного узла той же стороны
        for (int j = 0; j < NH; j++) {
            if (done[j]) continue;
            boolean liquidSide = j * H_STEP - hf <= hg - j * H_STEP;
            int src = j;
            while (!done[src]) src += liquidSide ? -1 : 1; // устойчивые узлы у границ купола всегда посчитаны
            System.arraycopy(row, src * PH_Q, row, j * PH_Q, PH_Q);
        }
        return row;
    }

    private static If97Point at(double p, double h, Boolean liquid) {
        return liquid == null ? If97.ph(p, h) : If97.phBranch(p, h, liquid);
    }

    /** То же для вспомогательных точек разностей: вне области эталона — null. */
    private static If97Point safeAt(double p, double h, Boolean liquid) {
        if (p < If97.P_MIN) return null;
        try {
            return at(p, h, liquid);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Значения и производные узла по ln p и h (01 §1.3.2(б)).
     *
     * @return false, если метастабильного состояния нет или оно у самой спинодали
     */
    private static boolean node(double p, double h, Boolean liquid, double cpLimit, double[] row, int off) {
        If97Point c = at(p, h, liquid);
        if (c == null || !(c.cp() > 0 && c.cp() < cpLimit)) return false;
        If97Point cPlus = at(p, h + DH_FD, liquid), cMinus = at(p, h - DH_FD, liquid);
        double[] d = firstDerivatives(c);
        double[] plus = cPlus != null ? firstDerivatives(cPlus) : d;
        double[] minus = cMinus != null ? firstDerivatives(cMinus) : d;
        double span = ((cPlus != null ? 1 : 0) + (cMinus != null ? 1 : 0)) * DH_FD;
        if (span == 0) return false;
        double[] values = {c.v(), c.t(), c.s()};
        for (int g = 0; g < 3; g++) {
            row[off + 4 * g] = values[g];
            row[off + 4 * g + 1] = d[2 * g];
            row[off + 4 * g + 2] = d[2 * g + 1];
            row[off + 4 * g + 3] = (plus[2 * g] - minus[2 * g]) / span;
        }
        cpDerivatives(p, h, liquid, c, cPlus, cMinus, row, off + PH_CP);
        row[off + PH_MU] = c.mu();
        row[off + PH_K] = c.k();
        return true;
    }

    /** c_p и её производные по ln p, h и смешанная — центральными разностями по эталону. */
    private static void cpDerivatives(double p, double h, Boolean liquid, If97Point c, If97Point hp, If97Point hm,
            double[] row, int off) {
        row[off] = c.cp();
        row[off + 1] = row[off + 2] = row[off + 3] = Double.NaN;
        double ep = Math.exp(DX_FD), em = Math.exp(-DX_FD);
        If97Point xp = safeAt(p * ep, h, liquid), xm = safeAt(p * em, h, liquid);
        If97Point pp = safeAt(p * ep, h + DH_FD, liquid), pm = safeAt(p * ep, h - DH_FD, liquid);
        If97Point mp = safeAt(p * em, h + DH_FD, liquid), mm = safeAt(p * em, h - DH_FD, liquid);
        if (xp != null && xm != null) row[off + 1] = (xp.cp() - xm.cp()) / (2 * DX_FD);
        else if (xp != null) row[off + 1] = (xp.cp() - c.cp()) / DX_FD; // край таблицы
        else if (xm != null) row[off + 1] = (c.cp() - xm.cp()) / DX_FD;
        if (hp != null && hm != null) row[off + 2] = (hp.cp() - hm.cp()) / (2 * DH_FD);
        if (pp != null && pm != null && mp != null && mm != null) {
            row[off + 3] = (pp.cp() - pm.cp() - mp.cp() + mm.cp()) / (4 * DX_FD * DH_FD);
        }
    }

    /** {∂v/∂ln p, ∂v/∂h, ∂T/∂ln p, ∂T/∂h, ∂s/∂ln p, ∂s/∂h} при постоянной второй переменной. */
    static double[] firstDerivatives(If97Point c) {
        double v = c.v(), t = c.t(), p = c.p(), cp = c.cp();
        double dvdpT = -c.drhodpT() / (c.rho() * c.rho());
        double dtdpH = -(v - t * c.dvdtP()) / cp;
        double dvdpH = dvdpT + c.dvdtP() * dtdpH;
        return new double[] {p * dvdpH, c.dvdtP() / cp, p * dtdpH, 1.0 / cp, -p * v / t, 1.0 / t};
    }

    // ---------------------------------------------------------------- граница купола u_b(ln ρ)

    static double rhoMinDome() {
        return If97.satVapor(If97.P_MIN).rho();
    }

    private static void writeDomeBoundary(DataOutputStream o) throws IOException {
        int sweep = 20_000;
        double tLo = 277.13; // выше максимума плотности воды ρ' монотонна по T
        double[] lr = new double[2 * sweep], ub = new double[2 * sweep];
        IntStream.range(0, sweep).parallel().forEach(k -> {
            double s = k / (double) (sweep - 1);
            double t = If97.T_MIN + 0.01 + (If97.T_C - 1e-6 - If97.T_MIN - 0.01) * (1 - (1 - s) * (1 - s));
            double p = Math.min(If97.psat(t), If97.P_C * (1 - 1e-9));
            If97Point g = If97.satVapor(p);
            lr[k] = Math.log(g.rho());
            ub[k] = g.u();
            double tl = Math.max(t, tLo);
            If97Point l = If97.satLiquid(Math.min(If97.psat(tl), If97.P_C * (1 - 1e-9)));
            lr[2 * sweep - 1 - k] = Math.log(l.rho());
            ub[2 * sweep - 1 - k] = l.u();
        });
        double lo = lr[0], hi = lr[2 * sweep - 1];
        double dr = (hi - lo) / (NR - 1);
        o.writeInt(NR);
        o.writeDouble(lo);
        o.writeDouble(dr);
        int k = 0;
        for (int n = 0; n < NR; n++) {
            double x = Math.min(lo + n * dr, hi);
            while (k < lr.length - 2 && lr[k + 1] < x) k++;
            double w = lr[k + 1] > lr[k] ? (x - lr[k]) / (lr[k + 1] - lr[k]) : 0;
            o.writeDouble(ub[k] + w * (ub[k + 1] - ub[k]));
        }
    }
}
