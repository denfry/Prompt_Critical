package dev.denfry.promptcritical.sim.reactor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Кладка графитового котла, найденная биндингом: узлы 1 м³ (блок), стержни в колоннах каналов и счётчики у
 * поверхности. Координаты — блочные, {@code y} — вертикаль. Порядок узлов задаёт биндинг; соседи и подпись
 * считаются здесь. Неизменяем после {@link Builder#build()}.
 */
public final class PileLayout {
    /** Кладка без узлов: котёл не собран. */
    public static final PileLayout EMPTY = new Builder().build();
    /** Узлов в кладке не больше (нодальная задача — на серверном потоке раз в секунду). */
    public static final int MAX_NODES = 2048;
    public static final int MAX_RODS = 64, MAX_DETECTORS = 8;
    /** Кассет блочков в одном топливном блоке: при полной загрузке узел — решётка T0 (24 канала на м²). */
    public static final int FULL_LOAD = 8;

    public enum NodeKind {
        /** Графит без топлива: замедлитель и отражатель. */
        GRAPHITE,
        /** Графит с каналами блочков; загрузка 0…{@link #FULL_LOAD}. */
        FUEL,
        /** Графит с каналом стержня. */
        ROD_CHANNEL,
        /** Графит с Ra-Be источником. */
        SOURCE
    }

    public enum RodKind {
        /** Регулирующий: привод в обе стороны. */
        CONTROL,
        /** Аварийный «zip»: на верёвке лебёдки, сброс — падение под весом. */
        ZIP
    }

    /** Стержень в колонне каналов; {@code nodes} — сверху вниз; {@code key} — позиция привода у биндинга. */
    public record Rod(long key, RodKind kind, int[] nodes) {
        public Rod {
            nodes = nodes.clone();
        }

        @Override
        public int[] nodes() {
            return nodes.clone();
        }

        public int length() {
            return nodes.length;
        }

        int node(int i) {
            return nodes[i];
        }
    }

    /** Счётчик у узла кладки; {@code key} — позиция блока у биндинга. */
    public record Detector(long key, int node) {}

    private final int[] x, y, z, load;
    private final NodeKind[] kind;
    private final Rod[] rods;
    private final Detector[] detectors;
    private final int[] neighbors;
    private final long signature;

    private PileLayout(Builder b) {
        int n = b.count;
        x = Arrays.copyOf(b.x, n);
        y = Arrays.copyOf(b.y, n);
        z = Arrays.copyOf(b.z, n);
        load = Arrays.copyOf(b.load, n);
        kind = Arrays.copyOf(b.kind, n);
        rods = b.rods.toArray(new Rod[0]);
        detectors = b.detectors.toArray(new Detector[0]);
        neighbors = new int[6 * n];
        for (int i = 0; i < n; i++) {
            // порядок решателя: −x, +x, −y, +y, −z, +z; вертикаль мира — ось z решателя
            neighbors[6 * i] = b.at(x[i] - 1, y[i], z[i]);
            neighbors[6 * i + 1] = b.at(x[i] + 1, y[i], z[i]);
            neighbors[6 * i + 2] = b.at(x[i], y[i], z[i] - 1);
            neighbors[6 * i + 3] = b.at(x[i], y[i], z[i] + 1);
            neighbors[6 * i + 4] = b.at(x[i], y[i] - 1, z[i]);
            neighbors[6 * i + 5] = b.at(x[i], y[i] + 1, z[i]);
        }
        long h = 1125899906842597L;
        for (int i = 0; i < n; i++) {
            h = 31 * h + x[i];
            h = 31 * h + y[i];
            h = 31 * h + z[i];
            h = 31 * h + kind[i].ordinal() * 16 + load[i];
        }
        for (Rod r : rods) {
            h = 31 * h + r.key();
            h = 31 * h + r.kind().ordinal();
            for (int i : r.nodes) h = 31 * h + i;
        }
        signature = h;
    }

    public int nodeCount() {
        return kind.length;
    }

    public NodeKind kind(int node) {
        return kind[node];
    }

    public int load(int node) {
        return load[node];
    }

    public int x(int node) {
        return x[node];
    }

    public int y(int node) {
        return y[node];
    }

    public int z(int node) {
        return z[node];
    }

    /** Соседи узлов для {@code NodalFluxSolver}: 6 на узел, −1 — воздух. */
    public int[] neighbors() {
        return neighbors.clone();
    }

    int neighbor(int node, int face) {
        return neighbors[6 * node + face];
    }

    public int rodCount() {
        return rods.length;
    }

    public Rod rod(int i) {
        return rods[i];
    }

    public int detectorCount() {
        return detectors.length;
    }

    public Detector detector(int i) {
        return detectors[i];
    }

    /** Граней кладки, выходящих на воздух (охлаждение конвекцией). */
    public int exposedFaces() {
        int f = 0;
        for (int v : neighbors) if (v < 0) f++;
        return f;
    }

    /** Узлов с топливом и суммарная загрузка в кассетах. */
    public int fuelNodes() {
        int c = 0;
        for (int i = 0; i < kind.length; i++) if (kind[i] == NodeKind.FUEL && load[i] > 0) c++;
        return c;
    }

    public int totalLoad() {
        int c = 0;
        for (int i = 0; i < kind.length; i++) if (kind[i] == NodeKind.FUEL) c += load[i];
        return c;
    }

    public int sources() {
        int c = 0;
        for (NodeKind k : kind) if (k == NodeKind.SOURCE) c++;
        return c;
    }

    /** Подпись строения: смена — новая нодальная задача. */
    public long signature() {
        return signature;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int[] x = new int[64], y = new int[64], z = new int[64], load = new int[64];
        private NodeKind[] kind = new NodeKind[64];
        private int count;
        private final Map<Long, Integer> index = new HashMap<>();
        private final java.util.List<Rod> rods = new java.util.ArrayList<>();
        private final java.util.List<Detector> detectors = new java.util.ArrayList<>();

        private static long pack(int x, int y, int z) {
            return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
        }

        int at(int x, int y, int z) {
            Integer i = index.get(pack(x, y, z));
            return i == null ? -1 : i;
        }

        /** Индекс узла в {@code (x, y, z)} или −1. */
        public int indexOf(int x, int y, int z) {
            return at(x, y, z);
        }

        public int nodeCount() {
            return count;
        }

        /** Новый узел; возвращает индекс. Повтор позиции и переполнение — ошибка. */
        public int addNode(int x, int y, int z, NodeKind kind, int load) {
            if (count >= MAX_NODES) throw new IllegalStateException("too many nodes");
            if (load < 0 || load > FULL_LOAD || (kind != NodeKind.FUEL && load != 0)) throw new IllegalArgumentException("load");
            long key = pack(x, y, z);
            if (index.containsKey(key)) throw new IllegalArgumentException("duplicate node");
            if (count == this.x.length) {
                int n = count * 2;
                this.x = Arrays.copyOf(this.x, n);
                this.y = Arrays.copyOf(this.y, n);
                this.z = Arrays.copyOf(this.z, n);
                this.load = Arrays.copyOf(this.load, n);
                this.kind = Arrays.copyOf(this.kind, n);
            }
            this.x[count] = x;
            this.y[count] = y;
            this.z[count] = z;
            this.load[count] = load;
            this.kind[count] = kind;
            index.put(key, count);
            return count++;
        }

        /** Стержень в колонне {@code nodes} (сверху вниз, узлы — каналы стержня). */
        public Builder addRod(long key, RodKind kind, int[] nodes) {
            if (rods.size() >= MAX_RODS) throw new IllegalStateException("too many rods");
            if (nodes.length == 0) throw new IllegalArgumentException("empty rod");
            for (int i : nodes) {
                if (i < 0 || i >= count || this.kind[i] != NodeKind.ROD_CHANNEL) throw new IllegalArgumentException("rod node");
            }
            for (Rod r : rods) {
                for (int a : r.nodes) for (int b : nodes) if (a == b) throw new IllegalArgumentException("shared rod channel");
            }
            rods.add(new Rod(key, kind, nodes));
            return this;
        }

        public Builder addDetector(long key, int node) {
            if (detectors.size() >= MAX_DETECTORS) throw new IllegalStateException("too many detectors");
            if (node < 0 || node >= count) throw new IllegalArgumentException("detector node");
            detectors.add(new Detector(key, node));
            return this;
        }

        public PileLayout build() {
            return new PileLayout(this);
        }
    }
}
