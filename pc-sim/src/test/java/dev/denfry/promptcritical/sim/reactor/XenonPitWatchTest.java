package dev.denfry.promptcritical.sim.reactor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** ОЗР и эпизод йодной ямы (04c §4c.9, достижение {@code xenon_pit}). */
class XenonPitWatchTest {
    private static final double XE0 = -0.03;

    /** Мощность → провал → ксенон растёт → снова мощность: ровно одно событие. */
    @Test
    void escapeWithMarginFiresOnce() {
        XenonPitWatch w = new XenonPitWatch();
        assertFalse(w.update(1.0, 60, XE0, 0));
        assertFalse(w.update(0.02, 60, XE0, 0));
        assertEquals(XenonPitWatch.Phase.DIPPED, w.phase());
        assertFalse(w.update(0.02, 40, XE0 - 0.01, 0));
        assertEquals(XenonPitWatch.Phase.IN_PIT, w.phase());
        assertFalse(w.update(0.3, 20, XE0 - 0.012, 0));
        assertTrue(w.update(0.6, 18, XE0 - 0.01, 0));
        assertEquals(18, w.minMargin(), 1e-9);
        assertFalse(w.update(0.9, 18, XE0, 0), "one event per episode");
    }

    /** Стержни выведены ниже 15 эквивалентных — эпизод сорван, выход на мощность не засчитан. */
    @Test
    void marginViolationSpoilsTheEpisode() {
        XenonPitWatch w = new XenonPitWatch();
        w.update(1.0, 60, XE0, 0);
        w.update(0.02, 60, XE0, 0);
        w.update(0.02, 40, XE0 - 0.01, 0);
        assertFalse(w.update(0.07, 12, XE0 - 0.01, 0));
        assertTrue(w.spoiled());
        assertFalse(w.update(0.6, 30, XE0 - 0.01, 0), "back at power after a violation is not an escape");
    }

    /** Разрушенный канал во время эпизода тоже срывает его; короткая просадка без ямы — не эпизод. */
    @Test
    void failedChannelOrShallowDipDoesNotCount() {
        XenonPitWatch w = new XenonPitWatch();
        w.update(1.0, 60, XE0, 0);
        w.update(0.02, 60, XE0, 0);
        w.update(0.02, 50, XE0 - 0.01, 0);
        assertFalse(w.update(0.6, 50, XE0 - 0.01, 1));
        XenonPitWatch shallow = new XenonPitWatch();
        shallow.update(1.0, 60, XE0, 0);
        shallow.update(0.05, 60, XE0, 0);
        assertFalse(shallow.update(0.8, 60, XE0 - 0.0005, 0));
        assertEquals(XenonPitWatch.Phase.AT_POWER, shallow.phase());
    }

    @Test
    void stateRoundTrips() {
        XenonPitWatch a = new XenonPitWatch();
        a.update(1.0, 60, XE0, 0);
        a.update(0.02, 33, XE0, 0);
        a.update(0.02, 31, XE0 - 0.01, 0);
        MutableStateTree tree = new MutableStateTree();
        a.save(new TreeStateWriter(tree));
        XenonPitWatch b = new XenonPitWatch();
        b.load(new TreeStateReader(tree));
        assertEquals(XenonPitWatch.Phase.IN_PIT, b.phase());
        assertEquals(31, b.minMargin(), 1e-9);
        assertTrue(b.update(0.7, 30, XE0, 0));
    }

    /** Шкала ОЗР: все рабочие стержни внизу — 180 эквивалентных, вверху — ноль; на номинале запас выше минимума. */
    @Test
    void operationalMarginScale() {
        ChannelReactor r = ChannelReactorTest.full();
        r.setRods(0, 1, 1);
        assertEquals(ChannelReactor.ORM_FULL_RODS, r.operationalMargin(), 1e-6);
        r.setRods(0, 0, 0);
        assertEquals(0, r.operationalMargin(), 1e-9);
        ChannelReactor hot = ChannelReactorTest.hotCritical();
        assertTrue(hot.operationalMargin() > ChannelReactor.ORM_MIN_RODS, "nominal ORM " + hot.operationalMargin());
    }

    /** Реактор на номинале с равновесным ксеноном, затем провал: после провала ксенон растёт, наблюдатель видит яму. */
    @Test
    void realDipEntersThePit() {
        ChannelReactor r = ChannelReactorTest.hotCritical();
        r.setAutoRegulator(true);
        for (int i = 0; i < 20 * 60; i++) r.step(0, ChannelReactorTest.SECONDARY_K);
        assertEquals(XenonPitWatch.Phase.AT_POWER, r.xenonPit().phase());
        // равновесный ксенон номинала: без подъёма РР мощность проваливается
        double f = r.fissionDensity(1), phi = r.flux(1), s = r.xenonSigmaB();
        for (int i = 0; i < 400; i++) r.poisons().step(3600, f, phi, s, dev.denfry.promptcritical.sim.neutronics.FissionPoisons.SIGMA_SM_B);
        for (int i = 0; i < 20 * 120 && r.power() >= XenonPitWatch.DIP; i++) r.step(0, ChannelReactorTest.SECONDARY_K);
        assertEquals(XenonPitWatch.Phase.DIPPED, r.xenonPit().phase(), "power " + r.power());
        r.scram();
        // 3 ч игровой химии ядов при шаге кинетики 0,05 с
        for (int i = 0; i < 3 * 3600; i++) r.step(1, ChannelReactorTest.SECONDARY_K);
        assertEquals(XenonPitWatch.Phase.IN_PIT, r.xenonPit().phase(), "xenon " + r.xenonReactivity());
    }
}
