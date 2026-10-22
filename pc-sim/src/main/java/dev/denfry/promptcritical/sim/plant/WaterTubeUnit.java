package dev.denfry.promptcritical.sim.plant;

import dev.denfry.promptcritical.sim.combustion.BmsState;
import dev.denfry.promptcritical.sim.combustion.BoilerEvents;
import dev.denfry.promptcritical.sim.combustion.BoilerInputs;
import dev.denfry.promptcritical.sim.combustion.BoilerOutputs;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.BurnerKind;
import dev.denfry.promptcritical.sim.combustion.FlammabilityLimits;
import dev.denfry.promptcritical.sim.combustion.FuelSpec;
import dev.denfry.promptcritical.sim.combustion.LarsonMiller;
import dev.denfry.promptcritical.sim.combustion.MftCause;
import dev.denfry.promptcritical.sim.combustion.WaterTubeBoiler;
import dev.denfry.promptcritical.sim.combustion.WaterTubeBoilerSpec;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.hydraulics.FlowLink;
import dev.denfry.promptcritical.sim.hydraulics.FluidNode;
import dev.denfry.promptcritical.sim.metallurgy.ProcessGas;
import java.util.ArrayList;
import java.util.List;

/**
 * Водотрубный барабанный котёл I1 класса ДКВР (07 §7.9.5, решение 11б): газовая сторона {@link WaterTubeBoiler} с BMS,
 * барабан — узел {@code SEPARATED} гидросети {@link SteamPlant} с ПК, пароперегреватель — узел {@code HEM} за
 * связью от барабана, из него берут пар машины. Питание — встроенным питательным насосом через экономайзер.
 *
 * <p>Регуляторы щита (решение 11б, вместо ПЛК игрока): питание — по уровню с упреждением по расходу пара; горение —
 * давление пара → нагрузка топки (ПИ) с перекрёстным ограничением: воздух не меньше топлива, топливо не больше воздуха
 * (при росте нагрузки сначала воздух, при снижении — сначала топливо); разрежение — дымососом на −30 Па. Каждый — АВТ
 * или РУЧ (игрок задаёт открытие сам, перекрёстного ограничения нет).
 *
 * <p>ПАЗ (07 §7.18) — {@link Protection}: в топливных состояниях BMS причина даёт MFT.
 */
public final class WaterTubeUnit {
    /** Рабочее давление ДКВР, Па, и уставка регулятора давления по умолчанию. */
    public static final double WORKING_PRESSURE_PA = 1.4e6, PRESSURE_SET_PA = 1.3e6;
    /** Барабан: высота узла, м; нормальный уровень; ПАЗ ±200 мм; срыв циркуляции экранов. */
    public static final double DRUM_HEIGHT_M = 1.0, NORMAL_LEVEL_M = 0.5, LEVEL_TRIP_M = 0.2, STARVE_LEVEL_M = 0.25;
    /** ПК на барабане: подрыв 1,07 p_раб (выше уставки ПАЗ 1,05), сечение ∅ 56 мм. */
    public static final double RELIEF_FACTOR = 1.07, RELIEF_AREA_M2 = 25e-4;
    /** Удельная нагрузка топочного объёма, Вт/м³ (газ 150–300 кВт/м³), и предел одной горелки, Вт [BALANCE]. */
    public static final double VOLUME_HEAT_RELEASE_W_M3 = 200e3, BURNER_MAX_W = 4e6;
    /**
     * UA одного блока пароперегревателя, экономайзера и воздухоподогревателя при номинальном расходе газов, Вт/К: три
     * блока пароперегревателя дают ДКВР-перегрев ≈ 250 °C, экономайзер и ТВП — уходящие газы ≈ 150 °C [BALANCE].
     */
    public static final double SUPERHEATER_UA_PER_BLOCK = 250, ECONOMIZER_UA_PER_BLOCK = 1_000, AIR_PREHEATER_UA_PER_BLOCK = 1_500;
    /** Продувка и розжиг: воздух при продувке и минимальный в топливных состояниях; топливо пробы основного факела. */
    public static final double PURGE_AIR = 0.4, MIN_AIR = 0.3, LIGHT_OFF_FUEL = 0.25;
    /** Нижний предел нагрузки регулятора давления (диапазон горелок 4:1): дальше давление ограничивает ПК. */
    public static final double MIN_FIRING = 0.25;
    /** Разрежение в топке, Па, которое держит регулятор дымососа. */
    public static final double FURNACE_SET_PA = -30;
    /** ПАЗ: давление в топке, Па; CO в газоходе, мольная доля, и выдержка, с. */
    public static final double FURNACE_HIGH_PA = 1_500, FURNACE_LOW_PA = -2_500, FLUE_CO_TRIP = 0.01, FLUE_CO_DELAY_S = 10;
    /** Расход воды из разорванной трубы экрана, кг/с. */
    public static final double TUBE_LEAK_KG_S = 2;
    /** Пар деаэратору — только при давлении в барабане выше, Па. */
    public static final double DEAERATOR_MIN_PA = 0.3e6;
    /** Регулятор давления: коэффициент на долю отклонения и постоянная интегрирования, с. */
    static final double PRESSURE_KP = 4, PRESSURE_TI_S = 60;
    /** Регулятор уровня: постоянная, с; дымосос: постоянная подстройки, с. */
    static final double LEVEL_TIME_S = 20, DRAFT_TIME_S = 2;
    /** Скорость хода регулирующих органов по горению, доля/с (воздух и топливо за 20 с от 0 до 100 %). */
    static final double ACTUATOR_RATE = 0.05;
    private static final double RHO_WATER = 870;

