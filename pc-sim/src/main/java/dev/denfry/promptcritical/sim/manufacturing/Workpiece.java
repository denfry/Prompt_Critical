package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import dev.denfry.promptcritical.sim.metallurgy.StockState;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Заготовка: марка, сечение, размеры, температура, твёрдость и тяжесть дефектов (0..1).
 * {@code aM} — диаметр (ROUND), сторона (SQUARE) или толщина (FLAT); {@code bM} — ширина FLAT,
 * для остальных равна {@code aM}. Длины в метрах, температура в кельвинах.
 */
public record Workpiece(SteelGrade grade, Section section, double aM, double bM, double lengthM,
        double tempK, double hardnessHrc, Map<PartDefect, Double> defects) {

    /** Форма поперечного сечения. */
    public enum Section { ROUND, SQUARE, FLAT }

    public Workpiece {
        if (grade == null || section == null) {
            throw new IllegalArgumentException("grade and section are required");
        }
        if (!(aM > 0) || !(bM > 0) || !(lengthM > 0) || !(tempK > 0)) {
            throw new IllegalArgumentException("aM, bM, lengthM and tempK must be positive");
        }
        var copy = new EnumMap<PartDefect, Double>(PartDefect.class);
        if (defects != null) {
            defects.forEach((d, s) -> {
                if (s != null && s > 0) {
                    copy.put(d, Math.min(1.0, s));
                }
            });
        }
        defects = Collections.unmodifiableMap(copy);
    }

    /** Чистая заготовка без дефектов, твёрдость берётся из марки. */
    public static Workpiece bar(SteelGrade g, Section s, double aM, double bM, double lengthM, double tempK) {
        return new Workpiece(g, s, aM, bM, lengthM, tempK, g.hardnessHrc(), Map.of());
    }

    public double areaM2() {
        return switch (section) {
            case ROUND -> Math.PI * aM * aM / 4;
            case SQUARE -> aM * aM;
            case FLAT -> aM * bM;
        };
    }

    public double volumeM3() {
        return areaM2() * lengthM;
    }

    /** ρ = {@link StockState#DENSITY}. */
    public double massKg() {
        return StockState.DENSITY * volumeM3();
    }

    /** Боковая поверхность плюс два торца. */
    public double surfaceM2() {
        double perimeter = switch (section) {
            case ROUND -> Math.PI * aM;
            case SQUARE -> 4 * aM;
            case FLAT -> 2 * (aM + bM);
        };
        return perimeter * lengthM + 2 * areaM2();
    }

    /** Эквивалентная толщина 2·V/A, м. */
    public double equivalentThicknessM() {
        return 2 * volumeM3() / surfaceM2();
    }

    public Workpiece withTemp(double tK) {
        return new Workpiece(grade, section, aM, bM, lengthM, tK, hardnessHrc, defects);
    }

    public Workpiece withSection(double a, double b, double length) {
        return new Workpiece(grade, section, a, b, length, tempK, hardnessHrc, defects);
    }

    public Workpiece withHardness(double hrc) {
        return new Workpiece(grade, section, aM, bM, lengthM, tempK, hrc, defects);
    }

    public Workpiece withGrade(SteelGrade g) {
        return new Workpiece(g, section, aM, bM, lengthM, tempK, hardnessHrc, defects);
    }

    /** Добавляет тяжесть дефекту, результат ограничен единицей. */
    public Workpiece withDefect(PartDefect d, double add) {
        var m = new EnumMap<PartDefect, Double>(PartDefect.class);
        m.putAll(defects);
        m.merge(d, add, Double::sum);
        return new Workpiece(grade, section, aM, bM, lengthM, tempK, hardnessHrc, m);
    }

    /** Тяжесть дефекта, 0 если его нет. */
    public double severity(PartDefect d) {
        return defects.getOrDefault(d, 0.0);
    }
}
