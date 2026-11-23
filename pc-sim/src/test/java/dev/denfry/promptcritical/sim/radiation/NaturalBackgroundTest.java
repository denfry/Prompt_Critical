package dev.denfry.promptcritical.sim.radiation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground.Dimension;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground.Ground;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Природный фон по 04b §4b.13: земное γ от грунта в поле зрения, космическое по высоте и перекрытию, Незер и Энд. */
class NaturalBackgroundTest {
    @Test
    void grassSurfaceIsAboutOnePointOneTenthMicroSievert() { // 04b §4b.13.2: 60·1,2 + 35 ≈ 0,11
        double t = NaturalBackground.terrestrialNGyH(Map.of(Ground.SOIL, 0.5));
        assertThat(t).isCloseTo(60, within(1e-9));
        assertThat(NaturalBackground.ambientUSvH(t, NaturalBackground.cosmicUSvH(Dimension.OVERWORLD, 63, 0)))
                .isCloseTo(0.107, within(0.001));
    }

    @Test
    void graniteCaveDoublesTheHalfSpace() {
        assertThat(NaturalBackground.terrestrialNGyH(Map.of(Ground.STONE, 1.0))).isCloseTo(214, within(1e-9));
    }

    @Test
    void cosmicDoublesPerFifteenHundredMetres() {
        assertThat(NaturalBackground.cosmicUSvH(Dimension.OVERWORLD, 1563, 0)).isCloseTo(0.070, within(1e-9));
    }

    @Test
    void deepOverburdenLeavesTheMuonFloor() {
        assertThat(NaturalBackground.cosmicUSvH(Dimension.OVERWORLD, 63, 40)).isCloseTo(0.0035, within(1e-5));
    }

    @Test
    void netherAndEnd() {
        assertThat(NaturalBackground.cosmicUSvH(Dimension.NETHER, 64, 0)).isEqualTo(0.5);
        assertThat(NaturalBackground.cosmicUSvH(Dimension.END, 63, 0)).isCloseTo(0.35, within(1e-9));
    }

    @Test
    void emptyMapIsZero() {
        assertThat(NaturalBackground.terrestrialNGyH(Map.of())).isZero();
    }

    @Test
    void fractionsAreClampedAndNegativeOverburdenIsSurface() {
        assertThat(NaturalBackground.terrestrialNGyH(Map.of(Ground.STONE, 5.0, Ground.SOIL, -1.0)))
                .isCloseTo(214, within(1e-9));
        assertThat(NaturalBackground.cosmicUSvH(Dimension.OVERWORLD, 63, -10))
                .isEqualTo(NaturalBackground.cosmicUSvH(Dimension.OVERWORLD, 63, 0));
    }
}
