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

    /**
     * Данные задачи компаундирования стенда смешения (09 §9.9.14 {@code BlendOptimizerInput}, 03d §3d.10.3 п. 1):
     * компоненты (смесительные ОЧИ и ОЧМ, ДНП, сера, бензол, ароматика, олефины, плотность, наличие м³/ч, цена) в порядке
     * каналов {@code %QW0…} и требования марки. Рецепт игрок ищет сам — симплексом на Lua или вручную.
     */
    @LuaFunction(mainThread = true)
    public final Map<String, Object> getBlendData() {
        var in = dev.denfry.promptcritical.sim.automation.twin.BlendTwinPlant.INPUT;
        List<Map<String, Object>> comps = new java.util.ArrayList<>();
        for (var c : in.components()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.id());
            m.put("ron", c.ron());
            m.put("mon", c.mon());
            m.put("rvp", c.rvpKpa());
            m.put("sulfur", c.sulfurPpm());
            m.put("benzene", c.benzenePct());
            m.put("aromatics", c.aromaticsPct());
            m.put("olefins", c.olefinsPct());
            m.put("density", c.density());
            m.put("available", c.availableM3h());
            m.put("cost", c.cost());
            comps.add(m);
        }
        var g = in.grade();
        Map<String, Object> grade = new LinkedHashMap<>();
        grade.put("id", g.id());
        grade.put("ron", g.ron());
        grade.put("mon", g.mon());
        grade.put("rvpMin", g.rvpMinKpa());
        grade.put("rvpMax", g.rvpMaxKpa());
        grade.put("sulfurMax", g.sulfurMaxPpm());
        grade.put("benzeneMax", g.benzeneMaxPct());
        grade.put("aromaticsMax", g.aromaticsMaxPct());
        grade.put("olefinsMax", g.olefinsMaxPct());
        grade.put("densityMin", g.densityMin());
        grade.put("densityMax", g.densityMax());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("components", comps);
        out.put("grade", grade);
        out.put("batchFlow", dev.denfry.promptcritical.sim.automation.twin.BlendTwinPlant.BATCH_M3H);
        return out;
    }

    /** Задача наладчика на выбранной установке станции (03d §3d.11). */
    @LuaFunction(mainThread = true)
    public final boolean startChallenge() {
        ws.startChallenge(null);
        return ws.run() != null;
    }
}
