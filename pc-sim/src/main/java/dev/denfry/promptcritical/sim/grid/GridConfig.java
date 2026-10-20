package dev.denfry.promptcritical.sim.grid;

/**
 * Ключи {@code grid.*} электрической модели (07 §7.21).
 *
 * @param baseMva             {@code grid.base_mva} — базисная мощность S_b
 * @param nominalFrequencyHz  {@code grid.nominal_frequency_hz} — 50 или 60
 * @param substeps            {@code grid.substeps} — подшаги машин на тик
 * @param solverIterations    {@code grid.solver_iterations} — итераций сети на подшаг
 * @param solverRelaxation    {@code grid.solver_relaxation} — нижняя релаксация ϖ
 * @param maxRefactorPerTick  {@code grid.max_refactor_per_tick} — переразложений островов за тик
 * @param loadDamping         {@code grid.load_damping} [BALANCE] — D_L нагрузок без своего D_f
 * @param governorDeadbandHz  {@code grid.governor_deadband_hz} [сверить] — мёртвая зона регуляторов
 * @param syncMaxAngleDeg     {@code grid.sync_max_angle_deg} — окно реле 25 по углу (IEEE C50.13)
 * @param syncMaxDv           {@code grid.sync_max_dv} — окно по напряжению, генератор не ниже сети
 * @param syncMaxSlipHz       {@code grid.sync_max_slip_hz} — окно по скольжению
 * @param syncTorqueDamagePu  {@code grid.sync_torque_damage_pu} [BALANCE] — T_pk, с которого копится D_shaft
 * @param couplingShearPu     {@code grid.coupling_shear_pu} [BALANCE] — T_pk среза муфты
 * @param lofTripDelayS       {@code grid.lof_trip_delay_s} — выдержка защиты от потери возбуждения 40
 * @param arSuccessLightning  {@code grid.ar_success_lightning} [BALANCE] — доля самоустраняющихся КЗ от молнии
 * @param groundFaultEscalationPerS {@code grid.ground_fault_escalation_per_s} [BALANCE] — λ перехода
 *                            замыкания на землю в сети с изолированной нейтралью в двойное
 * @param overvoltageAgingExponent {@code grid.overvoltage_aging_exponent} [BALANCE] — n в скорости старения k^n
 * @param lvBreakerThermalKS  {@code grid.lv_breaker_thermal_k_s} [сверить] — k_т теплового расцепителя 0,4 кВ
 */
public record GridConfig(double baseMva, double nominalFrequencyHz, int substeps, int solverIterations,
        double solverRelaxation, int maxRefactorPerTick, double loadDamping, double governorDeadbandHz,
        double syncMaxAngleDeg, double syncMaxDv, double syncMaxSlipHz, double syncTorqueDamagePu,
        double couplingShearPu, double lofTripDelayS, double arSuccessLightning, double groundFaultEscalationPerS,
        double overvoltageAgingExponent, double lvBreakerThermalKS) {
    public GridConfig {
        if (!(baseMva > 0)) throw new IllegalArgumentException("baseMva");
        if (nominalFrequencyHz != 50 && nominalFrequencyHz != 60) throw new IllegalArgumentException("nominalFrequencyHz");
        if (substeps < 1) throw new IllegalArgumentException("substeps");
        if (solverIterations < 1) throw new IllegalArgumentException("solverIterations");
        if (!(solverRelaxation > 0 && solverRelaxation <= 1)) throw new IllegalArgumentException("solverRelaxation");
        if (maxRefactorPerTick < 1) throw new IllegalArgumentException("maxRefactorPerTick");
        if (!(loadDamping >= 0)) throw new IllegalArgumentException("loadDamping");
        if (!(governorDeadbandHz >= 0)) throw new IllegalArgumentException("governorDeadbandHz");
        if (!(syncMaxAngleDeg > 0 && syncMaxAngleDeg < 180)) throw new IllegalArgumentException("syncMaxAngleDeg");
        if (!(syncMaxDv > 0)) throw new IllegalArgumentException("syncMaxDv");
        if (!(syncMaxSlipHz > 0)) throw new IllegalArgumentException("syncMaxSlipHz");
        if (!(syncTorqueDamagePu > 0 && couplingShearPu > syncTorqueDamagePu))
            throw new IllegalArgumentException("sync torque thresholds");
        if (!(lofTripDelayS >= 0)) throw new IllegalArgumentException("lofTripDelayS");
        if (!(arSuccessLightning >= 0 && arSuccessLightning <= 1)) throw new IllegalArgumentException("arSuccessLightning");
        if (!(groundFaultEscalationPerS >= 0)) throw new IllegalArgumentException("groundFaultEscalationPerS");
        if (!(overvoltageAgingExponent > 0)) throw new IllegalArgumentException("overvoltageAgingExponent");
        if (!(lvBreakerThermalKS > 0)) throw new IllegalArgumentException("lvBreakerThermalKS");
    }

    public static GridConfig defaults() {
        return new GridConfig(100, 50, 2, 2, 0.8, 2, 1.5, 0.075, 10, 0.05, 0.067, 1.5, 5.0, 5, 0.8, 1e-3, 12, 40);
    }

    public GridConfig withGovernorDeadbandHz(double hz) {
        return new GridConfig(baseMva, nominalFrequencyHz, substeps, solverIterations, solverRelaxation,
                maxRefactorPerTick, loadDamping, hz, syncMaxAngleDeg, syncMaxDv, syncMaxSlipHz, syncTorqueDamagePu,
                couplingShearPu, lofTripDelayS, arSuccessLightning, groundFaultEscalationPerS,
                overvoltageAgingExponent, lvBreakerThermalKS);
    }

    public GridConfig withGroundFaultEscalationPerS(double perS) {
        return new GridConfig(baseMva, nominalFrequencyHz, substeps, solverIterations, solverRelaxation,
                maxRefactorPerTick, loadDamping, governorDeadbandHz, syncMaxAngleDeg, syncMaxDv, syncMaxSlipHz,
                syncTorqueDamagePu, couplingShearPu, lofTripDelayS, arSuccessLightning, perS,
                overvoltageAgingExponent, lvBreakerThermalKS);
    }

    /** S_b, В·А. */
    public double baseVa() {
        return baseMva * 1e6;
    }

    /** ω_s = 2πf₀, рад/с. */
    public double synchronousSpeed() {
        return 2 * Math.PI * nominalFrequencyHz;
    }
}
