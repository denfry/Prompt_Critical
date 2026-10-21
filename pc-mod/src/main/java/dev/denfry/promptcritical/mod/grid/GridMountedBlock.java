package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
 * Малое устройство сети на грани блока (07 §7.4.2, §7.4.6): настенное ({@code wire_connector_lv},
 * {@code lv_distribution_box}) — фасад от стены, модель прижата к югу; напольное ({@code wire_connector_mv}) — на
 * верхней грани опоры или блока. Поведение — у BE ({@link FoundryBlock}).
 */
public final class GridMountedBlock extends FoundryBlock {
    private final boolean wall;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final VoxelShape floorShape;

    private GridMountedBlock(Properties props, Spec spec, BiFunction<BlockPos, BlockState, ? extends BlockEntity> factory,
            boolean wall, double[] box) {
        super(props, spec, factory);
        this.wall = wall;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes.put(d, rotated(d, box));
        this.floorShape = Shapes.box(box[0] / 16, box[1] / 16, box[2] / 16, box[3] / 16, box[4] / 16, box[5] / 16);
    }

    /** Настенное устройство: коробка модели {@code box} (пиксели, фасад на север, стена на юге). */
    public static BiFunction<Properties, Spec, PcDeviceBlock> wall(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be,
            double... box) {
        return (p, s) -> construct(s, () -> new GridMountedBlock(p, s, be, true, box));
    }

    /** Напольное устройство (без фасада). */
    public static BiFunction<Properties, Spec, PcDeviceBlock> floor(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be,
            double... box) {
        return (p, s) -> construct(s, () -> new GridMountedBlock(p, s, be, false, box));
    }

    /** Блок, на котором держится устройство. */
    public static BlockPos mountPos(BlockState state, BlockPos pos) {
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()) : pos.below();
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel s && !newState.is(this) && level.getBlockEntity(pos) instanceof ConnectorBlockEntity c) {
            SimLevelBinding b = SimBindings.get(s);
            if (b != null) b.grids().connectorRemoved(c);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (!wall) return defaultBlockState();
        Direction face = ctx.getClickedFace();
        Direction f = face.getAxis().isHorizontal() ? face : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, f);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return wall ? shapes.get(state.getValue(BlockStateProperties.HORIZONTAL_FACING)) : floorShape;
    }

    /** Коробка модели (фасад на север) повёрнутая к {@code facing}. */
    static VoxelShape rotated(Direction facing, double[] b) {
        double ax = b[0] - 8, az = b[2] - 8, bx = b[3] - 8, bz = b[5] - 8;
        double[] p = rot(facing, ax, az), q = rot(facing, bx, bz);
        return Shapes.box((Math.min(p[0], q[0]) + 8) / 16, b[1] / 16, (Math.min(p[1], q[1]) + 8) / 16,
                (Math.max(p[0], q[0]) + 8) / 16, b[4] / 16, (Math.max(p[1], q[1]) + 8) / 16);
    }

    private static double[] rot(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {-z, x};
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
    }
}
