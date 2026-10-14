package dev.denfry.promptcritical.sim.grid;

/**
 * Потокораспределение острова методом Ньютона в полярных координатах (07 §7.1, инициализация).
 * Шины с машинами — PV, балансирующая — шина самой мощной машины, остальные — PQ с ZIP-нагрузкой
 * при f = f₀. Якобиан — конечные разности, плотное решение с выбором ведущего элемента: считается
 * только при инициализации, вне горячего цикла.
 */
final class PowerFlow {
    private static final int MAX_ITERATIONS = 40;
    private static final double TOLERANCE = 1e-11, EPS = 1e-7;
    private static final int PQ = 0, PV = 1, SLACK = 2;

    private final PowerGrid g;
    private final PowerGrid.Island isl;
    private final int n;
    private final double[][] yr, yi;
    private final int[] type;
    private final double[] vm, th, pGen;
    private int[] varBus;
    private boolean[] varIsAngle;
    private int slackMachine = -1;

    PowerFlow(PowerGrid g, PowerGrid.Island isl) {
        this.g = g;
        this.isl = isl;
        n = isl.buses.length;
        yr = new double[n][n];
        yi = new double[n][n];
        type = new int[n];
        vm = new double[n];
        th = new double[n];
        pGen = new double[n];
    }

    void solve() {
        buildAdmittance();
        for (int i : isl.machines) {
            if (slackMachine < 0 || g.mS[i] > g.mS[slackMachine]) slackMachine = i;
        }
        java.util.Arrays.fill(vm, 1);
        for (int i : isl.machines) {
            int k = g.busLocal[g.mBus[i]];
            if (type[k] == PQ) {
                type[k] = PV;
                vm[k] = g.vSetPu[i];
            }
            if (i != slackMachine) pGen[k] += g.pTargetW[i] / g.sb;
        }
        int sk = g.busLocal[g.mBus[slackMachine]];
        type[sk] = SLACK;
        vm[sk] = g.vSetPu[slackMachine];

        int vars = 0;
        for (int k = 0; k < n; k++) vars += type[k] == SLACK ? 0 : type[k] == PV ? 1 : 2;
        varBus = new int[vars];
        varIsAngle = new boolean[vars];
        int v = 0;
        for (int k = 0; k < n; k++) {
            if (type[k] == SLACK) continue;
            varBus[v] = k;
            varIsAngle[v++] = true;
            if (type[k] == PQ) varBus[v++] = k;
        }
        double[] f = new double[vars], f1 = new double[vars], dx = new double[vars];
        double[][] jac = new double[vars][vars];
        boolean converged = vars == 0;
        for (int it = 0; it < MAX_ITERATIONS && !converged; it++) {
            mismatch(f);
            double norm = 0;
            for (double x : f) norm = Math.max(norm, Math.abs(x));
            if (norm < TOLERANCE) {
                converged = true;
                break;
            }
            for (int c = 0; c < vars; c++) {
                double saved = get(c);
                set(c, saved + EPS);
                mismatch(f1);
                set(c, saved);
                for (int r = 0; r < vars; r++) jac[r][c] = (f1[r] - f[r]) / EPS;
            }
            for (int r = 0; r < vars; r++) dx[r] = -f[r];
            gauss(jac, dx);
            for (int c = 0; c < vars; c++) set(c, get(c) + dx[c]);
        }
        if (!converged) throw new IllegalStateException("power flow did not converge in island " + isl.id);
        writeBack();
    }

    private double get(int c) {
        return varIsAngle[c] ? th[varBus[c]] : vm[varBus[c]];
    }

    private void set(int c, double x) {
        if (varIsAngle[c]) th[varBus[c]] = x;
        else vm[varBus[c]] = x;
    }

