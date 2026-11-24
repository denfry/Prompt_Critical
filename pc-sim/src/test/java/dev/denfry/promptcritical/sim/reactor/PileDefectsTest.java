package dev.denfry.promptcritical.sim.reactor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

/**
 * План HP2.3: монтажные дефекты защиты котла. Медленная защита ({@code TRIP_SLOW}) срабатывает позже на своё
 * запаздывание, перепутанный канал контроля потока ({@code LOOP_MISWIRED}) занижает мощность — и прибор, и защита.
 */
class PileDefectsTest {
    /** Котёл, разгоняющийся без стержней, в момент срабатывания защиты; иначе — через 600 с. */
    private static GraphitePile runToTrip(double delayS, double loopError) {
        GraphitePile p = Piles.pile(Piles.cube(5, 1, 8, false, false, true));
        p.setDefects(delayS, loopError);
        SplittableRandom r = new SplittableRandom(42);
        int n = (int) Math.round(600 / GraphitePile.DT);
        for (int i = 0; i < n && !p.tripped(); i++) p.step(i % 20 == 0, r, PileEvents.NONE);
        assertThat(p.trip()).isEqualTo(PileTrip.HIGH_POWER);
        return p;
    }

    private static double tripAt(double delayS, double loopError) {
        GraphitePile p = Piles.pile(Piles.cube(5, 1, 8, false, false, true));
        p.setDefects(delayS, loopError);
        SplittableRandom r = new SplittableRandom(42);
        int n = (int) Math.round(600 / GraphitePile.DT);
        for (int i = 0; i < n; i++) {
            p.step(i % 20 == 0, r, PileEvents.NONE);
            if (p.tripped()) return (i + 1) * GraphitePile.DT;
        }
        return Double.NaN;
    }

    @Test
    void slowProtectionTripsLateByItsDelay() {
        double sound = tripAt(0, 0);
        assertThat(sound).isFinite();
        assertThat(tripAt(5, 0)).isCloseTo(sound + 5, org.assertj.core.data.Offset.offset(2 * GraphitePile.DT));
    }

    /** Защита видит мощность через канал: с занижением на 40 % срабатывает, только когда истинная выше уставки / 0,6. */
    @Test
    void miswiredLoopReadsLowAndTripsLater() {
        double tripW = PileConfig.defaults().powerTripW();
        GraphitePile p = runToTrip(0, 0.4);
        assertThat(p.powerW()).isGreaterThan(tripW / 0.6);
        assertThat(p.indicatedPowerW()).isCloseTo(0.6 * p.powerW(), org.assertj.core.data.Offset.offset(1e-6 * p.powerW()));
        assertThat(tripAt(0, 0.4)).isGreaterThanOrEqualTo(tripAt(0, 0));
    }
}
