package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.SimId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Паспорт установки (13 §13.6.6): создаётся при приёмке, дальше пишется история. */
public record UnitPassport(SimId unit, UnitKind kind, long formedTick, long acceptedTick, UUID acceptedBy,
        Map<CommissioningCheck, CheckResult> results, Baseline baseline, List<PassportEntry> history) {
    public UnitPassport {
        results = Map.copyOf(results);
        history = List.copyOf(history);
    }
}
