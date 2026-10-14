package dev.denfry.promptcritical.sim.neutronics;

import java.util.Arrays;

/**
 * Группы предшественников запаздывающих нейтронов (01 §1.5.2): эффективные доли β_i и
 * постоянные распада λ_i, с⁻¹. Массивы копируются — запись неизменяема.
 */
public record DelayedGroups(double[] beta, double[] lambda) {
    public DelayedGroups {
        if (beta.length != lambda.length || beta.length == 0) throw new IllegalArgumentException("group count");
        beta = beta.clone();
        lambda = lambda.clone();
        for (int i = 0; i < beta.length; i++) {
            if (!(beta[i] >= 0)) throw new IllegalArgumentException("beta[" + i + "]");
            if (!(lambda[i] > 0)) throw new IllegalArgumentException("lambda[" + i + "]");
        }
    }

    @Override
    public double[] beta() {
        return beta.clone();
    }

    @Override
    public double[] lambda() {
        return lambda.clone();
    }

    public int count() {
        return beta.length;
    }

    public double beta(int i) {
        return beta[i];
    }

    public double lambda(int i) {
        return lambda[i];
    }

    public double betaTotal() {
        double s = 0;
        for (double b : beta) s += b;
        return s;
    }

    /** Эквивалентная одногрупповая постоянная λ_eff = β / Σ(β_i/λ_i), с⁻¹ (для U-235 — 0,0767). */
    public double lambdaEffective() {
        double t = 0;
        for (int i = 0; i < beta.length; i++) t += beta[i] / lambda[i];
        return betaTotal() / t;
    }

    /** Те же группы с суммарной долей, приведённой к {@code betaTotal}. */
    public DelayedGroups scaledTo(double betaTotal) {
        double k = betaTotal / betaTotal();
        double[] b = beta.clone();
        for (int i = 0; i < b.length; i++) b[i] *= k;
        return new DelayedGroups(b, lambda);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DelayedGroups g && Arrays.equals(beta, g.beta) && Arrays.equals(lambda, g.lambda);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(beta) + Arrays.hashCode(lambda);
    }

    @Override
    public String toString() {
        return "DelayedGroups[beta=" + Arrays.toString(beta) + ", lambda=" + Arrays.toString(lambda) + "]";
    }
}
