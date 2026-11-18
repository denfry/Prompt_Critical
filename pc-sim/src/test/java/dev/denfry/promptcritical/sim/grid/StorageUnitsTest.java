package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.hydraulics.PumpTurbine;
import dev.denfry.promptcritical.sim.hydraulics.PumpedStorage;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Накопители и управляемые нагрузки в сети: генератор на валу 6,3 кВ с потребителем 200 кВт; агрегат ГАЭС в турбинном
 * режиме отдаёт мощность и разгружает генератор, в насосном — берёт; электродный котёл берёт мощность по уровню на
 * электродах и пропорционально квадрату напряжения.
 */
class StorageUnitsTest {
    private static final long BUS = 41;
    private final WorkerPool pool = WorkerPool.direct();
    private SimulationWorld w;
    private GeneratorUnit gen;
    private ConsumerUnit load;
    private int x;

    @AfterEach
    void close() {
        if (w != null) w.close();
        pool.close();
    }

    private GridSubsystem grid() {
        return w.subsystem(GridSubsystem.class);
    }

    private void plant() {
        w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        gen = grid().generator(w.topology().addDevice("promptcritical:shaft_generator", new GridPos(x++, 64, 0)));
        gen.setVoltageClass(VoltageClass.MV_6_3);
        gen.setNodes(BUS);
        gen.setExciterPresent(true);
        gen.setFieldOn(true);
        load = grid().consumer(w.topology().addDevice("promptcritical:grid_consumer", new GridPos(x++, 64, 0)));
        load.setNodes(BUS);
        load.setDemandKw(200);
    }

    private void run(double seconds) {
        for (int i = 0; i < Math.round(seconds * 20); i++) {
            gen.shaft.sample(64, true, w.clock().tick());
            w.tick(ExternalInputs.NONE);
            w.drainEvents(e -> { });
        }
    }

    /** Малая ГАЭС: напор 60 м, турбина 150 кВт, бассейн 2000 м² со сработкой 5 м, водовод 120 м Ø0,6 м. */
    private static PumpedStorage.Spec small() {
        return new PumpedStorage.Spec(PumpTurbine.scaled(60, 150e3), 58, 5, 2000, 0, 120, 0.6);
    }

    @Test
    void pumpedStorageGeneratesIntoTheGridAndPumpsFromIt() {
        plant();
        PumpedStorageUnit s = grid().pumpedStorage(w.topology().addDevice("promptcritical:pumped_storage_unit", new GridPos(x++, 64, 0)));
        s.setNodes(BUS);
        s.configure(small());
        s.plant().setUpperLevelM(2.5);
        s.plant().setMode(PumpedStorage.Mode.IDLE);
        run(20);
        double alone = gen.electricalPowerW();
        s.plant().setMode(PumpedStorage.Mode.GENERATE);
        run(5);
        assertThat(s.powerW()).isCloseTo(-150e3, within(15e3));
        assertThat(gen.electricalPowerW()).isCloseTo(alone - 150e3, within(25e3));
        s.plant().setMode(PumpedStorage.Mode.PUMP);
        run(5);
        assertThat(s.powerW()).isPositive();
        assertThat(gen.electricalPowerW()).isGreaterThan(alone + 100e3);
    }

    @Test
    void electrodeBoilerDrawsByElectrodeLevel() {
        plant();
        ElectrodeBoilerUnit b = grid().electrodeBoiler(w.topology().addDevice("promptcritical:electrode_boiler", new GridPos(x++, 64, 0)));
        b.setNodes(BUS);
        b.setSteamSink(true);
        b.boiler().setMode(ElectrodeBoiler.Mode.MANUAL);
        b.boiler().setManualSetpoint(0.2);
        run(20);
        double v = grid().voltageV(BUS) / 6300;
        assertThat(b.boiler().level()).isCloseTo(0.2, within(1e-9));
        assertThat(b.powerW()).isCloseTo(0.2 * ElectrodeBoilerUnit.RATED_W * v * v, within(5e3));
        assertThat(b.heatW()).isCloseTo(b.powerW() * ElectrodeBoiler.EFFICIENCY, within(1e-6));
        b.setSteamSink(false); // котёл на коллекторе не принимает пар
        run(70);
        assertThat(b.powerW()).isCloseTo(0, within(1.0));
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        plant();
        PumpedStorageUnit s = grid().pumpedStorage(w.topology().addDevice("promptcritical:pumped_storage_unit", new GridPos(x++, 64, 0)));
        s.setNodes(BUS);
        s.configure(small());
        s.plant().setUpperLevelM(3.25);
        s.plant().setMode(PumpedStorage.Mode.IDLE);
        ElectrodeBoilerUnit b = grid().electrodeBoiler(w.topology().addDevice("promptcritical:electrode_boiler", new GridPos(x++, 64, 0)));
        b.setNodes(BUS);
        b.boiler().setMode(ElectrodeBoiler.Mode.MANUAL);
        run(1);
        MutableStateTree saved = w.saveTree();
        SimulationWorld c = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        c.register(new GridSubsystem(GridConfig.defaults()));
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        GridSubsystem g = c.subsystem(GridSubsystem.class);
        PumpedStorageUnit s2 = (PumpedStorageUnit) g.unit(s.id());
        s2.configure(small());
        assertThat(s2.plant().upperLevelM()).isCloseTo(3.25, within(0.01));
        assertThat(s2.plant().mode()).isEqualTo(PumpedStorage.Mode.IDLE);
        assertThat(((ElectrodeBoilerUnit) g.unit(b.id())).boiler().mode()).isEqualTo(ElectrodeBoiler.Mode.MANUAL);
        c.close();
    }
}
