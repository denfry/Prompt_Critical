package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Аккумуляторная установка ПВО 2×1×1 (09 §9.5.7): контроллер — левая клетка с пультом и рычагами, вал Create насоса
 * подзарядки — к клетке {@code kinetic_port} справа (клетка (1, 0, 0) модели, ось вдоль фасада). Пустая рука — пульт.
 */
public final class BopAccumulatorBlock extends FoundryBlock {
    public static final BlockPos PORT = new BlockPos(1, 0, 0);

    private BopAccumulatorBlock(Properties props, Spec spec) {
        super(props, spec, BopAccumulatorBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new BopAccumulatorBlock(props, spec));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.offsetFromAnchor().equals(PORT.rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
        return super.partState(controller, cell);
    }
}
