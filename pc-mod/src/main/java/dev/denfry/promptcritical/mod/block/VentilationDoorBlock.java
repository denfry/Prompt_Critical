package dev.denfry.promptcritical.mod.block;

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
 * {@code ventilation_door} (08 §8.2.7): глухая дверь или регулятор. Рука открывает и закрывает створку; у регулятора
 * «закрыто» — окно в четверть сечения. Сопротивление и утечки считает шахта по состоянию {@code open}.
 */
public class VentilationDoorBlock extends PcBlock {
    protected VentilationDoorBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static VentilationDoorBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new VentilationDoorBlock(props, spec));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isController(state)) return InteractionResult.PASS;
        boolean open = !state.getValue(BlockStateProperties.OPEN);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
