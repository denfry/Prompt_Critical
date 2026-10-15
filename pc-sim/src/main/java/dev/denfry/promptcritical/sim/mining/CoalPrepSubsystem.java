package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;

/** Углеобогащение I0 (08 §8.3): отсадочные машины {@link CoalJig}. */
public final class CoalPrepSubsystem extends DeviceSubsystem<CoalJig> {
    public static final String ID = "coal_prep";

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected CoalJig create(String deviceType) {
        return CoalJig.DEVICE_TYPE.equals(deviceType) ? new CoalJig() : null;
    }

    @Override
    public void stepFast(FastContext ctx) {
        sync(ctx.topology());
        for (int i = 0; i < size(); i++) unitAt(i).step(ctx);
    }
}
