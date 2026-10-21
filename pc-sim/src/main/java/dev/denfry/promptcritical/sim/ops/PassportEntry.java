package dev.denfry.promptcritical.sim.ops;

import java.util.UUID;

/** Запись истории паспорта (13 §13.6.6). */
public record PassportEntry(long tick, Kind kind, String text, UUID by) {
    public enum Kind { ACCEPTED, MAINTENANCE_DONE, REPLACEMENT, MOC, REACCEPTED }
}
