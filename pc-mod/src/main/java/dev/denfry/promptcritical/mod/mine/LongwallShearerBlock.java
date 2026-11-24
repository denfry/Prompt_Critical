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

    /**
     * Крайние клетки шнеков вдоль хода от контроллера, в модели (фасад на север, +x — на восток): {западный, восточный}.
     * У старого комбайна шнеки в соседних клетках; у реального масштаба центры шнеков ⌀1,6 м — в ±82 px от западной грани
     * контроллера, шнеки занимают клетки −6 и 5 (art/blockbench/mining/longwall_shearer_real.author.js).
     */
    public static int[] drumCells(BlockState s) {
        return s.is(dev.denfry.promptcritical.mod.registry.PcBlocks.LONGWALL_SHEARER_REAL.get()) ? new int[] {-6, 5} : new int[] {-1, 1};
    }

    /** Глубина корпуса от забоя, блоков: за ней на конвейер падает уголь (у реального масштаба забойный конвейер свой). */
    public static int depth(BlockState s) {
        return s.is(dev.denfry.promptcritical.mod.registry.PcBlocks.LONGWALL_SHEARER_REAL.get()) ? 2 : 1;
    }

    /** Ось хода вдоль лавы — поперёк фасада. */
    public static Direction.Axis travelAxis(Direction facing) {
        return facing.getClockWise().getAxis();
    }

    @Override
    @Nullable
    protected BlockEntity controllerEntity(BlockPos pos, BlockState state) {
        return new LongwallShearerBlockEntity(pos, state);
    }
}
