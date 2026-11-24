package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.scada.WatchdogRelayBlockEntity;
import dev.denfry.promptcritical.mod.scada.WatchdogRelayBlockEntity.WatchdogException;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_watchdog} сторожевого реле (03c §3c.7.2–§3c.7.3). Владелец — номер компьютера CC
 * ({@code os.getComputerID()}), первым вызвавшего {@code arm()}; команды других компьютеров — ошибка
 * {@code E_ACCESS: NOT_MASTER}. {@code kick()} ставят <b>после</b> успешной итерации регулятора, а не в отдельной
 * сопрограмме {@code parallel}.
 *
 * <pre>
 * local wd = peripheral.find("pc_watchdog")
 * wd.configure(2); wd.arm()
 * while true do controller.step(); wd.kick(); sleep(0.25) end
 * -- резерв: if wd.getState().tripped then wd.takeover(); wd.reset(); wd.arm() end
 * </pre>
 */
public final class WatchdogPeripheral implements IPeripheral {
    private final WatchdogRelayBlockEntity relay;

    public WatchdogPeripheral(WatchdogRelayBlockEntity relay) {
        this.relay = relay;
    }

    @Override
    public String getType() {
        return "pc_watchdog";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof WatchdogPeripheral p && p.relay == relay;
    }

    @Override
    public Object getTarget() {
        return relay;
    }

    private interface Action {
        void run();
    }

    private static boolean call(Action a) throws LuaException {
        try {
            a.run();
            return true;
        } catch (WatchdogException e) {
            throw new LuaException(e.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public final boolean arm(IComputerAccess computer) throws LuaException {
        CcAccess.gate(computer, relay, dev.denfry.promptcritical.sim.journal.Permission.CONTROL, "arm", true);
        return call(() -> relay.arm(computer.getID()));
    }

    @LuaFunction(mainThread = true)
    public final boolean kick(IComputerAccess computer) throws LuaException {
        return call(() -> relay.kick(computer.getID()));
    }

    @LuaFunction(mainThread = true)
    public final boolean reset(IComputerAccess computer) throws LuaException {
        CcAccess.gate(computer, relay, dev.denfry.promptcritical.sim.journal.Permission.CONTROL, "reset", true);
        return call(() -> relay.reset(computer.getID()));
    }

    @LuaFunction(mainThread = true)
    public final boolean takeover(IComputerAccess computer) throws LuaException {
        CcAccess.gate(computer, relay, dev.denfry.promptcritical.sim.journal.Permission.CONTROL, "takeover", true);
        return call(() -> relay.takeover(computer.getID()));
    }

    @LuaFunction(mainThread = true)
    public final boolean release(IComputerAccess computer) throws LuaException {
        CcAccess.gate(computer, relay, dev.denfry.promptcritical.sim.journal.Permission.CONTROL, "release", true);
        return call(() -> relay.release(computer.getID()));
    }

    @LuaFunction(mainThread = true)
    public final boolean configure(IComputerAccess computer, double timeout) throws LuaException {
        CcAccess.gate(computer, relay, dev.denfry.promptcritical.sim.journal.Permission.TESTS_AND_SETUP, "configure", true);
        return call(() -> relay.configure(timeout));
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getState(IComputerAccess computer) {
        return relay.state(computer.getID());
    }

    @LuaFunction(mainThread = true)
    public final boolean isOwner(IComputerAccess computer) {
        return relay.timer().owner() == computer.getID();
    }
}
