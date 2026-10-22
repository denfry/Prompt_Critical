package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.BoilerExplosion;
import dev.denfry.promptcritical.sim.combustion.BoilerInputs;
import dev.denfry.promptcritical.sim.combustion.BmsResponse;
import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.BurnerManagementSystem;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoiler;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoilerSpec;
import dev.denfry.promptcritical.sim.combustion.FlyballGovernor;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.combustion.SteamEngineUnit;
import dev.denfry.promptcritical.sim.consequence.BlastScaling;
import dev.denfry.promptcritical.sim.consequence.DamageBudget;
import dev.denfry.promptcritical.sim.consequence.DamageGrant;
import dev.denfry.promptcritical.sim.consequence.DamageKind;
import dev.denfry.promptcritical.sim.consequence.DamageRequest;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.CommandStatus;
import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.TimeDomain;
import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FlowLinkSpec;
import dev.denfry.promptcritical.sim.hydraulics.FluidNetwork;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.hydraulics.FluidNodeSpec;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicEventSink;
import dev.denfry.promptcritical.sim.hydraulics.HydraulicsConfig;
import dev.denfry.promptcritical.sim.hydraulics.LinkKind;
import dev.denfry.promptcritical.sim.hydraulics.StateCarryOver;
import dev.denfry.promptcritical.sim.hydraulics.ValveModel;
import dev.denfry.promptcritical.sim.thermo.CriticalFlowTable;
import dev.denfry.promptcritical.sim.thermo.WaterPackage;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Котельные эпохи I0 одного измерения (07 §7.9.4, §7.9.7) — подсистема мира. Котёл — газовая сторона
 * {@link FireTubeBoiler} и водяной узел {@code SEPARATED} (эквивалентная призма {@link FireTubeBoilerSpec}) с
 * предохранительным клапаном и каналом легкоплавкой пробки на атмосферу; машина — {@link SteamEngineUnit} с
 * золотниковой коробкой ({@code HEM}, 0,1 м³) за дроссельной заслонкой регулятора. Все узлы — в одной гидросети
 * (01): котельные — независимые компоненты связности.
 *
 * <p>Структура (котлы, машины, какой машине какой котёл) меняется вызовами биндинга между тиками; сеть пересобирается
 * в начале следующего FAST-шага с переносом состояния по id узлов. Действия игрока — команды
 * {@link SimCommand.ChargeFuel} и {@link SimCommand.Operate}.
 *
 * <p>Питание — поплавковым регулятором на нормальный уровень с упреждением по расходу пара (инжектор Жиффара с
 * питательным клапаном): из запаса воды блока, налив открытого котла быстрее, чем подпитка под давлением. Разрушение
 * котла — запрос {@code DamageRequest(BLAST)} с энергией вскипания × {@code boiler.explosion_blast_fraction}.
 */
public final class SteamPlant implements Subsystem {
    public static final String ID = "steam_plant";
    public static final double ATMOSPHERE_PA = BoilerExplosion.ATMOSPHERE_PA;
    /** Выхлоп машины в атмосферу через выхлопную трубу (07 §7.9.7), Па. */
    public static final double EXHAUST_PA = 0.11e6;
    /** Высота газохода котла без звеньев трубы над решёткой, м; звено {@code chimney} — 1 м. */
    public static final double UPTAKE_HEIGHT_M = 3;
    /** Действия {@link SimCommand.Operate}. */
    /**
     * Команды BMS котла I1 (07 §7.9.6): продувка, запальник следующей горелки, основное топливо на горелки с
     * запальником, останов, байпас датчиков пламени ключом. Отказ BMS — {@code REJECTED} ({@code E_INTERLOCK}),
     * причина — {@link #lastBmsResponse}.
     */
    public static final String BMS_PURGE = "bms_purge", BMS_IGNITER = "bms_igniter", BMS_MAIN_FUEL = "bms_main_fuel",
            BMS_SHUTDOWN = "bms_shutdown", BMS_TOGGLE_BYPASS = "bms_toggle_bypass";
    public static final String IGNITE = "ignite", FIT_FUSIBLE_PLUG = "fit_fusible_plug",
            FIT_GOVERNOR_DASHPOT = "fit_governor_dashpot", DRAIN = "drain", TOGGLE_STOP_VALVE = "toggle_stop_valve", BAR_OVER = "bar_over";
    /**
     * Событие домена: машина набрала обороты R3 (ω ≥ 0,2 ω₀) и отдаёт момент на вал Create — достижение «Первый пар»
     * (11 §11.2.2); величина {@code omega_pu} — ω/ω₀. Повторно — после остановки ниже 0,1 ω₀.
     */
    public static final String ENGINE_RUNNING = "promptcritical:engine_running";
    /**
     * Замеры для заданий маршрута {@code reach}/{@code operate} (11 §11.1.5): раз в {@link #SAMPLE_TICKS} тиков каждый
     * котёл шлёт давление барабана {@code drum_pressure_mpa}, МПа, каждая машина — {@code omega_pu}.
     */
    public static final String BOILER_STATE = "promptcritical:boiler_state", ENGINE_STATE = "promptcritical:engine_state";
    public static final int SAMPLE_TICKS = 20;
    private static final int SLOW_PHASE = 3;
    /** Дренаж котла (спуск воды перед чисткой накипи), кг/с — только без давления. */
    public static final double DRAIN_KG_S = 50;
    /** Котёл без давления (налив, замена пробки): ниже атмосферного + 50 кПа. */
    public static final double OPEN_PRESSURE_PA = ATMOSPHERE_PA + 50_000;
    /**
     * Низший уровень над потолком жаровых труб, м: до подъёма давления до половины рабочего регулятор питания держит
     * его, а не нормальный — вода при прогреве расширяется (≈ 6 % до 120 °C), и котёл растапливают при низшем уровне.
     */
    public static final double LOW_LEVEL_ABOVE_CROWN_M = 0.10;
    /** Постоянная поплавкового регулятора питания, с. */
    static final double FEED_TIME_S = 30;
    /** Плотность воды для коэффициента регулятора питания, кг/м³. */
    private static final double FEED_RHO = 950;
    /** ПК ∅ 50 мм, золотниковая коробка 0,1 м³. */
    private static final double RELIEF_AREA = Math.PI / 4 * 0.05 * 0.05, CHEST_VOLUME = 0.1;
    /** Машину можно провернуть ломом, пока вал почти стоит, рад/с. */
    private static final double BAR_OVER_MAX_OMEGA = 0.5;
    /** Радиус тяжёлых разрушений Z = 4,5 м/кг^⅓ (ΔP ≈ 50 кПа, Кингери — Булмаш). */
    private static final double DAMAGE_SCALED_DISTANCE = 4.5;
    // роли в id узлов и связей: серийный номер устройства << 3 | роль
    private static final int DRUM = 0, AIR = 1, CHEST = 2, SUPERHEATER = 3;
    private static final int RELIEF = 0, PLUG = 1, THROTTLE = 2, SUPERHEAT = 3;
    /** Пароперегреватель котла I1: коллектор 0,5 м³ над барабаном, связь от барабана ∅ 113 мм. */
    private static final double SH_VOLUME = 0.5, SH_LINK_AREA = 0.01;

