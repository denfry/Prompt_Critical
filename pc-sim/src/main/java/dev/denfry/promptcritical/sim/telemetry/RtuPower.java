package dev.denfry.promptcritical.sim.telemetry;

/**
 * Питание контролируемого пункта телемеханики на удалённом объекте (12 §12.5.1): КП без сети питается от РИТЭГа
 * вплотную. Потребление — экономичный КП с радиомодемом; когда РИТЭГ не покрывает его (источник выгорел, вынут,
 * перегружен), КП гаснет и перестаёт опрашивать щиты, а включается снова, только когда питания снова хватает целиком
 * (гистерезис, без дребезга на границе).
 *
 * <p>«Вечная батарейка» (11 §11.2.2): КП на РИТЭГе без перерыва опрашивает скважину {@link #EVERLASTING_S} секунд.
 */
public final class RtuPower {
    /**
     * Потребление КП, Вт: экономичный контроллер телемеханики с радиомодемом в режиме опроса — единицы ватт (как у
     * КП на солнечных панелях и батареях). Один «Бета-М» (~10 Вт) тянет его и ещё светильник-указатель.
     */
    public static final double DEMAND_W = 4;
    /** Ниже этой доли потребления КП гаснет. */
    public static final double BROWNOUT_PU = 0.9;
    /** Непрерывная работа на РИТЭГе с опросом скважины до «Вечной батарейки», с. */
    public static final double EVERLASTING_S = 60;

    private boolean on = true;
    private double autonomousS;
    private boolean everlasting;

    /**
     * Секунда работы КП.
     *
     * @param dtS       шаг, с
     * @param offGrid   КП питается от РИТЭГа (иначе — от сети объекта, всегда в норме)
     * @param supplyPu  доля потребления, которую покрывает РИТЭГ
     * @param pollsWell среди опрашиваемых щитов есть скважина
     * @return работает ли КП
     */
    public boolean step(double dtS, boolean offGrid, double supplyPu, boolean pollsWell) {
        if (!offGrid) {
            on = true;
            autonomousS = 0;
            return true;
        }
        if (on && supplyPu < BROWNOUT_PU) on = false;
        else if (!on && supplyPu >= 1) on = true;
        if (on && pollsWell) {
            autonomousS += dtS;
            if (autonomousS >= EVERLASTING_S) everlasting = true;
        } else {
            autonomousS = 0;
        }
        return on;
    }

    public boolean on() {
        return on;
    }

    /** Секунды непрерывной работы на РИТЭГе с опросом скважины. */
    public double autonomousS() {
        return autonomousS;
    }

    /** Достигнута «Вечная батарейка». */
    public boolean everlasting() {
        return everlasting;
    }

    /** Состояние из сохранения блока. */
    public void restore(boolean on, double autonomousS, boolean everlasting) {
        this.on = on;
        this.autonomousS = Math.max(0, autonomousS);
        this.everlasting = everlasting;
    }
}
