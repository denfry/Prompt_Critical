package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * BE комбайна: устройство мира симуляции и носитель BER шнеков (и на месте, и в контрапции). У комбайна реального
 * масштаба ({@code longwall_shearer_real}) BER рисует ещё и цельную модель.
 */
public final class LongwallShearerBlockEntity extends DeviceBlockEntity implements WholeModelHost {
    public LongwallShearerBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.LONGWALL_SHEARER.get(), pos, state);
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    /** Свет — с клетки забоя перед контроллером: сам контроллер в ряду шнеков. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing());
    }
}
