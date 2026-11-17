package dev.denfry.promptcritical.sim.combustion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import org.junit.jupiter.api.Test;

class SteamTurbineTest {
    private static final WaterProperties W = WaterProperties.tables();
    private static final SteamTurbine.Spec REF = SteamTurbine.Spec.reference(W);

    /**
     * 1,3 МПа, 250 °C → 0,12 МПа: h₁ ≈ 2934 кДж/кг, изоэнтропийный перепад ≈ 480 кДж/кг до влажного пара; при η 0,65 и
     * поправке Баумана — около 300 кДж/кг; 750 кВт требуют ≈ 2,5 кг/с (9 т/ч) — один ДКВР-10.
     */
    @Test
    void designPointGivesRatedPowerAtRatedSpeed() {
        assertThat(REF.ratedFlowKgS()).isBetween(2.3, 2.8);
        SteamTurbine t = new SteamTurbine(REF, W);
        t.setOmega(REF.nominalOmega());
        double torque = REF.ratedPowerW() / REF.nominalOmega();
        t.step(1e-3, REF.inletPa(), REF.inletEnthalpy(), REF.exhaustPa(), torque, true);
        assertThat(t.steamFlowKgS()).isCloseTo(REF.ratedFlowKgS(), within(1e-6 * REF.ratedFlowKgS()));
        assertThat(t.shaftPowerW()).isCloseTo(REF.ratedPowerW() * (1 - SteamTurbine.WINDAGE), within(1e-3 * REF.ratedPowerW()));
        assertThat(t.capacityW(REF.inletPa(), REF.inletEnthalpy(), REF.exhaustPa()))
                .isCloseTo(REF.ratedPowerW() * (1 - SteamTurbine.WINDAGE), within(1e-3 * REF.ratedPowerW()));
    }

    /** Активная ступень: пусковой момент вдвое больше расчётного, ротор без нагрузки разгоняется до срабатывания автомата. */
    @Test
    void stallTorqueIsTwiceRatedAndOverspeedTripLatches() {
        SteamTurbine t = new SteamTurbine(REF, W);
        double rated = REF.ratedPowerW() / REF.nominalOmega();
        t.step(1e-3, REF.inletPa(), REF.inletEnthalpy(), REF.exhaustPa(), 0, true);
        double accel = t.omega() / 1e-3;
        assertThat(accel * REF.inertiaKgM2() / rated).isCloseTo(2, within(0.01));
        t.setOmega(1.12 * REF.nominalOmega());
        t.step(0.01, REF.inletPa(), REF.inletEnthalpy(), REF.exhaustPa(), 0, true);
        assertThat(t.tripped()).isTrue();
        for (int i = 0; i < 100; i++) t.step(0.01, REF.inletPa(), REF.inletEnthalpy(), REF.exhaustPa(), 0, true);
        assertThat(t.valve()).isLessThan(0.01);
        assertThat(t.resetTrip()).isFalse(); // ещё на оборотах
        t.setOmega(0.5 * REF.nominalOmega());
        assertThat(t.resetTrip()).isTrue();
    }

    /** Регулятор: открытие по статизму — на номинальных оборотах полностью открыт, на холостых (105 %) закрыт. */
    @Test
    void governorFollowsDroop() {
        SteamTurbine t = new SteamTurbine(REF, W);
        t.setOmega(REF.nominalOmega());
        for (int i = 0; i < 500; i++) {
            t.setOmega(REF.nominalOmega());
            t.step(0.01, 0, 0, REF.exhaustPa(), 0, true);
        }
        assertThat(t.valve()).isCloseTo(1, within(1e-3));
        for (int i = 0; i < 500; i++) {
            t.setOmega(1.025 * REF.nominalOmega());
            t.step(0.01, 0, 0, REF.exhaustPa(), 0, true);
        }
        assertThat(t.valve()).isCloseTo(0.5, within(1e-3));
    }

    /** Насыщенный пар 1 МПа в атмосферу: выход влажный, Бауман срезает работу на половину выходной влажности. */
    @Test
    void wetExpansionLosesEfficiencyByBaumann() {
        double h1 = W.saturationP(1e6).hG();
        ThermoState st = new ThermoState();
        double dhs = h1 - W.enthalpyPS(0.12e6, W.entropyPH(1e6, h1));
        double dh = SteamTurbine.specificWork(W, 1e6, h1, 0.12e6, 0.7, st);
        W.stateFromPressureEnthalpy(0.12e6, h1 - 0.7 * dhs, st);
        double y = 1 - st.quality;
        assertThat(y).isPositive();
        assertThat(dh).isCloseTo(0.7 * (1 - y / 2) * dhs, within(1e-9 * dh));
    }
}
