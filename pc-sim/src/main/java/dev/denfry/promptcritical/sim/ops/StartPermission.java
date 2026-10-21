package dev.denfry.promptcritical.sim.ops;

import java.util.Set;

/**
 * Разрешение пуска (розжиг топки, пуск привода) по режиму {@code ops.commissioning} (13 §13.6.6).
 *
 * @param accepted установка принята (лампа «НЕ ПРИНЯТА» погашена)
 * @param missing  непройденные обязательные проверки
 */
public record StartPermission(boolean allowed, boolean accepted, Set<CommissioningCheck> missing) {
    public static final StartPermission FREE = new StartPermission(true, true, Set.of());

    public StartPermission {
        missing = Set.copyOf(missing);
    }
}
