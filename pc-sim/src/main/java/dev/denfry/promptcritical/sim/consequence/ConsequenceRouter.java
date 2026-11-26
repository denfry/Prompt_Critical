package dev.denfry.promptcritical.sim.consequence;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimId;
import java.util.UUID;

/**
 * R6: the single place where an accident turns into world consequences. Simulators report what
 * happened (a vessel burst, a hydrogen burn, a containment breach); the router decides which
 * {@link DamageRequest} goes to the {@link DamageBudget} and where any radioactive release goes —
 * into the containment volume (dose inside only) or out to the environment.
 */
public final class ConsequenceRouter {
    private ConsequenceRouter() {}

    /** Where fission products released by the event end up. */
    public enum ReleasePath { NONE, INSIDE_CONTAINMENT, ENVIRONMENT }

    /** Kinds of reactor accident event the router knows how to map. */
    public enum Event {
        /** Pressure vessel or primary circuit burst (stored-energy blast). */
        VESSEL_RUPTURE(4, 3),
        /**
         * Global hydrogen deflagration inside a building or containment: the burn loads the room with a slow pressure
         * rise, only a tenth of the chemical energy goes into breaking things (f_blast = 0.1 for a deflagration in a
         * volume, 04c §4c.13, 07 — the furnace explosion of the same kind).
         */
        HYDROGEN_BURN(6, 4, 0.1),
        /** Hydrogen detonation (04c §4c.7.3): the same chemical energy at the Chapman–Jouguet pressure, a sharper blast. */
        HYDROGEN_DETONATION(8, 3),
        /** Containment overpressure breach: the wall tears, no fireball. */
        CONTAINMENT_BREACH(3, 5),
        /**
         * Fuel–coolant interaction (04c §4c.10.4): melt or fragmented fuel hitting water, a confined blast at the core or
         * the reactor cavity.
         */
        STEAM_EXPLOSION(5, 3);

        final double maxRadiusM;
        final int priority;
        /** Share of the event's energy that goes into mechanical damage (the request carries energy × f_blast). */
        final double blastFraction;

        Event(double maxRadiusM, int priority) {
            this(maxRadiusM, priority, 1);
        }

        Event(double maxRadiusM, int priority, double blastFraction) {
            this.maxRadiusM = maxRadiusM;
            this.priority = priority;
            this.blastFraction = blastFraction;
        }
    }

    /** Routing decision: a damage request (null when the event carries no energy) and the release path. */
    public record Routed(DamageRequest damage, ReleasePath release) {}

    /**
     * @param energyJ           mechanical/chemical energy of the event (the request gets it × the event's f_blast)
     * @param containmentIntact whether a closed, unfailed containment surrounds the origin
     * @param releasesActivity  whether the event carries fission products (failed fuel)
     */
    public static Routed route(Event event, long requestId, SimId source, UUID team, GridPos origin,
            double energyJ, boolean containmentIntact, boolean releasesActivity) {
        DamageKind kind = event == Event.CONTAINMENT_BREACH ? DamageKind.OVERPRESSURE_BREACH : DamageKind.BLAST;
        DamageRequest damage = energyJ > 0
                ? new DamageRequest(requestId, source, team, kind, origin, energyJ * event.blastFraction, event.maxRadiusM, event.priority)
                : null;
        ReleasePath path;
        if (!releasesActivity) path = ReleasePath.NONE;
        else if (containmentIntact && event != Event.CONTAINMENT_BREACH) path = ReleasePath.INSIDE_CONTAINMENT;
        else path = ReleasePath.ENVIRONMENT;
        return new Routed(damage, path);
    }
}
