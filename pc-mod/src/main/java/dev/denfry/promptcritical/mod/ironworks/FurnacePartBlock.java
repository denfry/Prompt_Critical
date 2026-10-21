package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Кожух, фурма или лётка домны ({@code blast_furnace_shell}, {@code blast_furnace_tuyere},
 * {@code blast_furnace_taphole}): клик открывает щит контроллера печи; прогоревшую фурму меняют запасной
 * ({@code tuyere_spare}).
 */
public final class FurnacePartBlock extends PcBlock {
    private FurnacePartBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static FurnacePartBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new FurnacePartBlock(props, spec));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PcItems.TUYERE_SPARE.get()) || !state.hasProperty(PcProperties.BURNT) || !state.getValue(PcProperties.BURNT))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        BlastFurnaceControllerBlockEntity c = BlastFurnaceControllerBlockEntity.find(level, pos, s -> s.tuyeres().contains(pos));
        if (c == null || !c.replaceTuyere(pos)) return ItemInteractionResult.FAIL;
        if (!player.isCreative()) stack.shrink(1);
        player.displayClientMessage(IronworksText.msg("bf_tuyere_replaced"), true);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlastFurnaceControllerBlockEntity c = BlastFurnaceControllerBlockEntity.find(level, pos, s -> s.contains(pos));
        if (c == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && !player.isShiftKeyDown()) {
            PanelHost.open(sp, c);
            return InteractionResult.CONSUME;
        }
        return c.use(player);
    }
}
