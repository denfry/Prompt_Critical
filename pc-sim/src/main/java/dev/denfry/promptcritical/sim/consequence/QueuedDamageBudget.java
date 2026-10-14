package dev.denfry.promptcritical.sim.consequence;

import dev.denfry.promptcritical.sim.core.GridPos;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link DamageBudget} с очередью (04d §4d.2.3). При подаче запроса перечисляются блоки сферы радиуса
 * min(1,25 P_MC, maxRadiusM) по возрастанию расстояния и сразу применяются лимиты на событие, чанк и регион за
 * игровые сутки и на длину очереди. Порядок применения — приоритет запроса, затем расстояние от центра
 * (обрушение распространяется от центра).
 *
 * <p>Упрощения относительно §4d.2.4: блоки перечисляются сразу, а не лучами порциями по 256; остаточная
 * интенсивность луча не учитывает затенение соседними блоками ({@link BlastScaling#breaks}).
 */
public final class QueuedDamageBudget implements DamageBudget {
    /** Игровые сутки, тиков. */
    public static final long TICKS_PER_DAY = 24_000;
    /** Наибольший радиус перечисления, блоков (P_MC = 12 даёт 15). */
    public static final int MAX_RADIUS = 24;
    /** Сколько проверок пустых блоков допускается на одну операцию бюджета тика. */
    private static final int INSPECTIONS_PER_OPERATION = 16;

    private DamageBudgetConfig config;
    private final List<Entry> queue = new ArrayList<>();
    private final Map<Long, Integer> chunkUsed = new HashMap<>();
    private final Map<Long, Integer> regionUsed = new HashMap<>();
    private final EnumMap<DenyReason, Long> deniedByReason = new EnumMap<>(DenyReason.class);
    private long day = Long.MIN_VALUE;
    private long seq;
    private int pending;
    private long appliedTotal;
    private long deniedTotal;

    public QueuedDamageBudget(DamageBudgetConfig config) {
        this.config = config;
    }

    public void setConfig(DamageBudgetConfig config) {
        this.config = config;
    }

    public DamageBudgetConfig config() {
        return config;
    }

    @Override
    public DamageGrant submit(DamageRequest r) {
        double power = BlastScaling.power(r.energyJ(), config.tntReferenceKg(), config.maxExplosionPower());
        double radius = Math.min(Math.min(BlastScaling.radiusBlocks(power), r.maxRadiusM()), MAX_RADIUS);
        int requested = Offsets.countWithin(radius);
        if (config.mode() == ServerMode.CREATIVE_SAFE || power <= 0 || config.maxBlocksPerTick() == 0) {
            deny(DenyReason.MODE_FORBIDS, requested);
            return new DamageGrant(r.requestId(), 0, 0, requested, requested > 0 ? DenyReason.MODE_FORBIDS : DenyReason.NONE);
        }

        int[] granted = new int[Math.min(requested, config.maxBlocksPerEvent())];
        int count = 0;
        int chunkDenied = 0, regionDenied = 0, queueDenied = 0;
        int i = 0;
        GridPos o = r.origin();
        for (; i < requested && count < granted.length; i++) {
            int x = o.x() + Offsets.dx(i), z = o.z() + Offsets.dz(i);
            long chunk = GridPos.chunkKey(x >> 4, z >> 4);
            long region = GridPos.chunkKey(x >> 9, z >> 9);
            if (pending + count >= config.maxPending()) {
                queueDenied++;
                continue;
            }
            int cu = chunkUsed.getOrDefault(chunk, 0);
            if (cu >= config.maxBlocksPerChunkPerDay()) {
                chunkDenied++;
                continue;
            }
            int ru = regionUsed.getOrDefault(region, 0);
            if (ru >= config.maxBlocksPerRegionPerDay()) {
                regionDenied++;
                continue;
            }
            chunkUsed.put(chunk, cu + 1);
            regionUsed.put(region, ru + 1);
            granted[count++] = i;
        }
        int eventDenied = requested - i;

        deny(DenyReason.PER_EVENT_CAP, eventDenied);
        deny(DenyReason.PER_CHUNK_CAP, chunkDenied);
        deny(DenyReason.PER_REGION_CAP, regionDenied);
        deny(DenyReason.QUEUE_FULL, queueDenied);
        DenyReason main = DenyReason.NONE;
        int mainCount = 0;
        if (eventDenied > mainCount) { main = DenyReason.PER_EVENT_CAP; mainCount = eventDenied; }
        if (chunkDenied > mainCount) { main = DenyReason.PER_CHUNK_CAP; mainCount = chunkDenied; }
        if (regionDenied > mainCount) { main = DenyReason.PER_REGION_CAP; mainCount = regionDenied; }
        if (queueDenied > mainCount) main = DenyReason.QUEUE_FULL;

        if (count > 0) {
            Entry e = new Entry(r, power, Arrays.copyOf(granted, count), seq++);
            int at = 0;
            while (at < queue.size() && queue.get(at).before(e)) at++;
            queue.add(at, e);
            pending += count;
        }
        return new DamageGrant(r.requestId(), count, count, requested - count, main);
    }

    @Override
    public void drain(long gameTick, BlockMutator mutator, TerritoryOracle territory) {
        long d = Math.floorDiv(gameTick, TICKS_PER_DAY);
        if (d != day) {
            day = d;
            chunkUsed.clear();
            regionUsed.clear();
        }
        int budget = config.maxBlocksPerTick();
        int inspections = budget * INSPECTIONS_PER_OPERATION;
        while (budget > 0 && inspections > 0 && !queue.isEmpty()) {
            Entry e = queue.get(0);
            int offset = e.offsets[e.cursor++];
            pending--;
            inspections--;
            GridPos o = e.request.origin();
            GridPos p = o.offset(Offsets.dx(offset), Offsets.dy(offset), Offsets.dz(offset));
            BlockInfo info = mutator.inspect(p);
            if (info.air()) {
                // менять нечего: операция бюджета не тратится
            } else if (info.immune() || !BlastScaling.breaks(e.power, Offsets.distance(offset), info.blastResistance())) {
                deny(DenyReason.PROTECTED_BLOCK, 1);
            } else if (config.mode() == ServerMode.CONTAINED && !territory.mayDamage(e.request.responsibleTeam(), p)) {
                deny(DenyReason.OUTSIDE_TERRITORY, 1);
            } else {
                boolean drop = e.drops < config.maxItemEntitiesPerEvent()
                        && Math.floor((e.applied + 1) * config.itemDropFraction()) > Math.floor(e.applied * config.itemDropFraction());
                mutator.destroy(p, drop);
                if (drop) e.drops++;
                e.applied++;
                appliedTotal++;
                budget--;
            }
            if (e.cursor == e.offsets.length) queue.remove(0);
        }
    }

    @Override
    public BudgetStats stats() {
        return new BudgetStats(appliedTotal, pending, deniedTotal, deniedByReason);
    }

    private void deny(DenyReason reason, int n) {
        if (n <= 0) return;
        deniedTotal += n;
        deniedByReason.merge(reason, (long) n, Long::sum);
    }

    private static final class Entry {
        final DamageRequest request;
        final double power;
        final int[] offsets;
        final long seq;
        int cursor;
        int applied;
        int drops;

        Entry(DamageRequest request, double power, int[] offsets, long seq) {
            this.request = request;
            this.power = power;
            this.offsets = offsets;
            this.seq = seq;
        }

        boolean before(Entry o) {
            if (request.priority() != o.request.priority()) return request.priority() < o.request.priority();
            return seq < o.seq;
        }
    }

    /** Смещения сферы радиуса {@link #MAX_RADIUS}, отсортированные по расстоянию, затем по (y, z, x). */
    static final class Offsets {
        private static final int[] PACKED;
        private static final int[] DIST2;

        static {
            int r = MAX_RADIUS;
            List<int[]> all = new ArrayList<>();
            for (int y = -r; y <= r; y++)
                for (int z = -r; z <= r; z++)
                    for (int x = -r; x <= r; x++) {
                        int d2 = x * x + y * y + z * z;
                        if (d2 <= r * r) all.add(new int[] {d2, y, z, x});
                    }
            all.sort((a, b) -> {
                for (int k = 0; k < 4; k++) if (a[k] != b[k]) return Integer.compare(a[k], b[k]);
                return 0;
            });
            PACKED = new int[all.size()];
            DIST2 = new int[all.size()];
            for (int i = 0; i < all.size(); i++) {
                int[] a = all.get(i);
                DIST2[i] = a[0];
                PACKED[i] = ((a[3] + 64) << 16) | ((a[1] + 64) << 8) | (a[2] + 64);
            }
        }

        private Offsets() {
        }

        /** Число смещений с d ≤ radius. */
        static int countWithin(double radius) {
            if (radius < 0) return 0;
            int limit = (int) Math.floor(radius * radius + 1e-9);
            int lo = 0, hi = DIST2.length;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (DIST2[mid] <= limit) lo = mid + 1;
                else hi = mid;
            }
            return lo;
        }

        static int dx(int i) {
            return (PACKED[i] >>> 16) - 64;
        }

        static int dy(int i) {
            return ((PACKED[i] >>> 8) & 0xFF) - 64;
        }

        static int dz(int i) {
            return (PACKED[i] & 0xFF) - 64;
        }

        static double distance(int i) {
            return Math.sqrt(DIST2[i]);
        }
    }
}
