package dev.denfry.promptcritical.sim.utilities;

/**
 * Состояние загрузки ионообменной колонны (08 §8.12, компонент {@code resin_state}).
 *
 * @param type         смола
 * @param capacityEqM3 рабочая обменная ёмкость свежей смолы q_раб, моль-экв/м³ (1,0 экв/л = 1000)
 * @param loadingFrac  поглощено с последней регенерации в долях рабочей ёмкости; 1 — проскок, дальше до
 *                     {@link WaterTreatmentTrain#EXHAUSTED_LOADING} смола работает «на проскоке»
 * @param foulingFrac  необратимая потеря ёмкости (хлор, железо, органика), доля
 * @param volumeM3     объём смолы V_см, м³
 */
public record ResinState(ResinType type, double capacityEqM3, double loadingFrac, double foulingFrac, double volumeM3) {
    public ResinState {
        if (type == null) throw new IllegalArgumentException("type");
        if (!(capacityEqM3 > 0 && volumeM3 > 0)) throw new IllegalArgumentException("capacity and volume must be > 0");
        if (!(loadingFrac >= 0 && foulingFrac >= 0 && foulingFrac < 1))
            throw new IllegalArgumentException("loading/fouling");
    }

    /** Рабочая ёмкость колонны с учётом старения, моль-экв. */
    public double workingCapacityEq() {
        return capacityEqM3 * volumeM3 * (1 - foulingFrac);
    }

    /** Колонна прошла рабочую ёмкость: жёсткость (и Na⁺ у H-формы) проскакивает в фильтрат. */
    public boolean exhausted() {
        return loadingFrac >= 1;
    }
}