    /** Причина ПАЗ котла (07 §7.18, {@code SisCause}). */
    public enum Protection {
        DRUM_LEVEL_LOW_LOW, DRUM_LEVEL_HIGH_HIGH, STEAM_PRESSURE_HIGH, FURNACE_PRESSURE_HIGH, FURNACE_PRESSURE_LOW,
        FUEL_PRESSURE_LOW, FLUE_CO_HIGH, TUBE_LEAK
    }

    /** Запись журнала котла: события BMS для {@code ctx.events().journal}. */
    record Note(boolean critical, String text) {
    }

    final SimId id;
    final List<Note> notes = new ArrayList<>();
    final BoilerInputs in = new BoilerInputs();
    final BoilerOutputs out = new BoilerOutputs();
    WaterTubeBoiler boiler;
    String fuelKey;
    FuelSpec fuel;
    double furnaceVolumeM3;
    int superheaterBlocks, economizerBlocks, airPreheaterBlocks;
    FluidNode drum, superheater;
    FlowLink relief, superheatLink;
    /** Вход биндинга: запасы топлива и воды, кг; температура и качество питательной воды. */
    double fuelSupplyKg, fuelUsedKg, feedSupplyKg, feedUsedKg, feedTempK = 288.15;
    double feedHardness = BoilerUnit.RAW_HARDNESS, feedO2 = 9e-6, feedChloride = 30e-6;
    /** Вход биндинга: производительность вентиляторов по оборотам вала, кг/с. */
    double fdCapacityKgS, idCapacityKgS;
    /** Пар деаэратору: заявка, кг/с, и отданный с прошлого вызова, кг. */
    double deaeratorDemandKgS, deaeratorTakenKg;
    /** Регуляторы. */
    boolean combustionAuto = true, feedAuto = true;
    double pressureSetPa = PRESSURE_SET_PA, manualFuel, manualAir, manualDraft, manualFeed;
    double firingDemand, pressureIntegral, fuelPos, airPos, draftPos, feedRateKgS;
    double coTimer;
    Protection lastTrip;
    boolean exploded;
    long reliefLifts;
    boolean reliefOpen;
    double initPressure = Double.NaN, initEnthalpy = Double.NaN, initShPressure = Double.NaN, initShEnthalpy = Double.NaN;

    WaterTubeUnit(SimId id, BurnerKind kind, int burners, double furnaceVolumeM3, double wallAreaM2, int superheaters,
            int economizers, int airPreheaters, String fuelKey, double initialTempK) {
        this.id = id;
        configure(kind, burners, furnaceVolumeM3, wallAreaM2, superheaters, economizers, airPreheaters, fuelKey, initialTempK);
    }

