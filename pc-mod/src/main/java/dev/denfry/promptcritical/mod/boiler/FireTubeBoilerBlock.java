package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Жаротрубный котёл 3×3×6 (07 §7.9.4): BE на контроллере, руки игрока с любой клетки — к нему. */
public final class FireTubeBoilerBlock extends PcDeviceBlock {
    private FireTubeBoilerBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static FireTubeBoilerBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new FireTubeBoilerBlock(props, spec));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new FireTubeBoilerBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isController(state) ? (l, p, s, be) -> {
            if (be instanceof FireTubeBoilerBlockEntity b) b.tick();
        } : null;
    }

    @Nullable
    private FireTubeBoilerBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(state, pos)) instanceof FireTubeBoilerBlockEntity be ? be : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        FireTubeBoilerBlockEntity be = controller(level, pos, state);
        if (be == null || !FireTubeBoilerBlockEntity.accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return be.useItem(player, hand, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        FireTubeBoilerBlockEntity be = controller(level, pos, state);
        if (be == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return be.use(player);
        if (player instanceof ServerPlayer sp) PanelHost.open(sp, be);
        return InteractionResult.CONSUME;
    }
}
