package dev.denfry.promptcritical.sim.thermo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Объёмное паросодержание: HEM и drift-flux Зубера–Финдлея (01 §1.3.5, §1.3.6, §1.16). */
class VoidFractionTest {
    private static final SaturationState S7 = WaterProperties.tables().saturationP(7e6);
    private static final double VGJ = VoidFraction.driftVelocity(S7.sigma(), S7.rhoF(), S7.rhoG());

    @Test
    @Tag("T-1.3-07")
    void driftFluxAt7MPa() {
        assertThat(VGJ).isCloseTo(0.172, within(0.001));
        assertThat(VoidFraction.driftFlux(0.1, 1000, S7.rhoF(), S7.rhoG(), VGJ)).isCloseTo(0.590, within(0.005));
        assertThat(VoidFraction.homogeneous(0.1, S7.rhoF(), S7.rhoG())).isCloseTo(0.692, within(0.005));
    }

    /** Таблица сравнения 01 §1.3.5: 7 МПа, G = 1000 кг/(м²·с). */
    @Test
    void comparisonTable() {
        double[] x = {0.01, 0.05, 0.10, 0.20};
        double[] hem = {0.170, 0.516, 0.692, 0.835};
        double[] drift = {0.137, 0.432, 0.590, 0.722};
        for (int i = 0; i < x.length; i++) {
            assertThat(VoidFraction.of(VoidFraction.Model.HEM, x[i], 1000, S7.rhoF(), S7.rhoG(), S7.sigma()))
                    .as("HEM x=%s", x[i]).isCloseTo(hem[i], within(0.005));
            assertThat(VoidFraction.of(VoidFraction.Model.DRIFT_FLUX, x[i], 1000, S7.rhoF(), S7.rhoG(), S7.sigma()))
                    .as("drift x=%s", x[i]).isCloseTo(drift[i], within(0.005));
        }
    }

    @Test
    void limits() {
        double rf = S7.rhoF(), rg = S7.rhoG();
        assertThat(VoidFraction.homogeneous(-0.1, rf, rg)).isZero();
        assertThat(VoidFraction.homogeneous(1.2, rf, rg)).isEqualTo(1.0);
        assertThat(VoidFraction.driftFlux(0, 1000, rf, rg, VGJ)).isZero();
        assertThat(VoidFraction.driftFlux(1, 1000, rf, rg, VGJ)).isEqualTo(1.0);
        // G → 0: пузыри всплывают через неподвижную жидкость, α → x·G/(ρ_g V_gj)
        double g = 1e-3, x = 0.2;
        double still = x * g / (rg * VGJ);
        assertThat(VoidFraction.driftFlux(x, g, rf, rg, VGJ)).isCloseTo(still, within(1e-3 * still));
        assertThat(VoidFraction.driftFlux(x, 0, rf, rg, VGJ)).isZero();
        assertThat(VoidFraction.driftFlux(x, -1000, rf, rg, VGJ))
                .isEqualTo(VoidFraction.driftFlux(x, 1000, rf, rg, VGJ));
        // ни расхода, ни дрейфа — формула вырождается в 0/0, берётся гомогенная модель
        assertThat(VoidFraction.driftFlux(x, 0, rf, rg, 0)).isEqualTo(VoidFraction.homogeneous(x, rf, rg));
        for (int i = 1; i < 100; i++) {
            double xi = i / 100.0;
            assertThat(VoidFraction.driftFlux(xi, 1000, rf, rg, VGJ)).isLessThan(VoidFraction.homogeneous(xi, rf, rg));
        }
    }

    /** Паросодержание слоя под уровнем узла SEPARATED (§1.3.6). */
    @Test
    void poolVoidFraction() {
        assertThat(VoidFraction.pool(0, VGJ)).isZero();
        double jg = 0.5;
        assertThat(VoidFraction.pool(jg, VGJ)).isCloseTo(jg / (VoidFraction.C0 * jg + VGJ), within(1e-15));
        assertThat(VoidFraction.pool(1e6, VGJ)).isCloseTo(1 / VoidFraction.C0, within(1e-6));
    }
}
