package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Проводник без сопротивления (07 §7.4.6, §7.6.2): {@code power_cable} и {@code busbar}. Связная группа таких блоков —
 * одна шина вместе с выводами устройств, которых она касается. Соединения по шести граням — для рендера (к кабелям,
 * шинам и блокам сети); электрически связь — просто соседство.
 */
public final class CableBlock extends PcBlock {
    public static final Map<Direction, BooleanProperty> SIDES = new EnumMap<>(Map.of(
            Direction.NORTH, BlockStateProperties.NORTH, Direction.SOUTH, BlockStateProperties.SOUTH,
            Direction.EAST, BlockStateProperties.EAST, Direction.WEST, BlockStateProperties.WEST,
            Direction.UP, BlockStateProperties.UP, Direction.DOWN, BlockStateProperties.DOWN));
    private static final VoxelShape CORE = Shapes.box(5 / 16.0, 5 / 16.0, 5 / 16.0, 11 / 16.0, 11 / 16.0, 11 / 16.0);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.values()) {
            double x0 = 5, y0 = 5, z0 = 5, x1 = 11, y1 = 11, z1 = 11;
            switch (d) {
                case NORTH -> z0 = 0;
                case SOUTH -> z1 = 16;
                case WEST -> x0 = 0;
                case EAST -> x1 = 16;
                case DOWN -> y0 = 0;
                case UP -> y1 = 16;
            }
            ARMS.put(d, Shapes.box(x0 / 16, y0 / 16, z0 / 16, x1 / 16, y1 / 16, z1 / 16));
        }
    }

    private CableBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static CableBlock create(Properties props) {
        Spec spec = Spec.of(BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST,
                BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN);
        return construct(spec, () -> new CableBlock(props, spec));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.values())
            s = s.setValue(SIDES.get(d), connects(ctx.getLevel(), ctx.getClickedPos().relative(d)));
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return state.setValue(SIDES.get(dir), connects(level, neighborPos));
    }

    /** Кабель тянется к соседу: другой проводник или блок сети. */
    static boolean connects(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof CableBlock || GridBlocks.isGridBlock(level, pos, s);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet())
            if (state.getValue(e.getValue())) shape = Shapes.or(shape, ARMS.get(e.getKey()));
        return shape;
    }
}
