package dev.denfry.promptcritical.sim.utilities;

/** События водоподготовки и ВРУ для мира (журнал, сигнализация 03c, SIS, {@code DamageBudget}); без действия. */
public interface UtilitiesEvents {
    UtilitiesEvents NONE = new UtilitiesEvents() {};

    /** Колонна прошла рабочую ёмкость — проскок жёсткости ({@code pc_resin_breakthrough}). */
    default void resinBreakthrough(int column) {}

    /** Песчаный фильтр забит взвесью: взвесь идёт на иониты, нужна промывка. */
    default void filterClogged() {}

    default void asuStateChanged(AsuState from, AsuState to) {}

    /** Углеводороды в LOX выше уставки {@code asu.lox_hydrocarbon_trip_ppm} ({@code ASU_HYDROCARBON_HIGH}). */
    default void asuHydrocarbonHigh(double ppm) {}

    /** Проскок CO₂ и влаги через адсорберы ({@code ASU_MOLSIEVE_BREAKTHROUGH}): лёд в основном теплообменнике. */
    default void asuMolsieveBreakthrough() {}

    /** Взрыв конденсатора-испарителя (Бинтулу, 1997): энергия для {@code DamageRequest(BLAST)}, Дж. */
    default void asuExploded(double energyJ) {}
}
