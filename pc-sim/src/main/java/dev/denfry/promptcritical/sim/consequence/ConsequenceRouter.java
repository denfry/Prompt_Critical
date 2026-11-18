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
        /** Global hydrogen deflagration inside a building or containment. */
        HYDROGEN_BURN(6, 4),
        /** Containment overpressure breach: the wall tears, no fireball. */
        CONTAINMENT_BREACH(3, 5);

        final double maxRadiusM;
        final int priority;

        Event(double maxRadiusM, int priority) {
            this.maxRadiusM = maxRadiusM;
            this.priority = priority;
        }
    }

    /** Routing decision: a damage request (null when the event carries no energy) and the release path. */
    public record Routed(DamageRequest damage, ReleasePath release) {}

    /**
     * @param energyJ           mechanical/chemical energy of the event
     * @param containmentIntact whether a closed, unfailed containment surrounds the origin
     * @param releasesActivity  whether the event carries fission products (failed fuel)
     */
    public static Routed route(Event event, long requestId, SimId source, UUID team, GridPos origin,
            double energyJ, boolean containmentIntact, boolean releasesActivity) {
        DamageKind kind = event == Event.CONTAINMENT_BREACH ? DamageKind.OVERPRESSURE_BREACH : DamageKind.BLAST;
        DamageRequest damage = energyJ > 0
                ? new DamageRequest(requestId, source, team, kind, origin, energyJ, event.maxRadiusM, event.priority)
                : null;
        ReleasePath path;
        if (!releasesActivity) path = ReleasePath.NONE;
        else if (containmentIntact && event != Event.CONTAINMENT_BREACH) path = ReleasePath.INSIDE_CONTAINMENT;
        else path = ReleasePath.ENVIRONMENT;
        return new Routed(damage, path);
    }
}
