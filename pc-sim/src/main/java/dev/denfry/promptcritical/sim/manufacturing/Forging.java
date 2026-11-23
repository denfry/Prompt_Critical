package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.ArrayList;
import java.util.List;

/**
 * Горячая ковка: сопротивление деформации по температуре и скорости, протяжка и осадка по энергии
 * серии ударов с сохранением объёма, потеря устойчивости длинной осадки, трещины ниже ковочного интервала.
 */
public final class Forging {
    private Forging() {}

    /** Минимальное сечение заготовки, м: тоньше уже не поковка, а проволока [BALANCE]. */
    public static final double MIN_SECTION_M = 0.003;
    /** КПД удара: доля энергии бойка, ушедшая в пластическую деформацию [сверить]. */
    public static final double EFFICIENCY = 0.7;
    /** Предельное отношение длины к стороне при осадке без потери устойчивости (правило кузнеца L/a ≤ 2,5) [сверить]. */
    public static final double UPSET_MAX_LD = 2.5;
    /** Энергия удара ручника, Дж [BALANCE]. */
    public static final double HAND_BLOW_J = 60;
    /** Ударов в серии ручной ковки [BALANCE]. */
    public static final int HAND_BLOWS = 8;
    /** Скорость деформации при ручной ковке, 1/с [моё]. */
    public static final double HAND_STRAIN_RATE = 10;
    /** Длительность серии ручной ковки, с [BALANCE]. */
    public static final double HAND_SERIES_S = 5;
    /** Тяжесть трещины от серии ниже нижней границы интервала [BALANCE]. */
    public static final double CRACK_PER_COLD_SERIES = 0.25;
    /** Тяжесть трещины от серии при температуре более чем на 100 К ниже нижней границы [BALANCE]. */
    public static final double CRACK_PER_VERY_COLD_SERIES = 0.6;
    /** Тяжесть дефекта BUCKLED от одной серии осадки длинной заготовки [BALANCE]. */
    public static final double BUCKLING_PER_SERIES = 0.5;

    /** Коэффициент температурной чувствительности сопротивления деформации, 1/К [сверить]. */
    private static final double BETA_PER_K = 0.0035;
    /** Показатель скоростного упрочнения при горячей деформации [сверить]. */
    private static final double RATE_EXPONENT = 0.15;
    /** Опорная температура σ_ref, К (1000 °C). */
    private static final double REF_TEMP_K = 1273.15;
    /** Ниже этой температуры (600 °C) формула не экстраполируется: сопротивление ограничено сверху. */
    private static final double MIN_FLOW_TEMP_K = 873.15;
    /** Запас по температуре для «очень холодного» удара, К. */
    private static final double VERY_COLD_MARGIN_K = 100;

    /** Вид операции серии ударов. */
    public enum Op { DRAW, UPSET, BEND }

    /** Ковочный интервал: верхняя и нижняя граница, К. */
    public record Range(double upperK, double lowerK) {}

    /** Итог серии: новая заготовка и события. */
    public record Result(Workpiece piece, List<ForgeEvent> events) {}

    /** Ковочный интервал по семейству стали («Справочник кузнеца-штамповщика» [сверить]). */
    public static Range range(SteelFamily f) {
        return switch (f) {
            case CARBON -> new Range(1573, 1023);
            case MEDIUM_CARBON, CHROMIUM -> new Range(1473, 1073);
            case BEARING -> new Range(1423, 1073);
            case WROUGHT_IRON -> new Range(1573, 1073);
            default -> new Range(1473, 1123);
        };
    }

    /** σ_ref при 1273 К и 1/с, МПа (Полухин, «Сопротивление пластической деформации металлов и сплавов», 1983 [сверить]). */
    private static double referenceStressMpa(SteelFamily f) {
        return switch (f) {
            case CARBON -> 90;
            case MEDIUM_CARBON -> 110;
            case CHROMIUM -> 125;
            case BEARING -> 140;
            case WROUGHT_IRON -> 70;
            default -> 110;
        };
    }

