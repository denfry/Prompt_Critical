package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** 07 §7.7.1 и 16 §16.7.1: интерфейс сети и Forge Energy, паритет SU↔FE. */
class FeInterfaceTest {
    private static final double DT = 0.05;

    private static FeInterface online(FeInterface.Mode m, double ratedW) {
        var f = new FeInterface(FeInterface.Spec.standard(ratedW));
        f.setMode(m);
        for (double t = 0; t < FeInterface.RECONNECT_S + FeInterface.RAMP_S + 2; t += DT) f.step(DT, 1, 50);
        return f;
    }

    @Test void oneMegawattImportGives19400FePerTick() {
        // тест T7.19: 1 МВт → 19 400 FE/т при J_FE = 2,5
        assertThat(FeParity.fePerTick(1e6, 0.97, 2.5)).isCloseTo(19_400, within(1e-6));
    }

    @Test void importFillsTheBufferWithEfficiencyAndStopsWhenFull() {
        var f = online(FeInterface.Mode.IMPORT, 1e6);
        assertThat(f.connected()).isTrue();
        assertThat(f.fill()).isCloseTo(1.0, within(1e-9));
        double p = f.step(DT, 1, 50);
        assertThat(p).isZero();
        f.extract(0.5e6, false);
        double draw = f.step(DT, 1, 50);
        assertThat(draw).isCloseTo(1e6, within(1e-6));
        assertThat(f.bufferJ()).isCloseTo(0.5e6 + 1e6 * 0.97 * DT, within(1e-6));
    }

    @Test void exportFeedsTheGridOnlyInsideTheWindow() {
        var f = online(FeInterface.Mode.EXPORT, 100e3);
        assertThat(f.receive(100e3, false)).isCloseTo(100e3, within(1e-9));
        double p = f.step(DT, 1, 50);
        assertThat(p).isCloseTo(-100e3, within(1e-6));
        // напряжение пропало: через 0,2 с интерфейс отключается и острова не держит
        for (int i = 0; i < 3; i++) f.step(DT, 0, 0);
        assertThat(f.connected()).isTrue();
        f.step(DT, 0, 0);
        f.step(DT, 0, 0);
        assertThat(f.connected()).isFalse();
        assertThat(f.step(DT, 0, 0)).isZero();
        // назад только после выдержки в окне
        for (double t = 0; t < FeInterface.RECONNECT_S - 1; t += DT) f.step(DT, 1, 50);
        assertThat(f.connected()).isFalse();
    }

    @Test void exportCannotStartADeadGrid() {
        var f = new FeInterface(FeInterface.Spec.standard(1e6));
        f.setMode(FeInterface.Mode.EXPORT);
        f.receive(1e6, false);
        for (int i = 0; i < 400; i++) assertThat(f.step(DT, 0, 0)).isZero();
        assertThat(f.connected()).isFalse();
    }

    @Test void autoHoldsTheBufferHalfFull() {
        var f = online(FeInterface.Mode.AUTO, 1e6);
        assertThat(f.fill()).isCloseTo(0.5, within(1e-6));
        f.receive(0.3e6, false);
        double p = f.step(DT, 1, 50);
        assertThat(p).isNegative();
        assertThat(f.receive(1, true)).isPositive();
        assertThat(online(FeInterface.Mode.IMPORT, 1e6).receive(1, true)).isZero();
        assertThat(online(FeInterface.Mode.EXPORT, 1e6).extract(1, true)).isZero();
    }

    @Test void powerRampsUpAfterReconnecting() {
        var f = new FeInterface(FeInterface.Spec.standard(1e6));
        f.setMode(FeInterface.Mode.IMPORT);
        // буфер опустошается каждый шаг, чтобы мощность ограничивал только набор, а не полный буфер
        for (double t = 0; t < FeInterface.RECONNECT_S + FeInterface.RAMP_S / 2; t += DT) {
            f.extract(Double.MAX_VALUE, false);
            f.step(DT, 1, 50);
        }
        assertThat(f.connected()).isTrue();
        assertThat(f.gridW()).isBetween(0.3e6, 0.7e6);
    }

    @Test void setpointCapsThePower() {
        var f = online(FeInterface.Mode.IMPORT, 1e6);
        f.extract(1e6, false);
        f.setSetpointW(250e3);
        assertThat(f.step(DT, 1, 50)).isCloseTo(250e3, within(1e-6));
        f.setSetpointW(5e6);
        assertThat(f.setpointW()).isEqualTo(1e6);
    }

    @Test void defaultRatesGiveAPerpetualMotionWithABridgeAndParityClosesIt() {
        double w = 1000, eta = 0.97;
        double g = FeParity.forwardGain(w, 2.5, eta, FeParity.BRIDGE_MOTOR_FE_T_PER_SU);
        assertThat(g).isCloseTo(629, within(1.0));
        double j = FeParity.parityJPerFe(w);
        assertThat(FeParity.forwardGain(w, j, eta, FeParity.BRIDGE_MOTOR_FE_T_PER_SU)).isCloseTo(0.79, within(0.01)).isLessThan(1);
        assertThat(FeParity.reverseGain(w, j, eta, FeParity.BRIDGE_GENERATOR_FE_T_PER_SU)).isCloseTo(0.77, within(0.02)).isLessThan(1);
        assertThat(FeParity.fePerTick(1e6, eta, j)).isCloseTo(24.25, within(0.01));
    }
}
