package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.combustion.FireTubeBoiler;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.SimRandom;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Ввод в эксплуатацию (13 §13.6.6): монтажные дефекты разыгрываются один раз при сборке (зерно — от id установки,
 * у заменённого компонента — от его id), проверки их находят, регулируемые исправляются в ходе самой проверки.
 * Приёмка — паспорт с базовыми показателями пробного пуска. Save/load — вместе с миром (этап 11).
 */
public final class CommissioningService {
    /** Поток {@link SimRandom} монтажных дефектов. */
    public static final int RANDOM_STREAM = 0x13_66;
    /** Металл при гидроиспытании холоднее, K. */
    public static final double HYDROTEST_MAX_METAL_K = 333;
    /** Допуск на поднятое пробное давление. */
    public static final double TEST_PRESSURE_TOLERANCE = 0.01;
    /** Посадка ПК в долях давления подрыва [моё]. */
    public static final double RESEAT_FRACTION = 0.93;
    /** Минимальный груз клети при испытании тормоза, доля номинального. */
    public static final double BRAKE_TEST_MIN_LOAD = 0.5;
    /** Шов после подчеканки. */
    public static final double CAULKED_WEEP = 1.3, CAULKED_BURST = 4.0;

    // Вероятности и диапазоны монтажных дефектов [BALANCE]; PSV_OFFSET и GAUGE_OFFSET — p моё.
    static final double P_SEAM = 0.3, P_PSV_STUCK = 0.15, P_PSV_OFFSET = 0.3, P_GAUGE = 0.3, P_GLASS = 0.2,
            P_BRAKE = 0.25, P_DASHPOT = 0.3;

    private static final class Unit {
        UnitAssembly asm;
        CommissioningState state = CommissioningState.NEW;
        final List<CommissioningDefect> defects = new ArrayList<>();
        final Map<CommissioningCheck, CheckResult> passed = new EnumMap<>(CommissioningCheck.class);
        final List<PassportEntry> history = new ArrayList<>();
        long formedTick, acceptedTick = -1;
        UUID acceptedBy;
        Baseline baseline = Baseline.NONE;
        boolean primed;
    }

    private OpsConfig cfg;
    private final OpsEvents events;
    private final long worldSeed;
    private final Map<SimId, Unit> units = new TreeMap<>();

    public CommissioningService(OpsConfig cfg, long worldSeed, OpsEvents events) {
        this.cfg = cfg;
        this.worldSeed = worldSeed;
        this.events = events;
    }

    public OpsConfig config() {
        return cfg;
    }

    /** Перезагрузка конфига: режим и параметры проверок действуют сразу, разыгранные дефекты остаются. */
    public void reconfigure(OpsConfig cfg) {
        this.cfg = cfg;
    }

    /** Установка снята из мира. */
    public void unregister(SimId unit) {
        units.remove(unit);
    }

    /** Состав установки, с которым она зарегистрирована. */
    public Optional<UnitAssembly> assembly(SimId unit) {
        Unit u = units.get(unit);
        return u == null ? Optional.empty() : Optional.of(u.asm);
    }

    /**
     * Установка собрана. В режиме {@code OFF} состояния нет и дефектов нет.
     *
     * @return разыгранные дефекты (скрыты до проверок)
     */
    public List<CommissioningDefect> register(UnitAssembly asm, long tick) {
        if (cfg.commissioning() == CommissioningMode.OFF) return List.of();
        return register(asm,
                rollDefects(asm, SimRandom.of(worldSeed, asm.unit(), RANDOM_STREAM), cfg.defectRate()), tick);
    }

    /** Установка с известным набором дефектов (загрузка, сценарии 11 §11.3). */
    public List<CommissioningDefect> register(UnitAssembly asm, List<CommissioningDefect> defects, long tick) {
        if (cfg.commissioning() == CommissioningMode.OFF) return List.of();
        Unit u = new Unit();
        u.asm = asm;
        u.formedTick = tick;
        u.defects.addAll(defects);
        units.put(asm.unit(), u);
        return List.copyOf(u.defects);
    }

