package dev.denfry.promptcritical.sim.metallurgy;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TimeDomain;

/**
 * Коксохим и домна в мире (08 §8.4–8.5, этап 10 M3): коксовая батарея, домна, воздухонагреватели, обжиговая печь
 * электродов. Агрегаты — класс 1 §8.0.3 (лумпед SLOW, дешёвые), шаг идёт на серверном потоке в FAST-тике с периодом
 * SLOW, как у литейки. Машины на валу (сепаратор, агломашина, пресс, воздуходувка, эксгаустер) — в
 * {@link FoundrySubsystem}.
 */
public final class IronmakingSubsystem extends DeviceSubsystem<IronmakingUnit> {
    public static final String ID = "ironmaking";
    public static final String COKE_OVEN = "promptcritical:coke_oven";
    public static final String BLAST_FURNACE = "promptcritical:blast_furnace_controller";
    public static final String HOT_BLAST_STOVE = "promptcritical:hot_blast_stove";
    public static final String BAKING_FURNACE = "promptcritical:baking_furnace";
    private static final int SLOW_PHASE = 5;

    private SimConfig config;
    private IronmakingConfig ironmaking = IronmakingConfig.defaults();

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected IronmakingUnit create(String deviceType) {
        return switch (deviceType) {
            case COKE_OVEN -> new CokeBatteryUnit();
            case BLAST_FURNACE -> new BlastFurnaceUnit();
            case HOT_BLAST_STOVE -> new HotBlastStoveUnit();
            case BAKING_FURNACE -> new BakingFurnaceUnit();
            default -> null;
        };
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
        ironmaking = cfg.section(IronmakingConfig.class, IronmakingConfig.defaults());
    }

    public IronmakingConfig config() {
        return ironmaking;
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
