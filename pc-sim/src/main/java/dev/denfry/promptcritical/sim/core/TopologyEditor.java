package dev.denfry.promptcritical.sim.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Изменение топологии мира (05 §5.2.1, §5.3.1): выдача {@link SimId} и реестр устройств — доменных двойников
 * блоков. Только серверный поток и только вне FAST-шага: здесь растут массивы. Удаление устройства откладывается
 * до начала следующего тика, чтобы подсистемы не теряли объект посреди шага.
 *
 * <p>Устройства хранятся SoA и плотно (удаление переносом последнего); {@code SimId} отображается в слот.
 */
public final class TopologyEditor {
    private static final DeviceMode[] MODES = DeviceMode.values();

    private long nextSerial = 1;

    private int size;
    private long[] id = new long[16];
    private long[] pos = new long[16];
    private int[] type = new int[16];
    private byte[] mode = new byte[16];
    private final Map<Long, Integer> slotById = new HashMap<>();

    private final List<String> typeNames = new ArrayList<>();
    private final Map<String, Integer> typeIndex = new HashMap<>();

    private long[] pendingRemoval = new long[16];
    private int pendingCount;
    private int version;

    /** Новый идентификатор; номера сквозные для всех видов и никогда не переиспользуются. */
    public SimId allocate(SimId.Kind kind) {
        return SimId.of(kind, nextSerial++);
    }

    /**
     * Зарегистрировать устройство блока.
     *
     * @param deviceType ключ типа, обычно id блока ({@code promptcritical:grate_stoker})
     */
    public SimId addDevice(String deviceType, GridPos at) {
        SimId sid = allocate(SimId.Kind.DEVICE);
        insert(sid.raw(), at.asLong(), typeIndexOf(deviceType), DeviceMode.LOCAL);
        return sid;
    }

    /** Пометить устройство к удалению в начале следующего тика; повторная пометка безвредна. */
    public void removeDevice(SimId device) {
        if (!slotById.containsKey(device.raw())) return;
        for (int i = 0; i < pendingCount; i++) if (pendingRemoval[i] == device.raw()) return;
        if (pendingCount == pendingRemoval.length) pendingRemoval = Arrays.copyOf(pendingRemoval, pendingCount * 2);
        pendingRemoval[pendingCount++] = device.raw();
    }

    public boolean exists(SimId device) {
        return slotById.containsKey(device.raw());
    }

    public boolean removalPending(SimId device) {
        for (int i = 0; i < pendingCount; i++) if (pendingRemoval[i] == device.raw()) return true;
        return false;
    }

    public int deviceCount() {
        return size;
    }

    /** Устройство в плотном слоте {@code slot ∈ [0, deviceCount())}; порядок меняется при удалениях. */
    public SimId deviceAt(int slot) {
        return new SimId(id[slot]);
    }

    public String deviceType(SimId device) {
        return typeNames.get(type[slot(device)]);
    }

    public GridPos devicePos(SimId device) {
        return GridPos.fromLong(pos[slot(device)]);
    }

    public DeviceMode deviceMode(SimId device) {
        return MODES[mode[slot(device)]];
    }

    /** Счётчик изменений топологии: подсистемы и биндинг пересобирают производные структуры при его смене. */
    public int version() {
        return version;
    }

    void setDeviceMode(SimId device, DeviceMode m) {
        mode[slot(device)] = (byte) m.ordinal();
    }

    private int slot(SimId device) {
        Integer s = slotById.get(device.raw());
        if (s == null) throw new IllegalArgumentException("unknown device " + device.hex());
        return s;
    }

    /** Применить отложенные удаления; уведомить подсистемы. Начало тика, серверный поток. */
    void applyPendingRemovals(List<Subsystem> subsystems) {
        for (int i = 0; i < pendingCount; i++) {
            long raw = pendingRemoval[i];
            Integer s = slotById.remove(raw);
            if (s == null) continue;
            int last = size - 1;
            if (s != last) {
                id[s] = id[last];
                pos[s] = pos[last];
                type[s] = type[last];
                mode[s] = mode[last];
                slotById.put(id[s], s);
            }
            size--;
            version++;
            SimId removed = new SimId(raw);
            for (Subsystem sub : subsystems) sub.onDeviceRemoved(removed);
        }
        pendingCount = 0;
    }

    private void insert(long raw, long packedPos, int typeIdx, DeviceMode m) {
        if (size == id.length) {
            int cap = size * 2;
            id = Arrays.copyOf(id, cap);
            pos = Arrays.copyOf(pos, cap);
            type = Arrays.copyOf(type, cap);
            mode = Arrays.copyOf(mode, cap);
        }
        id[size] = raw;
        pos[size] = packedPos;
        type[size] = typeIdx;
        mode[size] = (byte) m.ordinal();
        slotById.put(raw, size);
        size++;
        version++;
    }

    private int typeIndexOf(String deviceType) {
        Integer i = typeIndex.get(deviceType);
        if (i != null) return i;
        int idx = typeNames.size();
        typeNames.add(deviceType);
        typeIndex.put(deviceType, idx);
        return idx;
    }

    void save(StateWriter out) {
        out.putLong("next_serial", nextSerial);
        out.beginObject("devices");
        out.putInt("count", size);
        out.putLongs("id", id, size);
        out.putLongs("pos", pos, size);
        out.putInts("type", type, size);
        out.putBytes("mode", mode, size);
        out.beginObject("types");
        for (int i = 0; i < typeNames.size(); i++) out.putString(Integer.toString(i), typeNames.get(i));
        out.endObject();
        out.endObject();
        // Отложенные удаления к сохранению применены не всегда: сохраняем, чтобы не воскресить устройство.
        out.putLongs("pending_removal", pendingRemoval, pendingCount);
    }

    void load(StateReader in) {
        nextSerial = in.getLong("next_serial");
        in.beginObject("devices");
        int n = in.getInt("count");
        long[] ids = in.getLongs("id");
        long[] poss = in.getLongs("pos");
        int[] types = in.getInts("type");
        byte[] modes = in.getBytes("mode");
        in.beginObject("types");
        List<String> names = new ArrayList<>();
        for (int i = 0; in.has(Integer.toString(i)); i++) names.add(in.getString(Integer.toString(i)));
        in.endObject();
        in.endObject();
        size = 0;
        version++;
        slotById.clear();
        typeNames.clear();
        typeIndex.clear();
        // таблица типов — в сохранённом порядке, с типами без устройств: иначе индексы сдвигаются и хеш состояния меняется
        for (String name : names) typeIndexOf(name);
        for (int i = 0; i < n; i++) insert(ids[i], poss[i], typeIndexOf(names.get(types[i])), MODES[Math.min(modes[i], MODES.length - 1)]);
        long[] pending = in.has("pending_removal") ? in.getLongs("pending_removal") : new long[0];
        pendingCount = 0;
        for (long raw : pending) removeDevice(new SimId(raw));
    }
}
