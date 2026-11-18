package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.mes.MesServerBlockEntity;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.TelemetryException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_mes} сервера завода: вызовы Lua идут на поток сервера, отказ сервера — ошибкой Lua.
 *
 * <pre>
 * local mes = peripheral.find("pc_mes")
 * local k = mes.getKpi()               -- generation, load, target, costRate, revenueRate, emissions, efficiency, lambda
 * for _, u in ipairs(mes.getDispatch()) do print(u.id, u.output, u.setpoint) end
 * mes.setDispatchTarget(12.5)          -- задание, МВт; отрицательное — по нагрузке
 * </pre>
 */
public final class MesPeripheral implements IPeripheral {
    private final MesServerBlockEntity server;

    public MesPeripheral(MesServerBlockEntity server) {
        this.server = server;
    }

    @Override
    public String getType() {
        return "pc_mes";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof MesPeripheral m && m.server == server;
    }

    private static <T> T call(Supplier<T> f) throws LuaException {
        try {
            return f.get();
        } catch (TelemetryException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getKpi() throws LuaException {
        return call(server::kpi);
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> getDispatch() throws LuaException {
        return call(server::dispatchTable);
    }

    @LuaFunction(mainThread = true)
    public final boolean setDispatchTarget(double mw) throws LuaException {
        return call(() -> {
            server.setTarget(mw);
            return true;
        });
    }
}
