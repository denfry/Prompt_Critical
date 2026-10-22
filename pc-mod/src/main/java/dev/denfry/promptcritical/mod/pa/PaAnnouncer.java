package dev.denfry.promptcritical.mod.pa;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * Громкая связь: объявление звучит у источника, каждому игроку — на его языке (если озвучен).
 * Громкость — ползунок «Голос и речь».
 */
public final class PaAnnouncer {
    /** Громкость звука; слышно на 16 × VOLUME блоков. */
    static final float VOLUME = 3f;
    private static final double RANGE = 16 * VOLUME;
    /** Та же фраза поблизости не повторяется раньше, тиков (несколько котлов, генераторы одной сети). */
    private static final long REPEAT_TICKS = 200;
    /** Источник молчит после объявления, пока оно не договорено, тиков. */
    private static final long BUSY_TICKS = 80;

    private record Played(PaLine line, ResourceKey<Level> dim, BlockPos pos, long time) {
    }

    private static final List<Played> RECENT = new ArrayList<>();

    private PaAnnouncer() {
    }

    public static void announce(ServerLevel level, BlockPos pos, PaLine line) {
        long now = level.getGameTime();
        RECENT.removeIf(p -> p.time > now || now - p.time > REPEAT_TICKS);
        for (Played p : RECENT) {
            if (p.dim != level.dimension()) continue;
            if (p.pos.equals(pos) && now - p.time < BUSY_TICKS) return;
            if (p.line == line && p.pos.distSqr(pos) < RANGE * RANGE) return;
        }
        RECENT.add(new Played(line, level.dimension(), pos.immutable(), now));
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        long seed = level.random.nextLong();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) > RANGE * RANGE) continue;
            player.connection.send(new ClientboundSoundPacket(PaSounds.get(line, language(player)), SoundSource.VOICE,
                    x, y, z, VOLUME, 1f, seed));
        }
    }

    /** Озвученный язык игрока: точное совпадение, затем по языку без региона, иначе первый. */
    static String language(ServerPlayer player) {
        String lang = player.clientInformation().language();
        for (String v : PaLine.VOICED) if (v.equals(lang)) return v;
        for (String v : PaLine.VOICED) if (lang.length() >= 2 && v.startsWith(lang.substring(0, 2))) return v;
        return PaLine.VOICED[0];
    }
}
