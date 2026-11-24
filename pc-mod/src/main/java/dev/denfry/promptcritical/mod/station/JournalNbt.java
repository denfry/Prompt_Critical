package dev.denfry.promptcritical.mod.station;

import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.journal.Actor;
import dev.denfry.promptcritical.sim.journal.JournalAction;
import dev.denfry.promptcritical.sim.journal.JournalEntry;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** Запись журнала станции в NBT и обратно; станция — у родительского тега. */
final class JournalNbt {
    private JournalNbt() {
    }

    static CompoundTag write(JournalEntry e) {
        CompoundTag c = new CompoundTag();
        c.putLong("seq", e.seq());
        c.putLong("tick", e.gameTick());
        c.putDouble("t", e.simTime());
        c.putString("action", e.action().name());
        c.putString("severity", e.severity().name());
        if (!e.target().isEmpty()) c.putString("target", e.target());
        if (!e.detail().isEmpty()) c.putString("detail", e.detail());
        CompoundTag a = new CompoundTag();
        switch (e.actor()) {
            case Actor.Player p -> {
                a.putString("kind", "player");
                a.putUUID("uuid", p.uuid());
                a.putString("name", p.nameAtTime());
            }
            case Actor.Computer k -> {
                a.putString("kind", "computer");
                a.putInt("id", k.computerId());
                if (k.cardHolder() != null) a.putUUID("uuid", k.cardHolder());
                a.putString("name", k.label() == null ? "" : k.label());
            }
            case Actor.Automatic s -> {
                a.putString("kind", "auto");
                a.putString("name", s.system());
            }
            case Actor.ServerPolicy s -> {
                a.putString("kind", "policy");
                a.putString("name", s.policy());
            }
            case Actor.Admin ad -> {
                a.putString("kind", "admin");
                a.putUUID("uuid", ad.uuid());
                a.putString("name", ad.command());
            }
        }
        c.put("actor", a);
        return c;
    }

    @Nullable
    static JournalEntry read(CompoundTag c, UUID station) {
        try {
            CompoundTag a = c.getCompound("actor");
            String name = a.getString("name");
            UUID uuid = a.hasUUID("uuid") ? a.getUUID("uuid") : null;
            Actor actor = switch (a.getString("kind")) {
                case "player" -> uuid == null ? null : new Actor.Player(uuid, name);
                case "computer" -> new Actor.Computer(a.getInt("id"), uuid, name);
                case "auto" -> new Actor.Automatic(name);
                case "policy" -> new Actor.ServerPolicy(name);
                case "admin" -> uuid == null ? null : new Actor.Admin(uuid, name);
                default -> null;
            };
            if (actor == null) return null;
            return new JournalEntry(c.getLong("seq"), c.getLong("tick"), c.getDouble("t"), station, actor,
                    JournalAction.valueOf(c.getString("action")), c.getString("target"), c.getString("detail"),
                    Severity.valueOf(c.getString("severity")));
        } catch (IllegalArgumentException bad) {
            return null; // неизвестное действие или важность из другой версии: запись пропускается
        }
    }
}
