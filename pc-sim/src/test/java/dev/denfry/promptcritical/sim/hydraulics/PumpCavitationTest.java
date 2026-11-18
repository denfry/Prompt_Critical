package dev.denfry.promptcritical.sim.hydraulics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Кавитация насоса (04a §4a.3): контрольные точки раздела и примеры «на салфетке». */
class PumpCavitationTest {
    /** ГЦН 1К в номинале (04a §4a.3.5): 15,5 МПа, 290 °C, ρ = 746 → NPSH_a ≈ 1101 м. */
    @Test
    void primaryPumpHasAKilometreOfMargin() {
        double a = PumpCavitation.npshAvailable(15.5e6, 563.15, 746, 0);
        assertThat(a).isCloseTo(1101, within(5.0));
        assertThat(PumpCavitation.intensity(a, 30)).isZero();
    }

    /** Конденсатный насос: на линии насыщения запас — только столб над насосом минус потери во всасе. */
    @Test
    void condensatePumpNeedsSubmergence() {
        double ps = dev.denfry.promptcritical.sim.thermo.if97.If97.psat(306.05);
        double rho = 995;
        double npshR = 2.5, hf = 0.5;
        double one = PumpCavitation.npshAvailable(ps + rho * PumpCavitation.G * (1 - hf), 306.05, rho, 0);
        double three = PumpCavitation.npshAvailable(ps + rho * PumpCavitation.G * (3 - hf), 306.05, rho, 0);
        assertThat(one).isCloseTo(0.5, within(1e-6));
        assertThat(PumpCavitation.headDrop(one, npshR)).isEqualTo(1.0);
        assertThat(PumpCavitation.headDrop(three, npshR)).isCloseTo(0.03, within(1e-6)); // ровно на границе NPSH_3
    }

    @Test
    void npshCurveAndAffinity() {
        assertThat(PumpCavitation.curve(1)).isCloseTo(1, within(1e-12));
        assertThat(PumpCavitation.curve(0)).isGreaterThan(PumpCavitation.curve(0.4)); // рециркуляция на малой подаче
        assertThat(PumpCavitation.npshRequired(4, 1, 1, 0)).isCloseTo(4, within(1e-12));
        // ω вдвое меньше при той же q: NPSH_r вчетверо меньше
        assertThat(PumpCavitation.npshRequired(4, 0.5, 0.5, 0)).isCloseTo(1, within(1e-12));
        // изношенное колесо кавитирует охотнее
        assertThat(PumpCavitation.npshRequired(4, 1, 1, 1)).isCloseTo(6, within(1e-12));
    }

    /** κ ≈ 0,67 при NPSH_a = NPSH_r; ΔH/H: 3 % там же, 22 % при 0,7 NPSH_r, 100 % при 0,47 NPSH_r. */
    @Test
    void intensityAndHeadDropMatchTheCheckPoints() {
        double r = 4;
        assertThat(PumpCavitation.intensity(2 * r, r)).isZero();
        assertThat(PumpCavitation.intensity(r, r)).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(PumpCavitation.intensity(0.5 * r, r)).isEqualTo(1);
        assertThat(PumpCavitation.headDrop(r, r)).isCloseTo(0.03, within(1e-12));
        assertThat(PumpCavitation.headDrop(0.7 * r, r)).isCloseTo(0.03 * Math.exp(2), within(1e-9));
        assertThat(PumpCavitation.headDrop(0.47 * r, r)).isEqualTo(1);
        assertThat(PumpCavitation.headDrop(3 * r, r)).isZero();
    }

    /** При κ = 1 колесо съедено за 200 ч; при κ = 0,5 — в восемь раз медленнее (κ³). */
    @Test
    void erosionIsCubicInIntensity() {
        assertThat(PumpCavitation.erosionRate(1) * 200 * 3600).isCloseTo(1, within(1e-12));
        assertThat(PumpCavitation.erosionRate(0.5) / PumpCavitation.erosionRate(1)).isCloseTo(0.125, within(1e-12));
        assertThat(PumpCavitation.erosionRate(0)).isZero();
    }

    /** Кавитационный слой на 9 дБ громче насоса при κ = 1 и на 6 дБ тише у порога. */
    @Test
    void cavitationLayerLevels() {
        assertThat(PumpCavitation.soundLayerDb(90, 1)).isEqualTo(99);
        assertThat(PumpCavitation.soundLayerDb(90, 1e-9)).isCloseTo(84, within(1e-6));
        assertThat(PumpCavitation.soundLayerDb(90, 0)).isEqualTo(Double.NEGATIVE_INFINITY);
    }
}
