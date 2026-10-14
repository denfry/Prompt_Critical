package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.command.PcCommands;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Жизненный цикл биндингов измерений (05 §5.3.1, §5.4.2): создание при загрузке уровня, тик, перезагрузка конфига,
 * закрытие. Пул воркеров общий для всех измерений сервера.
 */
public final class SimBindings {
    private static final Map<ResourceKey<Level>, SimLevelBinding> BINDINGS = new LinkedHashMap<>();
    private static final List<Function<ServerLevel, Subsystem>> SUBSYSTEMS = new ArrayList<>();
    @Nullable
    private static WorkerPool workers;

    private SimBindings() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(SimBindings::onConfigReload);
        NeoForge.EVENT_BUS.addListener(SimBindings::onLevelLoad);
        NeoForge.EVENT_BUS.addListener(SimBindings::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(SimBindings::onServerTickPre);
        NeoForge.EVENT_BUS.addListener(SimBindings::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(SimBindings::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SimBindings::onRegisterCommands);
    }

    /**
     * Фабрика подсистемы мира; порядок вызовов — порядок FAST-шага (05 §5.2.3). Регистрировать при конструировании
     * мода, до загрузки уровней.
     */
    public static void registerSubsystem(Function<ServerLevel, Subsystem> factory) {
        SUBSYSTEMS.add(factory);
    }

    @Nullable
    public static SimLevelBinding get(ServerLevel level) {
        return BINDINGS.get(level.dimension());
    }

    public static Iterable<SimLevelBinding> all() {
        return BINDINGS.values();
    }

    /** Изменился блок мода: отметить позицию для пересборки топологии (05 §5.3.3). */
    public static void markDirty(Level level, BlockPos pos) {
        if (level instanceof ServerLevel s) {
            SimLevelBinding b = BINDINGS.get(s.dimension());
            if (b != null) b.topology().markDirty(pos);
        }
    }

    private static void onLevelLoad(LevelEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (workers == null) workers = WorkerPool.create(SimConfigs.workerThreads());
        BINDINGS.put(level.dimension(), SimLevelBinding.create(level, workers, SUBSYSTEMS));
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        SimLevelBinding b = BINDINGS.remove(level.dimension());
        if (b != null) b.close();
    }

    private static void onServerTickPre(ServerTickEvent.Pre event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SimLevelBinding b = BINDINGS.get(level.dimension());
            if (b != null) b.preTick();
        }
    }

    private static void onServerTickPost(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            SimLevelBinding b = BINDINGS.get(level.dimension());
            if (b != null) b.postTick();
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        for (SimLevelBinding b : BINDINGS.values()) b.close();
        BINDINGS.clear();
        if (workers != null) {
            workers.close();
            workers = null;
        }
    }

    /** Событие приходит с потока наблюдателя файлов: применение — на серверном потоке, в домен — командой. */
    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != ServerConfig.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            SimConfig cfg = SimConfigs.fromServerConfig();
            for (SimLevelBinding b : BINDINGS.values()) b.reloadConfig(cfg);
            PromptCritical.LOGGER.info("Server config reloaded; applied at the next simulation tick");
        });
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        PcCommands.register(event.getDispatcher());
    }
}
