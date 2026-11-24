package dev.denfry.promptcritical.mod.api;

import dev.denfry.promptcritical.api.ApiEvent;
import dev.denfry.promptcritical.api.SimulationAccess;
import dev.denfry.promptcritical.mod.scada.device.DeviceHub;
import dev.denfry.promptcritical.mod.scada.device.DeviceRuntime;
import dev.denfry.promptcritical.mod.scada.device.DeviceTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * {@link SimulationAccess} over the devices' peripheral snapshots: the tags and values Lua's {@code listTags()} and
 * {@code read()} return (03b §3b.1). {@code dimension == null} looks in every dimension (the KubeJS binding).
 */
public final class SimulationAccessImpl implements SimulationAccess {
    @Nullable
    private final String dimension;

    public SimulationAccessImpl(@Nullable String dimension) {
        this.dimension = dimension;
    }

    private static MinecraftServer server() {
        MinecraftServer s = ServerLifecycleHooks.getCurrentServer();
        if (s == null) throw new IllegalStateException("Prompt Critical API: no server is running");
        if (!s.isSameThread()) throw new IllegalStateException("Prompt Critical API: call it on the server thread");
        return s;
    }

    private List<ServerLevel> levels() {
        List<ServerLevel> out = new ArrayList<>();
        for (ServerLevel l : server().getAllLevels())
            if (dimension == null || l.dimension().location().toString().equals(dimension)) out.add(l);
        return out;
    }

    private List<DeviceRuntime> devices(UUID stationId) {
        List<DeviceRuntime> out = new ArrayList<>();
        if (ApiEvent.NO_STATION.equals(stationId)) return out;
        for (ServerLevel l : levels()) {
            for (BlockEntity be : ApiEvents.stations().devices(l, stationId)) {
                DeviceRuntime r = DeviceHub.runtime(be);
                if (r != null) out.add(r);
            }
        }
        return out;
    }

    @Override
    public Optional<Double> read(UUID stationId, String tag) {
        int slash = tag.indexOf('/');
        if (slash <= 0) return Optional.empty();
        String device = tag.substring(0, slash), name = tag.substring(slash + 1);
        for (DeviceRuntime r : devices(stationId)) {
            if (!r.deviceId().equals(device)) continue;
            DeviceRuntime.Snapshot s = r.sampleNow();
            if (!s.present()) return Optional.empty();
            DeviceTag t = s.tag(name);
            if (t == null) return Optional.empty();
            double v = t.numeric(s.channels());
            return Double.isFinite(v) ? Optional.of(v) : Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    public List<String> tags(UUID stationId) {
        List<String> out = new ArrayList<>();
        for (DeviceRuntime r : devices(stationId)) {
            DeviceRuntime.Snapshot s = r.sampleNow();
            if (!s.present()) continue;
            for (DeviceTag t : s.tags()) out.add(r.deviceId() + "/" + t.name());
        }
        return out;
    }

    @Override
    public List<UUID> stations() {
        List<UUID> out = new ArrayList<>();
        for (ServerLevel l : levels()) out.addAll(ApiEvents.stations().stations(l));
        return out;
    }

    @Override
    public void subscribeEvents(Consumer<ApiEvent> listener) {
        ApiEvents.subscribe(listener);
    }
}
