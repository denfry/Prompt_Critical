package dev.denfry.promptcritical.sim.thermal;

import dev.denfry.promptcritical.sim.environment.Environment;

/**
 * Секция вентиляторной градирни: вода конденсатора остывает испарением в потоке воздуха. Предел — температура
 * мокрого термометра; на сколько выше неё выходит холодная вода — недоохлаждение (approach). В расчётной точке
 * (тепловая нагрузка {@link #DESIGN_HEAT_W}, вентилятор работает) — 5 K; с нагрузкой растёт примерно как Q^0,7
 * (больше тепла на тот же воздух), без вентилятора — втрое (естественная тяга слабая) [порядок, по каталогам
 * градирен]. Испаряется Q/r воды, r ≈ 2,4 МДж/кг. В мороз работающая секция обмерзает.
 */
public final class CoolingTowerCell {
    public static final double DESIGN_HEAT_W = 5e6, DESIGN_APPROACH_K = 5, FAN_OFF_FACTOR = 3, EVAPORATION_J_KG = 2.4e6;
    public static final double ICING_AIR_C = -2;

    private CoolingTowerCell() {
    }

    /** Недоохлаждение до мокрого термометра, К. */
    public static double approachK(double heatW, boolean fan) {
        double q = Math.max(0, heatW) / DESIGN_HEAT_W;
        return DESIGN_APPROACH_K * Math.pow(Math.max(q, 0.05), 0.7) * (fan ? 1 : FAN_OFF_FACTOR);
    }

    /** Температура холодной воды с градирни, °C: мокрый термометр плюс недоохлаждение, не ниже 1 °C. */
    public static double coldWaterC(double airC, double rhPercent, double heatW, boolean fan) {
        return Math.max(1, Environment.wetBulbC(airC, rhPercent) + approachK(heatW, fan));
    }

    /** Унос воды испарением, кг/с — столько надо подпитывать. */
    public static double evaporationKgS(double heatW) {
        return Math.max(0, heatW) / EVAPORATION_J_KG;
    }

    /** Секция обмерзает: вентилятор гонит морозный воздух. */
    public static boolean icing(double airC, boolean fan) {
        return fan && airC < ICING_AIR_C;
    }
}
