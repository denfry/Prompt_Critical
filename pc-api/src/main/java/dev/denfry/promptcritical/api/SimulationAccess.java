package dev.denfry.promptcritical.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Read-only access to one dimension's simulation (05 §5.8.1). Addons see the same data a Lua program sees through a
 * device's sensors, and they cannot command machines (16 §16.11.1, N4).
 *
 * <p>A tag is named {@code <device>/<tag>}: {@code device} is the device id the telemetry gateway and the annunciator
 * use ({@code pwr_reactor@12,64,-30}), {@code tag} is the name Lua's {@code listTags()} returns for that device.
 *
 * <p>Every method is called on the server thread; {@link #read} and {@link #tags} throw {@link IllegalStateException}
 * on any other thread.
 */
public interface SimulationAccess {
    /** Current value of {@code tag} at station {@code stationId}; empty if the station, the device or the tag is unknown,
     * the device is not loaded, or the tag has no numeric value. */
    Optional<Double> read(UUID stationId, String tag);

    /** Tags of station {@code stationId} in {@code <device>/<tag>} form; empty if the station is unknown or not loaded. */
    List<String> tags(UUID stationId);

    /** Stations of this dimension that have at least one loaded device. */
    List<UUID> stations();

    /**
     * Receive every {@link ApiEvent} of every dimension. The listener runs on the server thread, in the tick the event
     * happened; an exception thrown by it is logged and does not reach the simulation.
     */
    void subscribeEvents(Consumer<ApiEvent> listener);
}
