package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.block.PcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Затвор водозабора ГЭС: ставится в воду выше гидротурбины. Поднят — вода идёт в водовод; опущен — турбина
 * закрывает направляющий аппарат (с ограничением скорости — иначе гидроудар). Правый клик поднимает и опускает.
 */
public class DamGateBlock extends PcBlock {
    protected DamGateBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static DamGateBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new DamGateBlock(props, spec));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof net.minecraft.server.level.ServerLevel sl && !oldState.is(this))
            dev.denfry.promptcritical.mod.environment.RiverService.gatePlaced(sl, pos, true);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl && !newState.is(this))
            dev.denfry.promptcritical.mod.environment.RiverService.gatePlaced(sl, pos, false);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean open = !state.getValue(BlockStateProperties.OPEN);
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f);
            player.displayClientMessage(PowerhouseText.msg(open ? "dam_gate.open" : "dam_gate.closed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
