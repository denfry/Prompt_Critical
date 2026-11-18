package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.automation.twin.Fault;
import dev.denfry.promptcritical.sim.automation.twin.ScenarioRun;
import dev.denfry.promptcritical.sim.automation.twin.TwinPlants;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Пределы двойников и тренажёров на сервер (03d §3d.9.1, 11 §11.3): число одновременных сеансов
 * ({@code twin.max_sessions}, {@code content.max_simulators_running}), общий бюджет серверного времени
 * {@code twin.cpu_ms_per_tick} — при нехватке двойники замедляются, а не мир — и длительность сеанса
 * {@code twin.max_duration_s}. Сеанс живёт в памяти владельца-блока: выгрузка чанка или перезапуск сервера его
 * закрывают, итоги блок хранит сам.
 */
public final class TwinSessions {
    public enum Kind { TWIN, SIMULATOR }

    private static final Set<Object> TWINS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Object> SIMULATORS = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int budgetTick = -1;
    private static long spentNanos;

    private TwinSessions() {
    }

    private static Set<Object> set(Kind k) {
        return k == Kind.TWIN ? TWINS : SIMULATORS;
    }

    private static int limit(Kind k) {
        return k == Kind.TWIN ? ServerConfig.TWIN_MAX_SESSIONS.get() : ServerConfig.CONTENT_MAX_SIMULATORS.get();
    }

    /** Занять место под сеанс; {@code false} — предел сервера исчерпан (T11.8: третий тренажёр не запускается). */
    public static synchronized boolean acquire(Object owner, Kind kind) {
        Set<Object> s = set(kind);
        if (s.contains(owner)) return true;
        if (s.size() >= limit(kind)) return false;
        s.add(owner);
        return true;
    }

    public static synchronized void release(Object owner) {
        TWINS.remove(owner);
        SIMULATORS.remove(owner);
    }

    public static synchronized int running(Kind kind) {
        return set(kind).size();
    }

    /** Предел длительности сеанса в тиках сервера. */
    public static long maxTicks() {
        return ServerConfig.TWIN_MAX_DURATION_S.get() * 20L;
    }

    /**
     * Отказы, которые инструктор вводит кнопкой по кругу (03d §3d.9.1 «Ввод неисправностей»): застывший первый датчик,
     * обрыв его линии, заклинивший первый механизм, отказ ПЛК.
     */
    public static List<Fault> manualFaults(String template) {
        String sig = TwinPlants.signals(template).getFirst(), act = TwinPlants.actuators(template).getFirst();
        return List.of(new Fault.SensorFault(sig, Fault.SensorMode.FROZEN, 0), new Fault.SensorFault(sig, Fault.SensorMode.OPEN_LOOP, 0),
                new Fault.StuckActuator(act, Fault.StuckMode.STUCK_AT, Double.NaN), new Fault.PlcWatchdog());
    }

    /**
     * Шаги прогона в этом тике сервера: до {@code speed} шагов (не больше {@code twin.max_speed}), пока не исчерпан общий
     * бюджет тика. Возвращает число сделанных шагов.
     */
    public static int advance(MinecraftServer server, ScenarioRun run, int speed) {
        int tick = server.getTickCount();
        if (tick != budgetTick) {
            budgetTick = tick;
            spentNanos = 0;
        }
        long budget = (long) (ServerConfig.TWIN_CPU_MS_PER_TICK.get() * 1e6);
        int n = Math.min(speed, ServerConfig.TWIN_MAX_SPEED.get()), done = 0;
        while (done < n && spentNanos < budget && run.status() == ScenarioRun.Status.RUNNING) {
            long t0 = System.nanoTime();
            run.step();
            spentNanos += System.nanoTime() - t0;
            done++;
        }
        return done;
    }

    /** Сообщение игрокам у блока (16 блоков): итоги прогона, отказы, пределы. */
    public static void say(ServerLevel level, BlockPos pos, Component msg) {
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(pos.getCenter()) <= 16 * 16) p.displayClientMessage(msg, false);
    }
}
