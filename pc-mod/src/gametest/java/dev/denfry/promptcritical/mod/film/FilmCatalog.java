package dev.denfry.promptcritical.mod.film;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Каталог блоков для съёмочной площадки (только dev): если в каталоге игры лежит {@code catalog.request}, после
 * запуска сервера пишет {@code catalog.json} — каждый блок топологии (машины, приборы, трубы) с занимаемыми клетками
 * при фасаде на север от контроллера в начале координат и со свойствами состояния. По нему {@code art/film} строит
 * завод из всех машин.
 */
@EventBusSubscriber(modid = PromptCritical.MOD_ID)
public final class FilmCatalog {
    private static final Logger LOG = LogUtils.getLogger();

    private FilmCatalog() {
    }

    record Entry(String id, boolean facing, List<int[]> cells, Map<String, List<String>> props) {
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent e) {
        Path request = Path.of("catalog.request");
        if (!Files.exists(request)) return;
        List<Entry> out = new ArrayList<>();
        for (Block b : PcBlocks.topologyRelevant()) {
            if (b == PcBlocks.MULTIBLOCK_PART.get() || b == PcBlocks.KINETIC_PORT.get()) continue;
            List<int[]> cells = new ArrayList<>();
            boolean facing = false;
            if (b instanceof PcBlock pc) {
                facing = pc.spec().hasFacing();
                for (Footprint.Cell c : pc.spec().footprint().cells(BlockPos.ZERO, Direction.NORTH))
                    cells.add(new int[] {c.pos().getX(), c.pos().getY(), c.pos().getZ()});
            } else {
                cells.add(new int[] {0, 0, 0});
            }
            Map<String, List<String>> props = new LinkedHashMap<>();
            for (Property<?> p : b.getStateDefinition().getProperties())
                props.put(p.getName(), p.getPossibleValues().stream().map(v -> name(p, v)).toList());
            out.add(new Entry(BuiltInRegistries.BLOCK.getKey(b).toString(), facing, cells, props));
        }
        try {
            Files.writeString(Path.of("catalog.json"), new GsonBuilder().setPrettyPrinting().create().toJson(out));
            Files.delete(request);
            LOG.info("[film] catalog: {} blocks", out.size());
        } catch (IOException ex) {
            LOG.error("[film] catalog not written", ex);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String name(Property p, Object v) {
        return p.getName((Comparable) v);
    }
}
