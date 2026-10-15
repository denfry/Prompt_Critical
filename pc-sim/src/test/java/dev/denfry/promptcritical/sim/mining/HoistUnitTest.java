package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Подъёмная машина с панелью посадок (08 §8.2.8): тахограмма, предел мощности, защиты, сохранение. */
class HoistUnitTest {
    private static final double DT = 0.05;

    private static HoistUnit unit(double ropeMm) {
        HoistUnit u = new HoistUnit(HoistConfig.defaults());
        u.setShaft(new double[] {0, 40, 100});
        u.installRope(RopeState.fresh(ropeMm, 1770, RopeConstruction.R6X19_FC));
        return u;
    }

    /** Шагать, пока клеть не встанет на посадку; возвращает время, с. */
    private static double runToStop(HoistUnit u, double powerW, double maxS) {
        double t = 0;
        do {
            u.step(DT, powerW);
            t += DT;
        } while ((u.target() >= 0 || Math.abs(u.speedMs()) > 0) && t < maxS);
        return t;
    }

    @Test
    void notReadyWithoutRopeOrShaft() {
        HoistUnit u = new HoistUnit(HoistConfig.defaults());
        assertThat(u.ready()).isFalse();
        u.installRope(RopeState.fresh(36, 1770, RopeConstruction.R6X19_FC));
        assertThat(u.ready()).isFalse();
        u.setShaft(new double[] {0, 100});
        assertThat(u.ready()).isTrue();
        assertThat(u.hoist().spec().depthM()).isEqualTo(100);
    }

    /** Спуск на 100 м и подъём обратно: клеть встаёт на посадку с точностью 2 см, скорость не выше предела. */
    @Test
    void tachogramReachesLandingsAndStops() {
        HoistUnit u = unit(36);
        u.setSpeedLimit(8);
        assertThat(u.depart(2, HoistDuty.CARGO, 2000)).isEqualTo(Hoist.Verdict.ALLOWED);
        double vMax = 0, t = 0;
        while ((u.target() >= 0 || u.speedMs() != 0) && t < 120) {
            u.step(DT, HoistUnit.RATED_W);
            vMax = Math.max(vMax, Math.abs(u.speedMs()));
            t += DT;
        }
        assertThat(u.positionM()).isCloseTo(100, within(HoistUnit.ARRIVAL_M));
        assertThat(u.lastLanding()).isEqualTo(2);
        assertThat(u.landingAtCage()).isEqualTo(2);
        assertThat(vMax).isBetween(7.5, 8.0);
        // 100 м при 8 м/с и 1 м/с²: 8 с разгона, 4,5 с хода, 8 с замедления ≈ 20,5 с
        assertThat(t).isBetween(19.0, 26.0);
        assertThat(u.hoist().state()).isEqualTo(HoistState.HELD);

        assertThat(u.depart(0, HoistDuty.CARGO, 2000)).isEqualTo(Hoist.Verdict.ALLOWED);
        runToStop(u, HoistUnit.RATED_W, 120);
        assertThat(u.positionM()).isCloseTo(0, within(HoistUnit.ARRIVAL_M));
        // стоит на тормозе
        for (int i = 0; i < 100; i++) u.step(DT, HoistUnit.RATED_W);
        assertThat(u.positionM()).isCloseTo(0, within(HoistUnit.ARRIVAL_M));
    }

    /** Слабый вал: скорость подъёма ограничена мощностью v ≈ 0,9P/(mg), привод не берёт больше P. */
    @Test
    void weakShaftLimitsHoistingSpeed() {
        HoistUnit u = unit(36);
        u.depart(2, HoistDuty.CARGO, 0);
        runToStop(u, HoistUnit.RATED_W, 120);
        u.depart(0, HoistDuty.CARGO, 3000);
        double p = 100_000, vMax = 0, pMax = 0;
        for (int i = 0; i < 400; i++) {
            u.step(DT, p);
            vMax = Math.max(vMax, u.speedMs());
            pMax = Math.max(pMax, u.powerW() - HoistUnit.IDLE_SHARE * HoistUnit.RATED_W);
        }
        double m = u.hoist().spec().cageMassKg() + 3000 + u.rope().massPerMetreKg() * 100;
        assertThat(vMax).isLessThanOrEqualTo(0.9 * p / (m * Hoist.G) + 0.05);
        assertThat(pMax).isLessThanOrEqualTo(p * 1.001);
    }

