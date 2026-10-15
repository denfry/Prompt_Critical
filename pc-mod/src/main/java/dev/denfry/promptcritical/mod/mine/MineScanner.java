package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.sim.mining.AirwayLining;
import dev.denfry.promptcritical.sim.mining.BarrierKind;
import dev.denfry.promptcritical.sim.mining.BranchKind;
import dev.denfry.promptcritical.sim.mining.DoorKind;
import dev.denfry.promptcritical.sim.mining.FanSpec;
import dev.denfry.promptcritical.sim.mining.MethaneField;
import dev.denfry.promptcritical.sim.mining.MineTopology;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Заливка выработок и сборка {@link MineTopology} (08 §8.2.2, §8.2.7). Выработка — связный воздух (и проходимые
 * блоки: крепь, заслоны, факелы) под поверхностью; воздух над верхним непустым блоком столбца — поверхность. Узлы — связные части
 * выработки в ячейках {@value #CELL}³ блоков; ветви — грани между узлами соседних ячеек (сечение — число граней),
 * выходы на поверхность, двери и вентиляторы. Заслон относится ко всем ветвям своего узла: фронт не входит в узел
 * с заслоном и не выходит из него.
 *
 * <p>Ключи блоков — {@code BlockPos.asLong()} (совпадает с {@code GridPos.asLong()} домена). Ключ узла — ячейка и
 * порядковый номер части в ней, поэтому пока выработка растёт внутри ячейки, газ узла сохраняется.
 */
public final class MineScanner {
    /** Сторона ячейки узла, блоков. */
    public static final int CELL = 8;
    /** Вентилятор главного проветривания на номинале (08 §8.2.7) под выработки 2–9 м² [BALANCE]. */
    public static final FanSpec MAIN_FAN = FanSpec.of(1200, 12, 300, 0.7);
    /** Температура наружного воздуха и геотермический градиент [допущение: 3 К на 100 м]. */
    public static final double SURFACE_TEMP_K = 288.15, GEOTHERMAL_K_PER_M = 0.03;
    /** Окно регулятора в положении «закрыто», доля сечения проёма. */
    public static final double REGULATOR_CLOSED_FRACTION = 0.25;

    private static final byte SOLID = 0, AIR = 1, OUTSIDE = 2, DOOR = 3, FAN = 4;
    private static final Direction[] DIRS = Direction.values();

    /** Итог заливки: граф и привязка устройств к его индексам. */
    public static final class Scan {
        public MineTopology topology;
        public long[] origins;
        public boolean[] flames;
        public long[] coalKeys = new long[0];
        public double[] coalX = new double[0];
        public int coalCount;
        /** Контроллер вентилятора / двери по индексу в графе. */
        public long[] fanCtrl = new long[0], doorCtrl = new long[0];
        public double[] doorSection = new double[0];
        /** Скважина дегазации → узел. */
        public final Long2IntLinkedOpenHashMap boreholeNode = new Long2IntLinkedOpenHashMap();
        /** Якорные блоки, которых коснулась заливка. */
        public final LongLinkedOpenHashSet anchors = new LongLinkedOpenHashSet();
        /** Клетки канареек в выработке. */
        public final LongArrayList cages = new LongArrayList();
        /** Блоки узлов (CSR по индексу узла графа). */
        public int[] nodeStart = new int[1];
        public long[] nodeBlocks = new long[0];
        public BoundingBox bounds;
        public int airBlocks;
        public boolean truncated;
        /** Первая клетка в незагруженном чанке (для журнала). */
        public long firstUnloaded;
    }

    private final ServerLevel level;
    private final int cap;
    private final double barrierKgPerBlock, gasX0;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private long lastCtrl, firstUnloaded;
    private boolean truncated;

    public MineScanner(ServerLevel level, int maxBlocks, double barrierKgPerBlock, double gasX0) {
        this.level = level;
        this.cap = maxBlocks;
        this.barrierKgPerBlock = barrierKgPerBlock;
        this.gasX0 = gasX0;
    }

    // --- классификация -------------------------------------------------------------------------------------

    /** Якорь шахты: блок оборудования (контроллер) или проходимый воздух выработки. */
    public static boolean isAnchorBlock(BlockState s) {
        Block b = s.getBlock();
        if (b instanceof PcBlock pc && !pc.isController(s)) return false;
        return b == PcBlocks.MAIN_MINE_FAN.get() || b == PcBlocks.VENTILATION_DOOR.get() || b == PcBlocks.MINE_SUPPORT.get()
                || b == PcBlocks.EXPLOSION_BARRIER.get() || b == PcBlocks.MINE_DISPATCH_CABINET.get()
                || b == PcBlocks.DEGASIFICATION_BOREHOLE.get();
    }

    private static boolean minePassable(Block b) {
        return b == PcBlocks.MINE_SUPPORT.get() || b == PcBlocks.EXPLOSION_BARRIER.get() || b == PcBlocks.CANARY_CAGE.get();
    }

    /** Класс клетки; контроллер многоклеточного блока — в {@link #lastCtrl}. */
    private byte classify(long key) {
        BlockPos pos = cursor.set(key);
        lastCtrl = key;
        if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) return SOLID;
        if (!level.isLoaded(pos)) {
            if (!truncated) firstUnloaded = key;
            truncated = true;
            return SOLID;
        }
        BlockState s = level.getBlockState(pos);
        Block b = s.getBlock();
        if (b instanceof MultiblockPartBlock) {
            BlockPos anchor = MultiblockPartBlock.anchor(s, pos);
            BlockPos ctrl = PcBlock.structureController(level, anchor);
            if (ctrl == null) ctrl = anchor;
            lastCtrl = ctrl.asLong();
            b = level.getBlockState(ctrl).getBlock();
            cursor.set(key);
        }
        if (b == PcBlocks.VENTILATION_DOOR.get()) return DOOR;
        if (b == PcBlocks.MAIN_MINE_FAN.get()) return FAN;
        boolean passable = minePassable(b) || s.isAir()
                || s.getFluidState().isEmpty() && s.getCollisionShape(level, pos).isEmpty();
        if (!passable) return SOLID;
        return surface(pos) ? OUTSIDE : AIR;
    }

    /**
     * Поверхность — воздух над верхним непустым блоком столбца (карта высот обновляется сразу, свет неба — с
     * задержкой). Служебные невидимые блоки ({@code barrier}) над столбцом — не порода: столбец под ними
     * просматривается до первого настоящего блока.
     */
    private boolean surface(BlockPos pos) {
        int x = pos.getX(), z = pos.getZ(), top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        if (pos.getY() >= top) return true;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, top - 1, z);
        if (!level.getBlockState(p).is(Blocks.BARRIER)) return false;
        for (int y = top - 1; y > pos.getY(); y--) {
            BlockState s = level.getBlockState(p.setY(y));
            if (!s.isAir() && !s.is(Blocks.BARRIER)) return false;
        }
        return true;
    }

    /** Проходимая клетка выработки (не поверхность) — допустимый якорь из воздуха. */
    public boolean isMineAir(BlockPos pos) {
        return classify(pos.asLong()) == AIR;
    }

    // --- заливка -------------------------------------------------------------------------------------------

    public Scan scan(long[] seeds) {
        truncated = false;
        Scan out = new Scan();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongArrayList air = new LongArrayList();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        LongArrayList outAir = new LongArrayList(), outDir = new LongArrayList();
        LongArrayList doorOf = new LongArrayList(), doorAir = new LongArrayList();
        LongArrayList fanOf = new LongArrayList(), fanAir = new LongArrayList();
        Long2LongLinkedOpenHashMap coalAir = new Long2LongLinkedOpenHashMap(), boreAir = new Long2LongLinkedOpenHashMap();

        for (long seed : seeds) {
            for (long c : structureCells(seed)) {
                visit(c, visited, air, queue);
                for (Direction d : DIRS) visit(BlockPos.offset(c, d), visited, air, queue);
            }
        }
        while (!queue.isEmpty()) {
            long p = queue.dequeueLong();
            for (Direction d : DIRS) {
                long q = BlockPos.offset(p, d);
                byte c = classify(q);
                switch (c) {
                    case AIR -> {
                        if (!visited.contains(q)) {
                            if (air.size() >= cap) truncated = true;
                            else {
                                visited.add(q);
                                air.add(q);
                                queue.enqueue(q);
                            }
                        }
                    }
                    case OUTSIDE -> {
                        outAir.add(p);
                        outDir.add(d.ordinal());
                    }
                    case DOOR -> {
                        doorOf.add(lastCtrl);
                        doorAir.add(p);
                        out.anchors.add(lastCtrl);
                    }
                    case FAN -> {
                        fanOf.add(lastCtrl);
                        fanAir.add(p);
                        out.anchors.add(lastCtrl);
                    }
                    default -> {
                        BlockState s = level.getBlockState(cursor.set(q));
                        if (s.is(PcBlocks.COAL_SEAM.get())) coalAir.putIfAbsent(q, p);
                        else if (s.is(PcBlocks.DEGASIFICATION_BOREHOLE.get())) {
                            boreAir.putIfAbsent(q, p);
                            out.anchors.add(q);
                        } else if (s.is(PcBlocks.MINE_DISPATCH_CABINET.get()) || s.is(PcBlocks.MULTIBLOCK_PART.get())) {
                            BlockPos ctrl = controller(q, s);
                            if (ctrl != null && level.getBlockState(ctrl).is(PcBlocks.MINE_DISPATCH_CABINET.get()))
                                out.anchors.add(ctrl.asLong());
                        }
                    }
                }
            }
        }

        long[] blocks = air.toLongArray();
        Arrays.sort(blocks);
        out.airBlocks = blocks.length;
        out.truncated = truncated;
        out.firstUnloaded = firstUnloaded;
        build(out, blocks, outAir, outDir, doorOf, doorAir, fanOf, fanAir, coalAir, boreAir);
        return out;
    }

    private void visit(long key, LongOpenHashSet visited, LongArrayList air, LongArrayFIFOQueue queue) {
        if (visited.contains(key) || air.size() >= cap || classify(key) != AIR) return;
        visited.add(key);
        air.add(key);
        queue.enqueue(key);
    }

    /** Клетки конструкции якоря (одна — у одноклеточного блока). */
    private long[] structureCells(long key) {
        BlockPos pos = BlockPos.of(key);
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof PcBlock pc) || pc.spec().footprint().isSingle()) return new long[] {key};
        LongArrayList cells = new LongArrayList();
        for (Footprint.Cell c : pc.spec().footprint().cells(pos, pc.facing(s))) cells.add(c.pos().asLong());
        return cells.toLongArray();
    }

    private BlockPos controller(long key, BlockState s) {
        BlockPos pos = BlockPos.of(key);
        if (!(s.getBlock() instanceof MultiblockPartBlock)) return pos;
        BlockPos anchor = MultiblockPartBlock.anchor(s, pos);
        BlockPos ctrl = PcBlock.structureController(level, anchor);
        return ctrl != null ? ctrl : anchor;
    }

    // --- граф ----------------------------------------------------------------------------------------------

    private static final class Comp {
        long key;
        final IntArrayList members = new IntArrayList();
        double sx, sy, sz;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean flame, timber, arch;
        double barrierKg;
        BarrierKind barrierKind;
        int node;
    }

    /** Грань между двумя узлами или узлом и поверхностью. */
    private static final class Contact {
        int count;
        boolean vertical;
        int u0 = Integer.MAX_VALUE, u1 = Integer.MIN_VALUE, v0 = Integer.MAX_VALUE, v1 = Integer.MIN_VALUE;
        double depth;

        void add(Direction.Axis axis, int x, int y, int z) {
            count++;
            int u = axis == Direction.Axis.X ? z : x, v = axis == Direction.Axis.Y ? z : y;
            u0 = Math.min(u0, u);
            u1 = Math.max(u1, u);
            v0 = Math.min(v0, v);
            v1 = Math.max(v1, v);
            if (axis == Direction.Axis.Y) vertical = true;
        }

        double perimeter() {
            return 2.0 * ((u1 - u0 + 1) + (v1 - v0 + 1));
        }
    }

    private static long cellKey(long block, int ordinal) {
        long cx = BlockPos.getX(block) >> 3, cy = BlockPos.getY(block) >> 3, cz = BlockPos.getZ(block) >> 3;
        return ((cx & 0x7F_FFFFL) << 37) | ((cz & 0x7F_FFFFL) << 14) | ((cy & 0x3FFL) << 4) | (ordinal & 0xF);
    }

    private static boolean sameCell(long a, long b) {
        return BlockPos.getX(a) >> 3 == BlockPos.getX(b) >> 3 && BlockPos.getY(a) >> 3 == BlockPos.getY(b) >> 3
                && BlockPos.getZ(a) >> 3 == BlockPos.getZ(b) >> 3;
    }

    private static long pairKey(long a, long b, long salt) {
        long lo = Math.min(a, b), hi = Math.max(a, b);
        long h = lo * 0x9E37_79B9_7F4A_7C15L ^ Long.rotateLeft(hi, 29) ^ salt * 0xBF58_476D_1CE4_E5B9L;
        h ^= h >>> 31;
        return h == MineTopology.ATMOSPHERE_KEY ? h + 1 : h;
    }

    private void build(Scan out, long[] blocks, LongArrayList outAir, LongArrayList outDir, LongArrayList doorOf,
            LongArrayList doorAir, LongArrayList fanOf, LongArrayList fanAir, Long2LongLinkedOpenHashMap coalAir,
            Long2LongLinkedOpenHashMap boreAir) {
        // части выработки в ячейках
        Long2IntOpenHashMap compOf = new Long2IntOpenHashMap(blocks.length);
        compOf.defaultReturnValue(-1);
        Long2IntOpenHashMap perCell = new Long2IntOpenHashMap();
        java.util.List<Comp> comps = new java.util.ArrayList<>();
        LongArrayFIFOQueue q = new LongArrayFIFOQueue();
        Long2IntOpenHashMap index = new Long2IntOpenHashMap(blocks.length);
        for (int i = 0; i < blocks.length; i++) index.put(blocks[i], i);
        for (int i = 0; i < blocks.length; i++) {
            if (compOf.get(blocks[i]) >= 0) continue;
            Comp c = new Comp();
            long cell = cellKey(blocks[i], 0);
            int ordinal = perCell.getOrDefault(cell, 0);
            perCell.put(cell, ordinal + 1);
            c.key = ordinal < 16 ? cellKey(blocks[i], ordinal) : pairKey(blocks[i], cell, 7);
            int id = comps.size();
            comps.add(c);
            compOf.put(blocks[i], id);
            q.enqueue(blocks[i]);
            while (!q.isEmpty()) {
                long p = q.dequeueLong();
                c.members.add(index.get(p));
                for (Direction d : DIRS) {
                    long n = BlockPos.offset(p, d);
                    if (index.containsKey(n) && compOf.get(n) < 0 && sameCell(p, n)) {
                        compOf.put(n, id);
                        q.enqueue(n);
                    }
                }
            }
            IntArrays.quickSort(c.members.elements(), 0, c.members.size());
        }

        // свойства частей
        for (Comp c : comps) {
            for (int m = 0; m < c.members.size(); m++) {
                long k = blocks[c.members.getInt(m)];
                int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
                c.sx += x;
                c.sy += y;
                c.sz += z;
                c.minX = Math.min(c.minX, x);
                c.minY = Math.min(c.minY, y);
                c.minZ = Math.min(c.minZ, z);
                c.maxX = Math.max(c.maxX, x);
                c.maxY = Math.max(c.maxY, y);
                c.maxZ = Math.max(c.maxZ, z);
                BlockState s = level.getBlockState(cursor.set(k));
                Block b = s.getBlock();
                if (s.is(PcTags.Blocks.OPEN_FLAME) && s.getOptionalValue(BlockStateProperties.LIT).orElse(true)) c.flame = true;
                if (b == PcBlocks.EXPLOSION_BARRIER.get()) {
                    c.barrierKg += barrierKgPerBlock;
                    c.barrierKind = s.getValue(PcProperties.BARRIER_KIND) == PcProperties.BarrierKind.WATER ? BarrierKind.WATER : BarrierKind.STONE;
                    out.anchors.add(k);
                } else if (b == PcBlocks.CANARY_CAGE.get()) {
                    out.cages.add(k);
                } else if (b == PcBlocks.MINE_SUPPORT.get() || b == PcBlocks.MULTIBLOCK_PART.get()) {
                    BlockPos ctrl = controller(k, s);
                    BlockState cs = level.getBlockState(ctrl);
                    if (cs.is(PcBlocks.MINE_SUPPORT.get())) {
                        if (cs.getValue(PcProperties.SUPPORT_KIND) == PcProperties.SupportKind.ARCH) c.arch = true;
                        else c.timber = true;
                        out.anchors.add(ctrl.asLong());
                    }
                }
            }
        }

        // поверхность
        double groundSum = 0;
        int groundN = 0;
        Long2ObjectLinkedOpenHashMap<Contact> atm = new Long2ObjectLinkedOpenHashMap<>();
        for (int i = 0; i < outAir.size(); i++) {
            long p = outAir.getLong(i);
            Direction d = DIRS[(int) outDir.getLong(i)];
            int ci = compOf.get(p);
            if (ci < 0) continue;
            Contact c = atm.computeIfAbsent(ci, k -> new Contact());
            c.add(d.getAxis(), BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p));
            int ground = ground(BlockPos.getX(p) + d.getStepX(), BlockPos.getZ(p) + d.getStepZ());
            c.depth += Math.max(1, ground - BlockPos.getY(p));
            groundSum += ground;
            groundN++;
        }
        double surface = groundN > 0 ? groundSum / groundN : level.getSeaLevel();

        MineTopology.Builder tb = MineTopology.builder(surface, SURFACE_TEMP_K);
        int nn = comps.size();
        long[] origins = new long[nn + 1];
        boolean[] flames = new boolean[nn + 1];
        out.nodeStart = new int[nn + 2];
        out.nodeBlocks = new long[blocks.length];
        int w = 0;
        for (int ci = 0; ci < nn; ci++) {
            Comp c = comps.get(ci);
            int n = c.members.size();
            double cx = c.sx / n, cy = c.sy / n, cz = c.sz / n;
            int dx = c.maxX - c.minX + 1, dy = c.maxY - c.minY + 1, dz = c.maxZ - c.minZ + 1;
            int len = Math.max(dx, Math.max(dy, dz));
            int a, b;
            if (len == dx) { a = dy; b = dz; } else if (len == dy) { a = dx; b = dz; } else { a = dx; b = dy; }
            double section = Math.max(1.0, (double) n / len), perimeter = 2.0 * (a + b);
            double temp = SURFACE_TEMP_K + GEOTHERMAL_K_PER_M * Math.max(0, surface - cy);
            c.node = tb.node(c.key, n, cy + 0.5, section, perimeter, temp);
            long best = 0;
            double bestD = Double.MAX_VALUE;
            out.nodeStart[c.node] = w;
            for (int m = 0; m < n; m++) {
                long k = blocks[c.members.getInt(m)];
                tb.assignBlock(k, c.node);
                out.nodeBlocks[w++] = k;
                double ddx = BlockPos.getX(k) - cx, ddy = BlockPos.getY(k) - cy, ddz = BlockPos.getZ(k) - cz;
                double dd = ddx * ddx + ddy * ddy + ddz * ddz;
                if (dd < bestD) {
                    bestD = dd;
                    best = k;
                }
            }
            origins[c.node] = best;
            flames[c.node] = c.flame;
        }
        out.nodeStart[nn + 1] = w;

        // ветви между частями
        Long2ObjectLinkedOpenHashMap<Contact> faces = new Long2ObjectLinkedOpenHashMap<>();
        for (long k : blocks) {
            int ci = compOf.get(k);
            for (Direction d : new Direction[] {Direction.EAST, Direction.UP, Direction.SOUTH}) {
                long n = BlockPos.offset(k, d);
                int cj = compOf.get(n);
                if (cj < 0 || cj == ci) continue;
                long pk = (long) Math.min(ci, cj) << 32 | Math.max(ci, cj);
                faces.computeIfAbsent(pk, x -> new Contact()).add(d.getAxis(), BlockPos.getX(k), BlockPos.getY(k), BlockPos.getZ(k));
            }
        }
        java.util.List<IntArrayList> incident = new java.util.ArrayList<>();
        for (int i = 0; i < nn; i++) incident.add(new IntArrayList());
        for (var e : faces.long2ObjectEntrySet()) {
            int ci = (int) (e.getLongKey() >>> 32), cj = (int) e.getLongKey();
            Comp a = comps.get(ci), b = comps.get(cj);
            Contact c = e.getValue();
            double ax = a.sx / a.members.size() - b.sx / b.members.size();
            double ay = a.sy / a.members.size() - b.sy / b.members.size();
            double az = a.sz / a.members.size() - b.sz / b.members.size();
            double length = Math.max(1, Math.sqrt(ax * ax + ay * ay + az * az));
            int j = tb.branch(pairKey(a.key, b.key, 1), a.node, b.node, length, c.count, c.perimeter(),
                    0.5 * (lining(a) + lining(b)), c.vertical ? BranchKind.SHAFT : BranchKind.DRIFT);
            incident.get(ci).add(j);
            incident.get(cj).add(j);
        }
        for (var e : atm.long2ObjectEntrySet()) {
            int ci = (int) e.getLongKey();
            Comp a = comps.get(ci);
            Contact c = e.getValue();
            int j = tb.branch(pairKey(a.key, MineTopology.ATMOSPHERE_KEY, 2), a.node, MineTopology.ATMOSPHERE,
                    Math.max(1, c.depth / c.count), c.count, c.perimeter(), lining(a),
                    c.vertical ? BranchKind.SHAFT : BranchKind.DRIFT);
            incident.get(ci).add(j);
        }

        // двери
        LongArrayList doorCtrl = new LongArrayList();
        DoubleList doorSec = new DoubleList();
        for (long door : distinct(doorOf)) {
            int[] top = topComps(door, doorOf, doorAir, compOf);
            BlockState ds = level.getBlockState(BlockPos.of(door));
            if (!ds.is(PcBlocks.VENTILATION_DOOR.get())) continue;
            DoorKind kind = ds.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR ? DoorKind.REGULATOR : DoorKind.DOOR;
            double sec = structureCells(door).length;
            int j;
            if (top[1] >= 0) {
                j = tb.branch(pairKey(door, comps.get(top[0]).key ^ comps.get(top[1]).key, 3), comps.get(top[0]).node,
                        comps.get(top[1]).node, 1, sec, 2 * (sec + 1), AirwayLining.TIMBER.alpha, BranchKind.DRIFT);
                incident.get(top[1]).add(j);
            } else if (top[0] >= 0 && touchesOutside(door)) {
                j = tb.branch(pairKey(door, MineTopology.ATMOSPHERE_KEY, 3), comps.get(top[0]).node, MineTopology.ATMOSPHERE,
                        1, sec, 2 * (sec + 1), AirwayLining.TIMBER.alpha, BranchKind.DRIFT);
            } else continue;
            incident.get(top[0]).add(j);
            tb.door(j, kind);
            doorCtrl.add(door);
            doorSec.add(sec);
        }

        // вентиляторы: главный (на поверхность) — всасывающий, из выработки наружу; внутри шахты — между частями
        LongArrayList fanCtrl = new LongArrayList();
        for (long fan : distinct(fanOf)) {
            int[] top = topComps(fan, fanOf, fanAir, compOf);
            if (top[0] < 0) continue;
            int contacts = 0;
            for (int i = 0; i < fanOf.size(); i++) if (fanOf.getLong(i) == fan) contacts++;
            double sec = Math.max(1, Math.min(9, contacts));
            int j;
            if (touchesOutside(fan)) {
                j = tb.branch(pairKey(fan, MineTopology.ATMOSPHERE_KEY, 4), comps.get(top[0]).node, MineTopology.ATMOSPHERE,
                        3, sec, 4 * Math.sqrt(sec), AirwayLining.CONCRETE.alpha, BranchKind.SHAFT);
            } else if (top[1] >= 0) {
                j = tb.branch(pairKey(fan, comps.get(top[0]).key ^ comps.get(top[1]).key, 4), comps.get(top[0]).node,
                        comps.get(top[1]).node, 3, sec, 4 * Math.sqrt(sec), AirwayLining.CONCRETE.alpha, BranchKind.DRIFT);
                incident.get(top[1]).add(j);
            } else continue;
            incident.get(top[0]).add(j);
            tb.fan(j, MAIN_FAN);
            fanCtrl.add(fan);
        }

        // заслоны: на всех ветвях своего узла
        for (int ci = 0; ci < nn; ci++) {
            Comp c = comps.get(ci);
            if (c.barrierKg <= 0) continue;
            for (int k = 0; k < incident.get(ci).size(); k++) tb.barrier(incident.get(ci).getInt(k), c.barrierKind, c.barrierKg);
        }

        // обнажённый уголь и скважины
        out.coalKeys = new long[coalAir.size()];
        out.coalX = new double[coalAir.size()];
        for (var e : coalAir.long2LongEntrySet()) {
            int ci = compOf.get(e.getLongValue());
            if (ci < 0) continue;
            long coal = e.getLongKey();
            tb.assignBlock(coal, comps.get(ci).node);
            out.coalKeys[out.coalCount] = coal;
            out.coalX[out.coalCount++] = MethaneField.gasContent(gasX0, BlockPos.getY(coal));
        }
        for (var e : boreAir.long2LongEntrySet()) {
            int ci = compOf.get(e.getLongValue());
            if (ci >= 0) out.boreholeNode.put(e.getLongKey(), comps.get(ci).node);
        }

        out.topology = tb.build();
        out.origins = origins;
        out.flames = flames;
        out.fanCtrl = fanCtrl.toLongArray();
        out.doorCtrl = doorCtrl.toLongArray();
        out.doorSection = doorSec.toArray();
        out.bounds = bounds(blocks);
    }

    private static double lining(Comp c) {
        return c.arch ? AirwayLining.ARCH.alpha : c.timber ? AirwayLining.TIMBER.alpha : AirwayLining.ROCK.alpha;
    }

    /** Отметка земли у устья: наибольшая высота поверхности в соседних столбцах. */
    private int ground(int x, int z) {
        int h = Integer.MIN_VALUE;
        for (Direction d : Direction.Plane.HORIZONTAL)
            h = Math.max(h, level.getHeight(Heightmap.Types.WORLD_SURFACE, x + d.getStepX(), z + d.getStepZ()));
        return h;
    }

    private boolean touchesOutside(long ctrl) {
        for (long c : structureCells(ctrl))
            for (Direction d : DIRS)
                if (classify(BlockPos.offset(c, d)) == OUTSIDE) return true;
        return false;
    }

    private static long[] distinct(LongArrayList list) {
        LongLinkedOpenHashSet set = new LongLinkedOpenHashSet(list);
        return set.toLongArray();
    }

    /** Две части выработки с наибольшим числом граней у устройства (−1 — нет). */
    private static int[] topComps(long device, LongArrayList devOf, LongArrayList devAir, Long2IntOpenHashMap compOf) {
        Long2IntLinkedOpenHashMap count = new Long2IntLinkedOpenHashMap();
        for (int i = 0; i < devOf.size(); i++) {
            if (devOf.getLong(i) != device) continue;
            int ci = compOf.get(devAir.getLong(i));
            if (ci >= 0) count.addTo(ci, 1);
        }
        int first = -1, second = -1, fc = 0, sc = 0;
        for (var e : count.long2IntEntrySet()) {
            int ci = (int) e.getLongKey(), c = e.getIntValue();
            if (c > fc) {
                second = first;
                sc = fc;
                first = ci;
                fc = c;
            } else if (c > sc) {
                second = ci;
                sc = c;
            }
        }
        return new int[] {first, second};
    }

    private static BoundingBox bounds(long[] blocks) {
        if (blocks.length == 0) return new BoundingBox(0, 0, 0, 0, 0, 0);
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (long k : blocks) {
            int x = BlockPos.getX(k), y = BlockPos.getY(k), z = BlockPos.getZ(k);
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
        }
        return new BoundingBox(x0 - 1, y0 - 1, z0 - 1, x1 + 1, y1 + 1, z1 + 1);
    }

    /** Список double без упаковки. */
    private static final class DoubleList {
        private double[] a = new double[4];
        private int n;

        void add(double v) {
            if (n == a.length) a = Arrays.copyOf(a, n * 2);
            a[n++] = v;
        }

        double[] toArray() {
            return Arrays.copyOf(a, n);
        }
    }
}
