package dev.denfry.promptcritical.sim.alarm;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Движок сигнализации по ISA-18.2 (03c §3c.6): автомат состояний §3c.6.2 с задержками возникновения и снятия, зоной
 * нечувствительности и самоподхватом ({@code latching}), повторная сигнализация P1, дребезг, шельфинг, подавление по
 * группам (встроенная {@link #POST_TRIP} после АЗ, §3c.6.4), вывод из работы, первопричина (§3c.2.7), сирена с
 * гашением и метрики лавины EEMUA 191 (§3c.6.5).
 *
 * <p>Движок работает по <b>измеренным</b> значениям ({@link Inputs}), а не по истинным (§3c.6.2): отказавший датчик
 * может дать ложную сигнализацию или скрыть настоящую. Время — физическое, секунды, его передаёт владелец
 * ({@link #step}); движок детерминирован и не знает Minecraft.
 *
 * <p>Константы — из 03c: повторная сигнализация P1 через {@value #REALARM_P1_S} с ({@code scada.realarm_p1_s}), дребезг —
 * {@value #CHATTER_COUNT} срабатывания за {@value #CHATTER_WINDOW_S} с, лавина — больше {@value #FLOOD_ENTER} за 10 мин
 * с выходом ниже {@value #FLOOD_EXIT} (03b §3b.2.14), стоящая — активна дольше суток, шельфинг — до
 * {@value #MAX_SHELVE_S} с ({@code scada.max_shelve_s}), {@code POST_TRIP} — через {@value #POST_TRIP_DELAY_S} с после
 * АЗ на {@value #POST_TRIP_S} с (T-3c-06).
 */
public final class AlarmEngine {
    public static final double REALARM_P1_S = 1800, CHATTER_WINDOW_S = 60, FLOOD_WINDOW_S = 600, STANDING_S = 86_400,
            MAX_SHELVE_S = 28_800, POST_TRIP_DELAY_S = 2, POST_TRIP_S = 1800, FIRST_OUT_WINDOW_S = 2, MAX_CHATTER_DELAY_S = 60;
    public static final int CHATTER_COUNT = 3, FLOOD_ENTER = 10, FLOOD_EXIT = 5;
    /** Встроенная группа подавления «после АЗ» (03c §3c.6.4). */
    public static final String POST_TRIP = "POST_TRIP";
    /** Сколько последних возникновений помнит окно лавины. */
    static final int RING = 1024;
    private static final double EPS = 1e-9;

    /** Измеренное значение тега; NaN — значения нет (качество {@code BAD}). */
    @FunctionalInterface
    public interface Inputs {
        double measured(String tag);
    }

    @FunctionalInterface
    public interface Listener {
        void onEvent(AlarmEvent e);
    }

    private final boolean autoChatterDelay;
    private final Map<Integer, Alarm> byId = new LinkedHashMap<>();
    private final Map<String, Alarm> byKey = new HashMap<>();
    private final TreeSet<String> suppressedGroups = new TreeSet<>();
    private final Map<String, Double> lastGroupRaise = new HashMap<>();
    private final double[] ring = new double[RING];
    private int ringHead, ringSize, peak, nextId = 1;
    private double now, silenceT = Double.NEGATIVE_INFINITY, postTripOnAt = Double.NaN, postTripOffAt = Double.NaN,
            floodSince = Double.NaN;
    private boolean flood;
    private Listener listener = e -> {
    };

    /** @param autoChatterDelay {@code scada.auto_chatter_delay}: дребезжащей удваивать задержку возникновения (до 60 с) */
    public AlarmEngine(boolean autoChatterDelay) {
        this.autoChatterDelay = autoChatterDelay;
    }

    public AlarmEngine() {
        this(false);
    }

    public void setListener(Listener l) {
        listener = l == null ? e -> {
        } : l;
    }

    public double now() {
        return now;
    }

    // ------------------------------------------------------------------ описание

    /** Новая сигнализация; номер выдаёт движок. {@code program} — объявлена программой (её можно удалить). */
    public int define(AlarmDefinition d, boolean program) {
        int id = nextId++;
        Alarm a = new Alarm(d.withId(id), program);
        byId.put(id, a);
        if (d.kind() != AlarmKind.SOFT) byKey.putIfAbsent(d.key(), a);
        if (!d.group().isEmpty() && suppressedGroups.contains(d.group())) a.state = AlarmState.SUPPRESSED;
        return id;
    }

    /**
     * Встроенная сигнализация по ключу «тег и вид» (03c §3c.6.3): есть — обновить описание, сохранив состояние и номер;
     * нет — создать.
     */
    public int upsert(AlarmDefinition d) {
        Alarm a = byKey.get(d.key());
        if (a == null) return define(d, false);
        a.def = d.withId(a.id());
        if (!a.chattering) a.onDelayS = d.onDelayS();
        return a.id();
    }

    /** Удалить сигнализацию (встроенную — когда её источник исчез). */
    public boolean remove(int id) {
        Alarm a = byId.remove(id);
        if (a == null) return false;
        byKey.remove(a.def.key(), a);
        return true;
    }

    /** Удалить программную сигнализацию ({@code deleteAlarm}); встроенную — нельзя. */
    public boolean deleteProgram(int id) {
        Alarm a = byId.get(id);
        if (a == null) return false;
        if (!a.program) throw new AlarmAccessException("E_ACCESS: only program alarms can be deleted");
        return remove(id);
    }

    public Alarm get(int id) {
        return byId.get(id);
    }

    public Alarm find(String key) {
        return byKey.get(key);
    }

    /** Все сигнализации в порядке номеров. */
    public Collection<Alarm> alarms() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int size() {
        return byId.size();
    }

    // ------------------------------------------------------------------ шаг

    /** Шаг на момент {@code t}, с: таймеры, условия по измеренным значениям, повторная сигнализация, лавина. */
    public void step(double t, Inputs in) {
        now = t;
        if (!Double.isNaN(postTripOnAt) && t >= postTripOnAt - EPS) {
            postTripOnAt = Double.NaN;
            postTripOffAt = t + POST_TRIP_S;
            setSuppression(POST_TRIP, true);
        }
        if (!Double.isNaN(postTripOffAt) && t >= postTripOffAt - EPS) {
            postTripOffAt = Double.NaN;
            setSuppression(POST_TRIP, false);
        }
        for (Alarm a : byId.values()) {
            if (a.state == AlarmState.SHELVED && t >= a.shelvedUntil - EPS) leaveHidden(a, t);
            evaluate(a, in, t);
            if (a.state == AlarmState.ACKED && a.def.priority() == 1 && a.cond && t - a.tAck >= REALARM_P1_S - EPS) raise(a, t, false);
            if (a.chattering && (Double.isNaN(a.raises[0]) || t - a.raises[0] > CHATTER_WINDOW_S + EPS)) a.chattering = false;
        }
        updateFlood(t);
    }

    private void evaluate(Alarm a, Inputs in, double t) {
        AlarmDefinition d = a.def;
        // значение программной сигнализации — то, что передала программа в raise
        double v = d.kind() == AlarmKind.SOFT ? a.value : in.measured(d.tag());
        if (d.kind() == AlarmKind.RATE_OF_CHANGE && !Double.isNaN(v) && !Double.isNaN(a.prevValue) && t > a.prevT)
            a.rate = (v - a.prevValue) / (t - a.prevT);
        if (!Double.isNaN(v)) {
            a.prevValue = v;
            a.prevT = t;
        }
        a.value = v;
        int raw = raw(a, v);
        boolean target = raw < 0 ? a.cond : raw > 0;
        if (target == a.cond) {
            a.pendingSince = Double.NaN;
            return;
        }
        if (Double.isNaN(a.pendingSince)) a.pendingSince = t;
        double delay = a.cond ? d.offDelayS() : a.onDelayS;
        if (t - a.pendingSince >= delay - EPS) {
            a.pendingSince = Double.NaN;
            if (target) condOn(a, t);
            else condOff(a, t);
        }
    }

    /** Сырое условие: 1 — есть, 0 — нет, −1 — неизвестно (нет значения), держать прежнее. */
    private static int raw(Alarm a, double v) {
        AlarmDefinition d = a.def;
        if (d.kind() == AlarmKind.SOFT) return a.softActive ? 1 : 0;
        if (d.kind() == AlarmKind.BAD_QUALITY) return Double.isNaN(v) ? 1 : 0;
        if (Double.isNaN(v)) return -1;
        double lim = d.limit(), db = d.deadbandAbs();
        boolean on = switch (d.kind()) {
            case DISCRETE -> v >= 0.5;
            case HIGH, HIGH_HIGH -> a.cond ? v > lim - db : v > lim;
            case LOW, LOW_LOW -> a.cond ? v < lim + db : v < lim;
            case DEVIATION -> a.cond ? Math.abs(v) > lim - db : Math.abs(v) > lim;
            case RATE_OF_CHANGE -> a.cond ? Math.abs(a.rate) > lim - db : Math.abs(a.rate) > lim;
            default -> false;
        };
        return on ? 1 : 0;
    }

    private void condOn(Alarm a, double t) {
        a.cond = true;
        a.condSince = t;
        if (a.state == AlarmState.NORMAL || a.state == AlarmState.RTNUN) raise(a, t, true);
    }

    private void condOff(Alarm a, double t) {
        a.cond = false;
        a.condSince = Double.NaN;
        if (a.def.latching()) return;
        if (a.state == AlarmState.UNACK) set(a, AlarmState.RTNUN, AlarmEvent.Type.CLEAR, t);
        else if (a.state == AlarmState.ACKED) set(a, AlarmState.NORMAL, AlarmEvent.Type.CLEAR, t);
    }

    /** Возникновение: {@code counted} — настоящее (в метриках лавины, дребезга и первопричине), а не повтор/выход из скрытия. */
    private void raise(Alarm a, double t, boolean counted) {
        a.tRaise = t;
        a.count++;
        if (counted) {
            String g = a.def.group();
            Double last = lastGroupRaise.get(g);
            if (last == null || t - last > FIRST_OUT_WINDOW_S + EPS) {
                for (Alarm o : byId.values()) if (o.def.group().equals(g)) o.firstOut = false;
                a.firstOut = true;
            }
            lastGroupRaise.put(g, t);
            a.raises[0] = a.raises[1];
            a.raises[1] = a.raises[2];
            a.raises[2] = t;
            if (!a.chattering && !Double.isNaN(a.raises[0]) && t - a.raises[0] <= CHATTER_WINDOW_S + EPS) {
                a.chattering = true;
                if (autoChatterDelay) a.onDelayS = Math.min(MAX_CHATTER_DELAY_S, Math.max(1, 2 * a.onDelayS));
                listener.onEvent(new AlarmEvent(AlarmEvent.Type.CHATTER, a.id(), a.state, t, a.onDelayS));
            }
            if (a.def.priority() <= 3) {
                ring[ringHead] = t;
                ringHead = (ringHead + 1) % RING;
                ringSize = Math.min(RING, ringSize + 1);
            }
        }
        set(a, AlarmState.UNACK, AlarmEvent.Type.RAISE, t);
    }

    private void set(Alarm a, AlarmState s, AlarmEvent.Type type, double t) {
        a.state = s;
        listener.onEvent(new AlarmEvent(type, a.id(), s, t, a.value));
    }

    private void updateFlood(double t) {
        int rate = rate10min(t);
        peak = Math.max(peak, rate);
        if (!flood && rate > FLOOD_ENTER) {
            flood = true;
            floodSince = t;
            listener.onEvent(new AlarmEvent(AlarmEvent.Type.FLOOD_ON, 0, AlarmState.UNACK, t, rate));
        } else if (flood && rate < FLOOD_EXIT) {
            flood = false;
            floodSince = Double.NaN;
            listener.onEvent(new AlarmEvent(AlarmEvent.Type.FLOOD_OFF, 0, AlarmState.NORMAL, t, rate));
        }
    }

    /** Возникновений P1–P3 за 10 мин до {@code t}. */
    public int rate10min(double t) {
        int n = 0;
        for (int k = 1; k <= ringSize; k++) {
            if (ring[Math.floorMod(ringHead - k, RING)] < t - FLOOD_WINDOW_S - EPS) break;
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ оператор и программа

    /** Квитировать: UNACK → ACKED, RTNUN → NORMAL; {@code false} — квитировать нечего. */
    public boolean acknowledge(int id, double t) {
        Alarm a = byId.get(id);
        if (a == null) return false;
        if (a.state == AlarmState.UNACK) {
            a.tAck = t;
            a.firstOut = false;
            set(a, AlarmState.ACKED, AlarmEvent.Type.ACK, t);
            return true;
        }
        if (a.state == AlarmState.RTNUN) {
            a.tAck = t;
            a.firstOut = false;
            set(a, AlarmState.NORMAL, AlarmEvent.Type.ACK, t);
            return true;
        }
        return false;
    }

    /** Квитировать все (группы {@code group}, {@code null} — все); число квитированных. */
    public int acknowledgeAll(String group, double t) {
        int n = 0;
        for (Alarm a : byId.values()) if ((group == null || a.def.group().equals(group)) && acknowledge(a.id(), t)) n++;
        return n;
    }

    /** Сброс самоподхвата: квитированная сигнализация {@code latching} без условия → NORMAL. */
    public boolean reset(int id, double t) {
        Alarm a = byId.get(id);
        if (a == null || a.state != AlarmState.ACKED || a.cond) return false;
        set(a, AlarmState.NORMAL, AlarmEvent.Type.CLEAR, t);
        return true;
    }

    /** Гашение сирены без квитирования: звучат только сигнализации, возникшие позже. */
    public void silence(double t) {
        silenceT = t;
    }

    /** Приоритет, которым звучит сирена (наивысший среди неквитированных P1–P3, возникших после гашения); 0 — тишина. */
    public int hornPriority() {
        int p = 0;
        for (Alarm a : byId.values()) {
            int pr = a.def.priority();
            if (a.state == AlarmState.UNACK && pr <= 3 && a.tRaise > silenceT && (p == 0 || pr < p)) p = pr;
        }
        return p;
    }

    /** Программная сигнализация {@code SOFT}: поднять с значением {@code value}. */
    public void raiseSoft(int id, double value) {
        Alarm a = soft(id);
        a.softActive = true;
        a.value = value;
    }

    public void clearSoft(int id) {
        soft(id).softActive = false;
    }

    private Alarm soft(int id) {
        Alarm a = byId.get(id);
        if (a == null) throw new IllegalArgumentException("no alarm " + id);
        if (a.def.kind() != AlarmKind.SOFT) throw new IllegalArgumentException("alarm " + id + " is not SOFT");
        return a;
    }

    /**
     * Шельфинг на {@code seconds} (03c §3c.6.4): только приоритеты 2–4 ({@link AlarmAccessException} для P1, T-3c-07),
     * не дольше {@link #MAX_SHELVE_S}. Подавленную или выведенную из работы шельфовать нельзя ({@code false}).
     */
    public boolean shelve(int id, double seconds, double t) {
        Alarm a = byId.get(id);
        if (a == null) return false;
        if (a.def.priority() == 1) throw new AlarmAccessException("E_ACCESS: priority 1 alarms cannot be shelved");
        if (!(seconds > 0) || seconds > MAX_SHELVE_S + EPS) throw new IllegalArgumentException("shelve time must be 0.." + (int) MAX_SHELVE_S + " s");
        if (a.state == AlarmState.SUPPRESSED || a.state == AlarmState.OOS) return false;
        a.state = AlarmState.SHELVED;
        a.shelvedUntil = t + seconds;
        a.firstOut = false;
        return true;
    }

    public boolean unshelve(int id, double t) {
        Alarm a = byId.get(id);
        if (a == null || a.state != AlarmState.SHELVED) return false;
        leaveHidden(a, t);
        return true;
    }

    /**
     * Подавление группы по состоянию установки (state-based suppression, 03c §3c.6.4): показанные и NORMAL →
     * SUPPRESSED; снятие — UNACK, если условие есть, иначе NORMAL. Шельфованные и выведенные из работы не трогает.
     */
    public void setSuppression(String group, boolean active) {
        if (group == null || group.isEmpty()) throw new IllegalArgumentException("suppression group");
        if (active ? !suppressedGroups.add(group) : !suppressedGroups.remove(group)) return;
        for (Alarm a : byId.values()) {
            if (!a.def.group().equals(group)) continue;
            if (active && !a.state.hidden()) {
                a.state = AlarmState.SUPPRESSED;
                a.firstOut = false;
            } else if (!active && a.state == AlarmState.SUPPRESSED) leaveHidden(a, now);
        }
    }

    public boolean suppressed(String group) {
        return suppressedGroups.contains(group);
    }

    /** Вывод из работы (датчик в ремонте) и возврат — как выход из подавления. */
    public boolean setOutOfService(int id, boolean oos, double t) {
        Alarm a = byId.get(id);
        if (a == null) return false;
        if (oos) {
            a.state = AlarmState.OOS;
            a.firstOut = false;
            a.shelvedUntil = Double.NaN;
        } else if (a.state == AlarmState.OOS) leaveHidden(a, t);
        return true;
    }

    /** АЗ сработала в момент {@code t}: через 2 с группа {@link #POST_TRIP} подавляется на 30 мин (T-3c-06). */
    public void trip(double t) {
        postTripOnAt = t + POST_TRIP_DELAY_S;
    }

    private void leaveHidden(Alarm a, double t) {
        a.shelvedUntil = Double.NaN;
        if (!a.def.group().isEmpty() && suppressedGroups.contains(a.def.group())) {
            a.state = AlarmState.SUPPRESSED;
            return;
        }
        if (a.cond) raise(a, t, false);
        else a.state = AlarmState.NORMAL;
    }

    // ------------------------------------------------------------------ метрики

    public boolean flood() {
        return flood;
    }

    public AlarmStatistics statistics() {
        int standing = 0, stale = 0, shelved = 0, suppressed = 0, unacked = 0, active = 0;
        Map<Integer, Integer> chatter = new TreeMap<>();
        for (Alarm a : byId.values()) {
            if (a.cond && !Double.isNaN(a.condSince) && now - a.condSince > STANDING_S) standing++;
            if (a.def.kind() != AlarmKind.SOFT && Double.isNaN(a.value)) stale++;
            switch (a.state) {
                case SHELVED -> shelved++;
                case SUPPRESSED -> suppressed++;
                case UNACK, RTNUN -> unacked++;
                default -> {
                }
            }
            if ((a.state == AlarmState.UNACK || a.state == AlarmState.ACKED) && a.cond) active++;
            if (a.chattering) {
                int n = 0;
                for (double r : a.raises) if (!Double.isNaN(r) && now - r <= CHATTER_WINDOW_S + EPS) n++;
                chatter.put(a.id(), n);
            }
        }
        return new AlarmStatistics(rate10min(now), peak, standing, stale, chatter, flood, floodSince, shelved, suppressed, unacked, active);
    }

    /** Сигнализации по убыванию важности для табло и списка: показанные, затем приоритет, затем время возникновения. */
    public List<Alarm> ranked() {
        List<Alarm> out = new ArrayList<>(byId.values());
        out.sort((x, y) -> {
            int c = Boolean.compare(y.state.annunciated(), x.state.annunciated());
            if (c != 0) return c;
            c = Integer.compare(x.def.priority(), y.def.priority());
            if (c != 0) return c;
            return Integer.compare(x.id(), y.id());
        });
        return out;
    }

    // ------------------------------------------------------------------ сохранение (05 §5.2.8)

    public void save(StateWriter out) {
        out.putInt("nextId", nextId);
        out.putDouble("now", now);
        out.putDouble("silenceT", silenceT);
        out.putDouble("postTripOnAt", postTripOnAt);
        out.putDouble("postTripOffAt", postTripOffAt);
        out.putBoolean("flood", flood);
        out.putDouble("floodSince", floodSince);
        out.putInt("peak", peak);
        double[] r = new double[ringSize];
        for (int k = 0; k < ringSize; k++) r[k] = ring[Math.floorMod(ringHead - ringSize + k, RING)];
        out.putDoubles("ring", r, ringSize);
        out.putString("suppressed", String.join("\n", suppressedGroups));
        out.beginObject("alarms");
        for (Alarm a : byId.values()) {
            AlarmDefinition d = a.def;
            out.beginObject(Integer.toString(a.id()));
            out.putString("tag", d.tag());
            out.putString("kind", d.kind().name());
            out.putInt("priority", d.priority());
            out.putDouble("limit", d.limit());
            out.putDouble("deadband", d.deadband());
            out.putDouble("onDelay", d.onDelayS());
            out.putDouble("offDelay", d.offDelayS());
            out.putBoolean("latching", d.latching());
            out.putString("group", d.group());
            out.putString("message", d.message());
            out.putString("action", d.operatorAction());
            out.putBoolean("program", a.program);
            out.putString("state", a.state.name());
            out.putBoolean("cond", a.cond);
            out.putDouble("pendingSince", a.pendingSince);
            out.putDouble("value", a.value);
            out.putDouble("prevValue", a.prevValue);
            out.putDouble("prevT", a.prevT);
            out.putBoolean("soft", a.softActive);
            out.putDouble("tRaise", a.tRaise);
            out.putDouble("tAck", a.tAck);
            out.putDouble("condSince", a.condSince);
            out.putDouble("shelvedUntil", a.shelvedUntil);
            out.putInt("count", a.count);
            out.putBoolean("firstOut", a.firstOut);
            out.putBoolean("chattering", a.chattering);
            out.putDoubles("raises", a.raises, 3);
            out.putDouble("onDelayNow", a.onDelayS);
            out.endObject();
        }
        out.endObject();
    }

    public void load(StateReader in) {
        byId.clear();
        byKey.clear();
        suppressedGroups.clear();
        lastGroupRaise.clear();
        nextId = in.optInt("nextId", 1);
        now = in.optDouble("now", 0);
        silenceT = in.optDouble("silenceT", Double.NEGATIVE_INFINITY);
        postTripOnAt = in.optDouble("postTripOnAt", Double.NaN);
        postTripOffAt = in.optDouble("postTripOffAt", Double.NaN);
        flood = in.has("flood") && in.getBoolean("flood");
        floodSince = in.optDouble("floodSince", Double.NaN);
        peak = in.optInt("peak", 0);
        ringHead = 0;
        ringSize = 0;
        if (in.has("ring")) {
            for (double t : in.getDoubles("ring")) {
                ring[ringHead] = t;
                ringHead = (ringHead + 1) % RING;
                ringSize = Math.min(RING, ringSize + 1);
            }
        }
        String sup = in.optString("suppressed", "");
        if (!sup.isEmpty()) suppressedGroups.addAll(List.of(sup.split("\n")));
        if (!in.has("alarms")) return;
        in.beginObject("alarms");
        TreeMap<Integer, Alarm> loaded = new TreeMap<>();
        for (String k : new ArrayList<>(in.keys())) {
            int id;
            try {
                id = Integer.parseInt(k);
            } catch (NumberFormatException e) {
                continue;
            }
            in.beginObject(k);
            try {
                AlarmDefinition d = new AlarmDefinition(id, in.getString("tag"), AlarmKind.valueOf(in.getString("kind")), in.getInt("priority"),
                        in.getDouble("limit"), in.getDouble("deadband"), in.getDouble("onDelay"), in.getDouble("offDelay"),
                        in.getBoolean("latching"), in.optString("group", ""), in.optString("message", ""), in.optString("action", ""));
                Alarm a = new Alarm(d, in.getBoolean("program"));
                a.state = AlarmState.valueOf(in.getString("state"));
                a.cond = in.getBoolean("cond");
                a.pendingSince = in.optDouble("pendingSince", Double.NaN);
                a.value = in.optDouble("value", Double.NaN);
                a.prevValue = in.optDouble("prevValue", Double.NaN);
                a.prevT = in.optDouble("prevT", Double.NaN);
                a.softActive = in.has("soft") && in.getBoolean("soft");
                a.tRaise = in.optDouble("tRaise", Double.NaN);
                a.tAck = in.optDouble("tAck", Double.NaN);
                a.condSince = in.optDouble("condSince", Double.NaN);
                a.shelvedUntil = in.optDouble("shelvedUntil", Double.NaN);
                a.count = in.optInt("count", 0);
                a.firstOut = in.has("firstOut") && in.getBoolean("firstOut");
                a.chattering = in.has("chattering") && in.getBoolean("chattering");
                if (in.has("raises")) {
                    double[] r = in.getDoubles("raises");
                    for (int i = 0; i < Math.min(3, r.length); i++) a.raises[i] = r[i];
                }
                a.onDelayS = in.optDouble("onDelayNow", d.onDelayS());
                loaded.put(id, a);
                nextId = Math.max(nextId, id + 1);
            } catch (IllegalArgumentException e) {
                // повреждённая запись сигнализации — пропустить, остальные загрузить
            } finally {
                in.endObject();
            }
        }
        in.endObject();
        for (Alarm a : loaded.values()) {
            byId.put(a.id(), a);
            if (a.def.kind() != AlarmKind.SOFT) byKey.putIfAbsent(a.def.key(), a);
            if (!Double.isNaN(a.tRaise)) lastGroupRaise.merge(a.def.group(), a.tRaise, Math::max);
        }
    }
}
