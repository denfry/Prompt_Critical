package dev.denfry.promptcritical.sim.combustion;

/** Выходы котла за тик (изменяемый буфер, переиспользуется). Единицы — СИ. */
public final class BoilerOutputs {
    /** Тепло в воду: топка + конвективные поверхности − потеря в окружающую среду, Вт. */
    public double heatToWaterW;
    /** Тепловосприятие топки (жаровой трубы), Вт. */
    public double furnaceHeatW;
    /** Тепло дымогарных труб, Вт (отрицательно, если холодный воздух остужает воду). */
    public double convectiveHeatW;
    /** Потеря обмуровкой и обечайкой в окружающую среду q₅, Вт. */
    public double shellLossW;
    /** Расход топлива, сгорающего на решётке, кг/с. */
    public double fuelBurnKgS;
    /** Расход воздуха (естественная тяга через шибер), кг/с. */
    public double airKgS;
    /** Тяга трубы, Па. */
    public double draftPa;
    /** Температура газов на выходе из топки T''_т и уходящих газов (в трубу), К. */
    public double furnaceExitK, fluegasExitK;
    /** Мгновенный КПД брутто: тепло в воду / (B·Q_н); NaN без горения. */
    public double efficiency;
    /** Средняя температура металла потолка жаровой трубы и легкоплавкой пробки, К. */
    public double crownTempK, plugTempK;
    /** Потолок жаровой трубы оголён (уровень ниже). */
    public boolean crownUncovered;
    /** Горение за тик: состав газов и выбросы для {@code EmissionLedger} (14). */
    public final CombustionResult combustion = new CombustionResult();
}
