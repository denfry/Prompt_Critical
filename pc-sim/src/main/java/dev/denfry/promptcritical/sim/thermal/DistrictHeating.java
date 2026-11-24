package dev.denfry.promptcritical.sim.thermal;

import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * ТЭЦ и теплосеть (07 §7.14.2). Турбина с противодавлением отдаёт отработавший пар сетевому подогревателю, а не
 * конденсатору: пар конденсируется при температуре подачи сети плюс недогрев {@value #HEATER_TTD_K} К, поэтому давление
 * за турбиной выше, чем в конденсаторе, и электричества меньше — зато теплота конденсации идёт потребителям.
 *
 * <p>Подача по отопительному графику 150/70: при расчётной наружной −25 °C — 150 °C, обратка 70 °C; с потеплением
 * подача падает линейно до 70 °C при +8 °C (конец отопительного сезона); летом сеть держит 70/40 °C для горячей воды.
 * Нагрузка отопления поселения Q = q₀·V·(T_вн − T_a), q₀ = 0,4 Вт/(м³·К), T_вн = 18 °C, только пока T_a ниже +8 °C.
 */
public final class DistrictHeating {
    public static final double Q0_W_M3K = 0.4, INDOOR_C = 18, SEASON_END_C = 8, DESIGN_OUTDOOR_C = -25;
    public static final double SUPPLY_MAX_C = 150, SUPPLY_MIN_C = 70, RETURN_DESIGN_C = 70, RETURN_SUMMER_C = 40;
    public static final double HEATER_TTD_K = 5;
    /** Теплота конденсации пара при 100–150 °C, Дж/кг (2,26–2,11 МДж/кг; берём середину). */
    public static final double LATENT_J_KG = 2.18e6;

    private DistrictHeating() {
    }

    /** Температура подачи по отопительному графику, °C. */
    public static double supplyC(double outdoorC) {
        if (outdoorC >= SEASON_END_C) return SUPPLY_MIN_C;
        double x = (SEASON_END_C - outdoorC) / (SEASON_END_C - DESIGN_OUTDOOR_C);
        return Math.min(SUPPLY_MAX_C, SUPPLY_MIN_C + x * (SUPPLY_MAX_C - SUPPLY_MIN_C));
    }

    /** Температура обратки, °C: 70 в отопительный сезон, 40 летом. */
    public static double returnC(double outdoorC) {
        return outdoorC >= SEASON_END_C ? RETURN_SUMMER_C : RETURN_DESIGN_C;
    }

    /** Давление за турбиной на сетевой подогреватель, Па: насыщение при подаче плюс недогрев. */
    public static double exhaustPa(WaterProperties w, double outdoorC) {
        return w.saturationPressure(supplyC(outdoorC) + HEATER_TTD_K + 273.15);
    }

    /** Нагрузка отопления зданий объёмом {@code buildingM3}, Вт; 0 вне отопительного сезона. */
    public static double heatingLoadW(double buildingM3, double outdoorC) {
        if (outdoorC >= SEASON_END_C || buildingM3 <= 0) return 0;
        return Q0_W_M3K * buildingM3 * (INDOOR_C - outdoorC);
    }

    /** Тепло, которое отдаёт сети пар {@code steamKgS}, Вт. */
    public static double heatFromSteamW(double steamKgS) {
        return Math.max(0, steamKgS) * LATENT_J_KG;
    }

    /** Расход сетевой воды на {@code heatW}, кг/с, при перепаде подача − обратка. */
    public static double networkWaterKgS(double heatW, double outdoorC) {
        double dt = supplyC(outdoorC) - returnC(outdoorC);
        return dt <= 0 ? 0 : Math.max(0, heatW) / (4186 * dt);
    }
}
