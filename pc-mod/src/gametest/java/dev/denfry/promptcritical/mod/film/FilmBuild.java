package dev.denfry.promptcritical.mod.film;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.PromptCritical;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Стройка мира-завода (только dev): {@code /film run <имя>} выполняет команды из {@code <имя>.txt} в каталоге игры
 * (его пишет {@code art/film/plantworld.py}) порциями по {@value #BUDGET_MS} мс за тик. Перед каждой командой
 * синхронно генерирует и загружает чанки, которых она касается (setblock, fill, clone, place и команды с тремя
 * координатами), поэтому стройка идёт на живом рельефе без forceload всей площадки. {@code /film run stop} прерывает.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmBuild {
    private static final long BUDGET_MS = 40;
    private static final Pattern INT = Pattern.compile("(?<![\\w~^.])-?\\d+(?![\\w.])");

    private static List<String> lines;
    private static int next;
    private static String job;
    private static int errors;
    private static int lastPct = -1;
    private static long started;

    private FilmBuild() {
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("film").requires(s -> s.hasPermission(2))
                .then(Commands.literal("run").then(Commands.argument("name", StringArgumentType.word()).executes(FilmBuild::run)))
                .then(Commands.literal("feed").then(Commands.literal("stop").executes(ctx -> {
                            feed = null;
                            ctx.getSource().sendSuccess(() -> Component.literal("[film] feed stopped"), false);
                            return 1;
                        }))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 3600))
                                        .executes(FilmBuild::feed)))));
    }

    /**
     * {@code /film feed <name> <seconds>}: run {@code <name>.txt} again every period, quietly, so units that the
     * GameTests top up every tick (refinery feeds, furnace charges) keep running while a shot is filmed.
     */
    private static int feed(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        if (!Files.isRegularFile(Path.of(name + ".txt"))) {
            ctx.getSource().sendFailure(Component.literal("[film] no " + name + ".txt"));
            return 0;
        }
        feed = name;
        feedPeriod = 20 * com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
        feedCountdown = 0;
        ctx.getSource().sendSuccess(() -> Component.literal("[film] feed " + name + " every " + feedPeriod / 20 + " s"), false);
        return 1;
    }

    /** Repeating feed job, its period and ticks to its next run. */
    private static String feed;
    private static int feedPeriod, feedCountdown;
    private static boolean quiet;

    private static int run(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        CommandSourceStack src = ctx.getSource();
        if (name.equals("stop")) {
            lines = null;
            src.sendSuccess(() -> Component.literal("[film] run stopped at " + next), false);
            return 1;
        }
        if (lines != null) {
            src.sendFailure(Component.literal("[film] run " + job + " is still going: " + next + "/" + lines.size()));
            return 0;
        }
        String err = start(name, false);
        if (err != null) {
            src.sendFailure(Component.literal(err));
            return 0;
        }
        String msg = "[film] run " + name + ": " + lines.size() + " commands";
        src.sendSuccess(() -> Component.literal(msg), false);
        PromptCritical.LOGGER.info(msg);
        return 1;
    }

    /** Load {@code <name>.txt} as the current job; an error message, or {@code null} when it started. */
    private static String start(String name, boolean silent) {
        try {
            lines = Files.readAllLines(Path.of(name + ".txt")).stream()
                    .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException ex) {
            return "[film] cannot read " + name + ".txt: " + ex.getMessage();
        }
        job = name;
        next = 0;
        errors = 0;
        lastPct = -1;
        quiet = silent;
        started = System.currentTimeMillis();
        return null;
    }

    /** Ticks left of a {@code wait} line. */
    private static int pauseTicks;

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post e) {
        if (feed != null && --feedCountdown <= 0) {
            feedCountdown = feedPeriod;
            if (lines == null && start(feed, true) != null) feed = null;    // file gone: stop feeding
        }
        if (lines == null) return;
        if (pauseTicks > 0) {
            pauseTicks--;
            return;
        }
        MinecraftServer server = e.getServer();
        ServerLevel level = server.overworld();
        CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withPermission(4).withSuppressedOutput();
        long end = System.currentTimeMillis() + BUDGET_MS;
        while (lines != null && next < lines.size() && System.currentTimeMillis() < end) {
            String line = lines.get(next++);
            if (line.startsWith("wait ")) {        // "wait <ticks>": pause the run, e.g. to start machines one by one
                pauseTicks = Integer.parseInt(line.substring(5).trim());
                break;
            }
            try {
                load(level, line);
                server.getCommands().performPrefixedCommand(src, line);
            } catch (RuntimeException ex) {
                if (errors++ < 50) PromptCritical.LOGGER.warn("[film] run {} line {}: {} -> {}", job, next, line, ex.toString());
            }
        }
        if (lines == null) return;
        int pct = (int) (100L * next / Math.max(1, lines.size()));
        if (!quiet && pct / 10 != lastPct / 10) {
            lastPct = pct;
            say(server, "[film] run " + job + ": " + pct + " %");
        }
        if (next >= lines.size()) {
            if (quiet) {
                if (errors > 0) PromptCritical.LOGGER.warn("[film] feed {}: {} errors", job, errors);
                lines = null;
                return;
            }
            say(server, "[film] run " + job + " done: " + lines.size() + " commands, " + errors + " errors, "
                    + (System.currentTimeMillis() - started) / 1000 + " s");
            lines = null;
        }
    }

    private static void say(MinecraftServer server, String msg) {
        PromptCritical.LOGGER.info(msg);
        server.getPlayerList().broadcastSystemMessage(Component.literal(msg), false);
    }

    /** Генерирует чанки по горизонтальному охвату первых одной или двух троек координат команды. */
    private static void load(ServerLevel level, String line) {
        if (line.startsWith("execute") || line.startsWith("function") || line.startsWith("schedule")) return;
        Matcher m = INT.matcher(line);
        int[] v = new int[6];
        int n = 0;
        while (n < 6 && m.find()) v[n++] = Integer.parseInt(m.group());
        if (n < 3) return;
        int x1 = v[0], z1 = v[2], x2 = n >= 6 ? v[3] : x1, z2 = n >= 6 ? v[5] : z1;
        if (!line.startsWith("fill") && !line.startsWith("clone")) {
            x2 = x1;
            z2 = z1;
        }
        for (int cx = Math.min(x1, x2) >> 4; cx <= Math.max(x1, x2) >> 4; cx++)
            for (int cz = Math.min(z1, z2) >> 4; cz <= Math.max(z1, z2) >> 4; cz++)
                level.getChunk(cx, cz, ChunkStatus.FULL, true);
    }
}
