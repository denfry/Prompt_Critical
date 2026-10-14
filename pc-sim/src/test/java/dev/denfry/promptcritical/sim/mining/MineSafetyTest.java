package dev.denfry.promptcritical.sim.mining;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Канарейка, бензиновая лампа (08 §8.2.5) и правило пролёта кровли (08 §8.2.2). */
class MineSafetyTest {
    /** T8.7: CO 0,1 %, B₀ = 0 → падение (B = 0,6) через 73 с ±10 %; игрок за то же время — B = 0,03. */
    @Test
    void t8_7_canaryFallsBeforeMinerFeelsIt() {
        GasExposure bird = GasExposure.canary();
        GasExposure miner = GasExposure.human(MiningConfig.defaults().canaryUptakeRatio());
        int s = 0;
        while (bird.state() != ExposureState.FALLEN) {
            bird.step(1, 0.001, 0.2095);
            miner.step(1, 0.001, 0.2095);
            s++;
        }
        assertThat((double) s).isCloseTo(73, within(7.3));
        assertThat(miner.saturation()).isCloseTo(0.03, within(0.003));
        assertThat(miner.state()).isEqualTo(ExposureState.CALM);
    }

    /** Беспокойство раньше падения; гибель необратима; на свежем воздухе B спадает с τ = 30 мин. */
    @Test
    void canaryStatesAndRecovery() {
        GasExposure bird = GasExposure.canary();
        bird.step(40, 0.001, 0.2095);
        assertThat(bird.state()).isEqualTo(ExposureState.AGITATED);
        double b = bird.saturation();
        bird.step(1800, 0, 0.2095);
        assertThat(bird.saturation()).isCloseTo(b / Math.E, within(1e-12));
        bird.step(600, 0.005, 0.2095);
        assertThat(bird.state()).isEqualTo(ExposureState.DEAD);
        bird.step(10_000, 0, 0.2095);
        assertThat(bird.alive()).isFalse();
        assertThat(bird.saturation()).isEqualTo(1);

        // недостаток кислорода без CO: 15 % O₂ → k_O₂·0,02 = 10⁻³ с⁻¹
        GasExposure hypoxic = GasExposure.canary();
        hypoxic.step(1, 0, 0.15);
        assertThat(hypoxic.saturation()).isCloseTo(1e-3, within(1e-6));
    }

    @Test
    void safetyLampReadings() {
        assertThat(SafetyLamp.state(0.005, 0.2095, 1, true)).isEqualTo(LampState.NORMAL);
        assertThat(SafetyLamp.state(0.015, 0.2095, 1, true)).isEqualTo(LampState.HALO);
        assertThat(SafetyLamp.haloFraction(0.03)).isCloseTo(0.5, within(1e-12));
        assertThat(SafetyLamp.state(0.07, 0.2095, 1, true)).isEqualTo(LampState.FLAME_IN_GAUZE);
        assertThat(SafetyLamp.state(0.07, 0.2095, 5, true)).isEqualTo(LampState.IGNITES);
        assertThat(SafetyLamp.state(0.07, 0.2095, 1, false)).isEqualTo(LampState.IGNITES);
        assertThat(SafetyLamp.state(0.0, 0.16, 1, true)).isEqualTo(LampState.EXTINGUISHED);
    }

    /** Кровля в плане: прямоугольная выработка шириной w по X, бесконечная по Z, крепь — по маске. */
    private record Room(int width, MineSupport support, double condition) implements RoofSpanRule.RoofGrid {
        @Override public boolean open(int x, int z) { return x >= 0 && x < width; }
        @Override public MineSupport support(int x, int z) { return open(x, z) ? support : null; }
        @Override public double supportCondition(int x, int z) { return condition; }
    }

    @Test
    void roofSpanRule() {
        MiningConfig cfg = MiningConfig.defaults();
        Room drift = new Room(3, null, 1);
        assertThat(RoofSpanRule.span(drift, 1, 0)).isEqualTo(3);
        assertThat(RoofSpanRule.span(drift, 5, 0)).isZero();
        assertThat(RoofSpanRule.supported(drift, 1, 0, RoofRock.SANDSTONE, cfg)).isTrue();
        assertThat(RoofSpanRule.supported(drift, 1, 0, RoofRock.CARBONACEOUS_SHALE, cfg)).isFalse();

        Room chamber = new Room(8, null, 1);
        assertThat(RoofSpanRule.supported(chamber, 4, 0, RoofRock.SANDSTONE, cfg)).isFalse();
        assertThat(RoofSpanRule.collapseHeight(8, 6)).isEqualTo(2);
        assertThat(RoofSpanRule.collapseHeight(6, 6)).isZero();
        assertThat(RoofRock.SANDSTONE.collapseDelayS).isEqualTo(1800);

        Room timbered = new Room(8, MineSupport.TIMBER, 1);
        assertThat(RoofSpanRule.admissibleSpan(timbered, 4, 0, RoofRock.SANDSTONE, cfg)).isEqualTo(8);
        assertThat(RoofSpanRule.supported(timbered, 4, 0, RoofRock.SANDSTONE, cfg)).isTrue();
        Room rotten = new Room(8, MineSupport.TIMBER, 0.4);
        assertThat(RoofSpanRule.supported(rotten, 4, 0, RoofRock.SANDSTONE, cfg)).isFalse();

        // дерево во влажной выработке: −1 %/сут; арка не гниёт
        assertThat(MineSupport.TIMBER.decay(1, 10 * 86_400, true)).isCloseTo(0.9, within(1e-12));
        assertThat(MineSupport.TIMBER.decay(1, 10 * 86_400, false)).isEqualTo(1);
        assertThat(MineSupport.ARCH.decay(1, 10 * 86_400, true)).isEqualTo(1);
        assertThat(MineSupport.TIMBER.capacityN(0.9)).isCloseTo(45e3, within(1e-6));
    }
}
