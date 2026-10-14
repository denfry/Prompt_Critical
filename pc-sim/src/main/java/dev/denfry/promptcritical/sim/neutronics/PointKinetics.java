package dev.denfry.promptcritical.sim.neutronics;

/**
 * Точечная кинетика (01 §1.5.1, §1.5.4): амплитуда n = P_fis/P_nom и предшественники c_i в тех
 * же нормированных единицах.
 *
 * <p>Схема {@link Scheme#EXP_PRECURSOR_IMPLICIT}: на подшаге h n(t) линейна между n₀ и n₁, ρ
 * постоянна — уравнения предшественников интегрируются точно. Уравнение для n
 * dn/dt = αn + q, α = (ρ−β)/Λ, интегрируется экспоненциально-подогнанно с q на конце подшага:
 * n₁ = n₀e^{αh} + q₁(e^{αh}−1)/α; после подстановки c_{i,1} остаётся одно скалярное уравнение.
 * При αh → 0 это неявный Эйлер §1.5.4, при αh → −∞ — точный мгновенный скачок. Отличие от GDD:
 * у неявного Эйлера остаток мгновенной моды гасится в 1/(1−αh) раз, а не в e^{αh}, и за тик
 * 0,05 с скачок +0,1β недобирает 0,7 % (1,105 вместо 1,113, T-1.5-01 требует 0,5 %).
 * Схема L-устойчива: мгновенная мода (≈ −325 с⁻¹) затухает за один шаг без «звона».
 *
 * <p>Число подшагов выбирается по оценке доминирующей обратной постоянной ω̂ так, чтобы n росла
 * не более чем на ~5 % за подшаг; в докритике и при ρ &lt; 0,5β — один подшаг.
 *
 * <p>Горячий цикл без аллокаций; экспоненты кэшируются для текущего h.
 */
public final class PointKinetics {
    public enum Scheme { EXP_PRECURSOR_IMPLICIT }

    /** Энергия деления, Дж (≈ 200 МэВ, полное извлекаемое). */
    public static final double FISSION_ENERGY = 3.2e-11;
    /** Нижняя граница n — ниже любого физического источника (§1.5.4). */
    public static final double MIN_POWER = 1e-14;
    /** Допустимый рост n за подшаг. */
    private static final double GROWTH_PER_SUBSTEP = 0.05;

    private final int maxSubsteps;
    private int groups;
    private final double[] beta, lambda, c, e, a, b;
    private double betaTotal, lambdaEff, generationTime;
    private double n;
    private double cachedH = Double.NaN;
    private int lastSubsteps;
    private double lastReactivity, lastSource;

    public PointKinetics(DelayedGroups groups, double generationTime, double initialPower) {
        this(groups, generationTime, initialPower, NeutronicsConfig.defaults());
    }

    public PointKinetics(DelayedGroups groups, double generationTime, double initialPower, NeutronicsConfig config) {
        maxSubsteps = config.kineticsMaxSubsteps();
        int g = config.maxDelayedGroups();
        beta = new double[g];
        lambda = new double[g];
        c = new double[g];
        e = new double[g];
        a = new double[g];
        b = new double[g];
        setParameters(groups, generationTime);
        resetToEquilibrium(initialPower);
    }

    /** Нормированный внешний источник s = S E_f / (ν Λ P_nom), с⁻¹; S — нейтр./с. */
    public static double normalizedSource(double neutronsPerSecond, double nu, double generationTime,
            double nominalPower) {
        return neutronsPerSecond * FISSION_ENERGY / (nu * generationTime * nominalPower);
    }

    /** Число делений в секунду при амплитуде n. */
    public static double fissionRate(double n, double nominalPower) {
        return n * nominalPower / FISSION_ENERGY;
    }

    /**
     * SLOW-шаг или событие решётки: новые группы и Λ. Концентрации предшественников сохраняются;
     * если число групп растёт, новые группы стартуют из равновесия с текущей n.
     */
    public void setParameters(DelayedGroups groups, double generationTime) {
        if (groups.count() > beta.length) throw new IllegalArgumentException("too many delayed groups");
        if (!(generationTime > 0)) throw new IllegalArgumentException("generationTime");
        int old = this.groups;
        this.groups = groups.count();
        this.generationTime = generationTime;
        for (int i = 0; i < this.groups; i++) {
            beta[i] = groups.beta(i);
            lambda[i] = groups.lambda(i);
            if (i >= old) c[i] = beta[i] * n / (generationTime * lambda[i]);
        }
        for (int i = this.groups; i < beta.length; i++) c[i] = beta[i] = lambda[i] = 0;
        betaTotal = groups.betaTotal();
        lambdaEff = groups.lambdaEffective();
        cachedH = Double.NaN;
    }

