package dev.denfry.promptcritical.sim.npp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** БОР-60: петлевой натриевый реактор на 60 МВт(т) с аргоновой подушкой и прямоточным ПГ. */
class Bor60Test {
    private static final SodiumFastReactor.Design D = SodiumFastReactor.BOR60;
    private static final int PER_MIN = (int) (60 / SodiumFastReactor.DT);

    private static SodiumFastReactor hot() {
        SodiumFastReactor r = new SodiumFastReactor(D, 500, 0.25);
        r.hotStart(1.0);
        return r;
    }

    @Test
    void defaultDesignKeepsTheBnConstants() {
        SodiumFastReactor.Design d = SodiumFastReactor.DEFAULT;
        assertThat(d.flowKgS()).isCloseTo(SodiumFastReactor.NOMINAL_FLOW_KG_S, within(1e-9));
        assertThat(d.nominalW()).isEqualTo(SodiumFastReactor.NOMINAL_W);
        assertThat(new SodiumFastReactor(3000, 0.2).design()).isSameAs(d);
    }

    @Test
    void holdsSixtyMegawattsWithSodiumAt530() {
        SodiumFastReactor r = hot();
        for (int k = 0; k < 10 * PER_MIN; k++) r.step();
        assertThat(r.trip()).isEqualTo(SodiumFastReactor.Trip.NONE);
        assertThat(r.power()).isCloseTo(1.0, within(0.05));
        assertThat(r.thermalW()).isBetween(55e6, 66e6);
        assertThat(r.outletK() - 273.15).isCloseTo(530, within(15.0));
        assertThat(r.inletK() - 273.15).isCloseTo(310, within(15.0));
        assertThat(r.steamGeneratorW()).isBetween(50e6, 66e6);
    }

    @Test
    void argonCushionIsNominalHotAndTheGasSystemHoldsItsBand() {
        SodiumFastReactor r = hot();
        r.step();
        assertThat(r.argonPa()).isCloseTo(D.argonNomPa(), within(0.01e6));
        // расхолаживание: натрий сжимается и остывает, подушка падает, газовая система подпитывает аргон
        double kg0 = r.argonKg();
        r.scramButton();
        r.setSteamDemand(D.nominalW());
        for (int k = 0; k < 30 * PER_MIN; k++) r.step();
        assertThat(r.argonPa()).isGreaterThanOrEqualTo(D.argonNomPa() * (1 - SodiumFastReactor.ARGON_BAND) - 0.01e6);
        assertThat(r.argonKg()).as("аргон подпитан").isGreaterThan(kg0);
    }

    @Test
    void waterLeakInTheSteamGeneratorTripsOnHydrogenAndBurstsTheDisc() {
        SodiumFastReactor r = hot();
        r.setSteamGeneratorLeak(0.5);
        boolean burst = false;
        for (int k = 0; k < 10 * PER_MIN && !burst; k++) {
            r.step();
            burst = r.ruptureDiscBurst();
        }
        assertThat(r.trip()).isEqualTo(SodiumFastReactor.Trip.SODIUM_WATER);
        assertThat(r.sodiumWaterW()).isCloseTo(0.5 * SodiumFastReactor.NA_H2O_J_KG, within(1.0));
        assertThat(burst).as("водород поднял давление второго контура до мембраны").isTrue();
    }

    @Test
    void sodiumLeakDropsTheCushionUntilMakeupCatchesUp() {
        SodiumFastReactor r = hot();
        r.step();
        double p0 = r.argonPa();
        r.setPrimaryLeak(5);
        for (int k = 0; k < 2 * PER_MIN; k++) r.step();
        assertThat(r.argonPa()).as("вытекший натрий освободил объём под газ").isLessThan(p0);
        assertThat(r.sodiumFireW()).isPositive();
    }
}
