package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;

/**
 * Запись журнала эксплуатации установки (13 §13.6.5–13.6.6) для щита и кнопки «Журнал» {@code maintenance_panel}; в
 * общий журнал мира те же записи уходят событиями {@code Journal}.
 *
 * @param detail вид неисправности, дефекта, проверки или состояния ({@code name()} перечисления), список проверок
 */
public record OpsLogEntry(long tick, SimId unit, Kind kind, String detail) {
    public enum Kind {
        START_UNACCEPTED, START_REFUSED, STATE, DEFECT_FOUND, CHECK_PASSED, CHECK_FAILED, MAINTENANCE_REQUIRED,
        MAINTENANCE_DONE, UNSAFE_MAINTENANCE, SEIZED,
        /** LOTO 13 §13.1.7: замок повешен, снят владельцем, срезан администратором, попытка сломать запертую точку. */
        LOCKOUT_ON, LOCKOUT_OFF, LOCKOUT_ADMIN_CUT, UNAUTHORIZED_ACCESS,
        /** Наряды 13 §13.2, §13.12.3: смена состояния наряда, замер газа, огневые работы без наряда, фаза остановочного ремонта. */
        PERMIT_STATE, GAS_TEST, UNAUTHORIZED_WORK, TURNAROUND_PHASE
    }
}
