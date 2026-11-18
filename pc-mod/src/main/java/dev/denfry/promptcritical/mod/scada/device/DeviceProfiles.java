package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.automation.AutomationPanelBlockEntity;
import dev.denfry.promptcritical.mod.automation.PlcBlockEntity;
import dev.denfry.promptcritical.mod.drilling.DrillingPanels;
import dev.denfry.promptcritical.mod.npp.BwrPanels;
import dev.denfry.promptcritical.mod.npp.BwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduPanels;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.EccsPumpBlockEntity;
import dev.denfry.promptcritical.mod.npp.NppPanels;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.PrimaryPumpBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrPanels;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.SfrPanels;
import dev.denfry.promptcritical.mod.npp.SfrReactorBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.production.ProductionPanels;
import dev.denfry.promptcritical.mod.reactor.PileControllerBlockEntity;
import dev.denfry.promptcritical.mod.reactor.ReactorPanels;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.plc.IlCompiler;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import dev.denfry.promptcritical.sim.automation.sis.SisLogic;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Какие блоки мода — какие периферии CC (03b §3b.1.1, 00 §0.7.2, 03d §3d.14, 07 §7.17, 09) и чем профиль отличается
 * от общего {@code pc_device}.
 *
 * <p>Карта GDD → классы мода (решения, где GDD описывает блоки, которых в моде нет):
 * <ul>
 * <li>{@code pc_reactor} + {@code pc_rod_drive} + {@code pc_protection} (03b §3b.2.1–3): в моде реактор, привод СУЗ
 * и шкаф АЗ — один блок со щитом ({@link PowerReactorBlockEntity} РБМК, {@link PwrReactorBlockEntity},
 * {@link BwrReactorBlockEntity}, {@link CanduReactorBlockEntity}, {@link SfrReactorBlockEntity},
 * {@link PileControllerBlockEntity}), поэтому у периферии три типа. Команды — кнопки щита: {@code requestTrip},
 * {@code reset}, {@code withdraw}/{@code insert}/{@code stopRods}, у РБМК и ВВЭР ещё автоматический регулятор и
 * уставка. Вывод защит, «разрешения», выгрузка топлива в руки и учения — только руками ({@code E_HARDWARE_ONLY}),
 * как и {@code setSetpoint}, {@code setBypass}, {@code disable}, {@code setLogic}.</li>
 * <li>{@code pc_pump} (§3b.2.4): ГЦН {@link PrimaryPumpBlockEntity} и насос САОЗ {@link EccsPumpBlockEntity} крутит
 * сеть Create — муфты в моде нет, поэтому только чтение {@code getState}; пуск и останов — муфтой Create с
 * редстоуном. Буровой, шахтный водоотливной и дегазационный насосы — со щитом.</li>
 * <li>{@code pc_plc} (03d §3d.14.1): {@link PlcBlockEntity} — {@code getState}, {@code readIO}, {@code uploadST},
 * запись маркеров {@code writeMarker}/{@code writeWord}; {@code setMode} — ключ ЦП, только руками.</li>
 * <li>{@code pc_sis} (03d §3d.14.2): логический контроллер ПАЗ — только чтение; {@code reset}, {@code setBypass},
 * {@code requestTrip}, {@code setSetpoint}, {@code setMatrix} — {@code E_HARDWARE_ONLY}.</li>
 * <li>{@code pc_well}, {@code pc_drilling} (09): фонтанная арматура, станок-качалка, пульт бурильщика.</li>
 * <li>Остальные типы 00 §0.7.2 — по классам машин в {@link #GENERIC}; методы — общие.</li>
 * </ul>
 * Свои периферии уже есть у шлюза ({@code pc_rtu}), историана, двойника, биржи, MES и пульта диспетчера сети — они не
 * получают второй. Реле, аналоговые блоки, наряды и бумаги периферией не являются (03d §3d.14: их читают проводом).
 */
public final class DeviceProfiles {
    private DeviceProfiles() {
    }

    /** Типы машин со щитом без особых методов: простое имя класса BE → тип периферии. */
    static final Map<String, String> GENERIC = new LinkedHashMap<>();

    static {
        for (String c : List.of("FireTubeBoilerBlockEntity", "WaterTubeBoilerBlockEntity", "ElectrodeBoilerBlockEntity", "HrsgBlockEntity",
                "GrateStokerBlockEntity")) GENERIC.put(c, "pc_boiler");
        GENERIC.put("SteamEngineBlockEntity", "pc_turbine");
        for (String c : List.of("ShaftGeneratorBlockEntity", "DieselGeneratorBlockEntity", "WindTurbineBlockEntity", "TidalUnitBlockEntity",
                "PumpedStorageBlockEntity", "RtgBlockEntity")) GENERIC.put(c, "pc_generator");
        for (String c : List.of("BreakerCellBlockEntity", "RelayCabinetBlockEntity", "DistributionBoxBlockEntity")) GENERIC.put(c, "pc_breaker");
        GENERIC.put("TransformerBlockEntity", "pc_transformer");
        for (String c : List.of("BlastFurnaceControllerBlockEntity", "HotBlastStoveBlockEntity", "CokeOvenBlockEntity", "SinterStrandBlockEntity",
                "BakingFurnaceBlockEntity", "CupolaBlockEntity", "CrucibleBlockEntity", "BofVesselBlockEntity", "BatchFurnaceBlockEntity",
                "LadleStationBlockEntity", "SubmergedArcFurnaceBlockEntity", "AchesonFurnaceBlockEntity", "PyrolysisFurnaceBlockEntity"))
            GENERIC.put(c, "pc_furnace");
        for (String c : List.of("MillStandBlockEntity", "PiercingMillBlockEntity")) GENERIC.put(c, "pc_rolling_mill");
        GENERIC.put("AirSeparatorBlockEntity", "pc_utility");
        for (String c : List.of("CrudeColumnBlockEntity", "MethanolColumnBlockEntity", "HeavyWaterColumnBlockEntity", "GsExchangeTowerBlockEntity",
                "NglFractionatorBlockEntity")) GENERIC.put(c, "pc_column");
        GENERIC.put("SeparatorBlockEntity", "pc_tank");
        GENERIC.put("CascadeBlockEntity", "pc_cascade");
        for (String c : List.of("MainMineFanBlockEntity", "LampRackBlockEntity")) GENERIC.put(c, "pc_mine");
        GENERIC.put("FuelRackBlockEntity", "pc_fuel_pool");
        for (String c : List.of("CrackedGasUnitBlockEntity", "LdpeReactorBlockEntity", "BrineTreaterBlockEntity", "CellRoomBlockEntity",
                "EdcCrackerBlockEntity", "EdcUnitBlockEntity", "LiquefierBlockEntity", "PvcReactorBlockEntity", "RectifierBlockEntity",
                "CzochralskiPullerBlockEntity", "SiemensReactorBlockEntity", "TrichlorosilaneReactorBlockEntity", "WaferSawBlockEntity",
                "GasPlantBlockEntity", "HeliumRecoveryBlockEntity", "AcidConcentratorBlockEntity", "AmmoniaConverterBlockEntity",
                "Co2AbsorberBlockEntity", "NitricAcidBlockEntity", "SteamReformerBlockEntity", "MethanolConverterBlockEntity",
                "UraniumProcessBlockEntity", "PurexCellBlockEntity", "ReactorWaterCleanupBlockEntity", "FuelShopBlockEntity",
                "PelletPressBlockEntity", "MagneticSeparatorBlockEntity", "ByproductPlantBlockEntity", "ElectrodeExtruderBlockEntity",
                "UltrasonicBathBlockEntity")) GENERIC.put(c, "pc_chem_process");
    }

    /** Пакеты, чьи блоки периферией не являются: у SCADA своя, наряды и бумаги — дело людей. */
    private static final List<String> EXCLUDED_PACKAGES = List.of("dev.denfry.promptcritical.mod.scada.", "dev.denfry.promptcritical.mod.ops.",
            "dev.denfry.promptcritical.mod.paperwork.", "dev.denfry.promptcritical.mod.market.", "dev.denfry.promptcritical.mod.mes.");

    private static final Map<String, DeviceProfile> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Профиль блока или {@code null}, если блок — не периферия устройства. */
    @Nullable
    public static DeviceProfile of(BlockEntity be) {
        if (be instanceof AutomationPanelBlockEntity a) {
            if (a.kind() == AutomationKind.SIS_LOGIC) return CACHE.computeIfAbsent("sis", k -> sis());
            if (a.kind() == AutomationKind.GAS_DETECTOR) return CACHE.computeIfAbsent("analyzer", k -> generic("pc_analyzer", "GAS"));
            return null; // реле и аналоговые блоки — без периферии (03d §3d.14)
        }
        if (be instanceof PlcBlockEntity) return CACHE.computeIfAbsent("plc", k -> plc());
        if (be instanceof PowerReactorBlockEntity) return CACHE.computeIfAbsent("rbmk", k -> rbmk());
        if (be instanceof PwrReactorBlockEntity) return CACHE.computeIfAbsent("pwr", k -> pwr());
        if (be instanceof BwrReactorBlockEntity) return CACHE.computeIfAbsent("bwr", k -> reactor("BWR", BwrPanels.B_SCRAM, BwrPanels.B_RESET,
                BwrPanels.B_UP, BwrPanels.B_STOP, BwrPanels.B_DOWN).hardwareButtons(BwrPanels.B_UNLOAD, BwrPanels.B_LOCA).build());
        if (be instanceof CanduReactorBlockEntity) return CACHE.computeIfAbsent("candu", k -> reactor("CANDU", CanduPanels.B_SCRAM, CanduPanels.B_RESET,
                CanduPanels.B_UP, CanduPanels.B_STOP, CanduPanels.B_DOWN)
                .hardwareButtons(CanduPanels.B_REFUEL, CanduPanels.B_DRAIN, CanduPanels.B_LOCA).build());
        if (be instanceof SfrReactorBlockEntity) return CACHE.computeIfAbsent("sfr", k -> reactor("SFR", SfrPanels.B_SCRAM, SfrPanels.B_RESET,
                SfrPanels.B_UP, SfrPanels.B_STOP, SfrPanels.B_DOWN).hardwareButtons(SfrPanels.B_BLACKOUT).build());
        if (be instanceof PileControllerBlockEntity) return CACHE.computeIfAbsent("pile", k -> reactor("GRAPHITE_PILE", ReactorPanels.BTN_SCRAM,
                ReactorPanels.BTN_RESET, ReactorPanels.BTN_UP, ReactorPanels.BTN_STOP, ReactorPanels.BTN_DOWN)
                .button("windShutdownSpring", ReactorPanels.BTN_WIND).build());
        if (be instanceof PrimaryPumpBlockEntity) return CACHE.computeIfAbsent("primary_pump", k -> shaftPump("PRIMARY", (b, s, a) -> {
            PrimaryPumpBlockEntity p = (PrimaryPumpBlockEntity) b;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", p.drive() > 0 ? "RUNNING" : "STOPPED");
            m.put("drive", p.drive());
            m.put("flowShare", p.flowShare());
            return m;
        }));
        if (be instanceof EccsPumpBlockEntity) return CACHE.computeIfAbsent("eccs_pump", k -> shaftPump("ECCS", (b, s, a) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("state", ((EccsPumpBlockEntity) b).driven() ? "RUNNING" : "STOPPED");
            return m;
        }));
        if (!(be instanceof PanelHost)) return null;
        String cls = be.getClass().getName();
        for (String p : EXCLUDED_PACKAGES) if (cls.startsWith(p)) return null;
        if (be instanceof dev.denfry.promptcritical.mod.grid.GridDispatchCenterBlockEntity) return null;
        String simple = be.getClass().getSimpleName();
        return switch (simple) {
            case "MudPumpBlockEntity" -> CACHE.computeIfAbsent(simple, k -> panelPump("MUD").also("pc_drilling").build());
            case "MineDrainagePumpBlockEntity" -> CACHE.computeIfAbsent(simple, k -> panelPump("MINE_DRAINAGE").also("pc_mine").build());
            case "DegasificationPumpBlockEntity" -> CACHE.computeIfAbsent(simple, k -> panelPump("DEGASIFICATION").also("pc_mine").build());
            case "WellheadBlockEntity" -> CACHE.computeIfAbsent(simple, k -> DeviceProfile.builder("pc_well", "FLOWING")
                    .button("chokeDown", ProductionPanels.WB_CHOKE_DOWN).button("chokeUp", ProductionPanels.WB_CHOKE_UP)
                    .button("toggleMasterValve", ProductionPanels.WB_VALVE)
                    .button("counterbalanceDown", ProductionPanels.WB_CB_DOWN).button("counterbalanceUp", ProductionPanels.WB_CB_UP)
                    .button("acousticFluidLevel", ProductionPanels.WB_ECHO)
                    .read("getDynamometerCard", 32, (b, s, a) -> dynoCard((dev.denfry.promptcritical.mod.production.WellheadBlockEntity) b))
                    .read("getMotor", 4, (b, s, a) -> rodPumpMotor((dev.denfry.promptcritical.mod.production.WellheadBlockEntity) b))
                    .probe(WellEvents::new).build());
            case "PumpjackBlockEntity" -> CACHE.computeIfAbsent(simple, k -> DeviceProfile.builder("pc_well", "ROD_PUMP")
                    .also("pc_well_rodpump")
                    .read("getDynamometerCard", 32, (b, s, a) -> dynoCard(pumpjackWell(b)))
                    .read("getMotor", 4, (b, s, a) -> rodPumpMotor(pumpjackWell(b)))
                    .probe(() -> lampState("running", "pc_pump_state")).build());
            case "DrillersConsoleBlockEntity" -> CACHE.computeIfAbsent(simple, k -> DeviceProfile.builder("pc_drilling", "CONSOLE")
                    .button("wobDown", DrillingPanels.B_WOB_DOWN).button("wobUp", DrillingPanels.B_WOB_UP)
                    .button("spmDown", DrillingPanels.B_SPM_DOWN).button("spmUp", DrillingPanels.B_SPM_UP)
                    .button("toggleBop", DrillingPanels.B_BOP).button("advanceKill", DrillingPanels.B_KILL)
                    .button("zeroPitTotalizer", DrillingPanels.B_ZERO).button("workPipe", DrillingPanels.B_WORK)
                    .read("getBop", 4, DeviceProfiles::bopState)
                    .command("closeBop", a -> {
                        var comp = bopComponent(a);
                        return t -> ((dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity) t).closeComponent(comp, null);
                    })
                    .command("openBop", a -> {
                        var comp = bopComponent(a);
                        return t -> ((dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity) t).openComponent(comp, null);
                    }).build());
            default -> {
                String type = GENERIC.getOrDefault(simple, "pc_device");
                yield CACHE.computeIfAbsent(simple, k -> generic(type, subtype(simple)));
            }
        };
    }

    /** Элемент стека ПВО по имени 09 §9.13.2: {@code "ANNULAR"}, {@code "PIPE_RAM"}, {@code "BLIND_RAM"}, {@code "SHEAR_RAM"}. */
    private static dev.denfry.promptcritical.sim.drilling.BopStack.Component bopComponent(Object[] a) throws DeviceException {
        String name = LuaArgs.string(a, 0).toUpperCase(java.util.Locale.ROOT);
        for (var c : dev.denfry.promptcritical.sim.drilling.BopStack.Component.values()) if (c.name().equals(name)) return c;
        throw LuaArgs.arg(0, "ANNULAR, PIPE_RAM, BLIND_RAM or SHEAR_RAM");
    }

    /** {@code getBop()} 09 §9.13.2: элементы стека и аккумулятор. */
    private static Object bopState(BlockEntity be, DeviceRuntime.Snapshot s, Object[] args) {
        var c = (dev.denfry.promptcritical.mod.drilling.DrillersConsoleBlockEntity) be;
        var stack = c.bopStack();
        List<Map<String, Object>> list = new ArrayList<>();
        if (stack != null) for (var e : stack.elements()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("component", e.kind.name());
            m.put("closed", e.closed());
            m.put("sealing", e.sealing());
            m.put("pressureRating", e.holdingPa(!stack.pipeSheared()));
            m.put("closures", e.closures());
            m.put("lastTest", Double.isFinite(e.lastTestDay()) ? e.lastTestDay() : null);
            m.put("fault", e.fault().name());
            list.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("elements", list);
        out.put("pipeSheared", stack != null && stack.pipeSheared());
        var acc = c.accumulator();
        if (acc != null) {
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("pressure", acc.pressurePa());
            a.put("precharge", dev.denfry.promptcritical.sim.drilling.BopAccumulator.PRECHARGE_PA);
            a.put("bottles", acc.bottles());
            a.put("api53", stack != null && acc.meetsApi53(stack.requiredVolumeM3()));
            out.put("accumulator", a);
        }
        out.put("stuck", c.sticking().stuck());
        out.put("stickingForce", c.sticking().stickingForceN());
        var w = c.well();
        out.put("lossRate", w == null ? 0.0 : w.lossM3s());
        return out;
    }

    private static dev.denfry.promptcritical.mod.production.WellheadBlockEntity pumpjackWell(BlockEntity b) throws DeviceException {
        var w = ((dev.denfry.promptcritical.mod.production.PumpjackBlockEntity) b).wellhead();
        if (w == null) throw new DeviceException(DeviceException.RANGE, "no wellhead within reach of the pumpjack");
        return w;
    }

    /** {@code getDynamometerCard()} 09 §9.13.1: 200 точек, м и Н; {@code nil}, если скважина не на насосе. */
    private static Object dynoCard(dev.denfry.promptcritical.mod.production.WellheadBlockEntity w) {
        var well = w.well();
        var c = well == null ? null : well.card();
        if (c == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("period", c.periodS());
        List<Double> x = new ArrayList<>(c.positionM().length), f = new ArrayList<>(c.loadN().length);
        for (double v : c.positionM()) x.add(v);
        for (double v : c.loadN()) f.add(v);
        m.put("position", x);
        m.put("load", f);
        return m;
    }

    /** {@code getMotor()} для качалки: момент на редукторе, его паспорт и мощность на валу. */
    private static Object rodPumpMotor(dev.denfry.promptcritical.mod.production.WellheadBlockEntity w) {
        var well = w.well();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("torque", well == null ? 0.0 : well.peakTorqueNm());
        m.put("torqueRating", dev.denfry.promptcritical.sim.production.ProductionWell.GEARBOX_RATED_NM);
        m.put("counterbalance", well == null ? 0.0 : well.counterbalanceN());
        m.put("goodman", well == null ? 0.0 : well.goodmanRatio());
        m.put("gearboxBroken", well != null && well.gearboxBroken());
        return m;
    }

    /** События скважины 09 §9.13.1: {@code pc_well_fluid_level} после замера эхолотом и {@code pc_well_rod_part}. */
    static final class WellEvents implements DeviceProfile.Probe {
        private int echoes = -1, parts = -1;

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            var w = (dev.denfry.promptcritical.mod.production.WellheadBlockEntity) be;
            if (echoes >= 0 && w.echoCount() != echoes) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("level", w.echoLevelM());
                m.put("echoTime", w.echoTimeS());
                out.emit("pc_well_fluid_level", m);
            }
            if (parts >= 0 && w.rodPartCount() != parts && w.well() != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("depth", w.well().rodPartDepthM());
                out.emit("pc_well_rod_part", m);
            }
            echoes = w.echoCount();
            parts = w.rodPartCount();
        }
    }

    private static String subtype(String simpleClassName) {
        String s = simpleClassName.replace("BlockEntity", "");
        return s.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(java.util.Locale.ROOT);
    }

    private static DeviceProfile generic(String type, String subtype) {
        return DeviceProfile.builder(type, subtype).build();
    }

    // ------------------------------------------------------------------ реакторы

    private static final String[] PROTECTION_HARDWARE = {"setSetpoint", "setBypass", "disable", "setLogic"};

    private static DeviceProfile.Builder reactor(String subtype, int scram, int reset, int up, int stop, int down) {
        return DeviceProfile.builder("pc_reactor", subtype).also("pc_rod_drive", "pc_protection")
                .button("requestTrip", scram).button("reset", reset)
                .button("withdraw", up).button("stopRods", stop).button("insert", down)
                .hardware(PROTECTION_HARDWARE)
                .read("getStatus", 4, DeviceProfiles::reactorStatus)
                .probe(() -> new TripLamp("button:" + scram));
    }

    private static DeviceProfile rbmk() {
        return reactor("RBMK", NppPanels.B_SCRAM, NppPanels.B_RESET, NppPanels.B_RR_UP, NppPanels.B_RR_STOP, NppPanels.B_RR_DOWN)
                .button("toggleAuto", NppPanels.B_AUTO).button("setpointDown", NppPanels.B_SP_DOWN).button("setpointUp", NppPanels.B_SP_UP)
                .hardwareButtons(NppPanels.B_PROTECTION, NppPanels.B_UNLOAD, NppPanels.B_LOCA).build();
    }

    private static DeviceProfile pwr() {
        return reactor("PWR", PwrPanels.B_SCRAM, PwrPanels.B_RESET, PwrPanels.B_RG_UP, PwrPanels.B_RG_STOP, PwrPanels.B_RG_DOWN)
                .button("toggleAuto", PwrPanels.B_AUTO).button("setpointDown", PwrPanels.B_SP_DOWN).button("setpointUp", PwrPanels.B_SP_UP)
                .button("exchangeDown", PwrPanels.B_XCH_DOWN).button("exchangeUp", PwrPanels.B_XCH_UP)
                .button("exchangeSource", PwrPanels.B_XCH_SOURCE)
                .hardwareButtons(PwrPanels.B_LOW_FLUX, PwrPanels.B_UNLOAD, PwrPanels.B_LOCA).build();
    }

    @Nullable
    static String tagEndingWith(DeviceRuntime.Snapshot s, String suffix) {
        for (DeviceTag t : s.tags()) if (DeviceTag.DISCRETE.equals(t.kind()) && t.name().endsWith(suffix)) return t.name();
        return null;
    }

    /** {@code getStatus} СВРК в объёме щита (03b §3b.2.1): состояние по лампе «АЗ», без скрытых величин. */
    private static Object reactorStatus(BlockEntity be, DeviceRuntime.Snapshot s, Object[] args) {
        String trip = tagEndingWith(s, "tripped");
        boolean tripped = trip != null && s.numeric(trip) > 0.5;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", tripped ? "TRIPPED" : "OPERATING");
        m.put("tripped", tripped);
        m.put("formed", true);
        m.put("t", s.simTime());
        return m;
    }

    /** События {@code pc_scram}/{@code pc_scram_reset} (03b §3b.3.3) по лампе «АЗ» щита. */
    static final class TripLamp implements DeviceProfile.Probe {
        private final String scramChannel;
        private Boolean last;

        TripLamp(String scramChannel) {
            this.scramChannel = scramChannel;
        }

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            String tag = tagEndingWith(s, "tripped");
            if (tag == null) return;
            boolean now = s.numeric(tag) > 0.5;
            if (last != null && now != last) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (now) {
                    m.put("reasons", new LinkedHashMap<>());
                    // ручную АЗ с щита отсюда не отличить от автоматической: щит не сообщает, кто нажал
                    m.put("source", out.luaRequested(scramChannel, 40) ? "LUA" : "AUTOMATIC");
                    out.emit("pc_scram", m);
                } else {
                    out.emit("pc_scram_reset", m);
                }
            }
            last = now;
        }
    }

    // ------------------------------------------------------------------ насосы и скважины

    private static DeviceProfile.Builder panelPump(String subtype) {
        return DeviceProfile.builder("pc_pump", subtype).probe(() -> lampState("running", "pc_pump_state"));
    }

    private static DeviceProfile shaftPump(String subtype, DeviceProfile.Read state) {
        return DeviceProfile.builder("pc_pump", subtype).read("getState", 4, state).hardware("start", "stop", "setCoupling")
                .probe(() -> new FieldState("pc_pump_state", (be, s) -> {
                    try {
                        return String.valueOf(((Map<?, ?>) state.read(be, s, new Object[0])).get("state"));
                    } catch (DeviceException e) {
                        return null;
                    }
                })).build();
    }

    /** Смена состояния RUNNING/STOPPED по лампе щита — событие {@code event} {@code {state, prev}}. */
    static DeviceProfile.Probe lampState(String suffix, String event) {
        return new FieldState(event, (be, s) -> {
            String tag = tagEndingWith(s, suffix);
            return tag == null ? null : s.numeric(tag) > 0.5 ? "RUNNING" : "STOPPED";
        });
    }

    /** Событие {@code {state, prev}} при смене строки состояния; {@code null} — состояние неизвестно. */
    static final class FieldState implements DeviceProfile.Probe {
        private final String event;
        private final BiFunction<BlockEntity, DeviceRuntime.Snapshot, String> state;
        private String last;

        FieldState(String event, BiFunction<BlockEntity, DeviceRuntime.Snapshot, String> state) {
            this.event = event;
            this.state = state;
        }

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            String now = state.apply(be, s);
            if (now == null) return;
            if (last != null && !now.equals(last)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("state", now);
                m.put("prev", last);
                out.emit(event, m);
            }
            last = now;
        }
    }

    // ------------------------------------------------------------------ ПАЗ

    private static AutomationUnit unit(BlockEntity be) throws DeviceException {
        AutomationUnit u = be instanceof dev.denfry.promptcritical.mod.automation.AutomationBlockEntity a ? a.unit() : null;
        if (u == null) throw new DeviceException(DeviceException.UNLOADED, "device is not bound to the simulation yet");
        return u;
    }

    private static DeviceProfile sis() {
        return DeviceProfile.builder("pc_sis", "LOGIC_SOLVER").readOnly()
                .hardware("reset", "setBypass", "requestTrip", "setSetpoint", "setMatrix")
                .read("getState", 4, (be, s, a) -> sisState(unit(be)))
                .read("getCauses", 4, (be, s, a) -> sisCauses(unit(be)))
                .read("getBypasses", 1, (be, s, a) -> {
                    SisLogic l = unit(be).sis();
                    List<Map<String, Object>> out = new ArrayList<>();
                    if (l != null && l.bypass()) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("cause", "MAIN");
                        m.put("kind", "MAINTENANCE");
                        m.put("since", s.simTime() - l.bypassSeconds());
                        out.add(m);
                    }
                    return out;
                })
                .read("getPfd", 4, (be, s, a) -> {
                    AutomationUnit u = unit(be);
                    SisLogic l = u.sis();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("sif", "MAIN");
                    m.put("pfdAvg", u.sisPfdAvg());
                    m.put("pfdNow", u.sisPfdNow());
                    m.put("silAchieved", SisLogic.sil(u.sisPfdAvg()));
                    m.put("architecture", u.sisVoting().name());
                    m.put("sinceTestH", l == null ? 0.0 : l.sinceTestH());
                    m.put("intervalH", u.sisProofIntervalH());
                    m.put("overdue", l != null && l.proofTestOverdue(u.sisProofIntervalH()));
                    return List.of(m);
                })
                .probe(SisProbe::new).build();
    }

    private static Map<String, Object> sisState(AutomationUnit u) throws DeviceException {
        SisLogic l = u.sis();
        if (l == null) throw new DeviceException(DeviceException.FAULT, "no logic solver");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", l.state(u.sisPowered()).name());
        m.put("tripped", l.tripped());
        m.put("bypasses", l.bypass() ? 1 : 0);
        m.put("testing", l.testing());
        m.put("power", u.sisPowered());
        m.put("voting", u.sisVoting().name());
        m.put("trips", l.trips());
        m.put("proofTests", l.proofTests());
        m.put("proofTestsOnTime", l.proofTestsOnTime());
        m.put("proofTestOverdue", l.proofTestOverdue(u.sisProofIntervalH()));
        var cab = u.sisCabinet();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("causes", cab.causes());
        c.put("twoOutOfThree", cab.twoOutOfThree());
        c.put("bypassed", cab.bypassed());
        c.put("overdue", cab.overdue());
        m.put("cabinet", c);
        return m;
    }

    private static List<Map<String, Object>> sisCauses(AutomationUnit u) throws DeviceException {
        SisLogic l = u.sis();
        if (l == null) throw new DeviceException(DeviceException.FAULT, "no logic solver");
        SisLogic.Voting v = u.sisVoting();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", "MAIN");
        m.put("state", l.state(u.sisPowered()).name());
        m.put("voting", v.name());
        Map<String, Object> ch = new LinkedHashMap<>();
        int votes = 0;
        for (int i = 0; i < v.n && i < SisLogic.CHANNELS; i++) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("mA", u.sisChannelMa(i));
            c.put("q", l.channelFault(i) ? "BAD" : "GOOD");
            c.put("state", l.channelDemand(i) ? "TRIP" : "NORMAL");
            if (l.channelDemand(i)) votes++;
            ch.put(String.valueOf((char) ('A' + i)), c);
        }
        m.put("votes", votes + "/" + v.n);
        m.put("channels", ch);
        m.put("bypass", l.bypass());
        return List.of(m);
    }

    /** События ПАЗ (03d §3d.14.7): срабатывание, сброс, байпас, просрочка проверки. */
    static final class SisProbe implements DeviceProfile.Probe {
        private Boolean tripped, bypass, overdue;

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            AutomationUnit u;
            try {
                u = unit(be);
            } catch (DeviceException e) {
                return;
            }
            SisLogic l = u.sis();
            if (l == null) return;
            boolean t = l.tripped(), b = l.bypass(), o = l.proofTestOverdue(u.sisProofIntervalH());
            if (tripped != null && t != tripped) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("causes", List.of("MAIN"));
                if (t) {
                    m.put("source", "AUTOMATIC");
                    out.emit("pc_sis_trip", m);
                } else {
                    out.emit("pc_sis_reset", m);
                }
            }
            if (bypass != null && b != bypass) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("cause", "MAIN");
                m.put("kind", "MAINTENANCE");
                m.put("on", b);
                out.emit("pc_sis_bypass", m);
            }
            if (overdue != null && o && !overdue) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("sif", "MAIN");
                m.put("overdueS", Math.max(0, (l.sinceTestH() - u.sisProofIntervalH()) * 3600));
                m.put("pfdNow", u.sisPfdNow());
                out.emit("pc_sis_test_due", m);
            }
            tripped = t;
            bypass = b;
            overdue = o;
        }
    }

    // ------------------------------------------------------------------ ПЛК

    private static PlcCpu cpu(BlockEntity be) throws DeviceException {
        PlcCpu c = unit(be).plc();
        if (c == null) throw new DeviceException(DeviceException.FAULT, "no CPU");
        return c;
    }

    private static DeviceProfile plc() {
        return DeviceProfile.builder("pc_plc", "IL").hardware("setMode")
                .read("getState", 4, (be, s, a) -> {
                    PlcCpu c = cpu(be);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", c.mode().name());
                    m.put("key", c.runKey() ? "RUN" : "STOP");
                    m.put("fault", c.mode() == PlcCpu.Mode.FAULT);
                    m.put("programOk", c.program().ok());
                    m.put("errors", c.program().errors().size());
                    m.put("ops", c.lastExecuted());
                    m.put("t", s.simTime());
                    return m;
                })
                .read("readIO", 4 + 0.25 * (IlCompiler.IX + IlCompiler.QX + IlCompiler.MX + IlCompiler.IW + IlCompiler.QW + IlCompiler.MW),
                        (be, s, a) -> {
                            PlcCpu c = cpu(be);
                            Map<String, Object> m = new LinkedHashMap<>();
                            m.put("ix", bits(IlCompiler.IX, c::input));
                            m.put("qx", bits(IlCompiler.QX, c::output));
                            m.put("mx", bits(IlCompiler.MX, c::marker));
                            m.put("iw", words(IlCompiler.IW, c::analogInput));
                            m.put("qw", words(IlCompiler.QW, c::analogOutput));
                            m.put("mw", words(IlCompiler.MW, c::word));
                            m.put("t", s.simTime());
                            return m;
                        })
                .read("uploadST", 32, (be, s, a) -> cpu(be).program().source())
                .command("writeMarker", a -> {
                    int i = LuaArgs.integer(a, 0, 0, IlCompiler.MX - 1);
                    boolean v = LuaArgs.bool(a, 1);
                    return be -> cpu(be).writeMarker(i, v);
                })
                .command("writeWord", a -> {
                    int i = LuaArgs.integer(a, 0, 0, IlCompiler.MW - 1);
                    double v = LuaArgs.finite(a, 1);
                    if (Math.abs(v) > 1e9) throw new DeviceException(DeviceException.RANGE, "word value beyond ±1e9");
                    return be -> cpu(be).writeWord(i, v);
                })
                .probe(PlcProbe::new).build();
    }

    private static List<Boolean> bits(int n, java.util.function.IntPredicate f) {
        List<Boolean> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(f.test(i));
        return out;
    }

    private static List<Double> words(int n, java.util.function.IntToDoubleFunction f) {
        List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(f.applyAsDouble(i));
        return out;
    }

    /** События ПЛК (03d §3d.14.7): {@code pc_plc_mode} и {@code pc_plc_fault}. */
    static final class PlcProbe implements DeviceProfile.Probe {
        private PlcCpu.Mode last;

        @Override
        public void tick(BlockEntity be, DeviceRuntime.Snapshot s, DeviceProfile.Emitter out) {
            PlcCpu c;
            try {
                c = cpu(be);
            } catch (DeviceException e) {
                return;
            }
            PlcCpu.Mode now = c.mode();
            if (last != null && now != last) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("mode", now.name());
                m.put("prev", last.name());
                out.emit("pc_plc_mode", m);
                if (now == PlcCpu.Mode.FAULT) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    f.put("code", "WATCHDOG");
                    f.put("detail", "scan exceeded " + PlcCpu.WATCHDOG + " instructions");
                    out.emit("pc_plc_fault", f);
                }
            }
            last = now;
        }
    }
}
