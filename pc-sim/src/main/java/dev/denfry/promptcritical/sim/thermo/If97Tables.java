package dev.denfry.promptcritical.sim.thermo;

import static dev.denfry.promptcritical.sim.thermo.WaterTableFormat.*;

import dev.denfry.promptcritical.sim.thermo.if97.If97;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

/**
 * Табличная реализация {@link WaterProperties} (01 §1.3.2–1.3.4) поверх
 * ресурса {@link WaterTableFormat#RESOURCE}: кубические сплайны Эрмита на
 * линии насыщения, бикубическая интерполяция Эрмита в переменных (ln p, h),
 * граница купола u_b(ln ρ). Неизменяема и потокобезопасна; горячие методы не аллоцируют.
 */
public final class If97Tables implements WaterProperties {
    static final If97Tables INSTANCE = load();

    private static final double P_C = If97.P_C, T_C = If97.T_C;
    private static final double NEAR_CRIT_DP = 0.5e6, NEAR_CRIT_DT = 15.0;

    // линия насыщения: значения и производные по ln p, [q * sn + i]
    private final int sn;
    private final double sa, sb;
    private final double[] sx, sv, sd;

    // таблица (p, h): группы по 4 числа (значение, ∂/∂ln p, ∂/∂h, ∂²/∂ln p∂h)
    private static final int G_V = 0, G_T = 4, G_S = 8, G_CP = 12, G_MU = 16, G_K = 20, STRIDE = 24;
    // строки по ln p равномерны внутри грубой ячейки; у p_c грубые ячейки поделены
    private final int nc, np, nh;
    private final double x0, dxc, h0, dh, xMax, hMax;
    private final int[] firstRow;
    private final double[] rx, rw;
    private final double[] g;

    // граница купола u_b(ln ρ)
    private final int nr;
    private final double r0, dr;
    private final double[] ub;

    private If97Tables(ByteBuffer b) {
        if (b.getInt() != MAGIC) throw new IllegalStateException("not an IF97 table file");
        int version = b.getInt();
        if (version != VERSION) throw new IllegalStateException("IF97 table version " + version + ", expected " + VERSION);

        sn = b.getInt();
        sa = b.getDouble();
        sb = b.getDouble();
        sx = new double[sn];
        for (int i = 0; i < sn; i++) {
            double s = i / (double) (sn - 1);
            sx[i] = sa + (sb - sa) * (1 - (1 - s) * (1 - s));
        }
        sv = new double[SAT_Q * sn];
        sd = new double[SAT_Q * sn];
        b.asDoubleBuffer().get(sv);
        b.position(b.position() + 8 * sv.length);
        b.asDoubleBuffer().get(sd);
        b.position(b.position() + 8 * sd.length);

        nc = b.getInt();
        x0 = b.getDouble();
        dxc = b.getDouble();
        firstRow = new int[nc];
        for (int c = 0; c < nc; c++) firstRow[c] = b.getInt();
        np = firstRow[nc - 1] + 1;
        rx = new double[np];
        for (int c = 0; c < nc - 1; c++) {
            int n = firstRow[c + 1] - firstRow[c];
            for (int k = 0; k < n; k++) rx[firstRow[c] + k] = x0 + (c + k / (double) n) * dxc;
        }
        xMax = rx[np - 1] = x0 + (nc - 1) * dxc;
        rw = new double[np];
        for (int i = 0; i < np - 1; i++) rw[i] = rx[i + 1] - rx[i];
        rw[np - 1] = rw[np - 2];
        nh = b.getInt();
        h0 = b.getDouble();
        dh = b.getDouble();
        hMax = h0 + (nh - 1) * dh;
        double[] raw = new double[np * nh * PH_Q];
        for (int n = 0; n < np * nh; n++) {
            for (int q = 0; q < PH_Q; q++) raw[n * PH_Q + q] = q < PH_T ? b.getDouble() : b.getFloat();
        }
        g = expand(raw);

        nr = b.getInt();
        r0 = b.getDouble();
        dr = b.getDouble();
        ub = new double[nr];
        b.asDoubleBuffer().get(ub);
    }

