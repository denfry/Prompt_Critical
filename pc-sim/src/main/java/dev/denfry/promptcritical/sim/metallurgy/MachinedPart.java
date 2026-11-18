package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Деталь станочного парка — то, что фрезерный, строгальный и шлифовальный станки делают для постройки машин завода.
 * Объём снятия — порядок игровой детали [BALANCE], как у {@link CastingGeometry}: при номинальном режиме деталь
 * выходит за 1–2 минуты; режим резания, мощность, износ и чистота поверхности считаются по реальным формулам.
 *
 * @param itemId   ID предмета
 * @param machine  станок
 * @param volumeMm3 снятие, мм³
 * @param depthMm  у фрезы — глубина a_e (высота зуба 2,25m, глубина паза), у строгального — глубина a_p
 * @param widthMm  у фрезы — ширина a_p (впадина зуба πm/2, ширина паза); NaN — не фрезерная
 * @param maxRaUm  наибольшая допустимая шероховатость, мкм; грубее — брак
 */
public record MachinedPart(String itemId, ProductionToolUnit.Kind machine, double volumeMm3, double depthMm, double widthMm,
        double maxRaUm) {
    /** Прямозубое колесо m = 5 мм: впадина 2,25m × πm/2 модульной фрезой; 8-я степень точности — Ra ≤ 3,2. */
    public static final MachinedPart SPUR_GEAR = new MachinedPart("spur_gear", ProductionToolUnit.Kind.MILLING, 12_000,
            11.25, 7.85, 3.2);
    /** Вал Ø40 со шпоночным пазом 12×8 (ГОСТ 23360: b = 12, t₁ = 5) дисковой фрезой; Ra паза ≤ 6,3. */
    public static final MachinedPart KEYED_SHAFT = new MachinedPart("keyed_shaft", ProductionToolUnit.Kind.MILLING, 6_000,
            5, 12, 6.3);
    /** Чугунная направляющая станины, строганая под шабрение: Ra ≤ 3,2. */
    public static final MachinedPart MACHINE_SLIDEWAY = new MachinedPart("machine_slideway", ProductionToolUnit.Kind.SHAPER,
            15_000, 2, Double.NaN, 3.2);
    /** Плита, строганая под шлифование (припуск снимет круг): Ra ≤ 12,5. */
    public static final MachinedPart PLANED_PLATE = new MachinedPart("planed_plate", ProductionToolUnit.Kind.SHAPER, 12_000,
            2, Double.NaN, 12.5);
    /** Шлифованная плоская плита: припуск 0,05 мм с 200×100 мм; Ra ≤ 0,8. */
    public static final MachinedPart GROUND_PLATE = new MachinedPart("ground_plate", ProductionToolUnit.Kind.GRINDER, 1_000,
            Double.NaN, Double.NaN, 0.8);

    private static final MachinedPart[] ALL = {SPUR_GEAR, KEYED_SHAFT, MACHINE_SLIDEWAY, PLANED_PLATE, GROUND_PLATE};

    public static MachinedPart[] values() {
        return ALL.clone();
    }

    public static MachinedPart byItemId(String id) {
        for (MachinedPart p : ALL) if (p.itemId.equals(id)) return p;
        return null;
    }

    /**
     * Деталь из отливки: точёная заготовка шестерни — зубчатое колесо (фрезерный), станина — направляющая и плита —
     * строганая плита (строгальный). Отбелённый чугун не режется; {@code null} — отливка станку не нужна.
     */
    public static MachinedPart fromCasting(CastingSpec c) {
        if (c == null || c.defect() == CastingDefect.CHILL || CastAlloy.byId(c.alloyId()) == null) return null;
        return switch (c.kind()) {
            case GEAR_BLANK -> (c.machinedMask() & CastingSpec.TURNED) != 0 ? SPUR_GEAR : null;
            case MACHINE_BED -> MACHINE_SLIDEWAY;
            case BASE_PLATE -> PLANED_PLATE;
            default -> null;
        };
    }
}
