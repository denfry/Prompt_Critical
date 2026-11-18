package dev.denfry.promptcritical.sim.reactor;

/**
 * Вода первого контура при 10 МПа (IAPWS-IF97, таблицы Ривкина–Александрова): плотность и энтальпия жидкости до
 * насыщения 584,1 К (311 °C), теплота парообразования и плотность пара на линии насыщения. Интерполяция — линейная по
 * таблице; для контура с недогревом и малыми паросодержаниями этого достаточно.
 */
public final class Water10MPa {
    public static final double T_SAT = 584.15, H_FG = 1317e3, RHO_VAPOR = 55.5;
    private static final double[] T = {293.15, 373.15, 423.15, 473.15, 523.15, 553.15, 573.15, 584.15};
    private static final double[] RHO = {1002.6, 962.7, 922.4, 870.9, 806.7, 760.0, 722.6, 688.4};
    private static final double[] H = {93.2e3, 426.5e3, 638.1e3, 855.0e3, 1085.7e3, 1236.4e3, 1343.0e3, 1407.9e3};

    private Water10MPa() {
    }

    private static double interp(double[] y, double t) {
        if (t <= T[0]) return y[0];
        for (int i = 1; i < T.length; i++)
            if (t <= T[i]) return y[i - 1] + (y[i] - y[i - 1]) * (t - T[i - 1]) / (T[i] - T[i - 1]);
        return y[y.length - 1];
    }

    /** Плотность жидкости, кг/м³ (выше насыщения — насыщенная жидкость). */
    public static double density(double tK) {
        return interp(RHO, tK);
    }

    /** Удельная энтальпия жидкости, Дж/кг. */
    public static double enthalpy(double tK) {
        return interp(H, tK);
    }

    /** Теплоёмкость жидкости, Дж/(кг·К) (разность энтальпий). */
    public static double cp(double tK) {
        double t = Math.min(T_SAT - 1, Math.max(T[0] + 1, tK));
        return (enthalpy(t + 1) - enthalpy(t - 1)) / 2;
    }

    /** Массовое паросодержание по энтальпии (0 — недогрев). */
    public static double quality(double h) {
        return Math.max(0, (h - H[H.length - 1]) / H_FG);
    }

    /** Плотность гомогенной пароводяной смеси при паросодержании {@code x}, кг/м³. */
    public static double mixtureDensity(double x) {
        if (x <= 0) return RHO[RHO.length - 1];
        double xx = Math.min(1, x);
        return 1 / (xx / RHO_VAPOR + (1 - xx) / RHO[RHO.length - 1]);
    }
}
