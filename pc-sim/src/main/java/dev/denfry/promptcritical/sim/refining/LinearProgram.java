package dev.denfry.promptcritical.sim.refining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Точный решатель задачи линейного программирования для компаундирования (03d §3d.10.3 {@code pcopt.lp}): двухфазный
 * симплекс-метод на плотной таблице с правилом Бленда (Bland, 1977) — детерминированно и без зацикливания, до
 * {@value #MAX_VARIABLES} переменных и {@value #MAX_CONSTRAINTS} ограничений. Задача:
 *
 * <pre>
 * максимизировать cᵀx  при  aᵢᵀx (≤ | ≥ | =) bᵢ,  x ≥ 0
 * </pre>
 *
 * Возвращает решение и двойственные цены y_i = ∂(cᵀx*)/∂b_i — «сколько стоит единица ограничения» (цена октана
 * 03d §3d.10.3). Верхние границы переменных — обычные строки ≤. Без внешних библиотек.
 */
public final class LinearProgram {
    public static final int MAX_VARIABLES = 60, MAX_CONSTRAINTS = 40;
    private static final double EPS = 1e-9;

    public enum Sense { LE, GE, EQ }

    public enum Status { OPTIMAL, INFEASIBLE, UNBOUNDED }

    /** Итог: статус, x, значение цели, двойственные цены по строкам в порядке добавления. */
    public record Result(Status status, double[] x, double objective, double[] duals) {
        public boolean optimal() {
            return status == Status.OPTIMAL;
        }
    }

    private record Row(double[] a, Sense sense, double b) {
    }

    private final int n;
    private final double[] c;
    private final List<Row> rows = new ArrayList<>();

    /** Задача на максимум с целевыми коэффициентами {@code objective}. */
    public LinearProgram(double[] objective) {
        if (objective.length == 0 || objective.length > MAX_VARIABLES) throw new IllegalArgumentException("variables");
        n = objective.length;
        c = objective.clone();
    }

    public LinearProgram constraint(double[] a, Sense sense, double b) {
        if (a.length != n) throw new IllegalArgumentException("row width " + a.length + " != " + n);
        if (rows.size() == MAX_CONSTRAINTS) throw new IllegalArgumentException("more than " + MAX_CONSTRAINTS + " constraints");
        rows.add(new Row(a.clone(), sense, b));
        return this;
    }

    /** x_j ≤ ub. */
    public LinearProgram upperBound(int j, double ub) {
        double[] a = new double[n];
        a[j] = 1;
        return constraint(a, Sense.LE, ub);
    }

    public Result solve() {
        int m = rows.size();
        // столбцы: n исходных, по строке — единичный (слэк для ≤, искусственный для ≥ и =), для ≥ — ещё излишек
        int surplus = 0;
        boolean[] flip = new boolean[m];
        Sense[] sense = new Sense[m];
        for (int i = 0; i < m; i++) {
            Row r = rows.get(i);
            flip[i] = r.b < 0;
            sense[i] = !flip[i] || r.sense == Sense.EQ ? r.sense : r.sense == Sense.LE ? Sense.GE : Sense.LE;
            if (sense[i] == Sense.GE) surplus++;
        }
        int unit0 = n, sur0 = n + m, cols = n + m + surplus, rhs = cols;
        double[][] t = new double[m + 1][cols + 1];
        boolean[] artificial = new boolean[cols];
        int[] basis = new int[m];
        int s = sur0;
        for (int i = 0; i < m; i++) {
            Row r = rows.get(i);
            double k = flip[i] ? -1 : 1;
            for (int j = 0; j < n; j++) t[i][j] = k * r.a[j];
            t[i][rhs] = k * r.b;
            t[i][unit0 + i] = 1;
            basis[i] = unit0 + i;
            if (sense[i] != Sense.LE) artificial[unit0 + i] = true;
            if (sense[i] == Sense.GE) t[i][s++] = -1;
        }
        // фаза 1: максимум −Σ искусственных
        double[] z = t[m];
        for (int i = 0; i < m; i++) {
            if (!artificial[basis[i]]) continue;
            for (int j = 0; j <= cols; j++) if (j == rhs || !artificial[j]) z[j] -= t[i][j];
        }
        if (!iterate(t, basis, null, cols)) throw new IllegalStateException("phase 1 cannot be unbounded");
        if (-z[rhs] > 1e-7) return new Result(Status.INFEASIBLE, new double[n], Double.NaN, new double[m]);
        // вывести искусственные с нулевым значением из базиса; строка без других столбцов — избыточна
        for (int i = 0; i < m; i++) {
            if (!artificial[basis[i]]) continue;
            for (int j = 0; j < cols; j++) {
                if (!artificial[j] && Math.abs(t[i][j]) > EPS) {
                    pivot(t, basis, i, j);
                    break;
                }
            }
        }
        // фаза 2: строка цели d_j = c_B·B⁻¹A_j − c_j
        Arrays.fill(z, 0);
        for (int j = 0; j < n; j++) z[j] = -c[j];
        for (int i = 0; i < m; i++) {
            int b = basis[i];
            double cb = b < n ? c[b] : 0;
            if (cb != 0) for (int j = 0; j <= cols; j++) z[j] += cb * t[i][j];
        }
        if (!iterate(t, basis, artificial, cols)) return new Result(Status.UNBOUNDED, new double[n], Double.POSITIVE_INFINITY, new double[m]);
        double[] x = new double[n];
        for (int i = 0; i < m; i++) if (basis[i] < n) x[basis[i]] = t[i][rhs];
        double obj = 0;
        for (int j = 0; j < n; j++) obj += c[j] * x[j];
        double[] duals = new double[m];
        for (int i = 0; i < m; i++) duals[i] = (flip[i] ? -1 : 1) * z[unit0 + i];
        return new Result(Status.OPTIMAL, x, obj, duals);
    }

    /** Итерации до оптимума по правилу Бленда; {@code barred} — столбцы, которым нельзя входить. false — неограничена. */
    private static boolean iterate(double[][] t, int[] basis, boolean[] barred, int cols) {
        int m = basis.length;
        double[] z = t[m];
        for (int guard = 0; guard < 10_000; guard++) {
            int enter = -1;
            for (int j = 0; j < cols; j++) {
                if (barred != null && barred[j]) continue;
                if (z[j] < -EPS) {
                    enter = j;
                    break;
                }
            }
            if (enter < 0) return true;
            int leave = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < m; i++) {
                double a = t[i][enter];
                if (a <= EPS) continue;
                double ratio = t[i][cols] / a;
                if (ratio < best - EPS || (Math.abs(ratio - best) <= EPS && leave >= 0 && basis[i] < basis[leave])) {
                    best = ratio;
                    leave = i;
                }
            }
            if (leave < 0) return false;
            pivot(t, basis, leave, enter);
        }
        throw new IllegalStateException("simplex did not converge");
    }

    private static void pivot(double[][] t, int[] basis, int r, int c) {
        double[] pr = t[r];
        double p = pr[c];
        for (int j = 0; j < pr.length; j++) pr[j] /= p;
        for (int i = 0; i < t.length; i++) {
            if (i == r) continue;
            double f = t[i][c];
            if (f == 0) continue;
            double[] row = t[i];
            for (int j = 0; j < row.length; j++) row[j] -= f * pr[j];
        }
        basis[r] = c;
    }
}
