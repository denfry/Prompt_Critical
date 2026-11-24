package dev.denfry.promptcritical.mod.multiblock;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Перевод миров 1.0.1: у машин реального масштаба, построенных до клеток-патрубков, на месте патрубка стоит обычная
 * {@code multiblock_part} без BE, и труба Create к ней не стыкуется. Контроллер такой машины при загрузке чанка
 * встаёт в очередь; на тике мира, когда загружены все клетки машины, обычные части на клетках патрубков меняются на
 * {@link PortCellBlock} ({@link PcBlock#migratePortCells}). Один раз за загрузку: у перестроенной машины менять нечего.
 */
public final class PortCellMigration {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new ConcurrentHashMap<>();

    private PortCellMigration() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ChunkEvent.Load e) -> {
            if (e.getLevel() instanceof ServerLevel s) queue(s, e.getChunk());
        });
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel s) drain(s);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> PENDING.clear());
    }

    /** Контроллеры машин реального масштаба с объявленными патрубками в загруженном чанке. */
    private static void queue(ServerLevel level, ChunkAccess chunk) {
        for (BlockPos p : chunk.getBlockEntitiesPos()) {
            BlockState s = chunk.getBlockState(p);
            if (!(s.getBlock() instanceof PcBlock pc) || !pc.isController(s) || !PcBlocks.isWholeModel(pc)) continue;
            if (WholePorts.of(BuiltInRegistries.BLOCK.getKey(pc).getPath()).isEmpty()) continue;
            PENDING.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(p.immutable());
        }
    }

    private static void drain(ServerLevel level) {
        Set<BlockPos> q = PENDING.get(level.dimension());
        if (q == null || q.isEmpty()) return;
        for (Iterator<BlockPos> it = q.iterator(); it.hasNext(); ) {
            // −1 — часть клеток машины в незагруженном чанке: подождать; контроллер выгружен — снова встанет при загрузке
            if (PcBlock.migratePortCells(level, it.next()) >= 0) it.remove();
        }
    }
}
