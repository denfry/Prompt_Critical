package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** РЗА (07 §7.5.2–7.5.6), автоматы 0,4 кВ (§7.4.6) и пробой изоляции при несовпадении классов (§7.4.8). */
class GridProtectionTest {
    static final double DT = 0.05;
    static final RelayGeneration A1 = RelayGeneration.A1_ELECTROMECHANICAL, A2 = RelayGeneration.A2_STATIC;

    @Test
    @Tag("T7.8")
    void inverseTimeOvercurrentIecStandardInverse() {
        assertThat(IdmtCurve.IEC_SI.tripTimeS(10, 0.1)).isCloseTo(0.297, within(0.003));
        ProtectionRelay relay = ProtectionRelay.Overcurrent.idmt(A1, 100, 0.1, IdmtCurve.IEC_SI);
        RelayState st = relay.newState();
        RelayInputs in = new RelayInputs();
        in.clear();
        in.currentA = 1000;
        int ticks = 1;
        while (!relay.evaluate(in, st, DT)) ticks++;
        assertThat(ticks * DT).isCloseTo(0.30, within(1e-9)); // квантование 1 тик
        in.currentA = 90; // ниже уставки — сброс
        assertThat(relay.evaluate(in, st, DT)).isFalse();
        assertThat(IdmtCurve.IEC_VI.tripTimeS(2, 1)).isCloseTo(13.5, within(1e-9));
        assertThat(IdmtCurve.IEC_EI.tripTimeS(3, 1)).isCloseTo(10, within(1e-9));
        assertThat(IdmtCurve.DEFINITE.tripTimeS(1.5, 0.7)).isEqualTo(0.7);
    }

    @Test
    void relayGenerationsLimitTheFunctions() {
        assertThatThrownBy(() -> ProtectionRelay.Distance.stepped(A1, 10, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProtectionRelay.Differential(ProtectionFunction.F87T, A1, 0.3, 0.3, 0.6, 0.15))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProtectionRelay.Voltage(ProtectionFunction.F81U, A1, 0.7, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProtectionRelay.Differential.standard(ProtectionFunction.F87T, A1).h2Block()).isZero();
    }

    /** Шины 10,5 кВ бесконечной мощности → трансформатор 1 МВ·А 10,5/0,4 с вакуумным выключателем, без нагрузки. */
    static final class TransformerRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId hv, lv;
        final BranchId tr;
        final RelayId diff;
        final GridOutputs out = new GridOutputs();

        TransformerRig(RelayGeneration gen, long seed) {
            g.setRandomSeed(seed);
            hv = g.addBus(VoltageClass.MV_10_5, "hv");
            lv = g.addBus(VoltageClass.LV_0_4, "lv");
            g.addMachine(GridSynchronizationTest.infiniteSource(10.5e3), hv);
            tr = g.addBranch(new Branch.Transformer(hv, lv, 1e6, 0.055, 10.8e3, 1.6e3, 0.01, TapChanger.NONE,
                    TransformerThermal.onanDistribution()));
            g.attachBreaker(tr, BreakerSpec.vacuum());
            g.setBreaker(tr, false);
            diff = g.addRelay(ProtectionRelay.Differential.standard(ProtectionFunction.F87T, gen),
                    new RelayMount.Feeder(tr, true));
            g.initializePowerFlow();
        }

        void ticks(int n) {
            for (int k = 0; k < n; k++) g.stepFast(DT, GridInputs.NONE, out);
        }

        /** Включить и 2 с смотреть; true — дифзащита отключила трансформатор при включении. */
        boolean energizeTrips() {
            g.operateBreaker(tr, true);
            ticks(1); // t_вкл = 0,07 с
            ticks(1);
            assertThat(g.inrushActive(tr) || g.breakerState(tr) != BreakerState.CLOSED).isTrue();
            ticks(40);
            return g.breakerState(tr) == BreakerState.OPEN;
        }
    }

