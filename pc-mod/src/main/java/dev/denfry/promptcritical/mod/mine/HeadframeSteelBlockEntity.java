package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Стальной копёр реального масштаба (docs/research/headframe_steel-dimensions.md): только контроллер цельной модели.
 * Ствол, посадки и шкив копра ищет подъёмная машина ({@link HoistGeometry}); шкив она же и рисует.
 */
public final class HeadframeSteelBlockEntity extends BlockEntity implements WholeModelHost {
    /** Ось шкива от центра клетки контроллера, блоков (модель с фасадом на север): над стволом, к машине на юг. */
    public static final double SHEAVE_UP = 11.5, SHEAVE_BACK = 1.5;
    /** Радиус шкива, блоков (⌀3,0 м). */
    public static final double SHEAVE_R = 1.5;

    public HeadframeSteelBlockEntity(BlockPos pos, BlockState state) {
        super(MineBlockEntities.HEADFRAME_STEEL.get(), pos, state);
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(wholeModelFacing().getOpposite(), 3);
    }
}
