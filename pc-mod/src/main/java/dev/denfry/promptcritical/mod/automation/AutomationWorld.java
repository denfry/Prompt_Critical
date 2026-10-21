package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.grid.GridWorld;
import dev.denfry.promptcritical.sim.automation.AutomationSubsystem;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Провода автоматики измерения со стороны мира (03c §3c.2, 03d §3d.2.2): из проводов загруженных блоков собирает цепи
 * и отдаёт их двойникам. Провод хранится на обоих концах; цепь клеммы с проводами — наименьший ключ её членов (пар
 * «блок, клемма»), без проводов — своя ({@link AutomationUnit#NO_NET}). Конец в выгруженном чанке входит в цепь по
 * своему ключу; висячий конец (на месте блока другой блок) убирается при пересборке. Только серверный поток.
 */
public final class AutomationWorld {
    public static final int RESCAN_TICKS = 5;
    /** Наибольшая длина «провода» {@code wiring_tool} по манхэттенской метрике, м ({@code scada.max_virtual_wire_m}). */
    public static final int MAX_WIRE_M = 64;

    private final SimLevelBinding binding;
    private final Set<BlockPos> devices = new TreeSet<>();
    private boolean dirty = true;
    private long lastScan = Long.MIN_VALUE / 2;
    private int scans;

    public AutomationWorld(SimLevelBinding binding) {
        this.binding = binding;
    }

    private ServerLevel level() {
        return binding.level();
    }

    @Nullable
    private AutomationSubsystem automation() {
        return binding.world().subsystem(AutomationSubsystem.class);
    }

    public void register(BlockPos pos) {
        devices.add(pos.immutable());
        dirty = true;
    }

    public void unregister(BlockPos pos) {
        devices.remove(pos);
        dirty = true;
    }

    public void markDirty() {
        dirty = true;
    }

    public int scans() {
        return scans;
    }

    /** Ключ клеммы {@code t} блока {@code pos}; никогда не {@link AutomationUnit#NO_NET}. */
    public static long key(BlockPos pos, int t) {
        return GridWorld.mix(pos.asLong() * 64 + t + 1);
    }

    @Nullable
    private WiredDevice deviceAt(BlockPos pos) {
        ServerLevel level = level();
        return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof WiredDevice d ? d : null;
    }

    // ================================================================ провода

    /** Провод между клеммами двух блоков; {@code false} — такой уже есть. */
    public boolean connect(WiredDevice a, int ta, WiredDevice b, int tb) {
        WiredDevice.Wire w = new WiredDevice.Wire(ta, b.wiredPos().immutable(), tb);
        if (a.wires().contains(w)) return false;
        a.wires().add(w);
        if (a != b) b.wires().add(new WiredDevice.Wire(tb, a.wiredPos().immutable(), ta));
        else a.wires().add(new WiredDevice.Wire(tb, a.wiredPos().immutable(), ta));
        a.wiresChanged();
        b.wiresChanged();
        dirty = true;
        return true;
    }

    /** Снять все провода клеммы {@code t}; число снятых. */
    public int disconnect(WiredDevice d, int t) {
        List<WiredDevice.Wire> gone = new ArrayList<>();
        for (WiredDevice.Wire w : d.wires()) if (w.from() == t) gone.add(w);
        for (WiredDevice.Wire w : gone) {
            d.wires().remove(w);
            WiredDevice o = w.other().equals(d.wiredPos()) ? d : deviceAt(w.other());
            if (o != null) {
                o.wires().remove(new WiredDevice.Wire(w.to(), d.wiredPos(), t));
                if (o != d) o.wiresChanged();
            }
        }
        if (!gone.isEmpty()) {
            d.wiresChanged();
            dirty = true;
        }
        return gone.size();
    }

    /** Блок снят: провода уходят и с других концов. */
    public void deviceRemoved(WiredDevice d) {
        for (WiredDevice.Wire w : List.copyOf(d.wires())) {
            WiredDevice o = w.other().equals(d.wiredPos()) ? null : deviceAt(w.other());
            if (o != null && o.wires().removeIf(x -> x.other().equals(d.wiredPos()))) o.wiresChanged();
        }
        d.wires().clear();
        dirty = true;
    }

    // ================================================================ цепи

    public void preTick(long gameTime) {
        if (!dirty || gameTime - lastScan < RESCAN_TICKS) return;
        AutomationSubsystem a = automation();
        if (a == null) return;
        lastScan = gameTime;
        dirty = false;
        rescan(a);
    }

    private void rescan(AutomationSubsystem a) {
        scans++;
        UnionFind uf = new UnionFind();
        List<Object[]> found = new ArrayList<>();
        boolean incomplete = false;
        for (BlockPos pos : List.copyOf(devices)) {
            if (!level().isLoaded(pos)) continue;
            WiredDevice d = deviceAt(pos);
            if (d == null) {
                devices.remove(pos);
                continue;
            }
            AutomationUnit u = d.automationUnit(a);
            if (u == null) {
                incomplete = true; // блок ещё без id в топологии — пересобрать позже
                continue;
            }
            int n = d.wiredKind().terminalCount();
            boolean[] wired = new boolean[n];
            boolean pruned = false;
            for (WiredDevice.Wire w : List.copyOf(d.wires())) {
                if (w.from() < 0 || w.from() >= n || dangling(w)) {
                    d.wires().remove(w);
                    pruned = true;
                    continue;
                }
                wired[w.from()] = true;
                uf.union(uf.id(key(pos, w.from())), uf.id(key(w.other(), w.to())));
            }
            if (pruned) d.wiresChanged();
            found.add(new Object[] {u, pos, wired});
        }
        for (Object[] e : found) {
            AutomationUnit u = (AutomationUnit) e[0];
            BlockPos pos = (BlockPos) e[1];
            boolean[] wired = (boolean[]) e[2];
            long[] nets = new long[wired.length];
            for (int t = 0; t < wired.length; t++) nets[t] = wired[t] ? uf.key(uf.id(key(pos, t))) : AutomationUnit.NO_NET;
            u.setNets(nets);
        }
        if (incomplete) dirty = true;
    }

    /** Другой конец загружен, но там нет блока с такой клеммой. */
    private boolean dangling(WiredDevice.Wire w) {
        if (!level().isLoaded(w.other())) return false;
        WiredDevice o = deviceAt(w.other());
        return o == null || w.to() < 0 || w.to() >= o.wiredKind().terminalCount();
    }

    public Component report() {
        return Component.literal("automation devices " + devices.size() + ", scans " + scans);
    }

    /** Непересекающиеся множества ключей клемм с наименьшим ключом в корне. */
    private static final class UnionFind {
        private final Map<Long, Integer> index = new HashMap<>();
        private int[] parent = new int[64];
        private long[] key = new long[64];
        private int n;

        int id(long k) {
            Integer i = index.get(k);
            if (i != null) return i;
            if (n == parent.length) {
                parent = java.util.Arrays.copyOf(parent, n * 2);
                key = java.util.Arrays.copyOf(key, n * 2);
            }
            parent[n] = n;
            key[n] = k;
            index.put(k, n);
            return n++;
        }

        int find(int i) {
            while (parent[i] != i) {
                parent[i] = parent[parent[i]];
                i = parent[i];
            }
            return i;
        }

        void union(int a, int b) {
            int ra = find(a), rb = find(b);
            if (ra == rb) return;
            parent[rb] = ra;
            key[ra] = Math.min(key[ra], key[rb]);
        }

        long key(int i) {
            return key[find(i)];
        }
    }
}
