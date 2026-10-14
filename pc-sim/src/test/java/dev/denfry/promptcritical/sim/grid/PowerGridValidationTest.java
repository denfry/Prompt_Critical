package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Тест-кейсы 07 §7.24 для каркаса сети: частота, статизм, устойчивость, баланс. */
class PowerGridValidationTest {
    static final double DT = 0.05;

    private static double rel(double a, double e) {
        return Math.abs(a - e) / Math.abs(e);
    }

    /** Остров 200 МВ·А: две машины 100 МВ·А, H = 4 с, нагрузка 150 МВт и FE-импорт 20 МВт без инерции. */
    static final class Island200 {
        final PowerGrid g;
        final LoadId feImport;
        final GridOutputs out = new GridOutputs();

        Island200(GridConfig cfg) {
            g = new PowerGrid(cfg);
            BusId g1 = g.addBus(VoltageClass.MV_10_5, "g1"), g2 = g.addBus(VoltageClass.MV_10_5, "g2");
            BusId load = g.addBus(VoltageClass.HV_110, "load");
            g.addBranch(new Branch.Line(g1, load, 0, 0.1, 0, 1000, null, 1));
            g.addBranch(new Branch.Line(g2, load, 0, 0.1, 0, 1000, null, 1));
            for (BusId b : new BusId[] {g1, g2}) {
                SynchronousMachine m = new SynchronousMachine(MachineParams.turbogenerator(100e6, 10.5e3),
                        ExciterSpec.rotating(), PrimeMover.Governed.steamTurbine(100e6)); // статизм на S_ном (§7.2.3)
                m.setLoadTarget(65e6);
                g.addMachine(m, b);
            }
            g.addLoad(LoadModel.constantPower(150e6, 30e6, LoadKind.PROCESS).withDamping(1.5), load);
            feImport = g.addLoad(new LoadModel(-20e6, 0, 0, 0, 1, 0, UflsStage.NONE, LoadKind.FE_IMPORT), load);
            g.initializePowerFlow();
        }

        double step() {
            g.stepFast(DT, GridInputs.NONE, out);
            return g.island(0).frequencyHz();
        }
    }

    @Test
    @Tag("T7.1")
    void rateOfChangeOfFrequencyAfterLossOfGeneration() {
        Island200 s = new Island200(GridConfig.defaults());
        for (int k = 0; k < 20; k++) assertThat(s.step()).isCloseTo(50, within(1e-6));
        s.g.setLoadConnected(s.feImport, false);
        double f0 = s.g.island(0).frequencyHz();
        s.step();
        double f100ms = s.step();
        double expected = -20 * 50 / (2 * 4 * 200.0); // −0,625 Гц/с
        assertThat(rel((f100ms - f0) / 0.1, expected)).isLessThan(0.02);
    }

    @Test
    @Tag("T7.2")
    void droopSteadyStateDeviation() {
        // формула §7.2.3 не содержит мёртвой зоны регулятора — она выключена
        Island200 s = new Island200(GridConfig.defaults().withGovernorDeadbandHz(0));
        s.g.setLoadConnected(s.feImport, false);
        double f = 0;
        for (int k = 0; k < 1200; k++) f = s.step();
        double expected = -0.1 / (25 + 1.125) * 50; // −0,191 Гц
        assertThat(rel(f - 50, expected)).isLessThan(0.03);
        assertThat(Math.abs(s.g.island(0).rocofHzPerS())).isLessThan(1e-4);
    }

    /** Одномашинная схема: генератор H = 4 за x'_d + линия на шины бесконечной мощности, X_Σ = 0,5. */
    static final class Smib {
        final PowerGrid g = new PowerGrid(GridConfig.defaults());
        final BusId terminal;
        final SynchronousMachine gen;

        Smib() {
            terminal = g.addBus(VoltageClass.MV_10_5, "gen");
            BusId inf = g.addBus(VoltageClass.HV_110, "infinite");
            g.addBranch(new Branch.Line(terminal, inf, 0, 0.25 - 1e-5, 0, 1000, null, 1));
            // классическая модель: E' постоянна (T'_d0 → ∞, ручное возбуждение), без демпфирования
            gen = new SynchronousMachine(new MachineParams(100e6, 10.5e3, 1, 1.8, 0.25, 0.18, 1e9, 4, 0, 0, 0.98),
                    ExciterSpec.manual(), PrimeMover.Governed.constantPower(100e6));
            gen.setLoadTarget(80e6);
            gen.setVoltageReference(0.97890); // даёт E' = 1 при P = 0,8 и X_Σ = 0,5
            g.addMachine(gen, terminal);
            SynchronousMachine source = new SynchronousMachine(
                    new MachineParams(1e9, 110e3, 1, 1e-4, 1e-4, 1e-4, 1e9, 1e7, 0, 0, 1), ExciterSpec.manual(),
                    new PrimeMover.CreateShaft(1, 1e9));
            g.addMachine(source, inf); // самая мощная машина — балансирующая, V = 1∠0
            g.initializePowerFlow();
        }

        /** КЗ на выводах длительностью {@code faultTicks}, затем 5 с; true — синхронизм сохранён. */
        boolean survives(int faultTicks) {
            GridOutputs out = new GridOutputs();
            g.applyFault(terminal, FaultType.THREE_PHASE, 0);
            for (int k = 0; k < faultTicks; k++) g.stepFast(DT, GridInputs.NONE, out);
            g.clearFault(terminal);
            for (int k = 0; k < 100; k++) g.stepFast(DT, GridInputs.NONE, out);
            return gen.poleSlips() == 0;
        }
    }

    @Test
    @Tag("T7.3")
    void criticalClearingTime() {
        Smib s = new Smib();
        double pMax = s.gen.ePrimeQ() * 1.0 / 0.5;
        assertThat(pMax).isCloseTo(2.0, within(0.01));
        double pm = 0.8, d0 = Math.asin(pm / pMax), dMax = Math.PI - d0;
        double dCr = Math.acos(pm / pMax * (dMax - d0) + Math.cos(dMax)); // равенство площадей, P_e = 0 при КЗ
        double tCr = Math.sqrt(4 * 4 * (dCr - d0) / (2 * Math.PI * 50 * pm));
        assertThat(rel(tCr, 0.270)).isLessThan(0.03);
        assertThat(new Smib().survives(5)).as("КЗ 0,25 с").isTrue();
        assertThat(new Smib().survives(6)).as("КЗ 0,30 с").isFalse();
    }

    @Test
    @Tag("T7.20")
    void islandPowerBalanceHoldsEveryTick() {
        ReferenceGrid r = ReferenceGrid.build();
        GridOutputs out = new GridOutputs();
        double worst = 0;
        for (int tick = 0; tick < 10_000; tick++) {
            if (tick % 500 == 250) r.grid.setLoadConnected(r.loads.get(tick / 500 % 40), tick / 500 % 2 == 0);
            if (tick == 3000) r.grid.setBreaker(r.ringLines.get(3), false);
            if (tick == 6000) r.grid.setBreaker(r.ringLines.get(3), true);
            if (tick == 7000) r.grid.setBreaker(r.feeders.get(5), false); // подстанция теряет питание
            r.grid.stepFast(DT, GridInputs.NONE, out);
            worst = Math.max(worst, Math.abs(out.imbalanceW()));
        }
        assertThat(worst / r.grid.config().baseVa()).isLessThan(1e-6);
        assertThat(out.lossW()).isPositive();
        assertThat(r.grid.island(0).frequencyHz()).isBetween(49.5, 50.5);
    }
}
