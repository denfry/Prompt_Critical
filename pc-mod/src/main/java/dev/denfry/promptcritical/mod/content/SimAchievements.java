package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.sim.core.GridPos;
import dev.denfry.promptcritical.sim.core.SimEvent;
import dev.denfry.promptcritical.sim.core.TopologyEditor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Доставка событий домена и мира в триггер {@code sim_event} (11 §11.2.1). События эпохи I0: пуск паровой машины
 * (домен), канарейка посажена в шахте и откачана (мир).
 */
public final class SimAchievements {
    /** Радиус «смены» вокруг источника события, м (11 §11.2.1). */
    public static final double RADIUS_M = 64;
    public static final String CANARY_PLACED = "promptcritical:canary_placed";
    public static final String CANARY_REVIVED = "promptcritical:canary_revived";

    private SimAchievements() {
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
            if (!p.isSpectator() && p.distanceToSqr(pos.getCenter()) <= r2)
                PcTriggers.SIM_EVENT.get().trigger(p, event, tag, value, false);
        }
    }

    /** Событие, автор которого — игрок. */
    public static void direct(ServerPlayer player, String event, String tag, double value) {
        PcTriggers.SIM_EVENT.get().trigger(player, event, tag, value, true);
    }
}
