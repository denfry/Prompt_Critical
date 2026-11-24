package dev.denfry.promptcritical.mod.station;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.command.PcPermissions;
import dev.denfry.promptcritical.sim.core.Severity;
import dev.denfry.promptcritical.sim.journal.Actor;
import dev.denfry.promptcritical.sim.journal.JournalAction;
import dev.denfry.promptcritical.sim.journal.JournalCsv;
import dev.denfry.promptcritical.sim.journal.JournalEntry;
import dev.denfry.promptcritical.sim.journal.JournalFilter;
import dev.denfry.promptcritical.sim.journal.Role;
import dev.denfry.promptcritical.sim.journal.StationRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

/**
 * Команды станций (04d §4d.8.2, §4d.9.1): {@code /pc station info|grant|revoke|journal|claim} и
 * {@code /pc journal export}. Роли выдаются командой, а не ключ-картами из {@code control_room_core} — отступление от
 * §4d.9.1 до появления карт и их GUI.
 */
public final class StationCommands {
    private static final int MAX_LINES = 100;

    private static final SuggestionProvider<CommandSourceStack> ROLES = (ctx, b) -> SharedSuggestionProvider.suggest(
            java.util.Arrays.stream(Role.values()).map(r -> r.name().toLowerCase(Locale.ROOT)), b);
    private static final SuggestionProvider<CommandSourceStack> STATIONS = (ctx, b) -> {
        StationRegistry r = StationRegistry.of(ctx.getSource().getServer());
        return SharedSuggestionProvider.suggest(r == null ? List.<String>of() : r.all().stream().map(StationRecord::name).toList(), b);
    };

    private StationCommands() {
    }

