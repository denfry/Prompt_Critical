package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Клетка с канарейкой (08 §8.2.5, 11): птицу сажают предметом {@code canary}, забирают пустой рукой. Отравление
 * птицы ведёт шахта ({@code MineWorld}); упавшую, но живую птицу можно вынести и откачать (11, {@code canary_saved}).
 */
public class CanaryCageBlock extends PcBlock {
    protected CanaryCageBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static CanaryCageBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new CanaryCageBlock(props, spec));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(PcItems.CANARY.get()) || state.getValue(PcProperties.BIRD) != PcProperties.Bird.EMPTY)
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            stack.consume(1, player);
            level.setBlock(pos, state.setValue(PcProperties.BIRD, PcProperties.Bird.ALIVE), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.PARROT_AMBIENT, SoundSource.BLOCKS, 1.0f, 1.6f);
            SimLevelBinding b = SimBindings.get((ServerLevel) level);
            if (b != null) {
                b.mines().onCanaryPlaced(pos);
                if (player instanceof ServerPlayer sp && b.mines().inMine(pos))
                    SimAchievements.direct(sp, SimAchievements.CANARY_PLACED, "", 0);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PcProperties.BIRD) == PcProperties.Bird.EMPTY) return InteractionResult.PASS;
        if (!level.isClientSide) {
            SimLevelBinding b = SimBindings.get((ServerLevel) level);
            boolean alive = b == null || b.mines().takeCanary(pos);
            level.setBlock(pos, state.setValue(PcProperties.BIRD, PcProperties.Bird.EMPTY), Block.UPDATE_ALL);
            if (alive && !player.addItem(new ItemStack(PcItems.CANARY.get()))) player.drop(new ItemStack(PcItems.CANARY.get()), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
