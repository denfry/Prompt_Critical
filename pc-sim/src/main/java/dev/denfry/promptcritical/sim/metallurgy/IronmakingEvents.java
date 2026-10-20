package dev.denfry.promptcritical.sim.metallurgy;

/** События коксохима и домны для мира (журнал, SIS, звук, {@code DamageBudget}); все методы — без действия. */
public interface IronmakingEvents {
    IronmakingEvents NONE = new IronmakingEvents() {};

    /** Выталкивание раньше срока: поршень перегружен ({@code COKE_PUSH_OVERLOAD}), камера — {@code STICKER}. */
    default void cokePushOverload(int oven) {}

    /** Кладка батареи разрушена ({@code REFRACTORY_DAMAGE}). */
    default void refractoryFailed() {}

    default void furnaceStateChanged(BlastFurnaceState from, BlastFurnaceState to) {}

    /** Осадка подвисшей шихты: скачок давления, выброс через свечи; {@code severity} 0…1. */
    default void slip(double severity) {}

    /** Шлак дошёл до фурм (пропущен выпуск): фурма прогорела, вода фурмы течёт в горн. */
    default void tuyereBurnt(int burntTotal) {}

    /** Прорыв горна: жидкое вышло наружу (MELT_THROUGH, 04d). */
    default void burnthrough(double ironKg, double slagKg) {}
}
