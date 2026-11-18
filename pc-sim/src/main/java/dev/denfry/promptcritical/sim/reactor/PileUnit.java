package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.DeviceSubsystem;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimRandom;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Двойник пульта графитового котла {@code reactor_controller} в {@link ReactorSubsystem}: {@link GraphitePile} и
 * мост его событий в журнал и вехи мира ({@link ReactorSubsystem#PILE_STATE}).
 */
public final class PileUnit implements DeviceSubsystem.Unit {
    /** Поток ГСЧ счётчиков (§5.2.4). */
    static final int COUNTER_STREAM = 41;

    private final GraphitePile pile;
    private SplittableRandom random;
    private SimId id = SimId.NONE;
    private boolean scramRequested;

    PileUnit(PileConfig cfg, NeutronicsConfig neutronics) {
        pile = new GraphitePile(cfg, neutronics);
    }

    @Override
    public String deviceType() {
        return ReactorSubsystem.PILE_CONTROLLER;
    }

    public GraphitePile pile() {
        return pile;
    }

    /** Кладка от биндинга (сканер мира). */
    public void form(PileLayout layout) {
        pile.form(layout);
    }

    void step(FastContext ctx, SimId self, long worldSeed, boolean slow, boolean sample) {
        if (random == null || !self.equals(id)) {
            id = self;
            random = SimRandom.of(worldSeed, self, COUNTER_STREAM);
        }
        Bridge bridge = new Bridge(ctx, self);
        if (scramRequested) {
            scramRequested = false;
            pile.scram(bridge);
        }
        pile.step(slow, random, bridge);
        if (sample && pile.layout().nodeCount() > 0) {
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "power_w", pile.powerW());
            if (pile.detectorCount() > 0)
                ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "counts_cps", pile.countRate(0));
        }
    }

    /** Кнопка АЗ в зале или на пульте: сработает в начале следующего тика, с записью в журнал. */
    public void scram() {
        scramRequested = true;
    }

    @Override
    public void save(StateWriter out) {
        pile.save(out);
    }

    @Override
    public void load(StateReader in) {
        pile.load(in);
    }

    void resetToSafeState() {
        pile.resetToSafeState();
    }

    private record Bridge(FastContext ctx, SimId self) implements PileEvents {
        @Override
        public void tripped(PileTrip reason, double powerW, double periodS) {
            ctx.events().journal(ctx.tick(), self, Severity.WARNING, String.format(Locale.ROOT,
                    "pile scram %s at %.3g W, period %.1f s", reason.name().toLowerCase(Locale.ROOT), powerW, periodS));
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "scram", reason.ordinal());
        }

        @Override
        public void critical(double powerW) {
            ctx.events().journal(ctx.tick(), self, Severity.INFO, String.format(Locale.ROOT,
                    "pile self-sustaining at %.3g W", powerW));
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "critical", powerW);
        }

        @Override
        public void approachPoints(int points) {
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "one_over_m", points);
        }

        @Override
        public void cladFailed(double fuelTempK) {
            ctx.events().journal(ctx.tick(), self, Severity.CRITICAL, String.format(Locale.ROOT,
                    "pile slug cladding failed at %.0f K", fuelTempK));
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "clad_failed", fuelTempK);
        }

        @Override
        public void graphiteFire(double graphiteTempK) {
            ctx.events().journal(ctx.tick(), self, Severity.CRITICAL, String.format(Locale.ROOT,
                    "pile graphite fire at %.0f K", graphiteTempK));
            ctx.events().milestone(ctx.tick(), self, ReactorSubsystem.PILE_STATE, "graphite_fire", graphiteTempK);
        }
    }
}
