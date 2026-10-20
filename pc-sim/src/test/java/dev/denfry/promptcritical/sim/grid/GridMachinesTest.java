package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Асинхронный двигатель (07 §7.6.3), режимы АРВ и ограничители (§7.3.3–7.3.4), типовые нагрузки (§7.6.1). */
class GridMachinesTest {
    static final double DT = 0.05;

    /** Шины бесконечной мощности 6,3 кВ — трансформатор с отпайками 1/15 — двигатель 1 МВт под моментом 1 о.е. */
    static final class MotorRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BranchId tr;
        final MotorId motor;
        final BusId bus;
        final GridOutputs out = new GridOutputs();

        MotorRig() {
            BusId src = g.addBus(VoltageClass.MV_6_3, "src");
            bus = g.addBus(VoltageClass.MV_6_3, "motor");
            g.addMachine(GridSynchronizationTest.infiniteSource(6300), src);
            tr = g.addBranch(new Branch.Transformer(src, bus, 1e9, 0.1, 1e6, 0, 0,
                    new TapChanger(0, 10, 1 / 15.0, 5, true), null));
            motor = g.addMotor(InductionMotorSpec.standard(1e6, 6300), bus);
            g.setMotorLoadTorque(motor, 1);
            g.initializePowerFlow();
        }

        void ticks(int n) {
            for (int k = 0; k < n; k++) g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    @Tag("T7.22")
    void motorKeepsRunningAt075AndStallsAt06() {
        MotorRig ok = new MotorRig();
        InductionMotorSpec spec = ok.g.motorSpec(ok.motor);
        assertThat(spec.ratedSlip()).isCloseTo(0.031, within(0.0005));
        assertThat(spec.torquePu(1, 1)).isCloseTo(0.73, within(0.005));
        assertThat(ok.g.motorState(ok.motor)).isEqualTo(MotorState.RUNNING);
        ok.g.setTap(ok.tr, 5); // v = 1/(1 + 5/15) = 0,75: T_max = 1,41
        ok.ticks(20);
        double v = ok.g.busVoltagePu(ok.bus);
        assertThat(v).isCloseTo(0.75, within(0.005));
        assertThat(ok.g.motorState(ok.motor)).isEqualTo(MotorState.RUNNING);
        assertThat(ok.g.motorSlip(ok.motor)).isCloseTo(spec.slipAt(1, v), within(1e-3));
        assertThat(ok.g.motorTorquePu(ok.motor)).isCloseTo(1, within(1e-3));

        MotorRig stall = new MotorRig();
        stall.g.setTap(stall.tr, 10); // v = 0,6: T_max = 0,90 < T_нагр
        int t = 0;
        while (stall.g.motorState(stall.motor) != MotorState.STALLED && t < 40) {
            stall.ticks(1);
            t++;
        }
        assertThat(stall.g.busVoltagePu(stall.bus)).isCloseTo(0.6, within(0.005));
        assertThat(t * DT).as("опрокидывание за ≤ 1 с").isLessThanOrEqualTo(1.0);
        stall.ticks(100);
        assertThat(stall.g.motorSpeedPu(stall.motor)).isZero();
        assertThat(stall.g.motorSlip(stall.motor)).isCloseTo(1, within(1e-9));
    }

    @Test
    void ratedPointMatchesTheCatalogue() {
        MotorRig r = new MotorRig();
        r.ticks(20);
        InductionMotorSpec spec = r.g.motorSpec(r.motor);
        double p = r.g.motorPowerW(r.motor), q = r.g.motorReactiveVar(r.motor);
        double pEl = (1 - spec.ratedSlip()) / spec.ratedEfficiency();
        assertThat(p).isCloseTo(pEl * 1e6, within(1e-3 * p));
        assertThat(p / Math.hypot(p, q)).isCloseTo(spec.ratedPowerFactor(), within(1e-3));
        assertThat(spec.ratedPowerFactor()).isCloseTo(0.88, within(1e-12));
        assertThat(InductionMotorSpec.standard(15e3, 400).ratedEfficiency()).isCloseTo(0.91, within(1e-12));
        assertThat(InductionMotorSpec.standard(300e3, 400).ratedEfficiency()).isBetween(0.945, 0.96);
        assertThat(r.g.motorShaftSpeedRadS(r.motor))
                .isCloseTo((1 - spec.ratedSlip()) * 2 * Math.PI * 1500 / 60, within(0.05));
        assertThat(Math.abs(r.out.imbalanceW()) / r.g.config().baseVa()).isLessThan(1e-6);
    }

    @Test
    void directOnLineStartDrawsStartingCurrentAndAccelerates() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId bus = g.addBus(VoltageClass.MV_6_3, "bus");
        g.addMachine(GridSynchronizationTest.infiniteSource(6300), bus);
        g.initializePowerFlow();
        InductionMotorSpec spec = InductionMotorSpec.standard(1e6, 6300);
        MotorId m = g.addMotor(spec, bus);
        g.setMotorLoadTorque(m, 0.5);
        GridOutputs out = new GridOutputs();
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.motorState(m)).isEqualTo(MotorState.STARTING);
        double pEl = (1 - spec.ratedSlip()) / spec.ratedEfficiency();
        double iRated = pEl / spec.ratedPowerFactor() * 1e6 / (Math.sqrt(3) * 6300);
        assertThat(g.motorCurrentA(m) / iRated).isCloseTo(spec.iStartPu(), within(0.03 * spec.iStartPu()));
        for (int k = 0; k < 100; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.motorState(m)).isEqualTo(MotorState.RUNNING);
        assertThat(g.motorSlip(m)).isCloseTo(spec.slipAt(0.5, 1), within(1e-3));
        assertThat(Math.abs(out.imbalanceW()) / g.config().baseVa()).isLessThan(1e-6);

