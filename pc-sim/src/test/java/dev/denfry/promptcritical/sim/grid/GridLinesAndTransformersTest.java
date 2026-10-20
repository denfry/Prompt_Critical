package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Провода и ВЛ (07 §7.4.1–7.4.2), тепло трансформатора (§7.4.4), молния и заземление (§7.15.1). */
class GridLinesAndTransformersTest {
    static final double DT = 0.05;

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    /** 35 кВ: шины бесконечной мощности → ВЛ АС-120 длиной {@code km} → нагрузка P, cos φ 0,9; V в конце = 1. */
    static final class Line35 {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId src, end;
        final BranchId line;
        final GridOutputs out = new GridOutputs();

        Line35(double pW, double km, double vSource) {
            src = g.addBus(VoltageClass.MV_35, "src");
            end = g.addBus(VoltageClass.MV_35, "end");
            SynchronousMachine s = GridSynchronizationTest.infiniteSource(35e3);
            s.setVoltageReference(vSource);
            g.addMachine(s, src);
            line = g.addBranch(Branch.Line.overhead(src, end, ConductorSpec.ac120(), 0.43, km * 1e3,
                    VoltageClass.MV_35, g.config().baseVa()));
            g.addLoad(LoadModel.constantPower(pW, pW * Math.tan(Math.acos(0.9)), LoadKind.PROCESS), end);
            g.initializePowerFlow();
            g.stepFast(DT, GridInputs.NONE, out);
        }

        /** Напряжение источника, при котором в конце линии ровно номинал. */
        static Line35 atNominalEnd(double pW, double km) {
            double vs = 1;
            for (int it = 0; it < 4; it++) vs += 1 - new Line35(pW, km, vs).g.busVoltagePu(new BusId(1));
            return new Line35(pW, km, vs);
        }
    }

    @Test
    @Tag("T7.4")
    void lineLossesAt35kV() {
        assertThat(ConductorSpec.ac120().lossW(367, 2000)).isCloseTo(201e3, within(0.02 * 201e3));
        Line35 l = Line35.atNominalEnd(20e6, 2);
        assertThat(l.g.busVoltagePu(l.end)).isCloseTo(1, within(1e-3));
        assertThat(rel(l.g.branchCurrentA(l.line), 367)).isLessThan(0.02);
        assertThat(rel(l.out.lossW(), 201e3)).isLessThan(0.02);
    }

    @Test
    @Tag("T7.29")
    void conductorSagGrowsWithTemperature() {
        assertThat(ConductorSpec.ac70().sagM(64, 1.5, 20, 70)).isCloseTo(1.93, within(0.02 * 1.93));
        assertThat(ConductorSpec.ac70().sagM(64, 1.5, 20, 20)).isEqualTo(1.5);
        assertThat(ConductorSpec.m70().sagM(64, 1.5, 20, 70)).isLessThan(ConductorSpec.ac70().sagM(64, 1.5, 20, 70));
    }

    @Test
    void conductorHeatsTowardItsRatingTemperatureWithMinutesTimeConstant() {
        ConductorSpec c = ConductorSpec.ac120();
        double t = 20;
        for (int k = 0; k < 360; k++) t = c.stepTemperatureC(t, c.ampacityA(), 20, 15, 0, 10);
        assertThat(t).as("длительно допустимый ток — около 70 °C").isBetween(60.0, 80.0);
        double t5 = c.stepTemperatureC(20, c.ampacityA(), 20, 15, 0, 300);
        assertThat((t5 - 20) / (t - 20)).as("τ 5–15 мин").isBetween(0.28, 0.7);
    }

    @Test
    void overloadedLineAnnealsAndBreaksIntoAGroundFault() {
        Line35 l = new Line35(60e6, 2, 1.05); // ≈ 1100 А на АС-120 (390 А)
        List<Integer> broken = new ArrayList<>();
        GridEventSink sink = new GridEventSink() {
            @Override
            public void conductorBroken(int branch) {
                broken.add(branch);
            }
        };
        double hours = 0;
        while (broken.isEmpty() && hours < 5) {
            l.g.stepFast(DT, GridInputs.NONE, l.out);
            l.g.stepSlow(60);
            l.g.drainEvents(sink);
            hours += 60 / 3600.0;
        }
        assertThat(broken).containsExactly(l.line.index());
        assertThat(l.g.conductorDamage(l.line)).isEqualTo(1);
        assertThat(l.g.branchTemperatureC(l.line)).isGreaterThan(90);
        l.g.stepFast(DT, GridInputs.NONE, l.out);
        assertThat(l.g.groundFaultPresent(l.src)).as("35 кВ — изолированная нейтраль").isTrue();
        l.g.repairConductor(l.line);
        assertThat(l.g.conductorDamage(l.line)).isZero();
    }