    /** Стационар без источника: c_i = β_i n / (Λ λ_i). */
    public void resetToEquilibrium(double n) {
        this.n = Math.max(n, MIN_POWER);
        for (int i = 0; i < groups; i++) c[i] = beta[i] * this.n / (generationTime * lambda[i]);
        lastReactivity = 0;
        lastSource = 0;
    }

    /** Один FAST-тик; подшаги выбираются автоматически. Возвращает n в конце тика. */
    public double step(double dt, double reactivity, double sourceNormalized) {
        return step(dt, reactivity, sourceNormalized, 0);
    }

    /**
     * Тик с адиабатической обратной связью внутри подшагов (01 §1.13.4, RIA): реактивность на
     * подшаге ρ + κ·∫n dt от начала тика, κ = dρ/d(∫n dt) ≤ 0 [1/с] — например α_D P_nom / C_f
     * для доплера. Без неё вспышка Фукса–Хансена при ширине ~50 мс была бы завышена.
     */
    public double step(double dt, double reactivity, double sourceNormalized, double adiabaticFeedback) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt");
        int substeps = substeps(dt, reactivity);
        double h = dt / substeps;
        if (h != cachedH) cacheExponents(h);
        double invGen = 1 / generationTime;
        double integral = 0, rho = reactivity;
        for (int s = 0; s < substeps; s++) {
            double n0 = n;
            if (adiabaticFeedback != 0) rho = reactivity + adiabaticFeedback * (integral + h * n0);
            double src = sourceNormalized, coupling = 0;
            for (int i = 0; i < groups; i++) {
                double bi = beta[i] * invGen;
                src += lambda[i] * (c[i] * e[i] + bi * n0 * a[i]);
                coupling += lambda[i] * bi * b[i];
            }
            double alpha = (rho - betaTotal) * invGen, x = alpha * h;
            double decay = Math.exp(x), gain = Math.abs(x) < 1e-9 ? h : Math.expm1(x) / alpha;
            double den = Math.max(1 - gain * coupling, 1e-3);
            double n1 = Math.max((n0 * decay + gain * src) / den, MIN_POWER);
            for (int i = 0; i < groups; i++) c[i] = c[i] * e[i] + beta[i] * invGen * (n0 * a[i] + n1 * b[i]);
            n = n1;
            integral += 0.5 * h * (n0 + n1);
        }
        lastSubsteps = substeps;
        lastReactivity = rho;
        lastSource = sourceNormalized;
        return n;
    }

    /** N = clamp(⌈Δt ω̂ / 0,05⌉, 1, max); ω̂ = max(0,(ρ−β)/Λ) + λ_eff max(0,ρ)/max(β−ρ, 0,05β). */
    private int substeps(double dt, double rho) {
        if (rho < 0.5 * betaTotal) return 1;
        double omega = Math.max(0, (rho - betaTotal) / generationTime)
                + lambdaEff * Math.max(0, rho) / Math.max(betaTotal - rho, 0.05 * betaTotal);
        double want = Math.ceil(dt * omega / GROWTH_PER_SUBSTEP);
        return (int) Math.max(1, Math.min(maxSubsteps, want));
    }

    /** E = e^{−λh}, B = h(x − 1 + e^{−x})/x², A = (1−E)/λ − B, x = λh (ряд при малом x). */
    private void cacheExponents(double h) {
        for (int i = 0; i < groups; i++) {
            double x = lambda[i] * h;
            e[i] = Math.exp(-x);
            if (x < 1e-4) {
                b[i] = h * (0.5 - x / 6 + x * x / 24);
                a[i] = h * (0.5 - x / 3 + x * x / 8);
            } else {
                double oneMinusE = -Math.expm1(-x);
                b[i] = h * (x - oneMinusE) / (x * x);
                a[i] = oneMinusE / lambda[i] - b[i];
            }
        }
        cachedH = h;
    }

    public double power() {
        return n;
    }

    public double precursor(int i) {
        return c[i];
    }

    public int groupCount() {
        return groups;
    }

    public double betaEff() {
        return betaTotal;
    }

    public double generationTime() {
        return generationTime;
    }

    public int lastSubsteps() {
        return lastSubsteps;
    }

    /** Мгновенный период n/(dn/dt) по правой части уравнений; ∞ в стационаре. */
    public double periodInstant() {
        double dndt = (lastReactivity - betaTotal) / generationTime * n + lastSource;
        for (int i = 0; i < groups; i++) dndt += lambda[i] * c[i];
        if (Math.abs(dndt) < 1e-300) return Double.POSITIVE_INFINITY;
        double t = n / dndt;
        return Math.abs(t) > 1e12 ? Double.POSITIVE_INFINITY : t;
    }
}
