package dev.denfry.promptcritical.sim.grid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Аварийный дизель-генератор 07 §7.11: пуск по потере внешнего питания и приём нагрузки за ≤ 15 с, отказ пуска и
 * повторные попытки до исчерпания воздуха, блокировка без оперативного тока, провал частоты при набросе ступени
 * (IEEE 387, §7.11.4), расход топлива (пример T7.28), сохранение посреди работы.
 */
class DieselGeneratorTest {
    private static final long BUS_A = 21, BUS_B = 22;
    /** 625 кВ·А: двигатель 500 кВт, как в примере T7.28. */
    private static final double VA = 625e3;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<GeneratorUnit> shafts = new ArrayList<>();
    private final List<dev.denfry.promptcritical.sim.core.SimEvent.Milestone> milestones = new ArrayList<>();
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

    private GeneratorUnit diesel(SimulationWorld w, long node) {
        GeneratorUnit g = grid(w).dieselGenerator(device(w, "diesel_generator"), VA);
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(node);
        g.diesel().setConditions(true, true, 15, false, 45);
        return g;
    }

    /** Внешнее питание: генератор на валу Create, 64 об/мин, с возбуждением. */
    private GeneratorUnit offsite(SimulationWorld w, long node) {
        GeneratorUnit g = grid(w).generator(device(w, "shaft_generator"));
        g.setVoltageClass(VoltageClass.MV_6_3);
        g.setNodes(node);
        g.setExciterPresent(true);
        g.setFieldOn(true);
        shafts.add(g);
        return g;
    }

    private SwitchUnit cell(SimulationWorld w, long a, long b, boolean closed) {
        SwitchUnit s = grid(w).switchUnit(device(w, "switchgear_cell"), SwitchUnit.Kind.MV_BREAKER);
        s.setNodes(a, b);
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
        w.drainEvents(e -> {
            if (e instanceof dev.denfry.promptcritical.sim.core.SimEvent.Milestone m) milestones.add(m);
        });
    }

    /** Секунды до события, не дольше {@code limit}; NaN — не дождались. */
    private double until(SimulationWorld w, double limit, java.util.function.BooleanSupplier cond) {
        for (int i = 0; i < limit * 20; i++) {
            if (cond.getAsBoolean()) return i / 20.0;
            tick(w);
        }
        return cond.getAsBoolean() ? limit : Double.NaN;
    }

    @Test
    void lossOfOffsitePowerStartsTheDieselWhichTakesTheBusWithinFifteenSeconds() {
        SimulationWorld w = world();
        offsite(w, BUS_A);
        SwitchUnit incomer = cell(w, BUS_A, BUS_B, true);
        ConsumerUnit c = consumer(w, BUS_B, 100);
        GeneratorUnit dg = diesel(w, BUS_B);
        run(w, 25);
        assertThat(c.energized()).isTrue();
        dg.setAutoStart(true);
        run(w, 5);
        // шина под напряжением от сети: ДГ в резерве
        assertThat(dg.diesel().status()).isEqualTo(DieselEngine.Status.STANDBY);
        assertThat(dg.breakerClosed()).isFalse();

        incomer.operate(false); // потеря внешнего питания
        double t = until(w, 30, () -> dg.breakerClosed() && c.energized());
        assertThat(t).as("ДГ принял шину, с").isLessThanOrEqualTo(15);
        assertThat(dg.diesel().starts()).isEqualTo(1);
        run(w, 10);
        assertThat(dg.frequencyHz()).isCloseTo(50, within(0.5));
        assertThat(dg.terminalVoltageV()).isCloseTo(6300, within(6300 * 0.1));
        assertThat(dg.electricalPowerW()).isCloseTo(c.powerW(), within(10e3));
        assertThat(dg.diesel().fuelKgS()).isPositive();
    }

    /**
     * Блэкстарт (11 §11.2.2 {@code black_start}): шина обесточена, стоявший ДГ сам поднимает её с нагрузкой — веха с
     * поднятой нагрузкой; пока внешнее питание есть, вехи нет.
     */
    @Test
    void dieselRestoringADeadBusIsABlackStart() {
        SimulationWorld w = world();
        offsite(w, BUS_A);
        SwitchUnit incomer = cell(w, BUS_A, BUS_B, true);
        ConsumerUnit c = consumer(w, BUS_B, 100);
        GeneratorUnit dg = diesel(w, BUS_B);
        run(w, 25);
        dg.setAutoStart(true);
        run(w, 5);
        assertThat(milestones).noneMatch(m -> m.event().equals(GridSubsystem.BLACK_START));
        incomer.operate(false);
        until(w, 30, () -> dg.breakerClosed() && c.energized());
        run(w, 2);
        assertThat(milestones).filteredOn(m -> m.event().equals(GridSubsystem.BLACK_START)).singleElement()
                .satisfies(m -> {
                    assertThat(m.source()).isEqualTo(dg.id);
                    assertThat(m.value()).isBetween(GridSubsystem.BLACK_START_MIN_LOAD_W / 1e3, 115.0);
                });
    }

