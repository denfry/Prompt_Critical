package dev.denfry.promptcritical.mod.reactor;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.sim.reactor.PileUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
 * Кнопка аварийной защиты в зале {@code scram_button}: нажатие рукой или фронт редстоун-сигнала сбрасывает
 * аварийные стержни ближайшего котла (до {@link Reactor#SCRAM_REACH} блоков). Защита не зависит от автоматики:
 * кнопку можно завести на свою схему, но отменить сброс схема не может.
 */
public final class ScramButtonBlock extends PcBlock {
    private ScramButtonBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static ScramButtonBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new ScramButtonBlock(props, spec));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!press((ServerLevel) level, pos)) {
            player.displayClientMessage(ReactorPanels.MSG_NO_PILE.get(Reactor.SCRAM_REACH), true);
        } else {
            player.displayClientMessage(ReactorPanels.MSG_SCRAM.get(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (level.isClientSide || !state.hasProperty(BlockStateProperties.POWERED)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(BlockStateProperties.POWERED)) return;
        level.setBlock(pos, state.setValue(BlockStateProperties.POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered) press((ServerLevel) level, pos); // фронт сигнала
    }

    /** Сбросить аварийные стержни ближайшего котла; {@code false} — пульта рядом нет. */
    static boolean press(ServerLevel level, BlockPos pos) {
        PileControllerBlockEntity c = Reactor.nearestController(level, pos, Reactor.SCRAM_REACH);
        PileUnit u = c == null ? null : c.unit();
        if (u == null) return false;
        u.scram();
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0f, 0.6f);
        return true;
    }
}
