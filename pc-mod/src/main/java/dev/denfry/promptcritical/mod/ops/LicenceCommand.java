package dev.denfry.promptcritical.mod.ops;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import dev.denfry.promptcritical.mod.content.HandbookService;
import dev.denfry.promptcritical.mod.content.RouteKeyTasks;
import dev.denfry.promptcritical.mod.content.RouteTask;
import dev.denfry.promptcritical.sim.ops.LicenceRegistry;
import dev.denfry.promptcritical.sim.ops.LicenceRules;
import dev.denfry.promptcritical.sim.ops.LicenceVerdict;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import dev.denfry.promptcritical.sim.ops.PlantEra;
import dev.denfry.promptcritical.sim.ops.UnitKind;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /promptcritical licence status|apply <era>} — лицензии команды игрока в профиле licensing (спецификация
 * профиля, §1). Задания маршрута и лицензии ведёт верхний мир; принятые типы установок собираются со всех уровней, а
 * выданная лицензия записывается в каждый.
 */
public final class LicenceCommand {
    private static final PlantEra[] GATED = {PlantEra.I1, PlantEra.I2, PlantEra.I3, PlantEra.I4};

    private LicenceCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("promptcritical")
                .then(Commands.literal("licence")
                        .then(Commands.literal("status").executes(LicenceCommand::status))
                        .then(Commands.literal("apply").then(Commands.argument("era", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (PlantEra e : GATED) b.suggest(e.name());
                                    return b.buildFuture();
                                })
                                .executes(LicenceCommand::apply)))));
    }

    /** Решение по эпохе для команды; выданная лицензия записывается во все уровни. */
    public static LicenceVerdict apply(MinecraftServer server, UUID team, PlantEra era, long tick) {
        LicenceVerdict v = evaluate(server, team, era);
        if (v.granted()) for (ServerLevel level : server.getAllLevels()) {
            OpsSubsystem ops = OpsWorld.ops(level);
            if (ops != null) ops.licences().grant(team, era, tick);
        }
        return v;
    }

    static LicenceVerdict evaluate(MinecraftServer server, UUID team, PlantEra era) {
        OpsSubsystem main = OpsWorld.ops(server.overworld());
        LicenceRegistry merged = new LicenceRegistry();
        if (main != null) {
            for (PlantEra e : main.licences().licences(team)) merged.grant(team, e, 0);
            for (String t : main.licences().tasksDone(team)) merged.taskDone(team, t);
        }
        for (ServerLevel level : server.getAllLevels()) {
            OpsSubsystem ops = OpsWorld.ops(level);
            if (ops != null) for (UnitKind k : ops.licences().acceptedKinds(team)) merged.accepted(team, k);
        }
        PlantEra prev = era.previous();
        Set<String> keys = prev == null ? Set.of() : RouteKeyTasks.of(HandbookManager.data(), prev.name());
        return LicenceRules.evaluate(merged, team, era, keys);
    }

    private static boolean enabled(MinecraftServer server) {
        OpsSubsystem ops = OpsWorld.ops(server.overworld());
        return ops != null && ops.licensing();
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        if (!enabled(server)) {
            ctx.getSource().sendSuccess(() -> OpsText.msg("licence.off"), false);
            return 0;
        }
        UUID team = TeamResolver.team(p);
        ctx.getSource().sendSuccess(() -> OpsText.msg("licence.header").withStyle(ChatFormatting.GOLD), false);
        for (PlantEra era : GATED) {
            LicenceVerdict v = evaluate(server, team, era);
            OpsSubsystem main = OpsWorld.ops(server.overworld());
            boolean held = main.licences().licensed(team, era);
            for (MutableComponent line : lines(p, v, held)) ctx.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int apply(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        if (!enabled(server)) {
            ctx.getSource().sendFailure(OpsText.msg("licence.off"));
            return 0;
        }
        PlantEra era;
        try {
            era = PlantEra.of(StringArgumentType.getString(ctx, "era").toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(OpsText.msg("licence.unknown_era", StringArgumentType.getString(ctx, "era")));
            return 0;
        }
        if (era == PlantEra.I0) {
            ctx.getSource().sendSuccess(() -> OpsText.msg("licence.held", era.name()), false);
            return 1;
        }
        UUID team = TeamResolver.team(p);
        LicenceVerdict v = apply(server, team, era, server.overworld().getGameTime());
        if (v.granted()) {
            ctx.getSource().sendSuccess(() -> OpsText.msg("licence.granted", era.name()).withStyle(ChatFormatting.GREEN), true);
            return 1;
        }
        for (MutableComponent line : lines(p, v, false)) ctx.getSource().sendFailure(line);
        return 0;
    }

    /** Строки по эпохе: есть, можно оформить или чего не хватает (эпоха, задания по названиям, типы установок). */
    private static List<MutableComponent> lines(ServerPlayer p, LicenceVerdict v, boolean held) {
        List<MutableComponent> out = new ArrayList<>();
        String era = v.era().name();
        if (held) {
            out.add(OpsText.msg("licence.held", era));
            return out;
        }
        if (v.granted()) {
            out.add(OpsText.msg("licence.ready", era, era));
            return out;
        }
        if (v.missingEra() != null) out.add(OpsText.msg("licence.missing_era", era, v.missingEra().name()));
        if (!v.missingTasks().isEmpty()) {
            String lang = HandbookService.lang(p);
            List<String> titles = new ArrayList<>();
            for (String id : v.missingTasks()) {
                RouteTask t = HandbookManager.data().tasks().get(ResourceLocation.parse(id));
                titles.add(t == null ? id : t.title().get(lang));
            }
            out.add(OpsText.msg("licence.missing_tasks", era, String.join(", ", titles)));
        }
        if (!v.missingKinds().isEmpty())
            out.add(OpsText.msg("licence.missing_kinds", era, OpsText.list(EnumSet.copyOf(v.missingKinds()))));
        if (out.isEmpty()) out.add(Component.literal(era));
        return out;
    }
}