    @Test
    void breakerNeverClosesOntoALiveBus() {
        SimulationWorld w = world();
        offsite(w, BUS_B);
        GeneratorUnit dg = diesel(w, BUS_B);
        run(w, 20);
        dg.diesel().start(); // опробование при живой шине
        run(w, 20);
        assertThat(dg.dieselReady()).isTrue();
        assertThat(dg.closeBreaker()).isFalse();
        dg.setAutoStart(true);
        run(w, 5);
        assertThat(dg.breakerClosed()).isFalse();
        dg.diesel().stop();
        run(w, 240); // выбег агрегата H = 1 с на трении
        assertThat(dg.diesel().status()).isEqualTo(DieselEngine.Status.STANDBY);
        assertThat(dg.diesel().secondsSinceTest()).isLessThan(270); // удачный пуск (≈ 25 с) засчитан опробованием
    }

    @Test
    void failedStartsRetryEveryFiveSecondsUntilTheAirIsGone() {
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        dg.diesel().setFailToStartBase(1);
        dg.setAutoStart(true);
        run(w, 60);
        assertThat(dg.diesel().status()).isEqualTo(DieselEngine.Status.LOCKED_OUT);
        assertThat(dg.diesel().cause()).isEqualTo(DieselEngine.Cause.NO_AIR);
        assertThat(dg.diesel().failures()).isEqualTo(DieselEngine.AIR_STARTS);
        assertThat(dg.diesel().airStarts()).isZero();
        assertThat(dg.breakerClosed()).isFalse();
        // квитирование и обслуживание — снова в резерве, автомат пускает
        dg.diesel().setFailToStartBase(0);
        dg.diesel().service();
        assertThat(dg.diesel().reset()).isTrue();
        double t = until(w, 30, dg::breakerClosed);
        assertThat(t).isLessThanOrEqualTo(15);
    }

    @Test
    void withoutStationDcTheDieselDoesNotCrank() {
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        dg.diesel().setConditions(true, false, 15, false, 45);
        dg.setAutoStart(true);
        run(w, 10);
        assertThat(dg.diesel().status()).isEqualTo(DieselEngine.Status.STANDBY);
        assertThat(dg.diesel().cause()).isEqualTo(DieselEngine.Cause.NO_DC);
        assertThat(dg.diesel().airStarts()).isEqualTo(DieselEngine.AIR_STARTS);
    }

    @Test
    void coldEngineWithoutJacketHeaterAndPoorFuelFailsMoreOften() {
        DieselEngine e = new DieselEngine(VA, 1);
        e.setConditions(true, true, 15, false, 45);
        assertThat(e.failToStartProbability()).isCloseTo(0.005, within(1e-12));
        e.setConditions(true, true, -10, false, 45);
        assertThat(e.failToStartProbability()).isCloseTo(0.02, within(1e-12));
        e.setConditions(true, true, -10, true, 40);
        assertThat(e.failToStartProbability()).isCloseTo(0.005 * 2, within(1e-12));
        e.setConditions(true, true, 15, false, 35);
        assertThat(e.failToStartProbability()).isCloseTo(0.005 * 3 + 0.3, within(1e-12));
    }

    /**
     * ДГ держит шину с нагрузкой, затем наброс ступени: наименьшая скорость после наброса, скорость через 2 с
     * (40 % интервала ступеней 5 с, IEEE 387) и через 20 с, о.е.
     */
    private double[] stepDip(int firstKw, int... stepKw) {
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        if (firstKw > 0) consumer(w, BUS_B, firstKw);
        SwitchUnit step = cell(w, BUS_B, BUS_A, false);
        for (int kw : stepKw) consumer(w, BUS_A, kw);
        dg.setAutoStart(true);
        run(w, 30);
        assertThat(dg.breakerClosed()).isTrue();
        step.operate(true);
        double min = 2, at2 = 0;
        for (int i = 1; i <= 20 * 20; i++) {
            tick(w);
            min = Math.min(min, dg.speedPu());
            if (i == 40) at2 = dg.speedPu();
        }
        return new double[] {min, at2, dg.speedPu()};
    }

