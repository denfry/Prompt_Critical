package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Машины литейного двора реального масштаба (08 §8.5.5, art/blockbench/ironmaking/cast_house_machine): пушка
 * {@code cast_house_mud_gun_real} и бурмашина {@code cast_house_drill_real} на поворотной колонне, 3 × 3 × 3 блока.
 * Логика старой машины, вид — по блоку; привод — от сети, как у старой.
 */
public final class CastHouseMachineRealBlockEntity extends CastHouseMachineBlockEntity implements WholeModelHost {
    public CastHouseMachineRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.CAST_HOUSE_MACHINE_REAL.get(), pos, state);
    }

    @Override
    public PcProperties.CastHouseKind kind() {
        return getBlockState().is(PcBlocks.CAST_HOUSE_MUD_GUN_REAL.get()) ? PcProperties.CastHouseKind.MUD_GUN : PcProperties.CastHouseKind.DRILL;
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
