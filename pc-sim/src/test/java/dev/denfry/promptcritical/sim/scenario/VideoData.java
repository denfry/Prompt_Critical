package dev.denfry.promptcritical.sim.scenario;

import static dev.denfry.promptcritical.sim.scenario.SiteData.f;

import dev.denfry.promptcritical.sim.accident.LossOfCoolantTransient;
import dev.denfry.promptcritical.sim.grid.VideoGrid;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import dev.denfry.promptcritical.sim.reactor.TrigaCore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Real pc-sim runs behind the charts of the reactor video ("more power, more danger"): writes
 * {@code art/film/youtube/reactors/data/*.json} ({@code ./gradlew :pc-sim:videoData}). Every curve comes from the class
 * the game runs, set up the way its unit test sets it up; nothing here is drawn by hand.
 */
public final class VideoData {
    /** Boiler water on the AM-1 secondary side, as in ChannelReactorTest. */
    static final double SECONDARY_K = 463.15;

    private VideoData() {
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        SiteData.write(out, "triga_pulse", trigaPulse());
        SiteData.write(out, "am1_voiding", am1Voiding());
        SiteData.write(out, "am1_xenon", am1Xenon());
        SiteData.write(out, "decay_heat", decayHeat());
        SiteData.write(out, "loca", loca());
        SiteData.write(out, "ufls", VideoGrid.ufls());
    }

    /** $3 pulse of the TRIGA, as TrigaGameTests fires it: critical at 50 W, pulse mode, transient rod ejected. */
    static String trigaPulse() {
        TrigaCore c = new TrigaCore();
        c.reset();
        c.setControlInsertion((TrigaCore.CORE_EXCESS - TrigaCore.TRANSIENT_ROD_WORTH) / TrigaCore.CONTROL_ROD_WORTH);
        c.setSteady(50);
        if (!c.setPulseMode(true) || !c.arm()) throw new IllegalStateException("TRIGA would not arm");
        List<String> pts = new ArrayList<>();
        double dt = TrigaCore.FINE_DT;
        for (int i = 0; i <= 50; i++) {
            if (i == 20 && !c.fire()) throw new IllegalStateException("TRIGA would not fire");
            if (i >= 20) {
                for (int k = 0; k <= 400; k++) {
                    pts.add(f("[%s,%s,%s]", k * dt, c.powerW(), c.fuelK() - 273.15));
                    c.step(dt, TrigaCore.Events.NONE);
                }
                break;
            }
            c.step(0.05, TrigaCore.Events.NONE); // the FAST step the block entity uses
        }
        return f("{\"worthDollars\":%s,\"peakW\":%s,\"energyJ\":%s,\"fwhmS\":%s,\"peakFuelC\":%s,\"fuelDamaged\":%s,"
                        + "\"cols\":[\"t\",\"powerW\",\"fuelC\"],\"rows\":[",
                TrigaCore.TRANSIENT_ROD_WORTH / TrigaCore.BETA, c.pulsePeakW(), c.pulseEnergyJ(), c.pulseFwhmS(),
                c.pulsePeakFuelK() - 273.15, c.fuelDamaged()) + String.join(",", pts) + "]}";
    }

    /** Hot critical AM-1 at nominal power, as ChannelReactorTest.hotCritical: rods searched to zero reactivity. */
    static ChannelReactor am1HotCritical() {
        ChannelReactor r = new ChannelReactor();
        r.setRods(1, 1, 1);
        for (int i = 0; i < ChannelReactor.CHANNELS; i++) r.load(i, 4.3, 0.05, 0.0004);
        r.setFlowFraction(1);
        double lo = 0, hi = 1;
        for (int i = 0; i < 40; i++) {
            double m = (lo + hi) / 2;
            r.setTemperatures(700, 800, 508);
            r.setRods(0, m, 0.5);
            r.equilibrate(1);
            r.resetTrip();
            r.step(0, SECONDARY_K);
            if (r.reactivity() > 0) lo = m;
            else hi = m;
        }
        r.setTemperatures(700, 800, 508);
        r.setRods(0, lo, 0.5);
        r.equilibrate(1);
        r.resetTrip();
        return r;
    }

    /**
     * AM-1 losing its water (04c §4c.8): half the channel water boils off over ten seconds. The water in the channels
     * is an absorber in an over-moderated graphite lattice, so losing it takes reactivity away and power falls on its
     * own, protection or not; the run keeps protection on, as the game does.
     */
    static String am1Voiding() {
        ChannelReactor r = am1HotCritical();
        List<String> pts = new ArrayList<>();
        double dt = ChannelReactor.DT, tripAt = -1;
        for (int i = 0; i <= (int) (60 / dt); i++) {
            double t = i * dt;
            r.setCoolantLoss(Math.max(0, Math.min(0.5, (t - 5) / 20)));
            if (tripAt < 0 && r.trip() != ChannelReactor.Trip.NONE) tripAt = t;
            if (i % 4 == 0) pts.add(f("[%s,%s,%s,%s]", t, r.power(), r.reactivity() / r.betaEff(), r.coolantLoss()));
            r.step(0, SECONDARY_K);
        }
        return f("{\"tripAt\":%s,\"trip\":\"" + r.trip() + "\",\"cols\":[\"t\",\"power\",\"rhoDollars\",\"waterLost\"],\"rows\":[", tripAt)
                + String.join(",", pts) + "]}";
    }

    /**
     * Iodine pit of the AM-1: three days at full power to xenon equilibrium, then shut down; xenon worth over the next
     * sixty hours. Game time runs through {@code step(gameDt, …)}, so ten minutes a step is the poisons' own clock.
     */
    static String am1Xenon() {
        ChannelReactor r = am1HotCritical();
        // Xenon at power (about 1.8 β here) is more than the automatic regulator rod holds, so, as on the game desk,
        // the operator follows it with the manual rods: up while power is low, down while high, 1 % dead band. Poisons
        // move ten game minutes a step and the rods real time, so the operator gets 30 s after each step.
        double gameDt = 600;
        for (int i = 0; i < 72 * 6; i++) {
            r.step(gameDt, SECONDARY_K);
            for (int k = 0; k < 600; k++) {
                r.driveRr(r.power() < 0.99 ? -1 : r.power() > 1.01 ? 1 : 0);
                r.step(0, SECONDARY_K);
            }
        }
        r.driveRr(0);
        if (r.trip() != ChannelReactor.Trip.NONE) throw new IllegalStateException("AM-1 tripped at power: " + r.trip());
        double atPower = r.power(), rrAtPower = r.rr();
        double eq = r.xenonReactivity(), xeEq = r.poisons().xenon();
        r.setExternalTrip(true);
        List<String> pts = new ArrayList<>();
        double peak = eq, peakH = 0;
        for (int i = 0; i <= 60 * 6; i++) {
            double h = i / 6.0, x = r.xenonReactivity();
            if (Math.abs(x) > Math.abs(peak)) {
                peak = x;
                peakH = h;
            }
            pts.add(f("[%s,%s,%s,%s,%s]", h, x * 1e5, r.power(), r.poisons().xenon() / xeEq, r.graphiteK() - 273.15));
            r.step(gameDt, SECONDARY_K);
        }
        return f("{\"powerBeforeTrip\":%s,\"rrBeforeTrip\":%s,\"betaEff\":%s,\"equilibriumPcm\":%s,\"peakPcm\":%s,\"peakH\":%s,\"cols\":[\"h\",\"xenonPcm\",\"power\",\"xeOverEq\",\"graphiteC\"],\"rows\":[",
                atPower, rrAtPower, r.betaEff(), eq * 1e5, peak * 1e5, peakH) + String.join(",", pts) + "]}";
    }

    /** Decay heat after a year at power (Untermyer–Weills, the DecayHeat the game uses), fraction of full power. */
    static String decayHeat() {
        List<String> pts = new ArrayList<>();
        double year = 3.15e7;
        for (double lg = 0; lg <= 7.0001; lg += 0.05) {
            double t = Math.pow(10, lg);
            pts.add(f("[%s,%s,%s]", t, DecayHeat.untermyerWeills(t, year), DecayHeat.afterInfiniteOperation(t)));
        }
        return "{\"cols\":[\"s\",\"afterYear\",\"afterInfinite\"],\"rows\":[" + String.join(",", pts) + "]}";
    }

    /** Double-ended cold-leg break with the ECCS on station power, and the same break in a blackout. */
    static String loca() {
        StringBuilder s = new StringBuilder("{\"limitC\":" + SiteData.g(LossOfCoolantTransient.PCT_LIMIT_K - 273.15) + ",\"runs\":{");
        boolean[] ac = {true, false};
        for (int j = 0; j < ac.length; j++) {
            LossOfCoolantTransient l = LossOfCoolantTransient.doubleEndedColdLeg(4, ac[j]);
            List<String> pts = new ArrayList<>();
            double dt = 0.05;
            for (int i = 0; i <= (int) (600 / dt); i++) {
                if (i % 20 == 0) pts.add(f("[%s,%s,%s,%s]", l.timeS(), l.pressurePa() / 1e6, l.cladK() - 273.15, l.primaryMassKg() / 1e3));
                if (l.cladK() >= LossOfCoolantTransient.CLAD_MELT_K) break;
                l.step(dt);
            }
            s.append(j == 0 ? "" : ",").append(f("\"%s\":{\"peakC\":%s,\"peakAtS\":%s,\"hydrogenKg\":%s,\"cols\":[\"t\",\"pMPa\",\"cladC\",\"waterT\"],\"rows\":[",
                    ac[j] ? "eccs" : "blackout", l.peakCladK() - 273.15, l.peakCladTimeS(), l.hydrogenKg()))
                    .append(String.join(",", pts)).append("]}");
        }
        return s.append("}}").toString();
    }
}