    @Test
    @Tag("T7.9")
    void differentialProtectionRidesThroughInrushAndTripsInternalFaults() {
        TransformerRig r = new TransformerRig(A2, 1);
        assertThat(r.energizeTrips()).as("блокировка по 2-й гармонике").isFalse();
        assertThat(r.g.inrushActive(r.tr)).isFalse();

        r.g.applyFault(r.lv, FaultType.THREE_PHASE, 0); // внешнее КЗ: сквозной ток, торможение
        r.ticks(20);
        assertThat(r.g.breakerState(r.tr)).isEqualTo(BreakerState.CLOSED);
        r.g.clearFault(r.lv);
        r.ticks(2);

        r.g.applyBranchFault(r.tr, 0.5, FaultType.THREE_PHASE, 0, false);
        r.ticks(1);
        assertThat(r.g.relayState(r.diff).operated()).isTrue();
        r.ticks(1); // t_откл вакуумного 0,05 с: 1 тик реле + 1 тик хода
        assertThat(r.g.breakerState(r.tr)).isEqualTo(BreakerState.OPEN);
    }

    @Test
    @Tag("T7.9")
    void electromechanicalDifferentialSometimesTripsOnInrush() {
        int trips = 0, runs = 100;
        for (long seed = 0; seed < runs; seed++) if (new TransformerRig(A1, seed).energizeTrips()) trips++;
        assertThat(trips / (double) runs).isBetween(0.18, 0.42); // p = 0,3
    }

    @Test
    void buchholzTripsOnAnArcInsideTheTank() {
        TransformerRig r = new TransformerRig(A2, 1);
        r.g.addRelay(ProtectionRelay.Buchholz.standard(), new RelayMount.Feeder(r.tr, true));
        r.g.setBreaker(r.tr, true);
        r.ticks(2);
        r.g.applyBranchFault(r.tr, 0.9, FaultType.SINGLE_LINE_GROUND, 0.05, false);
        r.ticks(2);
        assertThat(r.g.breakerState(r.tr)).isEqualTo(BreakerState.OPEN);
    }

    /**
     * Сценарий §7.5.6: три машины по 100 МВ·А (H = 4 с, статизм 4 %, паровые турбины) по 70 МВт, нагрузка
     * 210 МВт (D_L = 1,5) с очередями АЧР по таблице; генераторы с 81U 47,5 Гц/10 с и 47,0 Гц/0,5 с.
     */
    static final class UflsIsland {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final List<BranchId> genBreakers = new ArrayList<>();
        final List<LoadId> staged = new ArrayList<>();
        final GridOutputs out = new GridOutputs();
        final BusId load;

        UflsIsland() {
            g.setRandomSeed(7);
            load = g.addBus(VoltageClass.HV_110, "load");
            for (int i = 0; i < 3; i++) {
                BusId b = g.addBus(VoltageClass.MV_10_5, "g" + i);
                BranchId cb = g.addBranch(new Branch.Line(b, load, 0, 0.05, 0, 1000, null, 1));
                g.attachBreaker(cb, BreakerSpec.oil());
                genBreakers.add(cb);
                SynchronousMachine m = new SynchronousMachine(MachineParams.turbogenerator(100e6, 10.5e3),
                        ExciterSpec.rotating(), PrimeMover.Governed.steamTurbine(100e6));
                m.setLoadTarget(70e6);
                MachineId id = g.addMachine(m, b);
                g.addRelay(new ProtectionRelay.Frequency(ProtectionFunction.F81U, A1, 47.5, 10),
                        new RelayMount.Generator(id, cb));
                g.addRelay(new ProtectionRelay.Frequency(ProtectionFunction.F81U, A1, 47.0, 0.5),
                        new RelayMount.Generator(id, cb));
            }
            double pl = 210e6, rest = pl;
            for (UflsStage s : UflsStage.values()) {
                if (s == UflsStage.NONE) continue;
                double p = (s.ordinal() <= 5 ? 0.08 : 0.05) * pl;
                rest -= p;
                LoadId id = g.addLoad(LoadModel.constantPower(p, 0.3 * p, LoadKind.NPC_CONSUMER).withUflsStage(s)
                        .withDamping(1.5), load);
                g.addRelay(ProtectionRelay.Ufls.standard(s, A1), new RelayMount.Load(id));
                staged.add(id);
            }
            g.addLoad(LoadModel.constantPower(rest, 0.3 * rest, LoadKind.PROCESS).withDamping(1.5), load);
            g.initializePowerFlow();
        }

        double step() {
            g.stepFast(DT, GridInputs.NONE, out);
            return g.busFrequencyHz(load);
        }
    }

