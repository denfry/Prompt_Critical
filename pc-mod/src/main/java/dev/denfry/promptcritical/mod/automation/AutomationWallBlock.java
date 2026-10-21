package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Настенный блок автоматики (реле на DIN-рейке, элементы пульта, газоанализатор, реле процесса): фасад от стены, модель
 * прижата к югу. У переходника реле → редстоун (свойство {@code powered}) — сильный сигнал в блок, на котором он
 * висит, как у рычага. Поведение — у BE.
 */
public final class AutomationWallBlock extends FoundryBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    private AutomationWallBlock(Properties props, Spec spec, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory,
            double[] box) {
        super(props, spec, factory);
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotated(d, box));
    }

    /** Коробка модели {@code box} (пиксели, фасад на север, стена на юге). */
    public static BiFunction<Properties, Spec, PcDeviceBlock> wall(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be,
            double... box) {
        return (p, s) -> construct(s, () -> new AutomationWallBlock(p, s, be, box));
    }

    /** Блок, на котором висит прибор. */
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
        return shapes.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    // --- редстоун переходника ---

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.hasProperty(BlockStateProperties.POWERED);
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED)
                && direction == state.getValue(BlockStateProperties.HORIZONTAL_FACING) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        boolean powered = state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED);
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (powered && !newState.is(this)) {
            level.updateNeighborsAt(pos, this);
            level.updateNeighborsAt(mountPos(state, pos), this);
        }
    }

    /** Коробка модели (фасад на север) повёрнутая к {@code facing}. */
    static VoxelShape rotated(Direction facing, double[] b) {
        double[] p = TerminalLayout.rotate(facing, b[0] - 8, b[2] - 8), q = TerminalLayout.rotate(facing, b[3] - 8, b[5] - 8);
        return Shapes.box((Math.min(p[0], q[0]) + 8) / 16, b[1] / 16, (Math.min(p[1], q[1]) + 8) / 16,
                (Math.max(p[0], q[0]) + 8) / 16, b[4] / 16, (Math.max(p[1], q[1]) + 8) / 16);
    }
}