    @Test
    void aModestLoadStepIsAcceptedAndAFullStepPullsTheFrequencyDown() {
        // IEEE 387: при набросе ступени частота не ниже 95 %
        double[] modest = stepDip(50, 100);
        assertThat(modest[0]).isGreaterThanOrEqualTo(0.95);
        assertThat(modest[1]).as("±2 % за 2 с").isCloseTo(1, within(0.02));
        // вся мощность одной ступенью: наддув не успевает, частота «садится», но агрегат её вытягивает
        double[] full = stepDip(0, 500);
        assertThat(full[0]).isLessThan(0.95);
        assertThat(full[2]).isCloseTo(1, within(0.01));
    }

    @Test
    void fuelRateFollowsTheSpecificConsumptionCurve() {
        // T7.28: 500 кВт на 75 % нагрузки, b = 0,209 кг/(кВт·ч), 78,4 кг/ч
        assertThat(DieselEngine.fuelKgPerHour(375, 500)).isCloseTo(78.4, within(0.1));
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        ConsumerUnit c = consumer(w, BUS_B, 300);
        dg.setAutoStart(true);
        run(w, 40);
        assertThat(c.energized()).isTrue();
        double kw = dg.diesel().torquePu() * dg.speedPu() * VA / 1e3;
        assertThat(kw).isCloseTo(c.powerW() / 1e3, within(15.0));
        assertThat(dg.diesel().fuelKgS() * 3600).isCloseTo(DieselEngine.fuelKgPerHour(kw, 500), within(0.01));
    }

    @Test
    void loadSequencerStripsTheBusAndReconnectsTheLargestLoadsFirstEveryFiveSeconds() {
        SimulationWorld w = world();
        offsite(w, BUS_A);
        SwitchUnit incomer = cell(w, BUS_A, BUS_B, true);
        ConsumerUnit small = consumer(w, BUS_B, 50), large = consumer(w, BUS_B, 300), medium = consumer(w, BUS_B, 100);
        GeneratorUnit dg = diesel(w, BUS_B);
        dg.setSequencer(true);
        run(w, 25);
        dg.setAutoStart(true);
        incomer.operate(false);
        until(w, 30, dg::breakerClosed);
        assertThat(dg.breakerClosed()).isTrue();
        assertThat(dg.pendingSteps()).isEqualTo(3);
        assertThat(List.of(small.switchedOn(), large.switchedOn(), medium.switchedOn())).containsOnly(false);
        run(w, GeneratorUnit.SEQUENCE_STEP_S + 0.5);
        assertThat(large.switchedOn()).isTrue();
        assertThat(medium.switchedOn()).isFalse();
        run(w, GeneratorUnit.SEQUENCE_STEP_S);
        assertThat(medium.switchedOn()).isTrue();
        assertThat(small.switchedOn()).isFalse();
        run(w, GeneratorUnit.SEQUENCE_STEP_S);
        assertThat(small.switchedOn()).isTrue();
        assertThat(dg.pendingSteps()).isZero();
        run(w, 5);
        assertThat(small.energized() && medium.energized() && large.energized()).isTrue();
        assertThat(dg.frequencyHz()).isCloseTo(50, within(0.5));
    }

    @Test
    void stopsWhenTheDayTankRunsDryAndLocksOut() {
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        consumer(w, BUS_B, 100);
        dg.setAutoStart(true);
        run(w, 30);
        assertThat(dg.breakerClosed()).isTrue();
        dg.diesel().setConditions(false, true, 15, false, 45);
        run(w, 30);
        assertThat(dg.breakerClosed()).isFalse();
        assertThat(dg.diesel().cause()).isEqualTo(DieselEngine.Cause.NO_FUEL);
        assertThat(dg.diesel().status()).isIn(DieselEngine.Status.COASTING, DieselEngine.Status.LOCKED_OUT);
    }

    @Test
    void stateSurvivesSaveAndLoadWhileCarryingTheBus() {
        SimulationWorld w = world();
        GeneratorUnit dg = diesel(w, BUS_B);
        consumer(w, BUS_B, 200);
        dg.setAutoStart(true);
        run(w, 30);
        assertThat(dg.breakerClosed()).isTrue();

        MutableStateTree tree = new MutableStateTree();
        grid(w).save(new TreeStateWriter(tree));
        SimulationWorld w2 = world();
        grid(w2).load(new TreeStateReader(tree));
        GeneratorUnit back = null;
        for (SimId id : List.of(dg.id)) back = grid(w2).generator(id);
        assertThat(back.diesel()).isNotNull();
        back.diesel().setConditions(true, true, 15, false, 45);
        assertThat(back.breakerClosed()).isTrue();
        assertThat(back.autoStart()).isTrue();
        assertThat(back.diesel().status()).isEqualTo(DieselEngine.Status.RUNNING);
        assertThat(back.diesel().starts()).isEqualTo(1);
    }
}