    /**
     * Перестроить котёл под конструкцию мира. Износ экранов и прогрев переносятся; BMS начинается с {@code OFF} — смена
     * конструкции только при остановленном котле (биндинг откладывает её до {@code OFF}).
     */
    void configure(BurnerKind kind, int burners, double furnaceVolumeM3, double wallAreaM2, int superheaters,
            int economizers, int airPreheaters, String fuelKey, double initialTempK) {
        FuelSpec f = fuelSpec(fuelKey);
        if (f == null) throw new IllegalArgumentException("fuel " + fuelKey);
        double heat = Math.min(VOLUME_HEAT_RELEASE_W_M3 * furnaceVolumeM3, BURNER_MAX_W * burners);
        double ratedFuel = heat / f.lhv();
        double alpha = kind == BurnerKind.PULVERIZED_COAL ? 1.25 : 1.1;
        boolean coal = kind == BurnerKind.PULVERIZED_COAL;
        WaterTubeBoilerSpec spec = new WaterTubeBoilerSpec(kind, burners, ratedFuel, alpha * ratedFuel * f.stoichAirKgPerKg(),
                furnaceVolumeM3, wallAreaM2, coal ? 0.6 : 0.35, coal ? 0.45 : 0.65,
                SUPERHEATER_UA_PER_BLOCK * superheaters, ECONOMIZER_UA_PER_BLOCK * economizers,
                AIR_PREHEATER_UA_PER_BLOCK * airPreheaters, 0.051, 0.0025, LarsonMiller.CARBON, STARVE_LEVEL_M,
                2 * wallAreaM2);
        WaterTubeBoiler b = new WaterTubeBoiler(spec, f, limits(fuelKey), events(), initialTempK);
        if (boiler != null) b.inheritFrom(boiler);
        boiler = b;
        this.fuelKey = fuelKey;
        this.fuel = f;
        this.furnaceVolumeM3 = furnaceVolumeM3;
        this.superheaterBlocks = superheaters;
        this.economizerBlocks = economizers;
        this.airPreheaterBlocks = airPreheaters;
    }

    /** Топливо котла I1: горючий газ завода ({@link ProcessGas}) или угольная пыль {@link PlantFuels#COAL_DUST}. */
    public static FuelSpec fuelSpec(String key) {
        ProcessGas g = ProcessGas.byFluid(key);
        if (g != null) return g.fuel;
        return PlantFuels.COAL_DUST.equals(key) ? PlantFuels.get(PlantFuels.COAL_DUST) : null;
    }

    /** Тип горелки под топливо: газ, низкокалорийный газ, пылеугольная; null — не топливо котла I1. */
    public static BurnerKind burnerKind(String key) {
        ProcessGas g = ProcessGas.byFluid(key);
        if (g != null) return g == ProcessGas.BLAST_FURNACE || g == ProcessGas.CONVERTER ? BurnerKind.LOW_BTU_GAS : BurnerKind.GAS;
        return PlantFuels.COAL_DUST.equals(key) ? BurnerKind.PULVERIZED_COAL : null;
    }

    static FlammabilityLimits limits(String key) {
        ProcessGas g = ProcessGas.byFluid(key);
        return g != null ? FlammabilityLimits.ofGas(g.composition()) : FlammabilityLimits.COAL_DUST;
    }

    private BoilerEvents events() {
        return new BoilerEvents() {
            @Override
            public void bmsStateChanged(BmsState from, BmsState to) {
                notes.add(new Note(false, "BMS " + from + " -> " + to));
            }

            @Override
            public void masterFuelTrip(MftCause cause) {
                notes.add(new Note(true, "master fuel trip: " + cause + (cause == MftCause.PROTECTION && lastTrip != null
                        ? " " + lastTrip : "")));
            }

            @Override
            public void burnerTripped(int burner) {
                notes.add(new Note(false, "burner " + (burner + 1) + " tripped"));
            }

            @Override
            public void tubeLeak() {
                notes.add(new Note(true, "screen tube rupture"));
            }

            @Override
            public void furnaceExplosion(double energyJ) {
                notes.add(new Note(true, "furnace explosion"));
            }
        };
    }

    public SimId id() {
        return id;
    }

    public WaterTubeBoiler boiler() {
        return boiler;
    }

    public WaterTubeBoilerSpec spec() {
        return boiler.spec();
    }

    public String fuelKey() {
        return fuelKey;
    }

    // --- вход биндинга ---

    public void setAmbient(double kelvin) {
        in.ambientK = kelvin;
    }

