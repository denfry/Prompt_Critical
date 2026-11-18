package dev.denfry.promptcritical.sim.mechanics;

/**
 * Тепловой прогиб ротора турбины после останова (03a §3a.7.6): горячий неподвижный ротор остывает неравномерно —
 * верх теплее низа — и выгибается. Если ротор не проворачивать 2–5 об/мин валоповоротным устройством, за
 * {@value #BOW_TIME_S} с физического времени (30 мин, SLOW-учёт) прогиб доходит до полного; пуск с прогибом — вибрация
 * ×(1 + {@value #VIBRATION_GAIN}·bow) (04a) и затяжной прогрев.
 *
 * <ul>
 *   <li>Прогиб растёт линейно, пока ротор горячий, стоит и не проворачивается.</li>
 *   <li>Ротор горячий {@value #HOT_TIME_S} с после останова [BALANCE: остывание корпуса турбины — часы; здесь —
 *   столько, сколько длится опасный период валоповорота].</li>
 *   <li>Валоповорот выпрямляет ротор с постоянной времени {@value #STRAIGHTEN_TAU_S} с [BALANCE]; вращение под нагрузкой
 *   — так же (прогрев на малых оборотах).</li>
 * </ul>
 */
public final class RotorBow {
    public static final double BOW_TIME_S = 1800, HOT_TIME_S = 4 * 3600, STRAIGHTEN_TAU_S = 600, VIBRATION_GAIN = 10;
    /** Ротору достаточно 2–5 об/мин; редуктор валоповорота даёт 3 об/мин от ≥ 16 об/мин вала Create (03a §3a.7.6). */
    public static final double MIN_INPUT_RPM = 16, ROTOR_RPM = 3;

    private double bow, sinceStopS = Double.POSITIVE_INFINITY;

    public RotorBow() {
    }

    public RotorBow(double bow, double sinceStopS) {
        this.bow = Math.clamp(bow, 0, 1);
        this.sinceStopS = sinceStopS;
    }

    public double bow() {
        return bow;
    }

    public double sinceStopS() {
        return sinceStopS;
    }

    /** Ротор ещё горячий после останова. */
    public boolean hot() {
        return sinceStopS < HOT_TIME_S;
    }

    /**
     * Шаг {@code dtS} с физического времени: турбина работает ({@code running}) или стоит; валоповорот вращает ротор
     * ({@code turning}).
     */
    public void step(double dtS, boolean running, boolean turning) {
        if (running) {
            sinceStopS = 0;
            bow *= Math.exp(-dtS / STRAIGHTEN_TAU_S);
            return;
        }
        sinceStopS += dtS;
        if (turning) bow *= Math.exp(-dtS / STRAIGHTEN_TAU_S);
        else if (hot()) bow = Math.min(1, bow + dtS / BOW_TIME_S);
    }

    /** Множитель вибрации при пуске с прогибом. */
    public double vibrationFactor() {
        return 1 + VIBRATION_GAIN * bow;
    }
}
