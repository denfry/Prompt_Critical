package dev.denfry.promptcritical.mod.api;

import dev.denfry.promptcritical.api.ApiEvent;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.alarm.Alarm;
import dev.denfry.promptcritical.sim.alarm.AlarmEvent;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.economy.SupplyContract;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Event bus of the addon API (05 §5.8.1): the mod's alarm, trip, release, milestone and contract paths call the helpers
 * here, and they turn into {@link ApiEvent}s for the listeners. Nothing is built while nobody listens. Server thread.
 */
public final class ApiEvents {
    private record Listener(Consumer<ApiEvent> sink, BooleanSupplier wants) {
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile StationDirectory stations = new DeviceStations();

    private ApiEvents() {
    }

    public static void subscribe(Consumer<ApiEvent> listener) {
        subscribe(listener, () -> true);
    }

    /**
     * Subscribe a listener that is only sometimes interested: the KubeJS plugin is installed for good, but costs the
     * device probes nothing while no script listens ({@code wants} is false).
     */
    public static void subscribe(Consumer<ApiEvent> listener, BooleanSupplier wants) {
        LISTENERS.add(new Listener(Objects.requireNonNull(listener, "listener"), Objects.requireNonNull(wants, "wants")));
    }

    /** Someone listens: the producers skip building events otherwise. */
    public static boolean active() {
        for (Listener l : LISTENERS) if (l.wants().getAsBoolean()) return true;
        return false;
    }

    public static StationDirectory stations() {
        return stations;
    }

    /** Replace the station model (04d §4d.9): called once by the station implementation when it is registered. */
    public static void stations(StationDirectory directory) {
        stations = Objects.requireNonNull(directory, "directory");
    }

    static void publish(ApiEvent e) {
        for (Listener l : LISTENERS) {
            try {
                l.sink().accept(e);
            } catch (RuntimeException ex) {
                PromptCritical.LOGGER.error("Prompt Critical API listener {} failed on {}", l.sink(), e, ex);
            }
        }
    }

    private static String dim(Level level) {
        return level.dimension().location().toString();
    }

    private static UUID station(ServerLevel level, BlockPos pos) {
        return stations.stationOf(level, pos);
    }

    // ------------------------------------------------------------------ producers

    /** An event a device probe sent to its CC computers ({@code pc_scram}, {@code pc_sis_trip}, ...). */
    public static void device(BlockEntity be, String deviceId, String event, Map<String, Object> payload) {
        if (!active() || !(be.getLevel() instanceof ServerLevel level)) return;
        String dim = dim(level);
        UUID st = station(level, be.getBlockPos());
        long t = level.getGameTime();
        switch (event) {
            case "pc_scram" -> publish(new ApiEvent.Trip(dim, st, t, deviceId, true, String.valueOf(payload.getOrDefault("source", ""))));
            case "pc_scram_reset" -> publish(new ApiEvent.Trip(dim, st, t, deviceId, false, ""));
            case "pc_sis_trip" -> publish(new ApiEvent.SisTrip(dim, st, t, deviceId, true));
            case "pc_sis_reset" -> publish(new ApiEvent.SisTrip(dim, st, t, deviceId, false));
            default -> {
                // остальные события устройств — только для Lua
            }
        }
    }

    /** An event of an annunciator's alarm engine; {@code alarm} is the alarm it concerns, {@code null} for floods. */
    public static void alarm(BlockEntity annunciator, String deviceId, AlarmEvent e, @Nullable Alarm alarm) {
        if (!active() || !(annunciator.getLevel() instanceof ServerLevel level)) return;
        int priority = alarm == null ? 0 : alarm.definition().priority();
        String tag = alarm == null ? "" : alarm.definition().tag();
        String message = alarm == null ? "" : alarm.definition().message();
        publish(new ApiEvent.Alarm(dim(level), station(level, annunciator.getBlockPos()), level.getGameTime(), deviceId,
                e.type().name(), e.id(), e.state().name(), priority, tag, message, e.value()));
    }

    /** A release to the atmosphere started at {@code at} (the first second of an episode, after vent filters). */
    public static void release(ServerLevel level, Vec3 at, String nuclide, double bqS) {
        if (!active()) return;
        publish(new ApiEvent.Release(dim(level), station(level, BlockPos.containing(at)), level.getGameTime(), nuclide, bqS,
                at.x, at.y, at.z));
    }

    /** A domain milestone; {@code at} is the source device's position, {@code null} if it has no block. */
    public static void milestone(ServerLevel level, @Nullable BlockPos at, SimEvent.Milestone m) {
        if (!active()) return;
        UUID st = at == null ? ApiEvent.NO_STATION : station(level, at);
        publish(new ApiEvent.Milestone(dim(level), st, level.getGameTime(), m.event(), m.tag() == null ? "" : m.tag(), m.value()));
    }

    /** A supply contract went from {@code before}'s state to {@code after}'s; nothing if the state did not change. */
    public static void contract(SupplyContract before, SupplyContract after) {
        if (!active() || before.state() == after.state()) return;
        publish(new ApiEvent.ContractState("", ApiEvent.NO_STATION, overworldTime(), after.id(), after.seller(), after.commodityId(),
                before.state().name(), after.state().name()));
    }

    /** A regulator inspection finished (17 §17.4.6). The inspection itself is not implemented yet; it will call this. */
    public static void inspection(ServerLevel level, BlockPos at, String kind, double rating, List<String> findings) {
        if (!active()) return;
        publish(new ApiEvent.Inspection(dim(level), station(level, at), level.getGameTime(), kind, rating, findings));
    }

    private static long overworldTime() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? 0 : server.overworld().getGameTime();
    }
}
