package dev.denfry.promptcritical.sim.ops;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Результат проверки (13 §13.6.6).
 *
 * @param unmet    условия, из-за которых проверка не начата
 * @param found    дефекты, найденные этой проверкой (в том числе исправленные регулировкой)
 * @param readings показания: ключи {@code READ_*}
 */
public record CheckResult(CommissioningCheck check, Outcome outcome, Set<CheckCondition> unmet,
        List<CommissioningDefect> found, Map<String, Double> readings, long tick, UUID by) {
    public enum Outcome { PASSED, FAILED, REFUSED }

    public static final String READ_TEST_PRESSURE = "test_pressure_pa";
    public static final String READ_BURST = "burst";
    public static final String READ_LIFT = "lift_pa";
    public static final String READ_RESEAT = "reseat_pa";
    public static final String READ_GAUGE_OFFSET = "gauge_offset";
    public static final String READ_REVERSED = "reversed";
    public static final String READ_BRAKE_TORQUE = "brake_torque";
    public static final String READ_HUNTING = "hunting";

    public CheckResult {
        unmet = unmet.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(unmet));
        found = List.copyOf(found);
        readings = Map.copyOf(readings);
    }

    public boolean passed() {
        return outcome == Outcome.PASSED;
    }
}
