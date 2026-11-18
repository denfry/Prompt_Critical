package dev.denfry.promptcritical.sim.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** ШГН 09 §9.6.5: динамограммы колонны из восьми масс. */
class RodPumpDynamicsTest {
    private static final double L = 1500, RHO = 900, SPM = 8;
    private static final double PD = RHO * BeamPump.G * L;

    private static RodPumpDynamics.DynoCard full() {
        return RodPumpDynamics.card(SPM, L, RHO, PD, 0.1e6, 1, 0, Double.NaN);
    }

    /** Площадь карты — работа за цикл, Дж. */
    private static double area(RodPumpDynamics.DynoCard c) {
        return c.rodPowerW() * c.periodS();
    }

    @Test
    void fullCardMatchesMillsLoads() {
        RodPumpDynamics.DynoCard c = full();
        assertThat(c.positionM()).hasSize(200);
        double wr = BeamPump.ROD_KG_PER_M * L * BeamPump.G, wf = c.fluidLoadN();
        double alpha = BeamPump.strokeM() * SPM * SPM / 1790;
        double pprl = wf + wr * (1 + alpha), mprl = wr * (1 - alpha) - 0.127 * wr * RHO / 1000;
        assertThat(wr).isCloseTo(35.8e3, within(0.1e3));
        assertThat(c.peakN()).isCloseTo(pprl, within(0.12 * pprl));
        // формула Миллса не видит колебаний штанг: у динамической модели минимум ниже
        assertThat(c.minN()).isCloseTo(mprl, within(0.25 * mprl));
        // ход плунжера меньше хода штока на растяжение штанг δ = F·L/(E·A)
        double stretch = BeamPump.rodStretchM(wf, L);
        assertThat(c.plungerStrokeM()).isCloseTo(BeamPump.strokeM() - stretch, within(0.25));
        assertThat(c.plungerStrokeM()).isLessThan(BeamPump.strokeM());
    }

    @Test
    void fluidPoundAndGasShrinkTheCard() {
        double fullArea = area(full());
        RodPumpDynamics.DynoCard pound = RodPumpDynamics.card(SPM, L, RHO, PD, 0.1e6, 0.5, 0, Double.NaN);
        RodPumpDynamics.DynoCard gas = RodPumpDynamics.card(SPM, L, RHO, PD, 3e6, 0.5, 0, Double.NaN);
        assertThat(area(pound) / fullArea).isBetween(0.35, 0.75);
        // газ в насосе: камера заполнена не жидкостью, работа за ход меньше полной
        assertThat(area(gas)).isLessThan(0.8 * fullArea);
        // газовая пробка: почти пустая камера и высокое давление приёма — нагнетательный клапан не открывается
        RodPumpDynamics.DynoCard lock = RodPumpDynamics.card(SPM, L, RHO, PD, 1.5e6, 0.0, 0, Double.NaN);
        assertThat(lock.peakN() - lock.minN()).isLessThan(0.6 * (full().peakN() - full().minN()));
    }

    @Test
    void leakingTravellingValveTiltsTheUpstroke() {
        RodPumpDynamics.DynoCard leak = RodPumpDynamics.card(SPM, L, RHO, PD, 0.1e6, 1, 0.6, Double.NaN);
        assertThat(area(leak)).isLessThan(0.92 * area(full()));
    }

    @Test
    void partedRodsDropTheLoadToTheRodsAbove() {
        RodPumpDynamics.DynoCard part = RodPumpDynamics.card(SPM, L, RHO, PD, 0.1e6, 1, 0, 750);
        double wrfAbove = BeamPump.ROD_KG_PER_M * 750 * BeamPump.G * (1 - RHO / BeamPump.STEEL_KGM3);
        assertThat(part.rodWeightInFluidN()).isCloseTo(wrfAbove, within(1.0));
        assertThat(part.peakN()).isLessThan(1.3 * wrfAbove);
        assertThat(part.plungerStrokeM()).isZero();
    }

    @Test
    void counterbalanceCutsThePeakTorque() {
        RodPumpDynamics.DynoCard c = full();
        double wrf = c.rodWeightInFluidN(), ideal = c.fluidLoadN() / 2 + wrf;
        double balanced = c.peakTorqueNm(ideal), none = c.peakTorqueNm(0);
        // 09 §9.6.5: T_p ≈ (PPRL − C)·S/2
        assertThat(balanced).isCloseTo((c.peakN() - ideal) * BeamPump.strokeM() / 2, within(0.5 * balanced));
        assertThat(none / balanced).isGreaterThan(2);
    }

    @Test
    void deterministic() {
        RodPumpDynamics.DynoCard a = full(), b = full();
        assertThat(a.loadN()).containsExactly(b.loadN());
    }
}
