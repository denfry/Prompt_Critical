package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Буровой насос 3×2×2: контроллер в середине, вал Create подводят к клетке {@code kinetic_port} на приводном конце
 * (клетка (−1, 0, 1) модели, ось коленчатого вала — вдоль фасада). Пустая рука — местный щит.
 */
public final class MudPumpBlock extends FoundryBlock {
    /** Клетка входа вала в координатах модели. */
    public static final BlockPos PORT = new BlockPos(-1, 0, 1);

    private MudPumpBlock(Properties props, Spec spec) {
        super(props, spec, MudPumpBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MudPumpBlock(props, spec));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.offsetFromAnchor().equals(PORT.rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
        return super.partState(controller, cell);
    }
}
