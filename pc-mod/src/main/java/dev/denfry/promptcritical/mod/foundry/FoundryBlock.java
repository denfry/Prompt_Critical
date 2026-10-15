package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
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

/**
 * Устройство литейки и цеха (08 §8.9–8.10): свой BE на контроллере, тик и руки игрока передаются ему ({@link
 * FoundryDevice}). Клик по любой клетке конструкции приходит на контроллер.
 */
public class FoundryBlock extends PcDeviceBlock {
    private final BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory;

    protected FoundryBlock(Properties props, Spec spec, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory) {
        super(props, spec);
        this.factory = factory;
    }

    /** Фабрика для {@code PcBlocks.device(name, props, spec, factory)}. */
    public static BiFunction<Properties, Spec, PcDeviceBlock> with(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new FoundryBlock(p, s, be));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? factory.apply(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isController(state) ? (l, p, s, be) -> {
            if (be instanceof FoundryDevice d) d.tickDevice();
        } : null;
    }

    @Nullable
    protected FoundryDevice device(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(state, pos)) instanceof FoundryDevice d ? d : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        FoundryDevice d = device(level, pos, state);
        if (d == null || !d.accepts(stack)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return d.useItem(player, hand, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        FoundryDevice d = device(level, pos, state);
        if (d == null) return InteractionResult.PASS;
        return level.isClientSide ? InteractionResult.SUCCESS : d.use(player);
    }
}
