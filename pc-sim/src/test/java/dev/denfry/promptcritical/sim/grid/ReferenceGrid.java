package dev.denfry.promptcritical.sim.grid;

import java.util.ArrayList;
import java.util.List;

/**
 * Эталонный комплекс бюджета 07 §7.20: 60 шин, 80 ветвей, 12 синхронных машин, 40 нагрузок.
 * Кольцо 110 кВ из 12 узлов с 6 хордами, 12 блоков «генератор — трансформатор», 36 подстанций
 * 10 кВ с 14 кабельными перемычками. Общий для тестов и JMH.
 */
public final class ReferenceGrid {
    public final PowerGrid grid;
    public final List<BusId> hv = new ArrayList<>(), gen = new ArrayList<>(), mv = new ArrayList<>();
    public final List<BranchId> ringLines = new ArrayList<>(), feeders = new ArrayList<>();
    public final List<LoadId> loads = new ArrayList<>();
    public final List<MachineId> machines = new ArrayList<>();

    public ReferenceGrid(GridConfig cfg) {
        grid = new PowerGrid(cfg);
        for (int i = 0; i < 12; i++) hv.add(grid.addBus(VoltageClass.HV_110, "hv" + i));
        for (int i = 0; i < 12; i++) gen.add(grid.addBus(VoltageClass.MV_10_5, "gen" + i));
        for (int i = 0; i < 36; i++) mv.add(grid.addBus(VoltageClass.MV_10_5, "mv" + i));
        for (int i = 0; i < 12; i++) ringLines.add(grid.addBranch(line(hv.get(i), hv.get((i + 1) % 12))));
        for (int i = 0; i < 6; i++) ringLines.add(grid.addBranch(line(hv.get(i), hv.get(i + 6))));

        double[] sMva = {100, 100, 50, 50, 50, 50, 30, 30, 6, 6, 2, 2};
        double[] pMw = {80, 80, 30, 30, 30, 30, 15, 15, 3, 3, 1, 1};
        for (int i = 0; i < 12; i++) {
            double s = sMva[i] * 1e6;
            grid.addBranch(transformer(hv.get(i), gen.get(i), s));
            SynchronousMachine m;
            if (i < 8) {
                m = new SynchronousMachine(MachineParams.turbogenerator(s, 10.5e3),
                        i < 2 ? ExciterSpec.thyristor() : ExciterSpec.rotating(), PrimeMover.Governed.steamTurbine(0.85 * s));
            } else if (i < 10) {
                m = new SynchronousMachine(MachineParams.dieselGenerator(s, 10.5e3), ExciterSpec.rotating(),
                        PrimeMover.Governed.steamTurbine(0.8 * s));
            } else {
                m = new SynchronousMachine(MachineParams.shaftGenerator(s, 10.5e3), ExciterSpec.rotating(),
                        new PrimeMover.CreateShaft(20, 0.8 * s));
            }
            m.setLoadTarget(pMw[i] * 1e6);
            m.setVoltageReference(1.02);
            machines.add(grid.addMachine(m, gen.get(i)));
        }
        for (int i = 0; i < 36; i++) feeders.add(grid.addBranch(transformer(hv.get(i % 12), mv.get(i), 40e6)));
        for (int i = 0; i < 14; i++) {
            grid.addBranch(new Branch.Cable(mv.get(2 * i), mv.get(2 * i + 1), 0.05, 0.04, 0.002, 600, 3000));
        }
        for (int i = 0; i < 40; i++) {
            double p = i < 36 ? 7.5e6 : 5e6;
            LoadModel l = switch (i % 3) {
                case 0 -> LoadModel.constantPower(p, 0.3 * p, LoadKind.PROCESS);
                case 1 -> LoadModel.constantImpedance(p, 0.1 * p, LoadKind.HEATER);
                default -> new LoadModel(p, 0.4 * p, 0.2, 0.3, 0.5, Double.NaN, UflsStage.ACHR1_1, LoadKind.NPC_CONSUMER);
            };
            loads.add(grid.addLoad(l, mv.get(i % 36)));
        }
        grid.initializePowerFlow();
    }

    public static ReferenceGrid build() {
        return new ReferenceGrid(GridConfig.defaults());
    }

    private static Branch.Line line(BusId a, BusId b) {
        return new Branch.Line(a, b, 0.01, 0.06, 0.02, 600, null, 30_000);
    }

    private static Branch.Transformer transformer(BusId hv, BusId lv, double s) {
        return new Branch.Transformer(hv, lv, s, 0.105, 0.004 * s, 0.001 * s, 0.005, TapChanger.NONE, null);
    }
}
