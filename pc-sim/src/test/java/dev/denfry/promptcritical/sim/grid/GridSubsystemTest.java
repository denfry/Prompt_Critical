package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Сеть мира (этап 8 M3): генератор на валу Create, ячейки КРУ, потребитель, двигатель, пролёт ВЛ; синхронизация по
 * синхроноскопу и несинхронное включение (домен T7.25), АЧР при дефиците, пробой двигателя 0,4 кВ на шине 6 кВ
 * (домен T7.27), перенос состояния при пересборке и сохранение.
 */
class GridSubsystemTest {
    private static final long BUS_A = 11, BUS_B = 12, BUS_C = 13, BUS_D = 14;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<SimEvent> events = new ArrayList<>();
    private final List<GeneratorUnit> shafts = new ArrayList<>();
    private int x;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.register(new GridSubsystem(GridConfig.defaults()));
        worlds.add(w);
        return w;
    }

    private static GridSubsystem grid(SimulationWorld w) {
        return w.subsystem(GridSubsystem.class);
    }

    private SimId device(SimulationWorld w, String type) {
        return w.topology().addDevice("promptcritical:" + type, new GridPos(x++, 64, 0));
    }

    /** Генератор 6,3 кВ на валу 64 об/мин с возбудителем; поле включено. */
    private GeneratorUnit generator(SimulationWorld w, long node, int trimPermil) {
        GeneratorUnit g = grid(w).generator(device(w, "shaft_generator"));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(node);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        g.setTrimPermil(trimPermil);
        shafts.add(g);
        return g;
    }

    private SwitchUnit cell(SimulationWorld w, long bus, long feeder, boolean closed) {
        SwitchUnit s = grid(w).switchUnit(device(w, "switchgear_cell"), SwitchUnit.Kind.MV_BREAKER);
        s.setNodes(bus, feeder);
        s.operate(closed);
        return s;
    }

    private ConsumerUnit consumer(SimulationWorld w, long node, int kw) {
        ConsumerUnit c = grid(w).consumer(device(w, "grid_consumer"));
        c.setNodes(node);
        c.setDemandKw(kw);
        return c;
    }

    private void run(SimulationWorld w, double seconds) {
        int n = (int) Math.round(seconds * 20);
        for (int i = 0; i < n; i++) tick(w);
    }

    private void tick(SimulationWorld w) {
        for (GeneratorUnit g : shafts) if (g.owner == grid(w)) g.shaft.sample(64, true, w.clock().tick());
        w.tick(ExternalInputs.NONE);
        w.drainEvents(events::add);
    }

    @Test
    void shaftGeneratorRunsUpExcitesAndCarriesTheConsumer() {
        SimulationWorld w = world();
        GeneratorUnit g = generator(w, BUS_A, 0);
        SwitchUnit s = cell(w, BUS_A, BUS_B, false);
        ConsumerUnit c = consumer(w, BUS_B, 200);
        run(w, 0.1);
        assertThat(g.state()).isEqualTo(MachineState.ROLLING); // разгоняется, ещё не в сети
        run(w, 19.9);
        assertThat(g.speedPu()).isCloseTo(1.0, within(0.01));
        assertThat(g.terminalVoltageV()).isCloseTo(6300, within(6300 * 0.05));
        assertThat(c.energized()).isFalse();

        s.operate(true);
        run(w, 10);
        assertThat(c.energized()).isTrue();
        assertThat(c.powerW()).isCloseTo(200e3, within(30e3));
        assertThat(g.electricalPowerW()).isCloseTo(c.powerW(), within(5e3));
        assertThat(g.shaftPowerW()).isGreaterThan(g.electricalPowerW() * 0.95);
        // статизм привода 4 %: 0,4 о.е. нагрузки — частота ниже на 1,6 %
        assertThat(g.frequencyHz()).isCloseTo(50 * (1 - 0.4 / GeneratorUnit.DRIVE_STIFFNESS), within(0.15));
        assertThat(s.energyForwardKwh()).isGreaterThan(0.3);
    }

    @Test
    void createOverstressStopsTheDriveAndTheMachineLeavesTheGrid() {
        SimulationWorld w = world();
        GeneratorUnit g = generator(w, BUS_A, 0);
        run(w, 20);
        assertThat(g.state()).isIn(MachineState.EXCITED, MachineState.SYNCHRONIZED);
        shafts.clear(); // вал Create стоит: отсчёты устаревают
        run(w, 90);
        assertThat(g.speedPu()).isLessThan(GeneratorUnit.OFFLINE_SPEED);
        assertThat(g.state()).isEqualTo(MachineState.ROLLING);
        assertThat(g.terminalVoltageV()).isZero();
    }

    /** Два генератора: G1 держит шину B с нагрузкой, G2 включается ячейкой на шину B. */
    private record TwoMachines(SimulationWorld w, GeneratorUnit g1, GeneratorUnit g2, SwitchUnit tie, ConsumerUnit load) {
    }

    private TwoMachines twoMachines(int trim2, boolean syncCheck) {
        SimulationWorld w = world();
        GeneratorUnit g1 = generator(w, BUS_A, 0);
        cell(w, BUS_A, BUS_B, true);
        ConsumerUnit load = consumer(w, BUS_B, 200);
        GeneratorUnit g2 = generator(w, BUS_C, trim2);
        SwitchUnit tie = cell(w, BUS_B, BUS_C, false);
        if (syncCheck) tie.setRelays(List.of(RelaySetting.standard(RelaySetting.Kind.SYNC_CHECK)));
        run(w, 25);
        return new TwoMachines(w, g1, g2, tie, load);
    }

    private static GeneratorUnit incoming(TwoMachines m) {
        GeneratorUnit g = m.g1.lastSync() != null ? m.g1 : m.g2;
        assertThat(g.lastSync()).isNotNull();
        return g;
    }

    /** Подстроить вариатор G2, как игрок по синхроноскопу: стрелка медленно по часовой (0 < Δf ≤ 0,067 Гц). */
    private void trimForSync(TwoMachines m) {
        for (int i = 0; i < 100; i++) {
            SyncScope sc = m.tie.syncScope();
            double slip = sc.slipHz();
            if (slip > 0.005 && slip < 0.06) return;
            m.g2.setTrimPermil(m.g2.trimPermil() + (slip <= 0.005 ? 1 : -1));
            run(m.w, 2);
        }
        throw new AssertionError("slip never settled");
    }

    /** Ждать, пока угол в момент касания контактов войдёт в окно [lo, hi], и дать команду. */
    private BreakerResponse closeAtAngle(TwoMachines m, double loDeg, double hiDeg) {
        for (int i = 0; i < 20 * 60; i++) {
            SyncScope sc = m.tie.syncScope();
            if (sc != null) {
                double a = Math.abs(Math.toDegrees(sc.angleAtClosingRad()));
                if (a >= loDeg && a <= hiDeg) return m.tie.operate(true);
            }
            tick(m.w);
        }
        throw new AssertionError("angle window never reached");
    }

    @Test
    void closingAt120DegreesWithoutSyncCheckShocksTheShaft() {
        TwoMachines m = twoMachines(-30, false); // скольжение ≈ 0,7 Гц
        BreakerResponse r = closeAtAngle(m, 115, 125);
        assertThat(r).isEqualTo(BreakerResponse.ACCEPTED);
        run(m.w, 1);
        GeneratorUnit incoming = incoming(m); // толчок — входящей машине (меньшая Σ H·S; при равенстве — сторона шины)
        SyncClosing c = incoming.lastSync();
        assertThat(c.result()).isNotEqualTo(SyncResult.OK);
        assertThat(c.torquePu()).isGreaterThan(1.5);
        assertThat(incoming.takeShockW()).isGreaterThan(0);
        assertThat(incoming.takeShockW()).isZero(); // забирается один раз
        assertThat(events).anyMatch(e -> e instanceof SimEvent.Milestone ms && ms.event().equals(GridSubsystem.SYNC_CLOSED));
    }

    @Test
    void syncCheckRelayRefusesClosingOutOfTheWindow() {
        TwoMachines m = twoMachines(-30, true);
        BreakerResponse r = closeAtAngle(m, 115, 125);
        assertThat(r).isEqualTo(BreakerResponse.INTERLOCK);
        run(m.w, 1);
        assertThat(m.tie.closed()).isFalse();
        assertThat(m.g2.syncCount()).isZero();
    }

    @Test
    void synchroscopeGuidedClosingIsSmoothAndTheMachinesShareTheLoad() {
        // G1 под нагрузкой 0,4 о.е. идёт на 1,6 % ниже; G2 подстроен чуть быстрее сети: стрелка медленно по часовой
        TwoMachines m = twoMachines(-14, false);
        trimForSync(m);
        BreakerResponse r = closeAtAngle(m, 0, 5);
        assertThat(r).isEqualTo(BreakerResponse.ACCEPTED);
        run(m.w, 5);
        SyncClosing c = incoming(m).lastSync();
        assertThat(c.result()).isEqualTo(SyncResult.OK);
        assertThat(c.torquePu()).isLessThan(0.5);
        assertThat(m.g2.state()).isIn(MachineState.SYNCHRONIZED, MachineState.LOADED);
        // сразу после включения G2 почти без нагрузки; прибавить ему вариатор — статизм перераспределит мощность
        m.g2.setTrimPermil(m.g2.trimPermil() + 25); // выше уставки G1 (0 ‰)
        run(m.w, 20);
        assertThat(m.g1.electricalPowerW() + m.g2.electricalPowerW()).isCloseTo(m.load.powerW(), within(10e3));
        assertThat(m.g2.electricalPowerW()).isGreaterThan(m.g1.electricalPowerW());
        assertThat(m.g1.frequencyHz()).isCloseTo(m.g2.frequencyHz(), within(0.01));
    }

    @Test
    void underfrequencyLoadSheddingDropsTheStageOnDeficit() {
        SimulationWorld w = world();
        GeneratorUnit g = generator(w, BUS_A, 0);
        cell(w, BUS_A, BUS_B, true);
        consumer(w, BUS_B, 200);
        ConsumerUnit big = consumer(w, BUS_B, 500);
        big.setRelays(List.of(new RelaySetting(RelaySetting.Kind.UFLS, RelayGeneration.A1_ELECTROMECHANICAL, 1, 0)));
        big.setSwitchedOn(false);
        run(w, 25);
        assertThat(g.frequencyHz()).isGreaterThan(49);
        big.setSwitchedOn(true); // 1,4 о.е. на машину с статизмом 4 %: частота ниже 48,8 Гц
        double fmin = 50;
        for (int i = 0; i < 20 * 10; i++) {
            tick(w);
            fmin = Math.min(fmin, g.frequencyHz());
        }
        assertThat(fmin).isLessThan(48.8);
        assertThat(big.shed()).isTrue();
        assertThat(big.shedCount()).isEqualTo(1);
        assertThat(g.frequencyHz()).isGreaterThan(48.8);
        assertThat(events).anyMatch(e -> e instanceof SimEvent.Milestone ms && ms.event().equals(GridSubsystem.LOAD_SHED));
    }



    @Test
    void lowVoltageMotorOnSixKilovoltBusBreaksDownAndOvercurrentTrips() {
        SimulationWorld w = world();
        GeneratorUnit g = generator(w, BUS_A, 0);
        SwitchUnit feeder = cell(w, BUS_A, BUS_B, false);
        // генератор 500 кВА (46 А) даёт КЗ ≈ 3 о.е. с затуханием: уставка ≈ 1,1·I_ном, кривая SI, TMS 0,05
        feeder.setRelays(List.of(new RelaySetting(RelaySetting.Kind.OVERCURRENT, RelayGeneration.A1_ELECTROMECHANICAL, 50, 0.05)));
        MotorUnit motor = grid(w).motor(device(w, "electric_motor"));
        motor.setNodes(BUS_B); // 0,4 кВ на шине класса 6 кВ (класс задаёт ячейка)
        motor.setContactor(true);
        run(w, 25);
        assertThat(g.terminalVoltageV()).isGreaterThan(6000);
        feeder.operate(true);
        int ticks = 0;
        while (feeder.closed() || feeder.breakerState() != BreakerState.OPEN) {
            tick(w);
            assertThat(++ticks).isLessThan(20 * 2);
        }
        assertThat(motor.state()).isEqualTo(MotorState.FAULTED);
        assertThat(feeder.protectionTrips()).isEqualTo(1);
    }

    @Test
    void overheadSpanFeedsTheFarConsumerAndATouchingBlockGroundsIt() {
        SimulationWorld w = world();
        generator(w, BUS_A, 0);
        cell(w, BUS_A, BUS_B, true);
        ConnectorUnit near = grid(w).connector(device(w, "wire_connector_mv"), true);
        near.setNodes(BUS_B);
        ConnectorUnit far = grid(w).connector(device(w, "wire_connector_mv"), true);
        far.setNodes(BUS_D);
        ConsumerUnit c = consumer(w, BUS_D, 100);
        grid(w).putLine(77, near.id(), far.id(), "m35", false, 30);
        run(w, 25);
        assertThat(c.energized()).isTrue();
        assertThat(grid(w).lineCurrentA(77)).isCloseTo(100e3 / (Math.sqrt(3) * 6300 * 0.9), within(4.0));

        grid(w).setLineTouch(77, 0.5); // дерево в габарите: «земля» в сети с изолированной нейтралью, без отключения
        run(w, 1);
        BusId b = grid(w).busOf(BUS_B);
        assertThat(grid(w).grid().groundFaultPresent(b)).isTrue();
        assertThat(c.energized()).isTrue();
        grid(w).setLineTouch(77, Double.NaN);
        run(w, 1);
        assertThat(grid(w).grid().groundFaultPresent(grid(w).busOf(BUS_B))).isFalse();

        grid(w).removeLine(77);
        run(w, 1);
        assertThat(c.energized()).isFalse();
    }

    @Test
    void rebuildCarriesTheRunningMachine() {
        SimulationWorld w = world();
        GeneratorUnit g = generator(w, BUS_A, 0);
        cell(w, BUS_A, BUS_B, true);
        ConsumerUnit c = consumer(w, BUS_B, 300);
        run(w, 30);
        double p = g.electricalPowerW(), v = g.terminalVoltageV();
        int rebuilds = grid(w).rebuilds();
        grid(w).connector(device(w, "wire_connector_lv"), false).setNodes(BUS_D); // постройка рядом пересобирает сеть
        run(w, 0.25);
        assertThat(grid(w).rebuilds()).isEqualTo(rebuilds + 1);
        assertThat(g.terminalVoltageV()).isCloseTo(v, within(v * 0.03));
        assertThat(g.electricalPowerW()).isCloseTo(p, within(p * 0.05));
        assertThat(c.energized()).isTrue();
    }

    @Test
    void stateSurvivesSaveAndLoad() {
        TwoMachines m = twoMachines(-14, false);
        trimForSync(m);
        closeAtAngle(m, 0, 5);
        run(m.w, 10);
        MutableStateTree saved = m.w.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(m.w.stateHash());
        GridSubsystem gc = grid(c);
        GeneratorUnit g2 = (GeneratorUnit) gc.unit(m.g2.id());
        SwitchUnit tie = (SwitchUnit) gc.unit(m.tie.id());
        shafts.clear();
        for (GridUnit u : gc.units()) if (u instanceof GeneratorUnit g) shafts.add(g);
        run(c, 2);
        assertThat(tie.closed()).isTrue();
        assertThat(g2.state()).isIn(MachineState.SYNCHRONIZED, MachineState.LOADED);
        assertThat(g2.terminalVoltageV()).isCloseTo(m.g2.terminalVoltageV(), within(300.0));
    }
}