        g.setMotorConnected(m, false); // выбег под моментом нагрузки 0,5: 2H·Δω/T = 2 с
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.motorState(m)).isEqualTo(MotorState.OFF);
        assertThat(g.motorPowerW(m)).isZero();
        for (int k = 0; k < 60; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.motorSpeedPu(m)).isZero();
    }

    @Test
    void motorLoadTorqueComesFromCreate() {
        MotorRig r = new MotorRig();
        GridInputs overload = new GridInputs() {
            @Override
            public double motorLoadTorquePu(int motor) {
                return 3.0; // больше T_max = 2,5: опрокидывается
            }
        };
        for (int k = 0; k < 20; k++) r.g.stepFast(DT, overload, r.out);
        assertThat(r.g.motorState(r.motor)).isEqualTo(MotorState.STALLED);
    }

    /** Турбогенератор 50 МВ·А, 40 МВт, через трансформатор 63 МВ·А на шины бесконечной мощности 110 кВ. */
    static final class GenRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final SynchronousMachine m;
        final GridOutputs out = new GridOutputs();

        GenRig(ExciterSpec exciter) {
            BusId hv = g.addBus(VoltageClass.HV_110, "hv"), lv = g.addBus(VoltageClass.MV_10_5, "gen");
            g.addMachine(GridSynchronizationTest.infiniteSource(110e3), hv);
            g.addBranch(new Branch.Transformer(hv, lv, 63e6, 0.105, 250e3, 50e3, 0.005, TapChanger.NONE, null));
            m = new SynchronousMachine(MachineParams.turbogenerator(50e6, 10.5e3), exciter,
                    PrimeMover.Governed.steamTurbine(50e6));
            m.setLoadTarget(40e6);
            g.addMachine(m, lv);
            g.initializePowerFlow();
        }

        void seconds(double s) {
            for (int k = 0; k < Math.round(s / DT); k++) g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    void reactivePowerModeHoldsTheSetpoint() {
        GenRig r = new GenRig(ExciterSpec.rotating());
        r.m.setExcitationMode(ExcitationMode.REACTIVE);
        assertThat(r.m.excitationMode()).isEqualTo(ExcitationMode.REACTIVE);
        r.m.setReactiveTarget(10e6);
        r.seconds(60);
        assertThat(r.m.reactivePowerVar()).isCloseTo(10e6, within(0.2e6));
        assertThat(r.m.electricalPowerW()).isCloseTo(40e6, within(0.5e6));
        assertThatThrownBy(() -> r.m.setReactiveTarget(60e6)).isInstanceOf(IllegalArgumentException.class);
        assertThat(r.m.maxReactiveVar(40e6)).isBetween(25e6, 30e6); // статор: √(50² − 40²) = 30 Мвар
        assertThat(r.m.minReactiveVar(40e6)).isNegative();
    }

    @Test
    void powerFactorModeHoldsCosPhi() {
        GenRig r = new GenRig(ExciterSpec.rotating());
        r.m.setExcitationMode(ExcitationMode.POWER_FACTOR);
        r.m.setPowerFactorTarget(0.95);
        r.seconds(60);
        double p = r.m.electricalPowerW(), q = r.m.reactivePowerVar();
        assertThat(q).isPositive();
        assertThat(p / Math.hypot(p, q)).isCloseTo(0.95, within(0.003));
        assertThatThrownBy(() -> r.m.setPowerFactorTarget(0.3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modeSwitchIsBumpless() {
        GenRig r = new GenRig(ExciterSpec.rotating());
        r.seconds(5);
        double efd = r.m.fieldVoltagePu(), q = r.m.reactivePowerVar();
        r.m.setExcitationMode(ExcitationMode.MANUAL);
        r.seconds(5);
        r.m.setExcitationMode(ExcitationMode.REACTIVE);
        r.seconds(10);
        assertThat(r.m.fieldVoltagePu()).isCloseTo(efd, within(1e-3));
        assertThat(r.m.reactivePowerVar()).isCloseTo(q, within(0.05e6));
    }

    @Test
    void overExcitationLimiterActsAfterInverseTime() {
        GenRig r = new GenRig(ExciterSpec.thyristor());
        double ifNom = MachineParams.turbogenerator(50e6, 10.5e3).ratedFieldPu();
        assertThat(ifNom).isCloseTo(2.53, within(0.01));
        r.m.setVoltageReference(1.3); // АРВ упирается в потолок 5 о.е. ≈ 2·i_f,ном → выдержка ≈ 10 с
        r.seconds(9);
        assertThat(r.m.overExcitationLimited()).isFalse();
        assertThat(r.m.fieldVoltagePu()).isCloseTo(5, within(0.01));
        r.seconds(2);
        assertThat(r.m.overExcitationLimited()).isTrue();
        assertThat(r.m.fieldVoltagePu()).isLessThanOrEqualTo(ifNom + 1e-9);
        r.m.setVoltageReference(1.0);
        r.seconds(5);
        assertThat(r.m.fieldVoltagePu()).isLessThanOrEqualTo(ifNom + 1e-9);
        r.seconds(40); // инерционное звено АРВ T_B = 10 с «помнит» насыщение
        assertThat(r.m.overExcitationLimited()).isFalse();
    }

    @Test
    void underExcitationLimiterHoldsTheMinimumReactivePower() {
        GenRig r = new GenRig(ExciterSpec.thyristor());
        r.m.setVoltageReference(0.85); // без UEL машина ушла бы в глубокое недовозбуждение
        r.seconds(40);
        double qMin = r.m.minReactiveVar(r.m.electricalPowerW());
        assertThat(r.m.reactivePowerVar()).isCloseTo(qMin, within(0.03 * 50e6));
        assertThat(r.m.poleSlips()).isZero();
    }

    @Test
    void typicalLoadsFollowTheTable() {
        LoadModel light = LoadModel.typical(LoadKind.LIGHTING, 1e6);
        assertThat(light.q0Var() / light.p0W()).isCloseTo(Math.tan(Math.acos(0.95)), within(1e-12));
        assertThat(light.aZ()).isEqualTo(0.5);
        LoadModel city = LoadModel.typical(LoadKind.NPC_CONSUMER, 1e6);
        assertThat(city.dF()).isEqualTo(1.5);
        assertThat(city.aP()).isEqualTo(0.5);
        assertThat(LoadModel.typical(LoadKind.HEATER, 1e6).q0Var()).isZero();
        assertThat(LoadModel.typical(LoadKind.PROCESS, 1e6).withUflsStage(UflsStage.ACHR1_2).uflsStage())
                .isEqualTo(UflsStage.ACHR1_2);
        assertThatThrownBy(() -> LoadModel.typical(LoadKind.ARC_FURNACE, 1e6))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
