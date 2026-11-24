package dev.denfry.promptcritical.mod.compat.ftbquests;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.content.HandbookManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * {@code /pc export ftbquests [folder]} (16 §16.11.2): writes the route as an FTB Quests book. Without a folder it goes
 * to {@code <world>/config/ftbquests/quests}; a folder is relative to the server directory and must stay inside it. FTB
 * Quests itself reads {@code <server>/config/ftbquests/quests}: copy the export there, or merge it in its editor.
 * A folder that already holds another quest book is left alone.
 */
public final class FtbQuestsCommand {
    private FtbQuestsCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("ftbquests")
                .executes(ctx -> run(ctx, null))
                .then(Commands.argument("folder", StringArgumentType.string()).executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "folder"))));
    }

    private static int run(CommandContext<CommandSourceStack> ctx, String folder) {
        MinecraftServer server = ctx.getSource().getServer();
        try {
            Path root = server.getServerDirectory().toRealPath();
            Path target = real(folder == null ? server.getWorldPath(LevelResource.ROOT).resolve("config/ftbquests/quests") : root.resolve(folder));
            if (!target.startsWith(root)) {
                ctx.getSource().sendFailure(Component.literal("FTB Quests export: " + folder + " is outside the server directory"));
                return 0;
            }
            FtbQuestsExport.Result r = write(server, target);
            Map<FtbQuestsExport.TaskType, Integer> n = new EnumMap<>(FtbQuestsExport.TaskType.class);
            for (FtbQuestsExport.TaskType t : r.types().values()) n.merge(t, 1, Integer::sum);
            String line = "FTB Quests export: " + r.types().size() + " quests (" + n.getOrDefault(FtbQuestsExport.TaskType.ITEM, 0) + " item, "
                    + n.getOrDefault(FtbQuestsExport.TaskType.ADVANCEMENT, 0) + " advancement, "
                    + n.getOrDefault(FtbQuestsExport.TaskType.CHECKMARK, 0) + " checkmark) in " + target;
            ctx.getSource().sendSuccess(() -> Component.literal(line), true);
            return r.types().size();
        } catch (IOException | IllegalStateException e) {
            ctx.getSource().sendFailure(Component.literal("FTB Quests export failed: " + e.getMessage()));
            return 0;
        }
    }

    /**
     * The real path of {@code p}: symbolic links and junctions of its nearest existing ancestor resolved, the missing rest
     * appended after normalising, so {@code ..} or a link cannot lead out of the server directory.
     */
    static Path real(Path p) throws IOException {
        Path abs = p.toAbsolutePath().normalize();
        Path existing = abs;
        while (existing != null && !Files.exists(existing)) existing = existing.getParent();
        if (existing == null) return abs;
        return existing.toRealPath().resolve(existing.relativize(abs)).normalize();
    }

    /** Export the loaded route into {@code target}; refuses a folder whose {@code data.snbt} is another quest book. */
    public static FtbQuestsExport.Result write(MinecraftServer server, Path target) throws IOException {
        FtbQuestsExport.Result r = FtbQuestsExport.export(HandbookManager.data().route(), id -> advancement(server, id));
        Path data = target.resolve(FtbQuestsExport.DATA_FILE);
        if (Files.exists(data) && !Files.readString(data, StandardCharsets.UTF_8).contains(FtbQuestsExport.hex("file/promptcritical")))
            throw new IllegalStateException(target + " already holds another quest book; export to an empty folder and merge it in the FTB Quests editor");
        for (Map.Entry<String, String> f : r.files().entrySet()) {
            Path p = target.resolve(f.getKey());
            Files.createDirectories(p.getParent());
            Files.writeString(p, f.getValue(), StandardCharsets.UTF_8);
        }
        return r;
    }

    private static ResourceLocation advancement(MinecraftServer server, ResourceLocation id) {
        return server.getAdvancements().get(id) == null ? null : id;
    }
}