    private final WaterProperties water = WaterProperties.tables();
    private final FluidNetwork net;
    private final FireTubeBoilerSpec boilerSpec = FireTubeBoilerSpec.defaults();
    private final double throttleArea, hAir;
    private final NavigableMap<SimId, BoilerUnit> boilers = new TreeMap<>();
    private final NavigableMap<SimId, EngineUnit> engines = new TreeMap<>();
    private final Map<Long, BoilerUnit> reliefLinks = new HashMap<>();
    private final NavigableMap<SimId, WaterTubeUnit> waterTubes = new TreeMap<>();
    private final Map<Long, WaterTubeUnit> wtReliefLinks = new HashMap<>();
    private final Map<SimId, BmsResponse> bmsResponses = new HashMap<>();
    private final Map<Long, Double> savedFlows = new HashMap<>();
    private final WaterTubeUnit.WaterPackageView waterView = new WaterTubeUnit.WaterPackageView() {
        @Override
        public double liquidEnthalpy(double pressurePa, double tempK) {
            return water.enthalpyPT(pressurePa, tempK);
        }

        @Override
        public double vaporEnthalpy(double pressurePa) {
            return water.saturationP(pressurePa).hG();
        }
    };
    private BoilerUnit[] boilerArr = new BoilerUnit[0];
    private WaterTubeUnit[] wtArr = new WaterTubeUnit[0];
    private EngineUnit[] engineArr = new EngineUnit[0];
    private BoilerUnit[] engineBoiler = new BoilerUnit[0];
    private WaterTubeUnit[] engineWt = new WaterTubeUnit[0];
    private SteamPlantConfig config;
    private boolean dirty;
    private long nextDamageRequest = 1;

    public SteamPlant(SteamPlantConfig config) {
        this.config = config;
        this.net = FluidNetwork.create(WaterPackage.of(CriticalFlowTable.Model.HEM), HydraulicsConfig.defaults());
        this.throttleArea = new SteamEngineUnit(SteamEngineUnit.Spec.reference(1, SteamEngineUnit.FLYWHEEL_INERTIA),
                FlyballGovernor.bare()).throttleAreaM2(water.saturationP(1.1e6).rhoG());
        this.hAir = water.enthalpyPT(ATMOSPHERE_PA, 300);
        net.setEventSink(new HydraulicEventSink() {
            @Override
            public void reliefLift(long linkId, boolean open) {
                BoilerUnit u = reliefLinks.get(linkId);
                if (u != null) {
                    u.reliefOpen = open;
                    if (open) u.reliefLifts++;
                }
                WaterTubeUnit w = wtReliefLinks.get(linkId);
                if (w != null) {
                    w.reliefOpen = open;
                    if (open) w.reliefLifts++;
                }
            }
        });
    }

    @Override
    public String id() {
        return ID;
    }

    public SteamPlantConfig config() {
        return config;
    }

    // --- структура (биндинг, серверный поток, между тиками) ---

    /**
     * Котёл устройства {@code id}; новый ставится пустым и холодным: в обечайке насыщенный пар при температуре
     * воздуха (водяной пакет не знает воздуха, поэтому холодный котёл под разрежением). Повторный вызов возвращает
     * существующий (загруженный из сохранения).
     */
    public BoilerUnit addBoiler(SimId id, double ambientK) {
        BoilerUnit u = boilers.get(id);
        if (u != null) return u;
        u = new BoilerUnit(id, boilerSpec, PlantFuels.COAL, PlantFuels.get(PlantFuels.COAL), ambientK);
        u.in.ambientK = ambientK;
        u.initPressure = water.saturationPressure(ambientK);
        u.initEnthalpy = water.saturationP(u.initPressure).hG();
        boilers.put(id, u);
        dirty = true;
        return u;
    }

