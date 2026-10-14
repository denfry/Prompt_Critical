package dev.denfry.promptcritical.sim.core;

import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.core.profile.SimProfiler;
import dev.denfry.promptcritical.sim.core.profile.SimTickEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import jdk.jfr.EventType;

/**
 * Корень симуляции одного измерения (05 §5.2.1, решение A2): часы, очередь команд, буфер событий, публичное
 * состояние, топология и подсистемы. Тик и все изменения — только серверный поток; {@link #commands()} и
 * {@link #snapshot()} безопасны с любых потоков.
 *
 * <p>Порядок тика (05 §5.4.2, §5.2.3): отложенные удаления → слияние и запуск SLOW, затем STORAGE (по фазам) →
 * команды → FAST подсистем в порядке регистрации → публикация среза → сдвиг часов.
 */
public final class SimulationWorld implements AutoCloseable {
    /** Текущая версия схемы сохранения (05 §5.2.8). */
    public static final int SCHEMA_VERSION = 1;

    private static final EventType TICK_EVENT = EventType.getEventType(SimTickEvent.class);
    private static final int STAGE_SLOW = 0, STAGE_COMMANDS = 1, STAGE_FAST = 2, STAGE_PUBLISH = 3, STAGE_TICK = 4;

    private SimConfig config;
    private final long worldSeed;
    private final int dimensionKey;
    private final WorkerPool workers;
    private final SimClock clock;
    private final CommandQueue commands;
    private final EventBuffer events;
    private final PublicState publicState = new PublicState();
    private final SnapshotPool snapshots;
    private final TopologyEditor topology = new TopologyEditor();
    private final DegradationController degradation;
    private final SimProfiler profiler = new SimProfiler("slow_merge", "commands", "fast", "publish", "tick");
    private final FastContext fastCtx = new FastContext(this);
    private final SlowContext slowCtx;
    private final SlowContext storageCtx;
    private final TickReport report = new TickReport();
    private final Map<Class<?>, Object> services = new HashMap<>();
    private final List<Subsystem> subsystemList = new ArrayList<>();
    private final Consumer<CommandEnvelope> applier = this::applyCommand;
    private final List<String> deferredJournal = new ArrayList<>();

    private Subsystem[] subsystems = new Subsystem[0];
    private WorkerPool.JobSlot[] slowSlots = new WorkerPool.JobSlot[0];
    private WorkerPool.JobSlot[] storageSlots = new WorkerPool.JobSlot[0];
    private boolean closed;

    public SimulationWorld(SimConfig config, long worldSeed, int dimensionKey, WorkerPool workers) {
        this.config = Objects.requireNonNull(config, "config");
        this.worldSeed = worldSeed;
        this.dimensionKey = dimensionKey;
        this.workers = Objects.requireNonNull(workers, "workers");
        clock = new SimClock(config.kSlow(), config.kStorage());
        commands = new CommandQueue(config.commandQueueCapacity());
        events = new EventBuffer(config.eventBufferCapacity());
        snapshots = new SnapshotPool(config.snapshotBuffers());
        degradation = new DegradationController(config.budgetMs());
        slowCtx = new SlowContext(clock, TimeDomain.SLOW, config);
        storageCtx = new SlowContext(clock, TimeDomain.STORAGE, config);
        snapshots.publish(0, 0.0, publicState);
    }

    // --- состав ---

    /**
     * Зарегистрировать подсистему. Порядок регистрации — порядок FAST-шага (05 §5.2.3) и фаза SLOW/STORAGE
     * (номер по модулю периода). Только до первого тика или сразу после загрузки.
     */
    public void register(Subsystem s) {
        for (Subsystem other : subsystemList) {
            if (other.id().equals(s.id())) throw new IllegalArgumentException("duplicate subsystem id " + s.id());
        }
        subsystemList.add(s);
        subsystems = subsystemList.toArray(new Subsystem[0]);
        slowSlots = Arrays.copyOf(slowSlots, subsystems.length);
        storageSlots = Arrays.copyOf(storageSlots, subsystems.length);
        slowSlots[subsystems.length - 1] = workers.newSlot();
        storageSlots[subsystems.length - 1] = workers.newSlot();
    }

