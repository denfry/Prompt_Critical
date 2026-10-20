package dev.denfry.promptcritical.sim.metallurgy;

/** События сталеплавильного передела для мира (журнал, SIS, звук); все методы — без действия. */
public interface SteelmakingEvents {
    SteelmakingEvents NONE = new SteelmakingEvents() {};

    default void converterPhaseChanged(ConverterPhase from, ConverterPhase to) {}

    /** Выброс шлака и металла из горловины: высокий Si чугуна при низкой фурме (08 §8.6.1). */
    default void slopping(double slagKg, double metalKg) {}

    /** Передув: C уже ниже {@link Converter#OVERBLOW_C}, кислород уходит в FeO шлака. */
    default void overblown() {}

    /** Фурма опущена в ванну: медный наконечник прогорает, вода фурмы течёт в металл ({@code BOF_LANCE_WATER_LEAK}). */
    default void lanceDamaged() {}

    /** Выпуск с нерасплавленным ломом: лом остался коржом в конвертере и для плавки пропал. */
    default void scrapUnmelted(double kg) {}
}
