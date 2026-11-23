package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Агломашина Дуайта — Ллойда реального масштаба {@code sinter_strand_real} (08 §8.5.1,
 * art/blockbench/ironmaking/sinter_strand): лента 1,07 м, 8 м между звёздочками, 3 × 3 × 9 блоков. Логика и юнит
 * старой агломашины; шихта — в бункер сверху, газ зажигания — с востока, агломерат — с разгрузочного конца на юге,
 * вал Create — в клетку (−1, 0, 8) с запада ({@link RealShaftBlock}).
 */
public final class SinterStrandRealBlockEntity extends SinterStrandBlockEntity implements WholeModelHost {
    public SinterStrandRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.SINTER_STRAND_REAL.get(), pos, state);
    }

    /** Порт вала в модели. */
    public static final BlockPos SHAFT_PORT = new BlockPos(-1, 0, 8);

    /** Дым из-под горна зажигания, пока агломашина спекает шихту. */
    @Override
    protected void clientTick() {
        if (!stateFlag(BlockStateProperties.LIT)) return;
        MachineFx fx = fx();
        if (fx != null && fx.chance(2)) fx.smoke(fx.at(8, 44, 32), false, 1.0);
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
        return worldPosition.relative(facing(), 1);
    }
}
