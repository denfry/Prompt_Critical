package dev.denfry.promptcritical.sim.wind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Аэродинамика колеса против теории: BEM на идеальной лопасти Глауэрта даёт его оптимум C_P(λ) (Manwell, McGowan,
 * Rogers, «Wind Energy Explained», 2009, табл. 3.2), ни один режим колеса Д-30 не превышает предел Бетца 16/27,
 * профильные и концевые потери снижают максимум до уровня трёхлопастных колёс, флюгер снимает момент.
 */
class RotorAerodynamicsTest {
    private static final WindTurbineSpec D30 = WindTurbineSpec.balaklavaD30();
    private static final RotorPerformance PERF = new RotorPerformance(D30.geometry(), D30.airfoil());

    /** Manwell et al. (2009), табл. 3.2: C_P,max идеального колеса с закруткой следа. */
    @ParameterizedTest
    @CsvSource({"0.5, 0.289", "1.0, 0.416", "1.5, 0.477", "2.0, 0.511", "2.5, 0.533", "5.0, 0.570", "7.5, 0.581",
            "10.0, 0.585"})
    void idealGlauertBladeReproducesTheOptimumPowerCoefficient(double tsr, double cpTable) {
        RotorGeometry ideal = RotorGeometry.glauertOptimum(1, 1e-3, 3, tsr, 1, 1 / (2 * Math.PI), 100, 400);
        double cp = BladeElementMomentum.solve(ideal, Airfoil.ideal(), tsr, 0, false).cp();
        assertThat(cp).isCloseTo(cpTable, within(0.0015));
    }

    @Test
    void noOperatingPointBeatsTheBetzLimit() {
        double max = 0;
        for (double tsr = 0; tsr <= RotorPerformance.TSR_MAX; tsr += 0.1)
            for (int pitch = RotorPerformance.PITCH_MIN_DEG; pitch <= 90; pitch++)
                max = Math.max(max, PERF.powerCoefficient(tsr, pitch));
        assertThat(max).isLessThan(16.0 / 27);
        // концевые потери Прандтля и C_d/C_l ≈ 1/70 шероховатого профиля: 0,44–0,48 у трёхлопастного колеса
        // с λ_опт ≈ 6, как у трёхлопастных колёс (Burton et al., «Wind Energy Handbook», 2011, гл. 3)
        assertThat(max).isBetween(0.42, 0.50);
    }

    /**
     * Нигде в таблице нет выбросов: момент ограничен сверху моментом лопастей, идущих плашмя со скоростью ΩR
     * (C_Q ≤ B·C_d,max·λ²·∫c·r³dr/(πR⁵) ≈ 0,03·λ² для Д-30), плюс аэродинамический момент неподвижного колеса.
     * Регрессия: решения «пропеллерного тормоза» с a = κ/(κ − 1) → ∞ давали C_Q ≈ −27 при β ≈ 69°, λ &lt; 1,5.
     */
    @Test
    void tableHasNoMomentumTheoryBlowUps() {
        for (double tsr = 0; tsr <= RotorPerformance.TSR_MAX; tsr += RotorPerformance.TSR_STEP)
            for (int pitch = RotorPerformance.PITCH_MIN_DEG; pitch <= 90; pitch++)
                assertThat(Math.abs(PERF.torqueCoefficient(tsr, pitch))).as("λ=%s β=%s", tsr, pitch)
                        .isLessThan(0.1 + 0.04 * tsr * tsr);
    }

    @Test
    void optimumTipSpeedRatioIsNearTheDesignValue() {
        double best = 0, bestTsr = 0;
        for (double tsr = 1; tsr <= 12; tsr += 0.05) {
            double cp = PERF.powerCoefficient(tsr, D30.runPitchDeg());
            if (cp > best) {
                best = cp;
                bestTsr = tsr;
            }
        }
        assertThat(bestTsr).isBetween(5.0, 7.0);
    }

    @Test
    void startingTorqueAtStandstillGrowsWithTheStartPitch() {
        double run = PERF.torqueCoefficient(0, D30.runPitchDeg()), start = PERF.torqueCoefficient(0, D30.startPitchDeg());
        assertThat(run).isPositive();
        assertThat(start).isGreaterThan(2 * run);
        // пусковой момент при 4 м/с больше холостого хода трансмиссии — колесо трогается на пороге включения
        double q = 0.5 * 1.225 * D30.sweptAreaM2() * 16 * D30.radiusM() * start;
        double noLoad = WindTurbine.NO_LOAD_TORQUE * D30.ratedPowerW() / (D30.gearEfficiency() * 2 * Math.PI * 30 / 60);
        assertThat(q).isGreaterThan(5 * noLoad);
    }

    @Test
    void featheredBladesBrakeASpinningRotor() {
        for (double tsr = 1; tsr <= 12; tsr += 1) assertThat(PERF.torqueCoefficient(tsr, 90)).isNegative();
        assertThat(PERF.thrustCoefficient(0, 90)).isLessThan(0.1 * PERF.thrustCoefficient(6, 0));
    }

    @Test
    void polarIsContinuousThroughStallAndAtNinetyDegrees() {
        Airfoil a = D30.airfoil();
        double[] lo = new double[2], hi = new double[2];
        for (double edge : new double[] {a.stallRad(), a.negativeStallRad(), Math.PI / 2, -Math.PI / 2}) {
            a.coefficients(edge - 1e-9, lo);
            a.coefficients(edge + 1e-9, hi);
            assertThat(hi[0]).isCloseTo(lo[0], within(1e-6));
            assertThat(hi[1]).isCloseTo(lo[1], within(1e-6));
        }
        a.coefficients(Math.PI / 2, lo);
        assertThat(lo[1]).isCloseTo(a.cdMax(), within(1e-9)); // плоская пластина поперёк потока
    }
}
