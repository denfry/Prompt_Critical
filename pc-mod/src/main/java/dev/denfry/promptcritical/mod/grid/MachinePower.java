package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.grid.PowerBoard;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Питание машин от сети 0,4 кВ: машина каждую секунду заявляет мощность ближайшей розетке, силовому щиту или ИБП, в
 * чью зону она входит, и получает долю 0…1 — во столько раз медленнее она работает. Нет щита рядом — доля 0.
 *
 * <p>На сервере GameTest машины питаются даром, кроме стоящих в коробках {@link #strictArea}.
 *
 * <p>Классы машин (решение владельца 2026-11-23, раздела в GDD нет): активные машины берут номинал; приборы и
 * автоматика — малую мощность, обычно от ИБП; пассивные блоки сюда не обращаются. Ключ
 * {@code energy.machines_need_power = false} кормит все машины даром. Только сервер.
 */
public final class MachinePower {
    private static final Map<Level, Set<BlockPos>> BOARDS = new WeakHashMap<>();
    private static final Map<Level, Set<long[]>> STRICT = new WeakHashMap<>();

    private MachinePower() {
    }

    /** Зона щита вида {@code kind}, блоков по каждой оси: длина шнура у розеток, групповые линии у щита и ИБП. */
    public static int range(PowerBoard.Kind kind) {
        return switch (kind) {
            case SOCKET -> 4;
            case SOCKET_3P -> 6;
            case BOARD, UPS -> 16;
        };
    }

    static synchronized void add(Level level, BlockPos pos) {
        BOARDS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> s = BOARDS.get(level);
        if (s != null) s.remove(pos);
    }

    /** Тест питания: в коробке {@code min..max} машины питаются по-настоящему даже на стенде. */
    public static synchronized void strictArea(Level level, BlockPos min, BlockPos max) {
        STRICT.computeIfAbsent(level, l -> new HashSet<>()).add(new long[] {min.asLong(), max.asLong()});
    }

    private static synchronized boolean strict(Level level, BlockPos pos) {
        Set<long[]> s = STRICT.get(level);
        if (s == null) return false;
        for (long[] box : s) {
            BlockPos a = BlockPos.of(box[0]), b = BlockPos.of(box[1]);
            if (pos.getX() >= a.getX() && pos.getX() <= b.getX() && pos.getY() >= a.getY() && pos.getY() <= b.getY()
                    && pos.getZ() >= a.getZ() && pos.getZ() <= b.getZ()) return true;
        }
        return false;
    }

    private static boolean free(Level level, BlockPos pos) {
        boolean need;
        try {
            need = ServerConfig.MACHINES_NEED_POWER.get();
        } catch (IllegalStateException e) {
            need = true; // конфиг ещё не загружен
        }
        // сервер GameTest: машины чужих тестов питаются даром, тесты питания отмечают свою коробку strictArea
        boolean bench = level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer;
        return !need || (bench && !strict(level, pos));
    }

    /**
     * Машина в {@code pos} хочет {@code watts}: заявить их щиту и узнать долю, которую она получит.
     *
     * @return 0…1; 1 — полная скорость, 0 — машина стоит
     */
    public static double supply(@Nullable Level level, BlockPos pos, double watts) {
        if (level == null || level.isClientSide) return 1;
        if (free(level, pos)) return 1;
        PowerBoardBlockEntity b = nearest(level, pos);
        if (b == null) return 0;
        return b.feed(pos, Math.max(0, watts), level.getGameTime());
    }

    /**
     * Привод машины с потактовым ходом: каждый тик набирает долю питания, целый такт хода — когда набралась единица.
     * На половине мощности машина делает такт через тик, то есть работает вдвое медленнее.
     */
    public static final class Drive {
        private double acc;
        private double last = 1;

        /** Тик машины мощностью {@code watts}: сделать ли такт хода. */
        public boolean advance(@Nullable Level level, BlockPos pos, double watts) {
            last = supply(level, pos, watts);
            acc = Math.min(1 + last, acc + last);
            if (acc < 1) return false;
            acc -= 1;
            return true;
        }

        /** Доля питания на последнем тике, 0…1. */
        public double factor() {
            return last;
        }
    }

    /** Прибор или автоматика с потреблением {@code watts}: есть ли питание. */
    public static boolean powered(@Nullable Level level, BlockPos pos, double watts) {
        return supply(level, pos, watts) > 0.5;
    }

    /** Щит, от которого питается машина: ближайший из тех, в чью зону она входит; при равенстве — меньший по координатам. */
    @Nullable
    public static synchronized PowerBoardBlockEntity nearest(Level level, BlockPos machine) {
        Set<BlockPos> s = BOARDS.get(level);
        if (s == null || s.isEmpty()) return null;
        PowerBoardBlockEntity best = null;
        BlockPos bestPos = null;
        long bestD = Long.MAX_VALUE;
        for (BlockPos p : s) {
            int dx = Math.abs(p.getX() - machine.getX()), dy = Math.abs(p.getY() - machine.getY()), dz = Math.abs(p.getZ() - machine.getZ());
            if (dx > 16 || dy > 16 || dz > 16) continue;
            long d = (long) dx * dx + (long) dy * dy + (long) dz * dz;
            if (d > bestD || (d == bestD && bestPos != null && p.compareTo(bestPos) >= 0)) continue;
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof PowerBoardBlockEntity b)) continue;
            int r = range(b.kind());
            if (dx > r || dy > r || dz > r) continue;
            best = b;
            bestPos = p;
            bestD = d;
        }
        return best;
    }
}