    /** Служба биндинга, доступная подсистемам через {@link FastContext#service}. */
    public <T> void registerService(Class<T> type, T impl) {
        services.put(type, Objects.requireNonNull(impl, "impl"));
    }

    public <T> T service(Class<T> type) {
        Object v = services.get(type);
        return v == null ? null : type.cast(v);
    }

    public <T extends Subsystem> T subsystem(Class<T> type) {
        for (Subsystem s : subsystems) if (type.isInstance(s)) return type.cast(s);
        throw new IllegalArgumentException("no subsystem " + type.getSimpleName());
    }

    // --- время и шаг ---

    public SimClock clock() {
        return clock;
    }

    public SimConfig config() {
        return config;
    }

    public long worldSeed() {
        return worldSeed;
    }

    public int dimensionKey() {
        return dimensionKey;
    }

    /** Один FAST-шаг 0,05 с; при наступлении срока — слияние и запуск SLOW/STORAGE. */
    public TickReport tick(ExternalInputs inputs) {
        if (closed) throw new IllegalStateException("world is closed");
        long t0 = SimProfiler.now();
        long tick = clock.tick();
        events.clear();
        flushDeferredJournal(tick);
        topology.applyPendingRemovals(subsystemList);

        long tSlow = SimProfiler.now();
        int timeoutMs = config.slowJoinTimeoutMs();
        for (int i = 0; i < subsystems.length; i++) {
            Subsystem s = subsystems[i];
            if (clock.due(TimeDomain.SLOW, i % TimeDomain.SLOW.periodTicks)) {
                SlowResult r = slowSlots[i].join(timeoutMs);
                if (r != null) s.mergeSlow(r);
                SlowJob job = s.prepareSlow(slowCtx);
                if (job != SlowJob.NONE) slowSlots[i].start(job);
            }
            if (clock.due(TimeDomain.STORAGE, i % TimeDomain.STORAGE.periodTicks)) {
                SlowResult r = storageSlots[i].join(timeoutMs);
                if (r != null) s.mergeStorage(r);
                SlowJob job = s.prepareStorage(storageCtx);
                if (job != SlowJob.NONE) storageSlots[i].start(job);
            }
        }
        long tCommands = SimProfiler.now();

        commands.setCurrentTick(tick);
        int applied = commands.drainSorted(config.maxCommandsPerTick(), applier);
        long tFast = SimProfiler.now();

        fastCtx.setInputs(inputs);
        for (Subsystem s : subsystems) s.stepFast(fastCtx);
        long tPublish = SimProfiler.now();

        snapshots.publish(tick, clock.simTime(), publicState);
        clock.advance();
        long tEnd = SimProfiler.now();

        profiler.record(STAGE_SLOW, tCommands - tSlow);
        profiler.record(STAGE_COMMANDS, tFast - tCommands);
        profiler.record(STAGE_FAST, tPublish - tFast);
        profiler.record(STAGE_PUBLISH, tEnd - tPublish);
        profiler.record(STAGE_TICK, tEnd - t0);
        DegradationLevel level = degradation.record(tEnd - t0);
        report.set(tick, tEnd - t0, tCommands - tSlow, applied, events.size(), level);
        if (TICK_EVENT.isEnabled()) commitTickEvent(tick, tEnd - t0, tCommands - tSlow, applied, level);
        return report;
    }

    private void commitTickEvent(long tick, long totalNanos, long slowNanos, int applied, DegradationLevel level) {
        SimTickEvent e = new SimTickEvent();
        e.dimension = dimensionKey;
        e.tick = tick;
        e.fastMicros = totalNanos / 1000;
        e.slowMergeMicros = slowNanos / 1000;
        e.commands = applied;
        e.degradation = level.ordinal();
        e.commit();
    }

