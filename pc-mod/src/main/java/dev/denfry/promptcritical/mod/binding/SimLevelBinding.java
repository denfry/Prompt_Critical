package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.mine.MineWorld;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageBudgetConfig;
import dev.denfry.promptcritical.sim.consequence.QueuedDamageBudget;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.TopologyEditor;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Биндинг одного измерения (05 §5.3, решение A2): мир симуляции, его сохранение, привязка BE ↔ домен, инкрементальная
 * топология, внешние входы и применение {@link DamageBudget}. Только серверный поток.
 *
 * <p>Тик (05 §5.4.2): {@code ServerTickEvent.Pre} — пересборка топологии и FAST-шаг домена; {@code Post} — бюджет
 * разрушений, события, раз в SLOW-шаг — отметка сохранения и поиск «осиротевших» устройств.
 */
public final class SimLevelBinding {
    /** Устройство без BE в загруженном чанке удаляется через 1 мин (05 §5.2.8). */
    public static final long ORPHAN_TIMEOUT_TICKS = 1200;
    private static final int SLOW_PERIOD = 20;
    private static final MigrationRegistry MIGRATIONS = new MigrationRegistry();

    private final ServerLevel level;
    private final SimulationWorld world;
    private final QueuedDamageBudget damage;
    private final LevelDamageMutator mutator;
    private final SimSavedData data;
    private final TopologyRebuilder topology = new TopologyRebuilder();
    private final MineWorld mines;
    private final Map<SimId, DeviceHost> hosts = new HashMap<>();
    private final Map<SimId, Long> orphanSince = new HashMap<>();
    private final ExternalInputs inputs = ExternalInputs.NONE;
    private long commandSeq;
    private int tickFailures;

    private SimLevelBinding(ServerLevel level, SimulationWorld world, QueuedDamageBudget damage, SimSavedData data) {
        this.level = level;
        this.world = world;
        this.damage = damage;
        this.data = data;
        this.mutator = new LevelDamageMutator(level);
        this.mines = new MineWorld(this);
    }

    static SimLevelBinding create(ServerLevel level, WorkerPool workers, List<Function<ServerLevel, Subsystem>> subsystems) {
        SimSavedData data = level.getDataStorage().computeIfAbsent(SimSavedData.FACTORY, SimSavedData.NAME);
        SimConfig cfg = SimConfigs.fromServerConfig();
        SimulationWorld world = new SimulationWorld(cfg, level.getSeed(), level.dimension().location().toString().hashCode(), workers);
        QueuedDamageBudget damage = new QueuedDamageBudget(cfg.section(DamageBudgetConfig.class, SimConfigs.damage()));
        world.registerService(DamageBudget.class, damage);
        for (Function<ServerLevel, Subsystem> f : subsystems) world.register(f.apply(level));
        MutableStateTree saved = data.takeLoaded();
        if (saved != null && saved.size() > 0) world.load(SimulationWorld.upgrade(saved, MIGRATIONS));
        data.bind(world);
        data.setDirty();
        return new SimLevelBinding(level, world, damage, data);
    }

    public ServerLevel level() {
        return level;
    }

    public SimulationWorld world() {
        return world;
    }

    public QueuedDamageBudget damage() {
        return damage;
    }

    public TopologyRebuilder topology() {
        return topology;
    }

    /** Шахты измерения (08 §8.2). */
    public MineWorld mines() {
        return mines;
    }

    public static GridPos gridPos(BlockPos p) {
        return new GridPos(p.getX(), p.getY(), p.getZ());
    }

    // --- BE ↔ домен (05 §5.3.1) ---

    /** BE загружен: привязать к доменному объекту или создать его. */
    public void attach(DeviceHost host) {
        TopologyEditor t = world.topology();
        SimId id = host.simId();
        String type = host.deviceType();
        GridPos pos = gridPos(host.pos());
        DeviceHost other = id.isNone() ? null : hosts.get(id);
        boolean reuse = !id.isNone() && t.exists(id) && !t.removalPending(id) && type.equals(t.deviceType(id))
                && pos.equals(t.devicePos(id)) && (other == null || other == host);
        if (!reuse) {
            id = t.addDevice(type, pos);
            host.bindSimId(id);
        }
        hosts.put(id, host);
        orphanSince.remove(id);
    }

