package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;

/** Шахтные подъёмы (08 §8.2.8): клеть — FAST, износ каната — в том же шаге. */
public final class HoistSubsystem extends DeviceSubsystem<HoistUnit> {
    public static final String ID = "hoisting";

    private HoistConfig cfg;

    public HoistSubsystem(HoistConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected HoistUnit create(String deviceType) {
        return HoistUnit.DEVICE_TYPE.equals(deviceType) ? new HoistUnit(cfg) : null;
    }

    @Override
    public void stepFast(FastContext ctx) {
        sync(ctx.topology());
        for (int i = 0; i < size(); i++) unitAt(i).step(ctx, idAt(i));
    }

    @Override
    public void onConfigReload(SimConfig config) {
        cfg = config.section(HoistConfig.class, cfg);
        for (int i = 0; i < size(); i++) unitAt(i).setConfig(cfg);
    }
}
