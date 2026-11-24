package dev.denfry.promptcritical.mod.compat.voicechat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.events.VoiceDistanceEvent;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.pa.PaSystem;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Плагин Simple Voice Chat (16 §16.4): Simple Voice Chat находит его по аннотации, без мода класс не загружается.
 * Маскировка речи шумом (§16.4.2) и ГГС (§16.4.1): речь говорящего на тангенте пульта уходит в статические
 * локационные каналы громкоговорителей его зоны с радиусом разборчивости каждого и громкостью категории {@code pc_pa}.
 * Маршрут — снимок серверного потока ({@link PaSystem#route}); здесь, в голосовом потоке, он только читается.
 */
@ForgeVoicechatPlugin
public final class PcVoicechatPlugin implements VoicechatPlugin {
    /** Категория громкости ГГС у игрока. */
    static final String CATEGORY = "pc_pa";
    /** Канал на громкоговоритель: ключ — измерение и позиция. */
    private static final Map<String, LocationalAudioChannel> CHANNELS = new ConcurrentHashMap<>();

    @Override
    public String getPluginId() {
        return PromptCritical.MOD_ID;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoiceDistanceEvent.class, PcVoicechatPlugin::onDistance);
        registration.registerEvent(MicrophonePacketEvent.class, PcVoicechatPlugin::onMicrophone);
        registration.registerEvent(VoicechatServerStartedEvent.class, PcVoicechatPlugin::onStarted);
    }

    private static void onStarted(VoicechatServerStartedEvent e) {
        VoicechatServerApi api = e.getVoicechat();
        api.registerVolumeCategory(api.volumeCategoryBuilder().setId(CATEGORY).setName("Plant PA")
                .setNameTranslationKey("promptcritical.voicechat.pa").setDescription("Plant public address loudspeakers")
                .setDescriptionTranslationKey("promptcritical.voicechat.pa.description").build());
        CHANNELS.clear();
    }

    private static void onDistance(VoiceDistanceEvent e) {
        VoicechatConnection c = e.getSenderConnection();
        if (c == null || c.getPlayer() == null) return;
        e.setDistance(SpeechMasking.distance(c.getPlayer().getUuid(), e.getDistance()));
    }

    private static void onMicrophone(MicrophonePacketEvent e) {
        VoicechatConnection c = e.getSenderConnection();
        if (c == null || c.getPlayer() == null) return;
        PaSystem.Route route = PaSystem.route(c.getPlayer().getUuid());
        if (route == null || route.speakers().isEmpty()) return;
        VoicechatServerApi api = e.getVoicechat();
        String dim = route.level().dimension().location().toString();
        for (PaSystem.Speaker s : route.speakers()) {
            String key = dim + "@" + s.x() + "," + s.y() + "," + s.z();
            LocationalAudioChannel ch = CHANNELS.get(key);
            if (ch == null || ch.isClosed()) {
                ch = api.createLocationalAudioChannel(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)),
                        api.fromServerLevel(route.level()), api.createPosition(s.x(), s.y(), s.z()));
                if (ch == null) continue;
                ch.setCategory(CATEGORY);
                CHANNELS.put(key, ch);
            }
            ch.setDistance(s.rangeM());
            ch.send(e.getPacket());
        }
    }
}