    /**
     * Водотрубный котёл I1 устройства {@code id} под конструкцию мира. Новый — холодный и пустой, как котёл I0.
     * Существующий с другой конструкцией перестраивается только остановленным (BMS {@code OFF}): иначе вызов
     * ничего не меняет и биндинг повторит его позже.
     *
     * @param kind         тип горелок (по топливу, {@link WaterTubeUnit#burnerKind})
     * @param volumeM3     объём топки, м³
     * @param wallAreaM2   площадь экранов, м²
     * @param superheaters блоков пароперегревателя; так же экономайзера и воздухоподогревателя
     * @param fuelKey      топливо ({@link WaterTubeUnit#fuelSpec})
     */
    public WaterTubeUnit addWaterTubeBoiler(SimId id, BurnerKind kind, int burners, double volumeM3, double wallAreaM2,
            int superheaters, int economizers, int airPreheaters, String fuelKey, double ambientK) {
        WaterTubeUnit u = waterTubes.get(id);
        if (u == null) {
            u = new WaterTubeUnit(id, kind, burners, volumeM3, wallAreaM2, superheaters, economizers, airPreheaters,
                    fuelKey, ambientK);
            u.in.ambientK = ambientK;
            u.initPressure = water.saturationPressure(ambientK);
            u.initEnthalpy = water.saturationP(u.initPressure).hG();
            waterTubes.put(id, u);
            dirty = true;
            return u;
        }
        var sp = u.spec();
        boolean same = sp.burnerKind() == kind && sp.burners() == burners && sp.furnaceVolumeM3() == volumeM3
                && sp.furnaceWallAreaM2() == wallAreaM2 && u.superheaterBlocks == superheaters
                && u.economizerBlocks == economizers && u.airPreheaterBlocks == airPreheaters && u.fuelKey.equals(fuelKey);
        if (same || u.bms() != BmsState.OFF) return u;
        boolean resize = WaterTubeUnit.drumArea(volumeM3) != u.drumArea();
        u.configure(kind, burners, volumeM3, wallAreaM2, superheaters, economizers, airPreheaters, fuelKey, ambientK);
        if (resize) dirty = true;
        return u;
    }

    public WaterTubeUnit waterTube(SimId id) {
        return waterTubes.get(id);
    }

    public int waterTubeCount() {
        return waterTubes.size();
    }

    /** Ответ BMS на последнюю команду котлу I1; null — команд не было. */
    public BmsResponse lastBmsResponse(SimId boiler) {
        return bmsResponses.get(boiler);
    }

    public EngineUnit addEngine(SimId id) {
        EngineUnit e = engines.get(id);
        if (e != null) return e;
        e = new EngineUnit(id);
        engines.put(id, e);
        dirty = true;
        return e;
    }

    /** Подключить машину к котлу ({@link SimId#NONE} — отключить). */
    public void connect(SimId engine, SimId boiler) {
        EngineUnit e = engines.get(engine);
        if (e == null) throw new IllegalArgumentException("no engine " + engine.hex());
        SimId b = boilers.containsKey(boiler) || waterTubes.containsKey(boiler) ? boiler : SimId.NONE;
        if (!b.equals(e.boiler)) {
            e.boiler = b;
            dirty = true;
        }
    }

    public BoilerUnit boiler(SimId id) {
        return boilers.get(id);
    }

    public EngineUnit engine(SimId id) {
        return engines.get(id);
    }

    public int boilerCount() {
        return boilers.size();
    }

    public int engineCount() {
        return engines.size();
    }

    @Override
    public void onDeviceRemoved(SimId device) {
        if (boilers.remove(device) != null || waterTubes.remove(device) != null) {
            for (EngineUnit e : engines.values()) if (e.boiler.equals(device)) e.boiler = SimId.NONE;
            bmsResponses.remove(device);
            dirty = true;
        } else if (engines.remove(device) != null) {
            dirty = true;
        }
    }

    // --- тик ---

