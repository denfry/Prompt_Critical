package dev.denfry.promptcritical.sim.ops;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ключи эксплуатации (13 §13.11): {@code ops.commissioning*}, {@code ops.hydrotest_*}, {@code ops.recording_gauge.*},
 * {@code lube.tallow_interval_h}, {@code acoustics.bearing_wear_multiplier}, {@code maintenance.<kind>.eff/.trip}.
 *
 * @param commissioning         {@code ops.commissioning} — режим пусконаладки (ADVISORY)
 * @param defectRate            {@code ops.commissioning.defect_rate} — множитель вероятностей монтажных дефектов (1,0)
 * @param trialRunS             {@code ops.commissioning.trial_run_s} — пробный пуск, с реального времени (600)
 * @param trialMinLoad          {@code ops.commissioning.trial_min_load} — доля номинальной нагрузки (0,7)
 * @param hydrotestFactor       {@code ops.hydrotest_factor} — p_исп/p_раб (1,25)
 * @param hydrotestMarginPa     {@code ops.hydrotest_margin_pa} — минимальный запас p_исп − p_раб, Па (3·10⁵)
 * @param hydrotestHoldPhysS    {@code ops.hydrotest_hold_phys_s} — выдержка под пробным давлением, с физики (600)
 * @param recordingPeriodS      {@code ops.recording_gauge.period_s} — оборот барабана, с реального времени (3600)
 * @param recordingSampleS      {@code ops.recording_gauge.sample_s} — шаг записи, с (5)
 * @param tallowIntervalH       {@code lube.tallow_interval_h} — интервал доливки {@code tallow}, ч физики (500)
 * @param bearingWearMultiplier {@code acoustics.bearing_wear_multiplier} — k_wear (20)
 * @param maintenance           {@code maintenance.<kind>.eff/.trip}; вид без записи берёт свои умолчания
 */
public record OpsConfig(CommissioningMode commissioning, double defectRate, double trialRunS, double trialMinLoad,
        double hydrotestFactor, double hydrotestMarginPa, double hydrotestHoldPhysS, double recordingPeriodS,
        double recordingSampleS, double tallowIntervalH, double bearingWearMultiplier,
        Map<MaintenanceFaultKind, FaultCoefficients> maintenance) {
    public OpsConfig {
        if (commissioning == null) throw new IllegalArgumentException("commissioning");
        if (!(defectRate >= 0)) throw new IllegalArgumentException("defectRate");
        if (!(trialRunS > 0 && trialMinLoad >= 0 && trialMinLoad <= 1)) throw new IllegalArgumentException("trial run");
        if (!(hydrotestFactor >= 1 && hydrotestMarginPa >= 0 && hydrotestHoldPhysS >= 0))
            throw new IllegalArgumentException("hydrotest");
        if (!(recordingSampleS > 0 && recordingPeriodS >= recordingSampleS))
            throw new IllegalArgumentException("recording gauge");
        if (!(tallowIntervalH > 0 && bearingWearMultiplier > 0)) throw new IllegalArgumentException("wear");
        EnumMap<MaintenanceFaultKind, FaultCoefficients> m = new EnumMap<>(MaintenanceFaultKind.class);
        if (maintenance != null) m.putAll(maintenance);
        maintenance = Map.copyOf(m);
    }

    public static OpsConfig defaults() {
        return new OpsConfig(CommissioningMode.ADVISORY, 1.0, 600, 0.7, 1.25, 3e5, 600, 3600, 5, 500, 20, Map.of());
    }

    public OpsConfig withCommissioning(CommissioningMode mode) {
        return new OpsConfig(mode, defectRate, trialRunS, trialMinLoad, hydrotestFactor, hydrotestMarginPa,
                hydrotestHoldPhysS, recordingPeriodS, recordingSampleS, tallowIntervalH, bearingWearMultiplier,
                maintenance);
    }

    public OpsConfig withDefectRate(double rate) {
        return new OpsConfig(commissioning, rate, trialRunS, trialMinLoad, hydrotestFactor, hydrotestMarginPa,
                hydrotestHoldPhysS, recordingPeriodS, recordingSampleS, tallowIntervalH, bearingWearMultiplier,
                maintenance);
    }

    /** p_исп = max(k·p_раб, p_раб + запас), Па (13 §13.6.6). */
    public double testPressurePa(double workingPressurePa) {
        return Math.max(hydrotestFactor * workingPressurePa, workingPressurePa + hydrotestMarginPa);
    }

    public FaultCoefficients coefficients(MaintenanceFaultKind kind) {
        FaultCoefficients c = maintenance.get(kind);
        return c != null ? c : kind.defaultCoefficients();
    }

    /** Точек на ленте самописца (720). */
    public int recordingPoints() {
        return (int) Math.round(recordingPeriodS / recordingSampleS);
    }
}
