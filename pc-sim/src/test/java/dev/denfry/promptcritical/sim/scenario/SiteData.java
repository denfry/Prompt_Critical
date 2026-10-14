package dev.denfry.promptcritical.sim.scenario;

import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.DelayedNeutronData;
import dev.denfry.promptcritical.sim.neutronics.Inhour;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.protection.TripReason;
import dev.denfry.promptcritical.sim.protection.TripSource;
import dev.denfry.promptcritical.sim.thermo.SaturationState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Реальные прогоны pc-sim для графиков сайта: пишет site/data/*.json ({@code ./gradlew :pc-sim:siteData}). */
public final class SiteData {
    static final double BETA = 0.0065, GEN = 2e-5;
    static final DelayedGroups U235 = DelayedNeutronData.u235(BETA);

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        write(out, "kinetics", kinetics());
        write(out, "scram", scram());
        write(out, "lof", lossOfFlow());
        write(out, "water", water());
        write(out, "perf", perf());
    }

    static String kinetics() {
        StringBuilder s = new StringBuilder("{\"beta\":" + BETA + ",\"gen\":" + GEN + ",\"inhour\":[");
        double[] dollars = {0.05, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0, 1.05, 1.1, 1.2};
        for (int i = 0; i < dollars.length; i++) {
            double rho = dollars[i] * BETA;
            s.append(i == 0 ? "" : ",").append(f("{\"d\":%s,\"withDelayed\":%s,\"promptOnly\":%s}",
                    dollars[i], Inhour.period(rho, U235, GEN), GEN / rho));
        }
        s.append("],\"curves\":{");
        double[] steps = {0.5, 0.9, 1.1};
        for (int j = 0; j < steps.length; j++) {
            PointKinetics k = new PointKinetics(U235, GEN, 1);
            List<String> pts = new ArrayList<>();
            double dt = 1e-3;
            for (int i = 0; i <= 3000; i++) {
                if (i % 10 == 0) pts.add(f("[%s,%s]", i * dt, k.power()));
                if (k.power() > 1e6) break;
                k.step(dt, steps[j] * BETA, 0);
            }
            s.append(j == 0 ? "" : ",").append(f("\"%s\":[", steps[j])).append(String.join(",", pts)).append("]");
        }
        // Без запаздывающих нейтронов: dn/dt = ρ/Λ·n — та же ступень 0,5$ за 3 с
        s.append(f(",\"promptOnly05\":{\"periodS\":%s,\"powerAt0_1s\":%s}", GEN / (0.5 * BETA),
                Math.exp(0.1 * 0.5 * BETA / GEN)));
        return s.append("}}").toString();
    }

    static String scram() {
        Pr300Plant p = new Pr300Plant();
        double dt = Pr300Plant.DT, tripAt = 5;
        List<String> pts = new ArrayList<>();
        for (int i = 0; i <= (int) (305 / dt); i++) {
            double t = i * dt;
            if (Math.abs(t - tripAt) < dt / 2) p.protection.requestTrip(TripReason.MANUAL, TripSource.OPERATOR_PANEL);
            boolean fine = t < 40, keep = fine ? i % 2 == 0 : i % 20 == 0;
            if (keep) pts.add(f("[%s,%s,%s,%s,%s]", t, p.power(), p.hotLegTemperature() - 273.15,
                    p.pressurizerPressure() / 1e6, decayHeat(t - tripAt)));
            p.tick();
        }
        return f("{\"tripAt\":%s,\"firstOut\":\"%s\",\"cols\":[\"t\",\"fission\",\"hotLegC\",\"pMPa\",\"decay\"],\"rows\":[",
                tripAt, p.protection.firstOut()) + String.join(",", pts) + "]}";
    }

    /** Way–Wigner после года работы на мощности: P/P0 = 0,0622 [t^-0,2 − (t+T0)^-0,2]. Только иллюстрация. */
    static double decayHeat(double t) {
        if (t <= 0) return 0;
        double t0 = 3.15e7;
        return 0.0622 * (Math.pow(Math.max(t, 1), -0.2) - Math.pow(t + t0, -0.2));
    }

    static String lossOfFlow() {
        Pr300Plant p = new Pr300Plant();
        double dt = Pr300Plant.DT, pumpTrip = 5;
        List<String> pts = new ArrayList<>();
        double tripT = -1;
        for (int i = 0; i <= (int) (60 / dt); i++) {
            double t = i * dt;
            if (Math.abs(t - pumpTrip) < dt / 2) p.primary.link(Pr300Plant.PUMP_B).pump().setDrivingTorque(0);
            if (tripT < 0 && p.protection.isTripped()) tripT = t;
            double fa = p.primary.link(Pr300Plant.PUMP_A).massFlow() / Pr300Plant.LOOP;
            double fb = p.primary.link(Pr300Plant.PUMP_B).massFlow() / Pr300Plant.LOOP;
            double d = p.lastDnbr();
            if (i % 2 == 0) pts.add(f("[%s,%s,%s,%s,%s,%s]", t, p.power(), fa, fb,
                    Double.isFinite(d) ? Math.min(d, 9.99) : 9.99, p.hotLegTemperature() - 273.15));
            p.tick();
        }
        return f("{\"pumpTrip\":%s,\"tripAt\":%s,\"firstOut\":\"%s\",\"cols\":[\"t\",\"power\",\"flowA\",\"flowB\",\"dnbr\",\"hotLegC\"],\"rows\":[",
                pumpTrip, tripT, p.protection.firstOut()) + String.join(",", pts) + "]}";
    }

    static String water() {
        WaterProperties w = WaterProperties.tables();
        List<String> pts = new ArrayList<>();
        for (double pm = 0.1; pm <= 22.0001; pm += 0.1) {
            SaturationState s = w.saturationP(pm * 1e6);
            pts.add(f("[%s,%s,%s,%s,%s]", pm, s.tSat() - 273.15, s.rhoF(), s.rhoG(), s.latentHeat() / 1e3));
        }
        return "{\"cols\":[\"pMPa\",\"tSatC\",\"rhoF\",\"rhoG\",\"hfgKJ\"],\"rows\":[" + String.join(",", pts) + "]}";
    }

    static String perf() {
        Pr300Plant p = new Pr300Plant();
        for (int i = 0; i < 20_000; i++) p.tick();
        int n = 20_000;
        long[] ns = new long[n];
        for (int i = 0; i < n; i++) {
            long a = System.nanoTime();
            p.tick();
            ns[i] = System.nanoTime() - a;
        }
        Arrays.sort(ns);
        double mean = Arrays.stream(ns).average().orElse(0) / 1e6;
        return f("{\"ticks\":%d,\"meanMs\":%s,\"p50Ms\":%s,\"p95Ms\":%s,\"p99Ms\":%s,\"budgetMs\":3}", n, mean,
                ns[n / 2] / 1e6, ns[(int) (n * 0.95)] / 1e6, ns[(int) (n * 0.99)] / 1e6);
    }

    static void write(Path dir, String name, String json) throws IOException {
        Files.writeString(dir.resolve(name + ".json"), json + "\n");
        System.out.println(name + ": " + json.length() + " chars");
    }

    static String f(String fmt, Object... a) {
        Object[] r = new Object[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] instanceof Double d ? g(d) : a[i];
        return String.format(Locale.ROOT, fmt.replace("%s", "%s"), r);
    }

    static String g(double d) {
        if (Double.isInfinite(d) || Double.isNaN(d)) return "null";
        return new java.math.BigDecimal(d, new java.math.MathContext(6)).stripTrailingZeros().toString();
    }
}
