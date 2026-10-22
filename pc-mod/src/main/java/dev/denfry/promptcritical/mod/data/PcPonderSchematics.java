package dev.denfry.promptcritical.mod.data;

import com.google.common.hash.Hashing;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.content.PonderLayouts;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Схемы сцен Ponder ({@link PonderLayouts}) в формате структуры: {@code assets/promptcritical/ponder/<сцена>.nbt}.
 * Плита — шахматка из белого и светло-серого бетона на y = 0.
 */
public final class PcPonderSchematics implements DataProvider {
    private final PackOutput output;

    public PcPonderSchematics(PackOutput output) {
        this.output = output;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> all = new ArrayList<>();
        for (PonderLayouts.Layout l : PonderLayouts.LAYOUTS.values()) {
            try {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed(structure(l), bytes);
                byte[] data = bytes.toByteArray();
                cache.writeIfNeeded(output.getOutputFolder(PackOutput.Target.RESOURCE_PACK).resolve(PromptCritical.MOD_ID)
                        .resolve("ponder").resolve(l.scene() + ".nbt"), data, Hashing.sha1().hashBytes(data));
            } catch (IOException e) {
                all.add(CompletableFuture.failedFuture(e));
            }
        }
        return CompletableFuture.allOf(all.toArray(new CompletableFuture[0]));
    }

    static CompoundTag structure(PonderLayouts.Layout l) {
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < l.size(); x++)
            for (int z = 0; z < l.size(); z++)
                blocks.put(new BlockPos(x, 0, z), ((x + z) % 2 == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState());
        l.blocks().forEach((p, s) -> blocks.put(p, parse(l.scene(), s)));
        List<BlockState> palette = new ArrayList<>();
        ListTag blockList = new ListTag();
        blocks.forEach((p, s) -> {
            int i = palette.indexOf(s);
            if (i < 0) {
                i = palette.size();
                palette.add(s);
            }
            CompoundTag b = new CompoundTag();
            b.put("pos", ints(p.getX(), p.getY(), p.getZ()));
            b.putInt("state", i);
            blockList.add(b);
        });
        ListTag paletteList = new ListTag();
        for (BlockState s : palette) paletteList.add(NbtUtils.writeBlockState(s));
        CompoundTag root = new CompoundTag();
        root.put("size", ints(l.size(), l.height(), l.size()));
        root.put("palette", paletteList);
        root.put("blocks", blockList);
        root.put("entities", new ListTag());
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return root;
    }

    private static BlockState parse(String scene, String state) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), state, false).blockState();
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("ponder " + scene + ": " + state, e);
        }
    }

    private static ListTag ints(int... v) {
        ListTag t = new ListTag();
        for (int i : v) t.add(IntTag.valueOf(i));
        return t;
    }

    @Override
    public String getName() {
        return "Ponder schematics";
    }
}
