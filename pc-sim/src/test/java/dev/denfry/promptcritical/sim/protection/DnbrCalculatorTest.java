package dev.denfry.promptcritical.sim.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermal.HeatTransferCorrelations;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

/** Расчётная DNBR на измерениях (01 §1.11.4) по горячему каналу ПР-300 (§1.14.1). */
class DnbrCalculatorTest {
    static final WaterProperties W = ProtectionSystemTest.W;
    /** ПР-300: 300 МВт, 1778 кг/с, 1,137 м², D_e 10,6 мм, q'' 350 кВт/м², F_q = F_ΔH·F_z = 2,0. */
    static final HotChannelModel PR300 = new HotChannelModel(300e6, 1778, 1.137, 0.0106, 350e3, 2.0 / 1.55, 1.55, 8);
    static final double P = 15.5e6, T_IN = 563.15;

    final DnbrCalculator real = new DnbrCalculator(PR300, ProtectionConfig.defaults().withoutDnbrUncertainty(), W);
    final DnbrCalculator calc = new DnbrCalculator(PR300, ProtectionConfig.defaults(), W);

    /**
     * Без погрешностей — W-3 у пика потока. Недогрев в горячем канале ПР-300 до самого верха больше области W-3
     * (x_e &lt; −0,15), поэтому CHF ≈ q''_W3(x_e = −0,15) и MDNBR ≈ CHF / q''_peak ≈ 4,2. В GDD (§1.14.1)
     * «≈ 3,7» — оценочная модель; при W-3 из §1.4.4 и F_q = 2,0 она не воспроизводится.
     */
    @Test
    void nominalHotChannelMatchesW3AtPeak() {
        var sat = W.saturationP(P);
        double chf = HeatTransferCorrelations.chfW3(P * 1e-6, PR300.nominalMassFlux(), -0.15,
                PR300.hydraulicDiameter(), (sat.hF() - W.enthalpyPT(P, T_IN)) * 1e-3);
        double peak = PR300.averageHeatFlux() * 2.0;
        assertThat(real.calculate(P, T_IN, 1, 1, Double.NaN)).isCloseTo(chf / peak, within(0.05 * chf / peak));
    }

    @Test
    void uncertaintiesMakeItConservative() {
        double[][] states = {{1, 1}, {0.86, 0.85}, {0.6, 1}, {1, 1.3}};
        for (double[] s : states) {
            double r = real.calculate(P, T_IN, s[0], s[1], Double.NaN);
            assertThat(calc.calculate(P, T_IN, s[0], s[1], Double.NaN)).isLessThan(r);
        }
        assertThat(calc.calculate(P, T_IN, 1, 1, Double.NaN)).isBetween(1.45, 4.22);
    }

    @Test
    void marginShrinksWithPowerFlowAndInletTemperature() {
        double nominal = calc.calculate(P, T_IN, 1, 1, Double.NaN);
        assertThat(calc.calculate(P, T_IN, 1, 1.3, Double.NaN)).isLessThan(nominal);
        assertThat(calc.calculate(P, T_IN, 0.4, 1, Double.NaN)).isLessThan(nominal);
        assertThat(calc.calculate(P, T_IN + 20, 1, 1, Double.NaN)).isLessThan(nominal);
        // Детекторы с F_z ниже консервативного убирают лишний запас (§1.11.4: «улучшить измерения»).
        assertThat(calc.calculate(P, T_IN, 1, 1, 1.3)).isGreaterThan(nominal);
    }

    @Test
    void missingInputIsFaultAndZeroPowerIsInfinite() {
        assertThat(calc.calculate(Double.NaN, T_IN, 1, 1, Double.NaN)).isNaN();
        assertThat(calc.calculate(P, T_IN, Double.NaN, 1, Double.NaN)).isNaN();
        assertThat(calc.calculate(P, T_IN, 1, 0, Double.NaN)).isInfinite();
    }

    @Test
    void lowDnbrFunctionUsesThreeCalculators() {
        var d = new ProtectionSystemTest.Driver(FakeInputs.pr300Nominal(), calc).atPower();
        d.in.dnbrSignal(DnbrSignal.PRESSURE, P, P, P)
                .dnbrSignal(DnbrSignal.INLET_TEMPERATURE, T_IN, T_IN, T_IN)
                .dnbrSignal(DnbrSignal.FLOW_FRAC, 1, 1, 1)
                .dnbrSignal(DnbrSignal.POWER_FRAC, 1, 1, 1);
        d.step();
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);
        assertThat(d.ps.calculatedDnbr(0)).isEqualTo(calc.calculate(P, T_IN, 1, 1, Double.NaN));

        // Вычислитель без датчика расхода — FAULT; один отказ не даёт АЗ.
        d.in.dnbrSignal(DnbrSignal.FLOW_FRAC, Double.NaN, 1, 1);
        d.step();
        assertThat(d.ps.channelState(TripReason.LOW_DNBR, 0)).isEqualTo(ChannelState.FAULT);
        assertThat(d.ps.mode()).isEqualTo(ProtectionMode.NORMAL);

        // Второй вычислитель видит запас ниже 1,45 (каналы камер АЗ по потоку здесь не трогаем).
        d.in.dnbrSignal(DnbrSignal.POWER_FRAC, 1, 2.3, 1);
        d.step();
        assertThat(d.ps.calculatedDnbr(1)).isLessThan(1.45);
        assertThat(d.ps.isTripped()).isTrue();
        assertThat(d.ps.firstOut()).isEqualTo(TripReason.LOW_DNBR);
    }
}