    /** Без вала стоящая клеть ждёт на тормозе; потеря вала на ходу — защита и тормоз. */
    @Test
    void powerLossTripsMovingCage() {
        HoistUnit u = unit(36);
        u.depart(2, HoistDuty.CARGO, 1000);
        for (int i = 0; i < 20; i++) u.step(DT, 0);
        assertThat(u.positionM()).isZero();
        assertThat(u.target()).isEqualTo(2);
        for (int i = 0; i < 100; i++) u.step(DT, HoistUnit.RATED_W);
        assertThat(u.speedMs()).isNegative();
        u.step(DT, 0);
        assertThat(u.hoist().state()).isEqualTo(HoistState.TRIP_POWER_LOSS);
        assertThat(u.target()).isEqualTo(-1);
        for (int i = 0; i < 200; i++) u.step(DT, 0);
        assertThat(u.speedMs()).isZero();
        assertThat(u.depart(2, HoistDuty.CARGO, 1000)).isNull();
        u.reset();
        assertThat(u.depart(2, HoistDuty.CARGO, 1000)).isEqualTo(Hoist.Verdict.ALLOWED);
    }

    /** T8.6 на панели: тонкий канат не пускает людей в {@code ENFORCED}. */
    @Test
    void enforcedSafetyFactorRefusesPeople() {
        HoistUnit u = unit(28);
        assertThat(u.depart(1, HoistDuty.PEOPLE, 3200)).isEqualTo(Hoist.Verdict.FORBIDDEN);
        assertThat(u.target()).isEqualTo(-1);
        assertThat(u.depart(1, HoistDuty.CARGO, 3200)).isEqualTo(Hoist.Verdict.ALLOWED);
    }

    /** Ствол изменился: канат и глубина клети переносятся. */
    @Test
    void shaftChangeKeepsRopeAndCage() {
        HoistUnit u = unit(36);
        u.depart(1, HoistDuty.CARGO, 0);
        runToStop(u, HoistUnit.RATED_W, 120);
        double damage = u.rope().damage();
        assertThat(damage).isPositive();
        u.setShaft(new double[] {0, 40, 150});
        assertThat(u.hoist().spec().depthM()).isEqualTo(150);
        assertThat(u.positionM()).isCloseTo(40, within(HoistUnit.ARRIVAL_M));
        assertThat(u.rope().damage()).isEqualTo(damage);
    }

    @Test
    void saveLoadRoundTrip() {
        HoistUnit u = unit(36);
        u.setSpeedLimit(6);
        u.depart(2, HoistDuty.MIXED, 1500);
        for (int i = 0; i < 150; i++) u.step(DT, HoistUnit.RATED_W);
        TreeStateWriter w = new TreeStateWriter();
        u.save(w);
        MutableStateTree tree = w.root();

        HoistUnit v = new HoistUnit(HoistConfig.defaults());
        v.load(new TreeStateReader(tree));
        assertThat(v.positionM()).isEqualTo(u.positionM());
        assertThat(v.speedMs()).isEqualTo(u.speedMs());
        assertThat(v.target()).isEqualTo(2);
        assertThat(v.speedLimitMs()).isEqualTo(6);
        assertThat(v.rope()).isEqualTo(u.rope());
        assertThat(v.hoist().payloadKg()).isEqualTo(1500);
        assertThat(v.hoist().duty()).isEqualTo(HoistDuty.MIXED);
        for (int i = 0; i < 50; i++) {
            u.step(DT, HoistUnit.RATED_W);
            v.step(DT, HoistUnit.RATED_W);
        }
        assertThat(v.positionM()).isEqualTo(u.positionM());
    }

    /** Длина каната на ствол — глубина плюс подвеска. */
    @Test
    void ropeLength() {
        assertThat(HoistUnit.ropeLengthM(100, 25)).isEqualTo(125);
    }
}
