package dev.denfry.promptcritical.mod.ops;

import com.simibubi.create.content.kinetics.transmission.ClutchBlock;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Расцепная муфта {@code mechanical_disconnect} (13 §13.1.2): вал Create насквозь, рычаг расцепляет передачу —
 * вращение «до» муфты продолжается, «после» — нет. Изолированное положение — «расцеплено» ({@link #POWERED} муфты
 * Create); точка принимает замок LOTO, и запертую муфту не сцепить. В отличие от муфты Create, редстоуном не
 * управляется: изоляция механической энергии — только рукой.
 */
public class MechanicalDisconnectBlock extends ClutchBlock {
    /** Висит замок LOTO (для модели). */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public MechanicalDisconnectBlock(Properties p) {
        super(p);
        registerDefaultState(defaultBlockState().setValue(POWERED, false).setValue(LOCKED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);
        b.add(LOCKED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(POWERED, false).setValue(LOCKED, false);
    }

    /** Редстоун муфту не трогает. */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (Lockouts.locked(level, pos)) {
            player.displayClientMessage(Lockouts.lockedMessage(level, pos), true);
            return InteractionResult.SUCCESS;
        }
        boolean disengage = !state.getValue(POWERED);
        detachKinetics(level, pos, true);
        level.setBlock(pos, state.setValue(POWERED, disengage), 2);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, disengage ? 0.6f : 0.8f);
        player.displayClientMessage(LockoutText.msg(disengage ? "disengaged" : "engaged"), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntityType<? extends SplitShaftBlockEntity> getBlockEntityType() {
        return OpsBlockEntities.MECHANICAL_DISCONNECT.get();
    }
}
