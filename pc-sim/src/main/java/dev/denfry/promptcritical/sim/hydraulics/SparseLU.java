package dev.denfry.promptcritical.sim.hydraulics;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Разреженный LU без выбора ведущего элемента с порядком исключения, найденным при
 * пересборке (01 §1.2.6, шаг 4). Порядок — жадная минимальная степень (вариант AMD без
 * аппроксимации: сети малы, считается вне горячего цикла). Шаблон заполнения симметричен,
 * значения хранятся в CSR по переставленным строкам; на тике — сборка, факторизация
 * и решение без аллокаций.
 */
final class SparseLU {
    final int n;
    private final int[] perm, inv; // perm[новый] = старый
    private final int[] rowPtr, col, diag;
    private final double[] val;
    private final int[] mark;
    private final double[] work;

    /** {@code adjacency[i]} — соседи узла i (без самого i). */
    SparseLU(int n, int[][] adjacency) {
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
        val = new double[col.length];
        mark = new int[n];
        Arrays.fill(mark, -1);
        work = new double[n];
    }

    /** Позиция элемента (i, j) в исходной нумерации; −1, если его нет в шаблоне. */
    int position(int i, int j) {
        int r = inv[i], c = inv[j];
        int k = Arrays.binarySearch(col, rowPtr[r], rowPtr[r + 1], c);
        return k >= 0 ? k : -1;
    }

    void clear() {
        Arrays.fill(val, 0);
    }

    void add(int pos, double v) {
        val[pos] += v;
    }

    /** Факторизация на месте (Дулиттл по строкам); false — нулевой или нечисловой ведущий элемент. */
    boolean factor() {
        for (int i = 0; i < n; i++) {
            int a = rowPtr[i], e = rowPtr[i + 1];
            for (int p = a; p < e; p++) mark[col[p]] = p;
            for (int p = a; p < diag[i]; p++) {
                int k = col[p];
                double l = val[p] / val[diag[k]];
                val[p] = l;
                for (int q = diag[k] + 1; q < rowPtr[k + 1]; q++) val[mark[col[q]]] -= l * val[q];
            }
            for (int p = a; p < e; p++) mark[col[p]] = -1;
            double d = val[diag[i]];
            if (d == 0 || !Double.isFinite(d)) return false;
        }
        return true;
    }

    /** Решает LU·x = b; b и x — в исходной нумерации, могут совпадать. */
    void solve(double[] b, double[] x) {
        for (int r = 0; r < n; r++) {
            double s = b[perm[r]];
            for (int p = rowPtr[r]; p < diag[r]; p++) s -= val[p] * work[col[p]];
            work[r] = s;
        }
        for (int r = n - 1; r >= 0; r--) {
            double s = work[r];
            for (int p = diag[r] + 1; p < rowPtr[r + 1]; p++) s -= val[p] * work[col[p]];
            work[r] = s / val[diag[r]];
        }
        for (int r = 0; r < n; r++) x[perm[r]] = work[r];
    }
}
