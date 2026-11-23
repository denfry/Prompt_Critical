package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Чугуновозный ковш реального масштаба {@code ladle_real} (08 §8.5.5, art/blockbench/ironmaking/ladle): клёпаный ковш
 * на 20 т на цапфах в стойке с червячным механизмом наклона, 3 × 3 × 3 блока. Логика старого ковша (жёлоб домны,
 * конвертер, разливка Shift + ПКМ); стоит на месте — контрапции его не возят.
 */
public final class LadleRealBlockEntity extends LadleBlockEntity implements WholeModelHost {
    public LadleRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.LADLE_REAL.get(), pos, state);
    }

    /** Пар и искры над горловиной, пока в ковше горячий металл; носок — px модели. */
    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.HOT)) return;
        MachineFx fx = fx();
        if (fx != null && fx.chance(1)) fx.smoke(fx.at(8, 46, 0), false, 0.6);
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
