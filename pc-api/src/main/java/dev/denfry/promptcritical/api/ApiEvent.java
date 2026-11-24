package dev.denfry.promptcritical.api;

import java.util.List;
import java.util.UUID;

/**
 * Something that happened at a plant, delivered to {@link SimulationAccess#subscribeEvents} listeners (05 §5.8.1) and
 * mirrored by the KubeJS events {@code PromptCriticalEvents} (16 §16.11.1).
 */
public sealed interface ApiEvent permits ApiEvent.Alarm, ApiEvent.Trip, ApiEvent.SisTrip, ApiEvent.Release,
        ApiEvent.Milestone, ApiEvent.ContractState, ApiEvent.Inspection {
    /** Station id of events that do not belong to one place (contracts are made by a player, not by a plant). */
    UUID NO_STATION = new UUID(0L, 0L);

    /** Dimension id, {@code minecraft:overworld}; empty for events without a place. */
    String dimension();

    /** Station the event happened at, or {@link #NO_STATION}. */
    UUID stationId();

    /** Game time of the dimension (or of the overworld for events without a place), ticks. */
    long gameTime();

    /**
     * An annunciator alarm changed (03c §3c.6.2), the same as Lua's {@code pc_alarm*} events.
     *
     * @param device the annunciator's device id
     * @param type   {@code RAISE}, {@code CLEAR}, {@code ACK}, {@code FLOOD_ON}, {@code FLOOD_OFF}, {@code CHATTER}
     * @param alarm  alarm number on the annunciator; 0 for flood events
     * @param state  ISA-18.2 state after the event
     * @param tag    tag the alarm watches ({@code <device>/<tag>}); empty for flood events
     * @param value  measured value at the event (for floods: alarms in the last 10 minutes)
     */
    record Alarm(String dimension, UUID stationId, long gameTime, String device, String type, int alarm, String state,
            int priority, String tag, String message, double value) implements ApiEvent {
    }

    /**
     * A reactor protection trip (scram) or its reset, the same as Lua's {@code pc_scram} and {@code pc_scram_reset}.
     *
     * @param tripped {@code true} for a trip, {@code false} for its reset
     * @param source  {@code AUTOMATIC} or {@code LUA} for a trip; empty for a reset
     */
    record Trip(String dimension, UUID stationId, long gameTime, String device, boolean tripped, String source)
            implements ApiEvent {
    }

    /** A safety instrumented system trip or reset (03d), the same as Lua's {@code pc_sis_trip} and {@code pc_sis_reset}. */
    record SisTrip(String dimension, UUID stationId, long gameTime, String device, boolean tripped) implements ApiEvent {
    }

    /**
     * A radioactive release to the atmosphere began at a stack or breach: one event per release episode and nuclide,
     * not one per second of release.
     *
     * @param nuclide nuclide name, {@code I131}, {@code CS137}, ...
     * @param bqPerS  release rate at the start, Bq/s (after vent stack filters)
     */
    record Release(String dimension, UUID stationId, long gameTime, String nuclide, double bqPerS, double x, double y,
            double z) implements ApiEvent {
    }

    /**
     * A domain milestone (11 §11.2.1), the event behind the {@code promptcritical:sim_event} advancement trigger.
     *
     * @param event milestone id, {@code promptcritical:engine_running}
     * @param tag   quantity name for the milestone's predicate; may be empty
     */
    record Milestone(String dimension, UUID stationId, long gameTime, String event, String tag, double value)
            implements ApiEvent {
    }

    /**
     * A supply contract changed state (14 §14.6.6).
     *
     * @param from {@code PROPOSED}, {@code ACTIVE}, {@code DEFAULTED}, {@code CLOSED}, {@code CANCELLED}, {@code SUSPENDED}
     */
    record ContractState(String dimension, UUID stationId, long gameTime, long contract, UUID seller,
            String commodity, String from, String to) implements ApiEvent {
    }

    /**
     * A regulator inspection finished (17 §17.4.6), the same as Lua's {@code pc_inspection}.
     *
     * @param rating   inspection rating R, 0..1
     * @param findings finding ids
     */
    record Inspection(String dimension, UUID stationId, long gameTime, String kind, double rating, List<String> findings)
            implements ApiEvent {
        public Inspection {
            findings = List.copyOf(findings);
        }
    }
}
