package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Пюпитр для документов (11 §11.1.2): на него кладут инструкцию по эксплуатации (или справочник), и любой игрок рядом
 * читает её, не забирая. Пустая рука — читать, присесть с пустой рукой — забрать.
 */
public class DocumentLecternBlock extends PcBlock implements EntityBlock {
    protected DocumentLecternBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static DocumentLecternBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new DocumentLecternBlock(props, spec));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DocumentLecternBlockEntity(pos, state);
    }

    public static boolean isDocument(ItemStack stack) {
        return stack.is(PcItems.OPERATING_PROCEDURE.get()) || stack.is(PcItems.ENGINEERING_HANDBOOK.get());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || state.getValue(PcProperties.HAS_DOCUMENT)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!isDocument(stack)) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(ContentText.LECTERN_ONLY_DOCUMENTS), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof DocumentLecternBlockEntity be) {
            be.setDocument(stack.consumeAndReturn(1, player));
            level.setBlock(pos, state.setValue(PcProperties.HAS_DOCUMENT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof DocumentLecternBlockEntity be) || be.document().isEmpty()) {
            player.displayClientMessage(Component.translatable(ContentText.LECTERN_EMPTY), true);
            return InteractionResult.CONSUME;
        }
        if (player.isSecondaryUseActive()) {
            ItemStack doc = be.takeDocument();
            if (!player.addItem(doc)) player.drop(doc, false);
            level.setBlock(pos, state.setValue(PcProperties.HAS_DOCUMENT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0f, 1.0f);
        } else if (player instanceof ServerPlayer sp) {
            ResourceLocation ref = be.document().get(PcDataComponents.PROCEDURE_REF.get());
            HandbookService.open(sp, ref, ref != null);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof DocumentLecternBlockEntity be && !be.document().isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.takeDocument());
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return state.getValue(PcProperties.HAS_DOCUMENT) ? 15 : 0;
    }

    @Nullable
    public static DocumentLecternBlockEntity at(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof DocumentLecternBlockEntity be ? be : null;
    }
}
