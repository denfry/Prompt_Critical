package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.core.FastContext;
import dev.denfry.promptcritical.sim.core.SimConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.core.Subsystem;
import dev.denfry.promptcritical.sim.core.TimeDomain;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Шахты измерения (08 §8.2) как подсистема мира. Шаг SLOW идёт в FAST-шаге раз в {@link TimeDomain#SLOW} тиков,
 * без воркера: сеть и атмосфера дешёвые, а фронт взрыва (узел за тик), показания приборов и действия игроков
 * должны видеть одно состояние. Шахты создаёт и перестраивает привязка с серверного потока между тиками.
 */
public final class MineSubsystem implements Subsystem {
    public static final String ID = "mining";

    private final Map<Long, MineNetwork> networks = new TreeMap<>();
    private MiningConfig cfg;
    private long nextId = 1;

    public MineSubsystem(MiningConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String id() {
        return ID;
    }

    public MiningConfig config() {
        return cfg;
    }

    /** Новая шахта с якорями; граф — {@link MineNetwork#rebuild}. */
    public MineNetwork create(long[] anchors) {
        MineNetwork n = new MineNetwork(nextId++, cfg);
        n.setAnchors(anchors);
        networks.put(n.id(), n);
        return n;
    }

    public void remove(long id) {
        networks.remove(id);
    }

    public MineNetwork network(long id) {
        return networks.get(id);
    }

    /** Шахты в порядке id. */
    public Collection<MineNetwork> networks() {
        return Collections.unmodifiableCollection(networks.values());
    }

    @Override
    public void stepFast(FastContext ctx) {
        boolean slow = ctx.tick() % TimeDomain.SLOW.periodTicks == 0;
        double dt = ctx.clock().slowDtPhys();
        for (MineNetwork n : networks.values()) {
            boolean was = n.exploding();
            if (slow) n.stepSlow(dt);
            n.stepFront();
            if (!was && n.exploding())
                ctx.events().journal(ctx.tick(), SimId.NONE, Severity.CRITICAL, "mine " + n.id() + ": methane explosion");
        }
    }

    @Override
    public void onConfigReload(SimConfig config) {
        cfg = config.section(MiningConfig.class, cfg);
        for (MineNetwork n : networks.values()) n.setConfig(cfg);
    }

    @Override
    public void save(StateWriter out) {
        out.putLong("next_id", nextId);
        long[] ids = new long[networks.size()];
        int i = 0;
        for (MineNetwork n : networks.values()) ids[i++] = n.id();
        out.putLongs("ids", ids, ids.length);
        for (MineNetwork n : networks.values()) {
            out.beginObject(Long.toString(n.id()));
            n.save(out);
            out.endObject();
        }
    }

    @Override
    public void load(StateReader in) {
        networks.clear();
        nextId = in.getLong("next_id");
        for (long id : in.getLongs("ids")) {
            MineNetwork n = new MineNetwork(id, cfg);
            in.beginObject(Long.toString(id));
            n.load(in);
            in.endObject();
            networks.put(id, n);
        }
    }

    @Override
    public void resetToSafeState() {
        networks.clear();
    }
}
