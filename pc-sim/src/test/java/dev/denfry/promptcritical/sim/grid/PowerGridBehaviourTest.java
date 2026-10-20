package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Поведение каркаса сети вне таблицы 07 §7.24: инициализация, острова, переразложение, ошибки. */
class PowerGridBehaviourTest {
    static final double DT = 0.05;

    @Test
    void initialStateIsStationary() {
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        double[] p0 = new double[r.machines.size()];
        for (int i = 0; i < p0.length; i++) p0[i] = r.grid.machine(r.machines.get(i)).electricalPowerW();
        for (int k = 0; k < 200; k++) r.grid.stepFast(DT, GridInputs.NONE, out);
        assertThat(r.grid.island(0).frequencyHz()).isCloseTo(50, within(1e-6));
        for (int i = 0; i < p0.length; i++) {
            SynchronousMachine m = r.grid.machine(r.machines.get(i));
            assertThat(m.electricalPowerW()).isCloseTo(p0[i], within(1.0));
            assertThat(m.terminalVoltagePu()).isCloseTo(1.02, within(1e-6));
            assertThat(m.state()).isEqualTo(MachineState.LOADED);
        }
        // не балансирующие машины держат задание, потери покрывает балансирующая
        assertThat(p0[3]).isCloseTo(30e6, within(1.0));
        assertThat(out.lossW()).isBetween(1e5, 1e7);
    }

    @Test
    void openingTheOnlyFeederDeenergizesTheSubstation() {
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        List<Integer> dead = new ArrayList<>();
        r.grid.drainEvents(new GridEventSink() {});
        r.grid.setBreaker(r.feeders.get(30), false); // mv30 без кабельной перемычки
        r.grid.stepFast(DT, GridInputs.NONE, out);
        r.grid.drainEvents(new GridEventSink() {
            @Override
            public void islandDeenergized(int island) {
                dead.add(island);
            }
        });
        int island = r.grid.islandOf(r.mv.get(30));
        assertThat(r.grid.islandCount()).isEqualTo(2);
        assertThat(dead).containsExactly(island);
        assertThat(r.grid.island(island).energized()).isFalse();
        assertThat(r.grid.busVoltagePu(r.mv.get(30))).isZero();
        assertThat(r.grid.loadPowerW(r.loads.get(30))).isZero();
        assertThat(r.grid.busFrequencyHz(r.mv.get(30))).isZero();
        r.grid.setBreaker(r.feeders.get(30), true);
        for (int k = 0; k < 40; k++) r.grid.stepFast(DT, GridInputs.NONE, out);
        assertThat(r.grid.islandCount()).isEqualTo(1);
        assertThat(r.grid.busVoltagePu(r.mv.get(30))).isGreaterThan(0.9);
        assertThat(r.grid.loadPowerW(r.loads.get(30))).isPositive();
    }

    @Test
    void refactoringIsLimitedPerTickAndOthersWaitFrozen() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId hub = g.addBus(VoltageClass.MV_10_5, "hub");
        List<BranchId> lines = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BusId b = g.addBus(VoltageClass.MV_10_5, "g" + i);
            lines.add(g.addBranch(new Branch.Line(b, hub, 0.01, 0.1, 0, 1000, null, 1)));
            SynchronousMachine m = new SynchronousMachine(MachineParams.turbogenerator(50e6, 10.5e3),
                    ExciterSpec.rotating(), PrimeMover.Governed.steamTurbine(50e6));
            m.setLoadTarget(20e6);
            g.addMachine(m, b);
            g.addLoad(LoadModel.constantImpedance(15e6, 3e6, LoadKind.HEATER), b);
        }
        g.addLoad(LoadModel.constantPower(15e6, 5e6, LoadKind.PROCESS), hub);
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        for (BranchId l : lines) g.setBreaker(l, false);
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(out.islands()).isEqualTo(4);
        assertThat(out.pendingIslands()).isEqualTo(1); // grid.max_refactor_per_tick = 2
        g.stepFast(DT, GridInputs.NONE, out);
        assertThat(out.pendingIslands()).isZero();
        // каждая машина теперь кормит свою нагрузку 15 МВт: частота островов растёт
        for (int k = 0; k < 20; k++) g.stepFast(DT, GridInputs.NONE, out);
        assertThat(g.island(g.islandOf(new BusId(1))).frequencyHz()).isGreaterThan(50.05);
        assertThat(g.island(g.islandOf(hub)).energized()).isFalse();
    }

    @Test
    void loadSwitchingNeedsNoRefactoring() {
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        for (int i = 0; i < 20; i++) r.grid.setLoadConnected(r.loads.get(i), false); // АЧР «десятки нагрузок за тик»
        r.grid.stepFast(DT, GridInputs.NONE, out);
        assertThat(out.pendingIslands()).isZero();
        assertThat(r.grid.island(0).rocofHzPerS()).isPositive();
    }

    @Test
    void rejectsWhatTheSkeletonDoesNotModel() {
        ReferenceGrid r = ReferenceGrid.build();
        assertThatThrownBy(() -> r.grid.applyFault(r.hv.get(0), FaultType.SINGLE_LINE_GROUND, 0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> r.grid.addBranch(new Branch.Tie(r.hv.get(0), r.hv.get(0), 0.1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> r.grid.addLoad(LoadModel.constantPower(1, 0, LoadKind.HEATER), new BusId(99)))
                .isInstanceOf(IllegalArgumentException.class);
        PowerGrid fresh = new PowerGrid(GridConfig.defaults());
        assertThatThrownBy(() -> fresh.stepFast(DT, GridInputs.NONE, new GridOutputs()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void identicalRunsAreBitwiseEqual() {
        assertThat(trace()).containsExactly(trace());
    }

    private static double[] trace() {
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        double[] t = new double[1000];
        for (int k = 0; k < t.length; k++) {
            if (k == 100) r.grid.setLoadConnected(r.loads.get(7), false);
            if (k == 400) r.grid.setBreaker(r.ringLines.get(2), false);
            r.grid.stepFast(DT, GridInputs.NONE, out);
            t[k] = r.grid.island(0).frequencyHz() + r.grid.busAngleRad(r.mv.get(11)) + out.lossW();
        }
        return t;
    }
}
