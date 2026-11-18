package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Плоское шлифование периферией круга на станке типа 3Г71 (круг ПП 250×20×76 24А, шпиндель ≈ 2680 об/мин, стол на
 * гидроприводе). Модель — по Malkin S., Guo C. «Grinding Technology: Theory and Applications of Machining with
 * Abrasives», 2-е изд., 2008, гл. 5–6.
 *
 * <p>Удельная энергия u = u_ch + u_sl, Дж/мм³: u_ch ≈ 13,8 — образование стружки (≈ энтальпия плавления стали);
 * u_sl = μ·p̄·A_a·(v_s/v_w)·√(d_s/a) — трение площадок износа зёрен о металл, A_a — доля площадок износа на рабочей
 * поверхности круга (свежеправленый ≈ 0,2 %, засаленный ≈ 2 %). Мощность P = u·Q_w, Q_w = a·f_a·v_w.
 *
 * <p>Прижог (отпуск закалённого слоя, цвета побежалости) — когда u выше предельной по Малкину:
 * u* = 6,2 + 1,76·d_s^{1/4}·a^{−3/4}·v_w^{−1/2} (d_s, a в мм, v_w в мм/с) [сверить коэффициенты для стали 45]. Глубокая
 * подача и тупой круг — прижог; править круг надо раньше, чем он засалится.
 *
 * <p>Шероховатость — от эквивалентной толщины среза h_eq = a·v_w/v_s: Ra = R_1·(h_eq/h_0)^{0,4} (Snoeys R., Peters J.,
 * «The significance of chip thickness in grinding», CIRP Annals 23 (1974); показатель 0,15–0,6), R_1 = 0,5 мкм при
 * h_0 = 0,1 мкм — по паспортной шероховатости 3Г71 Ra 0,32–0,63 [калибровка].
 *
 * <p>Тейлор к кругу не применим: круг не «тупится по времени», он засаливается по снятому объёму — доля
 * {@link #DRESS_VOLUME_MM3} до правки. Предел окружной скорости — 35 м/с; круг испытывают на разрыв при 1,5 рабочей
 * (ГОСТ 12.3.028), выше — разрыв круга.
 */
public final class Grinding {
    public static final double WHEEL_D_MM = 250, WHEEL_WIDTH_MM = 20;
    /** Рабочая окружная скорость, м/с, и скорость разрыва. */
    public static final double WORKING_SPEED_MS = 35, BURST_SPEED_MS = 1.5 * WORKING_SPEED_MS;
    /** Скорость стола при номинальных оборотах круга, м/мин, и поперечная подача на ход, мм. */
    public static final double TABLE_SPEED_M_MIN = 10, CROSS_FEED_MM = 4;
    /** Энергия стружкообразования, Дж/мм³. */
    public static final double U_CHIP = 13.8;
    /** μ·p̄ — трение площадок износа, Н/мм² [сверить]. */
    public static final double MU_P = 28;
    /** Доля площадок износа: свежеправленый и засаленный круг. */
    public static final double FLAT_FRESH = 0.002, FLAT_DULL = 0.02;
    /** Снятый объём до правки, мм³: ≈ 1000 мм³ на мм ширины круга [сверить]. */
    public static final double DRESS_VOLUME_MM3 = 1000 * WHEEL_WIDTH_MM;
    /** Шероховатость R_1 при h_0 = 0,1 мкм, показатель. */
    private static final double R1_UM = 0.5, H0_UM = 0.1, RA_EXP = 0.4;

    private Grinding() {
    }

    /** Окружная скорость круга, м/с. */
    public static double wheelSpeedMS(double rpm) {
        return Math.PI * WHEEL_D_MM * Math.abs(rpm) / 60_000;
    }

    /** Доля площадок износа при засаливании {@code dullness} (0 — после правки, 1 — пора править). */
    public static double wearFlatShare(double dullness) {
        return FLAT_FRESH + (FLAT_DULL - FLAT_FRESH) * Math.max(0, Math.min(1, dullness));
    }

    /** Съём Q_w = a·f_a·v_w, мм³/с (a в мм, v_w в м/мин). */
    public static double removalRateMm3S(double depthMm, double tableMMin) {
        return depthMm * CROSS_FEED_MM * tableMMin * 1000 / 60;
    }

    /** Удельная энергия u = u_ch + u_sl, Дж/мм³. */
    public static double specificEnergy(double depthMm, double wheelMS, double tableMMin, double dullness) {
        double vRatio = wheelMS * 60 / tableMMin;
        double sliding = MU_P * wearFlatShare(dullness) * vRatio * Math.sqrt(WHEEL_D_MM / depthMm) / 1000;
        return U_CHIP + sliding;
    }

    /** Предельная по прижогу удельная энергия u*, Дж/мм³. */
    public static double burnLimit(double depthMm, double tableMMin) {
        double vwMmS = tableMMin * 1000 / 60;
        return 6.2 + 1.76 * Math.pow(WHEEL_D_MM, 0.25) * Math.pow(depthMm, -0.75) / Math.sqrt(vwMmS);
    }

    /** Ra шлифованной поверхности, мкм. */
    public static double raUm(double depthMm, double wheelMS, double tableMMin) {
        double heqUm = depthMm * (tableMMin / 60) / wheelMS * 1000;
        return R1_UM * Math.pow(heqUm / H0_UM, RA_EXP);
    }
}
