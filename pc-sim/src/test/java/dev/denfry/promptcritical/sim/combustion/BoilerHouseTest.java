package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Котельная I0 на гидросети: котёл → барабан → дроссель → машина → вал (07 §7.9.4, §7.9.7). */
class BoilerHouseTest {
    private static final double P = 1.2e6;

    /** Размах величины за {@code seconds} работы. */
    private static double[] swing(BoilerHouse h, double seconds) {
        double rLo = 1e9, rHi = 0, pLo = 1e12, pHi = 0, mean = 0;
        int n = (int) Math.round(seconds / BoilerHouse.DT);
        for (int k = 0; k < n; k++) {
            h.tick();
            rLo = Math.min(rLo, h.engine.revPerS());
            rHi = Math.max(rHi, h.engine.revPerS());
            pLo = Math.min(pLo, h.chest().pressure());
            pHi = Math.max(pHi, h.chest().pressure());
            mean += h.engine.revPerS() / n;
        }
        return new double[] {(rHi - rLo) / mean, (pHi - pLo) / pHi, mean};
    }

    /**
     * Регулятор с катарактом держит обороты в полосе статизма при любой нагрузке: чем меньше нагрузка, тем выше
     * обороты; лишний пар котла уходит через ПК, давление не выше уставки; питательный насос держит уровень.
     */
    @Test
    void governorFollowsDroopAcrossLoads() {
        BoilerHouse h = new BoilerHouse(P, 0.67, 3, FlyballGovernor.withDashpot()).firing(0.26);
        double full = h.loadTorque, w0 = h.engine.nominalOmega() / (2 * Math.PI), prev = 0, pMax = 0;
        for (double f : new double[] {1, 0.75, 0.5, 0.25, 0}) {
            h.loadTorque = f * full;
            for (int k = 0; k < 20 * 100; k++) {
                h.tick();
                pMax = Math.max(pMax, h.drum().pressure());
            }
            double[] s = swing(h, 20);
            assertThat(s[0]).as("размах оборотов, нагрузка %.2f", f).isLessThan(0.002);
            assertThat(s[1]).as("размах давления в коробке").isLessThan(0.01);
            assertThat(s[2] / w0).isBetween(1 - FlyballGovernor.DROOP / 2 - 0.01, 1 + FlyballGovernor.DROOP / 2);
            assertThat(s[2]).isGreaterThan(prev);
            prev = s[2];
            assertThat(h.drum().collapsedLevel()).isBetween(h.spec.normalLevelM() - 0.03, h.spec.normalLevelM() + 0.03);
        }
        assertThat(h.reliefLifts).as("лишний пар на малой нагрузке").isPositive();
        assertThat(pMax).isLessThan(1.02 * h.spec.reliefSetPressure());
    }

    /** Урок Максвелла: тот же контур без катаракта качает обороты, давление в коробке гуляет на десятки процентов. */
    @Test
    void bareGovernorHunts() {
        BoilerHouse bare = new BoilerHouse(P, 0.67, 3, FlyballGovernor.bare()).firing(0.26);
        BoilerHouse damped = new BoilerHouse(P, 0.67, 3, FlyballGovernor.withDashpot()).firing(0.26);
        for (BoilerHouse h : new BoilerHouse[] {bare, damped}) {
            h.loadTorque *= 0.5;
            h.run(100);
        }
        double[] b = swing(bare, 20), d = swing(damped, 20);
        assertThat(b[0]).isGreaterThan(0.01);
        assertThat(b[1]).isGreaterThan(0.3);
        assertThat(d[0]).isLessThan(0.002);
    }

    /** Сброс нагрузки (overstress сети Create): машина не идёт вразнос, давление держит ПК. */
    @Test
    void loadRejection() {
        BoilerHouse h = new BoilerHouse(P, 0.67, 3, FlyballGovernor.withDashpot()).firing(0.3);
        h.run(60);
        h.loadTorque = 0;
        double revMax = 0, pMax = 0;
        for (int k = 0; k < 20 * 600; k++) {
            h.tick();
            revMax = Math.max(revMax, h.engine.revPerS());
            pMax = Math.max(pMax, h.drum().pressure());
        }
        assertThat(revMax / h.engine.spec().nominalRevPerS()).isLessThan(1.06);
        assertThat(h.reliefLifts).isPositive();
        assertThat(pMax).isLessThan(1.03 * h.spec.reliefSetPressure());
        assertThat(h.boiler.rupture()).isEqualTo(BoilerRupture.NONE);
    }

    /**
     * Растопка от атмосферного давления с закрытым паропроводом: 10 т воды от 100 °C до 1,2 МПа при ≈ 5 МВт —
     * ≈ 13 мин (Δu ≈ 378 кДж/кг).
     */
    @Test
    void raisingSteam() {
        BoilerHouse h = new BoilerHouse(BoilerExplosion.ATMOSPHERE_PA, 0.67, 3, FlyballGovernor.withDashpot())
                .firing(0.3);
        h.engineValve = false;
        h.feedPump = false;
        h.loadTorque = 0;
        double t = 0;
        while (h.drum().pressure() < P && t < 3600) {
            h.tick();
            t += BoilerHouse.DT;
        }
        assertThat(h.drum().pressure()).isGreaterThanOrEqualTo(P);
        assertThat(t / 60).as("мин до рабочего давления").isBetween(10.0, 17.0);
    }

    /**
     * Упуск воды (питательный насос стоит): уровень падает до потолка жаровых труб, пробка выплавляется и гасит топку
     * — котёл цел. Без пробки — смятие потолка и взрыв на десятки килограммов ТНТ.
     */
    @Test
    void lowWater() {
        for (boolean plug : new boolean[] {true, false}) {
            BoilerHouse h = new BoilerHouse(P, 0.55, 3, FlyballGovernor.withDashpot()).firing(0.3);
            h.boiler.setPlugFitted(plug);
            h.feedPump = false;
            double t = 0;
            while (!h.boiler.plugMelted() && h.boiler.rupture() == BoilerRupture.NONE && t < 3600) {
                h.tick();
                t += BoilerHouse.DT;
            }
            assertThat(h.out.crownUncovered).isTrue();
            assertThat(t / 60).as("мин без подпитки").isBetween(10.0, 40.0);
            if (plug) {
                assertThat(h.boiler.plugMelted()).isTrue();
                assertThat(h.boiler.isLit()).isFalse();
                h.run(600);
                assertThat(h.boiler.rupture()).isEqualTo(BoilerRupture.NONE);
            } else {
                assertThat(h.boiler.rupture()).isEqualTo(BoilerRupture.CROWN_COLLAPSE);
                double e = BoilerExplosion.energyJ(BoilerHouse.W, h.drum().pressure(), h.drum().mass(),
                        h.drum().quality());
                assertThat(BoilerExplosion.tntEquivalentKg(e, BoilerExplosion.BLAST_FRACTION)).isBetween(15.0, 50.0);
            }
        }
    }
}
