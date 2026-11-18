package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.grid.GridDispatchCenterBlockEntity;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_grid} пульта диспетчера (07 §7.17.4): острова, шины, ветви сети и прогноз нагрузки; вызовы —
 * на потоке сервера.
 *
 * <pre>
 * local grid = peripheral.find("pc_grid")
 * for _, isl in ipairs(grid.listIslands()) do print(isl.id, isl.freq, isl.pGen, isl.pLoad) end
 * local f = grid.getLoadForecast(18)   -- {hour, p, sigma}
 * </pre>
 */
public final class GridPeripheral implements IPeripheral {
    private final GridDispatchCenterBlockEntity center;

    public GridPeripheral(GridDispatchCenterBlockEntity center) {
        this.center = center;
    }

    @Override
    public String getType() {
        return "pc_grid";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof GridPeripheral g && g.center == center;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listIslands() {
        return center.listIslands();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getIsland(int id) throws LuaException {
        Map<String, Object> m = center.getIsland(id);
        if (m == null) throw new LuaException("no island " + id);
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listBuses() {
        return center.listBuses();
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> listBranches() {
        return center.listBranches();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getLoadForecast(int hour) {
        return center.getLoadForecast(hour);
    }
}
