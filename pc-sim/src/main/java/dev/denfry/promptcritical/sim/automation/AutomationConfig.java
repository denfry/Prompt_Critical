package dev.denfry.promptcritical.sim.automation;

/**
 * Ключи {@code relays.*}, {@code analog.*} и {@code cabinet.*} автоматики A1–A2 (03d §3d.17).
 *
 * @param operateTicks            {@code relays.operate_ticks} [BALANCE] — t_op = t_rel реле, тиков
 * @param pickupPu                {@code relays.pickup_pu} [сверить] — U_pu/U_n катушки
 * @param dropoutPu               {@code relays.dropout_pu} [сверить] — U_do/U_n катушки
 * @param baseFailureRatePerDay   {@code relays.base_failure_rate_per_day} [BALANCE] — λ₀ реле, 1/сут физики
 * @param electricalLifeOps       {@code relays.electrical_life_ops} [сверить] — N_life коммутаций под нагрузкой
 * @param bouncePulsesMean        {@code relays.bounce_pulses_mean} [BALANCE] — k̄_b дребезга на быстром входе
 * @param stepperMaxRateHz        {@code relays.stepper_max_rate_hz} [BALANCE] — предел шагов искателя
 * @param stepperLifeSteps        {@code relays.stepper_life_steps} — ресурс искателя, шагов
 * @param maxRelaysPerStation     {@code relays.max_per_station} [BALANCE]
 * @param driftRmsTubeV           {@code analog.drift_rms_tube_v} [BALANCE] — δ_ε лампового интегратора, В
 * @param driftRmsTransistorV     {@code analog.drift_rms_transistor_v} [BALANCE]
 * @param adjustIntervalDaysTube  {@code analog.adjust_interval_days_tube} [BALANCE] — T_adj, сут физики
 * @param adjustIntervalDaysTransistor {@code analog.adjust_interval_days_transistor} [BALANCE]
 * @param leakTimeTubeS           {@code analog.leak_time_tube_s} [BALANCE] — T_leak = R_ins·C, с
 * @param leakTimeTransistorS     {@code analog.leak_time_transistor_s} [BALANCE]
 * @param tubeLifeHours           {@code analog.tube_life_hours} [BALANCE] — средний ресурс лампы, ч физики
 * @param maxAnalogBlocksPerStation {@code analog.max_blocks_per_station} [BALANCE]
 * @param arrheniusStepK          {@code cabinet.arrhenius_step_k} [BALANCE] — шаг правила «10 градусов»
 */
