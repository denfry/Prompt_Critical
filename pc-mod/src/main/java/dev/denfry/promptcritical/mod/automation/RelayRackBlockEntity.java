package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * {@code relay_rack} (03d §3d.3.1): монтажная рама на 16 реле — только корпус (тепло, радиационная тень, защита от
 * взрыва — в следующих вехах). Клик открывает и закрывает дверь.
 */
public class RelayRackBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    /** Клиент: угол двери для плавного рендера. */
    public float doorAngle = Float.NaN;

    public RelayRackBlockEntity(BlockPos pos, BlockState state) {
        super(AutomationBlockEntities.RELAY_RACK.get(), pos, state);
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult use(Player player) {
        BlockState s = getBlockState();
        boolean open = !s.getValue(BlockStateProperties.OPEN);
        level.setBlock(worldPosition, s.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.6f, 1.1f);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void tickDevice() {
    }
}