    /** Топливо в коллекторе и бункере блока, кг (газ — нм³ × плотность). */
    public void setFuelSupply(double kg) {
        fuelSupplyKg = Math.max(0, kg);
    }

    /** Сожжённое топливо с прошлого вызова, кг; счётчик обнуляется. */
    public double takeFuelUsed() {
        double kg = fuelUsedKg;
        fuelUsedKg = 0;
        return kg;
    }

    public void setFeedSupply(double kg, double tempK) {
        feedSupplyKg = Math.max(0, kg);
        feedTempK = tempK;
    }

    /** Качество питательной воды: жёсткость, моль-экв/м³, O₂ и хлориды, кг/кг (по сорту воды в баке). */
    public void setFeedQuality(double hardnessMolEqM3, double o2KgKg, double chlorideKgKg) {
        feedHardness = Math.max(0, hardnessMolEqM3);
        feedO2 = Math.max(0, o2KgKg);
        feedChloride = Math.max(0, chlorideKgKg);
    }

    public double takeFeedUsed() {
        double kg = feedUsedKg;
        feedUsedKg = 0;
        return kg;
    }

    /** Производительность дутьевого вентилятора и дымососа при текущих оборотах, кг/с (0 — стоит). */
    public void setFans(double forcedKgS, double inducedKgS) {
        fdCapacityKgS = Math.max(0, forcedKgS);
        idCapacityKgS = Math.max(0, inducedKgS);
    }

    /** Номинальная производительность вентиляторов под котёл: воздух и газы с запасом 10 %, кг/с. */
    public double ratedForcedAirKgS() {
        return 1.1 * spec().ratedAirKgS();
    }

    public double ratedInducedDraftKgS() {
        return 1.1 * (spec().ratedAirKgS() + spec().ratedFuelKgS());
    }

    /** Заявка деаэратора на пар, кг/с. */
    public void setDeaeratorDemand(double kgS) {
        deaeratorDemandKgS = Math.max(0, kgS);
    }

    public double takeDeaeratorSteam() {
        double kg = deaeratorTakenKg;
        deaeratorTakenKg = 0;
        return kg;
    }

    // --- регуляторы ---

    public void setCombustionAuto(boolean auto) {
        if (auto && !combustionAuto) pressureIntegral = firingDemand;
        combustionAuto = auto;
    }

    public boolean combustionAuto() {
        return combustionAuto;
    }

    public void setFeedAuto(boolean auto) {
        feedAuto = auto;
    }

    public boolean feedAuto() {
        return feedAuto;
    }

    public void setPressureSetpoint(double pa) {
        pressureSetPa = Math.max(0.2e6, Math.min(WORKING_PRESSURE_PA, pa));
    }

    public double pressureSetpointPa() {
        return pressureSetPa;
    }

    /** Ручные задания (доли): топливо, воздух (лопатки дутьевого), дымосос, питание (доля производительности насоса). */
    public void setManual(double fuel, double air, double draft, double feed) {
        manualFuel = clamp(fuel);
        manualAir = clamp(air);
        manualDraft = clamp(draft);
        manualFeed = clamp(feed);
    }

    public double manualFuel() {
        return manualFuel;
    }

    public double manualAir() {
        return manualAir;
    }

    public double manualDraft() {
        return manualDraft;
    }

    public double manualFeed() {
        return manualFeed;
    }

    // --- выход ---

    public BmsState bms() {
        return boiler.bms();
    }

    public double drumPressurePa() {
        return drum != null ? drum.pressure() : Double.NaN;
    }

    /** Уровень в барабане от нормального, м (по водоуказательному стеклу). */
    public double drumLevelM() {
        return drum != null ? drum.collapsedLevel() - NORMAL_LEVEL_M : 0;
    }

    public double steamTempK() {
        return superheater != null ? superheater.temperature() : Double.NaN;
    }

    public double steamPressurePa() {
        return superheater != null ? superheater.pressure() : Double.NaN;
    }

    /** Пар через пароперегреватель, кг/с. */
    public double steamFlowKgS() {
        return superheatLink != null ? Math.max(0, superheatLink.massFlow()) : 0;
    }

    public double feedRateKgS() {
        return feedRateKgS;
    }

    public double firingDemand() {
        return firingDemand;
    }

