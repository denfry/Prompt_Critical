package dev.denfry.promptcritical.sim.automation.twin;

import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.consequence.BudgetStats;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageGrant;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.consequence.DenyReason;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.ExternalInputs;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import dev.denfry.promptcritical.sim.core.exec.WorkerPool;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.EngineUnit;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import dev.denfry.promptcritical.sim.plant.SteamPlantConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Эталонная установка «котёл I0» (11 §11.3.2): огнетрубный котёл с паровой машиной в собственном
 * {@link SimulationWorld} с той же подсистемой {@link SteamPlant}, что и мир (03d §3d.9.1: те же подсистемы, тот же
 * детерминизм). Бюджет разрушений песочницы отказывает во всём: взрыв котла — только {@link #failure()}.
 *
 * <p>Механизмы: {@code firing} — подача угля в долях номинальной {@code nominalFuelKgS} (кочегар забрасывает раз в
 * секунду); {@code feed_pump} — питание из запаса воды (поплавковый регулятор котла); {@code stop_valve} — стопорный
 * вентиль машины; {@code load_kw} — нагрузка вала. Пустой котёл не разжигается: установка начинается горячей, под
 * давлением и с нормальным уровнем.
 */
public final class BoilerTwinPlant implements TwinPlant {
    public static final String ID = "boiler_i0";
    public static final String PRESSURE = "pressure_bar", LEVEL = "level_m", LEVEL_ABOVE_CROWN = "level_above_crown_m",
            WATER_TEMP = "water_temp_c", RELIEF_OPEN = "relief_open", LIT = "lit", SPEED = "speed_pu", STEAM = "steam_kg_s",
            FIRING = "firing", FEED_PUMP = "feed_pump", STOP_VALVE = "stop_valve", LOAD = "load_kw";
    /** Нагрузка вала по умолчанию и предел, кВт. */
    public static final double LOAD_KW = 60, LOAD_MAX_KW = 150;
    public static final List<String> SIGNALS = List.of(PRESSURE, LEVEL, LEVEL_ABOVE_CROWN, WATER_TEMP, RELIEF_OPEN, LIT, SPEED, STEAM),
            ACTUATORS = List.of(FIRING, FEED_PUMP, STOP_VALVE, LOAD);
    private static final double[] UNIT = {0, 1}, LOAD_RANGE = {0, LOAD_MAX_KW};
    private static final int STOKE_TICKS = 20;

    private final WorkerPool pool = WorkerPool.direct();
    private final SimulationWorld world;
    private final SteamPlant plant;
    private final SimId boilerId, engineId;
    private final BoilerUnit boiler;
    private final EngineUnit engine;
    private final CommandSource source = new CommandSource.Panel(new UUID(0, 0), 0L);
    private double firing = 1, feedPump = 1, stopValve = 1, loadKw = LOAD_KW;
    private long seq, ticks;
    private boolean damaged;
    private String failure;

    /**
     * Горячий котёл: давление {@code pressureBar} (изб.), уровень {@code levelAboveCrownM} над потолком жаровой трубы
     * (NaN — нормальный), слой угля, машина под нагрузкой.
     */
    public BoilerTwinPlant(double pressureBar, double levelAboveCrownM, long seed) {
        world = new SimulationWorld(SimConfig.defaults(), seed, 0, pool);
        world.registerService(DamageBudget.class, new DenyAll());
        world.register(new SteamPlant(SteamPlantConfig.defaults()));
        plant = world.subsystem(SteamPlant.class);
        boilerId = world.topology().addDevice("promptcritical:fire_tube_boiler", new GridPos(0, 64, 0));
        boiler = plant.addBoiler(boilerId, 288.15);
        boiler.setChimneyHeight(20);
        engineId = world.topology().addDevice("promptcritical:steam_engine_cylinder", new GridPos(0, 64, 4));
        engine = plant.addEngine(engineId);
        plant.connect(engineId, boilerId);
        boiler.setFeedSupply(1e5, 288.15);
        plant.presetWater(boilerId, SteamPlant.ATMOSPHERE_PA + pressureBar * 1e5,
                Double.isNaN(levelAboveCrownM) ? boiler.spec().normalLevelM() : boiler.spec().crownHeightM() + levelAboveCrownM);
        submit(new SimCommand.ChargeFuel(boilerId, PlantFuels.COAL, boiler.spec().nominalFuelKgS() * 60));
        submit(new SimCommand.Operate(boilerId, SteamPlant.IGNITE));
        submit(new SimCommand.Operate(engineId, SteamPlant.FIT_GOVERNOR_DASHPOT));
        submit(new SimCommand.Operate(engineId, SteamPlant.TOGGLE_STOP_VALVE));
        // разгон машины до номинала под малой нагрузкой, затем рабочая нагрузка — до начала урока; питание закрыто,
        // чтобы регулятор не долил котёл до нормального уровня холодной водой
        loadKw = 10;
        feedPump = 0;
        for (int k = 0; k < 20 * 600 && engine.omega() < 0.95 * engine.nominalOmega(); k++) step();
        loadKw = LOAD_KW;
        for (int k = 0; k < 20 * 60; k++) step();
        feedPump = 1;
    }

    private void submit(SimCommand c) {
        world.commands().submit(c, source, seq++);
    }

    /** Подсистема песочницы — для тестов и разбора. */
    public SteamPlant steamPlant() {
        return plant;
    }

    @Override
    public String template() {
        return ID;
    }

    @Override
    public List<String> signals() {
        return SIGNALS;
    }

    @Override
    public double read(String signal) {
        return switch (signal) {
            case PRESSURE -> (boiler.pressurePa() - SteamPlant.ATMOSPHERE_PA) / 1e5;
            case LEVEL -> boiler.gaugeGlassLevelM();
            case LEVEL_ABOVE_CROWN -> boiler.gaugeGlassLevelM() - boiler.spec().crownHeightM();
            case WATER_TEMP -> boiler.waterTempK() - 273.15;
            case RELIEF_OPEN -> boiler.reliefOpen() ? 1 : 0;
            case LIT -> boiler.lit() ? 1 : 0;
            case SPEED -> engine.omega() / engine.nominalOmega();
            case STEAM -> engine.steamFlowKgS();
            default -> Double.NaN;
        };
    }

    @Override
    public List<String> actuators() {
        return ACTUATORS;
    }

    @Override
    public double[] range(String actuator) {
        return LOAD.equals(actuator) ? LOAD_RANGE : UNIT;
    }

    @Override
    public boolean discrete(String actuator) {
        return FEED_PUMP.equals(actuator) || STOP_VALVE.equals(actuator);
    }

    @Override
    public double actuator(String actuator) {
        return switch (actuator) {
            case FIRING -> firing;
            case FEED_PUMP -> feedPump;
            case STOP_VALVE -> engine.stopValveOpen() ? 1 : 0;
            case LOAD -> loadKw;
            default -> Double.NaN;
        };
    }

    @Override
    public void actuate(String actuator, double value) {
        switch (actuator) {
            case FIRING -> firing = TwinPlant.clamp(value, UNIT);
            case FEED_PUMP -> feedPump = value >= 0.5 ? 1 : 0;
            case STOP_VALVE -> stopValve = value >= 0.5 ? 1 : 0;
            case LOAD -> loadKw = TwinPlant.clamp(value, LOAD_RANGE);
            default -> throw new IllegalArgumentException("no actuator " + actuator);
        }
    }

    @Override
    public void step() {
        boolean dead = boiler.rupture() != BoilerRupture.NONE;
        if (!dead && ticks % STOKE_TICKS == 0 && firing > 0)
            submit(new SimCommand.ChargeFuel(boilerId, PlantFuels.COAL, firing * boiler.spec().nominalFuelKgS() * STOKE_TICKS * DT));
        if ((stopValve > 0) != engine.stopValveOpen()) submit(new SimCommand.Operate(engineId, SteamPlant.TOGGLE_STOP_VALVE));
        boiler.setFeedSupply(feedPump > 0 ? 1e5 : 0, 288.15);
        // как биндинг: сеть Create нагружает машину, только пока вал отдаёт обороты (R3: ω ≥ 0,2 ω₀)
        engine.setLoadPower(engine.omega() >= 0.2 * engine.nominalOmega() ? loadKw * 1e3 : 0);
        world.tick(ExternalInputs.NONE);
        world.drainEvents(e -> {
        });
        ticks++;
        if (failure == null) {
            if (boiler.rupture() != BoilerRupture.NONE || damaged) failure = "boiler_" + boiler.rupture().name().toLowerCase(java.util.Locale.ROOT);
            else if (boiler.plugMelted()) failure = "fusible_plug_melted";
        }
    }

    @Override
    public String failure() {
        return failure;
    }

    @Override
    public void close() {
        world.close();
        pool.close();
    }

    /** Бюджет песочницы: двойник не трогает мир (03d §3d.9.1), запрос разрушения — признак аварии установки. */
    private final class DenyAll implements DamageBudget {
        @Override
        public DamageGrant submit(DamageRequest request) {
            damaged = true;
            return new DamageGrant(request.requestId(), 0, 0, 0, DenyReason.MODE_FORBIDS);
        }

        @Override
        public void drain(long gameTick, BlockMutator mutator, TerritoryOracle territory) {
        }

        @Override
        public BudgetStats stats() {
            return new BudgetStats(0, 0, 0, Map.of());
        }
    }
}
