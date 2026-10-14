package dev.denfry.promptcritical.sim.grid;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Комплексный разреженный LU без выбора ведущего элемента для матрицы узловых проводимостей острова
 * (07 §7.1.3). Порядок исключения — жадная минимальная степень, как у гидросети (01 §1.2.6);
 * строится и факторизуется вне горячего цикла, на подшаге остаются прямая и обратная подстановки.
 * Диагональ Y доминирует (проводимости машин, шунты), поэтому выбор ведущего элемента не нужен.
 */
final class ComplexSparseLU {
    final int n;
    private final int[] perm, inv; // perm[новый] = старый
    private final int[] rowPtr, col, diag;
    private final double[] re, im;
    private final int[] mark;
    private final double[] wRe, wIm;

    /** {@code adjacency[i]} — соседи узла i (без самого i). */
    ComplexSparseLU(int n, int[][] adjacency) {
        this.n = n;
        BitSet[] g = new BitSet[n];
        for (int i = 0; i < n; i++) g[i] = new BitSet(n);
        for (int i = 0; i < n; i++) {
            for (int j : adjacency[i]) if (j != i) {
                g[i].set(j);
                g[j].set(i);
            }
        }
        BitSet[] filled = new BitSet[n];
        for (int i = 0; i < n; i++) filled[i] = (BitSet) g[i].clone();
        perm = new int[n];
        inv = new int[n];
        boolean[] done = new boolean[n];
        for (int k = 0; k < n; k++) {
            int best = -1, bestDeg = Integer.MAX_VALUE;
            for (int i = 0; i < n; i++) {
                if (!done[i] && g[i].cardinality() < bestDeg) {
                    best = i;
                    bestDeg = g[i].cardinality();
                }
            }
            perm[k] = best;
            inv[best] = k;
            done[best] = true;
            int[] nb = g[best].stream().toArray();
            for (int a : nb) {
                g[a].clear(best);
                for (int b : nb) if (a != b) {
                    g[a].set(b);
                    filled[a].set(b);
                }
            }
            g[best].clear();
        }
        rowPtr = new int[n + 1];
        int[][] rows = new int[n][];
        for (int r = 0; r < n; r++) {
            int[] c = filled[perm[r]].stream().map(o -> inv[o]).toArray();
            int[] withDiag = Arrays.copyOf(c, c.length + 1);
            withDiag[c.length] = r;
            Arrays.sort(withDiag);
            rows[r] = withDiag;
            rowPtr[r + 1] = rowPtr[r] + withDiag.length;
        }
        col = new int[rowPtr[n]];
        diag = new int[n];
        for (int r = 0; r < n; r++) {
            System.arraycopy(rows[r], 0, col, rowPtr[r], rows[r].length);
            diag[r] = rowPtr[r] + Arrays.binarySearch(rows[r], r);
        }
        re = new double[col.length];
        im = new double[col.length];
        mark = new int[n];
        Arrays.fill(mark, -1);
        wRe = new double[n];
        wIm = new double[n];
    }

    /** Позиция элемента (i, j) в исходной нумерации; −1, если его нет в шаблоне. */
    int position(int i, int j) {
        int r = inv[i], c = inv[j];
        int k = Arrays.binarySearch(col, rowPtr[r], rowPtr[r + 1], c);
        return k >= 0 ? k : -1;
    }

    void clear() {
        Arrays.fill(re, 0);
        Arrays.fill(im, 0);
    }

    void add(int pos, double vRe, double vIm) {
        re[pos] += vRe;
        im[pos] += vIm;
    }

    /** Факторизация на месте (Дулиттл по строкам); false — нулевой или нечисловой ведущий элемент. */
    boolean factor() {
        for (int i = 0; i < n; i++) {
            int a = rowPtr[i], e = rowPtr[i + 1];
            for (int p = a; p < e; p++) mark[col[p]] = p;
            for (int p = a; p < diag[i]; p++) {
                int k = col[p], d = diag[k];
                double den = re[d] * re[d] + im[d] * im[d];
                double lRe = (re[p] * re[d] + im[p] * im[d]) / den;
                double lIm = (im[p] * re[d] - re[p] * im[d]) / den;
                re[p] = lRe;
                im[p] = lIm;
                for (int q = d + 1; q < rowPtr[k + 1]; q++) {
                    int m = mark[col[q]];
                    re[m] -= lRe * re[q] - lIm * im[q];
                    im[m] -= lRe * im[q] + lIm * re[q];
                }
            }
            for (int p = a; p < e; p++) mark[col[p]] = -1;
            double dRe = re[diag[i]], dIm = im[diag[i]];
            if ((dRe == 0 && dIm == 0) || !Double.isFinite(dRe) || !Double.isFinite(dIm)) return false;
        }
        return true;
    }

    /** Решает LU·x = b; b и x — в исходной нумерации, могут совпадать. */
    void solve(double[] bRe, double[] bIm, double[] xRe, double[] xIm) {
        for (int r = 0; r < n; r++) {
            double sRe = bRe[perm[r]], sIm = bIm[perm[r]];
            for (int p = rowPtr[r]; p < diag[r]; p++) {
                int c = col[p];
                sRe -= re[p] * wRe[c] - im[p] * wIm[c];
                sIm -= re[p] * wIm[c] + im[p] * wRe[c];
            }
            wRe[r] = sRe;
            wIm[r] = sIm;
        }
        for (int r = n - 1; r >= 0; r--) {
            double sRe = wRe[r], sIm = wIm[r];
            for (int p = diag[r] + 1; p < rowPtr[r + 1]; p++) {
                int c = col[p];
                sRe -= re[p] * wRe[c] - im[p] * wIm[c];
                sIm -= re[p] * wIm[c] + im[p] * wRe[c];
            }
            int d = diag[r];
            double den = re[d] * re[d] + im[d] * im[d];
            wRe[r] = (sRe * re[d] + sIm * im[d]) / den;
            wIm[r] = (sIm * re[d] - sRe * im[d]) / den;
        }
        for (int r = 0; r < n; r++) {
            xRe[perm[r]] = wRe[r];
            xIm[perm[r]] = wIm[r];
        }
    }
}
