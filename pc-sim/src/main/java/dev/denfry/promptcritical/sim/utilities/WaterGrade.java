package dev.denfry.promptcritical.sim.utilities;

/**
 * Сорт воды в мире (08 §8.12): жидкости Create несут не {@code water_quality}, а сорт с номинальным качеством —
 * стеки с разными компонентами баки и трубы не смешивают. Качество — после ступеней {@link WaterTreatmentTrain} на
 * сырой речной воде {@link WaterQuality#RAW_RIVER}; умягчённая — с проскоком свежей Na-колонны 1 %.
 */
public enum WaterGrade {
    /** Ванильная вода и {@code raw_water}. */
    RAW("raw_water", WaterQuality.RAW_RIVER),
    /** После осветлителя с известкованием: карбонатная жёсткость до 0,6 мг-экв/л, шлам CaCO₃ во взвеси. */
    CLARIFIED("clarified_water", WaterQuality.of(1.6, 9e-6, 30e-6, 10e-6, 20e-6, WaterTreatmentTrain.LIMED_PH, 0.015, 0.6)),
    /** После песчаного фильтра. */
    FILTERED("filtered_water", WaterQuality.of(1.6, 9e-6, 30e-6, 10e-6, 20e-6, WaterTreatmentTrain.LIMED_PH, 0.0003, 0.6)),
    /** После Na-катионитной колонны: жёсткость заменена натрием. */
    SOFTENED("softened_water", WaterQuality.of(0.016, 9e-6, 30e-6, 10e-6, 56e-6, WaterTreatmentTrain.LIMED_PH, 0.0003, 0.6));

    private final String fluidId;
    private final WaterQuality quality;

    WaterGrade(String fluidId, WaterQuality quality) {
        this.fluidId = fluidId;
        this.quality = quality;
    }

    /** ID жидкости без пространства имён. */
    public String fluidId() {
        return fluidId;
    }

    public WaterQuality quality() {
        return quality;
    }

    /** Сорт по ID жидкости без пространства имён; ванильная вода — {@link #RAW}; не вода — null. */
    public static WaterGrade byFluid(String id) {
        if ("water".equals(id)) return RAW;
        for (WaterGrade g : values()) if (g.fluidId.equals(id)) return g;
        return null;
    }
}
