package dev.denfry.promptcritical.sim.utilities;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TimeDomain;

/**
 * Утилиты завода в мире (08 §8.11–8.12): с этапа 10б M3 — воздухоразделительная установка (кислород конвертеру),
 * водоподготовка — этап 11. Шаг SLOW на серверном потоке в FAST-тике, как у коксохима и домны.
 */
public final class UtilitiesSubsystem extends DeviceSubsystem<UtilitiesUnit> {
    public static final String ID = "utilities";
    public static final String AIR_SEPARATOR = "promptcritical:air_separator";
    private static final int SLOW_PHASE = 7;

    private SimConfig config;
    private UtilitiesConfig utilities = UtilitiesConfig.defaults();

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected UtilitiesUnit create(String deviceType) {
        return AIR_SEPARATOR.equals(deviceType) ? new AsuUnit(utilities) : null;
    }

    @Override
    public void stepFast(FastContext ctx) {
        if (ctx.config() != config) onConfigReload(ctx.config());
        sync(ctx.topology());
        double slowDt = ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE) ? ctx.clock().slowDtPhys() : 0;
        for (int i = 0; i < size(); i++) unitAt(i).step(this, ctx, slowDt);
    }

    @Override
    public void onConfigReload(SimConfig cfg) {
        config = cfg;
        utilities = cfg.section(UtilitiesConfig.class, UtilitiesConfig.defaults());
    }

    public UtilitiesConfig config() {
        return utilities;
    }

    @Override
    public void save(StateWriter out) {
        out.beginObject("units");
        super.save(out);
        out.endObject();
    }

    @Override
    public void load(StateReader in) {
        in.beginObject("units");
        super.load(in);
        in.endObject();
    }
}
