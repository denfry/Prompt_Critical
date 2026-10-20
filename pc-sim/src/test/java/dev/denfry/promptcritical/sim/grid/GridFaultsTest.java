package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Несимметричные КЗ, КЗ внутри ветви и «земля» в сети с изолированной нейтралью (07 §7.5.1). */
class GridFaultsTest {
    static final double DT = 0.05;

    /** Шины бесконечной мощности → чисто реактивная линия x = 0,1 о.е. → шина КЗ без нагрузки. */
    static final class Radial {
        final PowerGrid g;
        final BusId src, far;
        final BranchId line;
        final GridOutputs out = new GridOutputs();

        Radial(VoltageClass vc, GridConfig cfg) {
            g = new PowerGrid(cfg);
            src = g.addBus(vc, "src");
            far = g.addBus(vc, "far");
            g.addMachine(GridSynchronizationTest.infiniteSource(vc.volts), src);
            line = g.addBranch(new Branch.Line(src, far, 0, 0.1, 0, 1000, null, 1000));
            g.initializePowerFlow();
        }

        Radial(VoltageClass vc) {
            this(vc, GridConfig.defaults());
        }

        double faultA(FaultType type) {
            g.applyFault(far, type, 0);
            ticks(1);
            double a = g.faultCurrentA(far);
            g.clearFault(far);
            ticks(1);
            return a;
        }