    /**
     * Переносит v, T, s, c_p как есть, а производные μ, k (и c_p там, где генератор
     * их не определил) достраивает конечными разностями по соседним узлам.
     */
    private double[] expand(double[] raw) {
        double[] out = new double[np * nh * STRIDE];
        for (int i = 0; i < np; i++) {
            for (int j = 0; j < nh; j++) {
                int src = (i * nh + j) * PH_Q, dst = (i * nh + j) * STRIDE;
                System.arraycopy(raw, src, out, dst, 16);
                int ja = Math.max(j - 1, 0), jb = Math.min(j + 1, nh - 1);
                if (Double.isNaN(out[dst + G_CP + 1])) out[dst + G_CP + 1] = dX(raw, PH_CP, i, j);
                if (Double.isNaN(out[dst + G_CP + 2])) out[dst + G_CP + 2] = dH(raw, PH_CP, i, j);
                if (Double.isNaN(out[dst + G_CP + 3])) {
                    out[dst + G_CP + 3] = (dX(raw, PH_CP, i, jb) - dX(raw, PH_CP, i, ja)) / ((jb - ja) * dh);
                }
                int[] qs = {PH_MU, PH_K};
                for (int k = 0; k < 2; k++) {
                    int q = qs[k], o = dst + G_MU + 4 * k;
                    out[o] = raw[src + q];
                    out[o + 1] = dX(raw, q, i, j);
                    out[o + 2] = dH(raw, q, i, j);
                    out[o + 3] = (dX(raw, q, i, jb) - dX(raw, q, i, ja)) / ((jb - ja) * dh);
                }
            }
        }
        return out;
    }

    private double dX(double[] raw, int q, int i, int j) {
        int ia = Math.max(i - 1, 0), ib = Math.min(i + 1, np - 1);
        return (raw[(ib * nh + j) * PH_Q + q] - raw[(ia * nh + j) * PH_Q + q]) / (rx[ib] - rx[ia]);
    }

    private double dH(double[] raw, int q, int i, int j) {
        int ja = Math.max(j - 1, 0), jb = Math.min(j + 1, nh - 1);
        return (raw[(i * nh + jb) * PH_Q + q] - raw[(i * nh + ja) * PH_Q + q]) / ((jb - ja) * dh);
    }

