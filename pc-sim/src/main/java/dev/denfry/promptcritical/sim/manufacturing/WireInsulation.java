package dev.denfry.promptcritical.sim.manufacturing;

/**
 * Изоляция обмоточного провода (18 §18.6.9): класс нагревостойкости, толщина на сторону, м, и чувствительность к
 * наклёпу меди — эмаль на нагартованной проволоке трескается при укладке [сверить толщины по ГОСТ 26615 (эмаль,
 * повышенная — 0,035 мм) и ГОСТ 16512 (ПБ — 0,15 мм)]. Сериализуется по {@code name()}.
 */
public enum WireInsulation {
    NONE(null, 0, 0),
    /** Кабельная бумага, класс A. */
    PAPER(InsulationClass.A, 0.15e-3, 0.2),
    /** Фенольная эмаль, класс B. */
    ENAMEL(InsulationClass.B, 0.035e-3, 1.0),
    /** Эмаль со стекловолокном, класс F. */
    GLASS_ENAMEL(InsulationClass.F, 0.10e-3, 0.6);

    public final InsulationClass insulationClass;
    public final double radialM, strainSensitivity;

    WireInsulation(InsulationClass c, double radialM, double strainSensitivity) {
        this.insulationClass = c;
        this.radialM = radialM;
        this.strainSensitivity = strainSensitivity;
    }

    /** Диаметр провода в изоляции, м. */
    public double insulatedM(double bareM) {
        return bareM + 2 * radialM;
    }
}
