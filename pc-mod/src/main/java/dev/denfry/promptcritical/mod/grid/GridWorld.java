package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.GridUnit;
import dev.denfry.promptcritical.sim.grid.LineUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Электросеть измерения со стороны мира (07 §7.1.2, §7.4.2): из выводов загруженных устройств и связных групп кабелей
 * и шин собирает узлы и отдаёт их двойникам; держит пролёты ВЛ согласованными с доменом (восстановление после загрузки,
 * обрыв от перегрева, провисание по температуре провода), ищет блоки в габарите пролётов, передаёт удары молнии и
 * погоду. Пересканирование — при изменении блоков, не чаще раза в 10 тиков. Только серверный поток.
 *
 * <p>Ключ узла — наименьший из ключей его членов: позиции кабеля или пары (устройство, вывод); устройства в
 * выгруженных чанках сохраняют прежние узлы, обход кабелей в выгруженные чанки не заходит.
 */
public final class GridWorld {
    public static final int RESCAN_TICKS = 10, MAX_CABLE_BLOCKS = 8192;
    /** Шаг поиска блоков вдоль провода, м; концы пролёта на этой длине не проверяются. */
    static final double TOUCH_STEP_M = 0.25, TOUCH_END_M = 0.7;
    /** Радиус, в котором молния ищет пролёт, блоков. */
    static final double LIGHTNING_RADIUS = 6;

    private final SimLevelBinding binding;
    private final Set<BlockPos> devices = new TreeSet<>();
    private final List<SpanView> spans = new ArrayList<>();
    private final Set<Long> touchDirty = new HashSet<>();
    private boolean dirty = true;
    private long lastScan = Long.MIN_VALUE / 2;
    private int scans;

    /** Пролёт загруженного коннектора-владельца (меньшая позиция). */
    record SpanView(long key, BlockPos a, BlockPos b, boolean mv, AABB box) {
    }

    public GridWorld(SimLevelBinding binding) {
        this.binding = binding;
    }

    private ServerLevel level() {
        return binding.level();
    }

    private GridSubsystem grid() {
        return binding.world().subsystem(GridSubsystem.class);
    }

    public void register(BlockPos controller) {
        devices.add(controller.immutable());
        dirty = true;
    }

    public void unregister(BlockPos controller) {
        devices.remove(controller);
        dirty = true;
    }

    /** Блок мода изменился: возможно, кабель или устройство — пересобрать узлы. */
    public void markDirty() {
        dirty = true;
    }

    /** Сколько раз мир пересобирал узлы (тесты). */
    public int scans() {
        return scans;
    }

    /** Любой блок поставлен или убран: пролёты, через габарит которых он может проходить, перепроверяются. */
    public void onBlockChanged(BlockPos pos) {
        for (SpanView s : spans) if (s.box.contains(Vec3.atCenterOf(pos))) touchDirty.add(s.key);
    }

    // ================================================================ тик

    public void preTick(long gameTime) {
        GridSubsystem g = grid();
        if (g == null) return;
        if (dirty && gameTime - lastScan >= RESCAN_TICKS) {
            lastScan = gameTime;
            dirty = false;
            rescan(g);
        }
        if (!touchDirty.isEmpty() && gameTime % 5 == 0) {
            for (SpanView s : spans) if (touchDirty.contains(s.key)) g.setLineTouch(s.key, touchAt(s, sagOf(g, s)));
            touchDirty.clear();
        }
        if (gameTime % 20 == 0) maintainSpans(g);
        if (gameTime % 100 == 0) weather(g);
    }

    // ================================================================ узлы

    private void rescan(GridSubsystem g) {
        scans++;
        ServerLevel level = level();
        UnionFind uf = new UnionFind();
        Map<Long, Integer> faceOwner = new HashMap<>();
        Map<Long, Integer> cableSet = new HashMap<>();
        List<Object[]> found = new ArrayList<>();
        for (BlockPos pos : List.copyOf(devices)) {
            if (!level.isLoaded(pos)) continue;
            if (!(level.getBlockEntity(pos) instanceof GridDevice d)) {
                devices.remove(pos);
                continue;
            }
            GridUnit u = d.gridUnit(g);
            if (u == null) continue;
            List<List<GridDevice.Face>> ts = d.terminals();
            int[] ids = new int[ts.size()];
            for (int t = 0; t < ts.size(); t++) {
                int id = uf.add(mix(pos.asLong() * 8 + t + 1));
                ids[t] = id;
                for (GridDevice.Face f : ts.get(t)) {
                    Integer other = faceOwner.putIfAbsent(f.key(), id);
                    if (other != null) uf.union(id, other);
                    BlockPos nb = f.cell().relative(f.dir());
                    if (!level.isLoaded(nb) || !(level.getBlockState(nb).getBlock() instanceof CableBlock)) continue;
                    uf.union(id, cableComponent(level, nb, cableSet, uf));
                }
            }
            found.add(new Object[] {u, ids});
        }
        for (Object[] e : found) {
            GridUnit u = (GridUnit) e[0];
            int[] ids = (int[]) e[1];
            long[] nodes = new long[ids.length];
            for (int t = 0; t < ids.length; t++) nodes[t] = uf.key(ids[t]);
            if (nodes.length == u.terminals()) u.setNodes(nodes);
        }
        refreshSpans(g);
    }

