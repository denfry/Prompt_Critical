package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.ops.FireSuppression.Agent;
import dev.denfry.promptcritical.sim.ops.FireSuppression.Effect;
import dev.denfry.promptcritical.sim.ops.FireSuppression.FireClass;
import org.junit.jupiter.api.Test;

/** Пожаротушение 13 §13.5: примеры раздела и таблица классов. */
class FireSuppressionTest {
    /** §13.5.2: 1 МВт под потолком 6 м — ΔT 85 K, u 5,3 м/с; спринклер RTI 100, колба 68 °C при 20 °C — ≈ 36 с. */
    @Test
    void sprinklerUnderAOneMegawattFireOpensInAboutThirtySixSeconds() {
        assertThat(FireSuppression.ceilingJetRiseK(1000, 6)).isCloseTo(85, within(1.0));
        assertThat(FireSuppression.ceilingJetVelocity(1000, 6)).isCloseTo(5.3, within(0.05));
        FireSuppression.Element bulb = new FireSuppression.Element(100, 341.15, false, 293.15);
        double t = 0;
        while (!bulb.step(0.05, 1000, 6, 293.15)) t += 0.05;
        assertThat(t).isCloseTo(36, within(2.0));
    }

    /** §13.5.4: резервуар ⌀20 м с бензином — ≈ 530 МВт. */
    @Test
    void gasolineTankFireReleasesAboutFiveHundredMegawatts() {
        double q = FireSuppression.poolFireKw(0.055, 43.7e6, Math.PI * 100);
        assertThat(q / 1e3).isCloseTo(530, within(10.0));
    }

    @Test
    void foamPutsOutOnlyAboveTheMinimumRate() {
        assertThat(FireSuppression.foamExtinctionS(4.0)).isInfinite();
        assertThat(FireSuppression.foamExtinctionS(4.1)).isCloseTo(300, within(1e-9));
        assertThat(FireSuppression.foamExtinctionS(8.2)).isCloseTo(150, within(1e-9));
    }

    @Test
    void fastFireGrowsAsTimeSquaredUpToItsFuel() {
        assertThat(FireSuppression.growthKw(100, 5000)).isCloseTo(470, within(1e-9));
        assertThat(FireSuppression.growthKw(1000, 5000)).isEqualTo(5000);
    }

    /** §13.5.1: вода на металл и на щит под напряжением — опасно; порошок D — только на металлы. */
    @Test
    void agentsFollowTheFireClassTable() {
        assertThat(FireSuppression.effect(FireClass.A, Agent.WATER, false)).isEqualTo(Effect.EFFECTIVE);
        assertThat(FireSuppression.effect(FireClass.B, Agent.WATER, false)).isEqualTo(Effect.INEFFECTIVE);
        assertThat(FireSuppression.effect(FireClass.B, Agent.FOAM, false)).isEqualTo(Effect.EFFECTIVE);
        assertThat(FireSuppression.effect(FireClass.D, Agent.WATER, false)).isEqualTo(Effect.DANGEROUS);
        assertThat(FireSuppression.effect(FireClass.D, Agent.POWDER_D, false)).isEqualTo(Effect.EFFECTIVE);
        assertThat(FireSuppression.effect(FireClass.E, Agent.WATER, true)).isEqualTo(Effect.DANGEROUS);
        assertThat(FireSuppression.effect(FireClass.E, Agent.CO2, true)).isEqualTo(Effect.EFFECTIVE);
        assertThat(FireSuppression.effect(FireClass.A, Agent.WATER, true)).isEqualTo(Effect.DANGEROUS);
    }

    @Test
    void heatDetectorAlsoTripsOnTheRateOfRise() {
        FireSuppression.Element fixed = new FireSuppression.Element(50, 341.15, false, 293.15);
        FireSuppression.Element ror = new FireSuppression.Element(50, 341.15, true, 293.15);
        double t = 0;
        while (!ror.activated() && t < 300) {
            ror.step(1, FireSuppression.growthKw(t, 2000), 4, 293.15);
            fixed.step(1, FireSuppression.growthKw(t, 2000), 4, 293.15);
            t += 1;
        }
        assertThat(ror.activated()).isTrue();
        assertThat(fixed.activated()).as("rate-of-rise channel is faster").isFalse();
    }
}
