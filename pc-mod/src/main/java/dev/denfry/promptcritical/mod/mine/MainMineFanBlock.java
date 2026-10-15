package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Вентилятор главного проветривания 3×3×3 (08 §8.2.7): контроллер в центре, крыльчатка на оси фасада. Вал Create
 * подводят сзади к клетке {@code kinetic_port} на оси (клетка (0, 0, 1) модели).
 */
public final class MainMineFanBlock extends PcDeviceBlock {
    private MainMineFanBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MainMineFanBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MainMineFanBlock(props, spec));
    }

    public static BlockPos portPos(BlockPos controller, Direction facing) {
        return BoilerGeometry.offset(controller, facing, 0, 0, 1);
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.offsetFromAnchor().equals(new BlockPos(0, 0, 1).rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getAxis());
        return super.partState(controller, cell);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new MainMineFanBlockEntity(pos, state) : null;
    }
}
