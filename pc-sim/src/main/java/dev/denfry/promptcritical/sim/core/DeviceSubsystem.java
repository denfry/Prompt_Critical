package dev.denfry.promptcritical.sim.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Подсистема, у которой на каждое устройство своих типов есть доменный двойник (05 §5.3.1). Двойник создаётся,
 * когда устройство появляется в топологии (или когда биндинг впервые его спросил), и уходит вместе с устройством.
 * Двойники лежат в порядке {@link SimId}: порядок шага и сохранения не зависит от порядка загрузки чанков.
 *
 * @param <U> двойник
 */
public abstract class DeviceSubsystem<U extends DeviceSubsystem.Unit> implements Subsystem {
    /** Доменный двойник устройства. */
    public interface Unit {
        /** Тип устройства (id блока), из которого создан двойник; пишется в сохранение. */
        String deviceType();

        void save(StateWriter out);

        void load(StateReader in);
    }

    private final Map<SimId, U> byId = new HashMap<>();
    private final List<SimId> ids = new ArrayList<>();
    private final List<U> units = new ArrayList<>();
    private int seenVersion = -1;

    /** Двойник для типа устройства; {@code null} — тип не этой подсистемы. */
    protected abstract U create(String deviceType);

    /**
     * Двойник устройства; создаётся при первом обращении, если устройство есть в топологии и тип свой.
     * {@code null} — устройства нет, оно снимается или чужого типа.
     */
    public U unit(TopologyEditor topology, SimId id) {
        U u = byId.get(id);
        if (u != null || id.isNone() || !topology.exists(id) || topology.removalPending(id)) return u;
        u = create(topology.deviceType(id));
        if (u != null) put(id, u);
        return u;
    }

    /** Двойник, если он уже есть. */
    public U unit(SimId id) {
        return byId.get(id);
    }

    /** Подхватить устройства, появившиеся в топологии; вызывать в начале {@link #stepFast}. */
    protected final void sync(TopologyEditor topology) {
        if (topology.version() == seenVersion) return;
        seenVersion = topology.version();
        for (int i = 0; i < topology.deviceCount(); i++) unit(topology, topology.deviceAt(i));
    }

    protected final int size() {
        return units.size();
    }

    protected final U unitAt(int i) {
        return units.get(i);
    }

    protected final SimId idAt(int i) {
        return ids.get(i);
    }

    private void put(SimId id, U u) {
        byId.put(id, u);
        int at = 0;
        while (at < ids.size() && ids.get(at).compareTo(id) < 0) at++;
        ids.add(at, id);
        units.add(at, u);
    }

    @Override
    public void onDeviceRemoved(SimId device) {
        if (byId.remove(device) == null) return;
        int at = ids.indexOf(device);
        ids.remove(at);
        units.remove(at);
    }

    @Override
    public void save(StateWriter out) {
        for (int i = 0; i < ids.size(); i++) {
            out.beginObject(ids.get(i).hex());
            out.putString("type", units.get(i).deviceType());
            units.get(i).save(out);
            out.endObject();
        }
    }

    @Override
    public void load(StateReader in) {
        byId.clear();
        ids.clear();
        units.clear();
        seenVersion = -1;
        for (String key : in.keys()) {
            in.beginObject(key);
            U u = create(in.getString("type"));
            if (u != null) {
                u.load(in);
                put(new SimId(Long.parseUnsignedLong(key, 16)), u);
            }
            in.endObject();
        }
    }
}
