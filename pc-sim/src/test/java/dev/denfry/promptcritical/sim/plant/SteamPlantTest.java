package dev.denfry.promptcritical.sim.plant;

import static org.assertj.core.api.Assertions.assertThat;

import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.FlyballGovernor;
import dev.denfry.promptcritical.sim.consequence.BudgetStats;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageGrant;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.consequence.DenyReason;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Котельная в мире симуляции (07 §7.9.4, §7.9.7): структура, растопка, ПК, упуск воды, взрыв, сохранение. */
class SteamPlantTest {
    private static final double TICKS_PER_S = 20;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<DamageRequest> damage = new ArrayList<>();
    private final List<SimEvent> events = new ArrayList<>();
    private long seq;

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

    private final DamageBudget recorder = new DamageBudget() {
        @Override
        public DamageGrant submit(DamageRequest request) {
            damage.add(request);
            return new DamageGrant(request.requestId(), 10, 0, 0, DenyReason.NONE);
        }

        @Override
        public void drain(long gameTick, BlockMutator mutator, TerritoryOracle territory) {
        }

        @Override
        public BudgetStats stats() {
            return new BudgetStats(0, 0, 0, Map.of());
        }
    };

    private SimulationWorld world() {
        SimulationWorld w = new SimulationWorld(SimConfig.defaults(), 7, 0, pool);
        w.registerService(DamageBudget.class, recorder);
        w.register(new SteamPlant(SteamPlantConfig.defaults()));
        worlds.add(w);
        return w;
    }

    private static SteamPlant plant(SimulationWorld w) {
        return w.subsystem(SteamPlant.class);
    }

    private static SimId boiler(SimulationWorld w, int x) {
        SimId id = w.topology().addDevice("promptcritical:fire_tube_boiler", new GridPos(x, 64, 0));
        plant(w).addBoiler(id, 288.15).setChimneyHeight(20);
        return id;
    }

    private static SimId engine(SimulationWorld w, SimId boiler, int x) {
        SimId id = w.topology().addDevice("promptcritical:steam_engine_cylinder", new GridPos(x, 64, 4));
        plant(w).addEngine(id);
        plant(w).connect(id, boiler);
        return id;
    }

    private void command(SimulationWorld w, SimCommand c) {
        w.commands().submit(c, new CommandSource.Panel(new java.util.UUID(1, 1), 0L), seq++);
    }

    private void run(SimulationWorld w, double seconds) {
        for (int k = 0, n = (int) Math.round(seconds * TICKS_PER_S); k < n; k++) {
            w.tick(ExternalInputs.NONE);
            w.drainEvents(events::add);
        }
    }

    /** Котёл под давлением с нормальным уровнем и горящим слоем угля под номинальную подачу. */
    private void hotBoiler(SimulationWorld w, SimId b, double pressure, double level) {
        plant(w).presetWater(b, pressure, level);
        command(w, new SimCommand.ChargeFuel(b, PlantFuels.COAL, 0.3 * 600));
        command(w, new SimCommand.Operate(b, SteamPlant.IGNITE));
    }

    /** Подача угля «забрасывателем»: раз в секунду порция под номинальный расход. */
    private void stoke(SimulationWorld w, SimId b, double seconds, double kgS) {
        for (int s = 0; s < seconds; s++) {
            command(w, new SimCommand.ChargeFuel(b, PlantFuels.COAL, kgS));
            run(w, 1);
        }
    }

