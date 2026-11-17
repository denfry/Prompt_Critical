package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Секция вентиляторной градирни 3×3×3: охлаждает воду конденсатора, который её касается
 * ({@link dev.denfry.promptcritical.sim.thermal.CoolingTowerCell}). Правый клик — вентилятор вкл/выкл.
 */
public class CoolingTowerBlock extends PcBlock {
    protected CoolingTowerBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static CoolingTowerBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new CoolingTowerBlock(props, spec));
    }

    /** Контроллер секции, которой принадлежит клетка {@code pos}; {@code null} — это не градирня. */
    @Nullable
    public static BlockPos controller(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        BlockPos anchor = s.getBlock() instanceof MultiblockPartBlock ? MultiblockPartBlock.anchor(s, pos) : pos;
        if (!level.getBlockState(anchor).is(PcBlocks.COOLING_TOWER_CELL.get())) return null;
        BlockPos c = PcBlock.structureController(level, anchor);
        return c != null ? c : anchor;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean on = !state.getValue(PcProperties.RUNNING);
            level.setBlock(pos, state.setValue(PcProperties.RUNNING, on), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8f, on ? 0.9f : 0.6f);
            player.displayClientMessage(PowerhouseText.msg(on ? "cooling_tower.fan_on" : "cooling_tower.fan_off"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
