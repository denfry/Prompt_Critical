package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimRandom;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.TimeDomain;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Эксплуатация установок I0 в мире (13 §13.6.5–13.6.7, срез R31): пусконаладка {@link CommissioningService}, износ
 * машин {@link MachineWear}, их {@link MaintenancePanel} и пробный пуск. Установка — устройство другой подсистемы
 * (котёл, машина, вентилятор, насос, подъём) с тем же {@link SimId}; биндинг регистрирует её при сборке
 * ({@link #ensure}) и каждый тик пишет входы ({@link Unit#setRunning}, {@link #observeTrial}). Компоненты установки
 * (ПК, манометр, привод…) своих устройств не имеют: их id синтетические и меняются при замене компонента (MOC).
 *
 * <p>Износ — SLOW (×k_slow), только пока машина вращается. События пусконаладки и обслуживания идут в общий журнал и
 * в короткий журнал установки ({@link #log}) для щита и кнопки «Журнал».
 */
public final class OpsSubsystem implements Subsystem {
    private final LockoutRegistry lockouts = new LockoutRegistry();
    /** Наряды-допуски и остановочные ремонты (13 §13.2, §13.6.2): по стенду нарядов, ключ — позиция стенда. */
    private final PermitRegistry permits = new PermitRegistry();
    private final NavigableMap<Long, Turnaround> turnarounds = new TreeMap<>();
    public static final String ID = "ops";
    /** Поток {@link SimRandom} износа (u_b подшипника, новые вкладыши). */
    public static final int WEAR_STREAM = 0x13_67;
    /** Сброс нагрузки в пробном пуске: нагрузка ниже этой доли минимальной (или останов без защиты). */
    public static final double REJECTION_FRACTION = 0.3;
    /** Записей в журнале установки. */
    public static final int LOG_SIZE = 32;
    private static final int SLOW_PHASE = 11;

    /** Установка в мире: износ, панель, входы биндинга и пробный пуск. */
    public static final class Unit {
        final SimId id;
        final UnitKind kind;
        final MachineWear wear;
        final MaintenancePanel panel;
        final Deque<OpsLogEntry> log = new ArrayDeque<>();
        int generation, repairs;
        boolean running, dryRunning;
        double waterPh = Double.NaN;
        // пробный пуск
        double trialRunS;
        boolean trialArmed, trialRejected;
        Baseline trialBaseline = Baseline.NONE;

        Unit(SimId id, UnitKind kind, MachineWear wear, MaintenancePanel panel) {
            this.id = id;
            this.kind = kind;
            this.wear = wear;
            this.panel = panel;
        }

        public SimId id() {
            return id;
        }

        public UnitKind kind() {
            return kind;
        }

        /** Износ машины; у котла — null (накипь панели добавляет биндинг). */
        public MachineWear wear() {
            return wear;
        }

        public MaintenancePanel panel() {
            return panel;
        }

        /**
         * Входы биндинга на тик.
         *
         * @param dryRunning насос без заливки
         * @param waterPh    pH перекачиваемой воды (насос), иначе NaN
         */
        public void setRunning(boolean running, boolean dryRunning, double waterPh) {
            this.running = running;
            this.dryRunning = dryRunning;
            this.waterPh = waterPh;
        }

        public boolean running() {
            return running;
        }

        /** Подшипник заплавлен: машина стоит до замены вкладыша. */
        public boolean seized() {
            return wear != null && wear.bearing().seized();
        }

        /** Наработка пробного пуска, с реального времени. */
        public double trialRunS() {
            return trialRunS;
        }

        public boolean trialRejected() {
            return trialRejected;
        }

        public List<OpsLogEntry> log() {
            return List.copyOf(log);
        }
    }

    private final long worldSeed;
    private final NavigableMap<SimId, Unit> units = new TreeMap<>();
    private final List<OpsLogEntry> pending = new ArrayList<>();
    private final Events events = new Events();
    private final CommissioningService service;
    private final LicenceRegistry licences = new LicenceRegistry();
    /** Профиль licensing: пусконаладка всегда ENFORCED, пуск требует лицензии команды-владельца на эпоху. */
    private boolean licensing;
    private OpsConfig cfg;
    private SimConfig config;
    private long tick;

    public OpsSubsystem(OpsConfig cfg, long worldSeed) {
        this.cfg = cfg;
        this.worldSeed = worldSeed;
        this.service = new CommissioningService(cfg, worldSeed, events);
    }

    @Override
    public String id() {
        return ID;
    }

    /** Действующий конфиг: в профиле licensing режим пусконаладки — {@code ENFORCED}. */
    public OpsConfig config() {
        return effective(cfg);
    }

    public CommissioningService commissioning() {
        return service;
    }

    // --- структура (биндинг, между тиками) ---

    /**
     * Установка собрана: износ новой машины и, кроме режима {@code OFF}, пусконаладка с разыгранными дефектами.
     * Повторный вызов возвращает существующую (загруженную); если режим стал не {@code OFF} — регистрирует в
     * пусконаладке.
     *
     * @param workingPressurePa p_раб сосуда; у машин без давления — 0
     * @param reversedRotation  направление вала Create при сборке обратное (вентилятор, насос)
     * @param porousCastings    литые компоненты с пористостью (08 §8.9)
     */
    public Unit ensure(SimId unit, UnitKind kind, double workingPressurePa, boolean reversedRotation,
            Set<UnitComponent> porousCastings, boolean dashpotFitted) {
        Unit u = units.get(unit);
        if (u == null) u = create(unit, kind);
        if (!service.registered(unit) && effective(cfg).commissioning() != CommissioningMode.OFF) {
            Map<UnitComponent, SimId> comps = new EnumMap<>(UnitComponent.class);
            for (UnitComponent c : kind.components()) comps.put(c, componentId(unit, c, 0));
            service.register(new UnitAssembly(unit, kind, comps, workingPressurePa, reversedRotation, porousCastings,
                    dashpotFitted), tick);
        }
        return u;
    }

    private Unit create(SimId unit, UnitKind kind) {
        MachineWear wear = switch (kind) {
            case FIRE_TUBE_BOILER -> null;
            case STEAM_ENGINE -> MachineWear.newI0(cfg, SimRandom.of(worldSeed, unit, WEAR_STREAM), true, false);
            case MINE_DRAINAGE_PUMP -> MachineWear.newI0(cfg, SimRandom.of(worldSeed, unit, WEAR_STREAM), true, true);
            case MAIN_MINE_FAN, MINE_HOIST -> MachineWear.newI0(cfg, SimRandom.of(worldSeed, unit, WEAR_STREAM), false, false);
        };
        MaintenancePanel panel = new MaintenancePanel(unit, cfg, events);
        if (wear != null) for (FaultSource s : wear.sources()) panel.add(s);
        Unit u = new Unit(unit, kind, wear, panel);
        units.put(unit, u);
        return u;
    }

    /**
     * Источник поверх чужой модели (накипь котла, канат подъёма); один на вид. Строка, восстановленная из сохранения
     * без владельца ({@link Detached}), получает источник на месте.
     */
    public void attach(SimId unit, FaultSource source) {
        Unit u = units.get(unit);
        if (u == null) return;
        for (int i = 0; i < u.panel.size(); i++) {
            if (u.panel.source(i).kind() != source.kind()) continue;
            if (u.panel.source(i) instanceof Detached) u.panel.replace(i, source);
            return;
        }
        u.panel.add(source);
    }

    /** Строка щита после загрузки, пока биндинг не подключил модель-владельца: значения нет. */
    private record Detached(MaintenanceFaultKind kind) implements FaultSource {
        @Override
        public double value() {
            return Double.NaN;
        }

        @Override
        public void repair() {
        }
    }

    public Unit unit(SimId id) {
        return units.get(id);
    }

    public int unitCount() {
        return units.size();
    }

    /** Синтетический id компонента: серийный номер установки, поколение замены, роль. */
    static SimId componentId(SimId unit, UnitComponent role, int generation) {
        long serial = (unit.serial() & 0xFFFF_FFFFL) << 12 | (generation & 0xFFL) << 4 | role.ordinal();
        return SimId.of(SimId.Kind.PROCESS_UNIT, serial);
    }

    /** Замена компонента (MOC, 03d §3d.5.15): у нового — свои дефекты, сброшена только покрывающая проверка. */
    public void replaceComponent(SimId unit, UnitComponent role, UUID by) {
        Unit u = units.get(unit);
        UnitAssembly asm = service.assembly(unit).orElse(null);
        if (u == null || asm == null || !asm.components().containsKey(role)) return;
        u.generation++;
        SimId id = componentId(unit, role, u.generation);
        service.onModified(asm.withComponent(role, id), Set.of(id), by, tick);
    }

    @Override
    public void onDeviceRemoved(SimId device) {
        if (units.remove(device) != null) service.unregister(device);
        licences.forget(device);
    }

    // --- пусконаладка и обслуживание (биндинг) ---

    /** Проверка (13 §13.6.6); установка без пусконаладки — null. Команда — сам проверяющий. */
    public CheckResult perform(SimId unit, CheckInput input, UUID by) {
        return perform(unit, input, by, by);
    }

    /**
     * Проверка от имени команды: первая команда, проводившая проверку, становится владельцем установки (профиль
     * licensing). {@code team} == null (автоматика без игрока) владельца не задаёт.
     */
    public CheckResult perform(SimId unit, CheckInput input, UUID by, UUID team) {
        if (!service.registered(unit)) return null;
        // владелец ставится до проверки, чтобы приёмка в этом же вызове засчиталась команде; отказ его снимает
        boolean claimed = team != null && licences.owner(unit).isEmpty();
        if (claimed) licences.owner(unit, team);
        CheckResult r = service.perform(unit, input, by, tick);
        if (claimed && r.outcome() == CheckResult.Outcome.REFUSED) licences.forget(unit);
        if (r.outcome() != CheckResult.Outcome.REFUSED)
            log(unit, r.passed() ? OpsLogEntry.Kind.CHECK_PASSED : OpsLogEntry.Kind.CHECK_FAILED, r.check().name());
        return r;
    }

    /** Розжиг или пуск привода (13 §13.6.6): отказ в {@code ENFORCED}, запись {@code START_UNACCEPTED}. */
    public StartPermission requestStart(SimId unit) {
        StartPermission p = service.requestStart(unit);
        StartPermission gated = licenceGate(unit, p);
        if (gated != p) log(unit, OpsLogEntry.Kind.START_REFUSED, "licence " + gated.unlicensedEra());
        return gated;
    }

    public StartPermission canStart(SimId unit) {
        return licenceGate(unit, service.canStart(unit));
    }

    /** Профиль licensing: разрешённый пусконаладкой пуск требует лицензии команды-владельца на эпоху установки. */
    private StartPermission licenceGate(SimId unit, StartPermission p) {
        if (!licensing || !p.allowed()) return p;
        Unit u = units.get(unit);
        if (u == null) return p;
        PlantEra era = u.kind.era();
        UUID owner = licences.owner(unit).orElse(null);
        if (era == PlantEra.I0 || owner != null && licences.licensed(owner, era)) return p;
        return new StartPermission(false, p.accepted(), p.missing(), era);
    }

    /** Лицензии команд (профиль licensing). */
    public LicenceRegistry licences() {
        return licences;
    }

    public boolean licensing() {
        return licensing;
    }

    /** Включает профиль licensing (биндинг — по флагу мира при запуске сервера). */
    public void setLicensing(boolean on) {
        licensing = on;
        service.reconfigure(effective(cfg));
    }

    /** В профиле licensing пусконаладка всегда {@code ENFORCED}, что бы ни стояло в конфиге. */
    private OpsConfig effective(OpsConfig c) {
        return licensing && c.commissioning() != CommissioningMode.ENFORCED ? c.withCommissioning(CommissioningMode.ENFORCED) : c;
    }

    /**
     * Пробный пуск (13 §13.6.6): биндинг каждый тик сообщает нагрузку. Отсчёт идёт, пока установка работает с
     * нагрузкой не ниже {@code trial_min_load}, после того как пройдены прочие проверки; сброс нагрузки — падение
     * ниже {@link #REJECTION_FRACTION} минимальной без срабатывания защит. Набрав {@code trial_run_s} и сброс,
     * установка выполняет проверку сама; срабатывание защиты — провал.
     *
     * @return результат, если проверка выполнена в этот вызов; иначе null
     */
    public CheckResult observeTrial(SimId unit, double dtRealS, boolean running, double loadFraction,
            boolean protectionTripped, Baseline baseline) {
        Unit u = units.get(unit);
        UnitAssembly asm = service.assembly(unit).orElse(null);
        if (u == null || asm == null || !asm.kind().checklist().contains(CommissioningCheck.TRIAL_RUN)) return null;
        Set<CommissioningCheck> passed = service.passedChecks(unit);
        boolean ready = !passed.contains(CommissioningCheck.TRIAL_RUN);
        for (CommissioningCheck c : asm.kind().checklist())
            if (c != CommissioningCheck.TRIAL_RUN && !passed.contains(c)) ready = false;
        if (!ready) {
            resetTrial(u);
            return null;
        }
        if (protectionTripped && u.trialArmed) {
            // пуск сорван защитой: не набравший наработки пуск проверка отклоняет — в журнал его всё равно
            CheckResult r = perform(unit, new CheckInput.TrialRun(u.trialRunS, cfg.trialMinLoad(), true,
                    u.trialRejected, u.trialBaseline), null);
            if (r.outcome() == CheckResult.Outcome.REFUSED)
                log(unit, OpsLogEntry.Kind.CHECK_FAILED, CommissioningCheck.TRIAL_RUN.name());
            resetTrial(u);
            return r;
        }
        double min = cfg.trialMinLoad();
        if (running && loadFraction >= min) {
            u.trialArmed = true;
            u.trialRunS += dtRealS;
            u.trialBaseline = baseline;
        } else if (u.trialArmed && (!running || loadFraction < REJECTION_FRACTION * min)) {
            u.trialRejected = true;
        }
        if (u.trialRejected && u.trialRunS >= cfg.trialRunS()) {
            CheckResult r = perform(unit, new CheckInput.TrialRun(u.trialRunS, min, false, true, u.trialBaseline),
                    null);
            resetTrial(u);
            return r;
        }
        return null;
    }

    private static void resetTrial(Unit u) {
        u.trialArmed = u.trialRejected = false;
        u.trialRunS = 0;
        u.trialBaseline = Baseline.NONE;
    }

    /** Замер прибором строки {@code index} панели; false — прибор не применим. */
    public boolean measure(SimId unit, int index, Probe probe) {
        Unit u = units.get(unit);
        return u != null && index >= 0 && index < u.panel.size() && u.panel.measure(index, probe, tick);
    }

    /**
     * Ремонт строки {@code index} ({@code MAINTENANCE_DONE}): сброс износа у владельца, запись в паспорт; новый
     * вкладыш — с новым u_b по Вейбуллу. {@code unsafe} — работа на ходу при {@code ADVISORY}.
     */
    public void repair(SimId unit, int index, UUID by, boolean unsafe) {
        Unit u = units.get(unit);
        if (u == null || index < 0 || index >= u.panel.size()) return;
        MaintenanceFaultKind kind = u.panel.source(index).kind();
        u.panel.repair(index, tick);
        if (kind == MaintenanceFaultKind.BEARING_WEAR && u.wear != null)
            u.wear.bearing().replace(Weibull.bearingLifeFactor(SimRandom.of(worldSeed, unit, WEAR_STREAM + ++u.repairs)));
        if (service.registered(unit))
            service.record(unit, new PassportEntry(tick, PassportEntry.Kind.MAINTENANCE_DONE, kind.name(), by));
        log(unit, unsafe ? OpsLogEntry.Kind.UNSAFE_MAINTENANCE : OpsLogEntry.Kind.MAINTENANCE_DONE, kind.name());
    }

    /** Частичный ремонт без сброса строки: подтяжка сальника {@code torque_wrench}; false — набивка выбрана. */
    public boolean tightenPacking(SimId unit, UUID by) {
        Unit u = units.get(unit);
        if (u == null || u.wear == null || u.wear.packing() == null || !u.wear.packing().tighten()) return false;
        log(unit, OpsLogEntry.Kind.MAINTENANCE_DONE, "PACKING_TIGHTENED");
        return true;
    }

    public List<OpsLogEntry> log(SimId unit) {
        Unit u = units.get(unit);
        return u == null ? List.of() : u.log();
    }

    // --- действие дефектов на модели-владельцы ---

    public double reliefLiftPa(SimId unit, double setPa) {
        return service.reliefLiftPa(unit, setPa);
    }

    public double burstFactor(SimId unit, double designFactor) {
        return service.burstFactor(unit, designFactor);
    }

    public boolean seamWeeping(SimId unit, double pressurePa) {
        return service.seamWeeping(unit, pressurePa);
    }

    // --- тик ---

    @Override
    public void stepFast(FastContext ctx) {
        if (ctx.config() != config) onConfigReload(ctx.config());
        tick = ctx.tick();
        if (ctx.clock().due(TimeDomain.SLOW, SLOW_PHASE)) {
            double dt = ctx.clock().slowDtPhys();
            for (Unit u : units.values()) {
                if (u.wear != null && u.wear.stepSlow(dt, u.running, u.dryRunning, u.waterPh))
                    log(u.id, OpsLogEntry.Kind.SEIZED, MaintenanceFaultKind.BEARING_WEAR.name());
                u.panel.setPassport(service.passport(u.id).isPresent());
                u.panel.update(tick);
            }
        }
        permits.step(tick);
        for (PermitRegistry.Transition t : permits.drainTransitions())
            log(SimId.NONE, OpsLogEntry.Kind.PERMIT_STATE, "#" + t.permit() + " " + t.type() + " " + t.from() + "->" + t.to() + " " + t.reason());
        for (OpsLogEntry e : pending) ctx.events().journal(e.tick(), e.unit(), severity(e.kind()), message(e));
        pending.clear();
    }

    private static Severity severity(OpsLogEntry.Kind k) {
        return switch (k) {
            case START_REFUSED, START_UNACCEPTED, DEFECT_FOUND, CHECK_FAILED, MAINTENANCE_REQUIRED, UNSAFE_MAINTENANCE ->
                    Severity.WARNING;
            case SEIZED, LOCKOUT_ADMIN_CUT, UNAUTHORIZED_ACCESS -> Severity.CRITICAL;
            case UNAUTHORIZED_WORK -> Severity.WARNING;
            default -> Severity.INFO;
        };
    }

    private static String message(OpsLogEntry e) {
        return e.kind().name() + (e.detail().isEmpty() ? "" : " " + e.detail());
    }

    /** Запись мира в журнал эксплуатации (LOTO): уходит в журнал мира на ближайшем тике. */
    public void journal(SimId device, OpsLogEntry.Kind kind, String detail) {
        log(device, kind, detail);
    }

    private void log(SimId unit, OpsLogEntry.Kind kind, String detail) {
        OpsLogEntry e = new OpsLogEntry(tick, unit, kind, detail);
        pending.add(e);
        Unit u = units.get(unit);
        if (u == null) return;
        u.log.addLast(e);
        while (u.log.size() > LOG_SIZE) u.log.removeFirst();
    }

    @Override
    public void onConfigReload(SimConfig cfgAll) {
        config = cfgAll;
        cfg = cfgAll.section(OpsConfig.class, cfg);
        service.reconfigure(effective(cfg));
    }

    /** Режим пусконаладки сменён на лету (сценарии, GameTest). */
    public void setConfig(OpsConfig cfg) {
        this.cfg = cfg;
        service.reconfigure(effective(cfg));
    }

    // --- сохранение ---

    /** Замки LOTO мира (13 §13.1). */
    public LockoutRegistry lockouts() {
        return lockouts;
    }

    /** Наряды-допуски мира (13 §13.2). */
    public PermitRegistry permits() {
        return permits;
    }

    /** Остановочный ремонт установки у стенда нарядов (13 §13.6.2); создаётся при первом обращении. */
    public Turnaround turnaround(GridPos board) {
        return turnarounds.computeIfAbsent(board.asLong(), k -> new Turnaround());
    }

    /** Стенд снят: его ремонт забывается. */
    public void forgetTurnaround(GridPos board) {
        turnarounds.remove(board.asLong());
    }

    @Override
    public void save(StateWriter out) {
        out.beginObject("lockouts");
        lockouts.save(out);
        out.endObject();
        out.beginObject("permits");
        permits.save(out);
        out.endObject();
        out.putInt("turnarounds", turnarounds.size());
        int t = 0;
        for (Map.Entry<Long, Turnaround> e : turnarounds.entrySet()) {
            out.beginObject("t" + t++);
            out.putLong("board", e.getKey());
            e.getValue().save(out);
            out.endObject();
        }
        out.beginObject("commissioning");
        service.save(out);
        out.endObject();
        out.beginObject("licences");
        licences.save(out);
        out.endObject();
        out.putInt("units", units.size());
        int i = 0;
        for (Unit u : units.values()) {
            out.beginObject("u" + i++);
            out.putLong("id", u.id.raw());
            out.putInt("kind", u.kind.ordinal());
            out.putInt("generation", u.generation);
            out.putInt("repairs", u.repairs);
            out.putDouble("trial_run_s", u.trialRunS);
            out.putBoolean("trial_armed", u.trialArmed);
            out.putBoolean("trial_rejected", u.trialRejected);
            if (u.wear != null) {
                out.beginObject("wear");
                u.wear.save(out);
                out.endObject();
            }
            int[] rows = new int[u.panel.size()];
            for (int r = 0; r < rows.length; r++) rows[r] = u.panel.source(r).kind().ordinal();
            out.putInts("rows", rows, rows.length);
            out.beginObject("panel");
            u.panel.save(out);
            out.endObject();
            out.endObject();
        }
    }

    @Override
    public void load(StateReader in) {
        units.clear();
        if (in.has("lockouts")) {
            in.beginObject("lockouts");
            lockouts.load(in);
            in.endObject();
        }
        if (in.has("permits")) {
            in.beginObject("permits");
            permits.load(in);
            in.endObject();
        }
        turnarounds.clear();
        int nt = in.optInt("turnarounds", 0);
        for (int t = 0; t < nt; t++) {
            in.beginObject("t" + t);
            Turnaround ta = new Turnaround();
            ta.load(in);
            turnarounds.put(in.getLong("board"), ta);
            in.endObject();
        }
        in.beginObject("commissioning");
        service.load(in);
        in.endObject();
        if (in.has("licences")) {
            in.beginObject("licences");
            licences.load(in);
            in.endObject();
        } else {
            licences.clear();
        }
        int n = in.getInt("units");
        for (int i = 0; i < n; i++) {
            in.beginObject("u" + i);
            SimId id = new SimId(in.getLong("id"));
            UnitKind kind = UnitKind.values()[in.getInt("kind")];
            Unit u = create(id, kind);
            u.generation = in.getInt("generation");
            u.repairs = in.getInt("repairs");
            u.trialRunS = in.getDouble("trial_run_s");
            u.trialArmed = in.getBoolean("trial_armed");
            u.trialRejected = in.getBoolean("trial_rejected");
            if (u.wear != null && in.has("wear")) {
                in.beginObject("wear");
                u.wear.load(in);
                in.endObject();
            }
            int[] rows = in.getInts("rows");
            for (int r = u.panel.size(); r < rows.length; r++) u.panel.add(new Detached(MaintenanceFaultKind.values()[rows[r]]));
            in.beginObject("panel");
            u.panel.load(in);
            in.endObject();
            in.endObject();
        }
    }

    /** События сервиса и панелей → журнал установки и мира. */
    private final class Events implements OpsEvents {
        @Override
        public void maintenanceRequired(SimId unit, MaintenanceFaultKind kind, int severity, int prev) {
            log(unit, OpsLogEntry.Kind.MAINTENANCE_REQUIRED, kind.name() + " " + prev + "->" + severity);
        }

        @Override
        public void commissioningStateChanged(SimId unit, CommissioningState from, CommissioningState to) {
            log(unit, OpsLogEntry.Kind.STATE, to.name());
            Unit u = units.get(unit);
            if (to == CommissioningState.ACCEPTED && u != null) licences.owner(unit).ifPresent(t -> licences.accepted(t, u.kind));
        }

        @Override
        public void defectFound(SimId unit, CommissioningDefect defect) {
            log(unit, OpsLogEntry.Kind.DEFECT_FOUND, defect.kind().name());
        }

        @Override
        public void startUnaccepted(SimId unit) {
            log(unit, OpsLogEntry.Kind.START_UNACCEPTED, "");
        }

        @Override
        public void startRefused(SimId unit, Set<CommissioningCheck> missing) {
            log(unit, OpsLogEntry.Kind.START_REFUSED, missing.toString());
        }
    }
}
