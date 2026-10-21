package dev.denfry.promptcritical.sim.ops;

/**
 * Роль компонента в установке I0 (13 §13.6.6). Замена компонента сбрасывает только проверку, которая его
 * покрывает (MOC → {@code MODIFIED}): замена ПК — только {@code RELIEF_VALVE_SET}.
 */
public enum UnitComponent {
    /** Обечайка котла. */
    SHELL(CommissioningCheck.PRESSURE_TEST),
    RELIEF_VALVE(CommissioningCheck.RELIEF_VALVE_SET),
    PRESSURE_GAUGE(CommissioningCheck.INSTRUMENT_CHECK),
    GAUGE_GLASS(CommissioningCheck.INSTRUMENT_CHECK),
    /** Литой цилиндр паровой машины. */
    CYLINDER(CommissioningCheck.PRESSURE_TEST),
    STOP_VALVE(CommissioningCheck.PRESSURE_TEST),
    /** Центробежный регулятор с катарактом. */
    GOVERNOR(CommissioningCheck.TRIAL_RUN),
    /** Привод от вала Create (вентилятор, насос). */
    DRIVE(CommissioningCheck.ROTATION_CHECK),
    /** Корпус и колесо насоса. */
    PUMP(CommissioningCheck.PRIMING),
    /** Тормоз подъёмной машины. */
    BRAKE(CommissioningCheck.BRAKE_TEST);

    private final CommissioningCheck coveredBy;

    UnitComponent(CommissioningCheck coveredBy) {
        this.coveredBy = coveredBy;
    }

    public CommissioningCheck coveredBy() {
        return coveredBy;
    }
}
