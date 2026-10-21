package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import java.util.Set;

/** События эксплуатации для мира (журнал, щит A0, CC-события 13 §13.6.5–13.6.6); без действия. */
public interface OpsEvents {
    OpsEvents NONE = new OpsEvents() {};

    /** {@code pc_maintenance_required}: рост видимой тяжести или первое обнаружение замером. */
    default void maintenanceRequired(SimId unit, MaintenanceFaultKind kind, int severity, int prev) {}

    default void commissioningStateChanged(SimId unit, CommissioningState from, CommissioningState to) {}

    /** Проверка нашла монтажный дефект. */
    default void defectFound(SimId unit, CommissioningDefect defect) {}

    /** Пуск непринятой установки в {@code ADVISORY}: запись {@code START_UNACCEPTED}. */
    default void startUnaccepted(SimId unit) {}

    /** Пуск отклонён в {@code ENFORCED}: перечень непройденных проверок. */
    default void startRefused(SimId unit, Set<CommissioningCheck> missing) {}
}