    private void applyCommand(CommandEnvelope env) {
        SimCommand c = env.command();
        CommandStatus status = switch (c) {
            case SimCommand.ReloadConfig r -> {
                reloadConfig(r.config());
                yield CommandStatus.OK;
            }
            case SimCommand.SetDeviceMode m -> {
                if (!topology.exists(m.target())) yield CommandStatus.NO_TARGET;
                topology.setDeviceMode(m.target(), m.mode());
                yield CommandStatus.OK;
            }
            default -> dispatch(c, env.source());
        };
        events.commandResult(clock.tick(), c.target(), env.ticket(), status);
    }

    private CommandStatus dispatch(SimCommand c, CommandSource source) {
        for (Subsystem s : subsystems) {
            CommandStatus st = s.apply(c, source);
            if (st != CommandStatus.NOT_HANDLED) return st;
        }
        return CommandStatus.NOT_HANDLED;
    }

    private void reloadConfig(SimConfig next) {
        // Ключи структуры (ёмкости, число буферов) действуют только при создании мира (05 §5.3.6).
        config = new SimConfig(next.kSlow(), next.kStorage(), config.commandQueueCapacity(),
                next.maxCommandsPerTick(), config.eventBufferCapacity(), config.snapshotBuffers(),
                next.slowJoinTimeoutMs(), next.budgetMs(), next.sections());
        clock.setRates(config.kSlow(), config.kStorage());
        slowCtx.setConfig(config);
        storageCtx.setConfig(config);
        degradation.setBudget(config.budgetMs());
        for (Subsystem s : subsystems) s.onConfigReload(config);
    }

    // --- вход и выход ---

    public CommandQueue commands() {
        return commands;
    }

    public TopologyEditor topology() {
        return topology;
    }

    public PublicState publicState() {
        return publicState;
    }

    EventBuffer events() {
        return events;
    }

    /** Последний опубликованный срез; любые потоки. */
    public SimSnapshot snapshot() {
        return snapshots.latest();
    }

    public SimSnapshot snapshotLockedCopy() {
        return snapshots.lockedCopy();
    }

    /** События последнего тика. Серверный поток, после {@link #tick}. */
    public void drainEvents(SimEventSink sink) {
        events.drain(sink);
    }

    public long eventOverflowTotal() {
        return events.overflowTotal();
    }

    public SimProfiler profiler() {
        return profiler;
    }

    public DegradationController degradation() {
        return degradation;
    }

    public TickReport lastReport() {
        return report;
    }

    // --- состояние ---

    public void save(StateWriter out) {
        out.putInt("schema_version", SCHEMA_VERSION);
        out.putInt("dimension", dimensionKey);
        out.putLong("world_seed", worldSeed);
        out.putLong("tick", clock.tick());
        out.beginObject("topology");
        topology.save(out);
        out.endObject();
        out.beginObject("subsystems");
        for (Subsystem s : subsystems) {
            out.beginObject(s.id());
            s.save(out);
            out.endObject();
        }
        out.endObject();
        // Задания SLOW/STORAGE в работе: их входы, чтобы после загрузки слияние было в тот же тик (детерминизм).
        out.beginObject("pending_jobs");
        for (int i = 0; i < subsystems.length; i++) {
            if (slowSlots[i].active()) {
                out.beginObject(subsystems[i].id() + "/slow");
                slowSlots[i].job().save(out);
                out.endObject();
            }
            if (storageSlots[i].active()) {
                out.beginObject(subsystems[i].id() + "/storage");
                storageSlots[i].job().save(out);
                out.endObject();
            }
        }
        out.endObject();
    }

    /** Сохранение в дерево. */
    public MutableStateTree saveTree() {
        TreeStateWriter w = new TreeStateWriter();
        save(w);
        return w.root();
    }

