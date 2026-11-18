package dev.denfry.promptcritical.sim.neutronics;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Остаточное тепловыделение продуктов деления: 16 экспоненциальных групп, dE_i/dt = a_i·P − λ_i·E_i,
 * P_ост = Σ λ_i·E_i. Группы подогнаны (неотрицательные МНК, λ через полдекады от 0,316 до 10⁻⁸ с⁻¹) к формуле
 * Untermyer–Weills для бесконечной работы: P/P₀ = 0,1·[(t + 10)^−0,2 − 0,87·(t + 2·10⁷)^−0,2] (El-Wakil,
 * «Nuclear Heat Transport», 1971); отклонение от неё не больше 0,03 % от 0 до 1,6·10⁷ с. Сама формула — приближение:
 * ±20 % к ANSI/ANS-5.1 после первых 10 с [приближение]. Сразу после останова — 6,0 % мощности, через час — 1,6 %,
 * через сутки — 0,73 %.
 */
public final class DecayHeat {
    static final double[] LAMBDA = {0.3162, 0.1, 0.03162, 0.01, 0.003162, 0.001, 3.162e-4, 1e-4, 3.162e-5, 1e-5,
            3.162e-6, 1e-6, 3.162e-7, 1e-7, 3.162e-8, 1e-8};
    static final double[] SHARE = {8.69223e-4, 5.79846e-3, 9.17294e-3, 9.02779e-3, 7.68690e-3, 6.23413e-3, 4.99032e-3,
            3.96791e-3, 3.15943e-3, 2.50343e-3, 1.99738e-3, 1.57412e-3, 1.26834e-3, 8.85493e-4, 2.04320e-4, 7.42806e-4};

    /** Энергия, запасённая в группах, Дж. */
    private final double[] energy = new double[LAMBDA.length];

    /** Шаг при постоянной мощности деления {@code fissionW}. */
    public void step(double dtS, double fissionW) {
        double p = Math.max(0, fissionW);
        for (int i = 0; i < energy.length; i++) {
            double e = Math.exp(-LAMBDA[i] * dtS);
            energy[i] = energy[i] * e + SHARE[i] * p * (1 - e) / LAMBDA[i];
        }
    }

    /** Равновесие после бесконечной работы на мощности деления {@code fissionW} (горячий старт, тесты). */
    public void equilibrium(double fissionW) {
        double p = Math.max(0, fissionW);
        for (int i = 0; i < energy.length; i++) energy[i] = SHARE[i] * p / LAMBDA[i];
    }

    /** Мощность остаточного тепловыделения, Вт. */
    public double powerW() {
        double s = 0;
        for (int i = 0; i < energy.length; i++) s += LAMBDA[i] * energy[i];
        return s;
    }

    /** Доля мощности после бесконечной работы через {@code seconds} после останова (сама подгонка). */
    public static double afterInfiniteOperation(double seconds) {
        double s = 0;
        for (int i = 0; i < LAMBDA.length; i++) s += SHARE[i] * Math.exp(-LAMBDA[i] * seconds);
        return s;
    }

    /** Формула Untermyer–Weills: работа {@code operatingS} на постоянной мощности, затем {@code seconds} после. */
    public static double untermyerWeills(double seconds, double operatingS) {
        double t = seconds, T = operatingS;
        return 0.1 * (Math.pow(t + 10, -0.2) - Math.pow(t + T + 10, -0.2) + 0.87 * Math.pow(t + T + 2e7, -0.2)
                - 0.87 * Math.pow(t + 2e7, -0.2));
    }

    public void save(StateWriter out) {
        out.putDoubles("decay_heat", energy, energy.length);
    }

    public void load(StateReader in) {
        if (!in.has("decay_heat")) return;
        double[] e = in.getDoubles("decay_heat");
        System.arraycopy(e, 0, energy, 0, Math.min(e.length, energy.length));
    }
}