    /** Положение регулирующих органов: топливо, воздух, дымосос, доли. */
    public double fuelPosition() {
        return fuelPos;
    }

    public double airPosition() {
        return airPos;
    }

    public double draftPosition() {
        return draftPos;
    }

    public Protection lastTrip() {
        return lastTrip;
    }

    public boolean exploded() {
        return exploded;
    }

    public boolean reliefOpen() {
        return reliefOpen;
    }

    public long reliefLifts() {
        return reliefLifts;
    }

    public BoilerOutputs outputs() {
        return out;
    }

    public BoilerRupture rupture() {
        return boiler.rupture();
    }

    // --- сохранение (узлы — в SteamPlant) ---

    void save(StateWriter o) {
        WaterTubeBoilerSpec sp = boiler.spec();
        o.putInt("kind", sp.burnerKind().ordinal());
        o.putInt("burners", sp.burners());
        o.putDouble("volume", furnaceVolumeM3);
        o.putDouble("wall_area", sp.furnaceWallAreaM2());
        o.putInt("sh", superheaterBlocks);
        o.putInt("eco", economizerBlocks);
        o.putInt("aph", airPreheaterBlocks);
        o.putString("fuel", fuelKey);
        o.putDouble("ambient_k", in.ambientK);
        o.beginObject("boiler");
        boiler.save(o);
        o.endObject();
        o.putDoubles("supply", new double[] {fuelSupplyKg, fuelUsedKg, feedSupplyKg, feedUsedKg, feedTempK, feedHardness,
                feedO2, feedChloride, fdCapacityKgS, idCapacityKgS, deaeratorDemandKgS, deaeratorTakenKg}, 12);
        o.putBoolean("combustion_auto", combustionAuto);
        o.putBoolean("feed_auto", feedAuto);
        o.putDoubles("control", new double[] {pressureSetPa, manualFuel, manualAir, manualDraft, manualFeed, firingDemand,
                pressureIntegral, fuelPos, airPos, draftPos, feedRateKgS, coTimer}, 12);
        o.putInt("last_trip", lastTrip == null ? -1 : lastTrip.ordinal());
        o.putBoolean("exploded", exploded);
        o.putLong("relief_lifts", reliefLifts);
        o.putBoolean("relief_open", reliefOpen);
    }

    static WaterTubeUnit load(SimId id, StateReader i) {
        BurnerKind kind = BurnerKind.values()[i.getInt("kind")];
        double ambient = i.getDouble("ambient_k");
        WaterTubeUnit u = new WaterTubeUnit(id, kind, i.getInt("burners"), i.getDouble("volume"), i.getDouble("wall_area"),
                i.getInt("sh"), i.getInt("eco"), i.getInt("aph"), i.getString("fuel"), ambient);
        u.in.ambientK = ambient;
        i.beginObject("boiler");
        u.boiler.load(i);
        i.endObject();
        double[] s = i.getDoubles("supply");
        u.fuelSupplyKg = s[0];
        u.fuelUsedKg = s[1];
        u.feedSupplyKg = s[2];
        u.feedUsedKg = s[3];
        u.feedTempK = s[4];
        u.feedHardness = s[5];
        u.feedO2 = s[6];
        u.feedChloride = s[7];
        u.fdCapacityKgS = s[8];
        u.idCapacityKgS = s[9];
        u.deaeratorDemandKgS = s[10];
        u.deaeratorTakenKg = s[11];
        u.combustionAuto = i.getBoolean("combustion_auto");
        u.feedAuto = i.getBoolean("feed_auto");
        double[] c = i.getDoubles("control");
        u.pressureSetPa = c[0];
        u.manualFuel = c[1];
        u.manualAir = c[2];
        u.manualDraft = c[3];
        u.manualFeed = c[4];
        u.firingDemand = c[5];
        u.pressureIntegral = c[6];
        u.fuelPos = c[7];
        u.airPos = c[8];
        u.draftPos = c[9];
        u.feedRateKgS = c[10];
        u.coTimer = c[11];
        int t = i.getInt("last_trip");
        u.lastTrip = t < 0 ? null : Protection.values()[t];
        u.exploded = i.getBoolean("exploded");
        u.reliefLifts = i.getLong("relief_lifts");
        u.reliefOpen = i.getBoolean("relief_open");
        return u;
    }

