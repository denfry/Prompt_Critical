package dev.denfry.promptcritical.sim.combustion;

/**
 * Входы котла на тик (изменяемый буфер, заполняет биндинг). Единицы — СИ. Поля дутья, пароперегревателя и
 * экономайзера — только у водотрубного котла.
 */
public final class BoilerInputs {
    /**
     * Подача топлива, кг/с: жаротрубный — на решётку цепным забрасывателем (ручная загрузка —
     * {@code FireTubeBoiler.chargeFuel}); водотрубный — в коллектор горелок регулирующим клапаном, до отсечных
     * клапанов BMS.
     */
    public double fuelFeedKgS;
    /** Открытие шибера дымохода, [0, 1]. */
    public double damper = 1;
    /** Наружный воздух: температура (биом, 07 §7.13.1), К, и давление, Па. */
    public double ambientK = 288.15, ambientPa = 101_325;
    /** Вода в котле (узел гидросети): температура, К, и давление, Па. */
    public double waterTempK = 373.15, waterPressurePa = 101_325;
    /** Уровень смеси над низом водяного узла (у водотрубного — барабана), м. */
    public double waterLevelM;
    /** Дутьевой вентилятор: расход воздуха в топку, кг/с. */
    public double forcedAirKgS;
    /** Дымосос: расход газов, который он отсасывает при нулевом давлении в топке, кг/с. */
    public double inducedDraftKgS;
    /** Пар на входе пароперегревателя: расход, кг/с, температура, К, теплоёмкость, Дж/(кг·К). */
    public double steamFlowKgS, steamInletK = 573.15, steamCpJKgK = 3_000;
    /** Питательная вода на входе экономайзера: расход, кг/с, температура, К, теплоёмкость, Дж/(кг·К). */
    public double feedwaterFlowKgS, feedwaterInletK = 373.15, feedwaterCpJKgK = 4_600;
}
