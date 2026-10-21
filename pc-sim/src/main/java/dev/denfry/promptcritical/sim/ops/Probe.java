package dev.denfry.promptcritical.sim.ops;

/**
 * Способ обнаружения неисправности {@code MEASURED} в срезе I0–I1 (13 §13.6.7, лестница обнаружения). Прибор видит
 * износ только со своей стадии: ниже неё замер показывает норму.
 */
public enum Probe {
    /** Упрощённый {@code mechanic_stethoscope}: подшипник со стадии 1 (D_b 0,6), сальник. */
    STETHOSCOPE,
    /** Рукой по корпусу: подшипник со стадии 3 (D_b 0,9). */
    HAND,
    /** Осмотр: обрывы проволок каната, пар или вода у штока. */
    INSPECTION;

    /** Показание замера; NaN — прибор к этому виду не применим. */
    public double reading(MaintenanceFaultKind kind, double value) {
        return switch (this) {
            case STETHOSCOPE -> switch (kind) {
                case BEARING_WEAR -> value >= PlainBearing.STAGE_START[1] ? value : 0;
                case PACKING_LEAK -> value;
                default -> Double.NaN;
            };
            case HAND -> kind == MaintenanceFaultKind.BEARING_WEAR
                    ? value >= PlainBearing.STAGE_START[3] ? value : 0 : Double.NaN;
            case INSPECTION -> kind == MaintenanceFaultKind.ROPE_WEAR || kind == MaintenanceFaultKind.PACKING_LEAK
                    ? value : Double.NaN;
        };
    }
}
