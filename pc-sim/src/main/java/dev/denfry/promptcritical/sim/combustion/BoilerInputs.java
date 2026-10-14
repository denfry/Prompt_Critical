package dev.denfry.promptcritical.sim.combustion;

/** Входы котла на тик (изменяемый буфер, заполняет биндинг). Единицы — СИ. */
public final class BoilerInputs {
    /** Подача топлива на решётку цепным забрасывателем, кг/с; ручная загрузка — {@code FireTubeBoiler.chargeFuel}. */
    public double fuelFeedKgS;
    /** Открытие шибера дымохода, [0, 1]. */
    public double damper = 1;
    /** Наружный воздух: температура (биом, 07 §7.13.1), К, и давление, Па. */
    public double ambientK = 288.15, ambientPa = 101_325;
    /** Вода в котле (узел гидросети): температура, К, и давление, Па. */
    public double waterTempK = 373.15, waterPressurePa = 101_325;
    /** Уровень смеси над низом водяного узла, м. */
    public double waterLevelM;
}
