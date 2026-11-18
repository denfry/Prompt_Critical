package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.scada.HistorianBlockEntity;
import dev.denfry.promptcritical.sim.automation.historian.TagArchive;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_historian} (03d §3d.14.4): теги, сырые точки, интерполяция, агрегаты. Время — секунды игрового
 * времени сервера ({@code getStats().now}).
 *
 * <pre>
 * local h = peripheral.find("pc_historian")
 * local now = h.getStats().now
 * local s = h.interpolate(h.listTags()[1].tag, now - 600, now, 10)   -- {t={...}, v={...}}
 * print(h.aggregate(h.listTags()[1].tag, now - 3600, now, "avg"))
 * </pre>
 */
public final class HistorianPeripheral implements IPeripheral {
    private final HistorianBlockEntity server;

    public HistorianPeripheral(HistorianBlockEntity server) {
        this.server = server;
    }

    @Override
    public String getType() {
        return "pc_historian";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof HistorianPeripheral h && h.server == server;
    }

    private TagArchive tag(String name) throws LuaException {
        TagArchive a = server.archive(name);
        if (a == null) throw new LuaException("no tag " + name);
        return a;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listTags() {
        return server.listTags();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> query(String tag, double t0, double t1) throws LuaException {
        return HistorianBlockEntity.series(tag(tag).query(t0, t1, HistorianBlockEntity.QUERY_MAX));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> interpolate(String tag, double t0, double t1, double step) throws LuaException {
        if (!(step > 0)) throw new LuaException("step must be > 0");
        return HistorianBlockEntity.series(tag(tag).interpolate(t0, t1, step, HistorianBlockEntity.QUERY_MAX));
    }

    @LuaFunction(mainThread = true)
    public final double aggregate(String tag, double t0, double t1, String fn) throws LuaException {
        try {
            return tag(tag).aggregate(t0, t1, fn);
        } catch (IllegalArgumentException e) {
            throw new LuaException("fn: avg, min, max, count, integral, stdev");
        }
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getStats() {
        return server.stats();
    }
}
