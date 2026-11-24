package dev.denfry.promptcritical.mod.pa;

import dev.denfry.promptcritical.mod.acoustics.NoiseService;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.sound.PcSfx;
import dev.denfry.promptcritical.sim.acoustics.SpeechRange;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Громкоговорящая связь станции (13 §13.3.4, 16 §16.4.1). Громкоговорители регистрируются при загрузке; оповещение
 * идёт на громкоговорители своей команды (FTB Teams; без него — общий канал) в зоне сообщения или во всех зонах.
 * Слышно там, где рупор громче шума 04a на {@code ops.pa_snr_db}: в этом радиусе игроки получают сигнал и субтитр.
 * Сообщение с более высоким приоритетом прерывает текущее, с более низким — ждёт его конца.
 *
 * <p>Живая речь через Simple Voice Chat: серверный поток раз в полсекунды собирает для каждого говорящего снимок
 * маршрута — громкоговорители и их радиусы, голосовой поток плагина ({@code compat.voicechat}) только читает его.
 */
public final class PaSystem {
    /** Длительность предзаписанного сообщения и подсветки рупора, тиков. */
    static final int MESSAGE_TICKS = 100;
    /** Пульт держит тангенту не дольше, тиков; говорящий — не дальше 2 м от пульта (13 §13.3.4). */
    static final int TALK_MAX_TICKS = 20 * 60;
    static final double TALK_REACH_SQ = 2.5 * 2.5;

    private static final Tr SUBTITLE = PanelText.tr("pa.subtitle", "PA [%s] Zone %s: %s", "ГГС [%s] Зона %s: %s"),
            SUBTITLE_ALL = PanelText.tr("pa.subtitle_all", "PA [%s]: %s", "ГГС [%s]: %s");

    private static final Map<Level, Set<BlockPos>> SPEAKERS = new HashMap<>();
    /** Говорящий → маршрут его речи; читает голосовой поток. */
    private static final Map<UUID, Route> ROUTES = new ConcurrentHashMap<>();

    /** Громкоговоритель для голосового потока: позиция центра рупора и радиус разборчивости, м. */
    public record Speaker(double x, double y, double z, float rangeM) {
    }

    /** Куда идёт речь говорящего: уровень и громкоговорители. */
    public record Route(ServerLevel level, List<Speaker> speakers) {
    }

