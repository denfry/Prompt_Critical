package dev.denfry.promptcritical.sim.radiation;

import java.util.Map;

/**
 * Природный радиационный фон (04b §4b.13; UNSCEAR 2000).
 * <ul>
 *   <li>Земное γ: D = Σ D∞,m · Ω/2π · f_m, где D∞,m — мощность поглощённой дозы в воздухе на 1 м над полупространством
 *   материала m, а Ω/2π = 2·f для доли f полной сферы обзора.</li>
 *   <li>Космическое излучение: удвоение каждые 1500 м высоты, ослабление толщей породы с остаточным мюонным полом.</li>
 *   <li>Эквивалент амбиентной дозы H* = 1,2·D.</li>
 * </ul>
 * Мощность земной дозы — в нГр/ч, итоговый фон — в мкЗв/ч.
 */
public final class NaturalBackground {
    /** Грунт в поле зрения; D∞ на 1 м над полупространством, нГр/ч (04b §4b.13.1, UNSCEAR 2000). */
    public enum Ground {
        STONE(107), DEEP(23), SOIL(60), SAND(22), CLAY(73), NONE(0),
        /** Руда с 1 % U₃O₈ — [BALANCE] по содержанию. */
        URANIUM_ORE(48_400);

        public final double infiniteNGyH;

        Ground(double infiniteNGyH) {
            this.infiniteNGyH = infiniteNGyH;
        }
    }

    public enum Dimension { OVERWORLD, NETHER, END }

    /** Космический фон на уровне поверхности (y = 63), мкЗв/ч (04b §4b.13.2). */
    static final double COSMIC_SURFACE_USV_H = 0.035;
    static final double SURFACE_Y = 63, DOUBLING_M = 1500;
    /** Длина ослабления нуклонной компоненты породой, м (в блоках; [моё] по кривой 04b §4b.13.2). */
    static final double OVERBURDEN_E_FOLD_M = 2;
    /** Остаточный мюонный фон 10 % (04b §4b.13.2; [моё] — трактовка, делает кривую непрерывной). */
    static final double MUON_FLOOR = 0.1;
    /** Незер, мкЗв/ч — [BALANCE], 04b §4b.13.2. */
    static final double NETHER_USV_H = 0.5;
    /** Энд: десятикратный космический фон Верхнего мира — [BALANCE], 04b §4b.13.2. */
    static final double END_FACTOR = 10;
    /** H* = 1,2·D для воздуха (ICRP 74 / 04b §4b.13). */
    static final double H_STAR_PER_GRAY = 1.2;

    private NaturalBackground() {
    }

    /** Земное γ, нГр/ч: Σ D∞,m · 2·f_m; f_m — доля полной сферы, зажата в 0..1 (null и NaN дают 0), сумма не обязана быть 1. */
    public static double terrestrialNGyH(Map<Ground, Double> sphereFraction) {
        double sum = 0;
        for (Map.Entry<Ground, Double> e : sphereFraction.entrySet()) {
            Double v = e.getValue();
            double f = v == null || !(v > 0) ? 0 : Math.min(v, 1); // null и NaN — нет доли
            sum += e.getKey().infiniteNGyH * 2 * f;
        }
        return sum;
    }

    /** Космический фон, мкЗв/ч, на высоте y (блоки) под толщей породы overburdenM (м). */
    public static double cosmicUSvH(Dimension d, double y, double overburdenM) {
        return switch (d) {
            case NETHER -> NETHER_USV_H;
            case OVERWORLD -> overworld(y, overburdenM);
            case END -> END_FACTOR * overworld(y, overburdenM);
        };
    }

    private static double overworld(double y, double overburdenM) {
        double depth = Math.max(0, overburdenM);
        double altitude = Math.pow(2, (y - SURFACE_Y) / DOUBLING_M);
        return COSMIC_SURFACE_USV_H * altitude
                * ((1 - MUON_FLOOR) * Math.exp(-depth / OVERBURDEN_E_FOLD_M) + MUON_FLOOR);
    }

    /** Амбиентный эквивалент дозы, мкЗв/ч: 1,2·D(нГр/ч)/1000 + космический. */
    public static double ambientUSvH(double terrestrialNGyH, double cosmicUSvH) {
        return H_STAR_PER_GRAY * terrestrialNGyH / 1000 + cosmicUSvH;
    }
}
