package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code mine_dispatch_cabinet} (08 §8.2.5): диспетчерская шахты. Показывает сводку по газам и проветриванию;
 * {@code alarm} — метан выше уставки АГЗ где-либо в выработках. Пульты и CC — 03c, 03b.
 */
public class MineDispatchCabinetBlock extends PcDeviceBlock {
    protected MineDispatchCabinetBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MineDispatchCabinetBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MineDispatchCabinetBlock(props, spec));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            SimLevelBinding b = SimBindings.get((ServerLevel) level);
            if (b != null) b.mines().report(pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
