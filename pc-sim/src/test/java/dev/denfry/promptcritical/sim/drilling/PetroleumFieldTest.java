package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class PetroleumFieldTest {
    @Test
    void trapsAreDeterministicAndStayInsideTheirCell() {
        PetroleumField a = new PetroleumField(42), b = new PetroleumField(42);
        int found = 0;
        for (int cx = -10; cx < 10; cx++)
            for (int cz = -10; cz < 10; cz++) {
                Trap t = a.trap(cx, cz);
                assertThat(t).isEqualTo(b.trap(cx, cz));
                if (t == null) continue;
                found++;
                assertThat(PetroleumField.cellOf((int) Math.floor(t.crestX() - t.radiusM()))).isEqualTo(cx);
                assertThat(PetroleumField.cellOf((int) Math.ceil(t.crestX() + t.radiusM()))).isEqualTo(cx);
                assertThat(PetroleumField.cellOf((int) Math.floor(t.crestZ() - t.radiusM()))).isEqualTo(cz);
                assertThat(t.crestDepthM()).isBetween(600.0, 2500.0);
                assertThat(t.topDepthM(t.crestX(), t.crestZ())).isEqualTo(t.crestDepthM());
                assertThat(t.topDepthM(t.crestX() + t.radiusM(), t.crestZ())).isCloseTo(t.crestDepthM() + t.closureM(), within(1e-9));
            }
        // половина клеток — с ловушкой: из 400 не меньше 150 и не больше 250
        assertThat(found).isBetween(150, 250);
    }

    /** Давление непрерывно на контакте и падает вверх по залежи медленнее гидростатики: на своде — АВПД относительно воды. */
    @Test
    void pressureIsContinuousAtTheContactAndBuoyantAbove() {
        Trap gas = new Trap(0, 0, 80, 1500, 60, 1560, 15, 0.2, 100, FormationFluid.GAS, 10_200);
        double below = gas.pressureAt(1560 + 1e-6), above = gas.pressureAt(1560 - 1e-6);
        assertThat(above).isCloseTo(below, within(1.0));
        assertThat(gas.equivalentDensityKgM3(1500)).isGreaterThan(10_200 / Trap.G);
        // газ около 120 кг/м³ при 16 МПа и 335 K: 60 м колонны весят ≈ 0,07 МПа против 0,61 МПа воды
        double rho = NaturalGas.densityKgM3(10_200 * 1560 + NaturalGas.STD_PRESSURE_PA, Trap.temperatureK(1560));
        assertThat(rho).isBetween(110.0, 130.0);
        double gain = gas.pressureAt(1500) - 10_200 * 1500;
        assertThat(gain).isCloseTo(60 * (10_200 - rho * Trap.G), within(1.0));
        assertThat(gas.fluidAt(1550)).isEqualTo(FormationFluid.GAS);
        assertThat(gas.fluidAt(1570)).isEqualTo(FormationFluid.WATER);
    }

    @Test
    void seismicTimeDepthConversionRoundTrips() {
        for (double d : new double[] {600, 1500, 2500}) {
            double t = SeismicSurvey.twoWayTimeS(d);
            assertThat(SeismicSurvey.depthM(t)).isCloseTo(d, within(1e-6));
        }
        // 1500 м при v₀ = 1800 м/с, k = 0,6: t = (2/0,6)·ln(1,5) = 1,352 с
        assertThat(SeismicSurvey.twoWayTimeS(1500)).isCloseTo(2 / 0.6 * Math.log(1.5), within(1e-12));
    }

    /** Съёмка находит ловушку, глубина — в пределах ошибки скорости, Итон без ошибки скорости точен. */
    @Test
    void surveyImagesTheNearestStructure() {
        PetroleumField f = new PetroleumField(7);
        Trap t = null;
        for (int c = 0; t == null && c < 50; c++) t = f.trap(c, 0);
        assertThat(t).isNotNull();
        SeismicSurvey.Reading r = SeismicSurvey.survey(f, 7, t.crestX() + 20, t.crestZ() - 10, SeismicSurvey.apertureBlocks(4));
        assertThat(r).isNotNull();
        assertThat(r.crestX()).isEqualTo(t.crestX());
        assertThat(r.crestDepthM()).isCloseTo(t.crestDepthM(), within(t.crestDepthM() * SeismicSurvey.VELOCITY_ERROR + 1e-6));
        assertThat(r.brightSpot()).isEqualTo(t.fluid() == FormationFluid.GAS);
        assertThat(r.porePressureKgM3()).isCloseTo(t.gradientPaM() / Trap.G, within(0.15 * t.gradientPaM() / Trap.G));
        assertThat(SeismicSurvey.survey(f, 7, t.crestX() + 5000, t.crestZ(), 0)).isNull();
    }
}
