package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.sim.mining.MineNetwork;
import dev.denfry.promptcritical.sim.mining.MineTopology;
import dev.denfry.promptcritical.sim.mining.MineVentilation;
import dev.denfry.promptcritical.sim.radiation.NaturalBackground.Ground;
import dev.denfry.promptcritical.sim.radiation.RadonBox;
import dev.denfry.promptcritical.sim.radiation.RadonBox.Setting;
import dev.denfry.promptcritical.sim.radiation.RadonBox.Surface;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Радон в замкнутом воздухе вокруг игрока (04b §4b.13.3; 02 §2.1.6). Раз в {@value #REFRESH_TICKS} тиков (10 с) от
 * глаз игрока заливается воздушный карман; если он замкнут, породы на его гранях выдыхают радон, вентиляция шахты или
 * естественный обмен выносят его, а концентрация даёт добавку к дозе (через {@link RadonBox#doseUSvH}).
 * Состояние хранится на игрока, а не на карман: карман меняется с каждым шагом, а накопленным радоном дышит только тот,
 * кто в нём стоит.
 */
public final class RadonField {
    /** Период пересчёта, тиков; равен {@link #STEP_S} секундам физического времени. */
    public static final int REFRESH_TICKS = 200;
    /** Шаг {@link RadonBox#step}, с. */
    private static final double STEP_S = REFRESH_TICKS / 20.0;
    /** Предел заливки, блоков = м³: больше — это пещера, а не комната. [моё] */
    static final int MAX_POCKET = 4096;
    /** Кратность обмена открытого кармана, 1/ч: как у проветриваемого здания (04b §4b.13.3). [BALANCE] */
    private static final double OPEN_EXCHANGE_PER_H = 0.5;
    /** Кратность обмена закрытого подземного кармана без шахтной вентиляции, 1/ч (04b §4b.13.3). [BALANCE] */
    private static final double STILL_EXCHANGE_PER_H = 0.05;
    /** Здание над поверхностью: обмен как у открытого кармана, 1/ч (04b §4b.13.3). [BALANCE] */
    private static final double BUILDING_EXCHANGE_PER_H = 0.5;
    /** Радий-226 в урановой руде с 1 % U, Бк/кг (02 §2.1.6). [BALANCE] */
    static final double ORE_BQ_KG = 1.047e5;
    private static final double ORE_FLUX = RadonBox.oreExhalation(ORE_BQ_KG);

    private static final class State {
        double cBqM3;
        Setting setting = Setting.BUILDING;
    }

    /** Только серверный поток. */
    private static final Map<UUID, State> STATES = new HashMap<>();

    private RadonField() {
    }

    /** Добавка радона к дозе игрока, Зв/ч; без состояния — 0. */
    public static double doseSvH(ServerLevel level, ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : RadonBox.doseUSvH(s.cBqM3, s.setting) * 1e-6;
    }

    /**
     * Пора ли пересчитывать радон игроку. Сервис радиации тикает раз в 20 тиков, поэтому сдвиг по номеру игрока кратен 20:
     * сдвиг на {@code id % 200} почти всегда не попал бы на такт.
     */
    public static boolean due(long gameTime, ServerPlayer p) {
        return (gameTime - (p.getId() % 10) * 20L) % REFRESH_TICKS == 0;
    }

    /** Забыть состояние игрока (выход с сервера). */
    public static void forget(UUID player) {
        STATES.remove(player);
    }

    /** Число игроков с состоянием (для тестов). */
    public static int stateCount() {
        return STATES.size();
    }

    /** Обстановка последнего пересчёта (для тестов); без состояния — {@link Setting#BUILDING}. */
    public static Setting setting(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? Setting.BUILDING : s.setting;
    }

    /** Концентрация радона у игрока, Бк/м³ (для тестов). */
    public static double concentration(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        return s == null ? 0 : s.cBqM3;
    }

    /** Один шаг в 10 с: заливка кармана, источники, вентиляция. */
    public static void refresh(ServerLevel level, ServerPlayer p) {
        State st = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        BlockPos eye = BlockPos.containing(p.getEyePosition());
        Pocket pocket = flood(level, eye);
        if (pocket.skyOpen) {
            // карман открыт небу: радон уносит ветер
            st.cBqM3 = 0;
            st.setting = Setting.BUILDING;
            return;
        }
        if (pocket.blocks == 0) return; // глаз в твёрдом блоке: карман не найден, состояние не трогаем
        double mineQ = mineFlow(level, eye);
        if (pocket.capped && mineQ <= 0) {
            // пещера больше комнаты: источник не считаем, объём и обмен условные
            st.cBqM3 = RadonBox.step(st.cBqM3, 0, MAX_POCKET, MAX_POCKET * OPEN_EXCHANGE_PER_H / 3600, STEP_S);
            st.setting = Setting.BUILDING;
            return;
        }
        // выработка больше предела заливки: обойдённые 4096 м³ вокруг игрока — участок, через который идёт поток ветви
        double volume = Math.max(1, pocket.blocks);
        double vent;
        Setting setting;
        if (mineQ > 0) {
            vent = mineQ;
            setting = Setting.MINE_VENTILATED;
        } else if (pocket.naturalEnclosure() && eye.getY() < level.getHeight(Heightmap.Types.WORLD_SURFACE, eye.getX(), eye.getZ())) {
            // под землёй — только если стенки природные: крыша и стены постройки не делают её шахтой
            vent = STILL_EXCHANGE_PER_H * volume / 3600;
            setting = Setting.MINE_STILL;
        } else {
            vent = BUILDING_EXCHANGE_PER_H * volume / 3600;
            setting = Setting.BUILDING;
        }
        st.cBqM3 = RadonBox.step(st.cBqM3, pocket.sourceBqS, volume, vent, STEP_S);
        st.setting = setting;
    }

    /** Расход воздуха через узел выработки, м³/с: полусумма модулей расходов смежных ветвей; 0 — не в шахте или штиль. */
    private static double mineFlow(ServerLevel level, BlockPos eye) {
        SimLevelBinding b = SimBindings.get(level);
        MineNetwork n = b == null ? null : b.mines().mineAt(eye);
        if (n == null || !n.ready()) return 0;
        int node = n.nodeOf(eye.asLong());
        if (node <= 0) return 0;
        MineTopology t = n.topology();
        MineVentilation v = n.ventilation();
        double sum = 0;
        for (int br = 0; br < t.branchCount(); br++) if (t.from(br) == node || t.to(br) == node) sum += Math.abs(v.flow(br));
        return sum / 2;
    }

    private record Pocket(int blocks, double sourceBqS, boolean skyOpen, boolean capped, int naturalFaces, int solidFaces) {
        /** Большинство граней — порода или грунт. */
        boolean naturalEnclosure() {
            return solidFaces > 0 && naturalFaces * 2 > solidFaces;
        }
    }

    /** Заливка воздуха по 6 соседям от глаз, не более {@value #MAX_POCKET} блоков; грани породы дают источник. */
    private static Pocket flood(ServerLevel level, BlockPos start) {
        if (!passable(level.getBlockState(start), level, start)) return new Pocket(0, 0, false, false, 0, 0);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayList queue = new LongArrayList();
        seen.add(start.asLong());
        queue.add(start.asLong());
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos nb = new BlockPos.MutableBlockPos();
        double source = 0;
        int natural = 0;
        int solid = 0;
        for (int head = 0; head < queue.size(); head++) {
            at.set(queue.getLong(head));
            // небо над блоком — по карте высот: LevelReader.canSeeSky ниже уровня моря смотрит на столб от моря и ошибается
            if (at.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ())) return new Pocket(seen.size(), 0, true, false, 0, 0);
            for (Direction d : Direction.values()) {
                nb.setWithOffset(at, d);
                if (level.isOutsideBuildHeight(nb) || !level.hasChunkAt(nb)) return new Pocket(seen.size(), 0, true, false, 0, 0);
                BlockState s = level.getBlockState(nb);
                if (passable(s, level, nb)) {
                    if (seen.add(nb.asLong())) {
                        if (seen.size() >= MAX_POCKET) return new Pocket(seen.size(), 0, false, true, 0, 0);
                        queue.add(nb.asLong());
                    }
                } else {
                    Ground g = BackgroundField.ground(s);
                    source += flux(g);
                    solid++;
                    if (g != Ground.NONE) natural++;
                }
            }
        }
        return new Pocket(seen.size(), source, false, false, natural, solid);
    }

    /** Воздух и всё, что не мешает проходу: блок без коллизии. */
    private static boolean passable(BlockState s, ServerLevel level, BlockPos pos) {
        return s.isAir() || s.getCollisionShape(level, pos).isEmpty();
    }

    /** Поток радона с 1 м² грани, Бк/(м²·с): грунт и порода по тем же классам, что и у фона. */
    private static double flux(Ground g) {
        return switch (g) {
            case URANIUM_ORE -> ORE_FLUX;
            case SOIL, CLAY, SAND -> Surface.SOIL.bqM2S;
            case STONE, DEEP -> Surface.GRANITE.bqM2S;
            case NONE -> Surface.NONE.bqM2S;
        };
    }
}