    @Test
    @Tag("T7.10")
    void underFrequencyLoadSheddingSavesTheIsland() {
        UflsIsland s = new UflsIsland();
        for (int k = 0; k < 20; k++) s.step();
        List<Integer> shed = new ArrayList<>();
        GridEventSink sink = new GridEventSink() {
            @Override
            public void loadSwitched(int load, boolean connected) {
                if (!connected) shed.add(load);
            }
        };
        s.g.setBreaker(s.genBreakers.get(2), false); // машина отключилась: дефицит 70 МВт
        double fMin = 50;
        for (int k = 0; k < 600; k++) {
            fMin = Math.min(fMin, s.step());
            s.g.drainEvents(sink);
        }
        double f30 = s.g.busFrequencyHz(s.load);
        assertThat(fMin).isBetween(47.8, 48.3);
        assertThat(s.g.breakerState(s.genBreakers.get(0))).isEqualTo(BreakerState.CLOSED);
        assertThat(s.g.breakerState(s.genBreakers.get(1))).isEqualTo(BreakerState.CLOSED);
        assertThat(f30).isGreaterThanOrEqualTo(49.5);
        assertThat(shed).hasSizeBetween(3, 5);
    }

    @Test
    void frequencyRecoveryReconnectsShedLoad() {
        UflsIsland s = new UflsIsland();
        s.step();
        s.g.setBreaker(s.genBreakers.get(2), false);
        for (int k = 0; k < 40; k++) s.step();
        int off = 0;
        for (LoadId l : s.staged) if (s.g.loadPowerW(l) == 0) off++;
        for (int k = 0; k < 2400; k++) s.step(); // ЧАПВ: f ≥ 49,7 Гц 10–60 с
        int offLater = 0;
        for (LoadId l : s.staged) if (s.g.loadPowerW(l) == 0) offLater++;
        assertThat(offLater).isLessThan(off);
        assertThat(offLater).as("дальше — вторичное регулирование игрока").isPositive();
        assertThat(s.g.busFrequencyHz(s.load)).isBetween(49.5, 49.7);
    }

    /** Источник → выключатель ввода → шина 10 кВ → фидер с выключателем и нагрузкой на конце. */
    static final class FeederRig {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId src, bus, end, end2;
        final BranchId incomer, feeder, feeder2;
        final GridOutputs out = new GridOutputs();

        FeederRig() {
            src = g.addBus(VoltageClass.MV_10_5, "src");
            bus = g.addBus(VoltageClass.MV_10_5, "bus");
            end = g.addBus(VoltageClass.MV_10_5, "end");
            end2 = g.addBus(VoltageClass.MV_10_5, "end2");
            g.addMachine(GridSynchronizationTest.infiniteSource(10.5e3), src);
            incomer = g.addBranch(new Branch.Line(src, bus, 0, 0.5, 0, 1000, null, 1));
            feeder = g.addBranch(new Branch.Line(bus, end, 0.2, 0.5, 0, 300, null, 5000));
            feeder2 = g.addBranch(new Branch.Line(bus, end2, 0.2, 0.5, 0, 300, null, 5000));
            for (BranchId b : List.of(incomer, feeder, feeder2)) g.attachBreaker(b, BreakerSpec.oil());
            g.addLoad(LoadModel.constantPower(2e6, 0.5e6, LoadKind.PROCESS), end);
            g.addLoad(LoadModel.constantPower(2e6, 0.5e6, LoadKind.PROCESS), end2);
            g.initializePowerFlow();
        }

        void ticks(int n) {
            for (int k = 0; k < n; k++) g.stepFast(DT, GridInputs.NONE, out);
        }
    }

