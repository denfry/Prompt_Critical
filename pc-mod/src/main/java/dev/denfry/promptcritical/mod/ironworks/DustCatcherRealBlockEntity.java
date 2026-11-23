package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Пылеуловитель реального масштаба {@code dust_catcher_real} (08 §8.5.3, art/blockbench/ironmaking/dust_catcher): клёпаный
 * корпус ⌀2,9 м на четырёх колоннах, 3 × 10 × 3 блока. Логика старого пылеуловителя; очищенный газ — из патрубка
 * газопровода на юге клетки (0, 8, 1).
 */
public final class DustCatcherRealBlockEntity extends DustCatcherBlockEntity implements WholeModelHost {
    public DustCatcherRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.DUST_CATCHER_REAL.get(), pos, state);
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    /** Свет — с клетки перед фасадом вне корпуса: сам контроллер внутри глухого объёма. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing(), 2);
    }
}
