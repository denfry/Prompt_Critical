package dev.denfry.promptcritical.sim.neutronics;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Нодальная модель распределения потока (01 §1.6.5–1.6.6): одна групповая функция с
 * миграционной площадью, −∇·D∇φ + Σ_aφ = νΣ_fφ/k, D = M²Σ_a, сетка из узлов-блоков h = 1 м.
 *
 * <p>Связь соседей через грань: J = D̂(φ_i − φ_j), D̂ = (ℓ_i/D_i + ℓ_j/D_j)⁻¹. Длина связи
 * ℓ = h/2 для пары однородных узлов и стягивается к M на контрастной границе (топливо–стержень,
 * топливо–отражатель, топливо–вакуум): ℓ = h/2 − (h/2 − M)χ, χ = min(1, |κ_i² − κ_j²|/(|κ_i²| +
 * |κ_j²|)). Без поправки при h ≫ M эффективность стержня в соседнем узле занижена в ~h/(2M) раз.
 * Граница с вакуумом — условие Маршака: D̂_b = D/(ℓ_b + 2D).
 *
 * <p>Решение — степенной метод со сдвигом Виландта (k_s = k + 0,05, включается, когда k
 * устоялась) и внутренними итерациями SOR (ω = 1,5, 5 проходов); k — отношение Рэлея
 * ⟨φ, νΣ_fφ⟩/⟨φ, Aφ⟩ (оператор симметричен). Тёплый старт: каждый вызов
 * {@link #iterate} продолжает с прошлого потока.
 *
 * <p>Соседи — массив 6N в порядке −x, +x, −y, +y, −z, +z; −1 — вакуум.
 */
public final class NodalFluxSolver {
    public static final double NODE_SIZE = 100; // см
    private static final double HALF = NODE_SIZE / 2, SHIFT = 0.05, OMEGA = 1.5, EPS = 1e-12;
    private static final int SOR_SWEEPS = 5, MAX_CONVERGE = 200;
    private static final double TOL_K = 1e-5, TOL_PHI = 1e-3;

    private boolean contrastCorrection = true;
    private int n;
    private int[] neighbor = new int[0];
    private int[] x = new int[0], y = new int[0], z = new int[0];
    private double[] diff, sigA, nuSigF, sigF, mig;
    private double[] coupling, leak, phi, rhs;
    private double k = 1;
    private boolean shiftArmed, constantsSet;
    private int lastIterations;

    /** Только для тестов §1.16 (T-1.6-05): отключить стягивание длины связи на контрастах. */
    public void setContrastCorrection(boolean on) {
        contrastCorrection = on;
    }

    /** Структурное событие: новая сетка узлов. Поток сбрасывается в плоский, k = 1. */
    public void rebuild(CellComposition[] cells, int[] neighborIndex) {
        if (neighborIndex.length != 6 * cells.length) throw new IllegalArgumentException("neighborIndex length");
        n = cells.length;
        neighbor = neighborIndex.clone();
        for (int v : neighbor) if (v < -1 || v >= n) throw new IllegalArgumentException("neighbor " + v);
        diff = new double[n];
        sigA = new double[n];
        nuSigF = new double[n];
        sigF = new double[n];
        mig = new double[n];
        coupling = new double[6 * n];
        leak = new double[n];
        phi = new double[n];
        rhs = new double[n];
        for (int i = 0; i < n; i++) phi[i] = cells[i].hasFuel() ? 1 : 0.1;
        k = 1;
        shiftArmed = false;
        constantsSet = false;
        coordinates();
    }

    /** SLOW-шаг: новые константы узлов из {@link LatticeModel}. */
    public void updateConstants(SixFactor[] perNode) {
        if (perNode.length != n) throw new IllegalArgumentException("perNode length");
        for (int i = 0; i < n; i++) {
            SixFactor s = perNode[i];
            sigA[i] = s.sigmaA();
            diff[i] = s.diffusionCoefficient();
            nuSigF[i] = s.nuSigmaF();
            sigF[i] = s.sigmaF();
            mig[i] = Math.sqrt(s.migrationArea());
        }
        if (!constantsSet) {
            double kMax = 0;
            for (int i = 0; i < n; i++) if (sigA[i] > 0) kMax = Math.max(kMax, nuSigF[i] / sigA[i]);
            k = kMax > 0 ? kMax : 1;
        }
        constantsSet = true;
    }

    /** До сходимости, но не более 200 внешних итераций (структурное событие). */
    public boolean converge() {
        return iterate(MAX_CONVERGE);
    }

    /** Внешние итерации с тёплым стартом; true, если сошлось (|Δk| &lt; 10⁻⁵, max|Δφ|/φ &lt; 10⁻³). */
    public boolean iterate(int maxOuter) {
        if (!constantsSet) throw new IllegalStateException("updateConstants() first");
        for (int it = 1; it <= maxOuter; it++) {
            couple();
            double mu = 1 / k, muS = shiftArmed ? 1 / (k + SHIFT) : 0;
            for (int i = 0; i < n; i++) rhs[i] = (mu - muS) * nuSigF[i] * phi[i];
            double maxPhi = 0, maxDelta = 0;
            double[] old = phi.clone();
            for (int sweep = 0; sweep < SOR_SWEEPS; sweep++) {
                for (int i = 0; i < n; i++) {
                    double diag = leak[i] + sigA[i] - muS * nuSigF[i], s = rhs[i];
                    for (int f = 0; f < 6; f++) {
                        int j = neighbor[6 * i + f];
                        if (j >= 0) s += coupling[6 * i + f] * phi[j];
                    }
                    phi[i] = Math.max(0, (1 - OMEGA) * phi[i] + OMEGA * s / Math.max(diag, EPS));
                }
            }
            // Отношение Рэлея: оператор симметричен (D̂_ij = D̂_ji), поэтому ошибка k квадратична
            // по ошибке формы — тёплый старт после малого возмущения сходится за 2–4 итерации.
            double fission = 0, loss = 0;
            for (int i = 0; i < n; i++) {
                double a = (leak[i] + sigA[i]) * phi[i];
                for (int f = 0; f < 6; f++) {
                    int j = neighbor[6 * i + f];
                    if (j >= 0) a -= coupling[6 * i + f] * phi[j];
                }
                loss += phi[i] * a;
                fission += nuSigF[i] * phi[i] * phi[i];
            }
            if (!(fission > 0) || !(loss > 0)) throw new IllegalStateException("flux vanished: no multiplying nodes");
            double kNew = fission / loss;
            // Нормировка Σ Σ_f φ = 1 и оценка сходимости формы.
            double norm = 0;
            for (int i = 0; i < n; i++) norm += sigF[i] * phi[i];
            for (int i = 0; i < n; i++) {
                phi[i] /= norm;
                maxPhi = Math.max(maxPhi, phi[i]);
            }
            double oldNorm = 0;
            for (int i = 0; i < n; i++) oldNorm += sigF[i] * old[i];
            for (int i = 0; i < n; i++) maxDelta = Math.max(maxDelta, Math.abs(phi[i] - old[i] / oldNorm));
            double dk = Math.abs(kNew - k);
            k = kNew;
            lastIterations = it;
            if (!shiftArmed && it >= 2 && dk < 0.01) shiftArmed = true;
            if (dk < TOL_K && maxDelta / maxPhi < TOL_PHI) return true;
        }
        return false;
    }

    /** Коэффициенты связи по текущему k (контраст κ² зависит от k). */
    private void couple() {
        for (int i = 0; i < n; i++) {
            double li0 = HALF;
            double sum = 0;
            for (int f = 0; f < 6; f++) {
                int j = neighbor[6 * i + f];
                double c;
                if (j < 0) {
                    double lb = contrastCorrection ? Math.min(HALF, mig[i]) : HALF;
                    c = diff[i] / (lb + 2 * diff[i]);
                } else {
                    double chi = contrast(i, j);
                    double li = linkLength(i, chi, li0), lj = linkLength(j, chi, li0);
                    c = 1 / (li / Math.max(diff[i], EPS) + lj / Math.max(diff[j], EPS));
                }
                c /= NODE_SIZE; // A/V = 1/h
                coupling[6 * i + f] = j < 0 ? 0 : c;
                sum += c;
            }
            leak[i] = sum;
        }
    }

    private double linkLength(int i, double chi, double half) {
        if (!contrastCorrection || mig[i] >= half) return half;
        return half - (half - mig[i]) * chi;
    }

    private double contrast(int i, int j) {
        double ki = kappa2(i), kj = kappa2(j);
        return Math.min(1, Math.abs(ki - kj) / (Math.abs(ki) + Math.abs(kj) + EPS));
    }

    private double kappa2(int i) {
        return (sigA[i] - nuSigF[i] / k) / Math.max(diff[i], EPS);
    }

    public double keff() {
        return k;
    }

    public int lastIterations() {
        return lastIterations;
    }

    public int nodeCount() {
        return n;
    }

    /** Форма потока, нормированная Σ Σ_f φ V = 1 (V = 1 м³ в единицах узла). */
    public double[] flux() {
        return phi.clone();
    }

    /** Доля мощности узла P_i/P_core = Σ_f,i φ_i / Σ Σ_f φ. */
    public double[] nodePowerFraction() {
        double[] p = new double[n];
        double s = 0;
        for (int i = 0; i < n; i++) s += p[i] = sigF[i] * phi[i];
        for (int i = 0; i < n; i++) p[i] /= s;
        return p;
    }

    /** Статистические веса теории возмущений w_i = φ_i²V / Σ φ²V (§1.6.7). */
    public double[] importanceWeights() {
        double[] w = new double[n];
        double s = 0;
        for (int i = 0; i < n; i++) s += w[i] = phi[i] * phi[i];
        for (int i = 0; i < n; i++) w[i] /= s;
        return w;
    }

    /** K_v = P_i / P̄ по топливным узлам — максимум (§1.6.6). */
    public double maxVolumetricPeaking() {
        double[] p = nodePowerFraction();
        int fuel = 0;
        double max = 0;
        for (int i = 0; i < n; i++) {
            if (nuSigF[i] > 0) fuel++;
            max = Math.max(max, p[i]);
        }
        return max * fuel;
    }

    /** Аксиальный офсет AO = (P_верх − P_низ)/(P_верх + P_низ) относительно середины топливной высоты. */
    public double axialOffset() {
        double[] p = nodePowerFraction();
        double mid = fuelCenter(z);
        double top = 0, bottom = 0;
        for (int i = 0; i < n; i++) {
            if (z[i] > mid) top += p[i];
            else if (z[i] < mid) bottom += p[i];
            else {
                top += p[i] / 2;
                bottom += p[i] / 2;
            }
        }
        return top + bottom > 0 ? (top - bottom) / (top + bottom) : 0;
    }

    /** Перекос: мощность квадранта (0: −x−y, 1: +x−y, 2: −x+y, 3: +x+y) к средней по квадрантам. */
    public double radialTilt(int quadrant) {
        if (quadrant < 0 || quadrant > 3) throw new IllegalArgumentException("quadrant");
        double[] p = nodePowerFraction();
        double cx = fuelCenter(x), cy = fuelCenter(y);
        double[] q = new double[4];
        for (int i = 0; i < n; i++) {
            double wx1 = x[i] > cx ? 1 : x[i] < cx ? 0 : 0.5, wy1 = y[i] > cy ? 1 : y[i] < cy ? 0 : 0.5;
            q[0] += p[i] * (1 - wx1) * (1 - wy1);
            q[1] += p[i] * wx1 * (1 - wy1);
            q[2] += p[i] * (1 - wx1) * wy1;
            q[3] += p[i] * wx1 * wy1;
        }
        double mean = (q[0] + q[1] + q[2] + q[3]) / 4;
        return mean > 0 ? q[quadrant] / mean : 1;
    }

    private double fuelCenter(int[] c) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (nuSigF[i] <= 0) continue;
            lo = Math.min(lo, c[i]);
            hi = Math.max(hi, c[i]);
        }
        return lo > hi ? 0 : 0.5 * (lo + hi);
    }

    /** Целочисленные координаты обходом по соседям (у каждой компоненты — своё начало). */
    private void coordinates() {
        x = new int[n];
        y = new int[n];
        z = new int[n];
        boolean[] seen = new boolean[n];
        int[][] step = {{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int start = 0; start < n; start++) {
            if (seen[start]) continue;
            seen[start] = true;
            queue.add(start);
            while (!queue.isEmpty()) {
                int i = queue.poll();
                for (int f = 0; f < 6; f++) {
                    int j = neighbor[6 * i + f];
                    if (j < 0 || seen[j]) continue;
                    seen[j] = true;
                    x[j] = x[i] + step[f][0];
                    y[j] = y[i] + step[f][1];
                    z[j] = z[i] + step[f][2];
                    queue.add(j);
                }
            }
        }
    }

    @Override
    public String toString() {
        return "NodalFluxSolver[n=" + n + ", k=" + k + ", flux=" + Arrays.toString(phi) + "]";
    }
}
