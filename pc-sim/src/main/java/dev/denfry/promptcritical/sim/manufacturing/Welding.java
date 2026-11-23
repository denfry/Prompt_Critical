package dev.denfry.promptcritical.sim.manufacturing;

import java.util.ArrayList;
import java.util.List;

/**
 * Сварка (18 §18.6.10): кузнечная в горне, газовая и дуговая. Шов оценивается погонной энергией q, Дж/м, против
 * нужной для провара толщины: недогрев — непровар, перегрев — пористость. Кузнечная сварка идёт при белом калении с
 * флюсом; без флюса окалина мешает сплавлению.
 */
public final class Welding {
    private Welding() {}

    /** Температура кузнечной сварки, К (≈ 1300 °C) [сверить]. */
    public static final double FORGE_WELD_K = 1573.15;
    /** Ниже FORGE_WELD_K на столько кельвинов куски уже не схватываются [BALANCE]. */
    public static final double FORGE_WELD_MARGIN_K = 150;
    /** Непровар кузнечной сварки без флюса [BALANCE]. */
    public static final double NO_FLUX_FUSION_DEFECT = 0.5;
    /** Сечения стыкуемых частей не должны различаться больше чем на эту долю [моё]. */
    public static final double SECTION_TOLERANCE = 0.25;
    /** Нужная для провара погонная энергия, Дж/м на миллиметр толщины (0,25 кДж/мм на мм) [BALANCE]. */
    public static final double HEAT_INPUT_PER_MM = 250e3;
    /** КПД дуги покрытым электродом и газового пламени [сверить: Николаев «Сварка в машиностроении», 1978]. */
    public static final double ARC_EFFICIENCY = 0.75, GAS_EFFICIENCY = 0.3;
    /** Выше этой доли нужной энергии шов пористый и прожжён [BALANCE]. */
    public static final double OVERHEAT_RATIO = 1.6;

    /** Качество шва: тяжесть непровара и пористости 0..1. */
    public record Joint(double lackOfFusion, double porosity) {
        public static final Joint SOUND = new Joint(0, 0);
    }

    /** Итог сварки: сваренная заготовка (или null, если не сварено) и события. */
    public record Result(Workpiece piece, List<ForgeEvent> events) {}

    /** Погонная энергия дуги q = η·U·I/v, Дж/м. */
    public static double arcHeatInput(double volts, double amps, double speedMs) {
        return ARC_EFFICIENCY * volts * amps / speedMs;
    }

    /** Погонная энергия пламени q = η·P/v, Дж/м. */
    public static double gasHeatInput(double flameW, double speedMs) {
        return GAS_EFFICIENCY * flameW / speedMs;
    }

    /** Нужная погонная энергия для провара толщины, Дж/м. */
    public static double requiredHeatInput(double thicknessM) {
        return HEAT_INPUT_PER_MM * thicknessM * 1000;
    }

    /** Шов газовой или дуговой сварки по погонной энергии и толщине. */
    public static Joint bead(double thicknessM, double heatInputJm) {
        double ratio = heatInputJm / requiredHeatInput(thicknessM);
        return new Joint(clamp01((1 - ratio) / 0.4), clamp01((ratio - OVERHEAT_RATIO) / 0.8));
    }

    /** Шов кузнечной сварки по температуре стыка и флюсу. Холоднее порога — null: куски не схватились. */
    public static Joint forgeJoint(double tempK, boolean flux) {
        if (tempK < FORGE_WELD_K - FORGE_WELD_MARGIN_K) return null;
        double cold = clamp01((FORGE_WELD_K - tempK) / FORGE_WELD_MARGIN_K);
        return new Joint(clamp01((flux ? 0 : NO_FLUX_FUSION_DEFECT) + 0.6 * cold), 0);
    }

    /**
     * Стыковой шов двух частей одной стали и формы сечения с площадями в допуске. Объём сохраняется, сечение — по
     * средней площади, температура — средняя по массе; дефекты частей берутся по максимуму, к ним добавляется шов.
     */
    public static Result join(Workpiece a, Workpiece b, Joint joint) {
        if (joint == null) return new Result(null, List.of(ForgeEvent.TOO_COLD));
        if (a.grade().family() != b.grade().family() || a.section() != b.section()
                || Math.abs(a.areaM2() - b.areaM2()) > SECTION_TOLERANCE * Math.max(a.areaM2(), b.areaM2())) {
            return new Result(null, List.of(ForgeEvent.MISMATCH));
        }
        double length = a.lengthM() + b.lengthM();
        double volume = a.volumeM3() + b.volumeM3();
        double scale = Math.sqrt(volume / length / a.areaM2());
        Workpiece p = a.withSection(a.aM() * scale, a.bM() * scale, length)
                .withTemp((a.tempK() * a.volumeM3() + b.tempK() * b.volumeM3()) / volume);
        for (var e : b.defects().entrySet()) {
            double more = e.getValue() - p.severity(e.getKey());
            if (more > 0) p = p.withDefect(e.getKey(), more);
        }
        var events = new ArrayList<ForgeEvent>();
        if (joint.lackOfFusion() > 0) {
            p = p.withDefect(PartDefect.LACK_OF_FUSION, joint.lackOfFusion());
            events.add(ForgeEvent.POOR_FUSION);
        }
        if (joint.porosity() > 0) p = p.withDefect(PartDefect.POROSITY, joint.porosity());
        if (events.isEmpty()) events.add(ForgeEvent.OK);
        return new Result(p, List.copyOf(events));
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }
}