    /**
     * Монтажные дефекты по компонентам в порядке {@link UnitComponent}; случайные величины тянутся всегда, так что
     * {@code defectRate} меняет только, какие из них сбылись. Детерминированные дефекты (реверс, пористость, сухой
     * насос) — состояние, которое создал игрок, — от {@code defectRate} не зависят.
     */
    public static List<CommissioningDefect> rollDefects(UnitAssembly asm, SplittableRandom rnd, double defectRate) {
        List<CommissioningDefect> out = new ArrayList<>();
        for (UnitComponent role : UnitComponent.values()) {
            SimId id = asm.components().get(role);
            if (id != null) rollComponent(asm, role, id, rnd, defectRate, out);
        }
        return out;
    }

    private static void rollComponent(UnitAssembly asm, UnitComponent role, SimId id, SplittableRandom rnd,
            double rate, List<CommissioningDefect> out) {
        switch (role) {
            case SHELL -> {
                double u = rnd.nextDouble(), kw = uniform(rnd, 1.05, 1.2), kb = uniform(rnd, 1.6, 2.5);
                if (u < P_SEAM * rate)
                    out.add(new CommissioningDefect(CommissioningDefectKind.SEAM_WEEP, id, kw, kb, false));
            }
            case RELIEF_VALVE -> {
                double u1 = rnd.nextDouble(), stuck = uniform(rnd, 1.6, 2.0);
                double u2 = rnd.nextDouble(), delta = uniform(rnd, -0.10, 0.15);
                if (u1 < P_PSV_STUCK * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.PSV_STUCK, id, stuck));
                else if (u2 < P_PSV_OFFSET * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.PSV_OFFSET, id, delta));
            }
            case PRESSURE_GAUGE -> {
                double u = rnd.nextDouble(), offset = uniform(rnd, 0, 0.08);
                if (u < P_GAUGE * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.GAUGE_OFFSET, id, offset));
            }
            case GAUGE_GLASS -> {
                if (rnd.nextDouble() < P_GLASS * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.GAUGE_GLASS_BLOCKED, id, 1));
            }
            case CYLINDER, STOP_VALVE -> {
                if (asm.porousCastings().contains(role))
                    out.add(CommissioningDefect.of(CommissioningDefectKind.CASTING_POROSITY, id, 1));
            }
            case GOVERNOR -> {
                double u = rnd.nextDouble();
                if (asm.dashpotFitted() && u < P_DASHPOT * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.DASHPOT_DRY, id, 1));
            }
            case DRIVE -> {
                if (asm.reversedRotation())
                    out.add(CommissioningDefect.of(CommissioningDefectKind.REVERSED_ROTATION, id, 1));
            }
            case PUMP -> out.add(CommissioningDefect.of(CommissioningDefectKind.DRY_PUMP, id, 1));
            case BRAKE -> {
                double u = rnd.nextDouble(), torque = uniform(rnd, 0.5, 0.9);
                if (u < P_BRAKE * rate)
                    out.add(CommissioningDefect.of(CommissioningDefectKind.BRAKE_MISADJUSTED, id, torque));
            }
        }
    }

    private static double uniform(SplittableRandom rnd, double lo, double hi) {
        return lo + (hi - lo) * rnd.nextDouble();
    }

    public boolean registered(SimId unit) {
        return units.containsKey(unit);
    }

    public Optional<CommissioningState> state(SimId unit) {
        Unit u = units.get(unit);
        return u == null ? Optional.empty() : Optional.of(u.state);
    }

    /** Действующие дефекты (найденные и скрытые); исправленные сюда не входят. */
    public List<CommissioningDefect> defects(SimId unit) {
        Unit u = units.get(unit);
        return u == null ? List.of() : List.copyOf(u.defects);
    }

    public Set<CommissioningCheck> passedChecks(SimId unit) {
        Unit u = units.get(unit);
        return u == null || u.passed.isEmpty() ? Set.of() : Set.copyOf(u.passed.keySet());
    }

    public CheckResult perform(SimId unit, CheckInput input, UUID by, long tick) {
        Unit u = require(unit);
        CommissioningCheck check = input.check();
        Set<CheckCondition> unmet = EnumSet.noneOf(CheckCondition.class);
        List<CommissioningDefect> found = new ArrayList<>();
        Map<String, Double> readings = new HashMap<>();
        CheckResult.Outcome outcome = CheckResult.Outcome.PASSED;
        if (!u.asm.kind().checklist().contains(check)) {
            unmet.add(CheckCondition.NOT_IN_CHECKLIST);
        } else {
            switch (input) {
                case CheckInput.PressureTest p -> outcome = pressureTest(u, p, unmet, found, readings);
                case CheckInput.ReliefValveSet r -> {
                    if (!r.controlGauge() && !u.passed.containsKey(CommissioningCheck.INSTRUMENT_CHECK))
                        unmet.add(CheckCondition.NO_CONTROL_GAUGE);
                    else reliefValveSet(u, found, readings);
                }
                case CheckInput.InstrumentCheck i -> {
                    CommissioningDefect g = fix(u, CommissioningDefectKind.GAUGE_OFFSET, found);
                    readings.put(CheckResult.READ_GAUGE_OFFSET, g == null ? 0 : g.magnitude());
                    fix(u, CommissioningDefectKind.GAUGE_GLASS_BLOCKED, found);
                }
                case CheckInput.RotationCheck r -> {
                    readings.put(CheckResult.READ_REVERSED, r.reversed() ? 1.0 : 0.0);
                    if (r.reversed()) {
                        CommissioningDefect d = find(u, CommissioningDefectKind.REVERSED_ROTATION);
                        if (d == null) {
                            d = CommissioningDefect.of(CommissioningDefectKind.REVERSED_ROTATION,
                                    u.asm.components().get(UnitComponent.DRIVE), 1);
                            u.defects.add(d);
                        }
                        found.add(markFound(u, d));
                        outcome = CheckResult.Outcome.FAILED;
                    } else {
                        fix(u, CommissioningDefectKind.REVERSED_ROTATION, found);
                    }
                }
                case CheckInput.Priming p -> {
                    if (!p.pumpStopped()) unmet.add(CheckCondition.UNIT_RUNNING);
                    else {
                        fix(u, CommissioningDefectKind.DRY_PUMP, found);
                        u.primed = true;
                    }
                }
                case CheckInput.BrakeTest b -> {
                    if (!(b.loadFraction() >= BRAKE_TEST_MIN_LOAD)) unmet.add(CheckCondition.LOAD_LOW);
                    else {
                        CommissioningDefect d = fix(u, CommissioningDefectKind.BRAKE_MISADJUSTED, found);
                        readings.put(CheckResult.READ_BRAKE_TORQUE, d == null ? 1 : d.magnitude());
                    }
                }
                case CheckInput.TrialRun t -> outcome = trialRun(u, t, unmet, found, readings);
            }
        }
        if (!unmet.isEmpty()) outcome = CheckResult.Outcome.REFUSED;
        CheckResult result = new CheckResult(check, outcome, unmet, found, readings, tick, by);
        if (outcome == CheckResult.Outcome.REFUSED) return result;
        for (CommissioningDefect d : found) events.defectFound(unit, d);
        if (u.state == CommissioningState.NEW) setState(u, CommissioningState.TESTING);
        if (outcome == CheckResult.Outcome.PASSED) {
            u.passed.put(check, result);
            if (input instanceof CheckInput.TrialRun t) u.baseline = t.baseline();
            if (u.passed.keySet().containsAll(u.asm.kind().checklist())) accept(u, by, tick);
        } else {
            u.passed.remove(check);
        }
        return result;
    }

    private CheckResult.Outcome pressureTest(Unit u, CheckInput.PressureTest p, Set<CheckCondition> unmet,
            List<CommissioningDefect> found, Map<String, Double> readings) {
        double pw = u.asm.workingPressurePa();
        double test = cfg.testPressurePa(pw);
        if (!p.fireOut()) unmet.add(CheckCondition.FIRE_LIT);
        if (!(p.metalTempK() < HYDROTEST_MAX_METAL_K)) unmet.add(CheckCondition.METAL_HOT);
        if (!(p.pressurePa() >= test * (1 - TEST_PRESSURE_TOLERANCE))) unmet.add(CheckCondition.PRESSURE_LOW);
        if (!(p.heldPhysS() >= cfg.hydrotestHoldPhysS())) unmet.add(CheckCondition.HOLD_SHORT);
        if (!unmet.isEmpty()) return CheckResult.Outcome.REFUSED;
        readings.put(CheckResult.READ_TEST_PRESSURE, p.pressurePa());
        boolean leak = false;
        for (CommissioningDefect d : List.copyOf(u.defects)) {
            if (d.kind() == CommissioningDefectKind.SEAM_WEEP && p.pressurePa() >= d.magnitude() * pw) {
                found.add(markFound(u, d));
                if (p.pressurePa() >= d.limit() * pw) readings.put(CheckResult.READ_BURST, 1.0);
                leak = true;
            } else if (d.kind() == CommissioningDefectKind.CASTING_POROSITY) {
                found.add(markFound(u, d));
                leak = true;
            }
        }
        return leak ? CheckResult.Outcome.FAILED : CheckResult.Outcome.PASSED;
    }

    private void reliefValveSet(Unit u, List<CommissioningDefect> found, Map<String, Double> readings) {
        double set = FireTubeBoiler.RELIEF_FACTOR * u.asm.workingPressurePa();
        double lift = reliefLiftPa(u.asm.unit(), set);
        readings.put(CheckResult.READ_LIFT, lift);
        readings.put(CheckResult.READ_RESEAT, RESEAT_FRACTION * lift);
        fix(u, CommissioningDefectKind.PSV_STUCK, found);
        fix(u, CommissioningDefectKind.PSV_OFFSET, found);
    }

    private CheckResult.Outcome trialRun(Unit u, CheckInput.TrialRun t, Set<CheckCondition> unmet,
            List<CommissioningDefect> found, Map<String, Double> readings) {
        for (CommissioningCheck c : u.asm.kind().checklist())
            if (c != CommissioningCheck.TRIAL_RUN && !u.passed.containsKey(c)) unmet.add(CheckCondition.PREREQUISITES);
        if (!(t.runS() >= cfg.trialRunS())) unmet.add(CheckCondition.RUN_SHORT);
        if (!(t.loadFraction() >= cfg.trialMinLoad())) unmet.add(CheckCondition.LOAD_LOW);
        if (!t.loadRejected()) unmet.add(CheckCondition.NO_LOAD_REJECTION);
        if (!unmet.isEmpty()) return CheckResult.Outcome.REFUSED;
        if (t.protectionTripped()) return CheckResult.Outcome.FAILED;
        // Качание оборотов при сбросе нагрузки выдаёт сухой катаракт; масло доливают тут же.
        readings.put(CheckResult.READ_HUNTING, fix(u, CommissioningDefectKind.DASHPOT_DRY, found) != null ? 1.0 : 0.0);
        return CheckResult.Outcome.PASSED;
    }

    private void accept(Unit u, UUID by, long tick) {
        if (u.state == CommissioningState.ACCEPTED) return;
        boolean first = u.acceptedTick < 0;
        if (first) {
            u.acceptedTick = tick;
            u.acceptedBy = by;
        }
        u.history.add(new PassportEntry(tick, first ? PassportEntry.Kind.ACCEPTED : PassportEntry.Kind.REACCEPTED,
                "", by));
        setState(u, CommissioningState.ACCEPTED);
    }

    /** Подчеканка шва {@code caulking_hammer} на пустом холодном котле: k_w → 1,3, k_b → 4. */
    public boolean caulk(SimId unit, boolean emptyAndCold) {
        Unit u = require(unit);
        CommissioningDefect d = find(u, CommissioningDefectKind.SEAM_WEEP);
        if (d == null || !emptyAndCold) return false;
        u.defects.set(u.defects.indexOf(d),
                new CommissioningDefect(d.kind(), d.component(), CAULKED_WEEP, CAULKED_BURST, d.found()));
        return true;
    }

    /** Срыв подачи (уровень водосборника ниже приёма): насос снова сухой. */
    public void losePrime(SimId unit) {
        Unit u = units.get(unit);
        if (u != null) u.primed = false;
    }

    /** Насос залит: заливка в ходе {@code PRIMING}; без пусконаладки ({@code OFF}) — всегда. */
    public boolean primed(SimId unit) {
        Unit u = units.get(unit);
        return u == null || u.primed;
    }

    public StartPermission canStart(SimId unit) {
        Unit u = units.get(unit);
        if (u == null || cfg.commissioning() == CommissioningMode.OFF) return StartPermission.FREE;
        Set<CommissioningCheck> missing = EnumSet.noneOf(CommissioningCheck.class);
        for (CommissioningCheck c : u.asm.kind().checklist()) if (!u.passed.containsKey(c)) missing.add(c);
        boolean accepted = u.state == CommissioningState.ACCEPTED;
        boolean allowed = cfg.commissioning() == CommissioningMode.ADVISORY
                || missing.isEmpty() || missing.equals(EnumSet.of(CommissioningCheck.TRIAL_RUN));
        return new StartPermission(allowed, accepted, missing);
    }

    /** Розжиг или пуск привода: {@link #canStart} и запись в журнал через события. */
    public StartPermission requestStart(SimId unit) {
        StartPermission p = canStart(unit);
        if (!p.allowed()) events.startRefused(unit, p.missing());
        else if (!p.accepted()) events.startUnaccepted(unit);
        return p;
    }

    /**
     * MOC или замена компонентов: у заменённых дефекты разыгрываются заново (зерно — от id компонента), сбрасываются
     * только покрывающие их проверки; принятая установка переходит в {@code MODIFIED}.
     */
    public void onModified(UnitAssembly updated, Set<SimId> changedComponents, UUID by, long tick) {
        Unit u = require(updated.unit());
        if (updated.kind() != u.asm.kind()) throw new IllegalArgumentException("unit kind changed");
        Set<CommissioningCheck> reset = EnumSet.noneOf(CommissioningCheck.class);
        for (UnitComponent role : UnitComponent.values()) {
            SimId id = updated.components().get(role);
            if (id == null || !changedComponents.contains(id)) continue;
            SimId old = u.asm.components().get(role);
            u.defects.removeIf(d -> d.component().equals(old) || d.component().equals(id));
            rollComponent(updated, role, id, SimRandom.of(worldSeed, id, RANDOM_STREAM), cfg.defectRate(),
                    u.defects);
            reset.add(role.coveredBy());
            if (role == UnitComponent.PUMP) u.primed = false;
            u.history.add(new PassportEntry(tick, PassportEntry.Kind.REPLACEMENT, role.name(), by));
        }
        u.asm = updated;
        reset.retainAll(u.asm.kind().checklist());
        if (reset.isEmpty()) return;
        for (CommissioningCheck c : reset) u.passed.remove(c);
        u.history.add(new PassportEntry(tick, PassportEntry.Kind.MOC, reset.toString(), by));
        if (u.state == CommissioningState.ACCEPTED) setState(u, CommissioningState.MODIFIED);
    }

    /** Запись {@code MAINTENANCE_DONE} и прочая история паспорта. */
    public void record(SimId unit, PassportEntry entry) {
        require(unit).history.add(entry);
    }

    public Optional<UnitPassport> passport(SimId unit) {
        Unit u = units.get(unit);
        if (u == null || u.acceptedTick < 0) return Optional.empty();
        return Optional.of(new UnitPassport(unit, u.asm.kind(), u.formedTick, u.acceptedTick, u.acceptedBy,
                u.passed, u.baseline, u.history));
    }

    // ---- Действие дефектов на модели-владельцы (биндинг мира) ----

    /** Давление подрыва ПК, Па: прикипевший — кратность × уставка, смещённый — (1 + δ) × уставка. */
    public double reliefLiftPa(SimId unit, double setPa) {
        Unit u = units.get(unit);
        if (u == null) return setPa;
        CommissioningDefect stuck = find(u, CommissioningDefectKind.PSV_STUCK);
        if (stuck != null) return stuck.magnitude() * setPa;
        CommissioningDefect off = find(u, CommissioningDefectKind.PSV_OFFSET);
        return off != null ? (1 + off.magnitude()) * setPa : setPa;
    }

    /** Давление разрыва обечайки в долях p_раб: k_b шва вместо конструкционного. */
    public double burstFactor(SimId unit, double designFactor) {
        Unit u = units.get(unit);
        CommissioningDefect d = u == null ? null : find(u, CommissioningDefectKind.SEAM_WEEP);
        return d == null ? designFactor : Math.min(designFactor, d.limit());
    }

    /** Шов «парит» при давлении p. */
    public boolean seamWeeping(SimId unit, double pressurePa) {
        Unit u = units.get(unit);
        CommissioningDefect d = u == null ? null : find(u, CommissioningDefectKind.SEAM_WEEP);
        return d != null && pressurePa >= d.magnitude() * u.asm.workingPressurePa();
    }

    /** Показание манометра со смещённым нулём, Па. */
    public double gaugeReading(SimId unit, double truePa, double scalePa) {
        Unit u = units.get(unit);
        CommissioningDefect d = u == null ? null : find(u, CommissioningDefectKind.GAUGE_OFFSET);
        return d == null ? truePa : truePa - d.magnitude() * scalePa;
    }

    public boolean has(SimId unit, CommissioningDefectKind kind) {
        Unit u = units.get(unit);
        return u != null && find(u, kind) != null;
    }

    /** Доля расчётного тормозного момента подъёма. */
    public double brakeTorqueFactor(SimId unit) {
        Unit u = units.get(unit);
        CommissioningDefect d = u == null ? null : find(u, CommissioningDefectKind.BRAKE_MISADJUSTED);
        return d == null ? 1 : d.magnitude();
    }

    // ---- Сохранение ----

    public void save(StateWriter out) {
        out.putInt("units", units.size());
        int i = 0;
        for (Unit u : units.values()) {
            out.beginObject("u" + i++);
            saveAssembly(out, u.asm);
            out.putInt("state", u.state.ordinal());
            out.putLong("formed", u.formedTick);
            out.putLong("accepted", u.acceptedTick);
            putUuid(out, "accepted_by", u.acceptedBy);
            out.putBoolean("primed", u.primed);
            Baseline b = u.baseline;
            out.putDoubles("baseline", new double[] {b.efficiency(), b.flueGasK(), b.headM(), b.bearingK(),
                    b.speedRadS(), b.dpPa()}, 6);
            saveDefects(out, "defects", u.defects);
            out.putInt("passed", u.passed.size());
            int j = 0;
            for (CheckResult r : u.passed.values()) {
                out.beginObject("p" + j++);
                out.putInt("check", r.check().ordinal());
                out.putLong("tick", r.tick());
                putUuid(out, "by", r.by());
                saveDefects(out, "found", r.found());
                String[] keys = r.readings().keySet().stream().sorted().toArray(String[]::new);
                out.putInt("readings", keys.length);
                for (int k = 0; k < keys.length; k++) {
                    out.putString("rk" + k, keys[k]);
                    out.putDouble("rv" + k, r.readings().get(keys[k]));
                }
                out.endObject();
            }
            out.putInt("history", u.history.size());
            for (int k = 0; k < u.history.size(); k++) {
                PassportEntry e = u.history.get(k);
                out.beginObject("h" + k);
                out.putLong("tick", e.tick());
                out.putInt("kind", e.kind().ordinal());
                out.putString("text", e.text());
                putUuid(out, "by", e.by());
                out.endObject();
            }
            out.endObject();
        }
    }

    public void load(StateReader in) {
        units.clear();
        int n = in.getInt("units");
        for (int i = 0; i < n; i++) {
            in.beginObject("u" + i);
            Unit u = new Unit();
            u.asm = loadAssembly(in);
            u.state = CommissioningState.values()[in.getInt("state")];
            u.formedTick = in.getLong("formed");
            u.acceptedTick = in.getLong("accepted");
            u.acceptedBy = getUuid(in, "accepted_by");
            u.primed = in.getBoolean("primed");
            double[] b = in.getDoubles("baseline");
            u.baseline = new Baseline(b[0], b[1], b[2], b[3], b[4], b[5]);
            u.defects.addAll(loadDefects(in, "defects"));
            int np = in.getInt("passed");
            for (int j = 0; j < np; j++) {
                in.beginObject("p" + j);
                CommissioningCheck c = CommissioningCheck.values()[in.getInt("check")];
                long tick = in.getLong("tick");
                UUID by = getUuid(in, "by");
                List<CommissioningDefect> found = loadDefects(in, "found");
                Map<String, Double> readings = new HashMap<>();
                int nr = in.getInt("readings");
                for (int k = 0; k < nr; k++) readings.put(in.getString("rk" + k), in.getDouble("rv" + k));
                u.passed.put(c, new CheckResult(c, CheckResult.Outcome.PASSED, Set.of(), found, readings, tick, by));
                in.endObject();
            }
            int nh = in.getInt("history");
            for (int k = 0; k < nh; k++) {
                in.beginObject("h" + k);
                u.history.add(new PassportEntry(in.getLong("tick"), PassportEntry.Kind.values()[in.getInt("kind")],
                        in.getString("text"), getUuid(in, "by")));
                in.endObject();
            }
            in.endObject();
            units.put(u.asm.unit(), u);
        }
    }

    private static void saveAssembly(StateWriter out, UnitAssembly a) {
        out.putLong("unit", a.unit().raw());
        out.putInt("kind", a.kind().ordinal());
        long[] comps = new long[UnitComponent.values().length];
        for (Map.Entry<UnitComponent, SimId> e : a.components().entrySet()) comps[e.getKey().ordinal()] = e.getValue().raw();
        out.putLongs("components", comps, comps.length);
        out.putDouble("p_work", a.workingPressurePa());
        out.putBoolean("reversed", a.reversedRotation());
        int porous = 0;
        for (UnitComponent c : a.porousCastings()) porous |= 1 << c.ordinal();
        out.putInt("porous", porous);
        out.putBoolean("dashpot", a.dashpotFitted());
    }

    private static UnitAssembly loadAssembly(StateReader in) {
        UnitKind kind = UnitKind.values()[in.getInt("kind")];
        long[] comps = in.getLongs("components");
        Map<UnitComponent, SimId> m = new EnumMap<>(UnitComponent.class);
        for (int i = 0; i < Math.min(comps.length, UnitComponent.values().length); i++)
            if (comps[i] != 0) m.put(UnitComponent.values()[i], new SimId(comps[i]));
        int porous = in.getInt("porous");
        Set<UnitComponent> p = EnumSet.noneOf(UnitComponent.class);
        for (UnitComponent c : UnitComponent.values()) if ((porous & 1 << c.ordinal()) != 0) p.add(c);
        return new UnitAssembly(new SimId(in.getLong("unit")), kind, m, in.getDouble("p_work"),
                in.getBoolean("reversed"), p, in.getBoolean("dashpot"));
    }

    private static void saveDefects(StateWriter out, String key, List<CommissioningDefect> defects) {
        int n = defects.size();
        int[] kind = new int[n], found = new int[n];
        long[] comp = new long[n];
        double[] mag = new double[n], limit = new double[n];
        for (int i = 0; i < n; i++) {
            CommissioningDefect d = defects.get(i);
            kind[i] = d.kind().ordinal();
            comp[i] = d.component().raw();
            mag[i] = d.magnitude();
            limit[i] = d.limit();
            found[i] = d.found() ? 1 : 0;
        }
        out.beginObject(key);
        out.putInts("kind", kind, n);
        out.putLongs("component", comp, n);
        out.putDoubles("magnitude", mag, n);
        out.putDoubles("limit", limit, n);
        out.putInts("found", found, n);
        out.endObject();
    }

    private static List<CommissioningDefect> loadDefects(StateReader in, String key) {
        in.beginObject(key);
        int[] kind = in.getInts("kind"), found = in.getInts("found");
        long[] comp = in.getLongs("component");
        double[] mag = in.getDoubles("magnitude"), limit = in.getDoubles("limit");
        in.endObject();
        List<CommissioningDefect> out = new ArrayList<>(kind.length);
        for (int i = 0; i < kind.length; i++)
            out.add(new CommissioningDefect(CommissioningDefectKind.values()[kind[i]], new SimId(comp[i]), mag[i],
                    limit[i], found[i] != 0));
        return out;
    }

    private static void putUuid(StateWriter out, String key, UUID id) {
        out.putLongs(key, id == null ? new long[0] : new long[] {id.getMostSignificantBits(),
                id.getLeastSignificantBits()}, id == null ? 0 : 2);
    }

    private static UUID getUuid(StateReader in, String key) {
        long[] a = in.getLongs(key);
        return a.length == 2 ? new UUID(a[0], a[1]) : null;
    }

    private Unit require(SimId unit) {
        Unit u = units.get(unit);
        if (u == null) throw new IllegalStateException("unit not registered: " + unit);
        return u;
    }

    private static CommissioningDefect find(Unit u, CommissioningDefectKind kind) {
        for (CommissioningDefect d : u.defects) if (d.kind() == kind) return d;
        return null;
    }

    private static CommissioningDefect markFound(Unit u, CommissioningDefect d) {
        CommissioningDefect f = d.asFound();
        u.defects.set(u.defects.indexOf(d), f);
        return f;
    }

    /** Дефект найден и исправлен регулировкой в ходе проверки. */
    private static CommissioningDefect fix(Unit u, CommissioningDefectKind kind, List<CommissioningDefect> found) {
        CommissioningDefect d = find(u, kind);
        if (d == null) return null;
        u.defects.remove(d);
        CommissioningDefect f = d.asFound();
        found.add(f);
        return f;
    }

    private void setState(Unit u, CommissioningState to) {
        CommissioningState from = u.state;
        if (from == to) return;
        u.state = to;
        events.commissioningStateChanged(u.asm.unit(), from, to);
    }
}
