package dev.denfry.promptcritical.mod.film;

import com.mojang.logging.LogUtils;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.body.BodyService;
import dev.denfry.promptcritical.sim.body.BodyProfile;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * Only in {@code runFilm} (system property {@code promptcritical.film}): the camera player gets the reference body
 * before the mod asks for one, so the body screen never covers a shot or pauses the world, and the log gets a
 * stable {@code [film] player ready} line that launch scripts wait for.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmSession {
    private static final Logger LOGGER = LogUtils.getLogger();

    private FilmSession() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!Boolean.getBoolean("promptcritical.film") || !(e.getEntity() instanceof ServerPlayer p)) return;
        BodyService.submit(p, BodyProfile.REFERENCE);
        LOGGER.info("[film] player ready {} in {}", p.getGameProfile().getName(), p.server.getWorldData().getLevelName());
    }
}
