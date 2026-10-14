package dev.denfry.promptcritical.sim.consequence;

import java.util.Map;

/** Сводка бюджета для {@code /pc profile} и журнала (04d §4d.2.2). */
public record BudgetStats(long blocksAppliedTotal, int queueLength, long deniedTotal,
        Map<DenyReason, Long> deniedByReason) {
    public BudgetStats {
        deniedByReason = Map.copyOf(deniedByReason);
    }
}
