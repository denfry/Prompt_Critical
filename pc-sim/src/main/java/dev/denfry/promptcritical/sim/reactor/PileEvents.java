package dev.denfry.promptcritical.sim.reactor;

/** События графитового котла для мира (журнал, достижения, маршрут, дым и огонь); все методы — без действия. */
public interface PileEvents {
    PileEvents NONE = new PileEvents() {};

    /** Сработала аварийная защита: аварийные стержни падают, регулирующие идут вниз. */
    default void tripped(PileTrip reason, double powerW, double periodS) {}

    /** Котёл впервые за пуск держит цепную реакцию без источника: ρ ≥ 0 дольше {@link GraphitePile#CRITICAL_HOLD_S}. */
    default void critical(double powerW) {}

    /** Сразу после {@link #critical}: сколько разных загрузок было записано по 1/M до неё (достижение {@code one_over_m}). */
    default void approachPoints(int points) {}

    /** Оболочки блочков разрушены перегревом. */
    default void cladFailed(double fuelTempK) {}

    /** Графит загорелся на воздухе. */
    default void graphiteFire(double graphiteTempK) {}
}
