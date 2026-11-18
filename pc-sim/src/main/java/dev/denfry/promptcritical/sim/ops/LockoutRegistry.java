package dev.denfry.promptcritical.sim.ops;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Замки LOTO мира (13 §13.1): точки изоляции — клетки блоков, принимающих замок (выключатель, разъединитель,
 * задвижка, расцепная муфта, заглушка, ящик блокировки). Автомат точки — §13.1.8:
 * <pre>
 * IN_SERVICE ─(перевод в изолированное положение — мир)→ ISOLATED ─(замок)→ LOCKED ─(проверка)→ VERIFIED
 *          ←───────────────(последний замок снят)──────────────────┘
 * </pre>
 * Изолированное ли положение у блока, решает мир ({@code isolated}); реестр хранит замки и отметку проверки.
 * На точке без многозамковой накладки ({@code lockout_hasp}) — один замок, с накладкой — до {@link #HASP_LOCKS}.
 * Снимает замок только его владелец (ключ неотчуждаем, §13.1.7); администратор срезает все замки с журналом.
 */
public final class LockoutRegistry {
    /** Замков на накладке (§13.1.2). */
    public static final int HASP_LOCKS = 6;

    public enum IsolationState { IN_SERVICE, ISOLATED, LOCKED, VERIFIED }

    public enum LockResult {
        OK,
        /** Точка не в изолированном положении: «сначала изолируйте». */
        NOT_ISOLATED,
        /** На точке уже замок, а накладки нет (или накладка полна). */
        NO_ROOM,
        /** У игрока уже висят все его замки ({@code ops.max_locks_per_player}). */
        LIMIT,
        /** Этого игрока замка на точке нет. */
        NOT_YOURS,
        /** Замков на точке нет. */
        NOT_LOCKED,
        /** Проверка не подтвердила отсутствие энергии. */
        ENERGY_PRESENT,
        /** Накладка уже стоит / её нельзя снять, пока на ней замки. */
        HASP_BUSY,
        /** Ключи от замков игрока лежат в ящике блокировки, а на ящике ещё висят замки бригады (§13.1.2). */
        KEYS_IN_BOX
    }

    /** Замок на точке: владелец, бирка (причина), номер наряда (0 — без наряда), тик навески. */
    public record Lockout(long lockId, UUID owner, String ownerName, String tag, long permitId, long appliedTick) {
    }

    private static final class Point {
        final List<Lockout> locks = new ArrayList<>();
        boolean hasp, verified;
    }

    private final Map<Long, Point> points = new TreeMap<>();
    /** Ключи ответственного в ящике блокировки: игрок → клетка ящика. */
    private final Map<UUID, Long> keysInBox = new java.util.HashMap<>();
    private long nextLockId = 1;

    private Point point(GridPos p) {
        return points.get(p.asLong());
    }

    /** Состояние точки при нынешнем положении блока. */
    public IsolationState state(GridPos p, boolean isolated) {
        Point pt = point(p);
        if (pt != null && !pt.locks.isEmpty()) return pt.verified ? IsolationState.VERIFIED : IsolationState.LOCKED;
        return isolated ? IsolationState.ISOLATED : IsolationState.IN_SERVICE;
    }

    /** На точке висит хотя бы один замок: блок не управляется ни рукой, ни Lua, ни ПЛК, ни ПАЗ (§13.1.3). */
    public boolean isLocked(GridPos p) {
        Point pt = point(p);
        return pt != null && !pt.locks.isEmpty();
    }

    public boolean hasHasp(GridPos p) {
        Point pt = point(p);
        return pt != null && pt.hasp;
    }

    public List<Lockout> locks(GridPos p) {
        Point pt = point(p);
        return pt == null ? List.of() : Collections.unmodifiableList(pt.locks);
    }

    /** Замков игрока во всём мире. */
    public int locksOf(UUID player) {
        int n = 0;
        for (Point pt : points.values()) for (Lockout l : pt.locks) if (l.owner().equals(player)) n++;
        return n;
    }

    /**
     * Повесить замок с биркой. Отказ, если точка не изолирована, места нет (второй замок без накладки) или у игрока
     * кончились замки.
     */
    public LockResult apply(GridPos p, boolean isolated, UUID player, String name, String tag, long permitId, long tick,
            int maxPerPlayer) {
        Point pt = points.computeIfAbsent(p.asLong(), k -> new Point());
        if (pt.locks.isEmpty() && !isolated) {
            dropIfEmpty(p, pt);
            return LockResult.NOT_ISOLATED;
        }
        int room = pt.hasp ? HASP_LOCKS : 1;
        if (pt.locks.size() >= room) return LockResult.NO_ROOM;
        for (Lockout l : pt.locks) if (l.owner().equals(player)) return LockResult.NO_ROOM;
        if (locksOf(player) >= maxPerPlayer) {
            dropIfEmpty(p, pt);
            return LockResult.LIMIT;
        }
        pt.locks.add(new Lockout(nextLockId++, player, name, tag == null ? "" : tag, permitId, tick));
        return LockResult.OK;
    }

    /**
     * Снять свой замок; последний снятый возвращает точку в {@code ISOLATED} и сбрасывает проверку. Пока ключи
     * игрока в ящике блокировки, а на ящике висят замки бригады, свои замки на точках он снять не может.
     */
    public LockResult remove(GridPos p, UUID player) {
        Point pt = point(p);
        if (pt == null || pt.locks.isEmpty()) return LockResult.NOT_LOCKED;
        Long box = keysInBox.get(player);
        if (box != null && box != p.asLong()) {
            Point b = points.get(box);
            if (b != null && !b.locks.isEmpty()) return LockResult.KEYS_IN_BOX;
        }
        if (!pt.locks.removeIf(l -> l.owner().equals(player))) return LockResult.NOT_YOURS;
        if (pt.locks.isEmpty()) pt.verified = false;
        dropIfEmpty(p, pt);
        return LockResult.OK;
    }

    /**
     * Администратор срезает все замки точки (§13.1.7, {@code LOCKOUT_ADMIN_CUT}).
     *
     * @return срезанные замки (их владельцам — сообщение)
     */
    public List<Lockout> adminCut(GridPos p) {
        Point pt = point(p);
        if (pt == null) return List.of();
        List<Lockout> cut = new ArrayList<>(pt.locks);
        pt.locks.clear();
        pt.verified = false;
        dropIfEmpty(p, pt);
        return cut;
    }

    /** Проверка отсутствия энергии (§13.1.6): только на запертой точке и только по факту измерения. */
    public LockResult verify(GridPos p, boolean noEnergy) {
        Point pt = point(p);
        if (pt == null || pt.locks.isEmpty()) return LockResult.NOT_LOCKED;
        if (!noEnergy) return LockResult.ENERGY_PRESENT;
        pt.verified = true;
        return LockResult.OK;
    }

    public boolean verified(GridPos p) {
        Point pt = point(p);
        return pt != null && pt.verified && !pt.locks.isEmpty();
    }

    /** Поставить многозамковую накладку. */
    public LockResult fitHasp(GridPos p) {
        Point pt = points.computeIfAbsent(p.asLong(), k -> new Point());
        if (pt.hasp) return LockResult.HASP_BUSY;
        pt.hasp = true;
        return LockResult.OK;
    }

    /** Снять накладку: только пустую. */
    public LockResult removeHasp(GridPos p) {
        Point pt = point(p);
        if (pt == null || !pt.hasp || !pt.locks.isEmpty()) return LockResult.HASP_BUSY;
        pt.hasp = false;
        dropIfEmpty(p, pt);
        return LockResult.OK;
    }

    /**
     * Групповой ящик блокировки (§13.1.2): ответственный кладёт в него ключи от своих замков на точках, бригада вешает
     * личные замки на ящик. Ящик — точка с постоянной накладкой.
     */
    public LockResult depositKeys(UUID player, GridPos box) {
        if (locksOf(player) == 0) return LockResult.NOT_LOCKED;
        points.computeIfAbsent(box.asLong(), k -> new Point()).hasp = true;
        keysInBox.put(player, box.asLong());
        return LockResult.OK;
    }

    /** Забрать ключи: только когда на ящике нет замков бригады. */
    public LockResult withdrawKeys(UUID player, GridPos box) {
        Long at = keysInBox.get(player);
        if (at == null || at != box.asLong()) return LockResult.NOT_LOCKED;
        Point b = points.get(at);
        if (b != null && !b.locks.isEmpty()) return LockResult.KEYS_IN_BOX;
        keysInBox.remove(player);
        return LockResult.OK;
    }

    /** Чьи ключи лежат в ящике. */
    public List<UUID> keysIn(GridPos box) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : keysInBox.entrySet()) if (e.getValue() == box.asLong()) out.add(e.getKey());
        return out;
    }

    /** Блок точки исчез (сломан администратором, взрыв): замки и накладка уходят с ним. */
    public List<Lockout> forget(GridPos p) {
        keysInBox.values().removeIf(v -> v == p.asLong());
        Point pt = points.remove(p.asLong());
        return pt == null ? List.of() : pt.locks;
    }

    /** Все запертые точки (для команды {@code /pc lockout list} и тестов). */
    public List<GridPos> lockedPoints() {
        List<GridPos> out = new ArrayList<>();
        for (Map.Entry<Long, Point> e : points.entrySet()) if (!e.getValue().locks.isEmpty()) out.add(GridPos.fromLong(e.getKey()));
        return out;
    }

    private void dropIfEmpty(GridPos p, Point pt) {
        if (pt.locks.isEmpty() && !pt.hasp) points.remove(p.asLong());
    }

    // --- сохранение ---

    public void save(StateWriter out) {
        out.putLong("next", nextLockId);
        out.putInt("points", points.size());
        int i = 0;
        for (Map.Entry<Long, Point> e : points.entrySet()) {
            Point pt = e.getValue();
            out.beginObject("p" + i++);
            out.putLong("pos", e.getKey());
            out.putBoolean("hasp", pt.hasp);
            out.putBoolean("verified", pt.verified);
            out.putInt("locks", pt.locks.size());
            for (int k = 0; k < pt.locks.size(); k++) {
                Lockout l = pt.locks.get(k);
                out.beginObject("l" + k);
                out.putLong("id", l.lockId());
                out.putString("owner", l.owner().toString());
                out.putString("name", l.ownerName());
                out.putString("tag", l.tag());
                out.putLong("permit", l.permitId());
                out.putLong("tick", l.appliedTick());
                out.endObject();
            }
            out.endObject();
        }
        out.putInt("keys", keysInBox.size());
        int j = 0;
        for (Map.Entry<UUID, Long> e : keysInBox.entrySet()) {
            out.beginObject("k" + j++);
            out.putString("player", e.getKey().toString());
            out.putLong("box", e.getValue());
            out.endObject();
        }
    }

    public void load(StateReader in) {
        points.clear();
        keysInBox.clear();
        nextLockId = Math.max(1, in.optLong("next", 1));
        int n = in.optInt("points", 0);
        for (int i = 0; i < n; i++) {
            in.beginObject("p" + i);
            Point pt = new Point();
            long pos = in.getLong("pos");
            pt.hasp = in.getBoolean("hasp");
            pt.verified = in.getBoolean("verified");
            int m = in.getInt("locks");
            for (int k = 0; k < m; k++) {
                in.beginObject("l" + k);
                pt.locks.add(new Lockout(in.getLong("id"), UUID.fromString(in.getString("owner")), in.getString("name"),
                        in.getString("tag"), in.getLong("permit"), in.getLong("tick")));
                in.endObject();
            }
            in.endObject();
            if (!pt.locks.isEmpty() || pt.hasp) points.put(pos, pt);
        }
        int k = in.optInt("keys", 0);
        for (int j = 0; j < k; j++) {
            in.beginObject("k" + j);
            keysInBox.put(UUID.fromString(in.getString("player")), in.getLong("box"));
            in.endObject();
        }
    }
}
