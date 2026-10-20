package dev.denfry.promptcritical.sim.combustion;

/** События водотрубного котла и BMS для мира (журнал, лампы щита, сирена, {@code DamageBudget}); без действия. */
public interface BoilerEvents {
    BoilerEvents NONE = new BoilerEvents() {};

    default void bmsStateChanged(BmsState from, BmsState to) {}

    /** Главное топливное отсечение. */
    default void masterFuelTrip(MftCause cause) {}

    /** Отключена одна горелка (проба не удалась или погас её факел), остальные работают. */
    default void burnerTripped(int burner) {}

    /** Разрыв трубы экрана ({@code BOILER_TUBE_LEAK}): пар в топку, факел гаснет. */
    default void tubeLeak() {}

    /** Взрыв топки: энергия ударной волны E = m_нак·Q_н·f_blast, Дж, для {@code DamageRequest(BLAST)}. */
    default void furnaceExplosion(double energyJ) {}
}
