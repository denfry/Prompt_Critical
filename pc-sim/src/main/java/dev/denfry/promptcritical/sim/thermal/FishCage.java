package dev.denfry.promptcritical.sim.thermal;

/**
 * Садок в водоёме со сбросным теплом (17 §17.3.6), по садковым хозяйствам на тёплых водах электростанций. Прирост
 * пропорционален кардинальной функции температуры воды f(T) с T_min = 8, T_opt = 26, T_max = 34 °C и идёт, только
 * пока есть корм; выше 36 °C рыба гибнет вся. Удельная скорость роста при T_opt — 2 % массы в сутки, кормовой
 * коэффициент 1,5 кг корма на кг прироста [оценка, порядок для карпа и форели в садках]; садок держит до 500 кг рыбы.
 */
public final class FishCage {
    public static final double T_MIN_C = 8, T_OPT_C = 26, T_MAX_C = 34, KILL_C = 36;
    public static final double GROWTH_PER_DAY = 0.02, FEED_CONVERSION = 1.5, CAPACITY_KG = 500, STOCK_KG = 50;
    private static final double DAY_S = 86_400;

    private double biomassKg;
    private boolean killed;

    public FishCage(double biomassKg) {
        this.biomassKg = Math.max(0, biomassKg);
    }

    /** Кардинальная функция для рыбы, 0…1. */
    public static double cardinal(double tC) {
        if (tC <= T_MIN_C || tC >= T_MAX_C) return 0;
        double a = (tC - T_MIN_C) / (T_OPT_C - T_MIN_C);
        double b = (T_MAX_C - tC) / (T_MAX_C - T_OPT_C);
        return a * Math.pow(b, (T_MAX_C - T_OPT_C) / (T_OPT_C - T_MIN_C));
    }

    /**
     * Шаг: рост за {@code dtS} при воде {@code waterC} и запасе корма {@code feedKg}.
     *
     * @return съеденный корм, кг
     */
    public double step(double dtS, double waterC, double feedKg) {
        if (biomassKg <= 0) return 0;
        if (waterC > KILL_C) {
            biomassKg = 0;
            killed = true;
            return 0;
        }
        double room = Math.max(0, CAPACITY_KG - biomassKg);
        double gain = Math.min(room, GROWTH_PER_DAY * cardinal(waterC) * biomassKg * dtS / DAY_S);
        double eaten = Math.min(Math.max(0, feedKg), gain * FEED_CONVERSION);
        biomassKg += eaten / FEED_CONVERSION;
        return eaten;
    }

    /** Выловить всё сверх зарыбления: остаётся {@link #STOCK_KG}. */
    public double harvest() {
        double out = Math.max(0, biomassKg - STOCK_KG);
        biomassKg -= out;
        return out;
    }

    public double biomassKg() {
        return biomassKg;
    }

    /** Рыба погибла от перегрева; сбрасывается при новом зарыблении. */
    public boolean killed() {
        return killed;
    }

    /** Восстановить состояние из сохранения. */
    public void restore(double kg, boolean wasKilled) {
        biomassKg = Math.max(0, kg);
        killed = wasKilled;
    }

    public void restock(double kg) {
        biomassKg = Math.max(0, kg);
        killed = false;
    }
}
