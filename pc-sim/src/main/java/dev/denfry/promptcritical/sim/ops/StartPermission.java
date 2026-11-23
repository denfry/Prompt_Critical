package dev.denfry.promptcritical.sim.ops;

import java.util.Set;

/**
 * Разрешение пуска (розжиг топки, пуск привода) по режиму {@code ops.commissioning} (13 §13.6.6).
 *
 * @param accepted установка принята (лампа «НЕ ПРИНЯТА» погашена)
 * @param missing  непройденные обязательные проверки
 * @param unlicensedEra профиль licensing: эпоха, лицензии на которую у команды-владельца нет; иначе {@code null}
 */
public record StartPermission(boolean allowed, boolean accepted, Set<CommissioningCheck> missing, PlantEra unlicensedEra) {
    public static final StartPermission FREE = new StartPermission(true, true, Set.of());

    public StartPermission {
        missing = Set.copyOf(missing);
    }

    public StartPermission(boolean allowed, boolean accepted, Set<CommissioningCheck> missing) {
        this(allowed, accepted, missing, null);
    }
}
