package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;

/** Двойник установки утилит (08 §8.11–8.12) в {@link UtilitiesSubsystem}. */
public interface UtilitiesUnit extends DeviceSubsystem.Unit {
    /**
     * Шаг FAST.
     *
     * @param slowDt &gt; 0 — в этом тике и шаг SLOW, физ. с
     */
    void step(UtilitiesSubsystem utilities, FastContext ctx, double slowDt);
}
