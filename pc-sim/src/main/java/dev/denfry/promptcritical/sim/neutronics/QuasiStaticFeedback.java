package dev.denfry.promptcritical.sim.neutronics;

/**
 * Квазистатическая связь нодальной модели с точечной кинетикой (01 §1.6.7): между SLOW-шагами
 * ρ(t) = ρ_nodal(t_s) + Σ_i w_i Δρ_i(t), где Δρ_i — изменение локальной реактивности узла по
 * обратным связям через производные k∞ (§1.6.4) или по изменению Σ_a стержнями.
 *
 * <p>Веса — первый порядок теории возмущений одногрупповой задачи (сопряжённая функция совпадает
 * с прямой): Δρ = Δ(1 − 1/k) = Σ φ_i²(Δ(νΣ_f)_i/k − ΔΣ_a,i) / Σ νΣ_f,j φ_j². Для обратных связей
 * это w_i = νΣ_f,i φ_i² / (k Σ νΣ_f φ²) и Δρ_i = Δk∞/k∞; для стержня — Δρ = −Σ φ_i² ΔΣ_a,i / Σ νΣ_f φ².
 * В критическом реакторе (k = 1) обе формы совпадают с GDD.
 * В однородной зоне веса совпадают с w_i = φ²V/Σφ²V из GDD, но в отличие от них не «разбавляются»
 * узлами отражателя и позволяют считать стержни в каналах без топлива (νΣ_f = 0).
 *
 * <p>Горячие методы без аллокаций.
 */
public final class QuasiStaticFeedback {
    private int n;
    private double[] weight = new double[0], rodWeight = new double[0];
    private double[] dTf, dRho, dTm, dB;
    private double[] tf0, rho0, tm0, b0;

    /** SLOW-шаг t_s: опорное состояние узлов, их константы и поток нодального решения. */
    public void capture(NodalFluxSolver solver, SixFactor[] nodes, CellFeedbackState[] states) {
        double[] phi = solver.flux();
        if (nodes.length != phi.length || states.length != phi.length) throw new IllegalArgumentException("node count");
        if (n != phi.length) allocate(phi.length);
        double den = 0;
        for (int i = 0; i < n; i++) den += nodes[i].nuSigmaF() * phi[i] * phi[i];
        if (!(den > 0)) throw new IllegalArgumentException("no fission source");
        double invK = 1 / solver.keff();
        for (int i = 0; i < n; i++) {
            SixFactor s = nodes[i];
            double phi2 = phi[i] * phi[i];
            weight[i] = s.nuSigmaF() * phi2 / den * invK;
            rodWeight[i] = phi2 / den;
            boolean fuel = s.kInf() > 0;
            dTf[i] = fuel ? s.reactivityCoefficient(s.dkdTf()) : 0;
            dRho[i] = fuel ? s.reactivityCoefficient(s.dkdRhoC()) : 0;
            dTm[i] = fuel ? s.reactivityCoefficient(s.dkdTm()) : 0;
            dB[i] = fuel ? s.reactivityCoefficient(s.dkdBoron()) : 0;
            CellFeedbackState st = states[i];
            tf0[i] = st.fuelEffTemp();
            rho0[i] = st.coolantDensity();
            tm0[i] = st.moderatorTemp();
            b0[i] = st.boronPpm();
        }
    }

    private void allocate(int count) {
        n = count;
        weight = new double[n];
        rodWeight = new double[n];
        dTf = new double[n];
        dRho = new double[n];
        dTm = new double[n];
        dB = new double[n];
        tf0 = new double[n];
        rho0 = new double[n];
        tm0 = new double[n];
        b0 = new double[n];
    }

    /** DOPPLER: Σ w_i α_D,i (T_eff,i − T_eff,i(t_s)). */
    public double doppler(double[] fuelEffTemp) {
        return sum(dTf, fuelEffTemp, tf0);
    }

    /** COOLANT_DENSITY: по ρ_c, кг/м³. */
    public double coolant(double[] coolantDensity) {
        return sum(dRho, coolantDensity, rho0);
    }

    /** MODERATOR_TEMP: по T_m, К. */
    public double moderator(double[] moderatorTemp) {
        return sum(dTm, moderatorTemp, tm0);
    }

    /** BORON: по C_B, ppm. */
    public double boron(double[] boronPpm) {
        return sum(dB, boronPpm, b0);
    }

    /** RODS: Δρ = −Σ φ_i² ΔΣ_a,i / Σ νΣ_f φ²; ΔΣ_a — изменение поглощения узла с t_s, см⁻¹. */
    public double rods(double[] deltaSigmaA) {
        double s = 0;
        for (int i = 0; i < n; i++) s -= rodWeight[i] * deltaSigmaA[i];
        return s;
    }

    /** Вес узла w_i для HMI (вклад узла в обратные связи). */
    public double weight(int node) {
        return weight[node];
    }

    private double sum(double[] coef, double[] now, double[] ref) {
        double s = 0;
        for (int i = 0; i < n; i++) s += weight[i] * coef[i] * (now[i] - ref[i]);
        return s;
    }
}
