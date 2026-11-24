package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.grid.MvDrive;
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

    /** Правило питания здесь не действует: ключ сервера выключен или стенд GameTest вне {@link #strictArea}. */
    public static boolean exempt(Level level, BlockPos pos) {
        return free(level, pos);
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

    /**
     * Ввод 6,3 кВ машины мощнее 200 кВт: BE машины — {@link GridDevice} со своим потребителем {@link
     * dev.denfry.promptcritical.sim.grid.ConsumerUnit} (регистрация через {@link GridLoad}). Выключатель с нулевой
     * защитой {@link MvDrive}: ниже 0,85 о.е. отпадает и ждёт «Пуска» ({@link #start}); скорость идёт за частотой.
     * На сервере GameTest вне {@link #strictArea} машина питается даром.
     */
    public static final class Mv {
        /** Шаг защиты за тик сервера, с. */
        private static final double TICK_S = 0.05;
        private final MvDrive drive = new MvDrive();

        /**
         * Тик машины, которой сейчас нужна мощность {@code watts} (0 — простаивает, выключатель остаётся включён).
         *
         * @return доля номинальной скорости 0…1
         */
        public double supply(@Nullable Level level, BlockPos pos, SimId id, double watts) {
            if (level == null || level.isClientSide) return 1;
            if (free(level, pos)) return 1;
            dev.denfry.promptcritical.sim.grid.ConsumerUnit u = GridLoad.unit(level, id);
            if (u == null) return drive.step(TICK_S, 0, 0);
            u.setDemandW(drive.demandW(watts));
            return drive.step(TICK_S, u.inputPu(), u.frequencyHz());
        }

        /** Доля номинальной скорости на прошлом тике. */
        public double factor() {
            return drive.factor();
        }

        /** Выключатель отпал по нулевой защите. */
        public boolean tripped() {
            return drive.tripped();
        }

        public boolean on() {
            return drive.on();
        }

        /** Кнопка «Пуск». */
        public void start() {
            drive.start();
        }

        /** Кнопка «Стоп». */
        public void stop() {
            drive.stop();
        }

        public void save(net.minecraft.nbt.CompoundTag tag) {
            tag.putInt("mv_drive", drive.bits());
        }

        public void load(net.minecraft.nbt.CompoundTag tag) {
            if (tag.contains("mv_drive")) drive.restore(tag.getInt("mv_drive"));
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
