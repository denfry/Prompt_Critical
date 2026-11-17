package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.metallurgy.ArcFurnace;
import org.junit.jupiter.api.Test;

/** Дуговая печь как нагрузка: мощность меняется каждый тик без пересборки, напряжение у соседей качается (фликер). */
class ArcFurnaceLoadTest {
    private static final double DT = 0.05;

    @Test
    void furnacePowerFollowsTheArcAndFlickersTheBus() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId src = g.addBus(VoltageClass.MV_35, "src"), bus = g.addBus(VoltageClass.MV_6_3, "eaf");
        g.addMachine(GridSynchronizationTest.infiniteSource(35e3), src);
        g.addBranch(new Branch.Transformer(src, bus, 6.3e6, 0.075, 46e3, 8e3, 0.008, TapChanger.NONE, null));
        LoadId eaf = g.addLoad(LoadModel.typical(LoadKind.ARC_FURNACE, 0), bus);
        g.initializePowerFlow();
        GridOutputs out = new GridOutputs();
        g.stepFast(DT, GridInputs.NONE, out);
        double vIdle = g.busVoltagePu(bus);

        ArcFurnace f = new ArcFurnace();
        f.charge(6000);
        double vMin = 9, vMax = 0, p = 0;
        for (long t = 0; t < 400; t++) {
            f.step(DT, g.busVoltagePu(bus), t, true);
            g.setLoadPower(eaf, f.powerW(), f.reactiveVar());
            g.stepFast(DT, GridInputs.NONE, out);
            vMin = Math.min(vMin, g.busVoltagePu(bus));
            vMax = Math.max(vMax, g.busVoltagePu(bus));
            p = g.loadPowerW(eaf);
        }
        assertThat(p).isCloseTo(f.powerW(), within(0.02 * f.powerW()));
        assertThat(vIdle - vMax).isGreaterThan(0.02); // 3 МВт через 6,3 МВ·А с u_к 7,5 %: шина проседает
        assertThat(vMax - vMin).isGreaterThan(0.01); // и качается вместе с дугой
    }

    @Test
    void onlyConstantPowerLoadsMayChangeOnTheFly() {
        PowerGrid g = new PowerGrid(GridConfig.defaults());
        BusId b = g.addBus(VoltageClass.MV_6_3, "b");
        LoadId heater = g.addLoad(LoadModel.typical(LoadKind.HEATER, 1e5), b);
        assertThatThrownBy(() -> g.setLoadPower(heater, 2e5, 0)).isInstanceOf(IllegalStateException.class);
    }
}