    // --- шаг (SteamPlant) ---

    /**
     * Регуляторы и газовая сторона за тик: регулирующие органы → входы котла → {@link WaterTubeBoiler#stepFast} →
     * тепло в барабан и пароперегреватель, питание через экономайзер, пар деаэратору, течь экрана, ПАЗ.
     */
    void step(double dt, WaterPackageView water) {
        WaterTubeBoilerSpec spec = boiler.spec();
        BmsState s = boiler.bms();
        boolean fuelState = s == BmsState.IGNITER_TRIAL || s == BmsState.IGNITER_ON || s == BmsState.MAIN_TRIAL
                || s == BmsState.RUNNING;
        double p = drum.pressure();

        // горение
        double fuelTarget, airTarget, draftTarget;
        if (combustionAuto) {
            double e = (pressureSetPa - p) / pressureSetPa;
            if (s == BmsState.RUNNING) {
                pressureIntegral = clamp(pressureIntegral + PRESSURE_KP * e * dt / PRESSURE_TI_S);
                pressureIntegral = Math.max(pressureIntegral, MIN_FIRING - PRESSURE_KP * e);
                firingDemand = Math.max(MIN_FIRING, clamp(PRESSURE_KP * e + pressureIntegral));
            } else {
                pressureIntegral = firingDemand = fuelState ? LIGHT_OFF_FUEL : 0;
            }
            double airNow = fdCapacityKgS > 0 ? airPos * fdCapacityKgS / spec.ratedAirKgS() : 0;
            double fuelNow = fuelPos;
            airTarget = Math.max(firingDemand, fuelNow);
            airTarget = Math.max(airTarget, fuelState ? MIN_AIR : s == BmsState.OFF || s == BmsState.PURGE
                    || s == BmsState.PURGE_COMPLETE || s == BmsState.MFT || s == BmsState.POST_PURGE ? PURGE_AIR : 0);
            fuelTarget = fuelState ? Math.min(firingDemand, airNow) : 0;
            double airKg = airTarget * spec.ratedAirKgS();
            airTarget = fdCapacityKgS > 0 ? clamp(airKg / fdCapacityKgS) : 0;
            draftTarget = draftPos;
        } else {
            fuelTarget = manualFuel;
            airTarget = manualAir;
            draftTarget = manualDraft;
        }
        double step = ACTUATOR_RATE * dt;
        fuelPos = approach(fuelPos, fuelTarget, step);
        airPos = approach(airPos, airTarget, step);
        double air = airPos * fdCapacityKgS;
        double fuelKgS = Math.min(fuelPos * spec.ratedFuelKgS(), fuelSupplyKg / dt);
        if (combustionAuto) {
            // дымосос: отсосать газы топки и держать разрежение
            double flue = air + fuelKgS;
            double need = flue - FURNACE_SET_PA / WaterTubeBoiler.FURNACE_PRESSURE_GAIN * (spec.ratedAirKgS() + spec.ratedFuelKgS());
            double target = idCapacityKgS > 0 ? clamp(need / idCapacityKgS) : 1;
            draftTarget = draftPos + (target - draftPos) * Math.min(1, dt / DRAFT_TIME_S);
        }
        draftPos = clamp(draftTarget);
        in.forcedAirKgS = air;
        in.inducedDraftKgS = draftPos * idCapacityKgS;
        in.fuelFeedKgS = fuelKgS;
        in.waterTempK = drum.temperature();
        in.waterPressurePa = p;
        in.waterLevelM = drum.collapsedLevel();
        in.steamFlowKgS = steamFlowKgS();
        in.steamInletK = drum.temperature();
        in.steamCpJKgK = 2_600;

        // питание через экономайзер
        double steamOut = steamFlowKgS() + Math.max(0, relief.massFlow());
        double cap = 1.5 * ratedSteamKgS();
        double demand = feedAuto
                ? steamOut + RHO_WATER * drumArea() * (NORMAL_LEVEL_M - drum.collapsedLevel()) / LEVEL_TIME_S
                : manualFeed * cap;
        feedRateKgS = Math.max(0, Math.min(Math.min(demand, cap), feedSupplyKg / dt));
        in.feedwaterFlowKgS = feedRateKgS;
        in.feedwaterInletK = feedTempK;
        in.feedwaterCpJKgK = 4_300;

        boiler.stepFast(dt, in, out);
        double admitted = boiler.admittedFuelKgS() * dt;
        fuelSupplyKg = Math.max(0, fuelSupplyKg - admitted);
        fuelUsedKg += admitted;
        if (exploded) return;
        drum.addHeat(out.heatToWaterW);
        superheater.addHeat(out.superheaterHeatW);
        if (feedRateKgS > 0) {
            double hFeed = water.liquidEnthalpy(Math.max(p, SteamPlant.ATMOSPHERE_PA) + 0.3e6, feedTempK)
                    + out.economizerHeatW / feedRateKgS;
            drum.addSource(feedRateKgS, hFeed);
            feedSupplyKg = Math.max(0, feedSupplyKg - feedRateKgS * dt);
            feedUsedKg += feedRateKgS * dt;
        }
        if (deaeratorDemandKgS > 0 && p > DEAERATOR_MIN_PA) {
            extract(deaeratorDemandKgS, water.vaporEnthalpy(p));
            deaeratorTakenKg += deaeratorDemandKgS * dt;
        }
        if (boiler.tubeLeak() && drum.mass() > 0 && p > SteamPlant.ATMOSPHERE_PA) {
            extract(Math.min(TUBE_LEAK_KG_S, drum.mass() / dt * 0.5), water.liquidEnthalpy(p, drum.temperature()));
        }
        protect(dt, fuelState);
    }

