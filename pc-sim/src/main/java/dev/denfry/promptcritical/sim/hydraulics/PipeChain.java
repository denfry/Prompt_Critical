package dev.denfry.promptcritical.sim.hydraulics;

import java.util.ArrayList;
import java.util.List;

/**
 * Контрактация цепочки проходных труб в одну связь (01 §1.12.1, шаг 2) и разрезание
 * цепочки по ограничениям слияния. Обход графа блоков делает биндинг; здесь — правила.
 *
 * <p>Сегменты разного диаметра приводятся к сегменту с наименьшим сечением: длина трения
 * {@code L_eq = Σ L_k (D_ref/D_k)(A_ref/A_k)²}, ζ — {@code Σ ζ_k (A_ref/A_k)²}. Это точно
 * при одинаковом f; разница f из-за разных Re в пределах смены диаметра ≤ 2× (иначе цепочка
 * режется) — до ~15 % в потерях такого сегмента.
 */
public final class PipeChain {
    /** {@code hydraulics.max_link_length_m}. */
    public static final double MAX_LINK_LENGTH = 30;
    /** {@code hydraulics.max_link_rise_m}. */
    public static final double MAX_LINK_RISE = 4;
    public static final double MAX_DIAMETER_RATIO = 2;
    /** Местные сопротивления при слиянии: колено 90°, тройник, вход, выход. */
    public static final double ZETA_ELBOW_90 = 0.3, ZETA_TEE = 1.0, ZETA_ENTRY = 0.5, ZETA_EXIT = 1.0;

    private PipeChain() {}

    /**
     * Проходной блок цепочки. {@code rise} — подъём по направлению цепочки, м;
     * {@code formLoss} — ζ, отнесённое к сечению этого сегмента (колено, вход, решётка).
     */
    public record Segment(double length, double hydraulicDiameter, double area, double roughness, double rise,
            double formLoss) {
        public Segment {
            if (!(length >= 0)) throw new IllegalArgumentException("length < 0");
            if (!(hydraulicDiameter > 0 && area > 0)) throw new IllegalArgumentException("non-positive section");
            if (!(roughness >= 0 && formLoss >= 0)) throw new IllegalArgumentException("negative loss");
        }

        public static Segment circular(double length, double diameter, double roughness, double rise, double formLoss) {
            return new Segment(length, diameter, Math.PI * diameter * diameter / 4, roughness, rise, formLoss);
        }

        public double volume() {
            return area * length;
        }
    }

    /**
     * Свёрнутая связь. {@code length} — физическая длина (для оценки гидроудара 2L/c),
     * {@code frictionLength} — приведённая длина трения, {@code volume} — объём труб,
     * который делится между концевыми узлами.
     */
    public record Geometry(double inertia, double length, double frictionLength, double hydraulicDiameter,
            double area, double formLossSum, double roughness, double rise, double volume) {}

    public static Geometry contract(List<Segment> chain) {
        if (chain.isEmpty()) throw new IllegalArgumentException("empty chain");
        Segment ref = chain.get(0);
        for (Segment s : chain) if (s.area < ref.area) ref = s;
        double inertia = 0, length = 0, lEq = 0, zeta = 0, rise = 0, volume = 0, relRough = 0;
        for (Segment s : chain) {
            double ar = ref.area / s.area;
            inertia += s.length / s.area;
            length += s.length;
            lEq += s.length * (ref.hydraulicDiameter / s.hydraulicDiameter) * ar * ar;
            zeta += s.formLoss * ar * ar;
            rise += s.rise;
            volume += s.volume();
            relRough += s.length * s.roughness / s.hydraulicDiameter;
        }
        double roughness = length > 0 ? ref.hydraulicDiameter * relRough / length : ref.roughness;
        return new Geometry(inertia, length, lEq, ref.hydraulicDiameter, ref.area, zeta, roughness, rise, volume);
    }

    /**
     * Режет цепочку на участки по ограничениям слияния: длина ≤ {@code maxLength},
     * |подъём| ≤ {@code maxRise}, max D / min D ≤ {@code maxDiameterRatio}. Между участками
     * биндинг ставит промежуточный узел. Сегмент, сам нарушающий ограничение, идёт отдельно.
     */
    public static List<List<Segment>> split(List<Segment> chain, double maxLength, double maxRise,
            double maxDiameterRatio) {
        List<List<Segment>> out = new ArrayList<>();
        List<Segment> run = new ArrayList<>();
        double len = 0, rise = 0, dMin = 0, dMax = 0;
        for (Segment s : chain) {
            if (!run.isEmpty()) {
                double nMin = Math.min(dMin, s.hydraulicDiameter), nMax = Math.max(dMax, s.hydraulicDiameter);
                if (len + s.length > maxLength || Math.abs(rise + s.rise) > maxRise || nMax / nMin > maxDiameterRatio) {
                    out.add(run);
                    run = new ArrayList<>();
                }
            }
            if (run.isEmpty()) {
                len = rise = 0;
                dMin = dMax = s.hydraulicDiameter;
            }
            run.add(s);
            len += s.length;
            rise += s.rise;
            dMin = Math.min(dMin, s.hydraulicDiameter);
            dMax = Math.max(dMax, s.hydraulicDiameter);
        }
        if (!run.isEmpty()) out.add(run);
        return out;
    }

    public static List<List<Segment>> split(List<Segment> chain) {
        return split(chain, MAX_LINK_LENGTH, MAX_LINK_RISE, MAX_DIAMETER_RATIO);
    }
}
