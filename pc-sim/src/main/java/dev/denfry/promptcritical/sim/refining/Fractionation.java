package dev.denfry.promptcritical.sim.refining;

/**
 * Короткий метод расчёта ректификации Фенске–Андервуда–Гиллиленда (Seader, Henley, Roper, «Separation Process
 * Principles», 3-е изд., гл. 9; Kister, «Distillation Design», гл. 3; Александров И.А., «Ректификационные и
 * абсорбционные аппараты»):
 *
 * <ul>
 * <li>Фенске: минимальное число теоретических тарелок при полном орошении N_min = ln[(x_D/x_B)_лк·(x_B/x_D)_тк]/ln α;</li>
 * <li>Андервуд для бинарной смеси при подаче кипящей жидкостью: R_min = [x_D/x_F − α(1 − x_D)/(1 − x_F)]/(α − 1);</li>
 * <li>Гиллиленд в аппроксимации Молоканова: Y = 1 − exp[(1 + 54,4X)/(11 + 117,2X)·(X − 1)/√X], X = (R − R_min)/(R + 1),
 * Y = (N − N_min)/(N + 1).</li>
 * </ul>
 *
 * Для нефтяных фракций секцию между отборами рассматривают как разделение псевдобинарной пары ключевых компонентов
 * по обе стороны от температуры раздела (Watkins, «Petroleum Refinery Distillation», гл. 2). Относительная летучесть
 * из правила Трутона (ΔS_исп ≈ 87 Дж/(моль·К)): ln α = (ΔS/R)(T_тк − T_лк)/T, T — температура секции. По заданным
 * тарелкам и флегме находится чёткость разделения, при которой Фенске и Гиллиленд согласованы; дальше компоненты
 * распределяются по уравнению Фенске (метод Хенгстебека–Геддеса): d_i/b_i = α_i^{N_эф}.
 */
public final class Fractionation {
    /** ΔS_исп/R по Трутону. */
    public static final double TROUTON = 87.0 / 8.314_462;
    /** Ключевые компоненты — на этом расстоянии по температуре кипения от точки раздела, К. */
    public static final double KEY_HALF_SPAN_K = 15;

    private Fractionation() {
    }

    public static double fenskeMinStages(double alpha, double xLkD, double xLkB, double xHkD, double xHkB) {
        return Math.log((xLkD / xLkB) * (xHkB / xHkD)) / Math.log(alpha);
    }

    public static double underwoodMinRefluxBinary(double alpha, double xF, double xD) {
        return (xD / xF - alpha * (1 - xD) / (1 - xF)) / (alpha - 1);
    }

    /** Молоканов: Y по X. */
    public static double gilliland(double x) {
        if (x <= 0) return 1;
        if (x >= 1) return 0;
        return 1 - Math.exp((1 + 54.4 * x) / (11 + 117.2 * x) * (x - 1) / Math.sqrt(x));
    }

    /** Относительная летучесть компонента, кипящего при tb, к компоненту при tRef, в секции при tSection. */
    public static double relativeVolatility(double tbK, double tRefK, double tSectionK) {
        return Math.exp(TROUTON * (tRefK - tbK) / tSectionK);
    }

    /**
     * Эффективное число тарелок полного орошения для секции с {@code stages} теоретическими тарелками и флегмовым
     * числом {@code reflux} при точке раздела {@code cutK}: решение согласования Фенске и Гиллиленда для симметричной
     * псевдобинарной пары ключей (x_F = 0,5, чистота p в обоих продуктах).
     */
    public static double effectiveStages(double stages, double reflux, double cutK) {
        return effectiveStagesForAlpha(stages, reflux, relativeVolatility(cutK - KEY_HALF_SPAN_K, cutK + KEY_HALF_SPAN_K, cutK));
    }

    /**
     * То же для пары ключевых компонентов с известной относительной летучестью {@code alpha} (например, по
     * константам равновесия газофракционирующей колонны).
     */
    public static double effectiveStagesForAlpha(double stages, double reflux, double alpha) {
        if (!(alpha > 1)) throw new IllegalArgumentException("relative volatility must exceed 1: " + alpha);
        double lo = 0.5 + 1e-9, hi = 1 - 1e-12;
        for (int i = 0; i < 100; i++) {
            double p = 0.5 * (lo + hi);
            double nFenske = Math.log((p / (1 - p)) * (p / (1 - p))) / Math.log(alpha);
            double rMin = underwoodMinRefluxBinary(alpha, 0.5, p);
            double nGilliland;
            if (reflux <= rMin) {
                nGilliland = 0;
            } else {
                double y = gilliland((reflux - rMin) / (reflux + 1));
                nGilliland = stages - y * (stages + 1);
            }
            if (nFenske > nGilliland) hi = p;
            else lo = p;
        }
        double p = 0.5 * (lo + hi);
        return Math.log((p / (1 - p)) * (p / (1 - p))) / Math.log(alpha);
    }

    /** Доля компонента с температурой кипения tb, уходящая в лёгкий продукт секции. */
    public static double toLight(double tbK, double cutK, double effectiveStages) {
        double a = relativeVolatility(tbK, cutK, cutK);
        return 1 / (1 + Math.pow(a, -effectiveStages));
    }
}
