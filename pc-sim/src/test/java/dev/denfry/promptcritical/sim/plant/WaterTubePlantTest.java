package dev.denfry.promptcritical.sim.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.MftCause;
import dev.denfry.promptcritical.sim.consequence.BudgetStats;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageGrant;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.consequence.DenyReason;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.CommandStatus;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.MigrationRegistry;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import dev.denfry.promptcritical.sim.utilities.Deaerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Водотрубный котёл I1 класса ДКВР в котельной (07 §7.9.5–7.9.6, 7.18): розжиг по BMS, регуляторы, ПАЗ, взрыв топки. */
class WaterTubePlantTest {
    private static final double TICKS_PER_S = 20;
    private static final String GAS = ProcessGas.MINE_METHANE.fluidId;
    private final WorkerPool pool = WorkerPool.direct();
    private final List<SimulationWorld> worlds = new ArrayList<>();
    private final List<DamageRequest> damage = new ArrayList<>();
    private long seq;

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

    @AfterEach
    void close() {
        for (SimulationWorld w : worlds) w.close();
        pool.close();
    }

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

    /** Топка 5×8×5 (внутри 3×6×3: 54 м³, 90 м²), 2 газовые горелки, по 3 блока пароперегревателя и экономайзера, 2 ТВП. */
    private static SimId boiler(SimulationWorld w) {
        SimId id = w.topology().addDevice("promptcritical:water_tube_boiler", new GridPos(0, 64, 0));
        WaterTubeUnit u = plant(w).addWaterTubeBoiler(id, BurnerKind.GAS, 2, 54, 90, 3, 3, 2, GAS, 288.15);
        u.setFans(u.ratedForcedAirKgS(), u.ratedInducedDraftKgS());
        u.setFuelSupply(1e6);
        u.setFeedSupply(1e7, Deaerator.outletTempK());
        return id;
    }

    private static SimId engine(SimulationWorld w, SimId boiler, int x) {
        SimId id = w.topology().addDevice("promptcritical:steam_engine_cylinder", new GridPos(x, 64, 4));
        plant(w).addEngine(id);
        plant(w).connect(id, boiler);
        return id;
    }

    private CommandStatus command(SimulationWorld w, SimId target, String action) {
        w.commands().submit(new SimCommand.Operate(target, action), new CommandSource.Panel(new java.util.UUID(1, 1), 0L),
                seq++);
        w.tick(ExternalInputs.NONE);
        return plant(w).lastBmsResponse(target).accepted() ? CommandStatus.OK : CommandStatus.REJECTED;
    }

    private static void run(SimulationWorld w, double seconds) {
        for (int k = 0, n = (int) Math.round(seconds * TICKS_PER_S); k < n; k++) w.tick(ExternalInputs.NONE);
    }

    /**
     * Как биндинг: сеть Create нагружает машину, только пока вал отдаёт обороты (R3: ω ≥ 0,2 ω₀); полная нагрузка — после
     * разгона (как в {@code SteamPlantTest}).
     */
    private static void drive(SimulationWorld w, List<SimId> engines, double seconds, double loadW) {
        for (int s = 0; s < seconds; s++) {
            for (SimId e : engines) {
                EngineUnit m = plant(w).engine(e);
                double pu = m.omega() / m.nominalOmega();
                m.setLoadPower(pu >= 0.9 ? loadW : pu >= 0.2 ? 10e3 : 0);
            }
            run(w, 1);
        }
    }

    /** Котёл под давлением 1,2 МПа: продувка 300 с, запальник, основное топливо, вторая горелка. */
    private void lightOff(SimulationWorld w, SimId b) {
        plant(w).presetWaterTube(b, 1.2e6, WaterTubeUnit.NORMAL_LEVEL_M);
        run(w, 5);
        assertThat(command(w, b, SteamPlant.BMS_PURGE)).isEqualTo(CommandStatus.OK);
        run(w, 305);
        assertThat(plant(w).waterTube(b).bms()).isEqualTo(BmsState.PURGE_COMPLETE);
        assertThat(command(w, b, SteamPlant.BMS_IGNITER)).isEqualTo(CommandStatus.OK);
        run(w, 1);
        assertThat(plant(w).waterTube(b).bms()).isEqualTo(BmsState.IGNITER_ON);
        assertThat(command(w, b, SteamPlant.BMS_MAIN_FUEL)).isEqualTo(CommandStatus.OK);
        run(w, 25);
        assertThat(plant(w).waterTube(b).bms()).isEqualTo(BmsState.RUNNING);
        assertThat(command(w, b, SteamPlant.BMS_IGNITER)).isEqualTo(CommandStatus.OK);
        run(w, 1);
        assertThat(command(w, b, SteamPlant.BMS_MAIN_FUEL)).isEqualTo(CommandStatus.OK);
        run(w, 10);
    }