    /** Отбор из барабана с энтальпией {@code hOut} (пар сверху, вода снизу): узел отдаёт свою среднюю, разница — теплом. */
    private void extract(double kgS, double hOut) {
        drum.addSource(-kgS, 0);
        drum.addHeat(-(hOut - drum.enthalpy()) * kgS);
    }

    private void protect(double dt, boolean fuelState) {
        coTimer = out.flame && out.combustion.xCO > FLUE_CO_TRIP ? coTimer + dt : 0;
        if (!fuelState) return;
        double level = drumLevelM(), p = drum.pressure(), pf = out.furnacePressurePa;
        Protection trip = null;
        if (boiler.tubeLeak()) trip = Protection.TUBE_LEAK;
        else if (level < -LEVEL_TRIP_M) trip = Protection.DRUM_LEVEL_LOW_LOW;
        else if (level > LEVEL_TRIP_M) trip = Protection.DRUM_LEVEL_HIGH_HIGH;
        else if (p > 1.05 * WORKING_PRESSURE_PA) trip = Protection.STEAM_PRESSURE_HIGH;
        else if (pf > FURNACE_HIGH_PA) trip = Protection.FURNACE_PRESSURE_HIGH;
        else if (pf < FURNACE_LOW_PA) trip = Protection.FURNACE_PRESSURE_LOW;
        else if (fuelPos > 0.05 && fuelSupplyKg <= 0) trip = Protection.FUEL_PRESSURE_LOW;
        else if (coTimer >= FLUE_CO_DELAY_S) trip = Protection.FLUE_CO_HIGH;
        if (trip == null) return;
        lastTrip = trip;
        boiler.bmsControl().trip(MftCause.PROTECTION);
    }

    /** Номинальная паропроизводительность, кг/с: тепло топлива × 0,9 на нагрев воды 100 °C → пар 250 °C при p_раб. */
    public double ratedSteamKgS() {
        return 0.9 * boiler.spec().ratedFuelKgS() * fuel.lhv() / 2.5e6;
    }

    /** Сечение барабана с экранами, м²: водяной объём ≈ 15 % объёма топки. */
    double drumArea() {
        return drumArea(furnaceVolumeM3);
    }

    static double drumArea(double furnaceVolumeM3) {
        return Math.max(4, Math.min(40, 0.15 * furnaceVolumeM3));
    }

    double reliefLiftPa() {
        return RELIEF_FACTOR * WORKING_PRESSURE_PA;
    }

    private static double approach(double x, double target, double step) {
        return target > x ? Math.min(target, x + step) : Math.max(target, x - step);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    /** Свойства воды, которые нужны котлу (из {@code WaterProperties} подсистемы). */
    interface WaterPackageView {
        double liquidEnthalpy(double pressurePa, double tempK);

        double vaporEnthalpy(double pressurePa);
    }
}