    @Override
    public void stepFast(FastContext ctx) {
        for (BoilerUnit u : boilerArr) if (Double.compare(u.reliefLiftPa(), u.builtReliefPa) != 0) dirty = true;
        if (dirty) rebuild();
        double dt = ctx.dt();
        if (ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE)) {
            double slowDt = ctx.clock().slowDtPhys();
            for (BoilerUnit u : boilerArr) u.boiler.stepSlow(slowDt, u.feedHardness);
            for (WaterTubeUnit u : wtArr) u.boiler.stepSlow(slowDt, u.feedHardness, u.feedO2, u.feedChloride);
        }
        boolean sample = ctx.tick() % SAMPLE_TICKS == 0;
        for (BoilerUnit u : boilerArr) stepBoiler(ctx, u, dt);
        for (WaterTubeUnit u : wtArr) u.step(dt, waterView);
        for (int i = 0; i < engineArr.length; i++) {
            EngineUnit e = engineArr[i];
            stepEngine(e, engineBoiler[i], engineWt[i], dt);
            double pu = e.unit.omega() / e.unit.nominalOmega();
            if (!e.running && pu >= 0.2) {
                e.running = true;
                ctx.events().milestone(ctx.tick(), e.id, ENGINE_RUNNING, "omega_pu", pu);
            } else if (e.running && pu < 0.1) {
                e.running = false;
            }
            if (sample) ctx.events().milestone(ctx.tick(), e.id, ENGINE_STATE, "omega_pu", pu);
        }
        if (boilerArr.length > 0 || wtArr.length > 0) net.stepFast(dt);
        for (BoilerUnit u : boilerArr) {
            if (u.boiler.rupture() != BoilerRupture.NONE && !u.exploded) explode(ctx, u);
            if (sample && !u.exploded && u.drum != null)
                ctx.events().milestone(ctx.tick(), u.id, BOILER_STATE, "drum_pressure_mpa", u.drum.pressure() / 1e6);
        }
        for (WaterTubeUnit u : wtArr) {
            for (WaterTubeUnit.Note n : u.notes)
                ctx.events().journal(ctx.tick(), u.id, n.critical() ? Severity.CRITICAL : Severity.INFO, n.text());
            u.notes.clear();
            if (u.boiler.rupture() != BoilerRupture.NONE && !u.exploded) furnaceExplosion(ctx, u);
            if (sample && !u.exploded)
                ctx.events().milestone(ctx.tick(), u.id, BOILER_STATE, "drum_pressure_mpa", u.drum.pressure() / 1e6);
        }
    }

    /** Взрыв топки котла I1: {@code DamageRequest(BLAST)} с энергией облака E = m·Q_н·f_blast. */
    private void furnaceExplosion(FastContext ctx, WaterTubeUnit u) {
        u.exploded = true;
        double energy = u.boiler.explosionEnergyJ();
        DamageBudget budget = ctx.service(DamageBudget.class);
        if (budget == null || !(energy > 0) || !ctx.topology().exists(u.id)) return;
        GridPos origin = ctx.topology().devicePos(u.id);
        double radius = DAMAGE_SCALED_DISTANCE * Math.cbrt(BlastScaling.tntEquivalentKg(energy));
        long req = nextDamageRequest++;
        DamageGrant g = budget.submit(new DamageRequest(req, u.id, null, DamageKind.BLAST, origin, energy, radius, 0));
        ctx.events().damageRequested(ctx.tick(), u.id, req, g.blocksGranted(), g.blocksDenied());
    }

    private void stepBoiler(FastContext ctx, BoilerUnit u, double dt) {
        FluidNode drum = u.drum;
        BoilerInputs in = u.in;
        boolean plugWasMelted = u.boiler.plugMelted();
        in.waterTempK = drum.temperature();
        in.waterPressurePa = drum.pressure();
        in.waterLevelM = drum.mixtureLevel();
        in.fuelFeedKgS = 0;
        u.boiler.stepFast(dt, in, u.out);
        drum.addHeat(u.out.heatToWaterW);
        u.ashKg += u.out.fuelBurnKgS * dt * u.fuel.ash();
        boolean vent = u.boiler.plugMelted() || u.boiler.rupture() != BoilerRupture.NONE;
        u.plug.setValvePosition(vent ? 1 : 0);
        if (!plugWasMelted && u.boiler.plugMelted()) {
            ctx.events().journal(ctx.tick(), u.id, Severity.WARNING, "fusible plug melted: low water, furnace quenched");
        }
        if (u.exploded) return;
        if (u.draining) {
            // дренаж открыт: питание отключено, вода уходит, пока котёл без давления
            if (drum.pressure() < OPEN_PRESSURE_PA && drum.mass() > 0) {
                double kg = Math.min(DRAIN_KG_S, drum.mass() / dt * 0.5);
                drum.addSource(-kg, water.saturationP(Math.max(drum.pressure(), 1000)).hF());
            }
            return;
        }

        // питание: поплавковый регулятор на нормальный уровень (при растопке — на низший) с упреждением по расходу пара
        double steam = Math.max(0, u.relief.massFlow()) + Math.max(0, u.plug.massFlow());
        for (EngineUnit e : u.engines) steam += Math.max(0, e.throttle.massFlow());
        double p = drum.pressure();
        double gain = FEED_RHO * boilerSpec.waterNodeAreaM2() / FEED_TIME_S;
        double demand = steam + gain * (feedLevelM(p) - drum.collapsedLevel());
        double cap = p < OPEN_PRESSURE_PA ? config.feedOpenKgS() : config.feedPressurizedKgS();
        double rate = Math.max(0, Math.min(Math.min(demand, cap), u.feedSupplyKg / dt));
        if (rate > 0) {
            double hFeed = water.enthalpyPT(Math.max(p, ATMOSPHERE_PA) + 0.2e6, u.feedTempK);
            drum.addSource(rate, hFeed);
            u.feedSupplyKg = Math.max(0, u.feedSupplyKg - rate * dt);
            u.feedUsedKg += rate * dt;
        }
    }

    /** Уставка регулятора питания, м: низший уровень, пока котёл не поднял давление до половины рабочего. */
    double feedLevelM(double pressurePa) {
        return pressurePa < 0.5 * boilerSpec.workingPressurePa()
                ? boilerSpec.crownHeightM() + LOW_LEVEL_ABOVE_CROWN_M : boilerSpec.normalLevelM();
    }

    private void stepEngine(EngineUnit e, BoilerUnit b, WaterTubeUnit w, double dt) {
        SteamEngineUnit m = e.unit;
        double tau = e.loadPowerW / Math.max(m.omega(), 0.2 * m.nominalOmega());
        if (e.chest == null) {
            m.step(dt, EXHAUST_PA, 0, EXHAUST_PA, tau);
            e.capacityW = 0;
            return;
        }
        FluidNode chest = e.chest;
        m.step(dt, chest.pressure(), chest.density(), EXHAUST_PA, tau);
        if (e.seized) m.shaft().setOmega(0);
        chest.addSource(-m.steamFlowKgS(), 0);
        boolean dead = b != null ? b.exploded : w.exploded;
        double supply = b != null ? b.drum.pressure() : w.superheater.pressure();
        e.throttle.setValvePosition(e.stopValveOpen && !dead ? m.throttle() : 0);
        SteamEngineUnit.Spec s = m.spec();
        double pi = m.engine().indicatedPowerW(supply, EXHAUST_PA, m.cutoff(), s.nominalRevPerS());
        e.capacityW = e.stopValveOpen && !dead ? s.mechanicalEfficiency() * Math.max(0, pi) : 0;
    }

    private void explode(FastContext ctx, BoilerUnit u) {
        u.exploded = true;
        FluidNode drum = u.drum;
        double energy = BoilerExplosion.energyJ(water, drum.pressure(), drum.mass(), drum.quality())
                * config.explosionBlastFraction();
        ctx.events().journal(ctx.tick(), u.id, Severity.CRITICAL, u.boiler.rupture() == BoilerRupture.CROWN_COLLAPSE
                ? "boiler explosion: furnace crown collapsed after low water" : "boiler explosion: shell overpressure");
        DamageBudget budget = ctx.service(DamageBudget.class);
        if (budget == null || !(energy > 0) || !ctx.topology().exists(u.id)) return;
        GridPos origin = ctx.topology().devicePos(u.id);
        double radius = DAMAGE_SCALED_DISTANCE * Math.cbrt(BlastScaling.tntEquivalentKg(energy));
        long req = nextDamageRequest++;
        DamageGrant g = budget.submit(new DamageRequest(req, u.id, null, DamageKind.BLAST, origin, energy, radius, 0));
        ctx.events().damageRequested(ctx.tick(), u.id, req, g.blocksGranted(), g.blocksDenied());
    }

    // --- команды ---

    @Override
    public CommandStatus apply(SimCommand command, CommandSource source) {
        return switch (command) {
            case SimCommand.ChargeFuel c -> chargeFuel(c);
            case SimCommand.Operate o -> operate(o);
            default -> CommandStatus.NOT_HANDLED;
        };
    }

    private CommandStatus chargeFuel(SimCommand.ChargeFuel c) {
        BoilerUnit u = boilers.get(c.target());
        if (u == null) return CommandStatus.NOT_HANDLED;
        FuelSpec fuel = PlantFuels.get(c.fuel());
        if (fuel == null || !(c.kg() > 0) || u.boiler.rupture() != BoilerRupture.NONE) return CommandStatus.REJECTED;
        if (!c.fuel().equals(u.fuelKey)) {
            u.fuelKey = c.fuel();
            u.fuel = fuel;
            u.boiler.setFuel(fuel);
        }
        u.boiler.chargeFuel(c.kg());
        return CommandStatus.OK;
    }

    private CommandStatus operate(SimCommand.Operate o) {
        WaterTubeUnit w = waterTubes.get(o.target());
        if (w != null) return operateWaterTube(w, o.action());
        BoilerUnit u = boilers.get(o.target());
        if (u != null) {
            return switch (o.action()) {
                case IGNITE -> u.boiler.ignite() ? CommandStatus.OK : CommandStatus.REJECTED;
                case DRAIN -> {
                    u.draining = !u.draining;
                    yield CommandStatus.OK;
                }
                case FIT_FUSIBLE_PLUG -> {
                    boolean needed = u.boiler.plugMelted() || !u.boiler.plugFitted();
                    boolean open = u.drum == null || u.drum.pressure() < OPEN_PRESSURE_PA;
                    if (!needed || !open || u.boiler.rupture() != BoilerRupture.NONE) yield CommandStatus.REJECTED;
                    u.boiler.replacePlug();
                    yield CommandStatus.OK;
                }
                default -> CommandStatus.REJECTED;
            };
        }
        EngineUnit e = engines.get(o.target());
        if (e == null) return CommandStatus.NOT_HANDLED;
        return switch (o.action()) {
            case FIT_GOVERNOR_DASHPOT -> {
                if (e.dashpot) yield CommandStatus.REJECTED;
                e.dashpot = true;
                e.unit.governor().setDamping(FlyballGovernor.ZETA_DASHPOT);
                yield CommandStatus.OK;
            }
            case TOGGLE_STOP_VALVE -> {
                e.stopValveOpen = !e.stopValveOpen;
                double theta = e.unit.crankAngle();
                if (e.stopValveOpen && Math.abs(e.unit.omega()) <= BAR_OVER_MAX_OMEGA && Math.abs(Math.sin(theta)) < 0.5)
                    e.unit.setCrankAngle((theta + Math.PI / 2) % (2 * Math.PI));
                yield CommandStatus.OK;
            }
            case BAR_OVER -> {
                if (Math.abs(e.unit.omega()) > BAR_OVER_MAX_OMEGA) yield CommandStatus.REJECTED;
                e.unit.setCrankAngle((e.unit.crankAngle() + Math.PI / 3) % (2 * Math.PI));
                yield CommandStatus.OK;
            }
            default -> CommandStatus.REJECTED;
        };
    }

    private CommandStatus operateWaterTube(WaterTubeUnit w, String action) {
        BurnerManagementSystem bms = w.boiler.bmsControl();
        BmsResponse r;
        switch (action) {
            case BMS_PURGE -> r = bms.requestPurge();
            case BMS_IGNITER -> {
                // запальник первой горелки без факела; в RUNNING — следующей
                r = BmsResponse.INTERLOCK_STATE;
                for (int b = 0; b < bms.burners(); b++) {
                    if (bms.burnerPhase(b) != BurnerManagementSystem.BurnerPhase.OFF) continue;
                    r = bms.requestIgniter(b);
                    break;
                }
            }
            case BMS_MAIN_FUEL -> {
                // основное топливо на все горелки с запальником
                r = null;
                for (int b = 0; b < bms.burners(); b++) {
                    if (bms.burnerPhase(b) != BurnerManagementSystem.BurnerPhase.IGNITER_ON) continue;
                    BmsResponse x = bms.requestMainFuel(b);
                    if (r == null || x.accepted()) r = x;
                }
                if (r == null) r = bms.requestMainFuel(0);
            }
            case BMS_SHUTDOWN -> {
                bms.requestShutdown();
                r = BmsResponse.ACCEPTED;
            }
            case BMS_TOGGLE_BYPASS -> {
                boolean on = !bms.flameDetectorBypass(0);
                for (int b = 0; b < bms.burners(); b++) bms.setFlameDetectorBypass(b, on);
                r = BmsResponse.ACCEPTED;
            }
            default -> {
                return CommandStatus.REJECTED;
            }
        }
        bmsResponses.put(w.id, r);
        return r.accepted() ? CommandStatus.OK : CommandStatus.REJECTED;
    }

    @Override
    public void onConfigReload(SimConfig cfg) {
        config = cfg.section(SteamPlantConfig.class, config);
    }

    @Override
    public void resetToSafeState() {
        for (BoilerUnit u : boilers.values()) u.boiler.extinguish();
        for (WaterTubeUnit u : waterTubes.values()) u.boiler.bmsControl().requestShutdown();
    }

    // --- сеть ---

    private static long nodeId(SimId device, int role) {
        return device.serial() << 3 | role;
    }

    private void rebuild() {
        List<FluidNodeSpec> nodes = new ArrayList<>();
        List<FlowLinkSpec> links = new ArrayList<>();
        Map<Long, double[]> init = new HashMap<>();
        double height = boilerSpec.waterNodeHeightM(), area = boilerSpec.waterNodeAreaM2(), top = height - 0.05;
        double plugArea = FireTubeBoiler.FUSIBLE_PLUG_AREA_M2;
        reliefLinks.clear();
        for (BoilerUnit u : boilers.values()) {
            long d = nodeId(u.id, DRUM), a = nodeId(u.id, AIR);
            nodes.add(FluidNodeSpec.separated(d, 0, height, area));
            nodes.add(FluidNodeSpec.boundary(a, 2));
            if (!Double.isNaN(u.initPressure)) init.put(d, new double[] {u.initPressure, u.initEnthalpy});
            init.put(a, new double[] {ATMOSPHERE_PA, hAir});
            long relief = nodeId(u.id, RELIEF);
            u.builtReliefPa = u.reliefLiftPa();
            links.add(link(relief, LinkKind.VALVE_RELIEF, d, a, RELIEF_AREA, 1, top, 2,
                    ValveModel.Spec.relief(u.builtReliefPa, 1)));
            links.add(link(nodeId(u.id, PLUG), LinkKind.VALVE_CONTROL, d, a, plugArea, 1, boilerSpec.crownHeightM(), 2,
                    ValveModel.Spec.control(ValveModel.Characteristic.LINEAR, 1, 0)));
            reliefLinks.put(relief, u);
        }
        wtReliefLinks.clear();
        double drumH = WaterTubeUnit.DRUM_HEIGHT_M;
        for (WaterTubeUnit u : waterTubes.values()) {
            long d = nodeId(u.id, DRUM), a = nodeId(u.id, AIR), sh = nodeId(u.id, SUPERHEATER);
            nodes.add(FluidNodeSpec.separated(d, 0, drumH, u.drumArea()));
            nodes.add(FluidNodeSpec.hem(sh, SH_VOLUME, drumH + 0.5, 1));
            nodes.add(FluidNodeSpec.boundary(a, drumH + 2));
            if (!Double.isNaN(u.initPressure)) init.put(d, new double[] {u.initPressure, u.initEnthalpy});
            if (!Double.isNaN(u.initShPressure)) {
                init.put(sh, new double[] {u.initShPressure, u.initShEnthalpy});
            } else if (!Double.isNaN(u.initPressure)) {
                init.put(sh, new double[] {u.initPressure, water.saturationP(u.initPressure).hG()});
            }
            init.put(a, new double[] {ATMOSPHERE_PA, hAir});
            long relief = nodeId(u.id, RELIEF);
            links.add(link(relief, LinkKind.VALVE_RELIEF, d, a, WaterTubeUnit.RELIEF_AREA_M2, 1, drumH - 0.05, drumH + 2,
                    ValveModel.Spec.relief(u.reliefLiftPa(), 1)));
            links.add(link(nodeId(u.id, SUPERHEAT), LinkKind.VALVE_CONTROL, d, sh, SH_LINK_AREA, 2, drumH - 0.05,
                    drumH + 0.5, ValveModel.Spec.control(ValveModel.Characteristic.LINEAR, 2, 0)));
            wtReliefLinks.put(relief, u);
        }
        List<EngineUnit> linked = new ArrayList<>();
        for (EngineUnit e : engines.values()) {
            BoilerUnit b = boilers.get(e.boiler);
            WaterTubeUnit w = waterTubes.get(e.boiler);
            if (b == null && w == null) continue;
            long c = nodeId(e.id, CHEST);
            nodes.add(FluidNodeSpec.hem(c, CHEST_VOLUME, 2, 0.5));
            if (!Double.isNaN(e.initPressure)) {
                init.put(c, new double[] {e.initPressure, e.initEnthalpy});
            } else {
                double pb = b != null ? (b.drum != null ? b.drum.pressure() : b.initPressure)
                        : w.superheater != null ? w.superheater.pressure() : w.initPressure;
                init.put(c, new double[] {pb, water.saturationP(pb).hG()});
            }
            long src = b != null ? nodeId(b.id, DRUM) : nodeId(w.id, SUPERHEATER);
            links.add(link(nodeId(e.id, THROTTLE), LinkKind.VALVE_CONTROL, src, c, throttleArea, 0, top,
                    2.25, ValveModel.Spec.control(ValveModel.Characteristic.EQUAL_PERCENTAGE,
                            SteamEngineUnit.THROTTLE_ZETA, 0)));
            linked.add(e);
        }
        net.rebuild(nodes, links, new StateCarryOver() {
            @Override
            public double initialPressure(long id) {
                return init.get(id)[0];
            }

            @Override
            public double initialEnthalpy(long id) {
                return init.get(id)[1];
            }

            @Override
            public double initialMassFlow(long id) {
                return savedFlows.getOrDefault(id, 0.0);
            }
        });
        savedFlows.clear();

        boilerArr = boilers.values().toArray(new BoilerUnit[0]);
        Map<SimId, List<EngineUnit>> byBoiler = new HashMap<>();
        for (EngineUnit e : linked) byBoiler.computeIfAbsent(e.boiler, k -> new ArrayList<>()).add(e);
        for (BoilerUnit u : boilerArr) {
            u.drum = net.node(nodeId(u.id, DRUM));
            u.relief = net.link(nodeId(u.id, RELIEF));
            u.plug = net.link(nodeId(u.id, PLUG));
            u.engines = byBoiler.getOrDefault(u.id, List.of()).toArray(new EngineUnit[0]);
            u.initPressure = u.initEnthalpy = Double.NaN;
        }
        wtArr = waterTubes.values().toArray(new WaterTubeUnit[0]);
        for (WaterTubeUnit u : wtArr) {
            u.drum = net.node(nodeId(u.id, DRUM));
            u.superheater = net.node(nodeId(u.id, SUPERHEATER));
            u.relief = net.link(nodeId(u.id, RELIEF));
            u.superheatLink = net.link(nodeId(u.id, SUPERHEAT));
            u.superheatLink.setValvePosition(1);
            u.initPressure = u.initEnthalpy = u.initShPressure = u.initShEnthalpy = Double.NaN;
        }
        engineArr = engines.values().toArray(new EngineUnit[0]);
        engineBoiler = new BoilerUnit[engineArr.length];
        engineWt = new WaterTubeUnit[engineArr.length];
        for (int i = 0; i < engineArr.length; i++) {
            EngineUnit e = engineArr[i];
            BoilerUnit b = boilers.get(e.boiler);
            WaterTubeUnit w = b == null ? waterTubes.get(e.boiler) : null;
            engineBoiler[i] = b;
            engineWt[i] = w;
            boolean on = b != null || w != null;
            e.chest = on ? net.node(nodeId(e.id, CHEST)) : null;
            e.throttle = on ? net.link(nodeId(e.id, THROTTLE)) : null;
            e.initPressure = e.initEnthalpy = Double.NaN;
        }
        dirty = false;
    }

    private static FlowLinkSpec link(long id, LinkKind kind, long from, long to, double area, double zeta,
            double zFrom, double zTo, ValveModel.Spec valve) {
        double inertia = (kind == LinkKind.VALVE_CONTROL && zeta == 0 ? 10 : 2) / area;
        return new FlowLinkSpec(id, kind, from, to, inertia, 0, Math.sqrt(4 * area / Math.PI), area, zeta, 0, zFrom,
                zTo, null, valve);
    }

    // --- сохранение ---

    @Override
    public void save(StateWriter out) {
        out.putLong("next_damage_request", nextDamageRequest);
        out.putInt("boilers", boilers.size());
        int i = 0;
        for (BoilerUnit u : boilers.values()) {
            out.beginObject("b" + i++);
            out.putLong("id", u.id.raw());
            out.putString("fuel", u.fuelKey);
            out.beginObject("furnace");
            u.boiler.save(out);
            out.endObject();
            out.putDouble("ambient_k", u.in.ambientK);
            out.putDouble("ash_kg", u.ashKg);
            out.putDouble("feed_supply_kg", u.feedSupplyKg);
            out.putDouble("feed_used_kg", u.feedUsedKg);
            out.putDouble("feed_temp_k", u.feedTempK);
            out.putDouble("feed_hardness", u.feedHardness);
            out.putLong("relief_lifts", u.reliefLifts);
            out.putBoolean("relief_open", u.reliefOpen);
            out.putBoolean("exploded", u.exploded);
            out.putBoolean("draining", u.draining);
            out.putDouble("p", u.drum != null ? u.drum.pressure() : u.initPressure);
            out.putDouble("h", u.drum != null ? u.drum.enthalpy() : u.initEnthalpy);
            out.putDouble("relief_flow", flow(u.relief, nodeId(u.id, RELIEF)));
            out.putDouble("plug_flow", flow(u.plug, nodeId(u.id, PLUG)));
            out.endObject();
        }
        out.putInt("water_tubes", waterTubes.size());
        i = 0;
        for (WaterTubeUnit u : waterTubes.values()) {
            out.beginObject("w" + i++);
            out.putLong("id", u.id.raw());
            u.save(out);
            out.putDouble("p", u.drum != null ? u.drum.pressure() : u.initPressure);
            out.putDouble("h", u.drum != null ? u.drum.enthalpy() : u.initEnthalpy);
            out.putDouble("sh_p", u.superheater != null ? u.superheater.pressure() : u.initShPressure);
            out.putDouble("sh_h", u.superheater != null ? u.superheater.enthalpy() : u.initShEnthalpy);
            out.putDouble("relief_flow", flow(u.relief, nodeId(u.id, RELIEF)));
            out.putDouble("sh_flow", flow(u.superheatLink, nodeId(u.id, SUPERHEAT)));
            out.endObject();
        }
        out.putInt("engines", engines.size());
        i = 0;
        for (EngineUnit e : engines.values()) {
            out.beginObject("e" + i++);
            out.putLong("id", e.id.raw());
            out.putLong("boiler", e.boiler.raw());
            out.putBoolean("stop_valve_open", e.stopValveOpen);
            out.putBoolean("dashpot", e.dashpot);
            out.putDouble("load_w", e.loadPowerW);
            out.putDouble("capacity_w", e.capacityW);
            out.beginObject("unit");
            e.unit.save(out);
            out.endObject();
            out.putDouble("p", e.chest != null ? e.chest.pressure() : e.initPressure);
            out.putDouble("h", e.chest != null ? e.chest.enthalpy() : e.initEnthalpy);
            out.putDouble("throttle_flow", flow(e.throttle, nodeId(e.id, THROTTLE)));
            out.endObject();
        }
    }

    /** Расход связи; до пересборки после загрузки — сохранённый. */
    private double flow(FlowLink l, long id) {
        return l != null ? l.massFlow() : savedFlows.getOrDefault(id, 0.0);
    }

    @Override
    public void load(StateReader in) {
        boilers.clear();
        engines.clear();
        savedFlows.clear();
        nextDamageRequest = in.getLong("next_damage_request");
        int nb = in.getInt("boilers");
        for (int i = 0; i < nb; i++) {
            in.beginObject("b" + i);
            SimId id = new SimId(in.getLong("id"));
            String key = in.getString("fuel");
            FuelSpec fuel = PlantFuels.get(key);
            if (fuel == null) {
                key = PlantFuels.COAL;
                fuel = PlantFuels.get(key);
            }
            double ambient = in.getDouble("ambient_k");
            BoilerUnit u = new BoilerUnit(id, boilerSpec, key, fuel, ambient);
            in.beginObject("furnace");
            u.boiler.load(in);
            in.endObject();
            u.in.ambientK = ambient;
            u.ashKg = in.getDouble("ash_kg");
            u.feedSupplyKg = in.getDouble("feed_supply_kg");
            u.feedUsedKg = in.getDouble("feed_used_kg");
            u.feedTempK = in.getDouble("feed_temp_k");
            u.feedHardness = in.optDouble("feed_hardness", BoilerUnit.RAW_HARDNESS);
            u.reliefLifts = in.getLong("relief_lifts");
            u.reliefOpen = in.getBoolean("relief_open");
            u.exploded = in.getBoolean("exploded");
            u.draining = in.optInt("draining", 0) != 0;
            u.initPressure = in.getDouble("p");
            u.initEnthalpy = in.getDouble("h");
            savedFlows.put(nodeId(id, RELIEF), in.getDouble("relief_flow"));
            savedFlows.put(nodeId(id, PLUG), in.getDouble("plug_flow"));
            in.endObject();
            boilers.put(id, u);
        }
        waterTubes.clear();
        int nw = in.optInt("water_tubes", 0);
        for (int i = 0; i < nw; i++) {
            in.beginObject("w" + i);
            SimId id = new SimId(in.getLong("id"));
            WaterTubeUnit u = WaterTubeUnit.load(id, in);
            u.initPressure = in.getDouble("p");
            u.initEnthalpy = in.getDouble("h");
            u.initShPressure = in.getDouble("sh_p");
            u.initShEnthalpy = in.getDouble("sh_h");
            if (Double.isNaN(u.initShPressure) && !Double.isNaN(u.initPressure)) {
                u.initShPressure = u.initPressure;
                u.initShEnthalpy = water.saturationP(u.initPressure).hG();
            }
            savedFlows.put(nodeId(id, RELIEF), in.getDouble("relief_flow"));
            savedFlows.put(nodeId(id, SUPERHEAT), in.getDouble("sh_flow"));
            in.endObject();
            waterTubes.put(id, u);
        }
        int ne = in.getInt("engines");
        for (int i = 0; i < ne; i++) {
            in.beginObject("e" + i);
            EngineUnit e = new EngineUnit(new SimId(in.getLong("id")));
            e.boiler = new SimId(in.getLong("boiler"));
            e.stopValveOpen = in.getBoolean("stop_valve_open");
            e.dashpot = in.getBoolean("dashpot");
            e.loadPowerW = in.getDouble("load_w");
            e.capacityW = in.getDouble("capacity_w");
            in.beginObject("unit");
            e.unit.load(in);
            in.endObject();
            e.initPressure = in.getDouble("p");
            e.initEnthalpy = in.getDouble("h");
            savedFlows.put(nodeId(e.id, THROTTLE), in.getDouble("throttle_flow"));
            in.endObject();
            engines.put(e.id, e);
        }
        dirty = true;
    }

    /**
     * Котёл I1 для тестов и отладки: барабан при давлении {@code pressurePa} с уровнем {@code levelM} от низа,
     * пароперегреватель — насыщенным паром того же давления, топка и стены прогреты до насыщения.
     */
    public void presetWaterTube(SimId boiler, double pressurePa, double levelM) {
        WaterTubeUnit u = waterTubes.get(boiler);
        if (u == null) throw new IllegalArgumentException("no water-tube boiler " + boiler.hex());
        var s = water.saturationP(pressurePa);
        double area = u.drumArea(), h = WaterTubeUnit.DRUM_HEIGHT_M;
        double vl = Math.min(levelM, h) * area, vg = h * area - vl;
        double mf = vl / s.vF(), mg = vg / s.vG();
        waterTubes.remove(boiler);
        List<EngineUnit> detached = new ArrayList<>();
        for (EngineUnit e : engines.values()) {
            if (e.boiler.equals(boiler)) {
                detached.add(e);
                e.boiler = SimId.NONE;
            }
        }
        rebuild();
        waterTubes.put(boiler, u);
        for (EngineUnit e : detached) {
            e.boiler = boiler;
            e.initPressure = pressurePa;
            e.initEnthalpy = s.hG();
        }
        u.initPressure = pressurePa;
        u.initEnthalpy = s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
        u.initShPressure = pressurePa;
        u.initShEnthalpy = s.hG();
        rebuild();
    }

    /** Котёл для тестов и отладки: состояние воды задаётся до ближайшей пересборки сети. */
    public void presetWater(SimId boiler, double pressurePa, double levelM) {
        BoilerUnit u = boilers.get(boiler);
        if (u == null) throw new IllegalArgumentException("no boiler " + boiler.hex());
        var s = water.saturationP(pressurePa);
        double vl = Math.min(levelM, boilerSpec.waterNodeHeightM()) * boilerSpec.waterNodeAreaM2();
        double vg = boilerSpec.waterNodeHeightM() * boilerSpec.waterNodeAreaM2() - vl;
        double mf = vl / s.vF(), mg = vg / s.vG();
        u.initPressure = pressurePa;
        u.initEnthalpy = s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
        for (EngineUnit e : engines.values()) {
            if (e.boiler.equals(boiler)) {
                e.initPressure = pressurePa;
                e.initEnthalpy = s.hG();
            }
        }
        // узлы с прежним id сохраняют состояние — пересобрать сеть без них, затем с ними
        boolean had = u.drum != null;
        if (had) {
            boilers.remove(boiler);
            List<EngineUnit> detached = new ArrayList<>();
            for (EngineUnit e : engines.values()) {
                if (e.boiler.equals(boiler)) {
                    detached.add(e);
                    e.boiler = SimId.NONE;
                }
            }
            rebuild();
            boilers.put(boiler, u);
            for (EngineUnit e : detached) e.boiler = boiler;
            u.initPressure = pressurePa;
            u.initEnthalpy = s.hF() + mg / (mf + mg) * (s.hG() - s.hF());
            for (EngineUnit e : detached) {
                e.initPressure = pressurePa;
                e.initEnthalpy = s.hG();
            }
        }
        u.boiler.setMetalTemperature(s.tSat());
        rebuild();
    }
}
