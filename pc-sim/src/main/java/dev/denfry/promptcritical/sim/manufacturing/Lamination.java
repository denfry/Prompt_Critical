package dev.denfry.promptcritical.sim.manufacturing;

import java.util.Objects;

/**
 * Вырубленная пластина (18 §18.6.8): лист, из которого она вырублена, форма, высота заусенца, м, снят ли наклёп кромок
 * отжигом после вырубки и лакирована ли пластина уже после вырубки (лак тогда затекает на заусенец).
 */
public record Lamination(ElectricalSheet sheet, LaminationShape shape, double burrM, boolean edgesRelieved,
                         boolean coatedAfterPunch) {
    public Lamination {
        Objects.requireNonNull(sheet, "sheet");
        Objects.requireNonNull(shape, "shape");
        if (!(burrM >= 0) || !Double.isFinite(burrM)) throw new IllegalArgumentException("burr must be >= 0");
    }

    /** Отжиг для снятия наклёпа кромок: лист отжигается целиком, лак выгорает. */
    public Lamination anneal() {
        return new Lamination(sheet.anneal(), shape, burrM, true, false);
    }

    /** Лакировка готовой пластины. */
    public Lamination coat(SheetCoating c) {
        return new Lamination(sheet.coat(c), shape, burrM, edgesRelieved, c != SheetCoating.NONE);
    }

    public double massKg() {
        return shape.areaM2() * sheet.thicknessM() * ElectricalSheet.densityKgM3(sheet.siFrac());
    }
}
