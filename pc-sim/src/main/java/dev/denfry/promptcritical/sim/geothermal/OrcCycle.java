package dev.denfry.promptcritical.sim.geothermal;

import dev.denfry.promptcritical.sim.gasprocessing.GasComponent;

/**
 * Бинарный ORC-цикл на изобутане (15 §15.6.3) для пластов ниже ≈ 150 °C. Рассол не вскипает и отдаёт тепло в
 * испарителе {@code orc_evaporator}; рабочее тело — изобутан ({@link GasComponent#IC4}).
 *
 * <ul>
 *   <li>Термический КПД бинарной станции по температуре рассола на входе — корреляция DiPippo (2004),
 *       «Second Law assessment of binary plants generating power from low-temperature geothermal fluids»,
 *       Geothermics 33: {@code η = 0,0935·T[°C] − 2,3266} (%), по данным действующих станций.</li>
 *   <li>Давление испарения изобутана — корреляция Ли — Кеслера по критическим параметрам и ацентрическому фактору
 *       (Poling, Prausnitz, O'Connell, «The Properties of Gases and Liquids», 5-е изд., ур. 7-4.1): контур под
 *       давлением, утечка — горючий газ ({@code FLAMMABLE}, 09).</li>
 *   <li>Испаритель — с пинчем {@value #PINCH_K} К: изобутан кипит на {@value #PINCH_K} К ниже температуры
 *       рассола на выходе.</li>
 * </ul>
 */
public final class OrcCycle {
    public static final double PINCH_K = 5;
    /** Теплоёмкость рассола, Дж/(кг·К). */
    public static final double BRINE_CP = 4200;
    /** Ниже этой температуры рассола на входе корреляция даёт ноль. */
    public static final double MIN_INLET_C = 2.3266 / 0.0935;

    private OrcCycle() {}

    /** Термический КПД по температуре рассола на входе, доли. */
    public static double thermalEfficiency(double brineInK) {
        double c = brineInK - 273.15;
        return Math.max(0, (0.0935 * c - 2.3266) / 100);
    }

    /** Тепло, отданное рассолом в испарителе, Вт. */
    public static double heatW(double brineKgS, double brineInK, double brineOutK) {
        return Math.max(0, brineKgS) * BRINE_CP * Math.max(0, brineInK - brineOutK);
    }

    /** Электрическая мощность бинарного блока нетто, Вт. */
    public static double netPowerW(double brineKgS, double brineInK, double brineOutK) {
        return thermalEfficiency(brineInK) * heatW(brineKgS, brineInK, brineOutK);
    }

    /** Температура кипения изобутана в испарителе, К. */
    public static double evaporationK(double brineOutK) {
        return brineOutK - PINCH_K;
    }

    /** Давление насыщенного изобутана, Па (Ли — Кеслер); выше критической температуры — критическое давление. */
    public static double isobutaneSaturationPa(double tK) {
        GasComponent c = GasComponent.IC4;
        double tr = Math.min(1, tK / c.tcK);
        double f0 = 5.92714 - 6.09648 / tr - 1.28862 * Math.log(tr) + 0.169347 * Math.pow(tr, 6);
        double f1 = 15.2518 - 15.6875 / tr - 13.4721 * Math.log(tr) + 0.43577 * Math.pow(tr, 6);
        return c.pcPa * Math.exp(f0 + c.omega * f1);
    }
}