    private void buildAdmittance() {
        for (int k : isl.branches) {
            int a = g.busLocal[g.bFrom[k]], c = g.busLocal[g.bTo[k]];
            double t = g.tap[k];
            yr[a][a] += g.yRe[k] / (t * t) + g.shFG[k];
            yi[a][a] += g.yIm[k] / (t * t) + g.shFB[k];
            yr[c][c] += g.yRe[k] + g.shTG[k];
            yi[c][c] += g.yIm[k] + g.shTB[k];
            yr[a][c] -= g.yRe[k] / t;
            yi[a][c] -= g.yIm[k] / t;
            yr[c][a] -= g.yRe[k] / t;
            yi[c][a] -= g.yIm[k] / t;
        }
        for (int b : isl.buses) yr[g.busLocal[b]][g.busLocal[b]] += g.faultG[b];
    }

    /** S_k = V_k·conj((Y V)_k). */
    private void injection(int k, double[] out) {
        double vr = vm[k] * Math.cos(th[k]), vi = vm[k] * Math.sin(th[k]);
        double ir = 0, ii = 0;
        for (int j = 0; j < n; j++) {
            if (yr[k][j] == 0 && yi[k][j] == 0) continue;
            double ur = vm[j] * Math.cos(th[j]), ui = vm[j] * Math.sin(th[j]);
            ir += yr[k][j] * ur - yi[k][j] * ui;
            ii += yr[k][j] * ui + yi[k][j] * ur;
        }
        out[0] = vr * ir + vi * ii;
        out[1] = vi * ir - vr * ii;
    }

    private void mismatch(double[] f) {
        double[] s = new double[2];
        for (int c = 0; c < f.length; c++) {
            int k = varBus[c], b = isl.buses[k];
            injection(k, s);
            f[c] = varIsAngle[c] ? pGen[k] - g.busLoadP(b, vm[k]) - s[0] : -g.busLoadQ(b, vm[k]) - s[1];
        }
    }

    private void writeBack() {
        double[] s = new double[2];
        for (int k = 0; k < n; k++) {
            int b = isl.buses[k];
            g.vRe[b] = vm[k] * Math.cos(th[k]);
            g.vIm[b] = vm[k] * Math.sin(th[k]);
        }
        for (int k = 0; k < n; k++) {
            if (type[k] == PQ) continue;
            int b = isl.buses[k];
            injection(k, s);
            double pTot = s[0] + g.busLoadP(b, vm[k]), qTot = s[1] + g.busLoadQ(b, vm[k]);
            double sSum = 0, pOthers = 0;
            for (int i : isl.machines) {
                if (g.mBus[i] != b) continue;
                sSum += g.mS[i];
                if (i != slackMachine) pOthers += g.pTargetW[i] / g.sb;
            }
            for (int i : isl.machines) {
                if (g.mBus[i] != b) continue;
                g.pe[i] = i == slackMachine ? pTot - pOthers : g.pTargetW[i] / g.sb;
                g.qe[i] = qTot * g.mS[i] / sSum;
            }
        }
    }

    /** Решает A·x = b на месте (x → b) с частичным выбором ведущего элемента. */
    private static void gauss(double[][] a, double[] b) {
        int m = b.length;
        double[][] w = new double[m][];
        for (int r = 0; r < m; r++) w[r] = a[r].clone();
        for (int c = 0; c < m; c++) {
            int p = c;
            for (int r = c + 1; r < m; r++) if (Math.abs(w[r][c]) > Math.abs(w[p][c])) p = r;
            if (w[p][c] == 0) throw new IllegalStateException("singular power-flow Jacobian");
            double[] tr = w[c];
            w[c] = w[p];
            w[p] = tr;
            double tb = b[c];
            b[c] = b[p];
            b[p] = tb;
            for (int r = c + 1; r < m; r++) {
                double l = w[r][c] / w[c][c];
                if (l == 0) continue;
                for (int k = c; k < m; k++) w[r][k] -= l * w[c][k];
                b[r] -= l * b[c];
            }
        }
        for (int r = m - 1; r >= 0; r--) {
            double s = b[r];
            for (int k = r + 1; k < m; k++) s -= w[r][k] * b[k];
            b[r] = s / w[r][r];
        }
    }
}
