package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimClock;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Наряды-допуски станции (13 §13.2, домен {@code PermitService} §13.12.1): выдача, автомат состояний §13.2.4,
 * газоанализ §13.2.3, сроки, бригада и её доза (наряд {@code RADIATION}, 04b), выполненный объём работ. Оригинал наряда
 * живёт здесь; бланк у игрока — ссылка на номер (§13.2.2).
 *
 * <p>Одиночная игра (§13.0.3 п. 4): роли совмещены, черновик подписывается выдающим сразу — наряд создаётся в
 * {@link PermitState#ISSUED}. Сроки нарядов — реальные секунды (тики FAST, §13.0.3 п. 5).
 *
 * <p>Автомат (§13.2.4):
 * <pre>
 * ISSUED ─изоляция и проверка→ ISOLATED ─газоанализ в норме→ GAS_TESTED ─допуск→ ACTIVE ─работы окончены→ WORK_COMPLETE
 *                                  └────────── допуск, если газоанализ не нужен ──────────┘          │ замки сняты
 * ACTIVE ─тревога газа, срок повторного анализа, срок наряда→ SUSPENDED ─повторный анализ→ GAS_TESTED        CLOSED
 * GAS_TESTED ─тревога газа→ ISOLATED;  ISSUED…SUSPENDED ─срок истёк→ EXPIRED (ACTIVE — сначала SUSPENDED)
 * </pre>
 */
public final class PermitRegistry {
    /** Тиков FAST в секунде реального времени. */
    public static final double TICKS_PER_S = 1 / SimClock.FAST_DT;
    /** {@code ops.hot_work_max_s} — срок наряда {@code HOT}, с (§13.14). */
    public static final double HOT_WORK_MAX_S = 14400;
    /** {@code ops.shift_length_s} — срок остальных нарядов «смена», с (§13.2.1, §13.14). */
    public static final double SHIFT_S = 3600;
    /** {@code ops.gas_retest_s} — повторный газоанализ, с (§13.2.3). */
    public static final double GAS_RETEST_S = 7200;
    /** {@code ops.hot_work_radius_m} — зона огневых работ, м (35 футов OSHA 29 CFR 1910.252, §13.2.5). */
    public static final double HOT_WORK_RADIUS_M = 11;
    /** {@code ops.bump_test_max_age_s} — давность проверки прибора газом, с (§13.2.3). */
    public static final double BUMP_TEST_MAX_AGE_S = 86400;

    /** Ответ на действие с нарядом. */
    public enum Result {
        OK, UNKNOWN, WRONG_STATE, NOT_ISOLATED, NO_GAS_TEST, GAS_STALE, GAS_FAILED, NO_FIRE_WATCH, WORK_NOT_DONE,
        LOCKS_REMAIN, EXPIRED
    }

    /** Причина перехода (поле {@code reason} события {@code pc_permit_state}, §13.11.2). */
    public enum Reason { ISSUED, ISOLATED, GAS_OK, GAS_FAILED, ADMITTED, GAS_ALARM, GAS_RETEST_DUE, TIME_EXPIRED, WORK_DONE, CLOSED, CANCELLED }

    /** Смена состояния для журнала. */
    public record Transition(long tick, long permit, PermitType type, PermitState from, PermitState to, Reason reason) {
    }

    /** Наряд-допуск (GDD: {@code WorkPermit} §13.12.1; имя занято электрическим нарядом {@code sim.grid}). */
    public static final class Permit {
        final long id;
        final PermitType type;
        final GridPos target;
        final double radiusM;
        final String targetKind;
        final UUID issuer;
        final String issuerName;
        final long issuedTick, validToTick;
        PermitState state = PermitState.ISSUED;
        GasTest gas;
        long completedTick = -1, closedTick = -1;
        double workS, requiredWorkS;
        final Map<UUID, String> crew = new TreeMap<>();
        final Map<UUID, Double> doseSv = new TreeMap<>();

        Permit(long id, PermitType type, GridPos target, double radiusM, String targetKind, UUID issuer, String issuerName,
                long issuedTick, long validToTick) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.radiusM = radiusM;
            this.targetKind = targetKind;
            this.issuer = issuer;
            this.issuerName = issuerName;
            this.issuedTick = issuedTick;
            this.validToTick = validToTick;
        }

        public long id() {
            return id;
        }

        public PermitType type() {
            return type;
        }

        public PermitState state() {
            return state;
        }

        /** Объект работ (контроллер блока). */
        public GridPos target() {
            return target;
        }

        /** Радиус объёма работ вокруг объекта, м. */
        public double radiusM() {
            return radiusM;
        }

        /** Вид объекта (идентификатор блока) — для заданий и достижений. */
        public String targetKind() {
            return targetKind;
        }

        public UUID issuer() {
            return issuer;
        }

        public String issuerName() {
            return issuerName;
        }

        public long issuedTick() {
            return issuedTick;
        }

        public long validToTick() {
            return validToTick;
        }

        /** Последний принятый замер; {@code null} — замера нет. */
        public GasTest gas() {
            return gas;
        }

        public long completedTick() {
            return completedTick;
        }

        public long closedTick() {
            return closedTick;
        }

        /** Наработка бригады в объёме работ, с. */
        public double workS() {
            return workS;
        }

        /** Сколько работать до «работы окончены», с. */
        public double requiredWorkS() {
            return requiredWorkS;
        }

        /** Члены бригады: ник по UUID. */
        public Map<UUID, String> crew() {
            return Collections.unmodifiableMap(crew);
        }

        /** Доза каждого члена бригады за время наряда, Зв. */
        public Map<UUID, Double> doses() {
            return Collections.unmodifiableMap(doseSv);
        }

        /** Коллективная доза бригады, Зв (чел·Зв). */
        public double collectiveDoseSv() {
            double s = 0;
            for (double d : doseSv.values()) s += d;
            return s;
        }

        /** Точка в объёме работ. */
        public boolean covers(GridPos p) {
            return target.distanceSq(p) <= radiusM * radiusM;
        }

        /** Замер не старше {@code ops.gas_retest_s}. */
        public boolean gasFresh(long tick) {
            return gas != null && tick - gas.tick() <= GAS_RETEST_S * TICKS_PER_S;
        }
    }

    private final Map<Long, Permit> permits = new TreeMap<>();
    private final List<Transition> pending = new ArrayList<>();
    private final GasLimits limits;
    private long nextId = 1;

    public PermitRegistry() {
        this(GasLimits.DEFAULT);
    }

    public PermitRegistry(GasLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    public GasLimits limits() {
        return limits;
    }

    /** Срок наряда вида, с (§13.2.1). */
    public static double maxDurationS(PermitType type) {
        return type == PermitType.HOT ? HOT_WORK_MAX_S : SHIFT_S;
    }

    /**
     * Выписать наряд на объект: номер по сквозному журналу, состояние {@code ISSUED}.
     *
     * @param radiusM       объём работ вокруг объекта, м
     * @param targetKind    вид объекта (идентификатор блока)
     * @param requiredWorkS сколько бригаде работать в объёме до окончания работ, с (0 — не учитывается)
     */
    public Permit issue(PermitType type, GridPos target, double radiusM, String targetKind, UUID issuer, String issuerName,
            double requiredWorkS, long tick) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        if (!(radiusM > 0)) throw new IllegalArgumentException("radius: " + radiusM);
        long valid = tick + Math.round(maxDurationS(type) * TICKS_PER_S);
        Permit p = new Permit(nextId++, type, target, radiusM, targetKind == null ? "" : targetKind, issuer,
                issuerName == null ? "" : issuerName, tick, valid);
        p.requiredWorkS = Math.max(0, requiredWorkS);
        permits.put(p.id, p);
        pending.add(new Transition(tick, p.id, type, PermitState.DRAFT, PermitState.ISSUED, Reason.ISSUED));
        return p;
    }

    public Permit permit(long id) {
        return permits.get(id);
    }

    public Collection<Permit> all() {
        return Collections.unmodifiableCollection(permits.values());
    }

    /** Действующий ({@code ACTIVE}) наряд вида, в объём которого попадает точка; {@code null} — нет. */
    public Permit activeCovering(PermitType type, GridPos p) {
        for (Permit x : permits.values())
            if (x.type == type && x.state == PermitState.ACTIVE && x.covers(p)) return x;
        return null;
    }

    /** Открытых нарядов с объектом в радиусе {@code r} от центра. */
    public int openNear(GridPos center, double r) {
        int n = 0;
        for (Permit x : permits.values()) if (x.state.open() && x.target.distanceSq(center) <= r * r) n++;
        return n;
    }

    /** Нарядов с объектом в радиусе, работы по которым окончены начиная с тика {@code since}. */
    public int completedNear(GridPos center, double r, long since) {
        int n = 0;
        for (Permit x : permits.values())
            if (x.completedTick >= 0 && x.completedTick >= since && x.target.distanceSq(center) <= r * r) n++;
        return n;
    }

    // --- автомат §13.2.4 ---

    /** Изоляция по плану §13.1.4 выполнена и проверена ({@code isolationComplete} считает мир). */
    public Result isolate(long id, boolean isolationComplete, long tick) {
        Permit p = permits.get(id);
        if (p == null) return Result.UNKNOWN;
        if (p.state != PermitState.ISSUED) return Result.WRONG_STATE;
        if (!isolationComplete) return Result.NOT_ISOLATED;
        move(p, PermitState.ISOLATED, Reason.ISOLATED, tick);
        return Result.OK;
    }

    /**
     * Занести замер в наряд (§13.2.3): {@code ISOLATED}/{@code SUSPENDED} → {@code GAS_TESTED}, если в норме; у
     * {@code GAS_TESTED} плохой замер возвращает наряд в {@code ISOLATED}; у {@code ACTIVE} хороший — продлевает срок
     * повторного анализа, плохой — приостанавливает наряд.
     *
     * @return отклонения; пусто — замер принят
     */
    public Set<GasLimits.Fault> gasTest(long id, GasTest t, PermitEnforcement mode, long tick) {
        Permit p = permits.get(id);
        if (p == null || p.state.terminal()) return EnumSet.noneOf(GasLimits.Fault.class);
        Set<GasLimits.Fault> faults = limits.faults(p.type, t, mode == PermitEnforcement.ENFORCED);
        boolean ok = faults.isEmpty();
        if (ok) p.gas = t;
        switch (p.state) {
            case ISOLATED, SUSPENDED -> {
                if (ok) move(p, PermitState.GAS_TESTED, Reason.GAS_OK, tick);
            }
            case GAS_TESTED -> {
                if (!ok) {
                    p.gas = null;
                    move(p, PermitState.ISOLATED, Reason.GAS_FAILED, tick);
                }
            }
            case ACTIVE -> {
                if (!ok) move(p, PermitState.SUSPENDED, Reason.GAS_FAILED, tick);
            }
            default -> {
            }
        }
        return faults;
    }

    /**
     * Допуск бригады: из {@code GAS_TESTED} (или {@code ISOLATED}, если газоанализ виду не нужен) в {@code ACTIVE}.
     * Огневым работам нужен свежий замер и пожарный пост ({@code fireWatch}: игрок с огнетушителем в зоне).
     */
    public Result admit(long id, UUID member, String name, boolean fireWatch, long tick) {
        Permit p = permits.get(id);
        if (p == null) return Result.UNKNOWN;
        boolean ready = p.state == PermitState.GAS_TESTED || p.state == PermitState.ISOLATED && !p.type.needsGasTest();
        if (!ready) {
            if (p.state == PermitState.ISSUED) return Result.NOT_ISOLATED;
            if (p.state == PermitState.ISOLATED || p.state == PermitState.SUSPENDED) return Result.NO_GAS_TEST;
            return Result.WRONG_STATE;
        }
        if (p.type.needsGasTest() && !p.gasFresh(tick)) return Result.GAS_STALE;
        if (p.type.needsFireWatch() && !fireWatch) return Result.NO_FIRE_WATCH;
        join(id, member, name);
        move(p, PermitState.ACTIVE, Reason.ADMITTED, tick);
        return Result.OK;
    }

    /** Подпись члена бригады: ознакомлен, может находиться в объёме работ. */
    public void join(long id, UUID member, String name) {
        Permit p = permits.get(id);
        if (p == null || member == null || p.state.terminal()) return;
        p.crew.put(member, name == null ? "" : name);
        p.doseSv.putIfAbsent(member, 0.0);
    }

    /**
     * Тревога газа, ПАЗ или пожар в объёме работ: {@code ACTIVE} → {@code SUSPENDED}, {@code GAS_TESTED} →
     * {@code ISOLATED} в том же тике (T-13-08).
     */
    public boolean gasAlarm(long id, long tick) {
        Permit p = permits.get(id);
        if (p == null) return false;
        if (p.state == PermitState.ACTIVE) {
            move(p, PermitState.SUSPENDED, Reason.GAS_ALARM, tick);
            return true;
        }
        if (p.state == PermitState.GAS_TESTED) {
            p.gas = null;
            move(p, PermitState.ISOLATED, Reason.GAS_ALARM, tick);
            return true;
        }
        return false;
    }

    /** Работы окончены: из {@code ACTIVE}, если бригада наработала положенное. */
    public Result complete(long id, long tick) {
        Permit p = permits.get(id);
        if (p == null) return Result.UNKNOWN;
        if (p.state != PermitState.ACTIVE) return Result.WRONG_STATE;
        if (p.workS < p.requiredWorkS) return Result.WORK_NOT_DONE;
        p.completedTick = tick;
        move(p, PermitState.WORK_COMPLETE, Reason.WORK_DONE, tick);
        return Result.OK;
    }

    /** Закрыть: все замки объекта сняты, оборудование сдано допускающему. */
    public Result close(long id, boolean locksRemain, long tick) {
        Permit p = permits.get(id);
        if (p == null) return Result.UNKNOWN;
        if (p.state != PermitState.WORK_COMPLETE) return Result.WRONG_STATE;
        if (locksRemain) return Result.LOCKS_REMAIN;
        p.closedTick = tick;
        move(p, PermitState.CLOSED, Reason.CLOSED, tick);
        return Result.OK;
    }

    /** Отменить наряд, по которому бригада ещё не допускалась. */
    public Result cancel(long id, long tick) {
        Permit p = permits.get(id);
        if (p == null) return Result.UNKNOWN;
        if (p.state.terminal() || p.state == PermitState.ACTIVE || p.state == PermitState.WORK_COMPLETE) return Result.WRONG_STATE;
        move(p, PermitState.CANCELLED, Reason.CANCELLED, tick);
        return Result.OK;
    }

    /** Наработка бригады за {@code seconds} (мир решает, идёт ли работа: люди в объёме, объект остановлен). */
    public void addWork(long id, double seconds) {
        Permit p = permits.get(id);
        if (p != null && p.state == PermitState.ACTIVE && seconds > 0) p.workS += seconds;
    }

    /** Доза члена бригады в объёме работ, Зв (учёт дозы по наряду {@code RADIATION}, 04b §4b.12). */
    public void addDose(long id, UUID member, double sv) {
        Permit p = permits.get(id);
        if (p == null || p.state.terminal() || !(sv > 0) || !p.crew.containsKey(member)) return;
        p.doseSv.merge(member, sv, Double::sum);
    }

    /**
     * Сроки (§13.2.3, §13.2.4): у {@code ACTIVE} огневого наряда без свежего замера и у {@code ACTIVE} с истёкшим сроком
     * — {@code SUSPENDED}; прочие открытые с истёкшим сроком — {@code EXPIRED}.
     */
    public void step(long tick) {
        for (Permit p : permits.values()) {
            if (p.state.terminal() || p.state == PermitState.WORK_COMPLETE) continue;
            boolean expired = tick > p.validToTick;
            if (p.state == PermitState.ACTIVE) {
                if (expired) move(p, PermitState.SUSPENDED, Reason.TIME_EXPIRED, tick);
                else if (p.type.needsGasTest() && !p.gasFresh(tick)) move(p, PermitState.SUSPENDED, Reason.GAS_RETEST_DUE, tick);
            } else if (expired) {
                move(p, PermitState.EXPIRED, Reason.TIME_EXPIRED, tick);
            }
        }
    }

    private void move(Permit p, PermitState to, Reason why, long tick) {
        PermitState from = p.state;
        if (from == to) return;
        p.state = to;
        pending.add(new Transition(tick, p.id, p.type, from, to, why));
    }

    /** Смены состояний с прошлого вызова (для журнала и событий). */
    public List<Transition> drainTransitions() {
        if (pending.isEmpty()) return List.of();
        List<Transition> out = List.copyOf(pending);
        pending.clear();
        return out;
    }

    // --- сохранение ---

    public void save(StateWriter out) {
        out.putLong("next", nextId);
        out.putInt("n", permits.size());
        int i = 0;
        for (Permit p : permits.values()) {
            out.beginObject("p" + i++);
            out.putLong("id", p.id);
            out.putInt("type", p.type.ordinal());
            out.putInt("state", p.state.ordinal());
            out.putLong("target", p.target.asLong());
            out.putDouble("radius", p.radiusM);
            out.putString("kind", p.targetKind);
            out.putString("issuer", p.issuer == null ? "" : p.issuer.toString());
            out.putString("issuer_name", p.issuerName);
            out.putLong("issued", p.issuedTick);
            out.putLong("valid_to", p.validToTick);
            out.putLong("completed", p.completedTick);
            out.putLong("closed", p.closedTick);
            out.putDouble("work_s", p.workS);
            out.putDouble("required_s", p.requiredWorkS);
            if (p.gas != null) {
                out.beginObject("gas");
                GasTest g = p.gas;
                out.putLong("tick", g.tick());
                out.putString("by", g.by() == null ? "" : g.by().toString());
                out.putString("instrument", g.instrument());
                out.putBoolean("bump", g.bumpTested());
                out.putDoubles("x", new double[] {g.o2(), g.lelFraction(), g.h2sPpm(), g.coPpm()}, 4);
                out.endObject();
            }
            out.putInt("crew", p.crew.size());
            int k = 0;
            for (Map.Entry<UUID, String> e : p.crew.entrySet()) {
                out.beginObject("c" + k++);
                out.putString("id", e.getKey().toString());
                out.putString("name", e.getValue());
                out.putDouble("dose", p.doseSv.getOrDefault(e.getKey(), 0.0));
                out.endObject();
            }
            out.endObject();
        }
    }

    public void load(StateReader in) {
        permits.clear();
        pending.clear();
        nextId = Math.max(1, in.optLong("next", 1));
        int n = in.optInt("n", 0);
        for (int i = 0; i < n; i++) {
            in.beginObject("p" + i);
            String issuer = in.optString("issuer", "");
            Permit p = new Permit(in.getLong("id"), PermitType.values()[in.getInt("type")], GridPos.fromLong(in.getLong("target")),
                    in.getDouble("radius"), in.optString("kind", ""), issuer.isEmpty() ? null : UUID.fromString(issuer),
                    in.optString("issuer_name", ""), in.getLong("issued"), in.getLong("valid_to"));
            p.state = PermitState.values()[in.getInt("state")];
            p.completedTick = in.optLong("completed", -1);
            p.closedTick = in.optLong("closed", -1);
            p.workS = in.optDouble("work_s", 0);
            p.requiredWorkS = in.optDouble("required_s", 0);
            if (in.has("gas")) {
                in.beginObject("gas");
                String by = in.optString("by", "");
                double[] x = in.getDoubles("x");
                p.gas = new GasTest(in.getLong("tick"), by.isEmpty() ? null : UUID.fromString(by), in.optString("instrument", ""),
                        in.getBoolean("bump"), x[0], x[1], x[2], x[3]);
                in.endObject();
            }
            int c = in.optInt("crew", 0);
            for (int k = 0; k < c; k++) {
                in.beginObject("c" + k);
                UUID id = UUID.fromString(in.getString("id"));
                p.crew.put(id, in.optString("name", ""));
                p.doseSv.put(id, in.optDouble("dose", 0));
                in.endObject();
            }
            in.endObject();
            permits.put(p.id, p);
        }
    }
}
