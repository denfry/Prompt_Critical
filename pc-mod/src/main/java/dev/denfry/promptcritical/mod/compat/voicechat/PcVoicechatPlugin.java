package dev.denfry.promptcritical.mod.compat.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import dev.denfry.promptcritical.mod.PromptCritical;

/**
 * Плагин Simple Voice Chat (16 §16.4): Simple Voice Chat находит его по аннотации, без мода класс не загружается.
 * Сейчас — маскировка речи шумом (§16.4.2); ГГС §16.4.1 ждёт блоков пульта и громкоговорителя 13 §13.3.4.
 */
@ForgeVoicechatPlugin
public final class PcVoicechatPlugin implements VoicechatPlugin {
    @Override
    public String getPluginId() {
        return PromptCritical.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoiceDistanceEvent.class, PcVoicechatPlugin::onDistance);
    }

    private static void onDistance(VoiceDistanceEvent e) {
        VoicechatConnection c = e.getSenderConnection();
        if (c == null || c.getPlayer() == null) return;
        e.setDistance(SpeechMasking.distance(c.getPlayer().getUuid(), e.getDistance()));
    }
}
