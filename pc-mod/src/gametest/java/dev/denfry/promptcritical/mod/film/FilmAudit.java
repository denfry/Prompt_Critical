package dev.denfry.promptcritical.mod.film;

import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Проверка мира-завода (только dev): {@code /film audit <имя>[+<имя>...]} берёт из файлов стройки каждую команду
 * {@code setblock} блока мода и сверяет мир: стоит ли там этот блок и цел ли его футпринт (каждая клетка — контроллер
 * часть мультиблока или кинетический порт, а не то, что поставили позже поверх). Если в одной клетке ставили несколько раз, проверяется
 * последняя команда. Находки — в {@code plant_audit.json} и коротко в чат.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmAudit {
    private static final Pattern SET = Pattern.compile("^setblock (-?\\d+) (-?\\d+) (-?\\d+) (promptcritical:[a-z0-9_]+)");
    private static final Pattern ANY = Pattern.compile("^(?:setblock|fill) (-?\\d+) (-?\\d+) (-?\\d+)(?: (-?\\d+) (-?\\d+) (-?\\d+))? ");

    private FilmAudit() {
    }

    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("film").requires(s -> s.hasPermission(2))
                .then(Commands.literal("audit").then(Commands.argument("files", StringArgumentType.word()).executes(FilmAudit::audit))));
    }

    private static int audit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        Map<BlockPos, String> expect = new LinkedHashMap<>();
        try {
            for (String name : StringArgumentType.getString(ctx, "files").split("\\+")) {
                for (String line : Files.readAllLines(Path.of(name + ".txt"))) {
                    Matcher m = SET.matcher(line);
                    if (m.find()) {
                        expect.put(new BlockPos(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))), m.group(4));
                    } else {
                        // a later plain setblock/fill over a controller replaces it: drop the expectation
                        Matcher a = ANY.matcher(line);
                        if (a.find() && a.group(4) == null) expect.remove(new BlockPos(Integer.parseInt(a.group(1)),
                                Integer.parseInt(a.group(2)), Integer.parseInt(a.group(3))));
                    }
                }
            }
        } catch (IOException ex) {
            src.sendFailure(Component.literal("[film] audit: " + ex.getMessage()));
            return 0;
        }
        List<Map<String, Object>> bad = new ArrayList<>();
        int ok = 0, multi = 0;
        for (Map.Entry<BlockPos, String> en : expect.entrySet()) {
            BlockPos p = en.getKey();
            level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, true);
            Block want = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(en.getValue()));
            BlockState have = level.getBlockState(p);
            String problem = null;
            if (!have.is(want)) {
                problem = "found " + BuiltInRegistries.BLOCK.getKey(have.getBlock());
            } else if (want instanceof PcBlock pc && pc.spec().footprint() != Footprint.SINGLE) {
                multi++;
                List<String> broken = new ArrayList<>();
                for (Footprint.Cell c : pc.spec().footprint().cells(p, pc.facing(have))) {
                    if (c.pos().equals(p)) continue;
                    BlockState s = level.getBlockState(c.pos());
                    if (!s.is(PcBlocks.MULTIBLOCK_PART.get()) && !s.is(PcBlocks.KINETIC_PORT.get()) && !s.is(want)) {
                        broken.add(c.pos().toShortString() + " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
                        if (broken.size() >= 4) break;
                    }
                }
                if (!broken.isEmpty()) problem = "footprint: " + String.join("; ", broken);
            }
            if (problem == null) {
                ok++;
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("block", en.getValue());
            m.put("pos", p.toShortString());
            m.put("problem", problem);
            bad.add(m);
        }
        try {
            Files.writeString(Path.of("plant_audit.json"), new GsonBuilder().setPrettyPrinting().create().toJson(bad));
        } catch (IOException ex) {
            PromptCritical.LOGGER.error("[film] audit not written", ex);
        }
        String msg = "[film] audit: " + expect.size() + " machine blocks, " + ok + " ok (" + multi + " multiblocks checked), "
                + bad.size() + " problems (plant_audit.json)";
        PromptCritical.LOGGER.info(msg);
        src.sendSuccess(() -> Component.literal(msg), false);
        return bad.size();
    }
}
