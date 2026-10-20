package dev.denfry.promptcritical.sim.metallurgy;

/** Фаза плавки кислородного конвертера (08 §8.6.1). */
public enum ConverterPhase {
    EMPTY,
    /** Завалка: чугун, лом, известь; лом и известь прогреваются и тают, продувки нет. */
    CHARGED,
    BLOWING,
    /** Фурма поднята после продувки: замер, додувка или выпуск. */
    BLOWN
}
