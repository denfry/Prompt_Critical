package dev.denfry.promptcritical.sim.journal;

import dev.denfry.promptcritical.sim.core.Severity;
import java.util.Set;
import java.util.UUID;

/**
 * Отбор записей журнала (04d §4d.8.1). {@code actorPlayer} оставляет только действия одного игрока — так журнал видят
 * не-члены станции (§4d.8.2: «прочие — только записи о действиях с этой станцией, совершённых ими»).
 *
 * @param actions     пустой набор — любые
 * @param minSeverity {@code null} — любая
 * @param actorPlayer {@code null} — любой актор
 */
public record JournalFilter(Set<JournalAction> actions, Severity minSeverity, UUID actorPlayer) {
    public static final JournalFilter ALL = new JournalFilter(Set.of(), null, null);

    public JournalFilter {
        actions = Set.copyOf(actions);
    }

    public static JournalFilter ownActions(UUID player) {
        return new JournalFilter(Set.of(), null, player);
    }

    public boolean test(JournalEntry e) {
        if (!actions.isEmpty() && !actions.contains(e.action())) return false;
        if (minSeverity != null && e.severity().compareTo(minSeverity) < 0) return false;
        return actorPlayer == null || actorPlayer.equals(e.actor().responsible());
    }
}
