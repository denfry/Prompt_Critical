package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** BE комбайна: устройство мира симуляции и носитель BER шнеков (и на месте, и в контрапции). */
public final class LongwallShearerBlockEntity extends DeviceBlockEntity {
    public LongwallShearerBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.LONGWALL_SHEARER.get(), pos, state);
    }
}
