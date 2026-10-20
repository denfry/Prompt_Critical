package dev.denfry.promptcritical.sim.combustion;

/** Выходы котла за тик (изменяемый буфер, переиспользуется). Единицы — СИ. */
public final class BoilerOutputs {
    /**
     * Тепло в воду, Вт: жаротрубный — топка + дымогарные трубы − потеря обечайкой; водотрубный — экраны топки
     * (испарительный контур, узел барабана) − потеря обмуровкой.
     */
    public double heatToWaterW;
    /** Тепловосприятие топки (жаровой трубы), Вт. */
    public double furnaceHeatW;
    /**
     * Конвективные поверхности, Вт: жаротрубный — дымогарные трубы (отрицательно, если холодный воздух остужает
     * воду); водотрубный — пароперегреватель + экономайзер.
     */
    public double convectiveHeatW;
    /** Водотрубный: тепло в пар пароперегревателя и в воду экономайзера, Вт. */
    public double superheaterHeatW, economizerHeatW;
    /** Водотрубный: воздух после воздухоподогревателя (идёт в горелки на следующем тике), К. */
    public double airPreheatK;
    /** Водотрубный: давление в топке относительно атмосферы, Па (норма −20…−50). */
    public double furnacePressurePa;
    /** Водотрубный: тепловой поток на экраны в поясе горелок, Вт/м², и температура металла там (с накипью), К. */
    public double screenFluxWm2, screenMetalK;
    /** Водотрубный: топливо, накопленное в топке без факела, кг. */
    public double furnaceFuelKg;
    /** Водотрубный: в топке есть хотя бы один факел. */
    public boolean flame;
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
