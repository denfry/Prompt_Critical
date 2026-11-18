package dev.denfry.promptcritical.sim.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.ops.ConfinedSpaceAtmosphere.Gas;
import org.junit.jupiter.api.Test;

class ConfinedSpaceAtmosphereTest {
    private static double[] nitrogen(double m3s) {
        double[] in = new double[Gas.values().length];
        in[Gas.N2.ordinal()] = m3s * ConfinedSpaceAtmosphere.MOLAR_DENSITY;
        return in;
    }

    /** §13.2.6: ёмкость 20 м³, азот 1,7 л/с через неплотную задвижку — O₂ до 16 % за ≈ 3100 с. */
    @Test
    void nitrogenLeakTakesFiftyMinutesToSixteenPercent() {
        ConfinedSpaceAtmosphere a = new ConfinedSpaceAtmosphere(20);
        int t = 0;
        while (a.fraction(Gas.O2) > 0.16) {
            a.step(1, nitrogen(0.0017), 0);
            t++;
        }
        double expected = 20 / 0.0017 * Math.log(0.2095 / 0.16);
        assertThat((double) t).isCloseTo(expected, within(5.0));
        assertThat(expected).isCloseTo(3170, within(30.0));
    }

    /** Продувка 50 л/с — до 10 % за ≈ 5 мин. */
    @Test
    void purgeTakesFiveMinutesToTenPercent() {
        ConfinedSpaceAtmosphere a = new ConfinedSpaceAtmosphere(20);
        for (int t = 0; t < 300; t++) a.step(1, nitrogen(0.05), 0);
        assertThat(a.fraction(Gas.O2)).isCloseTo(0.2095 * Math.exp(-0.05 * 300 / 20), within(1e-3));
        assertThat(a.fraction(Gas.O2)).isCloseTo(0.10, within(0.005));
        assertThat(a.fraction(Gas.N2)).isGreaterThan(0.89);
    }

    /** Вентиляция с 5 % O₂ до 19,5 %: 2,4 объёма при идеальном перемешивании, 7,3 с коэффициентом 3. */
    @Test
    void ventilationNeedsSevenAirChanges() {
        assertThat(ConfinedSpaceAtmosphere.airChanges(0.05, 0.195)).isCloseTo(7.3, within(0.15)); // GDD считает от 20,9 %, здесь воздух 20,95 %
        ConfinedSpaceAtmosphere a = new ConfinedSpaceAtmosphere(20, new double[] {0.05, 0.95, 0, 0, 0, 0});
        double plan = a.ventilationTimeS(ConfinedSpaceAtmosphere.VENTILATOR_M3S);
        for (int t = 0; t < plan; t++) a.step(1, null, ConfinedSpaceAtmosphere.VENTILATOR_M3S);
        assertThat(a.fraction(Gas.O2)).isGreaterThan(ConfinedSpaceAtmosphere.O2_SAFE);
        assertThat(new ConfinedSpaceAtmosphere(20, new double[] {0.05, 0.95, 0, 0, 0, 0}).ventilationTimeS(0)).isInfinite();
    }

    /** Большой шаг устойчив, доли остаются в сумме единицей. */
    @Test
    void largeStepStaysBounded() {
        ConfinedSpaceAtmosphere a = new ConfinedSpaceAtmosphere(1);
        a.step(60, nitrogen(1), ConfinedSpaceAtmosphere.VENTILATOR_M3S);
        double sum = 0;
        for (double v : a.fractions()) {
            assertThat(v).isBetween(0.0, 1.0);
            sum += v;
        }
        assertThat(sum).isCloseTo(1, within(1e-9));
    }

    /** §13.2.6: CO 100 ppm при 20,9 % O₂ — COHb_eq 10,3 %, 1000 ppm — 53 %; стадии O₂ и H₂S по таблице. */
    @Test
    void physiologyThresholds() {
        assertThat(AtmosphereHazard.cohbEquilibrium(100e-6, 0.209)).isCloseTo(0.103, within(0.002));
        assertThat(AtmosphereHazard.cohbEquilibrium(1000e-6, 0.209)).isCloseTo(0.53, within(0.01));
        double cohb = 0;
        for (int t = 0; t < 600; t++) cohb = AtmosphereHazard.cohbStep(cohb, 1000e-6, 0.209, 1);
        assertThat(cohb).isCloseTo(0.53 * (1 - Math.exp(-1)), within(0.01));
        assertThat(AtmosphereHazard.cohbStage(cohb)).isEqualTo(2);
        assertThat(AtmosphereHazard.o2Stage(0.209)).isZero();
        assertThat(AtmosphereHazard.o2Stage(0.17)).isEqualTo(1);
        assertThat(AtmosphereHazard.o2Stage(0.13)).isEqualTo(2);
        assertThat(AtmosphereHazard.o2Stage(0.10)).isEqualTo(3);
        assertThat(AtmosphereHazard.o2Stage(0.05)).isEqualTo(4);
        assertThat(AtmosphereHazard.h2sStage(5)).isZero();
        assertThat(AtmosphereHazard.h2sStage(200)).isEqualTo(2);
        assertThat(AtmosphereHazard.h2sStage(2000)).isEqualTo(4);
    }
}
