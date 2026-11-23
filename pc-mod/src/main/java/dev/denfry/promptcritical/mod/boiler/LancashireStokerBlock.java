package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Механические забрасыватели ланкаширского котла {@code lancashire_stoker} (docs/research/lancashire_stoker-dimensions.md):
 * рама 3×3×2 перед лобовой стенкой котла, вал Create входит в западную грань клетки (−1, 0, −1) модели, линия вала
 * идёт поперёк фронта котла.
 */
public final class LancashireStokerBlock extends FoundryBlock {
    /** Клетка вала Create в модели (фасад на север). */
    public static final BlockPos PORT = new BlockPos(-1, 0, -1);

    private LancashireStokerBlock(Properties props, Spec spec) {
        super(props, spec, LancashireStokerBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new LancashireStokerBlock(props, spec));
    }

    /** Ось вала в мире: поперёк фасада. */
    public static Direction.Axis shaftAxis(Direction facing) {
        return facing.getClockWise().getAxis();
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку вала ищем по сегменту и смещению от его якоря: они не зависят от места постройки
        BlockPos at = PORT.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), shaftAxis(f));
                break;
            }
        return super.partState(controller, cell);
    }
}