    /**
     * ДКВР на шахтном метане: 200 кВт/м³ × 54 м³ = 10,8 МВт(т) по топливу, ≤ 4 МВт на горелку → 8 МВт на двух. Четыре
     * машины по 100 кВт: регулятор давления держит 1,3 МПа, уровень — у нормального, пар перегрет, в топке разрежение.
     */
    @Test
    void dkvrLightsOffAndCarriesEngines() {
        SimulationWorld w = world();
        SimId b = boiler(w);
        List<SimId> engines = new ArrayList<>();
        for (int i = 0; i < 4; i++) engines.add(engine(w, b, 3 * i));
        lightOff(w, b);
        for (SimId e : engines) {
            w.commands().submit(new SimCommand.Operate(e, SteamPlant.TOGGLE_STOP_VALVE),
                    new CommandSource.Panel(new java.util.UUID(1, 1), 0L), seq++);
        }
        drive(w, engines, 600, 100e3);
        WaterTubeUnit u = plant(w).waterTube(b);
        assertThat(u.spec().ratedFuelKgS() * u.boiler().combustor().lhv()).isCloseTo(8e6, within(1e3));
        assertThat(u.bms()).isEqualTo(BmsState.RUNNING);
        assertThat(u.drumPressurePa()).isCloseTo(WaterTubeUnit.PRESSURE_SET_PA, within(0.05e6));
        assertThat(u.drumLevelM()).isCloseTo(0, within(0.05));
        assertThat(u.steamTempK() - u.outputs().combustion.adiabaticFlameK).isNegative();
        assertThat(u.steamTempK()).isGreaterThan(463.15 + 20);
        assertThat(u.outputs().furnacePressurePa).isCloseTo(WaterTubeUnit.FURNACE_SET_PA, within(20.0));
        assertThat(u.outputs().efficiency).isBetween(0.85, 0.95);
        assertThat(u.outputs().combustion.excessAir).isBetween(1.05, 1.2);
        for (SimId e : engines) assertThat(plant(w).engine(e).omega() / plant(w).engine(e).nominalOmega()).isGreaterThan(0.9);
        assertThat(u.lastTrip()).isNull();
    }

    /** T7.24 (домен): топливо без продувки — {@code E_INTERLOCK}; продувка без вентиляторов — тоже. */
    @Test
    void interlocksRejectFuelWithoutPurge() {
        SimulationWorld w = world();
        SimId b = boiler(w);
        plant(w).presetWaterTube(b, 1.2e6, WaterTubeUnit.NORMAL_LEVEL_M);
        run(w, 1);
        assertThat(command(w, b, SteamPlant.BMS_MAIN_FUEL)).isEqualTo(CommandStatus.REJECTED);
        assertThat(plant(w).lastBmsResponse(b)).isEqualTo(BmsResponse.INTERLOCK_PURGE);
        assertThat(command(w, b, SteamPlant.BMS_IGNITER)).isEqualTo(CommandStatus.REJECTED);
        assertThat(plant(w).lastBmsResponse(b)).isEqualTo(BmsResponse.INTERLOCK_PURGE);
        plant(w).waterTube(b).setFans(0, 0);
        run(w, 1);
        assertThat(command(w, b, SteamPlant.BMS_PURGE)).isEqualTo(CommandStatus.REJECTED);
        assertThat(plant(w).lastBmsResponse(b)).isEqualTo(BmsResponse.INTERLOCK_FANS);
        assertThat(plant(w).waterTube(b).outputs().fuelBurnKgS).isZero();
    }

    /** ПАЗ: без питания (пар уходит деаэратору) уровень падает ниже −200 мм — MFT ({@code BOILER_DRUM_LEVEL_LOW_LOW}), потом пост-продувка. */
    @Test
    void lowDrumLevelTripsBoiler() {
        SimulationWorld w = world();
        SimId b = boiler(w);
        lightOff(w, b);
        WaterTubeUnit u = plant(w).waterTube(b);
        u.setFeedSupply(0, Deaerator.outletTempK());
        u.setDeaeratorDemand(2.0);
        for (int s = 0; s < 1200 && u.bms() == BmsState.RUNNING; s++) run(w, 1);
        assertThat(u.lastTrip()).isEqualTo(WaterTubeUnit.Protection.DRUM_LEVEL_LOW_LOW);
        assertThat(u.boiler().bmsControl().lastMftCause()).isEqualTo(MftCause.PROTECTION);
        run(w, 1);
        assertThat(u.bms()).isEqualTo(BmsState.POST_PURGE);
        assertThat(u.outputs().flame).isFalse();
    }

