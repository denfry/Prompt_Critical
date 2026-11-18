package dev.denfry.promptcritical.sim.npp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Аварийный слой БН: блэкаут с расхолаживанием и без него, разрушение оболочек. */
class SodiumAccidentTest {
    private static final int PER_HOUR = (int) (3600 / SodiumFastReactor.DT);

    private static SodiumFastReactor blackout(int dhrTrains) {
        SodiumFastReactor r = new SodiumFastReactor(3000, 0.2);
        r.hotStart(1.0);
        for (int k = 0; k < 20 * 60; k++) r.step();
        r.setDecayHeatRemovalTrains(dhrTrains);
        // потеря собственных нужд: насосы на выбеге, отбора пара нет, АЗ
        r.setPumpDrive(0, 0);
        r.setSteamDemand(0);
        r.setExternalTrip(true);
        return r;
    }

    @Test
    void blackoutWithAirCoolersStaysBelowCladdingLimits() {
        SodiumFastReactor r = blackout(SodiumFastReactor.DHR_TRAINS);
        double peakOut = 0;
        for (int k = 0; k < 6 * PER_HOUR; k++) {
            r.step();
            peakOut = Math.max(peakOut, r.outletK());
        }
        assertThat(r.shutDown()).isTrue();
        assertThat(r.dhrDampersOpen()).as("заслонки открыла АЗ").isTrue();
        assertThat(r.dhrW()).as("отвод воздухом").isPositive();
        assertThat(peakOut).as("естественная циркуляция держит выход ниже предела оболочки").isLessThan(SodiumFastReactor.CLAD_FAIL_K);
        assertThat(r.claddingFailedFraction()).isZero();
    }

    @Test
    void blackoutWithoutDecayHeatRemovalHeatsThePoolUntilCladdingFails() {
        SodiumFastReactor r = blackout(0);
        double hours = 0;
        while (r.claddingFailedFraction() == 0 && hours < 48) {
            for (int k = 0; k < PER_HOUR; k++) r.step();
            hours++;
        }
        System.out.printf("cladding fails after %.0f h, outlet %.0f K, boiling %.2f%n", hours, r.outletK(), r.boilingFraction());
        assertThat(r.claddingFailedFraction()).as("без расхолаживания остаточное тепло разогревает бассейн").isPositive();
        assertThat(hours).as("часы, а не минуты: бассейн большой").isGreaterThan(2);
    }
}
