package dev.denfry.promptcritical.sim.mining;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Граф выработок (08 §8.2.2, §8.2.7): узлы — объёмы воздуха, ветви — выработки между ними. Узел 0 —
 * {@link #ATMOSPHERE}, поверхность с наружным воздухом; к нему подходят стволы и шурфы. Граф строит привязка
 * заливкой при изменении блоков (фоновый пул 05) и отдаёт готовым; горячий цикл только читает SoA-массивы.
 *
 * <p>Независимые контуры для Харди Кросса — по остовному лесу: каждая хорда замыкает один контур.
 */
public final class MineTopology {
    public static final int ATMOSPHERE = 0;
    /** Ключ узла поверхности. */
    public static final long ATMOSPHERE_KEY = Long.MIN_VALUE;
    /** Закрытая глухая дверь, Н·с²/м⁸ (08 §8.2.7). */
    public static final double DOOR_RESISTANCE = 50;

    // узлы
    final long[] nodeKey;
    final double[] volume, elevation, section, perimeter, tempK;
    final int[] blockCount;
    // ветви
    final long[] branchKey;
    final int[] from, to, fanOf, doorOf;
    final double[] length, branchSection, resistance, barrierKg;
    final BranchKind[] kind;
    final BarrierKind[] barrierKind;
    // устройства
    final int[] fanBranch, doorBranch;
    final FanSpec[] fanSpec;
    final DoorKind[] doorKind;
    // смежность узлов (CSR) и контуры (CSR: ветвь и знак обхода)
    final int[] adjStart, adjBranch;
    final int[] loopStart, loopBranch, loopSign;
    final int[] branchLoopStart, branchLoop, branchLoopSign;

    private final LongIntMap blockToNode, nodeIndex, branchIndex;

    private MineTopology(Builder b) {
        int nn = b.nodes.size(), nb = b.branches.size();
        nodeKey = new long[nn];
        volume = new double[nn];
        elevation = new double[nn];
        section = new double[nn];
        perimeter = new double[nn];
        tempK = new double[nn];
        blockCount = new int[nn];
        nodeIndex = new LongIntMap(nn);
        for (int i = 0; i < nn; i++) {
            NodeDef d = b.nodes.get(i);
            nodeKey[i] = d.key;
            volume[i] = d.volume;
            elevation[i] = d.elevation;
            section[i] = d.section;
            perimeter[i] = d.perimeter;
            tempK[i] = d.tempK;
            nodeIndex.put(d.key, i);
        }
        branchKey = new long[nb];
        from = new int[nb];
        to = new int[nb];
        length = new double[nb];
        branchSection = new double[nb];
        resistance = new double[nb];
        kind = new BranchKind[nb];
        fanOf = new int[nb];
        doorOf = new int[nb];
        barrierKg = new double[nb];
        barrierKind = new BarrierKind[nb];
        Arrays.fill(fanOf, -1);
        Arrays.fill(doorOf, -1);
        branchIndex = new LongIntMap(nb);
        for (int j = 0; j < nb; j++) {
            BranchDef d = b.branches.get(j);
            branchKey[j] = d.key;
            from[j] = d.from;
            to[j] = d.to;
            length[j] = d.length;
            branchSection[j] = d.section;
            resistance[j] = atkinsonResistance(d.alpha, d.perimeter, d.length, d.section);
            kind[j] = d.kind;
            branchIndex.put(d.key, j);
        }
        fanBranch = new int[b.fans.size()];
        fanSpec = new FanSpec[fanBranch.length];
        for (int f = 0; f < fanBranch.length; f++) {
            fanBranch[f] = b.fans.get(f).branch;
            fanSpec[f] = b.fans.get(f).spec;
            fanOf[fanBranch[f]] = f;
        }
        doorBranch = new int[b.doors.size()];
        doorKind = new DoorKind[doorBranch.length];
        for (int d = 0; d < doorBranch.length; d++) {
            doorBranch[d] = b.doors.get(d).branch;
            doorKind[d] = b.doors.get(d).kind;
            doorOf[doorBranch[d]] = d;
        }
        for (BarrierDef d : b.barriers) {
            barrierKg[d.branch] += d.inertKg;
            barrierKind[d.branch] = d.kind;
        }
        blockToNode = b.blocks;
        for (int s = 0; s < blockToNode.capacity(); s++)
            if (blockToNode.usedAt(s)) blockCount[blockToNode.valueAt(s)]++;

        // смежность
        adjStart = new int[nn + 1];
        for (int j = 0; j < nb; j++) {
            adjStart[from[j] + 1]++;
            adjStart[to[j] + 1]++;
        }
        for (int i = 0; i < nn; i++) adjStart[i + 1] += adjStart[i];
        adjBranch = new int[2 * nb];
        int[] fill = Arrays.copyOf(adjStart, nn);
        for (int j = 0; j < nb; j++) {
            adjBranch[fill[from[j]]++] = j;
            adjBranch[fill[to[j]]++] = j;
        }

        // Остовный лес минимального сопротивления (Крускал): хордами становятся вентиляторы и самые «тяжёлые»
        // ветви — двери, лавы; диагональ матрицы контуров тогда преобладает и итерации сходятся быстрее
        boolean[] tree = new boolean[nb];
        Integer[] byR = new Integer[nb];
        double[] weight = new double[nb];
        for (int j = 0; j < nb; j++) {
            byR[j] = j;
            weight[j] = fanOf[j] >= 0 ? Double.POSITIVE_INFINITY : resistance[j] + (doorOf[j] >= 0 ? DOOR_RESISTANCE : 0);
        }
        Arrays.sort(byR, (x, y) -> Double.compare(weight[x], weight[y]));
        int[] set = new int[nn];
        for (int i = 0; i < nn; i++) set[i] = i;
        for (int j : byR) {
            int ra = find(set, from[j]), rb = find(set, to[j]);
            if (ra == rb) continue;
            set[ra] = rb;
            tree[j] = true;
        }
        int[] parent = new int[nn], parentBranch = new int[nn], depth = new int[nn];
        boolean[] seen = new boolean[nn];
        int[] queue = new int[nn];
        for (int root = 0; root < nn; root++) {
            if (seen[root]) continue;
            int head = 0, tail = 0;
            queue[tail++] = root;
            seen[root] = true;
            parent[root] = -1;
            parentBranch[root] = -1;
            while (head < tail) {
                int u = queue[head++];
                for (int a = adjStart[u]; a < adjStart[u + 1]; a++) {
                    int j = adjBranch[a], v = other(j, u);
                    if (!tree[j] || seen[v]) continue;
                    seen[v] = true;
                    parent[v] = u;
                    parentBranch[v] = j;
                    depth[v] = depth[u] + 1;
                    queue[tail++] = v;
                }
            }
        }
        // контур хорды from→to: сама хорда, подъём по дереву от to до общего предка, спуск от него к from
        List<int[]> loops = new ArrayList<>();
        int total = 0;
        int[] upNode = new int[nn + 1], downNode = new int[nn + 1];
        for (int j = 0; j < nb; j++) {
            if (tree[j]) continue;
            int nu = 0, nd = 0;
            upNode[0] = to[j];
            downNode[0] = from[j];
            int a = to[j], c = from[j];
            while (depth[a] > depth[c]) upNode[++nu] = a = parent[a];
            while (depth[c] > depth[a]) downNode[++nd] = c = parent[c];
            while (a != c) {
                upNode[++nu] = a = parent[a];
                downNode[++nd] = c = parent[c];
            }
            int[] loop = new int[2 * (1 + nu + nd)];
            int w = 0;
            loop[w++] = j;
            loop[w++] = 1;
            for (int k = 0; k < nu; k++) { // шаг upNode[k] → upNode[k+1]
                int u = upNode[k], e = parentBranch[u];
                loop[w++] = e;
                loop[w++] = from[e] == u ? 1 : -1;
            }
            for (int k = nd - 1; k >= 0; k--) { // шаг downNode[k+1] → downNode[k]
                int u = downNode[k], e = parentBranch[u];
                loop[w++] = e;
                loop[w++] = to[e] == u ? 1 : -1;
            }
            loops.add(loop);
            total += 1 + nu + nd;
        }
        loopStart = new int[loops.size() + 1];
        loopBranch = new int[total];
        loopSign = new int[total];
        int w = 0;
        for (int k = 0; k < loops.size(); k++) {
            int[] l = loops.get(k);
            for (int i = 0; i < l.length; i += 2) {
                loopBranch[w] = l[i];
                loopSign[w++] = l[i + 1];
            }
            loopStart[k + 1] = w;
        }
        // обратная связь: контуры каждой ветви (для матрицы Ньютона)
        branchLoopStart = new int[nb + 1];
        for (int i = 0; i < total; i++) branchLoopStart[loopBranch[i] + 1]++;
        for (int j = 0; j < nb; j++) branchLoopStart[j + 1] += branchLoopStart[j];
        branchLoop = new int[total];
        branchLoopSign = new int[total];
        int[] at = Arrays.copyOf(branchLoopStart, nb);
        for (int k = 0; k < loops.size(); k++)
            for (int i = loopStart[k]; i < loopStart[k + 1]; i++) {
                int p = at[loopBranch[i]]++;
                branchLoop[p] = k;
                branchLoopSign[p] = loopSign[i];
            }
    }

    private static int find(int[] set, int i) {
        while (set[i] != i) i = set[i] = set[set[i]];
        return i;
    }

    /** Сопротивление ветви по Аткинсону R = αPL/S³, Н·с²/м⁸ (08 §8.2.7). */
    public static double atkinsonResistance(double alpha, double perimeterM, double lengthM, double sectionM2) {
        return alpha * perimeterM * lengthM / (sectionM2 * sectionM2 * sectionM2);
    }

    /**
     * @param surfaceElevationM отметка поверхности (устьев стволов), м
     * @param surfaceTempK      температура наружного воздуха, К
     */
    public static Builder builder(double surfaceElevationM, double surfaceTempK) {
        return new Builder(surfaceElevationM, surfaceTempK);
    }

    public int nodeCount() {
        return nodeKey.length;
    }

    public int branchCount() {
        return branchKey.length;
    }

    public int fanCount() {
        return fanBranch.length;
    }

    public int doorCount() {
        return doorBranch.length;
    }

    public int loopCount() {
        return loopStart.length - 1;
    }

    /** Узел, которому заливка приписала блок (воздух или обнажённая стенка), или −1. */
    public int nodeOf(long blockKey) {
        return blockToNode.get(blockKey);
    }

    /** Индекс узла по ключу или −1. */
    public int nodeIndex(long key) {
        return nodeIndex.get(key);
    }

    /** Индекс ветви по ключу или −1. */
    public int branchIndex(long key) {
        return branchIndex.get(key);
    }

    public long nodeKey(int node) {
        return nodeKey[node];
    }

    public long branchKey(int branch) {
        return branchKey[branch];
    }

    public double volumeM3(int node) {
        return volume[node];
    }

    public double elevationM(int node) {
        return elevation[node];
    }

    public double sectionM2(int node) {
        return section[node];
    }

    public double perimeterM(int node) {
        return perimeter[node];
    }

    /** Число блоков, приписанных узлу. */
    public int blockCount(int node) {
        return blockCount[node];
    }

    public double branchSectionM2(int branch) {
        return branchSection[branch];
    }

    public int from(int branch) {
        return from[branch];
    }

    public int to(int branch) {
        return to[branch];
    }

    /** Аэродинамическое сопротивление самой выработки (без дверей и вентилятора), Н·с²/м⁸. */
    public double resistance(int branch) {
        return resistance[branch];
    }

    public BranchKind kind(int branch) {
        return kind[branch];
    }

    /** Масса инертного в заслонах ветви, кг. */
    public double barrierInertKg(int branch) {
        return barrierKg[branch];
    }

    /** Ветвь вентилятора. */
    public int fanBranch(int fan) {
        return fanBranch[fan];
    }

    public FanSpec fanSpec(int fan) {
        return fanSpec[fan];
    }

    public int doorBranch(int door) {
        return doorBranch[door];
    }

    public DoorKind doorKind(int door) {
        return doorKind[door];
    }

    int other(int branch, int node) {
        return from[branch] == node ? to[branch] : from[branch];
    }

    private record NodeDef(long key, double volume, double elevation, double section, double perimeter,
            double tempK) {}

    private record BranchDef(long key, int from, int to, double length, double section, double perimeter,
            double alpha, BranchKind kind) {}

    private record FanDef(int branch, FanSpec spec) {}

    private record DoorDef(int branch, DoorKind kind) {}

    private record BarrierDef(int branch, BarrierKind kind, double inertKg) {}

    /** Сборка графа; узел 0 (поверхность) создаётся сам. */
    public static final class Builder {
        private final List<NodeDef> nodes = new ArrayList<>();
        private final List<BranchDef> branches = new ArrayList<>();
        private final List<FanDef> fans = new ArrayList<>();
        private final List<DoorDef> doors = new ArrayList<>();
        private final List<BarrierDef> barriers = new ArrayList<>();
        private final LongIntMap keys = new LongIntMap(64), bKeys = new LongIntMap(64), blocks = new LongIntMap(256);
        private boolean built;

        private Builder(double surfaceElevationM, double surfaceTempK) {
            if (!(surfaceTempK > 0)) throw new IllegalArgumentException("surfaceTempK");
            nodes.add(new NodeDef(ATMOSPHERE_KEY, 0, surfaceElevationM, 0, 0, surfaceTempK));
            keys.put(ATMOSPHERE_KEY, ATMOSPHERE);
        }

        /**
         * Узел-объём выработки.
         *
         * @param key        устойчивый ключ (для переноса состояния при перестройке)
         * @param volumeM3   объём воздуха, м³
         * @param elevationM отметка (Y блока), м
         * @param sectionM2  характерное сечение S, м²
         * @param perimeterM периметр сечения P, м
         * @param airTempK   температура воздуха (с глубиной растёт от пород), К
         * @return индекс узла
         */
        public int node(long key, double volumeM3, double elevationM, double sectionM2, double perimeterM,
                double airTempK) {
            if (keys.get(key) != LongIntMap.MISSING) throw new IllegalArgumentException("duplicate node key " + key);
            if (!(volumeM3 > 0 && sectionM2 > 0 && perimeterM > 0 && airTempK > 0))
                throw new IllegalArgumentException("node " + key);
            keys.put(key, nodes.size());
            nodes.add(new NodeDef(key, volumeM3, elevationM, sectionM2, perimeterM, airTempK));
            return nodes.size() - 1;
        }

        /**
         * Ветвь-выработка; положительный расход — от {@code from} к {@code to}.
         *
         * @param alpha коэффициент Аткинсона α, Н·с²/м⁴ (см. {@link AirwayLining})
         */
        public int branch(long key, int from, int to, double lengthM, double sectionM2, double perimeterM,
                double alpha, BranchKind kind) {
            if (bKeys.get(key) != LongIntMap.MISSING) throw new IllegalArgumentException("duplicate branch key " + key);
            if (from < 0 || from >= nodes.size() || to < 0 || to >= nodes.size() || from == to)
                throw new IllegalArgumentException("branch " + key + ": " + from + "→" + to);
            if (!(lengthM > 0 && sectionM2 > 0 && perimeterM > 0 && alpha > 0) || kind == null)
                throw new IllegalArgumentException("branch " + key);
            bKeys.put(key, branches.size());
            branches.add(new BranchDef(key, from, to, lengthM, sectionM2, perimeterM, alpha, kind));
            return branches.size() - 1;
        }

        public int fan(int branch, FanSpec spec) {
            checkBranch(branch);
            for (FanDef f : fans) if (f.branch == branch) throw new IllegalArgumentException("second fan in " + branch);
            fans.add(new FanDef(branch, spec));
            return fans.size() - 1;
        }

        public int door(int branch, DoorKind kind) {
            checkBranch(branch);
            for (DoorDef d : doors) if (d.branch == branch) throw new IllegalArgumentException("second door in " + branch);
            doors.add(new DoorDef(branch, kind));
            return doors.size() - 1;
        }

        /** Заслон в ветви; несколько заслонов в одной ветви складываются. */
        public Builder barrier(int branch, BarrierKind kind, double inertKg) {
            checkBranch(branch);
            if (!(inertKg >= 0)) throw new IllegalArgumentException("inertKg");
            barriers.add(new BarrierDef(branch, kind, inertKg));
            return this;
        }

        /** Приписать блок узлу; повторная приписка переносит блок. */
        public Builder assignBlock(long blockKey, int node) {
            if (node < 1 || node >= nodes.size()) throw new IllegalArgumentException("node " + node);
            blocks.put(blockKey, node);
            return this;
        }

        private void checkBranch(int branch) {
            if (branch < 0 || branch >= branches.size()) throw new IllegalArgumentException("branch " + branch);
        }

        public MineTopology build() {
            if (built) throw new IllegalStateException("builder already used");
            built = true;
            return new MineTopology(this);
        }
    }
}
