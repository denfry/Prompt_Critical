package dev.denfry.promptcritical.sim.mining;

/** Состояние шахтного подъёма (08 §8.2.8). */
public enum HoistState {
    /** Стоит на тормозе. */
    HELD,
    /** Движется. */
    RUNNING,
    /** Аппаратная защита: превышение скорости &gt; 15 %. */
    TRIP_OVERSPEED,
    /** Аппаратная защита: переподъём (или переспуск). */
    TRIP_OVERWIND,
    /** Тормоз по потере питания. */
    TRIP_POWER_LOSS,
    /** Канат оборван, клеть села на парашюты. */
    ROPE_BROKEN_CAUGHT,
    /** Канат оборван, ловителей нет — клеть упала ({@code DamageBudget}). */
    ROPE_BROKEN_FELL;

    /** Аварийная остановка защитой (сбрасывается {@link Hoist#reset}). */
    public boolean tripped() {
        return this == TRIP_OVERSPEED || this == TRIP_OVERWIND || this == TRIP_POWER_LOSS;
    }

    /** Канат оборван — подъём выведен из работы. */
    public boolean broken() {
        return this == ROPE_BROKEN_CAUGHT || this == ROPE_BROKEN_FELL;
    }
}