    /**
     * T7.24 (домен): байпас датчиков пламени ключом и неисправный запальник первой горелки — BMS «видит» факел, газ
     * копится в холодной топке (оператор на ручном: газ 60 %, воздух 30 %); через 90 с облако выше нижнего предела, и
     * запальник второй горелки поджигает его — взрыв топки, {@code DamageRequest(BLAST)}.
     */
    @Test
    void bypassedFlameDetectorsLeadToFurnaceExplosion() {
        SimulationWorld w = world();
        SimId b = boiler(w);
        WaterTubeUnit u = plant(w).waterTube(b);
        plant(w).presetWaterTube(b, 0.3e6, WaterTubeUnit.NORMAL_LEVEL_M);
        u.boiler().setIgniterFault(0, true);
        run(w, 5);
        assertThat(command(w, b, SteamPlant.BMS_TOGGLE_BYPASS)).isEqualTo(CommandStatus.OK);
        assertThat(command(w, b, SteamPlant.BMS_PURGE)).isEqualTo(CommandStatus.OK);
        run(w, 305);
        assertThat(command(w, b, SteamPlant.BMS_IGNITER)).isEqualTo(CommandStatus.OK);
        run(w, 1);
        // оператор на ручном: газ на 60 %, воздух на 30 % — облако в топке выше нижнего предела
        u.setManual(0.6, 0.3 * u.spec().ratedAirKgS() / u.ratedForcedAirKgS(), 0.6, 0);
        u.setCombustionAuto(false);
        assertThat(command(w, b, SteamPlant.BMS_MAIN_FUEL)).isEqualTo(CommandStatus.OK);
        run(w, 90);
        assertThat(u.bms()).isEqualTo(BmsState.RUNNING);
        assertThat(u.outputs().flame).isFalse();
        assertThat(u.boiler().accumulatedFuelKg()).isPositive();
        assertThat(command(w, b, SteamPlant.BMS_IGNITER)).isEqualTo(CommandStatus.OK);
        run(w, 1);
        assertThat(u.rupture()).isEqualTo(BoilerRupture.FURNACE_EXPLOSION);
        assertThat(u.exploded()).isTrue();
        assertThat(damage).singleElement().satisfies(d -> {
            assertThat(d.kind()).isEqualTo(DamageKind.BLAST);
            assertThat(d.energyJ()).isEqualTo(u.boiler().explosionEnergyJ());
        });
    }

    /** Деаэратор берёт пар из барабана; на 1 кг пара 1,4 МПа ≈ 6 кг воды 15 °C → 104 °C. */
    @Test
    void deaeratorDrawsDrumSteam() {
        assertThat(Deaerator.outletTempK()).isCloseTo(377.9, within(0.5));
        assertThat(Deaerator.waterPerSteam(2.79e6, 288.15)).isCloseTo(6.3, within(0.2));
        SimulationWorld w = world();
        SimId b = boiler(w);
        plant(w).presetWaterTube(b, 1.2e6, WaterTubeUnit.NORMAL_LEVEL_M);
        WaterTubeUnit u = plant(w).waterTube(b);
        u.setDeaeratorDemand(0.5);
        run(w, 10);
        assertThat(u.takeDeaeratorSteam()).isCloseTo(5.0, within(0.1));
        assertThat(u.takeDeaeratorSteam()).isZero();
    }

    @Test
    void saveLoadKeepsStateAndDeterminism() {
        SimulationWorld a = world();
        SimId b = boiler(a);
        SimId e = engine(a, b, 0);
        lightOff(a, b);
        a.commands().submit(new SimCommand.Operate(e, SteamPlant.TOGGLE_STOP_VALVE),
                new CommandSource.Panel(new java.util.UUID(1, 1), 0L), seq++);
        drive(a, List.of(e), 30, 100e3);

        MutableStateTree saved = a.saveTree();
        SimulationWorld c = world();
        c.load(SimulationWorld.upgrade(saved, new MigrationRegistry()));
        assertThat(c.stateHash()).isEqualTo(a.stateHash());
        assertThat(plant(c).waterTube(b).bms()).isEqualTo(BmsState.RUNNING);
        drive(a, List.of(e), 30, 100e3);
        drive(c, List.of(e), 30, 100e3);
        assertThat(plant(c).waterTube(b).drumPressurePa()).isCloseTo(plant(a).waterTube(b).drumPressurePa(),
                org.assertj.core.data.Percentage.withPercentage(0.01));
        assertThat(plant(c).engine(e).omega()).isCloseTo(plant(a).engine(e).omega(),
                org.assertj.core.data.Percentage.withPercentage(0.01));
        assertThat(plant(c).waterTube(b).bms()).isEqualTo(BmsState.RUNNING);
    }
}
