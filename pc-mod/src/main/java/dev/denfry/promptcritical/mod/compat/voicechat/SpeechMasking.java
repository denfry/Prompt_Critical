package dev.denfry.promptcritical.mod.compat.voicechat;

import dev.denfry.promptcritical.mod.acoustics.NoiseService;
import dev.denfry.promptcritical.mod.compat.CompatSwitch;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.gear.ElectricGearItem;
import dev.denfry.promptcritical.mod.safety.PpeItem;
import dev.denfry.promptcritical.mod.safety.PpeService;
import dev.denfry.promptcritical.sim.acoustics.SpeechRange;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Шум цеха глушит речь (16 §16.4.2). Пакеты микрофона приходят в голосовом потоке Simple Voice Chat, поэтому всё, что
 * он читает, — неизменяемый снимок, который серверный поток обновляет раз в секунду: шум у говорящего по 04a и
 * множитель его маски. Класс не ссылается на API Simple Voice Chat и работает без него.
 */
public final class SpeechMasking {
    /** Снимок говорящего: шум у него, дБ(А), и множитель маски. */
    record Speaker(double noiseDb, double mask) {}

    /** Настройки, прочитанные серверным потоком. */
    record Settings(boolean enabled, double speechDb, double minM) {}

    private static final Map<UUID, Speaker> SPEAKERS = new ConcurrentHashMap<>();
    private static volatile Settings settings = new Settings(false, SpeechRange.SPEECH_DB, SpeechRange.MIN_M);

    private SpeechMasking() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SpeechMasking::onServerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> SPEAKERS.remove(e.getEntity().getUUID()));
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0) return;
        boolean on = ServerConfig.SPEC.isLoaded() && CompatSwitch.active(ServerConfig.COMPAT_VOICECHAT, "voicechat")
                && ServerConfig.VOICE_NOISE_MASKING.get();
        settings = on ? new Settings(true, ServerConfig.VOICE_SPEECH_SPL_DB.get(), ServerConfig.VOICE_MIN_RANGE_M.get())
                : new Settings(false, SpeechRange.SPEECH_DB, SpeechRange.MIN_M);
        if (!on) {
            SPEAKERS.clear();
            return;
        }
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers())
            SPEAKERS.put(p.getUUID(), new Speaker(NoiseService.lastLevelDb(p), mask(p)));
    }

    /** Маска глушит владельца: противогаз и респираторы ×0,6, изолирующий аппарат с мембраной ×0,8. */
    static double mask(ServerPlayer p) {
        if (PpeItem.is(p.getItemBySlot(EquipmentSlot.CHEST), PpeItem.Kind.SCBA)) return SpeechRange.SCBA_FACTOR;
        if (PpeService.wearsGasMask(p) || p.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof ElectricGearItem g
                && g.kind() == ElectricGearItem.Kind.RESPIRATOR) return SpeechRange.MASK_FACTOR;
        return 1;
    }

    /** Дальность речи игрока при дальности голоса сервера {@code serverM}; вызывается из голосового потока. */
    public static float distance(UUID speaker, float serverM) {
        Settings s = settings;
        Speaker sp = s.enabled ? SPEAKERS.get(speaker) : null;
        if (sp == null) return serverM;
        return (float) SpeechRange.metres(s.speechDb, sp.noiseDb, s.minM, serverM, sp.mask);
    }
}
