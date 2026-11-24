package dev.denfry.promptcritical.mod.station;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.journal.Actor;
import dev.denfry.promptcritical.sim.journal.IncidentScale;
import dev.denfry.promptcritical.sim.journal.JournalAction;
import dev.denfry.promptcritical.sim.journal.JournalEntry;
import dev.denfry.promptcritical.sim.journal.Permission;
import dev.denfry.promptcritical.sim.journal.StationRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Станции и права на них в мире (04d §4d.9): основание станции пультом реактора, каскада или химпередела, проверка
 * прав с ответом {@code E_ACCESS}, журнал действий и срабатываний защиты (§4d.8.2), объявления по шкале §4d.8.3.
 * Вне станций ограничений нет; владелец станции имеет все права, поэтому одиночная игра ничего не замечает.
 */
public final class Stations {
    /** Пульты, установка которых в чанке без станции основывает станцию (§4d.9.1). */
    private static final List<Supplier<? extends Block>> FOUNDERS = List.of(
            // реакторы
            PcBlocks.REACTOR_CONTROLLER, PcBlocks.POWER_REACTOR, PcBlocks.PWR_REACTOR, PcBlocks.BWR_REACTOR,
            PcBlocks.CANDU_REACTOR, PcBlocks.SFR_REACTOR, PcBlocks.VVER_REACTOR, PcBlocks.VVER70_REACTOR_REAL,
            PcBlocks.BOR60_REACTOR_REAL, PcBlocks.BWR240_REACTOR, PcBlocks.CANDU6_REACTOR, PcBlocks.NPD_REACTOR_REAL,
            PcBlocks.BN600_REACTOR, PcBlocks.AM1_REACTOR, PcBlocks.TRANSIENT_ROD_DRIVE,
            // разделительный каскад
            PcBlocks.CENTRIFUGE_CASCADE, PcBlocks.CENTRIFUGE_CASCADE_SECTION_REAL,
            // химпередел топливного цикла
            PcBlocks.HYDROFLUORINATION_REACTOR, PcBlocks.FLUORINATION_REACTOR, PcBlocks.PUREX_CELL);

    /** Последнее известное состояние АЗ установки: запись в журнал — только по фронту. */
    private static final Map<GlobalPos, Boolean> TRIP_STATE = new HashMap<>();

