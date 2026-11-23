package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Паспорт детали (GDD 18 §18.4). Равенство записи есть ключ стека предметов, поэтому значения
 * квантуются: почти одинаковые детали складываются в один стек. Поля «кем сделано» нет намеренно:
 * справочное поле разбивало бы равные стеки.
 *
 * @param itGrade квалитет по ГОСТ 25346; 0 означает «не обработана»
 * @param raUm шероховатость Ra, мкм, значение ряда ГОСТ 2789-73
 * @param defects неизменяемая карта уровней дефектов 1..3, без нулей
 */
public record PartSpec(String kind, String material, PartRoute route, HeatTreatState heatTreat,
                       int hardnessHrc, int itGrade, double raUm, Map<PartDefect, Integer> defects,
                       boolean inspected) {
    /** Шаг квантования твёрдости, HRC [BALANCE]. */
    public static final int HRC_STEP = 5;
    /** Максимум твёрдости по Роквеллу C, HRC. */
    public static final int HRC_MAX = 70;
    /** Порог тяжести, ниже которого дефект не записывается [BALANCE]. */
    public static final double DEFECT_MIN_SEVERITY = 0.05;
    /** Наибольший уровень дефекта [BALANCE]. */
    public static final int MAX_DEFECT_LEVEL = 3;
    /** Предпочтительный ряд Ra, мкм (ГОСТ 2789-73). */
    private static final double[] RA_SERIES =
            {0.025, 0.05, 0.1, 0.2, 0.4, 0.8, 1.6, 3.2, 6.3, 12.5, 25, 50, 100};

    public PartSpec {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(heatTreat, "heatTreat");
        Objects.requireNonNull(defects, "defects");
        if (hardnessHrc < 0 || hardnessHrc > HRC_MAX) {
            throw new IllegalArgumentException("hardnessHrc out of 0.." + HRC_MAX + ": " + hardnessHrc);
        }
        if (itGrade < 0) {
            throw new IllegalArgumentException("itGrade < 0: " + itGrade);
        }
        if (!Double.isFinite(raUm) || raUm <= 0) {
            throw new IllegalArgumentException("raUm must be finite and > 0: " + raUm);
        }
        // канонический вид: конструктор и кодек дают одну и ту же запись
        hardnessHrc = quantizeHrc(hardnessHrc);
        raUm = quantizeRa(raUm);
        var copy = new EnumMap<PartDefect, Integer>(PartDefect.class);
        for (var e : defects.entrySet()) {
            Objects.requireNonNull(e.getKey(), "defect");
            int level = Objects.requireNonNull(e.getValue(), "defect level");
            if (level < 1 || level > MAX_DEFECT_LEVEL) {
                throw new IllegalArgumentException("defect level out of 1..3: " + e.getKey() + "=" + level);
            }
            copy.put(e.getKey(), level);
        }
        defects = java.util.Collections.unmodifiableMap(copy);
    }

    public static PartSpec of(String kind, String material, PartRoute route, HeatTreatState ht,
                              double hardnessHrc, int itGrade, double raUm, Map<PartDefect, Double> severities) {
        if (!Double.isFinite(hardnessHrc) || !Double.isFinite(raUm)) {
            throw new IllegalArgumentException("hardness and Ra must be finite");
        }
        var levels = new EnumMap<PartDefect, Integer>(PartDefect.class);
        for (var e : severities.entrySet()) {
            double s = e.getValue();
            if (Double.isNaN(s)) {
                throw new IllegalArgumentException("NaN severity: " + e.getKey());
            }
            int level = defectLevel(s);
            if (level > 0) {
                levels.put(e.getKey(), level);
            }
        }
        return new PartSpec(kind, material, route, ht, quantizeHrc(hardnessHrc), itGrade, quantizeRa(raUm), levels, false);
    }

    /** Ближайшее кратное 5, в пределах 0..70. */
    public static int quantizeHrc(double hrc) {
        long q = Math.round(hrc / HRC_STEP) * HRC_STEP;
        return (int) Math.max(0, Math.min(HRC_MAX, q));
    }

    /** Ближайшее значение ряда ГОСТ 2789-73 не ниже заданного; выше 100 это 100. */
    public static double quantizeRa(double raUm) {
        for (double v : RA_SERIES) {
            if (raUm <= v) {
                return v;
            }
        }
        return RA_SERIES[RA_SERIES.length - 1];
    }

    /** Уровень дефекта 0..3 по тяжести 0..1 [BALANCE]. */
    public static int defectLevel(double severity) {
        return severity < DEFECT_MIN_SEVERITY ? 0 : Math.min(MAX_DEFECT_LEVEL, (int) (severity * 3) + 1);
    }

    /** Входной контроль: нет дефекта-ворот уровня 1 и выше. */
    public boolean passesGate() {
        for (var e : defects.entrySet()) {
            if (e.getKey().gate && e.getValue() >= 1) {
                return false;
            }
        }
        return true;
    }

    public PartSpec inspect() {
        return new PartSpec(kind, material, route, heatTreat, hardnessHrc, itGrade, raUm, defects, true);
    }
}
