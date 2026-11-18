package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.TelemetryException;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_rtu}: функции Lua переводятся в вызовы шлюза ({@link TelemetryGatewayBlockEntity}) на потоке
 * сервера ({@code mainThread = true}), отказ шлюза — ошибкой Lua.
 *
 * <pre>
 * local rtu = peripheral.find("pc_rtu")
 * for _, m in ipairs(rtu.machines()) do print(m.id) end
 * local v = rtu.read(id)            -- значения приборов по именам, v._age — возраст среза, с
 * local d = rtu.describe(id)        -- d.points (имя, вид, надпись, единицы), d.buttons
 * rtu.press(id, "drill.bop_button") -- кнопка щита
 * </pre>
 */
public final class RtuPeripheral implements IPeripheral {
    private final TelemetryGatewayBlockEntity gateway;

    public RtuPeripheral(TelemetryGatewayBlockEntity gateway) {
        this.gateway = gateway;
    }

    @Override
    public String getType() {
        return "pc_rtu";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof RtuPeripheral r && r.gateway == gateway;
    }

    private static <T> T call(Supplier<T> f) throws LuaException {
        try {
            return f.get();
        } catch (TelemetryException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public final List<Map<String, Object>> machines() throws LuaException {
        return call(gateway::machines);
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> describe(String id) throws LuaException {
        return call(() -> gateway.describe(id));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> read(String id) throws LuaException {
        return call(() -> gateway.read(id));
    }

    @LuaFunction(mainThread = true)
    public final boolean press(String id, String button) throws LuaException {
        return call(() -> {
            gateway.press(id, button);
            return true;
        });
    }

    @LuaFunction
    public final double cycle() {
        return gateway.cycleSeconds();
    }
}
