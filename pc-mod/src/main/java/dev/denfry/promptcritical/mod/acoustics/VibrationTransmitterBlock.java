package dev.denfry.promptcritical.mod.acoustics;

import dev.denfry.promptcritical.mod.block.PcBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Вибродатчик ({@code instrument_vibration}, 04a §4a.8.3): пьезоакселерометр с интегратором в скорость на магнитной
 * площадке, ставится на грань машины Create. Раз в секунду меряет общий уровень виброскорости машины и выдаёт его
 * аналоговым сигналом редстоуна: 0 — вал стоит, 15 — граница зон C/D класса машины и выше (как токовый выход 4–20 мА,
 * отнесённый к уставке отключения). Сигнал идёт во все стороны — на щит, компаратор, программируемый контроллер.
 */
public final class VibrationTransmitterBlock extends PcBlock implements EntityBlock {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // модель: площадка и корпус у южной грани (фасад на север), 5…11 × 0…11 × 11…16 px
        SHAPES.put(Direction.NORTH, Shapes.box(5 / 16.0, 0, 11 / 16.0, 11 / 16.0, 11 / 16.0, 1));
        SHAPES.put(Direction.SOUTH, Shapes.box(5 / 16.0, 0, 0, 11 / 16.0, 11 / 16.0, 5 / 16.0));
        SHAPES.put(Direction.EAST, Shapes.box(0, 0, 5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0));
        SHAPES.put(Direction.WEST, Shapes.box(11 / 16.0, 0, 5 / 16.0, 1, 11 / 16.0, 11 / 16.0));
    }

    private VibrationTransmitterBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static VibrationTransmitterBlock create(Properties props) {
        Spec spec = Spec.facing();
        return construct(spec, () -> new VibrationTransmitterBlock(props, spec));
    }

    /** Машина, на которой стоит датчик. */
    public static BlockPos mountPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction face = ctx.getClickedFace();
        Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPES.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof VibrationTransmitterBlockEntity t ? t.signal() : 0;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VibrationTransmitterBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (l, p, s, be) -> {
            if (be instanceof VibrationTransmitterBlockEntity t) t.tick();
        };
    }
}
