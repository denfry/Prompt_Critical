package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Агломашина 3×2×3: контроллер в середине, вал Create входит в клетку-порт {@code kinetic_port} слева внизу
 * ({@link #PORT}), ось — поперёк фасада.
 */
public final class SinterStrandBlock extends FoundryBlock {
    /** Порт вала в модельных координатах (фасад на север). */
    public static final BlockPos PORT = new BlockPos(-1, 0, 0);

    private SinterStrandBlock(Properties props, Spec spec) {
        super(props, spec, SinterStrandBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new SinterStrandBlock(props, spec));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.segment() == 0 && cell.offsetFromAnchor().equals(PORT.rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
        return super.partState(controller, cell);
    }
}