    /** Связная группа кабелей и шин с {@code start}: её элемент в {@code uf} (обход в ширину, один раз на группу). */
    private static int cableComponent(ServerLevel level, BlockPos start, Map<Long, Integer> cableSet, UnionFind uf) {
        Integer known = cableSet.get(start.asLong());
        if (known != null) return known;
        int id = uf.add(Long.MAX_VALUE);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start.immutable());
        cableSet.put(start.asLong(), id);
        long min = Long.MAX_VALUE;
        int n = 0;
        while (!queue.isEmpty() && n++ < MAX_CABLE_BLOCKS) {
            BlockPos p = queue.poll();
            min = Math.min(min, mix(p.asLong()));
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (cableSet.containsKey(q.asLong()) || !level.isLoaded(q)) continue;
                if (!(level.getBlockState(q).getBlock() instanceof CableBlock)) continue;
                cableSet.put(q.asLong(), id);
                queue.add(q);
            }
        }
        uf.setKey(id, min);
        return id;
    }

    // ================================================================ пролёты

    public static long lineKey(BlockPos a, BlockPos b) {
        long x = Math.min(a.asLong(), b.asLong()), y = Math.max(a.asLong(), b.asLong());
        return mix(mix(x) * 31 + y);
    }

    /** Натянуть пролёт между загруженными коннекторами. */
    void addSpan(ConnectorBlockEntity a, ConnectorBlockEntity b, String conductor, boolean insulated) {
        double len = Math.sqrt(a.getBlockPos().distSqr(b.getBlockPos()));
        a.addSpan(b.getBlockPos(), conductor, insulated, len);
        b.addSpan(a.getBlockPos(), conductor, insulated, len);
        GridSubsystem g = grid();
        if (g != null && !a.simId().isNone() && !b.simId().isNone())
            g.putLine(lineKey(a.getBlockPos(), b.getBlockPos()), a.simId(), b.simId(), conductor, insulated, len);
        dirty = true;
    }

    /** Коннектор снят: его пролёты уходят, провод выпадает. */
    void connectorRemoved(ConnectorBlockEntity c) {
        ServerLevel level = level();
        GridSubsystem g = grid();
        for (ConnectorBlockEntity.Span s : List.copyOf(c.spans())) {
            if (level.isLoaded(s.other()) && level.getBlockEntity(s.other()) instanceof ConnectorBlockEntity o) o.removeSpan(c.getBlockPos());
            if (g != null) g.removeLine(lineKey(c.getBlockPos(), s.other()));
            WireCoilItem.drop(level, c.getBlockPos(), s.insulated(), s.conductor());
        }
        dirty = true;
    }

    /** Пролёты загруженных коннекторов: восстановить в домене или убрать висячие концы. */
    private void refreshSpans(GridSubsystem g) {
        ServerLevel level = level();
        spans.clear();
        for (BlockPos pos : devices) {
            if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ConnectorBlockEntity c)) continue;
            for (ConnectorBlockEntity.Span s : List.copyOf(c.spans())) {
                if (pos.asLong() > s.other().asLong()) continue;
                long key = lineKey(pos, s.other());
                ConnectorBlockEntity o = level.isLoaded(s.other()) && level.getBlockEntity(s.other()) instanceof ConnectorBlockEntity x
                        ? x : null;
                if (g.line(key) == null) {
                    if (o != null && o.linkedTo(pos) && !c.simId().isNone() && !o.simId().isNone()) {
                        g.putLine(key, c.simId(), o.simId(), s.conductor(), s.insulated(), Math.sqrt(pos.distSqr(s.other())));
                    } else if (level.isLoaded(s.other())) {
                        c.removeSpan(s.other());
                        continue;
                    }
                }
                AABB box = new AABB(Vec3.atCenterOf(pos), Vec3.atCenterOf(s.other())).inflate(1, s.sagM() + 2, 1);
                SpanView v = new SpanView(key, pos, s.other(), c.mediumVoltage(), box);
                spans.add(v);
                touchDirty.add(key);
            }
        }
    }

    private static double sagOf(GridSubsystem g, SpanView s) {
        LineUnit l = g.line(s.key);
        double len = Math.sqrt(s.a.distSqr(s.b));
        double f0 = len * ConnectorBlockEntity.SAG_FRACTION;
        if (l == null) return f0;
        return LineUnit.spec(l.conductor()).sagM(len, f0, 20, g.lineTemperatureC(s.key));
    }

    /** Точка провода с долей пролёта {@code t}: прямая между подвесами минус парабола провеса. */
    public static Vec3 point(Vec3 a, Vec3 b, double sag, double t) {
        return a.add(b.subtract(a).scale(t)).subtract(0, 4 * sag * t * (1 - t), 0);
    }

    /** Первая точка касания провода с твёрдым блоком или листвой (доля пролёта); NaN — габарит свободен. */
    double touchAt(SpanView s, double sag) {
        ServerLevel level = level();
        Vec3 a = ConnectorBlockEntity.attachPoint(s.a, s.mv), b = ConnectorBlockEntity.attachPoint(s.b, s.mv);
        double len = a.distanceTo(b);
        int n = Math.max(2, (int) Math.ceil(len / TOUCH_STEP_M));
        for (int i = 1; i < n; i++) {
            double t = (double) i / n;
            if (t * len < TOUCH_END_M || (1 - t) * len < TOUCH_END_M) continue;
            BlockPos p = BlockPos.containing(point(a, b, sag, t));
            if (p.equals(s.a) || p.equals(s.b) || !level.isLoaded(p)) continue;
            BlockState st = level.getBlockState(p);
            if (st.isAir() || st.getBlock() instanceof PoleBlock || GridBlocks.isGridBlock(level, p, st)) continue;
            if (!st.getCollisionShape(level, p).isEmpty() || st.is(BlockTags.LEAVES)) return t;
        }
        return Double.NaN;
    }

    /** Провисание по температуре и обрыв от перегрева. */
    private void maintainSpans(GridSubsystem g) {
        ServerLevel level = level();
        for (SpanView s : List.copyOf(spans)) {
            LineUnit l = g.line(s.key);
            if (l != null && l.broken()) {
                breakSpan(g, s);
                continue;
            }
            if (!(level.getBlockEntity(s.a) instanceof ConnectorBlockEntity c)) continue;
            List<ConnectorBlockEntity.Span> list = c.spans();
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).other().equals(s.b)) continue;
                float before = list.get(i).sagM(), sag = (float) sagOf(g, s);
                c.setSag(i, sag);
                if (Math.abs(sag - before) >= 0.05f) touchDirty.add(s.key);
            }
        }
    }

    private void breakSpan(GridSubsystem g, SpanView s) {
        ServerLevel level = level();
        if (level.getBlockEntity(s.a) instanceof ConnectorBlockEntity a) a.removeSpan(s.b);
        if (level.isLoaded(s.b) && level.getBlockEntity(s.b) instanceof ConnectorBlockEntity b) b.removeSpan(s.a);
        g.removeLine(s.key);
        spans.remove(s);
        Vec3 mid = s.box.getCenter();
        level.playSound(null, BlockPos.containing(mid), SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.5f, 0.6f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 30, 1, 0.5, 1, 0.1);
        for (ServerPlayer p : level.players())
            if (p.distanceToSqr(mid) < 64 * 64) p.displayClientMessage(GridText.msg(GridText.WIRE_BROKEN), false);
    }

    // ================================================================ погода и молния

    private void weather(GridSubsystem g) {
        ServerLevel level = level();
        double h = level.isThundering() ? 35 : level.isRaining() ? 25 : 10;
        double solar = level.isDay() && !level.isRaining() ? 10 : 0;
        g.setWeather(level.isRaining() ? 12 : 18, h, solar);
    }

    /** Удар молнии в точку: ближайший пролёт в радиусе получает перекрытие изоляции. */
    public void lightning(Vec3 at) {
        GridSubsystem g = grid();
        if (g == null) return;
        SpanView best = null;
        double bestD = LIGHTNING_RADIUS, bestT = 0.5;
        for (SpanView s : spans) {
            Vec3 a = ConnectorBlockEntity.attachPoint(s.a, s.mv), b = ConnectorBlockEntity.attachPoint(s.b, s.mv);
            Vec3 ab = b.subtract(a);
            double t = Math.max(0, Math.min(1, at.subtract(a).dot(ab) / Math.max(1e-9, ab.lengthSqr())));
            Vec3 p = a.add(ab.scale(t));
            double d = Math.hypot(p.x - at.x, p.z - at.z);
            if (d < bestD) {
                bestD = d;
                best = s;
                bestT = t;
            }
        }
        if (best == null || !g.strikeLine(best.key, bestT)) return;
        Vec3 p = point(ConnectorBlockEntity.attachPoint(best.a, best.mv), ConnectorBlockEntity.attachPoint(best.b, best.mv),
                sagOf(g, best), bestT);
        level().sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 40, 0.3, 0.3, 0.3, 0.3);
    }

    /** Отчёт для команды отладки. */
    public Component report() {
        return Component.literal("grid devices " + devices.size() + ", spans " + spans.size() + ", scans " + scans);
    }

    // ================================================================ служебное

    /** Перемешивание SplitMix64: ключи узлов не пересекаются с соседними позициями. */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        z = z ^ (z >>> 31);
        return z == GridUnit.NO_NODE ? 1 : z;
    }

    /** Система непересекающихся множеств с наименьшим ключом в корне. */
    private static final class UnionFind {
        private int[] parent = new int[64];
        private long[] key = new long[64];
        private int n;

        int add(long k) {
            if (n == parent.length) {
                parent = java.util.Arrays.copyOf(parent, n * 2);
                key = java.util.Arrays.copyOf(key, n * 2);
            }
            parent[n] = n;
            key[n] = k;
            return n++;
        }

        void setKey(int i, long k) {
            int r = find(i);
            key[r] = Math.min(key[r], k);
            key[i] = Math.min(key[i], k);
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
