package dev.denfry.promptcritical.sim.ops;

import java.util.UUID;

/**
 * Замер газоанализатором перед работами (13 §13.2.3, §13.12.1): доли и концентрации в точке объёма работ.
 *
 * @param tick        тик замера
 * @param by          кто мерил
 * @param instrument  прибор (идентификатор предмета или стационарного датчика)
 * @param bumpTested  прибор прошёл проверку газом не позже {@code ops.bump_test_max_age_s}
 * @param o2          объёмная доля кислорода
 * @param lelFraction горючие, доля НКПР (1 = 100 % НКПР)
 * @param h2sPpm      сероводород, ppm
 * @param coPpm       монооксид углерода, ppm
 */
public record GasTest(long tick, UUID by, String instrument, boolean bumpTested, double o2, double lelFraction,
        double h2sPpm, double coPpm) {
    /** Чистый воздух: 20,95 % O₂, без горючих и токсичных. */
    public static GasTest air(long tick, UUID by, String instrument, boolean bumpTested) {
        return new GasTest(tick, by, instrument, bumpTested, 0.2095, 0, 0, 0);
    }
}
