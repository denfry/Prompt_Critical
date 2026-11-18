package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Станок-качалка 3×1×3 (редуктор с кривошипами, балансир, шатуны, головка): контроллер в середине, вал Create —
 * к клетке {@code kinetic_port} на быстроходном валу редуктора, клетка (−1, 0, 0) модели, ось вдоль фасада. От
 * электродвигателя сети его крутят тем же валом ({@code electric_motor}).
 */
public final class PumpjackBlock extends FoundryBlock {
    public static final BlockPos PORT = new BlockPos(-1, 0, 0);

    private PumpjackBlock(Properties props, Spec spec) {
        super(props, spec, PumpjackBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new PumpjackBlock(props, spec));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.offsetFromAnchor().equals(PORT.rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
        return super.partState(controller, cell);
    }
}