    private static If97Tables load() {
        try (InputStream in = If97Tables.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("missing resource " + RESOURCE);
            byte[] all = in.readAllBytes();
            CRC32 crc = new CRC32();
            crc.update(all, 0, all.length - 4);
            if ((int) crc.getValue() != ByteBuffer.wrap(all, all.length - 4, 4).getInt()) {
                throw new IllegalStateException("IF97 table checksum mismatch");
            }
            return new If97Tables(ByteBuffer.wrap(all, 0, all.length - 4));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ================================================================ публичный API

    @Override
    public void stateFromMassEnergy(double density, double specificEnergy, double pGuess, ThermoState out) {
        double lr = Math.log(density);
        boolean dome = lr > r0 && lr < r0 + (nr - 1) * dr && specificEnergy < domeEnergy(lr);
        if (dome && twoPhaseFromMassEnergy(density, specificEnergy, pGuess, out)) return;
        double guess = pGuess;
        boolean converged = false;
        for (int attempt = 0; attempt < 8; attempt++) {
            converged = singlePhaseFromMassEnergy(density, specificEnergy, guess, out);
            boolean insideDome = out.pressure < P_C && out.enthalpy > out.hF && out.enthalpy < out.hG;
            if (!insideDome) break;
            // граница u_b интерполирована; окончательно фазу решает положение h относительно купола
            if (twoPhaseFromMassEnergy(density, specificEnergy, out.pressure, out)) return;
            // Иначе Ньютон ушёл в метастабильную зону купола, где таблица — лишь продолжение
            // и возможны ложные корни. Перезапуск со стороны устойчивой фазы: жидкость —
            // при большем давлении, пар — при меньшем.
            boolean liquidSide = out.enthalpy - out.hF < out.hG - out.enthalpy;
            guess = liquidSide ? out.pressure * 1.5 : out.pressure / 1.5;
        }
        if (!converged) out.flags |= ThermoState.OUT_OF_RANGE;
    }

    @Override
    public void stateFromPressureEnthalpy(double p, double h, ThermoState out) {
        int flags = 0;
        double pc = Math.min(Math.max(p, Math.exp(x0)), Math.exp(xMax));
        if (pc != p) flags |= ThermoState.OUT_OF_RANGE;
        double x = Math.log(pc);
        if (pc < P_C) {
            int i = satCell(x);
            double hf = sat(SAT_HF, i, x), hg = sat(SAT_HG, i, x);
            if (h >= hf && h <= hg) {
                fillTwoPhase(i, x, (h - hf) / (hg - hf), out);
                out.flags |= flags;
                return;
            }
        }
        fillSinglePhase(x, h, out);
        out.flags |= flags;
    }

    @Override
    public double enthalpyPT(double p, double t) {
        double x = clampX(p);
        if (Math.exp(x) < P_C) {
            int i = satCell(x);
            double ts = sat(SAT_T, i, x);
            if (t < ts) return invertH(G_T, x, t, h0, sat(SAT_HF, i, x));
            if (t > ts) return invertH(G_T, x, t, sat(SAT_HG, i, x), hMax);
            return sat(SAT_HF, i, x);
        }
        return invertH(G_T, x, t, h0, hMax);
    }

    @Override
    public double temperaturePH(double p, double h) {
        double x = clampX(p);
        if (Math.exp(x) < P_C) {
            int i = satCell(x);
            if (h >= sat(SAT_HF, i, x) && h <= sat(SAT_HG, i, x)) return sat(SAT_T, i, x);
        }
        return at(G_T, x, h, 0, 0);
    }

    @Override
    public double entropyPH(double p, double h) {
        double x = clampX(p);
        if (Math.exp(x) < P_C) {
            int i = satCell(x);
            double hf = sat(SAT_HF, i, x), hg = sat(SAT_HG, i, x);
            if (h >= hf && h <= hg) {
                double q = (h - hf) / (hg - hf);
                return sat(SAT_SF, i, x) + q * (sat(SAT_SG, i, x) - sat(SAT_SF, i, x));
            }
        }
        return at(G_S, x, h, 0, 0);
    }

    @Override
    public double enthalpyPS(double p, double s) {
        double x = clampX(p);
        if (Math.exp(x) < P_C) {
            int i = satCell(x);
            double sf = sat(SAT_SF, i, x), sg = sat(SAT_SG, i, x);
            double hf = sat(SAT_HF, i, x), hg = sat(SAT_HG, i, x);
            if (s < sf) return invertH(G_S, x, s, h0, hf);
            if (s > sg) return invertH(G_S, x, s, hg, hMax);
            return hf + (s - sf) / (sg - sf) * (hg - hf);
        }
        return invertH(G_S, x, s, h0, hMax);
    }

    @Override
    public SaturationState saturationP(double p) {
        double[] v = new double[SAT_Q];
        saturationP(p, v);
        return new SaturationState(p, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], v[9], v[10], v[11],
                v[12], v[13], v[14], v[15], v[16]);
    }

    @Override
    public void saturationP(double p, double[] out17) {
        double x = Math.min(Math.max(Math.log(p), sa), sb);
        int i = satCell(x);
        for (int q = 0; q < SAT_Q; q++) out17[q] = sat(q, i, x);
    }

    @Override
    public double saturationPressure(double t) {
        return If97.psat(Math.min(Math.max(t, If97.T_MIN), T_C));
    }

    @Override
    public double soundSpeed(double p, double h) {
        double x = clampX(p);
        if (Math.exp(x) < P_C) {
            int i = satCell(x);
            double hf = sat(SAT_HF, i, x), hg = sat(SAT_HG, i, x);
            if (h >= hf && h <= hg) { // гомогенная формула Вуда
                double vf = sat(SAT_VF, i, x), vg = sat(SAT_VG, i, x);
                double q = (h - hf) / (hg - hf);
                double v = vf + q * (vg - vf);
                double alpha = q * vg / v;
                double cf = singlePhaseSound(x, hf), cg = singlePhaseSound(x, hg);
                double compressibility = alpha * vg / (cg * cg) + (1 - alpha) * vf / (cf * cf);
                return Math.sqrt(v / compressibility);
            }
        }
        return singlePhaseSound(x, h);
    }

    // ================================================================ (ρ, u): однофазная ветка

    /**
     * 2D-Ньютон по (ln p, h): v(p, h) = 1/ρ, h − p·v(p, h) = u (01 §1.3.3, шаг 3), с
     * дроблением шага по норме невязки: у жидкости при низком давлении v почти не
     * зависит от p, и полный шаг Ньютона улетает к краю таблицы.
     */
    private boolean singlePhaseFromMassEnergy(double rho, double u, double pGuess, ThermoState out) {
        double x = clampX(pGuess);
        double h = clampH(u + Math.exp(x) / rho);
        double uScale = Math.max(Math.abs(u), 1e5);
        double norm = massEnergyResidual(x, h, rho, u, uScale);
        boolean converged = false;
        for (int it = 0; it < 60; it++) {
            int i = row(x);
            double fj = cellPos((h - h0) / dh, nh);
            int j = (int) fj;
            double a = rowFrac(x, i), b = fj - j;
            double v = bic(G_V, i, j, a, b, 0, 0);
            double vx = bic(G_V, i, j, a, b, 1, 0);
            double vh = bic(G_V, i, j, a, b, 0, 1);
            double p = Math.exp(x);
            double r1 = v * rho - 1, r2 = h - p * v - u;
            if (Math.abs(r1) < 1e-11 && Math.abs(r2) < 1e-10 * uScale) {
                converged = true;
                break;
            }
            double j11 = rho * vx, j12 = rho * vh, j21 = -p * (v + vx), j22 = 1 - p * vh;
            double det = j11 * j22 - j12 * j21;
            double sx = (-r1 * j22 + j12 * r2) / det;
            double sh = (-j11 * r2 + j21 * r1) / det;
            if (!Double.isFinite(sx) || !Double.isFinite(sh)) break;
            // ограничение длины шага — общим множителем, чтобы не менять направление Ньютона
            double lam = Math.min(1.0, Math.min(0.7 / Math.abs(sx), 3e5 / Math.abs(sh)));
            double xn = x, hn = h, nn = norm;
            for (int k = 0; k < 20; k++) {
                xn = Math.min(Math.max(x + lam * sx, x0), xMax);
                hn = Math.min(Math.max(h + lam * sh, h0), hMax);
                nn = massEnergyResidual(xn, hn, rho, u, uScale);
                if (nn < norm) break;
                lam *= 0.5;
            }
            if (xn == x && hn == h) break;
            x = xn;
            h = hn;
            norm = nn;
        }
        fillSinglePhase(x, h, out);
        out.density = rho;
        out.specificEnergy = u;
        return converged;
    }

    private double massEnergyResidual(double x, double h, double rho, double u, double uScale) {
        double v = at(G_V, x, h, 0, 0);
        double r1 = v * rho - 1, r2 = (h - Math.exp(x) * v - u) / uScale;
        return r1 * r1 + r2 * r2;
    }

    // ================================================================ (ρ, u): двухфазная ветка

    /**
     * Ньютон по ln p с защитой бисекцией (01 §1.3.3, шаг 2):
     * f(p) = (v − v')/(v'' − v') − (u − u')/(u'' − u') = 0.
     * Пишет в {@code out} только при успехе (0 ≤ x ≤ 1).
     */
    private boolean twoPhaseFromMassEnergy(double rho, double u, double pGuess, ThermoState out) {
        double v = 1.0 / rho;
        double lo = sa, hi = sb - 1e-9;
        double fLo = twoPhaseResidual(lo, v, u), fHi = twoPhaseResidual(hi, v, u);
        if (!(fLo * fHi <= 0)) return false;
        double xl = fLo < 0 ? lo : hi, xh = fLo < 0 ? hi : lo;
        double x = Math.min(Math.max(Math.log(pGuess), lo), hi);
        double step = hi - lo, stepOld = step, f = Double.NaN;
        for (int it = 0; it < 100; it++) {
            int i = satCell(x);
            double vf = sat(SAT_VF, i, x), vg = sat(SAT_VG, i, x), uf = sat(SAT_UF, i, x), ug = sat(SAT_UG, i, x);
            double dvf = satD(SAT_VF, i, x), dvg = satD(SAT_VG, i, x);
            double duf = satD(SAT_UF, i, x), dug = satD(SAT_UG, i, x);
            double av = (v - vf) / (vg - vf), au = (u - uf) / (ug - uf);
            f = av - au;
            double df = (-dvf - av * (dvg - dvf)) / (vg - vf) - (-duf - au * (dug - duf)) / (ug - uf);
            if (Math.abs(f) < 1e-12) break;
            if (f < 0) xl = x;
            else xh = x;
            double xn = x - f / df;
            stepOld = step;
            // бисекция, если Ньютон уходит из брекета или сходится медленнее неё (rtsafe)
            if (!((xn - xl) * (xn - xh) < 0) || Math.abs(2 * f) > Math.abs(stepOld * df)) xn = 0.5 * (xl + xh);
            step = xn - x;
            x = xn;
            if (Math.abs(step) < 1e-15) break;
        }
        if (!(Math.abs(f) < 1e-9)) return false;
        int i = satCell(x);
        double uf = sat(SAT_UF, i, x), ug = sat(SAT_UG, i, x);
        double q = (u - uf) / (ug - uf);
        if (q < -1e-9 || q > 1 + 1e-9) return false;
        fillTwoPhase(i, x, Math.min(Math.max(q, 0), 1), out);
        out.density = rho;
        out.specificEnergy = u;
        return true;
    }

    private double twoPhaseResidual(double x, double v, double u) {
        int i = satCell(x);
        double vf = sat(SAT_VF, i, x), vg = sat(SAT_VG, i, x), uf = sat(SAT_UF, i, x), ug = sat(SAT_UG, i, x);
        return (v - vf) / (vg - vf) - (u - uf) / (ug - uf);
    }

    // ================================================================ заполнение состояния

    private void fillTwoPhase(int i, double x, double q, ThermoState out) {
        double p = Math.exp(x);
        double vf = sat(SAT_VF, i, x), vg = sat(SAT_VG, i, x);
        double uf = sat(SAT_UF, i, x), ug = sat(SAT_UG, i, x);
        double hf = sat(SAT_HF, i, x), hg = sat(SAT_HG, i, x);
        double v = vf + q * (vg - vf);
        out.pressure = p;
        out.temperature = out.tSat = sat(SAT_T, i, x);
        out.density = 1.0 / v;
        out.specificEnergy = uf + q * (ug - uf);
        out.enthalpy = hf + q * (hg - hf);
        out.quality = q;
        out.voidFraction = q * vg / v;
        out.rhoF = 1.0 / vf;
        out.rhoG = 1.0 / vg;
        out.hF = hf;
        out.hG = hg;
        out.cp = sat(SAT_CPF, i, x);
        out.viscosity = sat(SAT_MUF, i, x);
        out.conductivity = sat(SAT_KF, i, x);
        out.surfaceTension = sat(SAT_SIGMA, i, x);
        // производные по p вдоль линии насыщения (Клапейрон–Клаузиус), 01 §1.3.3
        double dvf = satD(SAT_VF, i, x) / p, dvg = satD(SAT_VG, i, x) / p;
        double duf = satD(SAT_UF, i, x) / p, dug = satD(SAT_UG, i, x) / p;
        double dv = dvf + q * (dvg - dvf), du = duf + q * (dug - duf);
        out.dPdUAtRho = 1.0 / (du - (ug - uf) * dv / (vg - vf));
        out.dPdRhoAtU = -v * v / (dv - (vg - vf) * du / (ug - uf));
        out.flags = ThermoState.TWO_PHASE | nearCritical(p, out.temperature);
    }

    private void fillSinglePhase(double x, double h, ThermoState out) {
        int flags = 0;
        double hc = clampH(h);
        if (hc != h || x < x0 || x > xMax) flags |= ThermoState.OUT_OF_RANGE;
        int i = row(x);
        double fj = cellPos((hc - h0) / dh, nh);
        int j = (int) fj;
        double a = rowFrac(x, i), b = fj - j;
        double p = Math.exp(x);
        double v = bic(G_V, i, j, a, b, 0, 0);
        double vx = bic(G_V, i, j, a, b, 1, 0), vh = bic(G_V, i, j, a, b, 0, 1);
        double rho = 1.0 / v;
        out.pressure = p;
        out.temperature = bic(G_T, i, j, a, b, 0, 0);
        out.density = rho;
        out.enthalpy = h;
        out.specificEnergy = h - p * v;
        out.cp = bic(G_CP, i, j, a, b, 0, 0);
        out.viscosity = bic(G_MU, i, j, a, b, 0, 0);
        out.conductivity = bic(G_K, i, j, a, b, 0, 0);

        double xs = Math.min(x, sb);
        int si = satCell(xs);
        out.tSat = sat(SAT_T, si, xs);
        out.hF = sat(SAT_HF, si, xs);
        out.hG = sat(SAT_HG, si, xs);
        out.rhoF = 1.0 / sat(SAT_VF, si, xs);
        out.rhoG = 1.0 / sat(SAT_VG, si, xs);
        if (p < P_C) {
            out.quality = (h - out.hF) / (out.hG - out.hF);
            out.voidFraction = h >= out.hG ? 1 : 0;
            out.surfaceTension = sat(SAT_SIGMA, si, xs);
        } else {
            out.quality = out.voidFraction = h < out.hF ? 0 : 1;
            out.surfaceTension = 0;
        }
        // якобиан ∂(ρ, u)/∂(ln p, h) и его обращение
        double rx = -rho * rho * vx, rh = -rho * rho * vh;
        double ux = -p * (v + vx), uh = 1 - p * vh;
        double det = rx * uh - rh * ux;
        out.dPdRhoAtU = p * uh / det;
        out.dPdUAtRho = -p * rh / det;
        out.flags = flags | nearCritical(p, out.temperature);
    }

    private static int nearCritical(double p, double t) {
        return Math.abs(p - P_C) < NEAR_CRIT_DP && Math.abs(t - T_C) < NEAR_CRIT_DT ? ThermoState.NEAR_CRITICAL : 0;
    }

    private double singlePhaseSound(double x, double h) {
        int i = row(x);
        double fj = cellPos((clampH(h) - h0) / dh, nh);
        int j = (int) fj;
        double a = rowFrac(x, i), b = fj - j;
        double v = bic(G_V, i, j, a, b, 0, 0);
        double dvdpS = bic(G_V, i, j, a, b, 1, 0) / Math.exp(x) + bic(G_V, i, j, a, b, 0, 1) * v; // (∂h/∂p)_s = v
        return Math.sqrt(-v * v / dvdpS);
    }

    /** Обращение по h при постоянном p для монотонно растущих по h величин (T, s). */
    private double invertH(int grp, double x, double target, double lo, double hi) {
        double h = 0.5 * (lo + hi);
        for (int it = 0; it < 80; it++) {
            int i = row(x);
            double fj = cellPos((h - h0) / dh, nh);
            int j = (int) fj;
            double a = rowFrac(x, i), b = fj - j;
            double f = bic(grp, i, j, a, b, 0, 0) - target;
            if (f < 0) lo = h;
            else hi = h;
            double hn = h - f / bic(grp, i, j, a, b, 0, 1);
            if (!(hn > lo && hn < hi)) hn = 0.5 * (lo + hi);
            if (Math.abs(hn - h) < 1e-6) return hn;
            h = hn;
        }
        return h;
    }

    // ================================================================ интерполяция

    private double clampX(double p) {
        return Math.min(Math.max(Math.log(p), x0), xMax);
    }

    private double clampH(double h) {
        return Math.min(Math.max(h, h0), hMax);
    }

    /** Строка таблицы (p, h), в ячейке над которой лежит ln p. */
    private int row(double x) {
        double fc = Math.min(Math.max((x - x0) / dxc, 0.0), nc - 1 - 1e-12);
        int c = (int) fc;
        int n = firstRow[c + 1] - firstRow[c];
        return firstRow[c] + Math.min((int) ((fc - c) * n), n - 1);
    }

    private double rowFrac(double x, int i) {
        return Math.min(Math.max((x - rx[i]) / rw[i], 0.0), 1.0);
    }

    /** Положение в ячейках, ограниченное так, что целая часть ∈ [0, n − 2]. */
    private static double cellPos(double f, int n) {
        return Math.min(Math.max(f, 0.0), n - 1 - 1e-12);
    }

    private double at(int grp, double x, double h, int da, int db) {
        int i = row(x);
        double fj = cellPos((clampH(h) - h0) / dh, nh);
        int j = (int) fj;
        return bic(grp, i, j, rowFrac(x, i), fj - j, da, db);
    }

    /** Бикубика Эрмита по узлам (i, j)…(i+1, j+1); da, db — порядок производной по ln p и h. */
    private double bic(int grp, int i, int j, double a, double b, int da, int db) {
        double fa0, fa1, ga0, ga1, fb0, fb1, gb0, gb1;
        double dx = rw[i];
        if (da == 0) {
            fa0 = h00(a); fa1 = h01(a); ga0 = h10(a) * dx; ga1 = h11(a) * dx;
        } else {
            fa0 = d00(a) / dx; fa1 = d01(a) / dx; ga0 = d10(a); ga1 = d11(a);
        }
        if (db == 0) {
            fb0 = h00(b); fb1 = h01(b); gb0 = h10(b) * dh; gb1 = h11(b) * dh;
        } else {
            fb0 = d00(b) / dh; fb1 = d01(b) / dh; gb0 = d10(b); gb1 = d11(b);
        }
        int n00 = (i * nh + j) * STRIDE + grp, n10 = n00 + nh * STRIDE;
        return corner(n00, fa0, ga0, fb0, gb0) + corner(n10, fa1, ga1, fb0, gb0)
                + corner(n00 + STRIDE, fa0, ga0, fb1, gb1) + corner(n10 + STRIDE, fa1, ga1, fb1, gb1);
    }

    private double corner(int n, double fa, double ga, double fb, double gb) {
        return g[n] * fa * fb + g[n + 1] * ga * fb + g[n + 2] * fa * gb + g[n + 3] * ga * gb;
    }

    private int satCell(double x) {
        double xi = Math.min(Math.max((x - sa) / (sb - sa), 0.0), 1.0);
        int i = (int) ((1 - Math.sqrt(1 - xi)) * (sn - 1));
        if (i > sn - 2) i = sn - 2;
        while (i > 0 && x < sx[i]) i--;
        while (i < sn - 2 && x > sx[i + 1]) i++;
        return i;
    }

    private double sat(int q, int i, double x) {
        double w = sx[i + 1] - sx[i], t = (x - sx[i]) / w;
        int k = q * sn + i;
        return h00(t) * sv[k] + h10(t) * w * sd[k] + h01(t) * sv[k + 1] + h11(t) * w * sd[k + 1];
    }

    /** Производная сплайна насыщения по ln p. */
    private double satD(int q, int i, double x) {
        double w = sx[i + 1] - sx[i], t = (x - sx[i]) / w;
        int k = q * sn + i;
        return (d00(t) * sv[k] + d01(t) * sv[k + 1]) / w + d10(t) * sd[k] + d11(t) * sd[k + 1];
    }

    private double domeEnergy(double lr) {
        double f = Math.min(Math.max((lr - r0) / dr, 0.0), nr - 1 - 1e-12);
        int k = (int) f;
        return ub[k] + (f - k) * (ub[k + 1] - ub[k]);
    }

    private static double h00(double t) {
        return (1 + 2 * t) * (1 - t) * (1 - t);
    }

    private static double h01(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double h10(double t) {
        return t * (1 - t) * (1 - t);
    }

    private static double h11(double t) {
        return t * t * (t - 1);
    }

    private static double d00(double t) {
        return 6 * t * t - 6 * t;
    }

    private static double d01(double t) {
        return 6 * t - 6 * t * t;
    }

    private static double d10(double t) {
        return 3 * t * t - 4 * t + 1;
    }

    private static double d11(double t) {
        return 3 * t * t - 2 * t;
    }
}