    @Test
    void coldBoilerFillsRaisesSteamAndDrivesEngine() {
        SimulationWorld w = world();
        SimId b = boiler(w, 0);
        SimId e = engine(w, b, 0);
        BoilerUnit u = plant(w).boiler(b);
        EngineUnit m = plant(w).engine(e);
        u.setFeedSupply(1e5, 288.15);
        command(w, new SimCommand.Operate(b, SteamPlant.IGNITE));
        run(w, 1);
        assertThat(u.lit()).as("пустую решётку не разжечь").isFalse();
        assertThat(u.pressurePa()).as("холодный котёл под разрежением").isLessThan(5_000);

        run(w, 5 * 60);
        assertThat(u.gaugeGlassLevelM()).as("налит до нормального уровня")
                .isBetween(u.spec().normalLevelM() - 0.03, u.spec().normalLevelM() + 0.03);
        assertThat(u.takeFeedUsed()).isBetween(9_000.0, 11_000.0);

        command(w, new SimCommand.ChargeFuel(b, PlantFuels.COAL, 180));
        command(w, new SimCommand.Operate(b, SteamPlant.IGNITE));
        command(w, new SimCommand.Operate(e, SteamPlant.FIT_GOVERNOR_DASHPOT));
        double t = 0, firstTurn = Double.NaN;
        while (m.omega() < 0.95 * m.nominalOmega() && t < 5400) {
            // машинист открывает стопорный вентиль, когда давление поднялось
            if (!m.stopValveOpen() && u.pressurePa() > 0.5e6) command(w, new SimCommand.Operate(e, SteamPlant.TOGGLE_STOP_VALVE));
            // как биндинг: сеть Create нагружает машину, только пока вал отдаёт обороты (R3: ω ≥ 0,2 ω₀)
            m.setLoadPower(m.omega() >= 0.2 * m.nominalOmega() ? 10e3 : 0);
            stoke(w, b, 10, 0.3);
            t += 10;
            if (Double.isNaN(firstTurn) && m.omega() >= 0.2 * m.nominalOmega()) firstTurn = t;
        }
        System.out.printf("first turn %.1f min, nominal speed %.1f min, p %.3f MPa%n", firstTurn / 60, t / 60,
                u.pressurePa() / 1e6);
        assertThat(firstTurn / 60).as("мин от холодной воды до первого оборота вала (R3: ω ≥ 0,2 ω₀)").isLessThan(40);
        assertThat(t / 60).as("мин до номинальных оборотов").isLessThan(45);
        while (u.pressurePa() < 1.1e6 && t < 5400) {
            stoke(w, b, 10, 0.3);
            t += 10;
        }
        for (int k = 0; k < 600; k++) {
            m.setLoadPower(m.omega() >= 0.2 * m.nominalOmega() ? 100e3 : 0);
            stoke(w, b, 1, 0.3);
        }
        assertThat(m.omega() / m.nominalOmega()).as("обороты в полосе статизма")
                .isBetween(1 - FlyballGovernor.DROOP, 1 + FlyballGovernor.DROOP);
        assertThat(m.shaftPowerW()).isBetween(95e3, 105e3);
        assertThat(m.capacityW()).as("запас мощности под ёмкость Create").isGreaterThan(110e3);
        assertThat(u.pressurePa()).isLessThan(1.03 * u.spec().reliefSetPressure());
        assertThat(u.gaugeGlassLevelM()).isBetween(u.spec().normalLevelM() - 0.05, u.spec().normalLevelM() + 0.05);
        assertThat(u.ashKg()).isPositive();
    }

    @Test
    void reliefValveHoldsPressureWithEngineStopped() {
        SimulationWorld w = world();
        SimId b = boiler(w, 0);
        SimId e = engine(w, b, 0);
        BoilerUnit u = plant(w).boiler(b);
        u.setFeedSupply(1e5, 288.15);
        hotBoiler(w, b, 1.2e6, 0.67);
        double pMax = 0;
        boolean whistled = false;
        for (int s = 0; s < 600; s++) {
            stoke(w, b, 1, 0.3);
            pMax = Math.max(pMax, u.pressurePa());
            whistled |= u.reliefOpen();
        }
        assertThat(plant(w).engine(e).stopValveOpen()).as("стопорный вентиль закрыт по умолчанию").isFalse();
        assertThat(u.reliefLifts()).isPositive();
        assertThat(whistled).isTrue();
        assertThat(pMax).isLessThan(1.03 * u.spec().reliefSetPressure());
        assertThat(u.rupture()).isEqualTo(BoilerRupture.NONE);
    }

    @Test
    void lowWaterMeltsPlugAndQuenchesFurnace() {
        SimulationWorld w = world();
        SimId b = boiler(w, 0);
        BoilerUnit u = plant(w).boiler(b);
        hotBoiler(w, b, 1.2e6, 0.5);
        double t = 0;
        while (!u.plugMelted() && t < 3600) {
            stoke(w, b, 10, 0.3);
            t += 10;
        }
        assertThat(u.plugMelted()).isTrue();
        assertThat(u.lit()).isFalse();
        assertThat(events).anyMatch(ev -> ev instanceof SimEvent.Journal j && j.severity() == Severity.WARNING
                && j.source().equals(b));
        command(w, new SimCommand.Operate(b, SteamPlant.FIT_FUSIBLE_PLUG));
        run(w, 1);
        assertThat(u.plugMelted()).as("пробку не меняют под давлением").isTrue();
        command(w, new SimCommand.Operate(b, SteamPlant.IGNITE));
        double p0 = u.pressurePa();
        run(w, 600);
        assertThat(u.lit()).as("с выплавленной пробкой топку не разжечь").isFalse();
        assertThat(u.pressurePa()).as("пар уходит через пробку (∅ ½″ — часами)").isLessThan(0.9 * p0);
        assertThat(u.rupture()).isEqualTo(BoilerRupture.NONE);
        plant(w).presetWater(b, SteamPlant.ATMOSPHERE_PA, 0.3);   // котёл остыл
        command(w, new SimCommand.Operate(b, SteamPlant.FIT_FUSIBLE_PLUG));
        run(w, 1);
        assertThat(u.plugMelted()).isFalse();
        assertThat(damage).isEmpty();
    }

