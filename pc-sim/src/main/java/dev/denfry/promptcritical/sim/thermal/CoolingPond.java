package dev.denfry.promptcritical.sim.thermal;

/**
 * Водоём, принимающий сбросное тепло конденсаторов (07 §7.13.4). Стоячая вода отдаёт тепло поверхностью:
 * Q = K·F·(T_пов − T_равн), K = 30 Вт/(м²·К) (испарение, конвекция и излучение; середина 25–40). В установившемся
 * режиме перегрев поверхности ΔT = Q/(K·F): пруд 0,42 км² при 100 МВт греется на 8 К. Река уносит тепло расходом:
 * ΔT = Q/(ρ·c·Q_реки) после полного смешения; расход по умолчанию 15 м³/с — сечение 30 м² при 0,5 м/с, как в примере
 * раздела [оценка]. Океан бесконечен: перегрева нет.
 */
public final class CoolingPond {
    public static final double K_W_M2K = 30, RIVER_FLOW_M3S = 15, WATER_RHO = 1000, WATER_CP = 4186;

    private CoolingPond() {
    }

    /** Перегрев зеркала пруда площадью {@code areaM2} при сбросе {@code heatW}, К. */
    public static double pondRiseK(double heatW, double areaM2) {
        return areaM2 <= 0 ? 0 : Math.max(0, heatW) / (K_W_M2K * areaM2);
    }

    /** Подогрев реки после полного смешения, К. */
    public static double riverRiseK(double heatW, double flowM3s) {
        return flowM3s <= 0 ? 0 : Math.max(0, heatW) / (WATER_RHO * WATER_CP * flowM3s);
    }
}
