package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.TimeDomain;
import dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig;

/**
 * Реакторы мира: пока — графитовый котёл T0 (этап I1). Кинетика идёт каждый FAST-тик на серверном потоке; нодальная
 * задача кладки — на тиках SLOW, она мала (сотни узлов) и не уходит на воркер.
 */
public final class ReactorSubsystem extends DeviceSubsystem<PileUnit> {
    public static final String ID = "reactor";
    public static final String PILE_CONTROLLER = "promptcritical:reactor_controller";
    /** Вехи котла: {@code power_w}, {@code counts_cps} раз в секунду; {@code critical}, {@code scram}, аварии — событием. */
    public static final String PILE_STATE = "promptcritical:pile_state";
    private static final int SLOW_PHASE = 7;

    private final long worldSeed;
    private SimConfig config;
    private PileConfig pileConfig = PileConfig.defaults();
    private NeutronicsConfig neutronics = NeutronicsConfig.defaults();

    public ReactorSubsystem(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    protected PileUnit create(String deviceType) {
        return PILE_CONTROLLER.equals(deviceType) ? new PileUnit(pileConfig, neutronics) : null;
    }

    @Override
    public void stepFast(FastContext ctx) {
        if (ctx.config() != config) onConfigReload(ctx.config());
        sync(ctx.topology());
        boolean slow = ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE);
        for (int i = 0; i < size(); i++) unitAt(i).step(ctx, idAt(i), worldSeed, slow, slow);
    }

    @Override
    public void onConfigReload(SimConfig cfg) {
        config = cfg;
        pileConfig = cfg.section(PileConfig.class, PileConfig.defaults());
        neutronics = cfg.section(NeutronicsConfig.class, NeutronicsConfig.defaults());
    }

    public PileConfig pileConfig() {
        return pileConfig;
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

    @Override
    public void resetToSafeState() {
        for (int i = 0; i < size(); i++) unitAt(i).resetToSafeState();
    }
}