    @Test
    void lowWaterWithoutPlugExplodes() {
        SimulationWorld w = world();
        SimId b = boiler(w, 0);
        BoilerUnit u = plant(w).boiler(b);
        u.boiler().setPlugFitted(false);
        hotBoiler(w, b, 1.2e6, 0.5);
        double t = 0;
        while (u.rupture() == BoilerRupture.NONE && t < 3600) {
            stoke(w, b, 10, 0.3);
            t += 10;
        }
        assertThat(u.rupture()).isEqualTo(BoilerRupture.CROWN_COLLAPSE);
        assertThat(damage).hasSize(1);
        DamageRequest r = damage.getFirst();
        assertThat(r.kind()).isEqualTo(DamageKind.BLAST);
        assertThat(r.source()).isEqualTo(b);
        assertThat(r.origin()).isEqualTo(new GridPos(0, 64, 0));
        assertThat(r.energyJ() / 4.184e6).as("кг ТНТ").isBetween(10.0, 60.0);
        assertThat(events).anyMatch(ev -> ev instanceof SimEvent.Journal j && j.severity() == Severity.CRITICAL);
        assertThat(events).anyMatch(ev -> ev instanceof SimEvent.DamageRequested);
        stoke(w, b, 10, 0.3);
        assertThat(damage).as("взрыв один раз").hasSize(1);
        assertThat(u.lit()).isFalse();
    }

    @Test
    void saveLoadKeepsStateAndDeterminism() {
        SimulationWorld a = world();
        SimId b = boiler(a, 0);
        SimId e = engine(a, b, 0);
        plant(a).boiler(b).setFeedSupply(5e4, 288.15);
        hotBoiler(a, b, 1.0e6, 0.67);
        command(a, new SimCommand.Operate(e, SteamPlant.TOGGLE_STOP_VALVE));
        plant(a).engine(e).setLoadPower(50e3);
        stoke(a, b, 30, 0.3);

        MutableStateTree saved = a.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(a.stateHash());
        assertThat(plant(c).engine(e).boiler()).isEqualTo(b);
        for (int s = 0; s < 30; s++) {
            for (SimulationWorld w : List.of(a, c)) stoke(w, b, 1, 0.3);
        }
        assertThat(plant(c).boiler(b).pressurePa()).isCloseTo(plant(a).boiler(b).pressurePa(),
                org.assertj.core.data.Percentage.withPercentage(0.01));
        assertThat(plant(c).engine(e).omega()).isCloseTo(plant(a).engine(e).omega(),
                org.assertj.core.data.Percentage.withPercentage(0.01));
    }

    @Test
    void removingBoilerDisconnectsEngineAndBarOverMovesCrank() {
        SimulationWorld w = world();
        SimId b = boiler(w, 0);
        SimId e = engine(w, b, 0);
        run(w, 1);
        assertThat(plant(w).engine(e).chestPressurePa()).isFinite();
        w.topology().removeDevice(b);
        run(w, 1);
        assertThat(plant(w).boiler(b)).isNull();
        assertThat(plant(w).engine(e).boiler()).isEqualTo(SimId.NONE);
        assertThat(plant(w).engine(e).chestPressurePa()).isNaN();
        double before = plant(w).engine(e).crankAngle();
        command(w, new SimCommand.Operate(e, SteamPlant.BAR_OVER));
        run(w, 0.05);
        assertThat(plant(w).engine(e).crankAngle()).isCloseTo((before + Math.PI / 3) % (2 * Math.PI),
                org.assertj.core.data.Offset.offset(0.05));
        assertThat(plant(w).boilerCount()).isZero();
        assertThat(plant(w).engineCount()).isEqualTo(1);
    }
}
