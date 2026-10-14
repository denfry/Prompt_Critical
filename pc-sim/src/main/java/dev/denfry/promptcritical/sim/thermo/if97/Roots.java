package dev.denfry.promptcritical.sim.thermo.if97;

import java.util.function.DoubleFunction;

/** Ньютон с защитой бисекцией (rtsafe) для одномерных обращений IF97. */
final class Roots {
    private Roots() {}

    /**
     * Корень f(x) = 0 на отрезке [lo, hi] со сменой знака.
     *
     * @param f функция, возвращающая {f(x), f'(x)}
     */
    static double solve(DoubleFunction<double[]> f, double lo, double hi, double x0, double tol) {
        double flo = f.apply(lo)[0];
        if (flo == 0) return lo;
        double fhi = f.apply(hi)[0];
        if (fhi == 0) return hi;
        if (flo * fhi > 0) {
            throw new IllegalArgumentException("no sign change on [" + lo + ", " + hi + "]: " + flo + ", " + fhi);
        }
        // xl — сторона с f < 0, xh — с f > 0
        double xl = flo < 0 ? lo : hi;
        double xh = flo < 0 ? hi : lo;
        double x = x0 > lo && x0 < hi ? x0 : 0.5 * (lo + hi);
        double dxOld = Math.abs(hi - lo), dx = dxOld;
        for (int it = 0; it < 300; it++) {
            double[] fd = f.apply(x);
            double fx = fd[0], dfx = fd[1];
            if (fx == 0) return x;
            if (fx < 0) xl = x;
            else xh = x;
            double newton = x - fx / dfx;
            boolean outside = (newton - xl) * (newton - xh) >= 0;
            if (!Double.isFinite(newton) || outside || Math.abs(2 * fx) > Math.abs(dxOld * dfx)) {
                dxOld = dx;
                dx = 0.5 * (xh - xl);
                x = xl + dx;
            } else {
                dxOld = dx;
                dx = newton - x;
                x = newton;
            }
            if (Math.abs(dx) < tol) return x;
        }
        throw new IllegalStateException("root did not converge on [" + lo + ", " + hi + "]");
    }
}
