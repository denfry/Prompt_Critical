package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Инвертор в собранной сети (15 §15.9): подключается через 5 с в окне и не теряет выдержку от пересборок сети —
 * в мире любая постройка в измерении пересобирает сеть, и первый тик после неё шины ещё не посчитаны.
 */
class InverterUnitTest {
    private static final long BUS = 31;
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;
    private int x;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    @Test
    void connectsAndExportsAlthoughTheGridIsRebuiltEveryTwoSeconds() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        GridSubsystem grid = w.subsystem(GridSubsystem.class);
        GeneratorUnit g = grid.generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(BUS);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        ConsumerUnit c = grid.consumer(w.topology().addDevice("promptcritical:grid_consumer", new GridPos(x++, 64, 0)));
        c.setNodes(BUS);
        c.setDemandKw(200);
        InverterUnit inv = grid.inverter(w.topology().addDevice("promptcritical:battery_inverter", new GridPos(x++, 64, 0)));
        inv.configure(500e3, 0.97);
        inv.setNodes(BUS);
        inv.setDispatch(100e3, 125e3, 0);
        boolean connected = false;
        for (int i = 0; i < 40 * 20; i++) {
            if (i % 40 == 39) grid.markDirty();
            g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            connected |= inv.source().connected();
        }
        assertThat(connected).as("inverter connects within 40 s").isTrue();
        assertThat(inv.source().connected()).isTrue();
        assertThat(inv.powerW()).isGreaterThan(50e3);
    }

    /**
     * Остров генератора на обгонной муфте с нагрузкой 150 кВт (шкаф FE на импорте) и батарейным инвертором с заданием
     * «номинал» при батарее на 125 кВт, как в InverterGameTests: оба включаются через 5 с в окне, инвертор набирает
     * мощность не быстрее источника за ним и не обгоняет нагрузку, генератор не уходит в разнос выше 51 Гц; затем
     * заряд при нагрузке 50 кВт берёт из сети, не выводя частоту ниже окна: при статизме привода 4 % остров от
     * холостых 49,97 Гц до края окна 49 Гц несёт лишь ≈ 250 кВт, и 150 кВт нагрузки с зарядом 129 кВт в него не входят.
     */
    @Test
    void batteryJoinsAnIslandOnAnOverrunningClutchWithoutRunningItAway() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        GridSubsystem grid = w.subsystem(GridSubsystem.class);
        GeneratorUnit g = grid.generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(BUS);
        g.setExciterPresent(true);
        while (g.gearRpm() != 64) g.cycleGear();
        g.setFieldOn(true);
        FeInterfaceUnit load = grid.feInterface(w.topology().addDevice("promptcritical:grid_fe_interface_large", new GridPos(x++, 64, 0)));
        load.configure(FeInterface.Spec.standard(1e7));
        load.setNodes(BUS);
        load.iface().setMode(FeInterface.Mode.IMPORT);
        load.iface().setSetpointW(150e3);
        InverterUnit inv = grid.inverter(w.topology().addDevice("promptcritical:battery_inverter", new GridPos(x++, 64, 0)));
        inv.configure(500e3, 0.97);
        inv.setNodes(BUS);
        inv.setDispatch(500e3, 125e3 * 0.97, 125e3 / 0.97);
        double maxHz = 0;
        boolean dropped = false, was = false;
        for (int i = 0; i < 60 * 20; i++) {
            g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            load.iface().extract(Double.MAX_VALUE, false);
            boolean on = inv.source().connected();
            dropped |= was && !on;
            was = on;
            if (on) maxHz = Math.max(maxHz, g.frequencyHz());
        }
        assertThat(dropped).as("the inverter never trips once on").isFalse();
        assertThat(maxHz).as("the island stays in the window").isLessThan(InverterSource.F_MAX_HZ);
        assertThat(load.iface().connected()).isTrue();
        assertThat(inv.powerW()).as("discharge at the battery limit").isGreaterThan(115e3).isLessThan(125e3 * 0.97 + 1);
        load.iface().setSetpointW(50e3);
        inv.setDispatch(-500e3, 125e3 * 0.97, 125e3 / 0.97);
        double minHz = 99;
        for (int i = 0; i < 30 * 20; i++) {
            g.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            load.iface().extract(Double.MAX_VALUE, false);
            dropped |= !inv.source().connected();
            minHz = Math.min(minHz, g.frequencyHz());
        }
        assertThat(dropped).as("charging does not trip the inverter").isFalse();
        assertThat(minHz).as("charging keeps the island in the window").isGreaterThan(InverterSource.F_MIN_HZ);
        assertThat(inv.powerW()).as("charging draws from the grid").isLessThan(-40e3);
    }

    /** LFSM-O режет мощность, которую источник может дать, а не задание сверх неё (P_ref EN 50549-1). */
    @Test
    void overFrequencyCutsTheAvailablePowerNotTheDemand() {
        InverterSource s = new InverterSource(500e3, 0.97);
        double p = 0;
        for (int i = 0; i < 20 * 40; i++) p = s.step(0.05, 1.0, 50, 500e3, 100e3, 0);
        assertThat(p).isCloseTo(100e3, org.assertj.core.data.Offset.offset(1e3));
        for (int i = 0; i < 20 * 5; i++) p = s.step(0.05, 1.0, 50.7, 500e3, 100e3, 0);
        assertThat(p).as("0.5 Hz over 50.2 Hz: 20 percent off the 100 kW available").isCloseTo(80e3, org.assertj.core.data.Offset.offset(1e3));
    }
}
