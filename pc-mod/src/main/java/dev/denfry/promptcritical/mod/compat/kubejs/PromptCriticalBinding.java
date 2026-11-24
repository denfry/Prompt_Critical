package dev.denfry.promptcritical.mod.compat.kubejs;

import dev.denfry.promptcritical.api.SimulationAccess;
import dev.denfry.promptcritical.mod.api.SimulationAccessImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * The {@code PromptCritical} binding of server scripts (16 §16.11.1): read only, the same values Lua reads. Station ids
 * are strings (scripts get them from event {@code stationId}); tags are {@code <device>/<tag>}. Nothing here moves a
 * machine (N4).
 */
public final class PromptCriticalBinding {
    private final SimulationAccess all = new SimulationAccessImpl(null);

    /** Value of {@code tag} at station {@code stationId}, or {@code null} if unknown, not loaded or not numeric. */
    @Nullable
    public Double read(String stationId, String tag) {
        UUID id = parse(stationId);
        return id == null ? null : all.read(id, tag).orElse(null);
    }

    /** Tags of station {@code stationId}; empty if it is unknown. */
    public List<String> tags(String stationId) {
        UUID id = parse(stationId);
        return id == null ? List.of() : all.tags(id);
    }

    /** Stations with loaded devices, in all dimensions. */
    public List<String> stations() {
        List<String> out = new ArrayList<>();
        for (UUID id : all.stations()) out.add(id.toString());
        return out;
    }

    @Nullable
    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }
}
