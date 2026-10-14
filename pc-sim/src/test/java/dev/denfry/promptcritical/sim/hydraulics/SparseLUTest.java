package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/** Разреженный LU с порядком минимальной степени против плотного Гаусса (01 §1.2.6). */
class SparseLUTest {
    @Test
    void matchesDenseGaussOnRandomNetworks() {
        SplittableRandom rnd = new SplittableRandom(42);
        for (int trial = 0; trial < 200; trial++) {
            int n = 1 + rnd.nextInt(40);
            // дерево + несколько контуров, как в игровых сетях
            List<int[]> edges = new ArrayList<>();
            for (int i = 1; i < n; i++) edges.add(new int[] {i, rnd.nextInt(i)});
            for (int k = 0; k < n / 4; k++) {
                int x = rnd.nextInt(n), y = rnd.nextInt(n);
                if (x != y) edges.add(new int[] {x, y});
            }
            List<List<Integer>> adj = new ArrayList<>();
            for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
            for (int[] e : edges) {
                adj.get(e[0]).add(e[1]);
                adj.get(e[1]).add(e[0]);
            }
            int[][] a = new int[n][];
            for (int i = 0; i < n; i++) a[i] = adj.get(i).stream().mapToInt(Integer::intValue).toArray();
            SparseLU lu = new SparseLU(n, a);

            // несимметричная, диагонально доминирующая по строкам, как матрица давления
            double[][] dense = new double[n][n];
            for (int i = 0; i < n; i++) dense[i][i] = 1;
            for (int[] e : edges) {
                double g1 = rnd.nextDouble(0, 1e6), g2 = rnd.nextDouble(0, 1e6);
                dense[e[0]][e[0]] += g1;
                dense[e[0]][e[1]] -= g1;
                dense[e[1]][e[1]] += g2;
                dense[e[1]][e[0]] -= g2;
            }
            lu.clear();
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (dense[i][j] == 0) continue;
                    int pos = lu.position(i, j);
                    assertThat(pos).isNotNegative();
                    lu.add(pos, dense[i][j]);
                }
            }
            double[] rhs = new double[n];
            for (int i = 0; i < n; i++) rhs[i] = rnd.nextDouble(-1e7, 1e7);
            double[] expected = gauss(dense, rhs.clone());
            assertThat(lu.factor()).isTrue();
            double[] x = new double[n];
            lu.solve(rhs, x);
            for (int i = 0; i < n; i++) {
                assertThat(x[i]).isCloseTo(expected[i], org.assertj.core.data.Offset.offset(1e-9 * (1 + Math.abs(expected[i]))));
            }
        }
    }

    private static double[] gauss(double[][] m0, double[] b) {
        int n = b.length;
        double[][] m = new double[n][];
        for (int i = 0; i < n; i++) m[i] = m0[i].clone();
        for (int k = 0; k < n; k++) {
            int piv = k;
            for (int i = k + 1; i < n; i++) if (Math.abs(m[i][k]) > Math.abs(m[piv][k])) piv = i;
            double[] tr = m[k];
            m[k] = m[piv];
            m[piv] = tr;
            double tb = b[k];
            b[k] = b[piv];
            b[piv] = tb;
            for (int i = k + 1; i < n; i++) {
                double l = m[i][k] / m[k][k];
                for (int j = k; j < n; j++) m[i][j] -= l * m[k][j];
                b[i] -= l * b[k];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = b[i];
            for (int j = i + 1; j < n; j++) s -= m[i][j] * x[j];
            x[i] = s / m[i][i];
        }
        return x;
    }
}
