package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Геометрия модели отливки (08 §8.9) — то, что нужно заливке и механообработке. В GDD задан только корпус DN 100 с
 * модулем 1,5 см; остальное — порядки игровых деталей [BALANCE]: масса металла, модуль V/A, разнотолщинность (у
 * крыльчатки лопатки тоньше ступицы в 4 раза — сталь и Al рвутся горячими трещинами), объёмы снятия на токарном и
 * сверлильном станках и диаметры обработки. Прибыль модели — модуль 1,25 модуля отливки (≥ 1,2 по §8.9).
 *
 * @param massKg         масса металла в форме, кг
 * @param moduleCm       модуль отливки V/A, см
 * @param thicknessRatio отношение наибольшей толщины стенки к наименьшей
 * @param dn             условный проход корпуса, мм (0 — не корпус)
 * @param sandItems      формовочной смеси на форму, предметов {@code foundry_sand}
 * @param turnVolumeMm3  снятие на токарном, мм³ (0 — не точится)
 * @param turnDiameterMm диаметр точения, мм
 * @param drillVolumeMm3 снятие на сверлильном, мм³ (0 — не сверлится)
 * @param drillDiameterMm диаметр сверла, мм
 */
public record CastingGeometry(double massKg, double moduleCm, double thicknessRatio, int dn, int sandItems,
        double turnVolumeMm3, double turnDiameterMm, double drillVolumeMm3, double drillDiameterMm) {
    /** Модуль прибыли относительно модуля отливки. */
    public static final double RISER_RATIO = 1.25;

    public static CastingGeometry of(CastingKind kind) {
        return switch (kind) {
            case VALVE_BODY -> new CastingGeometry(35, 1.5, 2.0, 100, 2, 15_000, 220, 20_000, 18);
            case PUMP_CASING -> new CastingGeometry(80, 2.0, 2.5, 150, 4, 30_000, 300, 30_000, 22);
            case IMPELLER -> new CastingGeometry(15, 0.8, 4.0, 0, 1, 10_000, 250, 8_000, 40);
            case GEAR_BLANK -> new CastingGeometry(40, 2.5, 1.5, 0, 2, 25_000, 260, 12_000, 40);
            case WHEEL -> new CastingGeometry(60, 2.2, 2.0, 0, 3, 30_000, 400, 12_000, 50);
            case FLYWHEEL -> new CastingGeometry(200, 4.0, 3.0, 0, 8, 60_000, 800, 25_000, 60);
            case INGOT_MOLD -> new CastingGeometry(500, 6.0, 1.0, 0, 16, 0, 0, 0, 0);
            case BEARING_HOUSING -> new CastingGeometry(25, 1.4, 2.0, 0, 2, 12_000, 180, 15_000, 30);
        };
    }

    /** Модуль прибыли, см; NaN — модель без прибыли. */
    public double riserModuleCm(boolean riser) {
        return riser ? RISER_RATIO * moduleCm : Double.NaN;
    }
}