    /** Поднять сохранение до {@link #SCHEMA_VERSION}; дерево без версии — схема 0. */
    public static MutableStateTree upgrade(MutableStateTree root, MigrationRegistry migrations) {
        int from = root.has("schema_version") ? (Integer) root.get("schema_version") : 0;
        migrations.upgrade(root, from, SCHEMA_VERSION);
        root.put("schema_version", SCHEMA_VERSION);
        return root;
    }

    /**
     * Загрузить состояние, уже поднятое {@link #upgrade} до текущей схемы. Подсистемы регистрируются до вызова.
     * Подсистема без сохранения или с нечитаемым сохранением переводится в безопасное состояние с записью журнала;
     * сохранение незарегистрированной подсистемы отбрасывается с записью журнала (05 §5.2.8).
     */
    public void load(MutableStateTree root) {
        int schema = (Integer) root.get("schema_version");
        if (schema != SCHEMA_VERSION) throw new IllegalStateException("save schema " + schema + " was not upgraded");
        TreeStateReader in = new TreeStateReader(root);
        clock.setTick(in.getLong("tick"));
        in.beginObject("topology");
        topology.load(in);
        in.endObject();

        MutableStateTree saved = root.has("subsystems") ? root.child("subsystems") : new MutableStateTree();
        Set<String> unknown = new LinkedHashSet<>(saved.keys());
        for (Subsystem s : subsystems) {
            unknown.remove(s.id());
            if (!saved.has(s.id())) {
                s.resetToSafeState();
                deferredJournal.add("subsystem '" + s.id() + "' has no saved state: safe state");
                continue;
            }
            try {
                s.load(new TreeStateReader(saved.child(s.id())));
            } catch (RuntimeException e) {
                s.resetToSafeState();
                deferredJournal.add("subsystem '" + s.id() + "' state unreadable (" + e.getMessage() + "): safe state");
            }
        }
        for (String id : unknown) deferredJournal.add("saved state of unknown subsystem '" + id + "' dropped");

        MutableStateTree jobs = root.has("pending_jobs") ? root.child("pending_jobs") : new MutableStateTree();
        for (int i = 0; i < subsystems.length; i++) {
            Subsystem s = subsystems[i];
            restoreJob(s, slowSlots[i], jobs, s.id() + "/slow", true);
            restoreJob(s, storageSlots[i], jobs, s.id() + "/storage", false);
        }
        snapshots.publish(clock.tick(), clock.simTime(), publicState);
    }

    private void restoreJob(Subsystem s, WorkerPool.JobSlot slot, MutableStateTree jobs, String key, boolean slow) {
        if (slot.active()) slot.join(-1);
        if (!jobs.has(key)) return;
        try {
            TreeStateReader in = new TreeStateReader(jobs.child(key));
            SlowJob job = slow ? s.restoreSlow(in) : s.restoreStorage(in);
            if (job != SlowJob.NONE) slot.start(job);
            else deferredJournal.add("pending " + key + " job of subsystem was not restored: one step lost");
        } catch (RuntimeException e) {
            deferredJournal.add("pending " + key + " job unreadable (" + e.getMessage() + "): one step lost");
        }
    }

    private void flushDeferredJournal(long tick) {
        if (deferredJournal.isEmpty()) return;
        for (String msg : deferredJournal) events.journal(tick, SimId.NONE, Severity.WARNING, msg);
        deferredJournal.clear();
    }

    /** xxHash64 всего сохраняемого состояния (05 §5.2.4, {@code /pc debug hash}). */
    public long stateHash() {
        return StateHash.of(saveTree());
    }

    /** Дождаться запущенных заданий SLOW/STORAGE и закрыть мир; пул воркеров остаётся у владельца. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (WorkerPool.JobSlot slot : slowSlots) if (slot.active()) slot.join(-1);
        for (WorkerPool.JobSlot slot : storageSlots) if (slot.active()) slot.join(-1);
    }
}
