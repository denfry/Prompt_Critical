package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Блоки электросети I1 (07 §7.22): к ним тянутся кабели и шины. */
public final class GridBlocks {
    private static Set<Block> blocks;

    private GridBlocks() {
    }

    private static Set<Block> blocks() {
        if (blocks == null) {
            blocks = Set.of(PcBlocks.SHAFT_GENERATOR.get(), PcBlocks.ELECTRIC_MOTOR.get(), PcBlocks.SWITCHGEAR_CELL.get(),
                    PcBlocks.LV_SWITCHBOARD.get(), PcBlocks.DISTRIBUTION_TRANSFORMER.get(), PcBlocks.DISCONNECTOR.get(),
                    PcBlocks.GRID_CONSUMER.get(), PcBlocks.LV_DISTRIBUTION_BOX.get(), PcBlocks.WIRE_CONNECTOR_LV.get(),
                    PcBlocks.WIRE_CONNECTOR_MV.get(), PcBlocks.RELAY_POWER_SUPPLY.get());
        }
        return blocks;
    }

    /** Клетка принадлежит устройству сети (контроллер, якорь сегмента или часть конструкции). */
    public static boolean isGridBlock(BlockGetter level, BlockPos pos, BlockState s) {
        if (blocks().contains(s.getBlock())) return true;
        if (s.getBlock() instanceof MultiblockPartBlock && level instanceof Level l) {
            BlockPos c = PcBlock.structureController(l, MultiblockPartBlock.anchor(s, pos));
            return c != null && blocks().contains(l.getBlockState(c).getBlock());
        }
        return false;
    }
}
