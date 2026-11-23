package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Вырубка пластин (18 §18.6.8). Усилие P = L·s·τ_ср, τ_ср = {@link #SHEAR_RATIO}·σ_в. Если пресс тянет всю пластину
 * за ход — вырубка совмещённым штампом; если нет — пазовая штамповка: паз за пазом и два хода на окружности
 * (так штамповали статоры на пазовырубных прессах). Заусенец растёт с отходом зазора от оптимального и с износом
 * штампа; переточка возвращает его к свежему.
 */
public final class Punching {
    private Punching() {}

    /** τ_ср / σ_в [сверить: 0,75–0,85 для стали — Романовский, «Справочник по холодной штамповке», 1979]. */
    public static final double SHEAR_RATIO = 0.8;
    /** Оптимальный зазор на сторону, доля толщины [сверить: 5–8 % для кремнистой стали]. */
    public static final double CLEARANCE_OPT = 0.06;
    /** Заусенец свежего штампа при оптимальном зазоре, доля толщины [сверить: 10–20 мкм при 0,5 мм]. */
    public static final double BURR_FRESH = 0.03;
    /** Рост заусенца на долю толщины при отходе зазора за пределы ±2 % [моё]. */
    public static final double BURR_CLEARANCE_GAIN = 0.6, CLEARANCE_TOLERANCE = 0.02;
    /** Износ: прирост заусенца, м, на {@link #DIE_LIFE_STROKES} ходов [сверить: допуск 0,05 мм; BALANCE ходы]. */
    public static final double BURR_WEAR_M = 40e-6;
    public static final long DIE_LIFE_STROKES = 5000;

    public enum Mode { COMPOUND, NOTCHING, TOO_WEAK }

    /** Итог вырубки одной пластины: пластина (null, если пресс слаб), режим, ходов и наибольшее усилие хода, Н. */
    public record Result(Lamination lamination, Mode mode, int strokes, double peakForceN) {}

    /** Усилие вырубки P = L·s·τ_ср, Н. */
    public static double forceN(double cutLengthM, double thicknessM, double utsPa) {
        return cutLengthM * thicknessM * SHEAR_RATIO * utsPa;
    }

    /** Высота заусенца, м, при зазоре {@code clearance} (доля толщины на сторону) и ходах штампа с переточки. */
    public static double burrM(double thicknessM, double clearance, long dieStrokes) {
        double off = Math.max(0, Math.abs(clearance - CLEARANCE_OPT) - CLEARANCE_TOLERANCE);
        return thicknessM * (BURR_FRESH + BURR_CLEARANCE_GAIN * off)
                + BURR_WEAR_M * Math.max(0, dieStrokes) / DIE_LIFE_STROKES;
    }

    /** Вырубить пластину формы {@code shape} из листа на прессе с усилием не больше {@code pressCapacityN}. */
    public static Result punch(ElectricalSheet sheet, LaminationShape shape, double clearance, long dieStrokes,
            double pressCapacityN) {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(shape, "shape");
        if (!(pressCapacityN > 0) || !(clearance > 0)) throw new IllegalArgumentException("capacity and clearance > 0");
        double s = sheet.thicknessM(), uts = sheet.utsPa();
        double full = forceN(shape.cutLengthM(), s, uts);
        double burr = burrM(s, clearance, dieStrokes);
        if (full <= pressCapacityN)
            return new Result(new Lamination(sheet, shape, burr, false, false), Mode.COMPOUND, 1, full);
        double ring = forceN(Math.PI * shape.outerM(), s, uts);
        double slot = forceN(shape.slotCutM(), s, uts);
        if (ring > pressCapacityN) return new Result(null, Mode.TOO_WEAK, 0, ring);
        return new Result(new Lamination(sheet, shape, burr, false, false), Mode.NOTCHING, shape.slots() + 2,
                Math.max(ring, slot));
    }
}