    private PaSystem() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PaSystem::onPlace);
        NeoForge.EVENT_BUS.addListener(PaSystem::onServerTick);
    }

    static synchronized void add(Level level, BlockPos pos) {
        SPEAKERS.computeIfAbsent(level, k -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> s = SPEAKERS.get(level);
        if (s != null) s.remove(pos);
    }

    /** Команда станции, поставившей устройство; {@code null} — без FTB Teams все на общем канале. */
    @Nullable
    static UUID teamOf(ServerPlayer p) {
        return ModList.get().isLoaded("ftbteams") ? dev.denfry.promptcritical.mod.ops.TeamResolver.team(p) : null;
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel sl)) return;
        if (sl.getBlockEntity(e.getPos()) instanceof PaDevice d) d.setTeam(teamOf(p));
    }

    /** Громкоговорители команды {@code team} в зоне {@code zone}; {@code zone} 0 — все зоны. */
    static synchronized List<PlantLoudspeakerBlockEntity> speakers(ServerLevel level, @Nullable UUID team, int zone) {
        List<PlantLoudspeakerBlockEntity> out = new ArrayList<>();
        Set<BlockPos> s = SPEAKERS.get(level);
        if (s == null) return out;
        for (BlockPos p : List.copyOf(s)) {
            if (!level.isLoaded(p) || !(level.getBlockEntity(p) instanceof PlantLoudspeakerBlockEntity b)) continue;
            if (Objects.equals(b.team(), team) && (zone == 0 || b.zone() == zone)) out.add(b);
        }
        return out;
    }

    /** Радиус разборчивости громкоговорителя по шуму у рупора, м. */
    static double rangeM(ServerLevel level, BlockPos pos) {
        double noise = NoiseService.levelAt(level, pos.getCenter());
        return SpeechRange.speakerMetres(ServerConfig.PA_SPEAKER_SPL_DB.get(), noise, ServerConfig.PA_SNR_DB.get(),
                ServerConfig.PA_MAX_RANGE_M.get());
    }

    /**
     * Предзаписанное сообщение: гонг, сигнал канала и субтитр игрокам в радиусе разборчивости каждого громкоговорителя.
     *
     * @return число громкоговорителей, которые его передали; 0 — нет зоны, нет питания или занято важнее
     */
    public static int announce(ServerLevel level, @Nullable UUID team, int zone, PaMessage msg) {
        return broadcast(level, team, msg.channel, zone, msg.text.get(), msg);
    }

    /**
     * Текстовый вызов (13 §13.3.4): без Simple Voice Chat живую речь заменяет сообщение, набранное в пульте, — гонг и
     * субтитр по каналу пульта, с его приоритетом.
     *
     * @return число громкоговорителей, которые его передали
     */
    public static int page(ServerLevel level, @Nullable UUID team, PaChannel channel, int zone, String text) {
        return broadcast(level, team, channel, zone, Component.literal(text), null);
    }

    /** Гонг, сигнал сообщения {@code signal} (у текстового вызова его нет) и субтитр {@code body} по каналу. */
    private static int broadcast(ServerLevel level, @Nullable UUID team, PaChannel channel, int zone, Component body,
            @Nullable PaMessage signal) {
        List<PlantLoudspeakerBlockEntity> list = speakers(level, team, channel.allZones ? 0 : zone);
        long now = level.getGameTime();
        for (PlantLoudspeakerBlockEntity s : list) if (s.busyAbove(channel.priority, now)) return 0;
        Component text = channel.allZones ? SUBTITLE_ALL.get(channel.label.get(), body) : SUBTITLE.get(channel.label.get(), zone, body);
        Set<ServerPlayer> told = new HashSet<>();
        int sent = 0;
        for (PlantLoudspeakerBlockEntity s : list) {
            if (!s.powered()) continue;
            s.play(channel.priority, now + MESSAGE_TICKS);
            BlockPos p = s.getBlockPos();
            level.playSound(null, p, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 3f, 0.7f);
            if (signal != null) switch (signal) {
                case EVACUATE, GAS -> level.playSound(null, p, PcSfx.AGZ_SIREN.get(), SoundSource.BLOCKS, 3f, signal == PaMessage.EVACUATE ? 0.8f : 1.1f);
                case FIRE -> level.playSound(null, p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 3f, 1.2f);
                case ALL_CLEAR -> level.playSound(null, p, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 3f, 1.0f);
            }
            double r = rangeM(level, p);
            for (ServerPlayer pl : level.players())
                if (pl.distanceToSqr(p.getCenter()) <= r * r && told.add(pl))
                    pl.displayClientMessage(text.copy().withStyle(channel.priority <= 1 ? ChatFormatting.RED : ChatFormatting.YELLOW), true);
            sent++;
        }
        return sent;
    }

    /** Маршрут речи говорящего для голосового потока; {@code null} — он не на тангенте. */
    @Nullable
    public static Route route(UUID talker) {
        return ROUTES.get(talker);
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) return;
        Map<UUID, Route> next = new HashMap<>();
        for (ServerLevel level : e.getServer().getAllLevels()) {
            for (IntercomStationBlockEntity ic : IntercomStationBlockEntity.talking(level)) {
                UUID talker = ic.talker();
                if (talker == null) continue;
                List<Speaker> list = new ArrayList<>();
                long now = level.getGameTime();
                for (PlantLoudspeakerBlockEntity s : speakers(level, ic.team(), ic.channel().allZones ? 0 : ic.zone())) {
                    if (!s.powered() || s.busyAbove(ic.channel().priority, now)) continue;
                    s.play(ic.channel().priority, now + 20);
                    var c = s.getBlockPos().getCenter();
                    list.add(new Speaker(c.x, c.y, c.z, (float) rangeM(level, s.getBlockPos())));
                }
                next.put(talker, new Route(level, List.copyOf(list)));
            }
        }
        ROUTES.keySet().retainAll(next.keySet());
        ROUTES.putAll(next);
    }

    /** Устройство ГГС с командой станции. */
    interface PaDevice {
        void setTeam(@Nullable UUID team);
    }
}
