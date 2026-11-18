package dev.denfry.promptcritical.sim.thermal;

/**
 * Водозабор {@code water_intake} (07 §7.13.4): береговое сооружение с решёткой в водоёме — граничный узел техводы.
 *
 * <ul>
 *   <li>Допустимый забор по водоёму: океан (связной воды больше {@value #OCEAN_BLOCKS} блоков или биом океана) —
 *   ограничен только насосами; река — не больше {@value #RIVER_SHARE} расхода реки, расход = сечение у водозабора ×
 *   {@value #RIVER_VELOCITY_MS} м/с ({@code cooling.river_velocity_ms}); озеро, пруд — по объёму воды, без перетока:
 *   забор не больше {@value #LAKE_TURNOVER_PER_DAY} объёма в сутки [BALANCE: пополнение стоячей воды].</li>
 *   <li>Решётка засоряется (07 §7.13.5: «решётка водозабора не чищена — засор, падение расхода насоса»): доля засора
 *   c(t) = 1 − e^{−t/θ}, θ = θ₀·2^{−(T_w − 25)/10}, θ₀ = {@value #SCREEN_THETA0_S} с (10 физ. суток, как у
 *   обрастания Керна–Ситона §7.13.5 [BALANCE для решётки]); пропуск решётки ×(1 − {@value #CLOG_LOSS}·c). Очистка
 *   граблями сбрасывает засор.</li>
 * </ul>
 */
public final class WaterIntake {
    public static final int OCEAN_BLOCKS = 4096;
    public static final double RIVER_VELOCITY_MS = 0.5, RIVER_SHARE = 0.5, LAKE_TURNOVER_PER_DAY = 0.1;
    public static final double SCREEN_THETA0_S = 10 * 86400, CLOG_LOSS = 0.9;
    /** Пропуск чистой решётки, м³/с [BALANCE: решётка одного блока водозабора, ≈ 1 м² при 0,5 м/с]. */
    public static final double SCREEN_M3S = 0.5;

    public enum Body { OCEAN, RIVER, LAKE }

    private double clog;

    public WaterIntake(double clog) {
        this.clog = Math.clamp(clog, 0, 1);
    }

    public double clog() {
        return clog;
    }

    /** Постоянная засорения при температуре воды {@code waterC}, с. */
    public static double theta(double waterC) {
        return SCREEN_THETA0_S * Math.pow(2, -(waterC - 25) / 10);
    }

    /** {@code dtS} с физического времени работы водозабора (засор растёт, только когда идёт вода). */
    public void step(double dtS, double waterC, boolean flowing) {
        if (!flowing) return;
        clog = 1 - (1 - clog) * Math.exp(-dtS / theta(waterC));
    }

    /** Очистка решётки. */
    public void clean() {
        clog = 0;
    }

    /** Предел забора по водоёму, м³/с. */
    public static double bodyLimitM3s(Body body, double riverSectionM2, double lakeVolumeM3) {
        return switch (body) {
            case OCEAN -> Double.POSITIVE_INFINITY;
            case RIVER -> RIVER_SHARE * riverSectionM2 * RIVER_VELOCITY_MS;
            case LAKE -> LAKE_TURNOVER_PER_DAY * lakeVolumeM3 / 86400;
        };
    }

    /** Допустимый забор, м³/с: решётка с засором и водоём. */
    public double capacityM3s(Body body, double riverSectionM2, double lakeVolumeM3) {
        return Math.min(SCREEN_M3S * (1 - CLOG_LOSS * clog), bodyLimitM3s(body, riverSectionM2, lakeVolumeM3));
    }
}