    private Stations() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Stations::onPlace);
        // основание — после всех проверок: отменённая другой защитой установка станцию не создаёт
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, Stations::onPlaceFound);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Stations::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, Stations::onUseExecutor);
        NeoForge.EVENT_BUS.addListener(Stations::onServerTick);
        NeoForge.EVENT_BUS.addListener(Stations::onStopping);
        dev.denfry.promptcritical.mod.api.ApiEvents.stations(new RegistryStationDirectory());
    }

    public static boolean isFounder(BlockState state) {
        for (Supplier<? extends Block> b : FOUNDERS) if (state.is(b.get())) return true;
        return false;
    }

    // ------------------------------------------------------------------ кто есть кто

    /** Id команды игрока для {@link StationRecord#ownerTeam()}; {@code null} — игрок вне команды. */
    @Nullable
    public static UUID teamOf(Player p) {
        PlayerTeam t = p.getTeam() instanceof PlayerTeam pt ? pt : null;
        return t == null ? null : StationRecord.teamId(t.getName());
    }

    public static Actor.Player actor(Player p) {
        return new Actor.Player(p.getUUID(), p.getGameProfile().getName());
    }

    @Nullable
    public static StationRecord stationAt(ServerLevel level, BlockPos pos) {
        StationRegistry r = StationRegistry.of(level.getServer());
        return r == null ? null : r.at(level.dimension(), new ChunkPos(pos));
    }

    public static String roleList(Set<?> roles) {
        return roles.isEmpty() ? "-" : roles.stream().map(r -> r.toString().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    // ------------------------------------------------------------------ права

    /**
     * Проверка права игрока на устройстве в {@code pos}. Вне станции — можно. Отказ: {@code E_ACCESS} игроку и запись
     * WARNING в журнал, не чаще раза в минуту на пару (игрок, устройство) (§4d.8.2).
     */
    public static boolean check(ServerPlayer p, ServerLevel level, BlockPos pos, Permission perm, String device) {
        StationRegistry r = StationRegistry.of(level.getServer());
        if (r == null) return true;
        StationRecord s = r.at(level.dimension(), new ChunkPos(pos));
        if (s == null || s.has(p.getUUID(), teamOf(p), perm)) return true;
        p.displayClientMessage(StationText.msg("denied", s.name(), perm.name().toLowerCase(Locale.ROOT))
                .withStyle(ChatFormatting.RED), true);
        long tick = level.getGameTime();
        // блоки — один ключ на станцию, иначе серия по разным координатам вытеснила бы журнал (§4d.8.2)
        String key = perm == Permission.BREAK_PLACE ? "blocks@" + s.stationId() : device;
        if (r.denials.shouldLog(p.getUUID(), key, tick))
            r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor(p), JournalAction.UNAUTHORIZED_ACCESS,
                    device, "needs " + perm.name(), Severity.WARNING));
        return false;
    }

    /**
     * Кнопка щита (§4d.9.2): право по {@link PanelHost#requiredPermission(int)}; принятая на станции команда — запись
     * INFO в журнал (§4d.8.2).
     */
    public static boolean allowPanel(ServerPlayer p, PanelHost host, int button) {
        if (!(p.level() instanceof ServerLevel level)) return true;
        BlockPos pos = host.panelPos();
        String device = device(level, pos);
        if (!check(p, level, pos, host.requiredPermission(button), device)) return false;
        StationRegistry r = StationRegistry.of(level.getServer());
        StationRecord s = r == null ? null : r.at(level.dimension(), new ChunkPos(pos));
        if (s != null) {
            long tick = level.getGameTime();
            r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor(p), JournalAction.COMMAND, device,
                    "button " + button, Severity.INFO));
        }
        return true;
    }

    /** Поле ввода щита (уставка, имя) — управление механизмом: нужно {@link Permission#CONTROL}. */
    public static boolean allowPanelText(ServerPlayer p, PanelHost host) {
        if (!(p.level() instanceof ServerLevel level)) return true;
        BlockPos pos = host.panelPos();
        return check(p, level, pos, Permission.CONTROL, device(level, pos));
    }

    static String device(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().getDescriptionId().replaceFirst("^block\\.[a-z_]+\\.", "")
                + "@" + pos.toShortString();
    }

    // ------------------------------------------------------------------ блоки

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)) return;
        StationRegistry r = StationRegistry.of(level.getServer());
        if (r == null) return;
        BlockPos pos = e.getPos();
        StationRecord s = r.at(level.dimension(), new ChunkPos(pos));
        if (s == null) return;
        if (!check(p, level, pos, Permission.BREAK_PLACE, device(level, pos))) {
            e.setCanceled(true);
            return;
        }
        note(r, s, p, JournalAction.BLOCK_PLACE, pos, level.getGameTime());
    }

    private static void onPlaceFound(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)) return;
        StationRegistry r = StationRegistry.of(level.getServer());
        if (r == null) return;
        if (isExecutor(e.getPlacedBlock())) recordPlacer(r, level, e.getPos(), p);
        if (isFounder(e.getPlacedBlock()) && r.at(level.dimension(), new ChunkPos(e.getPos())) == null)
            found(r, level, p, e.getPos());
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getPlayer() instanceof ServerPlayer p)) return;
        StationRegistry r = StationRegistry.of(level.getServer());
        if (r == null) return;
        BlockPos pos = e.getPos();
        StationRecord s = r.at(level.dimension(), new ChunkPos(pos));
        if (s != null && !check(p, level, pos, Permission.BREAK_PLACE, device(level, pos))) {
            e.setCanceled(true);
            return;
        }
        // компьютер уносит свой id в предмете, его владелец остаётся; запись по месту снимается
        if (r.placed.remove(GlobalPos.of(level.dimension(), pos)) != null) r.setDirty();
        if (s == null) return;
        note(r, s, p, JournalAction.BLOCK_BREAK, pos, level.getGameTime());
    }

    private static void note(StationRegistry r, StationRecord s, ServerPlayer p, JournalAction a, BlockPos pos, long tick) {
        for (JournalEntry closed : r.blockSeries.note(s.stationId(), actor(p), a, pos.toShortString(),
                s.isMember(p.getUUID(), teamOf(p)), tick))
            r.append(closed);
    }

    /** Новая станция на чанке пульта; владелец — команда игрока или он сам (§4d.9.1). */
    static StationRecord found(StationRegistry r, ServerLevel level, ServerPlayer p, BlockPos pos) {
        UUID team = teamOf(p);
        String base = p.getTeam() instanceof PlayerTeam t ? t.getName() : p.getGameProfile().getName();
        StationRecord s = StationRecord.create(UUID.randomUUID(), r.uniqueName(base), team != null ? team : p.getUUID(),
                r.dimensionIndex(level.dimension()), new ChunkPos(pos).toLong(), level.getGameTime());
        r.put(s);
        long tick = level.getGameTime();
        r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor(p), JournalAction.COMMAND,
                device(level, pos), "station founded", Severity.NOTICE));
        p.displayClientMessage(StationText.msg("created", s.name()).withStyle(ChatFormatting.GREEN), false);
        return s;
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        long tick = server.overworld().getGameTime();
        if (tick % 20 != 0) return;
        StationRegistry r = StationRegistry.of(server);
        if (r != null) for (JournalEntry closed : r.blockSeries.flush(tick, false)) r.append(closed);
    }

    private static void onStopping(ServerStoppingEvent e) {
        StationRegistry r = StationRegistry.of(e.getServer());
        if (r != null) for (JournalEntry closed : r.blockSeries.flush(Long.MAX_VALUE, true)) r.append(closed);
        TRIP_STATE.clear();
    }

    // ------------------------------------------------------------------ компьютеры

    /** Блоки, от имени которых уходят команды: компьютеры и черепахи CC, сервер MES. */
    public static boolean isExecutor(BlockState state) {
        if (state.is(PcBlocks.MES_SERVER.get())) return true;
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "computercraft".equals(key.getNamespace())
                && (key.getPath().startsWith("computer_") || key.getPath().startsWith("turtle_"));
    }

    static void recordPlacer(StationRegistry r, ServerLevel level, BlockPos pos, Player p) {
        StationRegistry.Placer who = new StationRegistry.Placer(p.getUUID(), p.getGameProfile().getName(),
                GlobalPos.of(level.dimension(), pos.immutable()));
        int id = computerIdAt(level, pos);
        if (id >= 0) {
            r.computers.put(id, who);
            r.placed.remove(GlobalPos.of(level.dimension(), pos));
        } else r.placed.put(GlobalPos.of(level.dimension(), pos.immutable()), who);
        r.setDirty();
    }

    /**
     * Id компьютера CC в блоке или -1: читается из сохранения блока ({@code ComputerId}), API CC его по месту не даёт.
     * Новый компьютер получает id при первом запуске, поэтому запись по месту привязывается к id позже.
     */
    static int computerIdAt(ServerLevel level, BlockPos pos) {
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return -1;
        net.minecraft.nbt.CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        return tag.contains("ComputerId", net.minecraft.nbt.Tag.TAG_INT) ? tag.getInt("ComputerId") : -1;
    }

    /** Кто поставил компьютер {@code id}; записи по месту из загруженных чанков привязываются к id при поиске. */
    @Nullable
    public static StationRegistry.Placer computerOwner(MinecraftServer server, int id) {
        StationRegistry r = StationRegistry.of(server);
        if (r == null || id < 0) return null;
        StationRegistry.Placer p = r.computers.get(id);
        if (p != null) return p;
        for (var it = r.placed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerLevel l = server.getLevel(e.getKey().dimension());
            BlockPos at = e.getKey().pos();
            if (l == null || !l.isLoaded(at) || l.getBlockState(at).is(PcBlocks.MES_SERVER.get())) continue;
            int found = computerIdAt(l, at);
            if (found < 0) continue;
            r.computers.putIfAbsent(found, e.getValue()); // явная запись командой важнее старой по месту
            it.remove();
            r.setDirty();
        }
        return r.computers.get(id);
    }

    /** Кто поставил блок-исполнитель в {@code pos} (сервер MES). */
    @Nullable
    public static StationRegistry.Placer placerAt(ServerLevel level, BlockPos pos) {
        StationRegistry r = StationRegistry.of(level.getServer());
        return r == null ? null : r.placed.get(GlobalPos.of(level.dimension(), pos));
    }

    /** Записывает владельца компьютера {@code id}, стоящего в {@code at}, напрямую (GameTest, перенос мира). */
    public static void bindComputer(MinecraftServer server, int id, UUID owner, String name, GlobalPos at) {
        StationRegistry r = StationRegistry.of(server);
        if (r == null) return;
        r.computers.put(id, new StationRegistry.Placer(owner, name, at));
        r.setDirty();
    }

    /** Владелец исполнителя в {@code pos}: компьютера CC по его id, сервера MES по месту. */
    @Nullable
    static StationRegistry.Placer executorOwner(ServerLevel level, BlockPos pos) {
        int id = computerIdAt(level, pos);
        return id >= 0 ? computerOwner(level.getServer(), id) : placerAt(level, pos);
    }

    /**
     * Почему исполнитель не может действовать на станции {@code s}, или {@code null}: он должен стоять в её чанках и
     * быть там, где записан (сдвинутая черепаха или переставленный компьютер теряет права до {@code /pc station
     * computer}). Иначе права владельца получил бы любой, кто сел за его компьютер вне станции (R74).
     */
    @Nullable
    private static String executorOutside(MinecraftServer server, StationRegistry r, StationRecord s, int computerId,
                                          StationRegistry.Placer owner) {
        GlobalPos at = owner.at();
        if (at == null) return "the computer's place is not recorded, use /pc station computer";
        StationRecord there = r.at(at.dimension(), new ChunkPos(at.pos()));
        if (there == null || !there.stationId().equals(s.stationId()))
            return "the computer stands outside the station at " + at.pos().toShortString();
        ServerLevel l = server.getLevel(at.dimension());
        if (l == null || !l.isLoaded(at.pos())) return "the computer's chunk is not loaded";
        boolean present = computerId >= 0 ? computerIdAt(l, at.pos()) == computerId
                : l.getBlockState(at.pos()).is(PcBlocks.MES_SERVER.get());
        return present ? null : "the computer is not where it was recorded, use /pc station computer";
    }

    /**
     * Открыть чужой компьютер, черепаху или сервер MES в чанке станции можно только с правом управления (§4d.9.2):
     * иначе посетитель отдал бы команды с правами владельца исполнителя. Свой исполнитель владелец открывает всегда.
     * Запрещается только использование блока, предмет в руке (установка блока к грани) работает как обычно.
     */
    private static void onUseExecutor(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getEntity() instanceof ServerPlayer p)) return;
        BlockPos pos = e.getPos();
        if (!isExecutor(level.getBlockState(pos)) || stationAt(level, pos) == null) return;
        StationRegistry.Placer who = executorOwner(level, pos);
        if (who != null && who.uuid().equals(p.getUUID())) return;
        if (!check(p, level, pos, Permission.CONTROL, device(level, pos)))
            e.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    /**
     * Команда устройству в {@code pos} от компьютера или автоматики (Lua, RTU, MES): права — у игрока, поставившего
     * исполнитель (§4d.9.2). Вне станции — можно. Принятая на станции команда — запись INFO от {@link Actor.Computer};
     * отказ — запись WARNING не чаще раза в минуту на пару (компьютер, устройство) и текст {@code E_ACCESS}.
     *
     * @param computerId id компьютера CC, -1 у сервера MES
     * @param owner      владелец исполнителя; {@code null} — неизвестен (поставлен до учёта), прав нет
     * @param journal    писать ли принятую команду (отказ пишется всегда, с прореживанием)
     * @return {@code null} — разрешено, иначе текст отказа для квитанции или исключения Lua
     */
    @Nullable
    public static String allowComputer(ServerLevel level, BlockPos pos, int computerId, @Nullable StationRegistry.Placer owner,
                                       String label, Permission perm, String command, boolean journal) {
        StationRegistry r = StationRegistry.of(level.getServer());
        if (r == null) return null;
        StationRecord s = r.at(level.dimension(), new ChunkPos(pos));
        if (s == null) return null;
        String device = device(level, pos);
        long tick = level.getGameTime();
        Actor actor = new Actor.Computer(computerId, owner == null ? null : owner.uuid(), label);
        UUID team = null;
        if (owner != null) {
            PlayerTeam t = level.getServer().getScoreboard().getPlayersTeam(owner.name());
            team = t == null ? null : StationRecord.teamId(t.getName());
        }
        String outside = owner == null ? null : executorOutside(level.getServer(), r, s, computerId, owner);
        if (owner != null && outside == null && s.has(owner.uuid(), team, perm)) {
            if (journal) r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor, JournalAction.COMMAND, device, command,
                    Severity.INFO));
            return null;
        }
        if (r.denials.shouldLog(owner == null ? NO_OWNER : owner.uuid(), "computer#" + computerId + "@" + device, tick))
            r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor, JournalAction.UNAUTHORIZED_ACCESS, device,
                    command + (outside != null ? ": " + outside : " needs " + perm.name()), Severity.WARNING));
        return "E_ACCESS: station " + s.name() + ", " + (owner == null ? "the computer has no recorded owner"
                : outside != null ? outside : owner.name() + " lacks " + perm.name().toLowerCase(Locale.ROOT));
    }

    /** Ключ ограничителя отказов для компьютеров без владельца. */
    private static final UUID NO_OWNER = new UUID(0, 0);

    // ------------------------------------------------------------------ защита

    /**
     * Состояние АЗ установки, вызывается её тиком. По фронту пишет {@link JournalAction#TRIP} (CRITICAL) или
     * {@link JournalAction#TRIP_RESET} (NOTICE) от имени автоматики {@code PROTECTION} (§4d.8.2); первое наблюдение
     * после загрузки только запоминается. АЗ — событие уровня 0 шкалы §4d.8.3.
     *
     * @param reason первопричина срабатывания для записи, может быть пустой
     */
    public static void tripState(ServerLevel level, BlockPos pos, boolean tripped, String reason) {
        Boolean was = TRIP_STATE.put(GlobalPos.of(level.dimension(), pos.immutable()), tripped);
        if (was == null || was == tripped) return;
        StationRegistry r = StationRegistry.of(level.getServer());
        StationRecord s = r == null ? null : r.at(level.dimension(), new ChunkPos(pos));
        if (s == null) return;
        long tick = level.getGameTime();
        r.append(StationRegistry.auto(s.stationId(), tick, "PROTECTION", tripped ? JournalAction.TRIP : JournalAction.TRIP_RESET,
                device(level, pos), reason, tripped ? Severity.CRITICAL : Severity.NOTICE));
        announce(level, pos, s, IncidentScale.LEVEL_TRIP, Component.literal((tripped ? "TRIP " : "TRIP RESET ") + reason));
    }

    /**
     * Объявление события уровня {@code level} шкалы §4d.8.3, если он не ниже {@code journal.announce_min_level}: команде
     * станции, соседям в 256 м, всему серверу, со звуком или титром.
     */
    public static void announce(ServerLevel world, BlockPos at, StationRecord s, int level, Component what) {
        int min = ServerConfig.SPEC.isLoaded() ? ServerConfig.JOURNAL_ANNOUNCE_MIN_LEVEL.get()
                : IncidentScale.DEFAULT_ANNOUNCE_MIN_LEVEL;
        if (!IncidentScale.announce(level, min)) return;
        IncidentScale.Audience aud = IncidentScale.audience(level);
        Component line = StationText.msg("announce", s.name(), level, what)
                .withStyle(level >= 4 ? ChatFormatting.DARK_RED : ChatFormatting.GOLD);
        double r2 = IncidentScale.NEIGHBOUR_RADIUS_M * IncidentScale.NEIGHBOUR_RADIUS_M;
        Stream<ServerPlayer> all = world.getServer().getPlayerList().getPlayers().stream();
        List<ServerPlayer> to = switch (aud) {
            case STATION -> all.filter(p -> s.isMember(p.getUUID(), teamOf(p))).toList();
            case STATION_AND_NEIGHBOURS -> all.filter(p -> s.isMember(p.getUUID(), teamOf(p))
                    || p.level() == world && p.distanceToSqr(at.getCenter()) <= r2).toList();
            default -> all.toList();
        };
        for (ServerPlayer p : to) {
            p.sendSystemMessage(line);
            if (aud == IncidentScale.Audience.SERVER_SOUND || aud == IncidentScale.Audience.SERVER_TITLE)
                p.playNotifySound(SoundEvents.WARDEN_SONIC_BOOM, SoundSource.MASTER, 1f, 0.5f);
            if (aud == IncidentScale.Audience.SERVER_TITLE)
                p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(line));
        }
    }
}
