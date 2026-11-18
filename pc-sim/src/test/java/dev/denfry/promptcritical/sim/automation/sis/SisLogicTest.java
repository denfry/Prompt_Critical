package dev.denfry.promptcritical.sim.automation.sis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.automation.sis.SisLogic.Voting;
import org.junit.jupiter.api.Test;

/** ПАЗ (IEC 61511): голосование, отказ в безопасную сторону, защёлка и квитирование, байпас; PFDavg по IEC 61508-6. */
class SisLogicTest {
    static final double DT = 0.05, SP = 16;

    static boolean step(SisLogic s, Voting v, double a, double b, double c) {
        return s.step(DT, true, new double[] {a, b, c}, v, true, SP, false);
    }

    @Test
    void twoOutOfThreeIgnoresOneHighChannelAndTripsOnTwo() {
        SisLogic s = new SisLogic();
        assertThat(step(s, Voting.TWO_OO_THREE, 12, 12, 12)).isTrue();
        assertThat(step(s, Voting.TWO_OO_THREE, 17, 12, 12)).as("one vote is not enough").isTrue();
        assertThat(step(s, Voting.TWO_OO_THREE, 17, 17, 12)).as("two votes trip").isFalse();
        assertThat(s.trips()).isEqualTo(1);
    }

    @Test
    void oneOutOfTwoTripsOnEitherAndTwoOutOfTwoNeedsBoth() {
        SisLogic a = new SisLogic(), b = new SisLogic();
        assertThat(step(a, Voting.ONE_OO_TWO, 12, 17, 12)).isFalse();
        assertThat(step(b, Voting.TWO_OO_TWO, 12, 17, 12)).isTrue();
        assertThat(step(b, Voting.TWO_OO_TWO, 17, 17, 12)).isFalse();
        SisLogic c = new SisLogic();
        assertThat(step(c, Voting.ONE_OO_TWO, 12, 12, 30)).as("third channel is not in a 1oo2 group").isTrue();
    }

    @Test
    void brokenLoopVotesForTripAndDegradesTwoOutOfThree() {
        SisLogic s = new SisLogic();
        assertThat(step(s, Voting.TWO_OO_THREE, 0, 12, 12)).as("one broken loop alone").isTrue();
        assertThat(s.channelFault(0)).isTrue();
        assertThat(step(s, Voting.TWO_OO_THREE, 0, 17, 12)).as("broken loop + one demand trips, as 1oo2").isFalse();
        SisLogic one = new SisLogic();
        assertThat(step(one, Voting.ONE_OO_ONE, 22, 12, 12)).as("over-range is a fault too").isFalse();
    }

    @Test
    void tripLatchesUntilResetWithAllChannelsClear() {
        SisLogic s = new SisLogic();
        step(s, Voting.ONE_OO_ONE, 17, 12, 12);
        assertThat(step(s, Voting.ONE_OO_ONE, 12, 12, 12)).as("does not restart by itself").isFalse();
        assertThat(s.step(DT, true, new double[] {15.9, 12, 12}, Voting.ONE_OO_ONE, true, SP, true)).as("inside the return band").isFalse();
        assertThat(s.step(DT, true, new double[] {12, 12, 12}, Voting.ONE_OO_ONE, true, SP, true)).as("reset when clear").isTrue();
    }

    @Test
    void lowTripProtectsAgainstFallingValue() {
        SisLogic s = new SisLogic();
        assertThat(s.step(DT, true, new double[] {8, 8, 8}, Voting.ONE_OO_ONE, false, 6, false)).isTrue();
        assertThat(s.step(DT, true, new double[] {5, 8, 8}, Voting.ONE_OO_ONE, false, 6, false)).isFalse();
    }

    @Test
    void lossOfPowerTripsAndStaysTripped() {
        SisLogic s = new SisLogic();
        assertThat(step(s, Voting.TWO_OO_THREE, 12, 12, 12)).isTrue();
        assertThat(s.step(DT, false, new double[] {12, 12, 12}, Voting.TWO_OO_THREE, true, SP, false)).isFalse();
        assertThat(step(s, Voting.TWO_OO_THREE, 12, 12, 12)).as("power back, still tripped").isFalse();
        assertThat(s.step(DT, true, new double[] {12, 12, 12}, Voting.TWO_OO_THREE, true, SP, true)).isTrue();
    }

    @Test
    void bypassHoldsTheOutputButCountsItsTime() {
        SisLogic s = new SisLogic();
        s.setBypass(true);
        for (int i = 0; i < 20; i++) assertThat(step(s, Voting.ONE_OO_ONE, 17, 12, 12)).isTrue();
        assertThat(s.state(true)).isEqualTo(SisLogic.State.BYPASSED);
        assertThat(s.bypassSeconds()).isCloseTo(1.0, within(1e-9));
        assertThat(s.trips()).as("a bypassed demand is not a trip").isZero();
        s.setBypass(false);
        assertThat(step(s, Voting.ONE_OO_ONE, 17, 12, 12)).as("bypass off with the demand still there").isFalse();
    }

    @Test
    void pfdAvgFollowsIec61508SimplifiedEquations() {
        double lambda = 1e-6, ti = 8760, beta = 0.1; // λ_DU 1e-6 1/ч, испытания раз в год
        double p1 = SisLogic.pfdAvg(Voting.ONE_OO_ONE, lambda, ti, beta);
        assertThat(p1).isCloseTo(4.38e-3, within(1e-5));
        assertThat(SisLogic.sil(p1)).isEqualTo(2);
        double p12 = SisLogic.pfdAvg(Voting.ONE_OO_TWO, lambda, ti, beta);
        assertThat(p12).isCloseTo(Math.pow(0.9 * 8.76e-3, 2) / 3 + 0.1 * 8.76e-3 / 2, within(1e-9));
        assertThat(SisLogic.sil(p12)).isEqualTo(3);
        assertThat(SisLogic.pfdAvg(Voting.TWO_OO_TWO, lambda, ti, beta)).as("2oo2 is worse than 1oo1").isGreaterThan(p1);
        double p23 = SisLogic.pfdAvg(Voting.TWO_OO_THREE, lambda, ti, beta);
        assertThat(p23).isBetween(p12, p1);
        assertThat(SisLogic.pfdAvg(Voting.TWO_OO_THREE, lambda, ti, 0)).as("without common cause, redundancy pays off").isLessThan(1e-4);
    }
}
