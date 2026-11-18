package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.IdentityHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Реестр периферий устройств: одно состояние {@link DeviceRuntime} на блок, часы для ограничителей и общий бюджет
 * вызовов компьютера (03b §3b.1.4: корзина {@code cc.token_rate}/{@code cc.token_burst} на все периферии мода
 * суммарно). Тикает только устройства, к которым подключён хотя бы один компьютер. Подключается мостом CC
 * ({@code mod.scada.cc.CcIntegration}) — без CC реестр не нужен и не регистрируется.
 */
public final class DeviceHub {
    private static final Map<BlockEntity, DeviceRuntime> RUNTIMES = Collections.synchronizedMap(new IdentityHashMap<>());
    private static final Set<DeviceRuntime> LIVE = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, TokenBucket> CALLS = new ConcurrentHashMap<>();
    private static volatile long clock;
    private static final long PRUNE_TICKS = 200;

    private DeviceHub() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DeviceHub::onServerTick);
        NeoForge.EVENT_BUS.addListener(DeviceHub::onServerStopped);
    }

    /** Часы ограничителей и квитанций: тики сервера с его запуска (монотонны в пределах сессии). */
    public static long clock() {
        return clock;
    }

    /** Состояние устройства блока или {@code null}, если у блока нет профиля периферии. Только поток сервера. */
    @Nullable
    public static DeviceRuntime runtime(BlockEntity be) {
        DeviceRuntime r = RUNTIMES.get(be);
        if (r != null) return r;
        DeviceProfile p = DeviceProfiles.of(be);
        if (p == null) return null;
        r = new DeviceRuntime(be, p);
        RUNTIMES.put(be, r);
        return r;
    }

    static void live(DeviceRuntime r) {
        LIVE.add(r);
    }

    static void idle(DeviceRuntime r) {
        if (!r.hasClients()) LIVE.remove(r);
    }

    /** Списать {@code cost} токенов с бюджета компьютера {@code computerId}; {@code false} — бюджет исчерпан. */
    static boolean admit(int computerId, double cost) {
        TokenBucket b = CALLS.computeIfAbsent(computerId, id -> new TokenBucket(ServerConfig.CC_TOKEN_RATE.get(), ServerConfig.CC_TOKEN_BURST.get()));
        synchronized (b) {
            return b.tryTake(clock, cost);
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        long now = event.getServer().getTickCount();
        clock = now;
        for (DeviceRuntime r : LIVE) r.tick(now);
        // снятые блоки без компьютеров забываем раз в 10 с
        if (now % PRUNE_TICKS == 0) synchronized (RUNTIMES) {
            RUNTIMES.values().removeIf(r -> r.blockEntity().isRemoved() && !r.hasClients());
        }
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        LIVE.clear();
        RUNTIMES.clear();
        CALLS.clear();
        clock = 0;
    }
}