    @Test
    void autoRecloseRestoresATransientFaultAndLocksOutOnAPermanentOne() {
        FeederRig r = new FeederRig();
        r.g.addRelay(ProtectionRelay.Overcurrent.instantaneous(A1, 2000), new RelayMount.Feeder(r.feeder, true));
        r.g.addRelay(ProtectionRelay.Overcurrent.idmt(A1, 300, 0.5, IdmtCurve.IEC_SI),
                new RelayMount.Feeder(r.feeder, true));
        RelayId ar = r.g.addRelay(ProtectionRelay.AutoReclose.standard(A1), new RelayMount.Feeder(r.feeder, true));
        r.ticks(2);
        r.g.applyBranchFault(r.feeder, 0.4, FaultType.LINE_LINE, 0, true); // перекрытие от молнии
        r.ticks(4);
        assertThat(r.g.breakerState(r.feeder)).isEqualTo(BreakerState.OPEN);
        r.ticks(20 + 10 + 2); // пауза 1 с + t_вкл 0,45 с
        assertThat(r.g.breakerState(r.feeder)).isEqualTo(BreakerState.CLOSED);
        assertThat(r.g.busVoltagePu(r.end)).isGreaterThan(0.9);

        r.ticks(400); // время готовности прошло
        r.g.applyBranchFault(r.feeder, 0.4, FaultType.LINE_LINE, 0, false); // обрыв провода — устойчивое
        r.ticks(4 + 20 + 10 + 6);
        assertThat(r.g.breakerState(r.feeder)).isEqualTo(BreakerState.OPEN);
        r.ticks(60);
        assertThat(r.g.breakerState(r.feeder)).isEqualTo(BreakerState.OPEN);
        assertThat(ProtectionRelay.AutoReclose.lockedOut(r.g.relayState(ar))).isTrue();
    }

    @Test
    void breakerFailureTripsTheAdjacentBreakers() {
        FeederRig r = new FeederRig();
        r.g.addRelay(ProtectionRelay.Overcurrent.instantaneous(A1, 2000), new RelayMount.Feeder(r.feeder, true));
        r.g.addRelay(ProtectionRelay.BreakerFailure.standard(A1), new RelayMount.Feeder(r.feeder, true));
        r.g.setBreakerControlPower(r.feeder, false); // привод без оперативного тока
        r.ticks(2);
        r.g.applyFault(r.end, FaultType.THREE_PHASE, 0);
        r.ticks(1 + 5); // реле + t_УРОВ 0,25 с
        assertThat(r.g.breakerState(r.feeder)).isEqualTo(BreakerState.CLOSED);
        assertThat(r.g.breakerState(r.incomer)).isEqualTo(BreakerState.OPENING);
        assertThat(r.g.breakerState(r.feeder2)).isEqualTo(BreakerState.OPENING);
        r.ticks(2);
        assertThat(r.g.busVoltagePu(r.bus)).isZero();
    }

    @Test
    @Tag("T7.30")
    void lowVoltageBreakerTripCurve() {
        GridConfig cfg = GridConfig.defaults();
        LvTripUnit c = LvTripUnit.breaker(100, LvTripUnit.Curve.C, cfg);
        assertThat(c.tripTimeS(200)).isCloseTo(13.33, within(0.01));
        assertThat(c.tripTimeS(1200)).isZero();
        assertThat(c.tripTimeS(110)).isInfinite();
        assertThat(LvTripUnit.breaker(100, LvTripUnit.Curve.B, cfg).tripTimeS(600)).isZero();
        assertThat(LvTripUnit.breaker(100, LvTripUnit.Curve.D, cfg).tripTimeS(600)).isPositive();

        // в сети: участок 0,4 кВ, ток 2 I_н — отключение через 13,3 с ±1 тик
        PowerGrid g = new PowerGrid(cfg);
        BusId src = g.addBus(VoltageClass.LV_0_4, "src"), sec = g.addBus(VoltageClass.LV_0_4, "section");
        g.addMachine(GridSynchronizationTest.infiniteSource(400), src);
        BranchId cb = g.addBranch(new Branch.Cable(src, sec, 1e-3, 1e-3, 0, 400, 10));
        g.attachBreaker(cb, BreakerSpec.lvAir());
        g.addLoad(LoadModel.constantImpedance(0.4 * Math.sqrt(3) * 400 * 200, 0, LoadKind.HEATER), sec);
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        g.stepFast(DT, GridInputs.NONE, out);
        double i = g.branchCurrentA(cb);
        LvTripUnit unit = LvTripUnit.breaker(i / 2, LvTripUnit.Curve.C, cfg);
        g.attachTripUnit(cb, unit);
        int ticks = 0;
        while (g.breakerState(cb) == BreakerState.CLOSED && ticks < 1000) {
            g.stepFast(DT, GridInputs.NONE, out);
            ticks++;
        }
        assertThat(ticks * DT).isCloseTo(unit.tripTimeS(i), within(DT + 1e-9));
        assertThat(unit.tripTimeS(i)).isCloseTo(13.33, within(0.05));
    }

