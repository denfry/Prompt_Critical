package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.TopologyEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Доставка событий домена и мира в триггер {@code sim_event} (11 §11.2.1) и в задания маршрута (11 §11.1.5). События
 * эпохи I0: пуск паровой машины и замеры котлов и машин (домен), канарейка посажена в шахте и откачана (мир); I1 —
 * синхронизация, релейная схема и аналоговый контур (домен), кокс, чугун, сталь и бесшовная труба (мир).
 */
public final class SimAchievements {
    /** Радиус «смены» вокруг источника события, м (11 §11.2.1). */
    public static final double RADIUS_M = 64;
    public static final String CANARY_PLACED = "promptcritical:canary_placed";
    public static final String CANARY_REVIVED = "promptcritical:canary_revived";
    /** События мира эпохи I1: выдача кокса из камеры, выпуск чугуна, слив плавки конвертера, бесшовная труба. */
    public static final String COKE_PUSHED = "promptcritical:coke_pushed", IRON_TAPPED = "promptcritical:iron_tapped",
            STEEL_TAPPED = "promptcritical:steel_tapped", SEAMLESS_PIPE = "promptcritical:seamless_pipe";

    /**
     * События первых продуктов и установок для достижений 11 §11.2.2: первая нефть, гелий, этилен, первый налив UF₆,
     * обогащение (тег {@code u235_pct}), загрузка МОКС, заглушённая скважина, ПЛК в RUN, языки ПЛК (тег
     * {@code languages}), чтение Lua, историан за сутки (тег {@code tags}), шлюзы у историана (тег {@code rtus}), замок
     * LOTO, дефект на плёнке, сутки с дозиметром, чистая АЗ (тег {@code power_pct}).
     */
    public static final String FIRST_OIL = "promptcritical:first_oil", HELIUM = "promptcritical:helium_recovered",
            ETHYLENE = "promptcritical:ethylene", UF6_FILLED = "promptcritical:uf6_filled", ENRICHED = "promptcritical:enriched",
            MOX_LOADED = "promptcritical:mox_loaded", WELL_KILLED = "promptcritical:well_killed", PLC_RUN = "promptcritical:plc_run",
            PLC_LANGUAGES = "promptcritical:plc_languages", LUA_READ = "promptcritical:lua_read",
            HISTORIAN_DAY = "promptcritical:historian_day", TELEMETRY = "promptcritical:telemetry",
            LOCKOUT = "promptcritical:lockout", RADIOGRAPHY_DEFECT = "promptcritical:radiography_defect",
            DOSIMETER_DAY = "promptcritical:dosimeter_day", SCRAM_CLEAN = "promptcritical:scram_clean";
    /**
     * События мастерства эксплуатации 11 §11.2.2: выход из йодной ямы без нарушения ОЗР (тег {@code orm_min}), 1/M перед
     * критичностью (тег {@code points}), блэкстарт, следование графику, сутки без АЗ, промышленный комплекс.
     */
    public static final String XENON_PIT = "promptcritical:xenon_pit", HUNDRED_DAYS = "promptcritical:hundred_days",
            INDUSTRIAL_COMPLEX = "promptcritical:industrial_complex", LOAD_FOLLOW = "promptcritical:load_follow",
            PUMPJACK_DAY = "promptcritical:pumpjack_day", FIRST_CUT = "promptcritical:first_cut";
    /**
     * Эксплуатация 13 и ПАЗ 03d (11 §11.2.2): огневые работы по наряду с газоанализом, остановочный ремонт (тег
     * {@code repairs}), ремонт ГЦН по плану дозы (тег {@code dose_msv} — коллективная доза бригады), проверка ПАЗ в срок,
     * матрица причин ПАЗ (тег {@code causes}).
     */
    public static final String HOT_WORK_PERMIT = "promptcritical:hot_work_permit", TURNAROUND = "promptcritical:turnaround",
            ALARA = "promptcritical:alara", PROOF_TEST = "promptcritical:sis_proof_test", SIS_MATRIX = "promptcritical:sis_matrix";
    /**
     * SCADA/HMI 03c (11 §11.2.2): экран {@code pcui} уровня L1 сообщил число своих тегов (тег {@code tags}); урок
     * тренажёра пройден — общее событие {@code scenario_passed} тренажёра, тег — id урока.
     */
    public static final String HMI = "promptcritical:hmi_screen";
    /** Источники, уже объявившие событие: повторно не шлют (сбрасывается с выгрузкой блока). */
    private static final java.util.Map<Object, java.util.Set<String>> ONCE = new java.util.WeakHashMap<>();

    private SimAchievements() {
    }

    /** Событие мира у блока, один раз на экземпляр блока (первая нефть, первый налив и т. п.). */
    public static void once(net.minecraft.world.level.block.entity.BlockEntity be, String event, String tag, double value) {
        if (!(be.getLevel() instanceof ServerLevel sl)) return;
        synchronized (ONCE) {
            if (!ONCE.computeIfAbsent(be, k -> new java.util.HashSet<>()).add(event + "/" + tag)) return;
        }
        nearby(sl, be.getBlockPos(), event, tag, value);
    }

    /** Событие домена от устройства: всем игрокам рядом с ним. */
    public static void onMilestone(ServerLevel level, TopologyEditor topology, SimEvent.Milestone m) {
        onMilestone(level.players(), topology, m);
    }

    /** То же для заданных игроков (обычно — игроки измерения). */
    public static void onMilestone(Iterable<? extends ServerPlayer> players, TopologyEditor topology, SimEvent.Milestone m) {
        if (m.source().isNone() || !topology.exists(m.source())) return;
        GridPos g = topology.devicePos(m.source());
        nearby(players, new BlockPos(g.x(), g.y(), g.z()), m.event(), m.tag(), m.value());
    }

    /** Событие мира у позиции: всем игрокам в {@link #RADIUS_M} (не авторам). */
    public static void nearby(ServerLevel level, BlockPos pos, String event, String tag, double value) {
        nearby(level.players(), pos, event, tag, value);
    }

    public static void nearby(Iterable<? extends ServerPlayer> players, BlockPos pos, String event, String tag, double value) {
        double r2 = RADIUS_M * RADIUS_M;
        for (ServerPlayer p : players) {
            if (!p.isSpectator() && p.distanceToSqr(pos.getCenter()) <= r2) {
                PcTriggers.SIM_EVENT.get().trigger(p, event, tag, value, false);
                RouteService.onEvent(p, event, tag, value, p.server.getTickCount());
            }
        }
    }

    /** Событие, автор которого — игрок. */
    public static void direct(ServerPlayer player, String event, String tag, double value) {
        PcTriggers.SIM_EVENT.get().trigger(player, event, tag, value, true);
        RouteService.onEvent(player, event, tag, value, player.server.getTickCount());
    }
}
