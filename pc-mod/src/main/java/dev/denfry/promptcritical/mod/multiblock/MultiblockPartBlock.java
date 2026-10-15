package dev.denfry.promptcritical.mod.multiblock;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Невидимая клетка многоклеточного блока. Свойство {@code cell} кодирует смещение от якоря своего сегмента
 * (dx, dy, dz ∈ −1…1): (dx+1)·9 + (dy+1)·3 + (dz+1). Модель сегмента рисует якорь; клетка даёт объём, проксирует
 * взаимодействие и при разрушении разрушает конструкцию.
 */
public class MultiblockPartBlock extends Block {
    public static final IntegerProperty CELL = IntegerProperty.create("cell", 0, 26);
    public static final com.mojang.serialization.MapCodec<MultiblockPartBlock> CODEC = simpleCodec(MultiblockPartBlock::new);

    public MultiblockPartBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(CELL, 13));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends Block> codec() {
        return CODEC;
    }

    public static BlockState forOffset(BlockPos offsetFromAnchor) {
        int idx = (offsetFromAnchor.getX() + 1) * 9 + (offsetFromAnchor.getY() + 1) * 3 + (offsetFromAnchor.getZ() + 1);
        return PcBlocks.MULTIBLOCK_PART.get().defaultBlockState().setValue(CELL, idx);
    }

    public static BlockPos anchor(BlockState state, BlockPos pos) {
        int idx = state.getValue(CELL);
        return pos.offset(-(idx / 9 - 1), -(idx / 3 % 3 - 1), -(idx % 3 - 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CELL);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos anchor = anchor(state, pos);
        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof PcBlock)) return InteractionResult.PASS;
        return anchorState.useWithoutItem(level, player, hit.withPosition(anchor));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        BlockPos anchor = anchor(state, pos);
        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof PcBlock)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return anchorState.useItemOn(stack, level, player, hand, hit.withPosition(anchor));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockState anchorState = level.getBlockState(anchor(state, pos));
        return anchorState.getBlock() instanceof PcBlock b ? new ItemStack(b) : ItemStack.EMPTY;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos controller = PcBlock.structureController(level, anchor(state, pos));
            if (controller != null) level.removeBlock(controller, false);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this)) {
            BlockPos controller = PcBlock.structureController(level, anchor(state, pos));
            if (controller != null) {
                BlockState cs = level.getBlockState(controller);
                if (cs.getBlock() instanceof PcBlock b && b.isController(cs)) level.destroyBlock(controller, true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