    @Test
    void fuseBlowsOnAShortCircuitAndMustBeReplaced() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId src = g.addBus(VoltageClass.LV_0_4, "src"), sec = g.addBus(VoltageClass.LV_0_4, "section");
        g.addMachine(GridSynchronizationTest.infiniteSource(400), src);
        BranchId fuse = g.addBranch(new Branch.Cable(src, sec, 0.5, 0.5, 0, 100, 10));
        g.attachTripUnit(fuse, LvTripUnit.fuse(100, g.config()));
        g.initializePowerFlow();
        g.applyFault(sec, FaultType.SINGLE_LINE_GROUND, 0);
        GridOutputs out = new GridOutputs();
        for (int k = 0; k < 3; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.fuseBlown(fuse)).isTrue();
        assertThat(g.isBreakerClosed(fuse)).isFalse();
        assertThatThrownBy(() -> g.setBreaker(fuse, true)).isInstanceOf(IllegalStateException.class);
        g.clearFault(sec);
        g.replaceFuse(fuse);
        g.setBreaker(fuse, true);
        for (int k = 0; k < 3; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.busVoltagePu(sec)).isGreaterThan(0.9);
    }

    @Test
    @Tag("T7.27")
    void motorOfTheWrongVoltageClassBreaksDownAndIsCleared() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId src = g.addBus(VoltageClass.MV_6_3, "src"), bus = g.addBus(VoltageClass.MV_6_3, "cell");
        g.addMachine(GridSynchronizationTest.infiniteSource(6300), src);
        BranchId cell = g.addBranch(new Branch.Cable(src, bus, 0.1, 0.5, 0, 400, 100)); // I_КЗ ≈ 18 кА < 25
        g.attachBreaker(cell, BreakerSpec.vacuum());
        g.addRelay(ProtectionRelay.Overcurrent.instantaneous(A1, 3000), new RelayMount.Feeder(cell, true));
        g.initializePowerFlow();
        MotorId m = g.addMotor(InductionMotorSpec.standard(55e3, 400), bus); // 0,4 кВ на шине 6 кВ: k ≈ 16
        List<Integer> breakdowns = new ArrayList<>();
        GridEventSink sink = new GridEventSink() {
            @Override
            public void insulationBreakdown(int b) {
                breakdowns.add(b);
            }
        };
        GridOutputs out = new GridOutputs();
        int ticks = 0;
        while (g.breakerState(cell) == BreakerState.CLOSED && ticks < 20) {
            g.stepFast(DT, GridInputs.NONE, out);
            g.drainEvents(sink);
            ticks++;
        }
        assertThat(ticks).isLessThanOrEqualTo(3);
        assertThat(breakdowns).containsExactly(bus.index());
        assertThat(g.motorState(m)).isEqualTo(MotorState.FAULTED);
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.breakerState(cell)).isEqualTo(BreakerState.OPEN);
        assertThat(VoltageClass.LV_0_4.breakdownRatio()).isCloseTo(6.25, within(1e-9));
        assertThat(VoltageClass.MV_10_5.breakdownRatio()).isCloseTo(4, within(1e-9));
    }

    @Test
    void motorThermalRelayTripsAnOverloadedMotorInMinutes() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId bus = g.addBus(VoltageClass.MV_6_3, "bus");
        g.addMachine(GridSynchronizationTest.infiniteSource(6300), bus);
        MotorId m = g.addMotor(InductionMotorSpec.standard(1e6, 6300), bus);
        g.setMotorLoadTorque(m, 1.4);
        g.addRelay(ProtectionRelay.Thermal.standard(A1), new RelayMount.Motor(m));
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        double t = 0;
        while (g.motorState(m) != MotorState.OFF && t < 7200) {
            g.stepFast(DT, GridInputs.NONE, out);
            g.stepSlow(10);
            t += 10;
        }
        assertThat(t).isBetween(60.0, 3600.0);
        assertThat(g.motorWindingTemperatureC(m)).isGreaterThan(140);
    }
}
