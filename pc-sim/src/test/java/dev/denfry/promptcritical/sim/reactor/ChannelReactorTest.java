package dev.denfry.promptcritical.sim.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.FissionPoisons;
import dev.denfry.promptcritical.sim.neutronics.Inhour;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import org.junit.jupiter.api.Test;

/** Канальный энергетический реактор T1 (АМ-1) против литературы. */
class ChannelReactorTest {
    static final double SECONDARY_K = 463.15;

    /** 128 каналов по 4,3 кг ТМ при 5 % — около 550 кг урана, как в АМ-1. */
    static ChannelReactor full() {
        ChannelReactor r = new ChannelReactor();
        r.setRods(1, 1, 1);
        for (int i = 0; i < ChannelReactor.CHANNELS; i++) assertTrue(r.load(i, 4.3, 0.05, 0.0004));
        return r;
    }

    /** Горячий критический на номинале: подбор РР, АЗ взведена, расход номинальный. */
    static ChannelReactor hotCritical() {
        ChannelReactor r = full();
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

    /** АЗ и РР внизу на холодном чистом реакторе — подкритичность не меньше 1 %. */
    @Test
    void shutdownMarginCold() {
        ChannelReactor r = full();
        r.equilibrate(1e-9);
        double rho = (r.keffStatic() - 1) / r.keffStatic() + r.rodReactivity();
        assertTrue(rho < -0.01, "cold clean with all rods in: " + rho);
        assertTrue(r.keffStatic() > 1.05, "cold clean core has excess reactivity: " + r.keffStatic());
    }

    /** Номинал АМ-1: 30 МВт, вода 190 → 280 °C, тепло уходит в парогенератор. */
    @Test
    void nominalOperation() {
        ChannelReactor r = hotCritical();
        r.setAutoRegulator(true);
        for (int i = 0; i < 20 * 300; i++) r.step(0, SECONDARY_K);
        assertEquals(1.0, r.power(), 0.03);
        assertEquals(553, r.outletK(), 6);
        assertEquals(463, r.inletK(), 6);
        assertEquals(30e6, r.steamGeneratorW(), 2e6);
        assertEquals(ChannelReactor.Trip.NONE, r.trip());
        assertTrue(r.fuelK() > 650 && r.fuelK() < 760, "fuel " + r.fuelK());
        assertEquals(0, r.failedChannels());
    }

    /**
     * Потеря теплоносителя от слоя аварии (04c §4c.11.2): паровой эффект воды АМ-1 отрицательный, осушение каналов
     * снижает реактивность; разогрев зоны разрушает самые нагруженные каналы.
     */
    @Test
    void coolantLossReducesReactivityAndHotChannelsFailFirst() {
        ChannelReactor r = hotCritical();
        r.step(0, SECONDARY_K);
        double rho0 = r.reactivity();
        r.setCoolantLoss(0.5);
        for (int i = 0; i < 3; i++) r.step(0, SECONDARY_K); // плотность — в конце шага, реактивность — в начале следующего
        assertTrue(r.reactivity() < rho0 - 0.005, "voiding: " + rho0 + " -> " + r.reactivity());
        assertEquals(3, r.failHottest(3));
        assertEquals(3, r.failedChannels());
        assertTrue(r.failed(0), "the central channel carries the most power");
    }

    /** Ступенчатый ввод +0,1β: установившийся период — по уравнению обратных часов с группами решётки. */
    @Test
    void inhourPeriodAfterStep() {
        ChannelReactor r = hotCritical();
        PointKinetics k = new PointKinetics(r.delayedGroups(), r.generationTime(), 1e-6);
        double rho = 0.1 * r.betaEff();
        double n0 = 0;
        for (int i = 0; i < 20 * 120; i++) {
            k.step(0.05, rho, 0);
            if (i == 20 * 100 - 1) n0 = k.power();
        }
        double measured = 20 / Math.log(k.power() / n0);
        double inhour = Inhour.period(rho, r.delayedGroups(), r.generationTime());
        assertEquals(inhour, measured, 0.03 * inhour);
        assertTrue(inhour > 50 && inhour < 120, "+0.1 beta period " + inhour);
    }

    /** Равновесный Xe-135 высокопоточного графитового реактора — 2,5–4 % (Lamarsh, §7.4). */
    @Test
    void equilibriumXenon() {
        ChannelReactor r = hotCritical();
        double x = r.equilibriumXenonReactivity(1);
        assertTrue(x < -0.025 && x > -0.04, "equilibrium xenon " + x);
        assertTrue(r.equilibriumXenonReactivity(0.2) > x, "less xenon at low power");
    }

    /** Йодная яма: после останова с номинала ксенон растёт и проходит максимум через 8–12 ч (Lamarsh, рис. 7.8). */
    @Test
    void iodinePitPeaksAboutTenHoursAfterShutdown() {
        ChannelReactor r = hotCritical();
        FissionPoisons p = new FissionPoisons();
        double f = r.fissionDensity(1), phi = r.flux(1), s = r.xenonSigmaB();
        for (int i = 0; i < 400; i++) p.step(3600, f, phi, s, FissionPoisons.SIGMA_SM_B);
        double xe0 = p.xenon(), best = xe0, bestT = 0;
        for (int t = 1; t <= 48 * 6; t++) {
            p.step(600, 0, 0, s, FissionPoisons.SIGMA_SM_B);
            if (p.xenon() > best) {
                best = p.xenon();
                bestT = t / 6.0;
            }
        }
        assertTrue(bestT >= 8 && bestT <= 12, "peak at " + bestT + " h");
        assertTrue(best > 1.5 * xe0, "pit deeper than equilibrium: " + best / xe0);
    }

    /** Остаточное тепло: ≈ 6 % сразу после останова и ≈ 1–1,6 % через час (Untermyer–Weills; ANS-5.1 ≈ 1,3 %). */
    @Test
    void decayHeatAfterShutdown() {
        assertEquals(0.062, DecayHeat.afterInfiniteOperation(1), 0.006);
        double hour = DecayHeat.afterInfiniteOperation(3600);
        assertTrue(hour > 0.010 && hour < 0.017, "one hour " + hour);
    }

    /** Вода в АМ-1 добавляет реактивность (опыт 1954 г.): нагрев воды контура (меньше плотность) реактивность снижает. */
    @Test
    void hotterWaterReducesReactivity() {
        assertTrue(ChannelReactor.ALPHA_WATER > 0);
        ChannelReactor r = hotCritical();
        r.step(0, SECONDARY_K);
        r.step(0, SECONDARY_K);
        double rho0 = r.reactivity(), d0 = r.waterDensity();
        r.setTemperatures(r.fuelK(), r.graphiteK(), 545);
        r.step(0, SECONDARY_K);
        r.step(0, SECONDARY_K);
        assertTrue(r.waterDensity() < d0 - 40, "water lighter: " + r.waterDensity());
        assertTrue(r.reactivity() < rho0 - 0.002, "reactivity falls: " + r.reactivity() + " from " + rho0);
    }

    /** Потеря расхода: защита глушит реактор, остаточное тепло уносит естественная циркуляция — каналы целы. */
    @Test
    void lossOfFlowWithProtectionIsSafe() {
        ChannelReactor r = hotCritical();
        r.setFlowFraction(0);
        for (int i = 0; i < 20 * 120; i++) r.step(0, SECONDARY_K);
        assertEquals(ChannelReactor.Trip.FLOW, r.trip());
        assertEquals(0, r.failedChannels());
        assertTrue(r.power() < 0.02, "delayed neutron tail: " + r.power());
    }

    /** Та же потеря расхода с выведенными защитами: кризис теплообмена разрушает каналы. */
    @Test
    void lossOfFlowWithoutProtectionBreaksChannels() {
        ChannelReactor r = hotCritical();
        r.setProtection(false);
        r.setFlowFraction(0);
        for (int i = 0; i < 20 * 60; i++) r.step(0, SECONDARY_K);
        assertTrue(r.failedChannels() > 0, "dryout breaks channels");
    }

    /** Быстрый ввод реактивности — АЗ по периоду. */
    @Test
    void shortPeriodTrips() {
        ChannelReactor r = hotCritical();
        r.setRods(0, r.rr() - 0.08, 0.5);
        for (int i = 0; i < 20 * 30 && r.trip() == ChannelReactor.Trip.NONE; i++) r.step(0, SECONDARY_K);
        assertTrue(r.trip() == ChannelReactor.Trip.PERIOD || r.trip() == ChannelReactor.Trip.POWER, "trip " + r.trip());
        for (int i = 0; i < 20 * 30; i++) r.step(0, SECONDARY_K);
        assertTrue(r.power() < 0.05, "shut down: " + r.power());
        assertEquals(1, r.az(), 1e-9);
    }

    /** Выгорание: средняя глубина — энергия на тонну; U-235 — ≈ 1,2 г на МВт·сут; копится Pu-239. */
    @Test
    void burnupBookkeeping() {
        ChannelReactor r = hotCritical();
        double u5Before = 0, hm = 0;
        for (int i = 0; i < ChannelReactor.CHANNELS; i++) {
            u5Before += r.fuel(i).u235Kg();
            hm += r.fuel(i).hm0Kg();
        }
        int days = 10;
        double joules = 0;
        for (int d = 0; d < days * 24; d++) {
            r.equilibrate(1);
            r.step(3600, SECONDARY_K);
            joules += r.fissionW() * 3600;
        }
        double mean = 0, u5 = 0, pu = 0;
        for (int i = 0; i < ChannelReactor.CHANNELS; i++) {
            ChannelReactor.ChannelFuel f = r.fuel(i);
            mean += f.burnupMwdKg() * f.hm0Kg();
            u5 += f.u235Kg();
            pu += f.pu239Kg();
        }
        double mwd = joules / 86400 / 1e6;
        assertTrue(mwd > 10, "energy produced " + mwd);
        assertEquals(mwd / hm, mean / hm, 0.03 * mwd / hm);
        assertEquals(1.23, (u5Before - u5) * 1000 / mwd, 0.1);
        assertTrue(pu > 0, "plutonium bred");
        ChannelReactor.ChannelFuel c = r.fuel(0);
        assertTrue(c.u236Kg() > 0 && c.pu240Kg() > 0 && c.pu240Kg() < c.pu239Kg(), "captures make U-236 and Pu-240");
        assertEquals(c.hm0Kg(), c.heavyMetalKg() + c.fpKg(), 1e-3 * c.hm0Kg());
        assertTrue(r.maxBurnup() > mwd / hm, "central channels burn deeper");
    }

    @Test
    void refuelOnlyWhenShutDown() {
        ChannelReactor r = hotCritical();
        assertTrue(r.unload(0) == null, "no refuelling at power");
        ChannelReactor cold = full();
        cold.step(0, Double.NaN);
        ChannelReactor.ChannelFuel f = cold.unload(0);
        assertTrue(f != null && f.hm0Kg() == 4.3);
        assertTrue(cold.load(0, 4.3, 0.05, 0.0004));
    }

    @Test
    void stateRoundTrips() {
        ChannelReactor a = hotCritical();
        for (int i = 0; i < 100; i++) a.step(3.6, SECONDARY_K);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        ChannelReactor b = new ChannelReactor();
        b.load(new TreeStateReader(tree));
        assertEquals(a.power(), b.power(), 1e-9);
        assertEquals(a.rr(), b.rr(), 1e-12);
        assertEquals(a.poisons().xenon(), b.poisons().xenon(), 1e-6 * Math.max(1, a.poisons().xenon()));
        assertEquals(a.fuel(5).burnupMwdKg(), b.fuel(5).burnupMwdKg(), 1e-12);
        assertEquals(a.loadedChannels(), b.loadedChannels());
    }

    /**
     * Облучательный канал (12 §12.1): поток АМ-1 на номинале — порядка 10¹³…10¹⁴ (у АМ-1 ≈ 5·10¹³), у оси выше, чем на
     * краю; 100 г кобальта в канале — отрицательная реактивность сотни pcm (плата за изотопы), 1 кг — несколько β.
     */
    @Test
    void irradiationChannelFluxAndWorth() {
        ChannelReactor r = hotCritical();
        r.step(0, SECONDARY_K);
        double phi = r.fluxAt(ChannelReactor.EXPERIMENTAL_CHANNEL_R_M);
        assertTrue(phi > 1e13 && phi < 3e14, "flux " + phi);
        assertTrue(r.fluxAt(0) > phi && r.fluxAt(0.7) < phi, "J0 shape");
        assertEquals(0, r.fluxAt(5), 0);
        double before = r.reactivity();
        r.setIrradiationAbsorber(dev.denfry.promptcritical.sim.isotopes.TargetMaterial.COBALT_METAL.absorptionCm2(0.1));
        double worth = r.irradiationWorth();
        assertTrue(worth < -1e-3 && worth > -1e-2, "100 g Co worth " + worth);
        r.step(0, SECONDARY_K);
        assertEquals(before + worth, r.reactivity(), 2e-4);
    }
}
