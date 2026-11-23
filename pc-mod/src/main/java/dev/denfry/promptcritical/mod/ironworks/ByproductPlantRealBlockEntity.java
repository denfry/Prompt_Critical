package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Аппараты улавливания реального масштаба (08 §8.4.5, art/blockbench/coke/byproduct_plant): первичный холодильник,
 * смолоотделитель, сатуратор и скруббер — четыре блока на логике старой установки, вид — по блоку, а не по свойству.
 */
public final class ByproductPlantRealBlockEntity extends ByproductPlantBlockEntity implements WholeModelHost {
    public ByproductPlantRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.BYPRODUCT_PLANT_REAL.get(), pos, state);
    }

    @Override
    public PcProperties.ByproductKind kind() {
        Block b = getBlockState().getBlock();
        if (b == PcBlocks.BYPRODUCT_PLANT_PRECIPITATOR_REAL.get()) return PcProperties.ByproductKind.PRECIPITATOR;
        if (b == PcBlocks.BYPRODUCT_PLANT_SATURATOR_REAL.get()) return PcProperties.ByproductKind.SATURATOR;
        if (b == PcBlocks.BYPRODUCT_PLANT_SCRUBBER_REAL.get()) return PcProperties.ByproductKind.SCRUBBER;
        return PcProperties.ByproductKind.COOLER;
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
