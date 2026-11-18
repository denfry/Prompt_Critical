package dev.denfry.promptcritical.mod.scada.cc;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.denfry.promptcritical.mod.scada.TwinWorkstationBlockEntity;
import dev.denfry.promptcritical.sim.automation.twin.LoopMetrics;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Периферия {@code pc_twin} инженерной станции (03d §3d.9.1, «режим стенда»): Lua-программа читает приборы копии
 * установки и командует её механизмами, а не реальными. Программа, отлаженная на стенде, переносится на реальные
 * устройства заменой источника данных. Отказы датчиков копии видны и Lua.
 *
 * <pre>
 * local tw = peripheral.find("pc_twin")
 * tw.startFat()
 * while tw.getState().status == "running" do
 *   if tw.read("level_m") > 6 then tw.command("inlet_valve", 0) end
 *   os.sleep(0.5)
 * end
 * </pre>
 */
public final class TwinPeripheral implements IPeripheral {
    private final TwinWorkstationBlockEntity ws;

    public TwinPeripheral(TwinWorkstationBlockEntity ws) {
        this.ws = ws;
    }

    @Override
    public String getType() {
        return "pc_twin";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof TwinPeripheral t && t.ws == ws;
    }

    private TwinSession session() throws LuaException {
        ScenarioRun r = ws.run();
        if (r == null) throw new LuaException("no twin session: call startFat() or press FAT");
        return r.session();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getState() {
        ScenarioRun r = ws.run();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("plant", ws.template());
        m.put("status", r == null ? (ws.status() == 2 ? "passed" : ws.status() == 3 ? "failed" : "idle") : "running");
        if (r != null) {
            TwinSession s = r.session();
            m.put("scenario", r.scenario().id());
            m.put("t", s.timeS());
            m.put("remaining", r.remainingS());
            m.put("faults", s.faults().stream().map(f -> f.label()).toList());
            m.put("plc", s.plc() == null ? "none" : s.plc().mode().name());
            if (!s.loops().isEmpty()) {
                LoopMetrics l = s.loops().getFirst();
                m.put("iae", l.iae());
                m.put("ise", l.ise());
                m.put("itae", l.itae());
                m.put("overshoot", l.overshoot());
                m.put("settlingTime", l.settlingTimeS());
                m.put("valveTravel", l.valveTravel());
            }
        }
        m.put("deployable", ws.deployable());
        return m;
    }

    @LuaFunction(mainThread = true)
    public final List<String> listSignals() throws LuaException {
        return session().plant().signals();
    }

    @LuaFunction(mainThread = true)
    public final List<String> listActuators() throws LuaException {
        return session().plant().actuators();
    }

    /** Показание прибора копии; {@code nil} — нет показания (обрыв линии). */
    @LuaFunction(mainThread = true)
    public final Object read(String signal) throws LuaException {
        TwinSession s = session();
        if (!s.plant().signals().contains(signal)) throw new LuaException("no signal " + signal);
        double v = s.measured(signal);
        return Double.isNaN(v) ? null : v;
    }

    @LuaFunction(mainThread = true)
    public final void command(String actuator, double value) throws LuaException {
        TwinSession s = session();
        if (!s.plant().actuators().contains(actuator)) throw new LuaException("no actuator " + actuator);
        s.command(actuator, value);
    }

    @LuaFunction(mainThread = true)
    public final boolean startFat() {
        ws.startFat(null);
        return ws.run() != null;
    }

    @LuaFunction(mainThread = true)
    public final List<String> getLog() throws LuaException {
        return List.copyOf(session().log());
    }
}
