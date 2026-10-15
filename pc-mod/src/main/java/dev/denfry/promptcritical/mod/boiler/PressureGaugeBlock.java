package dev.denfry.promptcritical.mod.boiler;

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
 * Местный манометр A0 ({@code instrument_pressure}, 11 §11.5.4): трубка Бурдона на плите, вешается на грань котла или
 * машины и показывает избыточное давление того, на чём висит (котёл — паровое пространство, машина — золотниковая
 * коробка). Шкала 0–1,6 МПа, стрелка — {@code BlockEntityRenderer}.
 */
public final class PressureGaugeBlock extends PcBlock implements EntityBlock {
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) SHAPES.put(d, rotated(d, 2.5, 0, 10.5, 13.5, 15, 16));
    }

    private PressureGaugeBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static PressureGaugeBlock create(Properties props) {
        Spec spec = Spec.facing();
        return construct(spec, () -> new PressureGaugeBlock(props, spec));
    }

    /** Блок, на котором висит прибор. */
    public static BlockPos mountPos(BlockState state, BlockPos pos) {
        return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
    }

    /** Коробка модели (фасад на север, плита на юге) повёрнутая к {@code facing}. */
    private static VoxelShape rotated(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        double ax = x0 - 8, az = z0 - 8, bx = x1 - 8, bz = z1 - 8;
        double[] a = rot(facing, ax, az), b = rot(facing, bx, bz);
        return Shapes.box((Math.min(a[0], b[0]) + 8) / 16, y0 / 16, (Math.min(a[1], b[1]) + 8) / 16,
                (Math.max(a[0], b[0]) + 8) / 16, y1 / 16, (Math.max(a[1], b[1]) + 8) / 16);
    }

    private static double[] rot(Direction facing, double x, double z) {
        return switch (facing) {
            case EAST -> new double[] {-z, x};
            case SOUTH -> new double[] {-x, -z};
            case WEST -> new double[] {z, -x};
            default -> new double[] {x, z};
        };
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
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressureGaugeBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return (l, p, s, be) -> {
            if (be instanceof PressureGaugeBlockEntity g) g.tick();
        };
    }
}
