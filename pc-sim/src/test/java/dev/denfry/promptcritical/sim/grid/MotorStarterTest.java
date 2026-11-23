package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Способы пуска асинхронного двигателя (07 §7.6.3): звезда–треугольник, плавный пуск, частотный привод. */
class MotorStarterTest {
    static final double DT = 0.05;

    /** Двигатель на шине бесконечной мощности; ток — в долях номинального. */
    static final class Rig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final GridOutputs out = new GridOutputs();
        final InductionMotorSpec spec;
        final MotorId m;
        final double iRated;

        Rig(VoltageClass vc, double pRatedW, MotorStarter starter, double loadTorque) {
            BusId bus = g.addBus(vc, "bus");
            g.addMachine(GridSynchronizationTest.infiniteSource(vc.volts), bus);
            g.initializePowerFlow();
            spec = InductionMotorSpec.standard(pRatedW, vc.volts);
            m = g.addMotor(spec, bus);
            g.setMotorStarter(m, starter);
            g.setMotorLoadTorque(m, loadTorque);
            iRated = 1 / spec.ratedEfficiency() / spec.ratedPowerFactor() * pRatedW / (Math.sqrt(3) * vc.volts);
        }

        double currentPu() {
            return g.motorCurrentA(m) / iRated;
        }

        void step() {
            g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    void starDeltaStartsWithAThirdOfTheCurrentAndKicksOnTheSwitchOver() {
        Rig r = new Rig(VoltageClass.LV_0_4, 100e3, MotorStarter.STAR_DELTA, 0.2);
        r.step();
        assertThat(r.currentPu()).isCloseTo(r.spec.iStartPu() / 3, within(0.05 * r.spec.iStartPu()));
        assertThat(r.g.motorTorquePu(r.m)).isCloseTo(r.spec.torquePu(1, 1) / 3, within(0.02));
        double starTorque = 0, kick = 0;
        boolean delta = false;
        for (int k = 0; k < 400 && !delta; k++) {
            starTorque = r.g.motorTorquePu(r.m);
            r.step();
            // переключение — внутри шага, когда скорость перешла уставку реле времени
            if (r.g.motorSpeedPu(r.m) >= MotorStarter.STAR_DELTA_SWITCH_SPEED) {
                delta = true;
                kick = r.g.motorTorquePu(r.m);
            }
        }
        assertThat(delta).as("switched to delta").isTrue();
        // на переключении обмотка получает полное напряжение при скольжении ≈ 0,1: момент и ток втрое выше звезды
        // (ток 3–4 I_н, §7.6.3); малый двигатель дотягивает до рабочей скорости за доли тика
        assertThat(kick).isGreaterThan(2 * starTorque);
        for (int k = 0; k < 40; k++) r.step();
        assertThat(r.g.motorState(r.m)).isEqualTo(MotorState.RUNNING);
    }

    @Test
    void starDeltaIsNotBuiltForMediumVoltage() {
        Rig r = new Rig(VoltageClass.MV_6_3, 1e6, MotorStarter.STAR_DELTA, 0.2);
        assertThat(r.g.motorStarter(r.m)).isEqualTo(MotorStarter.DIRECT);
        r.step();
        assertThat(r.currentPu()).isCloseTo(r.spec.iStartPu(), within(0.05 * r.spec.iStartPu()));
    }

    @Test
    void softStarterHoldsTheCurrentLimitAndRunsALightLoadUp() {
        Rig r = new Rig(VoltageClass.LV_0_4, 100e3, MotorStarter.SOFT, 0.1);
        double peak = 0;
        for (int k = 0; k < 800 && r.g.motorState(r.m) != MotorState.RUNNING; k++) {
            r.step();
            peak = Math.max(peak, r.currentPu());
        }
        assertThat(peak).isLessThanOrEqualTo(MotorStarter.SOFT_CURRENT_LIMIT_PU * 1.05);
        assertThat(r.g.motorState(r.m)).isEqualTo(MotorState.RUNNING);
        for (int k = 0; k < 40; k++) r.step();
        assertThat(r.g.motorSlip(r.m)).isCloseTo(r.spec.slipAt(0.1, 1), within(2e-3));
    }

    @Test
    void softStarterCannotBreakAwayAHeavyLoad() {
        // ток 3 I_н при I_пуск 6,5 I_н: напряжение 0,46, момент трогания 0,73·0,46² ≈ 0,16 — меньше 0,5
        Rig r = new Rig(VoltageClass.LV_0_4, 100e3, MotorStarter.SOFT, 0.5);
        for (int k = 0; k < 600; k++) r.step();
        assertThat(r.g.motorSpeedPu(r.m)).isLessThan(0.05);
        assertThat(r.g.motorState(r.m)).isEqualTo(MotorState.STALLED);
    }

    @Test
    void driveRunsAtItsReferenceWithinOneAndAHalfRatedCurrent() {
        Rig r = new Rig(VoltageClass.LV_0_4, 100e3, MotorStarter.VFD, 0.5);
        r.g.setMotorSpeedReference(r.m, 0.5);
        double peak = 0;
        for (int k = 0; k < 400; k++) {
            r.step();
            peak = Math.max(peak, r.currentPu());
        }
        assertThat(peak).isLessThan(1.7);
        assertThat(r.g.motorSpeedPu(r.m)).isCloseTo(0.5, within(0.01));
        assertThat(r.g.motorState(r.m)).isEqualTo(MotorState.RUNNING);
        // из сети — мощность вала через КПД привода: T·ω/η
        double shaft = 0.5 * 0.5 * r.spec.pRatedW();
        assertThat(r.g.motorPowerW(r.m)).isCloseTo(shaft / MotorStarter.VFD_EFFICIENCY, within(0.02 * shaft));
    }

    @Test
    void softStartCutsTheVoltageDipOfAOneMegawattMotorOnAOneMvaTransformer() {
        // 07 §7.6.3: прямой пуск 1 МВт от трансформатора 1 МВ·А (S_КЗ ≈ 18 МВ·А) — провал ≈ 30 %
        double direct = dip(MotorStarter.DIRECT), soft = dip(MotorStarter.SOFT);
        assertThat(direct).isBetween(0.2, 0.4);
        assertThat(soft).isLessThan(direct * 0.6);
    }

    private static double dip(MotorStarter starter) {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId src = g.addBus(VoltageClass.MV_6_3, "src"), bus = g.addBus(VoltageClass.MV_6_3, "load");
        g.addMachine(GridSynchronizationTest.infiniteSource(6300), src);
        g.addBranch(new Branch.Transformer(src, bus, 1e6, 0.055, 10e3, 2e3, 0.01, TapChanger.NONE, null));
        g.initializePowerFlow();
        MotorId m = g.addMotor(InductionMotorSpec.standard(1e6, 6300), bus);
        g.setMotorStarter(m, starter);
        g.setMotorLoadTorque(m, 0.1);
        GridOutputs out = new GridOutputs();
        double min = 1;
        for (int k = 0; k < 10; k++) {
            g.stepFast(DT, GridInputs.NONE, out);
            min = Math.min(min, g.busVoltagePu(bus));
        }
        return 1 - min;
    }
}