    /**
     * Сопротивление деформации, Па: σ_ref·exp(β·(1273,15 − T))·ε̇^m. Температура снизу ограничена
     * 873,15 К, чтобы холодный удар не давал бесконечного сопротивления.
     */
    public static double flowStressPa(SteelGrade g, double tK, double strainRate) {
        double t = Math.max(tK, MIN_FLOW_TEMP_K);
        return referenceStressMpa(g.family()) * 1e6 * Math.exp(BETA_PER_K * (REF_TEMP_K - t))
                * Math.pow(strainRate, RATE_EXPONENT);
    }

    /**
     * Серия ударов: Δε = η·E·n / (σ·V), затем геометрия по операции, трещины по температуре
     * удара и остывание за {@code seriesSeconds}. Если протяжка дала бы сечение, а осадка длину тоньше
     * {@link #MIN_SECTION_M}, геометрия не меняется, событие только TOO_THIN (трещины не проверяются).
     */
    public static Result series(Workpiece w, Op op, double blowEnergyJ, int blows, double strainRate,
            double seriesSeconds, double ambientK) {
        if (!Double.isFinite(blowEnergyJ) || blowEnergyJ < 0 || blows < 0
                || !Double.isFinite(strainRate) || !(strainRate > 0)) {
            throw new IllegalArgumentException("blow energy and blows must be non-negative, strain rate positive");
        }
        double strain = EFFICIENCY * blowEnergyJ * blows
                / (flowStressPa(w.grade(), w.tempK(), strainRate) * w.volumeM3());
        var events = new ArrayList<ForgeEvent>();
        Workpiece p = w;
        switch (op) {
            case DRAW -> {
                double area = w.areaM2() * Math.exp(-strain);
                double a = sideFor(w, area);
                double b = secondSide(w, a);
                if (Math.min(a, b) < MIN_SECTION_M) {
                    return new Result(ForgeHeating.cool(w, ambientK, seriesSeconds), List.of(ForgeEvent.TOO_THIN));
                }
                p = w.withSection(a, b, w.volumeM3() / area);
            }
            case UPSET -> {
                if (w.lengthM() / w.aM() > UPSET_MAX_LD) {
                    p = w.withDefect(PartDefect.BUCKLED, BUCKLING_PER_SERIES);
                    events.add(ForgeEvent.BUCKLED);
                } else {
                    double length = w.lengthM() * Math.exp(-strain);
                    if (length < MIN_SECTION_M) {
                        return new Result(ForgeHeating.cool(w, ambientK, seriesSeconds),
                                List.of(ForgeEvent.TOO_THIN));
                    }
                    double a = sideFor(w, w.volumeM3() / length);
                    p = w.withSection(a, secondSide(w, a), length);
                }
            }
            case BEND -> { }
        }
        double lower = range(w.grade().family()).lowerK();
        if (w.tempK() < lower - VERY_COLD_MARGIN_K) {
            p = p.withDefect(PartDefect.CRACK, CRACK_PER_VERY_COLD_SERIES);
            events.add(ForgeEvent.CRACKED);
        } else if (w.tempK() < lower) {
            p = p.withDefect(PartDefect.CRACK, CRACK_PER_COLD_SERIES);
            events.add(ForgeEvent.CRACKED);
        }
        if (events.isEmpty()) {
            events.add(ForgeEvent.OK);
        }
        return new Result(ForgeHeating.cool(p, ambientK, seriesSeconds), List.copyOf(events));
    }

    /** Диаметр, сторона или толщина сечения заданной площади; FLAT сохраняет отношение b/a заготовки. */
    private static double sideFor(Workpiece w, double area) {
        return switch (w.section()) {
            case ROUND -> Math.sqrt(4 * area / Math.PI);
            case SQUARE -> Math.sqrt(area);
            case FLAT -> Math.sqrt(area * w.aM() / w.bM());
        };
    }

    /** Вторая сторона: для ROUND и SQUARE равна первой, для FLAT сохраняет отношение b/a. */
    private static double secondSide(Workpiece w, double a) {
        return w.section() == Workpiece.Section.FLAT ? a * w.bM() / w.aM() : a;
    }
}
