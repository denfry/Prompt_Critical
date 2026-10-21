package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;

/** Двойник агрегата коксохима и домны (08 §8.4–8.5) в {@link IronmakingSubsystem}. */
public interface IronmakingUnit extends DeviceSubsystem.Unit {
    /**
     * Шаг FAST.
     *
     * @param slowDt &gt; 0 — в этом тике и шаг SLOW, физ. с
     */
    void step(IronmakingSubsystem works, FastContext ctx, double slowDt);
}
