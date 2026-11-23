package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * {@code shaft_signal} — стволовой сигнальный аппарат посадки (08 §8.2.8), см. {@link ShaftSignalBlockEntity}. Пустая
 * рука — звонок машинисту (вызов клети к посадке), как кнопка вызова у ствола.
 */
public class ShaftSignalBlock extends PcBlock implements EntityBlock {
    protected ShaftSignalBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static ShaftSignalBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new ShaftSignalBlock(props, spec));
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShaftSignalBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != MineBlockEntities.SHAFT_SIGNAL.get() ? null
                : (l, p, s, be) -> ShaftSignalBlockEntity.tick((ShaftSignalBlockEntity) be);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof ShaftSignalBlockEntity be)) return InteractionResult.PASS;
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 0.6f, 1.4f);
        MineHoistBlockEntity h = be.hoist();
        if (h == null || be.landing() < 0) {
            player.displayClientMessage(Component.translatable("message.promptcritical.shaft_signal.unlinked"), true);
            return InteractionResult.CONSUME;
        }
        MineHoistBlockEntity.CallResult r = h.call(be.landing(), player);
        player.displayClientMessage(Component.translatable("message.promptcritical.shaft_signal."
                + r.name().toLowerCase(java.util.Locale.ROOT), be.landing() + 1), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ShaftSignalBlockEntity be) be.onInput(level.hasNeighborSignal(pos));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ShaftSignalBlockEntity be ? be.comparator() : 0;
    }
}