public record AutomationConfig(int operateTicks, double pickupPu, double dropoutPu, double baseFailureRatePerDay,
        double electricalLifeOps, double bouncePulsesMean, double stepperMaxRateHz, double stepperLifeSteps,
        int maxRelaysPerStation, double driftRmsTubeV, double driftRmsTransistorV, double adjustIntervalDaysTube,
        double adjustIntervalDaysTransistor, double leakTimeTubeS, double leakTimeTransistorS, double tubeLifeHours,
        int maxAnalogBlocksPerStation, double arrheniusStepK) {
    /** Механический ресурс реле без нагрузки контактов (03d §3d.3.3); в λ берётся электрический. */
    public static final double MECHANICAL_LIFE_OPS = 1e7;

    public AutomationConfig {
        if (operateTicks < 1 || operateTicks > 4) throw new IllegalArgumentException("operateTicks");
        if (!(dropoutPu > 0 && pickupPu > dropoutPu && pickupPu <= 1)) throw new IllegalArgumentException("coil thresholds");
        if (!(baseFailureRatePerDay >= 0 && baseFailureRatePerDay <= 1)) throw new IllegalArgumentException("baseFailureRatePerDay");
        if (!(electricalLifeOps >= 1e3 && electricalLifeOps <= 1e8)) throw new IllegalArgumentException("electricalLifeOps");
        if (!(bouncePulsesMean >= 0 && bouncePulsesMean <= 10)) throw new IllegalArgumentException("bouncePulsesMean");
        if (!(stepperMaxRateHz >= 1 && stepperMaxRateHz <= 20)) throw new IllegalArgumentException("stepperMaxRateHz");
        if (!(stepperLifeSteps > 0)) throw new IllegalArgumentException("stepperLifeSteps");
        if (maxRelaysPerStation < 0 || maxRelaysPerStation > 4096) throw new IllegalArgumentException("maxRelaysPerStation");
        if (!(driftRmsTubeV >= 0 && driftRmsTransistorV >= 0)) throw new IllegalArgumentException("drift");
        if (!(adjustIntervalDaysTube > 0 && adjustIntervalDaysTransistor > 0)) throw new IllegalArgumentException("adjustInterval");
        if (!(leakTimeTubeS > 0 && leakTimeTransistorS > 0)) throw new IllegalArgumentException("leakTime");
        if (!(tubeLifeHours > 0)) throw new IllegalArgumentException("tubeLifeHours");
        if (maxAnalogBlocksPerStation < 0 || maxAnalogBlocksPerStation > 4096)
            throw new IllegalArgumentException("maxAnalogBlocksPerStation");
        if (!(arrheniusStepK > 0)) throw new IllegalArgumentException("arrheniusStepK");
    }

    public static AutomationConfig defaults() {
        return new AutomationConfig(1, 0.75, 0.30, 0.0005, 1e5, 1.5, 10, 1e6, 256, 0.010, 0.002, 7, 30, 2e4, 2e5,
                5000, 128, 10);
    }

    public AutomationConfig withBouncePulsesMean(double mean) {
        return new AutomationConfig(operateTicks, pickupPu, dropoutPu, baseFailureRatePerDay, electricalLifeOps, mean,
                stepperMaxRateHz, stepperLifeSteps, maxRelaysPerStation, driftRmsTubeV, driftRmsTransistorV,
                adjustIntervalDaysTube, adjustIntervalDaysTransistor, leakTimeTubeS, leakTimeTransistorS,
                tubeLifeHours, maxAnalogBlocksPerStation, arrheniusStepK);
    }

    public AutomationConfig withBaseFailureRatePerDay(double perDay) {
        return new AutomationConfig(operateTicks, pickupPu, dropoutPu, perDay, electricalLifeOps, bouncePulsesMean,
                stepperMaxRateHz, stepperLifeSteps, maxRelaysPerStation, driftRmsTubeV, driftRmsTransistorV,
                adjustIntervalDaysTube, adjustIntervalDaysTransistor, leakTimeTubeS, leakTimeTransistorS,
                tubeLifeHours, maxAnalogBlocksPerStation, arrheniusStepK);
    }

    public AutomationConfig withStepperMaxRateHz(double hz) {
        return new AutomationConfig(operateTicks, pickupPu, dropoutPu, baseFailureRatePerDay, electricalLifeOps,
                bouncePulsesMean, hz, stepperLifeSteps, maxRelaysPerStation, driftRmsTubeV, driftRmsTransistorV,
                adjustIntervalDaysTube, adjustIntervalDaysTransistor, leakTimeTubeS, leakTimeTransistorS,
                tubeLifeHours, maxAnalogBlocksPerStation, arrheniusStepK);
    }

    public AutomationConfig withStationLimits(int relays, int analogBlocks) {
        return new AutomationConfig(operateTicks, pickupPu, dropoutPu, baseFailureRatePerDay, electricalLifeOps,
                bouncePulsesMean, stepperMaxRateHz, stepperLifeSteps, relays, driftRmsTubeV, driftRmsTransistorV,
                adjustIntervalDaysTube, adjustIntervalDaysTransistor, leakTimeTubeS, leakTimeTransistorS,
                tubeLifeHours, analogBlocks, arrheniusStepK);
    }

    /** Ускорение отказов электроники f_T = max(1, 2^((T − 313 K)/шаг)) (03d §3d.13.1). */
    public double arrheniusFactor(double cabinetTempK) {
        return Math.max(1, Math.pow(2, (cabinetTempK - 313.15) / arrheniusStepK));
    }
}
