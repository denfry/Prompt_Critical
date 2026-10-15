package dev.denfry.promptcritical.mod.mine;

import com.simibubi.create.api.contraption.ContraptionType;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.contraptions.TranslatingContraption;
import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Клеть в стволе (08 §8.2.8) — контрапция Create из клеток {@code mine_cage}: якорь — контроллер клети, позицию
 * задаёт подъёмная машина ({@link MineHoistBlockEntity}). Снятие и постановка клеток идут под
 * {@link PcBlock#relocating}: многоклеточный блок не разрушается и не достраивается заново.
 */
public final class CageContraption extends TranslatingContraption {
    @Override
    public boolean assemble(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof MineCageBlock cage) || !cage.isController(s)) return false;
        anchor = pos;
        bounds = new AABB(BlockPos.ZERO);
        for (Footprint.Cell c : cage.spec().footprint().cells(pos, cage.facing(s))) addBlock(level, c.pos(), capture(level, c.pos()));
        startMoving(level);
        return true;
    }

    @Override
    public void removeBlocksFromWorld(Level level, BlockPos offset) {
        PcBlock.relocating(() -> super.removeBlocksFromWorld(level, offset));
    }

    @Override
    public void addBlocksToWorld(Level level, StructureTransform transform) {
        PcBlock.relocating(() -> super.addBlocksToWorld(level, transform));
    }

    @Override
    public ContraptionType getType() {
        return MineHoisting.CAGE_CONTRAPTION.get();
    }

    @Override
    public boolean canBeStabilized(Direction facing, BlockPos localPos) {
        return false;
    }
}
