package dev.denfry.promptcritical.sim.nonferrous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.nonferrous.ReductionPot.State;
import org.junit.jupiter.api.Test;

/** Электролиз алюминия против 08 §8.8.2 и тестов T8.14, T8.17, T8.20. */
class ReductionPotTest {
    /** Ванна в работе: криолит залит, глинозём на уставке, аноды и фториды есть, температура рабочая. */
    private static ReductionPot running() {
        ReductionPot p = new ReductionPot();
        p.addAnodeKg(5000);
        p.addCryoliteKg(ReductionPot.BATH_START_KG + 500);
        p.addAlumina(p.aluminaWanted());
        p.forceState(State.NORMAL, ReductionPot.NOMINAL_K);
        return p;
    }

    @Test
    void t814FaradayAndSpecificEnergy() {
        double kgH = ReductionPot.rateKgS(150e3, 0.93) * 3600;
        assertThat(kgH).isCloseTo(46.8, within(46.8 * 0.01));
        double kwh = ReductionPot.specificEnergyJPerKg(4.3, 0.93) / 3.6e6;
        assertThat(kwh).isCloseTo(13.8, within(13.8 * 0.01));
        assertThat(ReductionPot.cellVoltage(150e3)).isCloseTo(4.3, within(1e-9));
        assertThat(ReductionPot.cellVoltage(150e3) * 150e3 / 1e3).isCloseTo(645.0, within(1.0));
    }

    @Test
    void t817HundredMegawattsMakeSevenTonnesPerRealHour() {
        double tPerH = 100e6 / ReductionPot.specificEnergyJPerKg(4.3, 0.93) * 3600 / 1000;
        assertThat(tPerH).isCloseTo(7.25, within(7.25 * 0.02));
    }

    @Test
    void nominalPotHoldsItsTemperatureAndMakesMetal() {
        ReductionPot p = running();
        for (int i = 0; i < 3600; i++) {
            p.addAlumina(Math.min(p.aluminaWanted(), ReductionPot.FEEDER_KG_S));
            p.step(1, 150e3);
        }
        assertThat(p.state()).isEqualTo(State.NORMAL);
        assertThat(p.tempK()).isCloseTo(ReductionPot.NOMINAL_K, within(1.0));
        assertThat(p.metalKg()).isCloseTo(46.8, within(0.5));
        assertThat(p.specificKwhPerKg()).isCloseTo(13.8, within(0.15));
        // аноды 0,42 т/т, глинозём 1,93 т/т
        assertThat(5000 - p.anodeKg()).isCloseTo(46.8 * 0.42, within(0.5));
    }

    @Test
    void t820PotWithoutCurrentFreezesInThreeToSixMinutes() {
        ReductionPot p = running();
        int s = 0;
        while (s < 60) {
            p.step(1, 0);
            s++;
            assertThat(p.state()).isEqualTo(State.NORMAL);
        }
        while (p.state() != State.FROZEN && s < 3600) {
            p.step(1, 0);
            s++;
            if (p.state() != State.FROZEN) assertThat(p.state()).isEqualTo(State.FREEZING);
        }
        assertThat(s / 60.0).isBetween(3.0, 6.0);
        // ток вернулся — поздно: ванна не проводит, нужен капремонт
        p.step(1, 150e3);
        assertThat(p.state()).isEqualTo(State.FROZEN);
        assertThat(p.lastAmps()).isZero();
        assertThat(p.reline()).isTrue();
        assertThat(p.state()).isEqualTo(State.COLD);
    }

    @Test
    void shortOutageWithinGraceIsHarmless() {
        ReductionPot p = running();
        for (int i = 0; i < 50; i++) p.step(1, 0);
        for (int i = 0; i < 600; i++) {
            p.addAlumina(Math.min(p.aluminaWanted(), ReductionPot.FEEDER_KG_S));
            p.step(1, 150e3);
        }
        assertThat(p.state()).isEqualTo(State.NORMAL);
    }

    @Test
    void starvedPotGoesIntoAnodeEffectAndEmitsPfc() {
        ReductionPot p = running();
        int s = 0;
        while (p.state() == State.NORMAL && s < 36000) {
            p.step(1, 150e3);
            s++;
        }
        assertThat(p.state()).isEqualTo(State.ANODE_EFFECT);
        assertThat(p.aluminaFraction()).isLessThan(ReductionPot.ANODE_EFFECT_ALUMINA);
        p.step(1, 150e3);
        assertThat(p.lastVoltage()).isBetween(20.0, 40.0);
        // наклон IPCC: 0,143 кг CF₄ на т Al на минуту АЭ в сутки ванны → ≈ 0,16 кг CF₄ в минуту
        assertThat(p.lastCf4KgS() * 60).isCloseTo(0.16, within(0.01));
        assertThat(p.lastC2f6KgS() / p.lastCf4KgS()).isCloseTo(0.121, within(1e-9));
        // питание глинозёмом гасит эффект
        p.addAlumina(p.aluminaWanted());
        p.step(1, 150e3);
        assertThat(p.state()).isEqualTo(State.NORMAL);
    }

    @Test
    void coldPotBakesAndStartsOnlyWithCryolite() {
        ReductionPot p = new ReductionPot();
        p.step(1, 150e3);
        assertThat(p.lastAmps()).as("no anodes, no circuit").isZero();
        p.addAnodeKg(5000);
        int s = 0;
        while (p.state() == State.COLD && s < 4 * 3600) {
            p.step(1, 150e3);
            s++;
        }
        assertThat(p.state()).isEqualTo(State.STARTUP);
        for (int i = 0; i < 1800; i++) p.step(1, 150e3);
        assertThat(p.state()).as("hot, but no bath").isEqualTo(State.STARTUP);
        p.addCryoliteKg(ReductionPot.BATH_START_KG);
        p.addAlumina(p.aluminaWanted());
        s = 0;
        while (p.state() == State.STARTUP && s < 3600) {
            p.step(1, 150e3);
            s++;
        }
        assertThat(p.state()).isEqualTo(State.NORMAL);
    }

    @Test
    void wornCathodeFailsAndLosesTheMetal() {
        ReductionPot p = running();
        p.step(ReductionPot.CATHODE_LIFE_AS / 150e3 * 1.01, 150e3);
        assertThat(p.state()).isEqualTo(State.CATHODE_FAILURE);
        assertThat(p.metalKg()).isZero();
        assertThat(p.conducting()).isFalse();
    }
}
