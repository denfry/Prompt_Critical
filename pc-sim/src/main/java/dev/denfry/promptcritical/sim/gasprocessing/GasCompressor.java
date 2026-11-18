package dev.denfry.promptcritical.sim.gasprocessing;

/**
 * Поршневой компрессор попутного газа с промежуточным охлаждением: ступени с равными степенями сжатия, после каждой
 * газ охлаждается до температуры всасывания. Работа ступени на моль — адиабатная
 * w = Z·RT₁·k/(k − 1)·(r^((k−1)/k) − 1)/η_ад (GPSA Engineering Data Book, разд. 13; Campbell, т. 2, гл. 15).
 * Показатель адиабаты жирного газа k = 1,25, адиабатный КПД 0,80, степень сжатия ступени не выше 4.
 */
public final class GasCompressor {
    public static final double K = 1.25, EFFICIENCY = 0.80, MAX_STAGE_RATIO = 4;

    private GasCompressor() {
    }

    public static int stages(double p1, double p2) {
        return Math.max(1, (int) Math.ceil(Math.log(p2 / p1) / Math.log(MAX_STAGE_RATIO) - 1e-9));
    }

    /** Работа сжатия на нормальный кубометр газа от p1 до p2 (абсолютных) при всасывании t1, Дж/нм³. */
    public static double workJPerSm3(double[] x, double t1, double p1, double p2) {
        if (p2 <= p1) return 0;
        int n = stages(p1, p2);
        double r = Math.pow(p2 / p1, 1.0 / n), w = 0, p = p1;
        for (int i = 0; i < n; i++) {
            double z = PengRobinson.zVapor(x, t1, p);
            w += z * PengRobinson.R * t1 * K / (K - 1) * (Math.pow(r, (K - 1) / K) - 1) / EFFICIENCY;
            p *= r;
        }
        return w * WaterContent.MOL_PER_SM3;
    }

    /** Температура нагнетания ступени без охлаждения, К. */
    public static double dischargeK(double t1, double ratio) {
        return t1 * (1 + (Math.pow(ratio, (K - 1) / K) - 1) / EFFICIENCY);
    }
}