    @Test
    @Tag("T7.5")
    void transformerHotSpotAndAging() {
        TransformerThermal th = TransformerThermal.onanPower();
        assertThat(th.steadyTopOilRiseK(1.3)).isCloseTo(75.4, within(0.1));
        assertThat(th.steadyHotSpotRiseK(1.3)).isCloseTo(39.6, within(0.1));
        double hs = th.steadyHotSpotC(1.3, 20);
        assertThat(hs).isCloseTo(135, within(2.0));
        assertThat(rel(TransformerThermal.agingRate(hs), 72)).isLessThan(0.05);

        // в сети: трансформатор 10 МВ·А 35/10,5 с нагрузкой 13 МВт; 30 ч SLOW-шагами по 60 с
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId hv = g.addBus(VoltageClass.MV_35, "hv"), lv = g.addBus(VoltageClass.MV_10_5, "lv");
        g.addMachine(GridSynchronizationTest.infiniteSource(35e3), hv);
        BranchId tr = g.addBranch(new Branch.Transformer(hv, lv, 10e6, 0.075, 46e3, 8e3, 0.008, TapChanger.NONE, th));
        g.addLoad(LoadModel.constantPower(13e6, 0, LoadKind.PROCESS), lv);
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        g.stepFast(DT, GridInputs.NONE, out);
        double k = g.branchCurrentA(tr) * Math.sqrt(3) * g.busVoltageV(hv) / 10e6; // K = S/S_ном
        for (int s = 0; s < 30 * 60; s++) g.stepSlow(60);
        assertThat(g.branchTemperatureC(tr)).isCloseTo(th.steadyHotSpotC(k, 20), within(0.5));
        double life = g.transformerLossOfLifeH(tr);
        assertThat(life).isGreaterThan(30 * 0.5 * TransformerThermal.agingRate(th.steadyHotSpotC(k, 20)));

        g.setBranchAmbient(tr, 40); // пустыня: та же нагрузка — горячее
        for (int s = 0; s < 30 * 60; s++) g.stepSlow(60);
        assertThat(g.branchTemperatureC(tr)).isCloseTo(th.steadyHotSpotC(k, 40), within(0.5));
    }

    @Test
    @Tag("T7.17")
    void backFlashoverDependsOnTowerGrounding() {
        double u50 = Lightning.insulatorCfoKv(7);
        assertThat(u50).isCloseTo(600, within(3.0));
        assertThat(Lightning.backFlashoverProbability(600, 10)).isCloseTo(0.152, within(0.01 * 0.152));
        assertThat(Lightning.backFlashoverProbability(600, 50)).isCloseTo(0.92, within(0.01 * 0.92));
        assertThat(Lightning.probabilityCurrentAbove(Lightning.currentKa(0.3))).isCloseTo(0.3, within(1e-12));
    }

    @Test
    @Tag("T7.18")
    void dwightGroundRod() {
        assertThat(Lightning.rodResistanceOhm(100, 3, 0.016)).isCloseTo(33.5, within(0.01 * 33.5));
    }

    @Test
    void lightningStrokeRateOfALine() {
        double w = Lightning.collectionWidthM(5, 25);
        assertThat(w).isCloseTo(198, within(2.0));
        assertThat(Lightning.strokeRatePerS(1e-3, w, 1000) * 600).isCloseTo(0.12, within(0.005));
        assertThat(Lightning.directStrokeKv(31)).isCloseTo(6200, within(1e-9));
        assertThat(Lightning.transformerBreakdownProbability(600, 480)).isCloseTo(0.45, within(1e-9));
        assertThat(Lightning.arresterLimitKv(VoltageClass.HV_110)).isLessThan(VoltageClass.HV_110.bilKv);
    }

    @Test
    void voltageClassLadder() {
        assertThat(VoltageClass.nearest(380)).isEqualTo(VoltageClass.LV_0_4);
        assertThat(VoltageClass.nearest(10e3)).isEqualTo(VoltageClass.MV_10_5);
        assertThat(VoltageClass.MV_10_5.isolatedNeutral()).isTrue();
        assertThat(VoltageClass.HV_110.isolatedNeutral()).isFalse();
        assertThat(VoltageClass.agingRate(1.2, 12)).isCloseTo(Math.pow(1.2, 12), within(1e-9));
        assertThat(VoltageClass.agingRate(0.9, 12)).isEqualTo(1);
    }
}
