package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Строгание на поперечно-строгальном станке типа 7305 (ход ползуна до 500 мм, кулисный привод): резец режет только на
 * рабочем ходе, обратный ход ускоренный.
 *
 * <p>Кулиса даёт отношение времени рабочего хода к холостому ≈ 1,5 (угол поворота кривошипа ≈ 216° из 360°), так что
 * резание идёт долю цикла {@link #CUT_SHARE} = 0,6, а средняя скорость рабочего хода v_c = L·n/(1000·0,6) м/мин при
 * n двойных ходах в минуту (Ачеркан Н.С. «Металлорежущие станки», т. 1, 1965, кулисный механизм).
 *
 * <p>Сечение среза резцом с главным углом в плане κ = 45°: толщина h = f·sin κ, ширина b = a_p/sin κ; сила по Кинцле
 * F_c = b·h·k_c(h), мощность на рабочем ходе P = F_c·v_c, средняя по циклу — доля 0,6 от неё.
 */
public final class Shaping {
    /** Ход ползуна с перебегом, мм (деталь 400 мм + 2×25 мм перебега). */
    public static final double STROKE_MM = 450;
    /** Доля цикла под резанием. */
    public static final double CUT_SHARE = 0.6;
    /** Главный угол в плане проходного строгального резца, рад. */
    public static final double KAPPA = Math.toRadians(45);
    /** Радиус при вершине резца, мм. */
    public static final double NOSE_RADIUS_MM = 1.0;

    private Shaping() {
    }

    /** Средняя скорость рабочего хода, м/мин. */
    public static double cuttingSpeedMMin(double strokeMm, double doubleStrokesPerMin) {
        return strokeMm * Math.abs(doubleStrokesPerMin) / (1000 * CUT_SHARE);
    }

    /** Съём за цикл, средний по времени: L·f·a_p·n, мм³/с. */
    public static double removalRateMm3S(double strokeMm, double feedMm, double depthMm, double doubleStrokesPerMin) {
        return strokeMm * feedMm * depthMm * Math.abs(doubleStrokesPerMin) / 60;
    }

    /** Сила резания F_c = b·h·k_c(h), Н. */
    public static double cuttingForceN(Machining.WorkMaterial m, double feedMm, double depthMm, double wear) {
        double h = feedMm * Math.sin(KAPPA);
        double b = depthMm / Math.sin(KAPPA);
        return b * h * Machining.kienzleKc(m, h) * Machining.wearForceFactor(wear);
    }

    /** Мощность на рабочем ходе, Вт. */
    public static double strokePowerW(double forceN, double vcMMin) {
        return forceN * vcMMin / 60;
    }
}
