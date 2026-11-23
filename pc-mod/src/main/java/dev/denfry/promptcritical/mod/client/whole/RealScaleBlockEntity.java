package dev.denfry.promptcritical.mod.client.whole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * BE, который служит и машине старой серии, и её двойнику реального масштаба (цельная модель): id модели — по блоку,
 * фасад — из состояния (без фасада — север), свет — с клетки перед фасадом. Реализуют наследники BlockEntity.
 */
public interface RealScaleBlockEntity extends WholeModelHost {
    BlockState getBlockState();

    BlockPos getBlockPos();

    /** Стоит блок реального масштаба (цельная модель), а не старый. */
    default boolean real() {
        return wholeModelId() != null;
    }

    @Override
    @Nullable
    default String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    default Direction wholeModelFacing() {
        BlockState s = getBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    @Override
    default BlockPos wholeModelLightPos() {
        return getBlockPos().relative(wholeModelFacing());
    }

    /** Тип устройства в симуляции: у блока реального масштаба — тип старого блока (id без {@code _real}). */
    static String simType(BlockState state, String blockId) {
        return WholeModelHost.idOf(state) != null && blockId.endsWith("_real") ? blockId.substring(0, blockId.length() - 5) : blockId;
    }
}