    /**
     * BE снят: {@code removed} — блок разрушен (устройство уходит из топологии в начале следующего тика), иначе
     * выгрузка чанка (домен живёт дальше).
     */
    public void detach(DeviceHost host, boolean removed) {
        SimId id = host.simId();
        if (id.isNone() || hosts.get(id) != host) return;
        hosts.remove(id);
        if (removed) world.topology().removeDevice(id);
    }

    public DeviceHost host(SimId id) {
        return hosts.get(id);
    }

    /** Команда в домен (05 §5.2.6): исполнится в начале ближайшего тика; −1 — очередь переполнена. */
    public long submit(SimCommand command, CommandSource source) {
        return world.commands().submit(command, source, commandSeq++);
    }

    // --- тик ---

    void preTick() {
        long gameTime = level.getGameTime();
        topology.process(gameTime, SimConfigs.topologyBudgetNanos());
        mines.preTick(gameTime);
        try {
            world.tick(inputs);
        } catch (RuntimeException e) {
            if (tickFailures++ < 3 || tickFailures % 1200 == 0) {
                PromptCritical.LOGGER.error("Simulation tick failed in {} ({} failures)", level.dimension().location(), tickFailures, e);
            }
        }
    }

    void postTick() {
        long gameTime = level.getGameTime();
        mines.postTick(gameTime);
        damage.drain(gameTime, mutator, mutator);
        world.drainEvents(this::onEvent);
        if (gameTime % SLOW_PERIOD == 0) {
            data.setDirty();
            scanOrphans(gameTime);
        }
    }

    private void onEvent(SimEvent e) {
        switch (e) {
            case SimEvent.Journal j -> {
                String src = j.source().isNone() ? "sim" : j.source().hex();
                if (j.severity().compareTo(Severity.WARNING) >= 0) {
                    PromptCritical.LOGGER.warn("[{}] {} {}: {}", level.dimension().location(), j.severity(), src, j.message());
                } else {
                    PromptCritical.LOGGER.info("[{}] {} {}: {}", level.dimension().location(), j.severity(), src, j.message());
                }
            }
            case SimEvent.DamageRequested d -> PromptCritical.LOGGER.warn("[{}] damage request {} from {}: {} blocks granted, {} denied",
                    level.dimension().location(), d.requestId(), d.source().hex(), d.blocksGranted(), d.blocksDenied());
            case SimEvent.Milestone m -> dev.denfry.promptcritical.mod.content.SimAchievements.onMilestone(level, world.topology(), m);
            case SimEvent.CommandResult ignored -> {
                // итоги команд читают CC и пульты (03b, 03c)
            }
        }
    }

    /** Устройства без BE в загруженных чанках (BE удалён вне игры) снимаются через минуту (05 §5.2.8). */
    private void scanOrphans(long gameTime) {
        TopologyEditor t = world.topology();
        List<SimId> expired = new ArrayList<>();
        for (int i = 0; i < t.deviceCount(); i++) {
            SimId id = t.deviceAt(i);
            if (hosts.containsKey(id) || t.removalPending(id)) {
                orphanSince.remove(id);
                continue;
            }
            GridPos p = t.devicePos(id);
            if (!level.isLoaded(new BlockPos(p.x(), p.y(), p.z()))) continue;
            long since = orphanSince.computeIfAbsent(id, k -> gameTime);
            if (gameTime - since >= ORPHAN_TIMEOUT_TICKS) expired.add(id);
        }
        for (SimId id : expired) {
            orphanSince.remove(id);
            t.removeDevice(id);
            PromptCritical.LOGGER.warn("[{}] orphaned device {} ({}) removed", level.dimension().location(), id.hex(), t.deviceType(id));
        }
    }

    // --- конфиг и закрытие ---

    void reloadConfig(SimConfig cfg) {
        world.commands().submit(new SimCommand.ReloadConfig(cfg), new CommandSource.ServerPolicy("config_reload"), commandSeq++);
        damage.setConfig(cfg.section(DamageBudgetConfig.class, damage.config()));
    }

    void close() {
        world.close();
    }
}
