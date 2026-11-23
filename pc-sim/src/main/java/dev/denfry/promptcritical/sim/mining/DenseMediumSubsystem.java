package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;

/** Тяжелосредное обогащение угля I1 (08 §8.3): сепараторы {@link DenseMediumSeparator}. */
public final class DenseMediumSubsystem extends DeviceSubsystem<DenseMediumSeparator> {
    public static final String ID = "dense_medium";

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected DenseMediumSeparator create(String deviceType) {
        return DenseMediumSeparator.DEVICE_TYPE.equals(deviceType) ? new DenseMediumSeparator() : null;
    }

    @Override
    public void stepFast(FastContext ctx) {
        sync(ctx.topology());
        for (int i = 0; i < size(); i++) unitAt(i).step(ctx);
    }
}
