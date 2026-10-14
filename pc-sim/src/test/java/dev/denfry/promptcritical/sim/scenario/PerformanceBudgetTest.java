package dev.denfry.promptcritical.sim.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.grid.GridInputs;
import dev.denfry.promptcritical.sim.grid.GridOutputs;
import dev.denfry.promptcritical.sim.grid.ReferenceGrid;
import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.hydraulics.ValveModel;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.FluidPackage;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Тест-кейсы производительности 01 §1.16 (T-1.12): аллокации, сон, детерминизм. Время — JMH (pc-sim/src/jmh). */
class PerformanceBudgetTest {
    static final double DT = Pr300Plant.DT;

    @Test
    @Tag("T-1.12-02")
    void hotLoopDoesNotAllocateAfterWarmUp() {
        Pr300Plant plant = new Pr300Plant();
        ReferenceGrid grid = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int k = 0; k < 5_000; k++) {
            plant.tick();
            grid.grid.stepFast(DT, GridInputs.NONE, out);
        }
        long before = mx.getThreadAllocatedBytes(thread);
        for (int k = 0; k < 100_000; k++) {
            plant.tick();
            grid.grid.stepFast(DT, GridInputs.NONE, out);
        }
        long allocated = mx.getThreadAllocatedBytes(thread) - before;
        // «0 байт на тик»: любая аллокация на тике дала бы ≥ 16 Б × 10⁵; разовые десятки байт — это
        // рематериализация скалярно заменённых объектов при деоптимизации JIT, а не код модели
        assertThat(allocated / 100_000).as("bytes per tick (total %d B)", allocated).isZero();
        assertThat(plant.power()).isBetween(0.95, 1.05);
    }

    /** Пакет воды, считающий вызовы EOS. */
    static final class CountingFluid implements FluidPackage {
        final FluidPackage d = WaterPackage.of(CriticalFlowTable.Model.HEM);
        long eos;

        @Override public String id() { return d.id(); }
        @Override public int componentCount() { return d.componentCount(); }

        @Override
        public void stateFromMassEnergy(double density, double specificEnergy, double[] f, int offset, double pGuess,
                ThermoState out) {
            eos++;
            d.stateFromMassEnergy(density, specificEnergy, f, offset, pGuess, out);
        }

        @Override
        public void stateFromPressureEnthalpy(double p, double h, double[] f, int offset, ThermoState out) {
            eos++;
            d.stateFromPressureEnthalpy(p, h, f, offset, out);
        }

        @Override public double criticalMassFlux(double p0, double h0, double[] f, int o) { return d.criticalMassFlux(p0, h0, f, o); }
        @Override public double soundSpeed(double p, double h, double[] f, int o) { return d.soundSpeed(p, h, f, o); }
        @Override public long familyKey(double[] f, int o) { return d.familyKey(f, o); }
    }

    @Test
    @Tag("T-1.12-03")
    void sleepingComponentOfThousandPipesCostsNoEosAndWakesOnValveCommand() {
        CountingFluid fluid = new CountingFluid();
        FluidNetwork n = FluidNetwork.create(fluid, HydraulicsConfig.defaults());
        List<FluidNodeSpec> nodes = new ArrayList<>();
        List<FlowLinkSpec> links = new ArrayList<>();
        for (int i = 0; i <= 1000; i++) nodes.add(FluidNodeSpec.hem(i + 1, 0.05, 0, 0.2));
        for (int j = 0; j < 1000; j++) {
            boolean valve = j == 500;
            links.add(new FlowLinkSpec(10_000 + j, valve ? LinkKind.VALVE_GATE : LinkKind.PIPE, j + 1, j + 2, 200, 0,
                    0.05, 0.002, 1, 0, 0.1, 0.1, null, valve ? ValveModel.Spec.gate(0.2, 10) : null));
        }
        double p = 1e6;
        n.rebuild(nodes, links, StateCarryOver.uniform(p, WaterProperties.tables().enthalpyPT(p, 320)));
        assertThat(n.componentCount()).isEqualTo(1);
        for (int k = 0; k < 41; k++) n.stepFast(DT);
        assertThat(n.isAsleep(0)).isTrue();
        long eos0 = fluid.eos;
        for (int k = 0; k < 100; k++) n.stepFast(DT);
        assertThat(fluid.eos - eos0).isZero();

        n.link(10_500).setValvePosition(0.5);
        assertThat(n.isAsleep(0)).isFalse(); // пробуждение в том же тике, до шага
        long eos1 = fluid.eos;
        n.stepFast(DT);
        assertThat(fluid.eos - eos1).isGreaterThanOrEqualTo(1001);
    }

    @Test
    @Tag("T-1.12-04")
    void identicalInputsGiveBitwiseIdenticalSnapshots() {
        assertThat(run()).containsExactly(run());
    }

    /** 10⁴ тиков ПР-300 и эталонной сети с одинаковыми событиями; хэш снимка на каждый тик. */
    private static long[] run() {
        Pr300Plant plant = new Pr300Plant();
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        double[] snap = new double[Pr300Plant.SNAPSHOT_SIZE + 4];
        long[] hashes = new long[10_000];
        for (int k = 0; k < hashes.length; k++) {
            if (k == 3000) plant.primary.link(Pr300Plant.PUMP_B).pump().setDrivingTorque(0); // выбег ГЦН
            if (k % 700 == 0) r.grid.setLoadConnected(r.loads.get(k / 700), false);
            plant.tick();
            r.grid.stepFast(DT, GridInputs.NONE, out);
            int at = plant.snapshot(snap, 0);
            snap[at++] = r.grid.island(0).frequencyHz();
            snap[at++] = out.generationW();
            snap[at++] = out.lossW();
            snap[at] = r.grid.busAngleRad(r.mv.get(17));
            long h = 1125899906842597L;
            for (double v : snap) h = 31 * h + Double.doubleToRawLongBits(v);
            hashes[k] = h;
        }
        assertThat(plant.protection.isTripped()).as("coastdown trips on low flow").isTrue();
        return hashes;
    }
}
