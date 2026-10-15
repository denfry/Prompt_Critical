package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Очистной комбайн {@code longwall_shearer} 3×1×1 (08 §8.2.3): фасад смотрит в забой, шнеки на концах корпуса, ход
 * — вдоль лавы. Сам по себе не режет: его везёт контрапция Create на {@code gantry}, а резание ведёт
 * {@link ShearerMovementBehaviour}.
 */
public final class LongwallShearerBlock extends PcDeviceBlock {
    private LongwallShearerBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static LongwallShearerBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new LongwallShearerBlock(props, spec));
    }

    /** Ось хода вдоль лавы — поперёк фасада. */
    public static Direction.Axis travelAxis(Direction facing) {
        return facing.getClockWise().getAxis();
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new LongwallShearerBlockEntity(pos, state) : null;
    }
}
