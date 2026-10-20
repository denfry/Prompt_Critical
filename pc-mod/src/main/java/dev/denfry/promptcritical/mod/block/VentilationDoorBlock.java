package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.mine.HoistText;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

/**
 * {@code ventilation_door} (08 §8.2.7): глухая дверь или регулятор. Рука открывает и закрывает створку двери; у
 * регулятора рука переставляет окно по ступеням {@link #WINDOW_FRACTIONS} (с Shift — назад). Сопротивление и утечки
 * считает шахта по состоянию блока. Регулятор ставится своим предметом {@code ventilation_regulator}.
 */
public class VentilationDoorBlock extends PcBlock {
    /** Окно регулятора по ступеням {@link PcProperties#WINDOW}, доля сечения проёма. */
    public static final double[] WINDOW_FRACTIONS = {0.10, 0.25, 0.50, 1.00};

    protected VentilationDoorBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static VentilationDoorBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new VentilationDoorBlock(props, spec));
    }

    public static double windowFraction(BlockState state) {
        return WINDOW_FRACTIONS[state.getValue(PcProperties.WINDOW)];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isController(state)) return InteractionResult.PASS;
        if (state.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR) {
            int n = WINDOW_FRACTIONS.length;
            int w = Math.floorMod(state.getValue(PcProperties.WINDOW) + (player.isShiftKeyDown() ? -1 : 1), n);
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(PcProperties.WINDOW, w), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.8f, 0.6f + 0.2f * w);
                player.displayClientMessage(HoistText.msg("regulator.window", Math.round(WINDOW_FRACTIONS[w] * 100)), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        boolean open = !state.getValue(BlockStateProperties.OPEN);
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return state.getValue(PcProperties.DOOR_KIND) == PcProperties.DoorKind.REGULATOR
                ? new ItemStack(PcItems.VENTILATION_REGULATOR.get()) : super.getCloneItemStack(level, pos, state);
    }
}
