package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.block.PcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/** Personnel hatch in the containment wall: an open hatch breaks the containment boundary. */
public final class ContainmentHatchBlock extends PcBlock {
    private ContainmentHatchBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static ContainmentHatchBlock create(Properties props) {
        Spec spec = Spec.of(BlockStateProperties.OPEN);
        return construct(spec, () -> new ContainmentHatchBlock(props, spec));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), 3);
            level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.6f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
