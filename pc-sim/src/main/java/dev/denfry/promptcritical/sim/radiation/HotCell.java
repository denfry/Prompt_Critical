package dev.denfry.promptcritical.sim.radiation;

import java.util.List;

/**
 * Рабочее место горячей камеры (04b §4b.4.4, пример 1): стена из магнетитового бетона 1,0 м, окно из свинцового
 * стекла на всю толщину стены (ASTM C1572) — окно держит не хуже стены, поэтому наружу считается стена. Оператор —
 * в {@value #OPERATOR_M} м от источника на столе камеры. Цель проектирования — не больше
 * {@value #DESIGN_SV_H} Зв/ч у оператора.
 */
public final class HotCell {
    public static final double OPERATOR_M = 1.5, DESIGN_SV_H = 10e-6;
    public static final List<DoseRate.Layer> WALL = List.of(new DoseRate.Layer(ShieldMaterial.HEAVY_CONCRETE, 100));
    public static final List<DoseRate.Layer> WINDOW = List.of(new DoseRate.Layer(ShieldMaterial.LEAD_GLASS, 100));

    private HotCell() {
    }

    /** Источник в камере, видимый снаружи. */
    public static PointSource outside(PointSource inCell) {
        return DoseRate.behind(inCell, WALL);
    }

    /** Мощность дозы у оператора, Зв/ч. */
    public static double operatorSvH(PointSource inCell) {
        return DoseRate.totalSvH(outside(inCell), OPERATOR_M, List.of());
    }
}
