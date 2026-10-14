package dev.denfry.promptcritical.sim.thermal;

import java.util.Arrays;
import java.util.Objects;

/**
 * Общая часть 1D-теплопроводности (01 §1.2.3): узлы с теплоёмкостями и проводимостями между
 * соседями, неявная схема Эйлера со свойствами на старом шаге, прогонка. Граничное условие
 * поверхности линеаризуется по T_w (1–3 итерации Ньютона, §1.4.3); тепло, ушедшее в жидкость,
 * берётся из той же линеаризации, поэтому баланс энергии структура + узлы точный.
 * Явная связь со узлом разбивается на подшаги n = ⌈2Δt·hA/C_node⌉ ≤ {@code max_coupling_substeps}.
 */
abstract class ConductionStructure implements HeatStructure {
    private static final int MAX_ITERATIONS = 3;
    private static final double TOLERANCE = 0.01, PROBE = 0.05;

    final long id;
    final Geometry geometry;
    final ThermalConfig config;
    final int n;
    /** Температуры узлов, К; теплоёмкости, Дж/К; проводимости i–(i+1), Вт/К; источники, Вт. */
    final double[] t, cap, cond, source;
    final ConvectiveSurface[] surfaces = new ConvectiveSurface[2];
    /** Площадь поверхности стороны, м². */
    final double[] area = new double[2];

    private final double[] t0, lower, diag, upper, rhs;
    private final double[] linQ = new double[2], linDq = new double[2], linTw = new double[2];
    private final double[] stepHeat = new double[2], heatRate = new double[2];
    private double stored;

    ConductionStructure(long id, Geometry geometry, int nodes, ThermalConfig config) {
        if (nodes < 2) throw new IllegalArgumentException("nodes >= 2");
        this.id = id;
        this.geometry = geometry;
        this.config = Objects.requireNonNull(config, "config");
        n = nodes;
        t = new double[n];
        cap = new double[n];
        cond = new double[n - 1];
        source = new double[n];
        t0 = new double[n];
        lower = new double[n];
        diag = new double[n];
        upper = new double[n];
        rhs = new double[n];
    }

    /** Свойства материалов, проводимости и источники по температурам старого шага. */
    abstract void updateProperties();

    /** После подшага (плавление топлива). Энергию сохранять: менять T можно только в обмен на скрытую теплоту. */
    void afterSubstep() {}

    /** После шага (газовыделение). */
    void afterStep(double dt) {}

    /** Начальные условия: однородная температура, К. */
    public void setUniformTemperature(double temperature) {
        Arrays.fill(t, temperature);
        updateProperties();
        stored = 0;
        for (int i = 0; i < n; i++) stored += cap[i] * t[i];
    }

    final int surfaceNode(int side) {
        return side == 0 ? 0 : n - 1;
    }

    @Override
    public final void step(double dt) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt");
        updateProperties();
        int substeps = 1;
        for (int side = 0; side < 2; side++) {
            ConvectiveSurface s = surfaces[side];
            if (s == null) continue;
            s.prepare();
            double c = s.capacity();
            if (Double.isFinite(c)) {
                double tw = t[surfaceNode(side)];
                double ha = area[side] * Math.max(0, (s.flux(tw + PROBE) - s.flux(tw)) / PROBE);
                substeps = Math.max(substeps, (int) Math.ceil(2 * dt * ha / c));
            }
        }
        substeps = Math.min(substeps, config.maxCouplingSubsteps());
        double h = dt / substeps;
        stepHeat[0] = stepHeat[1] = 0;
        for (int k = 0; k < substeps; k++) {
            substep(h);
            afterSubstep();
        }
        for (int side = 0; side < 2; side++) {
            heatRate[side] = stepHeat[side] / dt;
            ConvectiveSurface s = surfaces[side];
            if (s != null) s.finish(t[surfaceNode(side)], dt, area[side]);
        }
        afterStep(dt);
    }

    private void substep(double h) {
        System.arraycopy(t, 0, t0, 0, n);
        for (int it = 0; it < MAX_ITERATIONS; it++) {
            for (int side = 0; side < 2; side++) {
                ConvectiveSurface s = surfaces[side];
                if (s == null) continue;
                double tw = t[surfaceNode(side)], q = s.flux(tw);
                linTw[side] = tw;
                linQ[side] = q;
                linDq[side] = Math.max(0, (s.flux(tw + PROBE) - q) / PROBE);
            }
            solve(h);
            boolean converged = true;
            for (int side = 0; side < 2; side++) {
                if (surfaces[side] != null && Math.abs(t[surfaceNode(side)] - linTw[side]) > TOLERANCE) converged = false;
            }
            if (converged) break;
        }
        for (int side = 0; side < 2; side++) {
            ConvectiveSurface s = surfaces[side];
            if (s == null) continue;
            double q = area[side] * (linQ[side] + linDq[side] * (t[surfaceNode(side)] - linTw[side]));
            s.deposit(q * h);
            stepHeat[side] += q * h;
        }
        for (int i = 0; i < n; i++) stored += cap[i] * (t[i] - t0[i]);
    }

    /** Неявный шаг h от t0 с текущей линеаризацией поверхностей; результат — в t (прогонка). */
    private void solve(double h) {
        for (int i = 0; i < n; i++) {
            diag[i] = cap[i] / h;
            rhs[i] = cap[i] / h * t0[i] + source[i];
            lower[i] = upper[i] = 0;
        }
        for (int i = 0; i < n - 1; i++) {
            double g = cond[i];
            diag[i] += g;
            diag[i + 1] += g;
            upper[i] = -g;
            lower[i + 1] = -g;
        }
        for (int side = 0; side < 2; side++) {
            if (surfaces[side] == null) continue;
            int i = surfaceNode(side);
            double a = area[side];
            diag[i] += a * linDq[side];
            rhs[i] += a * (linDq[side] * linTw[side] - linQ[side]);
        }
        for (int i = 1; i < n; i++) {
            double m = lower[i] / diag[i - 1];
            diag[i] -= m * upper[i - 1];
            rhs[i] -= m * rhs[i - 1];
        }
        t[n - 1] = rhs[n - 1] / diag[n - 1];
        for (int i = n - 2; i >= 0; i--) t[i] = (rhs[i] - upper[i] * t[i + 1]) / diag[i];
    }

    public ConvectiveSurface surface(int side) {
        return surfaces[side];
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public Geometry geometry() {
        return geometry;
    }

    @Override
    public double[] radialTemperatures() {
        return t.clone();
    }

    @Override
    public double centerlineTemperature() {
        return Double.NaN;
    }

    @Override
    public double surfaceTemperature(int side) {
        return t[surfaceNode(side)];
    }

    @Override
    public double heatFlux(int side) {
        return surfaces[side] == null ? 0 : surfaces[side].heatFlux();
    }

    @Override
    public double heatRate(int side) {
        return heatRate[side];
    }

    /** null — адиабатическая сторона. */
    @Override
    public HeatTransferMode mode(int side) {
        return surfaces[side] == null ? null : surfaces[side].mode();
    }

    @Override
    public double dnbr() {
        return Double.NaN;
    }

    @Override
    public double gapConductance() {
        return Double.NaN;
    }

    @Override
    public double meltFraction() {
        return 0;
    }

    @Override
    public double rodInternalPressure() {
        return Double.NaN;
    }

    @Override
    public boolean dnbOccurred() {
        return surfaces[0] != null && surfaces[0].dnbOccurred() || surfaces[1] != null && surfaces[1].dnbOccurred();
    }

    @Override
    public double storedEnergy() {
        return stored;
    }
}