    /** Ветка {@code station}: для игроков, права проверяются по ролям станции. */
    public static LiteralArgumentBuilder<CommandSourceStack> station() {
        return Commands.literal("station").requires(CommandSourceStack::isPlayer)
                .then(Commands.literal("info").executes(StationCommands::info))
                .then(Commands.literal("grant").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .executes(ctx -> assign(ctx, true, true)))))
                .then(Commands.literal("revoke").then(Commands.argument("player", GameProfileArgument.gameProfile())
                        .executes(ctx -> assign(ctx, false, false))
                        .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES)
                                .executes(ctx -> assign(ctx, false, true)))))
                .then(Commands.literal("journal").executes(ctx -> journal(ctx, 10))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, MAX_LINES))
                                .executes(ctx -> journal(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
                .then(Commands.literal("claim").executes(StationCommands::claim));
    }

    /** Ветка {@code journal export <станция>}: члены станции и администраторы — весь журнал, прочие — свои действия. */
    public static LiteralArgumentBuilder<CommandSourceStack> journal() {
        return Commands.literal("journal").requires(s -> s.isPlayer() || PcPermissions.admin(s))
                .then(Commands.literal("export").then(Commands.argument("station", StringArgumentType.string())
                        .suggests(STATIONS).executes(StationCommands::export)));
    }

    // ------------------------------------------------------------------

    @Nullable
    private static StationRecord here(CommandSourceStack src, StationRegistry r) {
        ServerPlayer p = src.getPlayer();
        if (p == null) return null;
        StationRecord s = r.at(p.level().dimension(), p.chunkPosition());
        if (s == null) src.sendFailure(StationText.msg("none_here"));
        return s;
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StationRegistry r = StationRegistry.of(src.getServer());
        StationRecord s = r == null ? null : here(src, r);
        if (s == null) return 0;
        ServerPlayer p = src.getPlayer();
        src.sendSuccess(() -> StationText.msg("info", s.name(), s.stationId().toString().substring(0, 8),
                ownerName(src, s.ownerTeam()), s.chunks().size(), s.createdTick()), false);
        s.members().forEach((uuid, roles) -> src.sendSuccess(() -> StationText.msg("info.member",
                playerName(src, uuid), Stations.roleList(roles)), false));
        Set<Role> mine = s.rolesOf(p.getUUID(), Stations.teamOf(p));
        src.sendSuccess(() -> StationText.msg("info.you", Stations.roleList(mine)), false);
        return 1;
    }

    private static int assign(CommandContext<CommandSourceStack> ctx, boolean grant, boolean withRole)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        StationRegistry r = StationRegistry.of(src.getServer());
        StationRecord s = r == null ? null : here(src, r);
        if (s == null) return 0;
        Role role = null;
        if (withRole) {
            String name = StringArgumentType.getString(ctx, "role").toUpperCase(Locale.ROOT);
            try {
                role = Role.valueOf(name);
            } catch (IllegalArgumentException e) {
                src.sendFailure(Component.literal("? " + name));
                return 0;
            }
        }
        ServerPlayer p = src.getPlayerOrException();
        Set<Role> mine = s.rolesOf(p.getUUID(), Stations.teamOf(p));
        boolean admin = PcPermissions.admin(src);
        // «Выпуск/отзыв ключ-карт, роли»: OWNER — все, SHIFT_SUPERVISOR — кроме OWNER (§4d.9.2)
        boolean allowed = admin || (role != null ? Role.canAssign(mine, role)
                : mine.contains(Role.OWNER) || Role.canAssign(mine, Role.VISITOR));
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "player");
        if (!allowed || (role == null && !admin && !mine.contains(Role.OWNER) && targets.stream()
                .anyMatch(t -> s.members().getOrDefault(t.getId(), Set.of()).contains(Role.OWNER)))) {
            src.sendFailure(StationText.msg("cannot_assign", role == null ? "*" : role.name().toLowerCase(Locale.ROOT), s.name()));
            return 0;
        }
        ServerLevel level = src.getLevel();
        long tick = level.getGameTime();
        StationRecord out = s;
        for (GameProfile t : targets) {
            out = grant ? out.withRole(t.getId(), role) : out.withoutRole(t.getId(), role);
            String roleName = role == null ? "*" : role.name().toLowerCase(Locale.ROOT);
            Actor actor = admin && !mine.contains(Role.OWNER) && !mine.contains(Role.SHIFT_SUPERVISOR)
                    ? new Actor.Admin(p.getUUID(), "pc station " + (grant ? "grant" : "revoke"))
                    : Stations.actor(p);
            // карт пока нет: выдача роли командой пишется как выпуск или отзыв карты (§4d.8.1)
            r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), actor,
                    grant ? JournalAction.CARD_ISSUED : JournalAction.CARD_REVOKED, t.getName(), "role " + roleName,
                    Severity.NOTICE));
            Component msg = grant ? StationText.msg("granted", t.getName(), roleName, s.name())
                    : role == null ? StationText.msg("revoked_all", t.getName(), s.name())
                    : StationText.msg("revoked", t.getName(), roleName, s.name());
            src.sendSuccess(() -> msg, true);
        }
        r.put(out);
        return targets.size();
    }

    private static int journal(CommandContext<CommandSourceStack> ctx, int n) {
        CommandSourceStack src = ctx.getSource();
        StationRegistry r = StationRegistry.of(src.getServer());
        StationRecord s = r == null ? null : here(src, r);
        if (s == null) return 0;
        ServerPlayer p = src.getPlayer();
        boolean full = PcPermissions.admin(src) || s.isMember(p.getUUID(), Stations.teamOf(p));
        List<JournalEntry> rows = r.journal().query(s.stationId(), full ? JournalFilter.ALL : JournalFilter.ownActions(p.getUUID()), n);
        src.sendSuccess(() -> StationText.msg("journal.head", s.name(), rows.size()), false);
        if (!full) src.sendSuccess(() -> StationText.msg("journal.own").withStyle(ChatFormatting.GRAY), false);
        if (rows.isEmpty()) src.sendSuccess(() -> StationText.msg("journal.empty"), false);
        for (JournalEntry e : rows) {
            ChatFormatting colour = switch (e.severity()) {
                case INFO -> ChatFormatting.GRAY;
                case NOTICE -> ChatFormatting.WHITE;
                case WARNING -> ChatFormatting.GOLD;
                case CRITICAL -> ChatFormatting.RED;
            };
            String line = "#" + e.seq() + " t" + e.gameTick() + " " + e.severity() + " " + e.action() + " "
                    + e.actor().display() + (e.target().isEmpty() ? "" : " " + e.target())
                    + (e.detail().isEmpty() ? "" : ": " + e.detail());
            src.sendSuccess(() -> Component.literal(line).withStyle(colour), false);
        }
        return rows.size();
    }

    /** Присоединяет чанк игрока к соседней (по стороне или углу) станции, где у него есть право выдавать роли. */
    private static int claim(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StationRegistry r = StationRegistry.of(src.getServer());
        ServerPlayer p = src.getPlayer();
        if (r == null || p == null) return 0;
        ChunkPos c = p.chunkPosition();
        StationRecord taken = r.at(p.level().dimension(), c);
        if (taken != null) {
            src.sendFailure(StationText.msg("claim_taken", taken.name()));
            return 0;
        }
        boolean admin = PcPermissions.admin(src);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                StationRecord s = r.at(p.level().dimension(), new ChunkPos(c.x + dx, c.z + dz));
                if (s == null || !(admin || s.has(p.getUUID(), Stations.teamOf(p),
                        dev.denfry.promptcritical.sim.journal.Permission.MANAGE_ROLES))) continue;
                r.put(s.withChunk(c.toLong()));
                long tick = src.getLevel().getGameTime();
                r.append(new JournalEntry(0, tick, tick / 20.0, s.stationId(), Stations.actor(p), JournalAction.COMMAND,
                        "chunk " + c.x + "," + c.z, "chunk claimed", Severity.NOTICE));
                src.sendSuccess(() -> StationText.msg("claimed", c.x + "," + c.z, s.name()), true);
                return 1;
            }
        src.sendFailure(StationText.msg("claim_none"));
        return 0;
    }

    private static int export(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        StationRegistry r = StationRegistry.of(src.getServer());
        String q = StringArgumentType.getString(ctx, "station");
        StationRecord s = r == null ? null : r.find(q);
        if (s == null) {
            src.sendFailure(StationText.msg("unknown", q));
            return 0;
        }
        ServerPlayer p = src.getPlayer();
        boolean full = PcPermissions.admin(src) || (p != null && s.isMember(p.getUUID(), Stations.teamOf(p)));
        List<JournalEntry> rows = full ? r.journal().entries(s.stationId())
                : r.journal().query(s.stationId(), JournalFilter.ownActions(p.getUUID()), Integer.MAX_VALUE);
        String file = s.name().replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + src.getLevel().getGameTime() + ".csv";
        Path dir = src.getServer().getWorldPath(LevelResource.ROOT).resolve(PromptCritical.MOD_ID).resolve("journal");
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(file), JournalCsv.format(rows), StandardCharsets.UTF_8);
        } catch (IOException e) {
            PromptCritical.LOGGER.warn("Journal export of {} failed", s.name(), e);
            src.sendFailure(StationText.msg("export_failed", e.getMessage()));
            return 0;
        }
        String shown = PromptCritical.MOD_ID + "/journal/" + file;
        src.sendSuccess(() -> StationText.msg("exported", rows.size(), shown), true);
        return rows.size();
    }

    // ------------------------------------------------------------------

    private static String playerName(CommandSourceStack src, UUID uuid) {
        var cache = src.getServer().getProfileCache();
        return cache == null ? uuid.toString() : cache.get(uuid).map(GameProfile::getName).orElse(uuid.toString());
    }

    private static String ownerName(CommandSourceStack src, UUID owner) {
        for (var t : src.getServer().getScoreboard().getPlayerTeams())
            if (StationRecord.teamId(t.getName()).equals(owner)) return "team " + t.getName();
        return playerName(src, owner);
    }
}
