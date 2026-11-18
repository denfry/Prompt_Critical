package dev.denfry.promptcritical.sim.ops;

/**
 * Вид наряда-допуска (13 §13.2.1). В моде работают {@code COLD} (ремонт оборудования работающей установки), {@code HOT}
 * (огневые работы, §13.2.5) и {@code RADIATION} (работы в зоне излучения по плану дозы, 04b §4b.12); электрический
 * наряд на аппарат сети — отдельная модель {@link dev.denfry.promptcritical.sim.grid.WorkPermit} (ПОТ ЭЭ).
 */
public enum PermitType {
    COLD, HOT, CONFINED_SPACE, ELECTRICAL, RADIATION, MOC;

    /** Перед допуском нужен газоанализ (§13.2.3). */
    public boolean needsGasTest() {
        return this == HOT || this == CONFINED_SPACE;
    }

    /** Нужен пожарный пост с первичными средствами тушения (§13.2.1). */
    public boolean needsFireWatch() {
        return this == HOT;
    }
}