        void ticks(int n) {
            for (int k = 0; k < n; k++) g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    void unsymmetricalFaultCurrentsFollowTheNeutralMode() {
        // 0,4 кВ, глухозаземлённая нейтраль: z₀ ≈ z₁ ⇒ I_1ф = 3V/(z₁ + z₂ + z₀) = I_3ф
        Radial lv = new Radial(VoltageClass.LV_0_4);
        double i3 = lv.faultA(FaultType.THREE_PHASE);
        assertThat(i3 / (100e6 / (Math.sqrt(3) * 400))).isCloseTo(10, within(0.02)); // 1/0,1 о.е.
        assertThat(lv.faultA(FaultType.SINGLE_LINE_GROUND) / i3).isCloseTo(1, within(0.01));
        assertThat(lv.faultA(FaultType.LINE_LINE) / i3).isCloseTo(Math.sqrt(3) / 2, within(0.01));
        // 110 кВ, эффективно заземлённая: z₀ ≈ 3z₁ ⇒ I_1ф = 3/5·I_3ф; двухфазное на землю — 0,892·I_3ф
        Radial hv = new Radial(VoltageClass.HV_110);
        double h3 = hv.faultA(FaultType.THREE_PHASE);
        assertThat(hv.faultA(FaultType.SINGLE_LINE_GROUND) / h3).isCloseTo(0.6, within(0.01));
        assertThat(hv.faultA(FaultType.LINE_LINE_GROUND) / h3).isCloseTo(0.892, within(0.01));
        // ток линии при однофазном КЗ — фазный, с обратной и нулевой последовательностями
        hv.g.applyFault(hv.far, FaultType.SINGLE_LINE_GROUND, 0);
        hv.ticks(1);
        assertThat(hv.g.branchCurrentA(hv.line) / hv.g.faultCurrentA(hv.far)).isCloseTo(1, within(0.01));
        // здоровые фазы в месте КЗ: V₁ = 0,8, V₂ = −0,2, V₀ = −0,6 ⇒ |V_b| = 1,25 (коэффициент замыкания на землю)
        assertThat(hv.g.busMaxPhaseVoltagePu(hv.far)).isCloseTo(1.249, within(0.01));
        assertThat(hv.g.busMaxPhaseVoltagePu(hv.src)).isCloseTo(1, within(0.01));
    }

    @Test
    void faultInsideALineIsSplitByKronReduction() {
        Radial r = new Radial(VoltageClass.MV_10_5);
        double end = r.faultA(FaultType.THREE_PHASE);
        r.g.applyBranchFault(r.line, 0.5, FaultType.THREE_PHASE, 0, false);
        r.ticks(1);
        assertThat(r.g.branchFaultCurrentA(r.line) / end).isCloseTo(2, within(0.02)); // x·z вдвое меньше
        assertThat(r.g.busVoltagePu(r.far)).isLessThan(0.01); // за местом КЗ — без питания
        assertThat(Math.abs(r.out.imbalanceW()) / r.g.config().baseVa()).isLessThan(1e-6);
        assertThat(r.out.lossW()).isGreaterThan(0); // мощность дуги учтена в потерях ветви
        r.g.clearBranchFault(r.line);
        r.ticks(2);
        assertThat(r.g.busVoltagePu(r.far)).isCloseTo(1, within(0.01));
    }

    @Test
    void selfClearingFaultDisappearsWhenTheLineIsDeenergized() {
        Radial r = new Radial(VoltageClass.MV_10_5);
        r.g.applyBranchFault(r.line, 0.3, FaultType.LINE_LINE, 0, true);
        r.ticks(1);
        assertThat(r.g.branchFaultCurrentA(r.line)).isPositive();
        r.g.setBreaker(r.line, false);
        r.ticks(1);
        r.g.setBreaker(r.line, true);
        r.ticks(2);
        assertThat(r.g.branchFaultCurrentA(r.line)).isZero();
        assertThat(r.g.busVoltagePu(r.far)).isCloseTo(1, within(0.01));
    }

    @Test
    @Tag("T7.26")
    void singleLineGroundInAnIsolatedNetworkIsNotAShortCircuit() {
        Radial r = new Radial(VoltageClass.MV_10_5);
        r.g.attachBreaker(r.line, BreakerSpec.vacuum());
        r.g.addRelay(ProtectionRelay.Overcurrent.idmt(RelayGeneration.A1_ELECTROMECHANICAL, 600, 0.1,
                IdmtCurve.IEC_SI), new RelayMount.Feeder(r.line, true));
        List<Boolean> ground = new ArrayList<>();
        GridEventSink sink = new GridEventSink() {
            @Override
            public void groundFault(int bus, boolean active) {
                ground.add(active);
            }
        };
        r.g.applyFault(r.far, FaultType.SINGLE_LINE_GROUND, 0);
        for (int k = 0; k < 40; k++) {
            r.ticks(1);
            r.g.drainEvents(sink);
        }
        assertThat(r.g.breakerState(r.line)).isEqualTo(BreakerState.CLOSED);
        assertThat(ground).containsExactly(true);
        assertThat(r.g.groundFaultPresent(r.src)).isTrue();
        assertThat(r.g.faultCurrentA(r.far)).isZero();
        double v = r.g.busVoltagePu(r.far);
        assertThat(r.g.busMaxPhaseVoltagePu(r.far)).isCloseTo(Math.sqrt(3) * v, within(1e-9));
        r.g.clearFault(r.far);
        r.g.drainEvents(sink);
        assertThat(ground).containsExactly(true, false);
    }

    @Test
    void groundFaultEscalatesIntoADoubleEarthFaultAndIsTripped() {
        Radial r = new Radial(VoltageClass.MV_10_5, GridConfig.defaults().withGroundFaultEscalationPerS(10));
        r.g.attachBreaker(r.line, BreakerSpec.vacuum());
        r.g.addRelay(ProtectionRelay.Overcurrent.instantaneous(RelayGeneration.A1_ELECTROMECHANICAL, 3000),
                new RelayMount.Feeder(r.line, true));
        r.g.applyFault(r.far, FaultType.SINGLE_LINE_GROUND, 1.0); // z_f держит ток ниже 25 кА вакуумного
        r.ticks(40);
        assertThat(r.g.groundFaultPresent(r.src)).isFalse();
        assertThat(r.g.breakerState(r.line)).isEqualTo(BreakerState.OPEN);
    }

    @Test
    void distanceProtectionZonesFollowTheFaultLocation() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId src = g.addBus(VoltageClass.HV_110, "src"), far = g.addBus(VoltageClass.HV_110, "far");
        g.addMachine(GridSynchronizationTest.infiniteSource(110e3), src);
        BranchId line = g.addBranch(Branch.Line.overhead(src, far, ConductorSpec.ac120(), 0.43, 30e3,
                VoltageClass.HV_110, g.config().baseVa()));
        g.addLoad(LoadModel.constantPower(20e6, 5e6, LoadKind.PROCESS), far);
        g.attachBreaker(line, BreakerSpec.sf6());
        double zLine = Math.hypot(0.249, 0.43) * 30, angle = Math.atan2(0.43, 0.249);
        RelayId relay = g.addRelay(ProtectionRelay.Distance.stepped(RelayGeneration.A2_STATIC, zLine, angle),
                new RelayMount.Feeder(line, true));
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        for (int k = 0; k < 10; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.relayState(relay).operated()).as("нагрузка далеко от характеристики").isFalse();

        g.applyBranchFault(line, 0.5, FaultType.THREE_PHASE, 0, true);
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.relayState(relay).operated()).as("ступень 1 — без выдержки").isTrue();
        for (int k = 0; k < 3; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.breakerState(line)).isEqualTo(BreakerState.OPEN);

        g.setBreaker(line, true);
        for (int k = 0; k < 4; k++) g.stepFast(DT, GridInputs.NONE, out);
        g.applyFault(far, FaultType.THREE_PHASE, 0); // конец линии — за ступенью 1
        int ticks = 0;
        while (g.breakerState(line) == BreakerState.CLOSED && ticks < 100) {
            g.stepFast(DT, GridInputs.NONE, out);
            ticks++;
        }
        assertThat(ticks * DT).as("ступень 2: 0,4 с").isCloseTo(0.4, within(DT + 1e-9));
    }
}
