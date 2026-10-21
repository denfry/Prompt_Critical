package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcBlock;
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
 * Газосборник батареи {@code gas_collector_main} (08 §8.4.4): стояк над камерой и горизонтальная труба. Плечи — к
 * соседним участкам газосборника и к машинам, которые его касаются (эксгаустер, аппараты улавливания); газ по
 * группе считает батарея ({@link CokeBatteryShape}).
 */
public final class GasMainBlock extends PcBlock {
    public static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, BlockStateProperties.NORTH,
            Direction.SOUTH, BlockStateProperties.SOUTH, Direction.EAST, BlockStateProperties.EAST, Direction.WEST, BlockStateProperties.WEST);
    private static final VoxelShape CORE = Shapes.box(2 / 16.0, 2 / 16.0, 2 / 16.0, 14 / 16.0, 1, 14 / 16.0);

    private GasMainBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static GasMainBlock create(Properties props) {
        Spec spec = Spec.of(BlockStateProperties.NORTH, BlockStateProperties.SOUTH, BlockStateProperties.EAST, BlockStateProperties.WEST);
        return construct(spec, () -> new GasMainBlock(props, spec));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = defaultBlockState();
        for (Direction d : Direction.Plane.HORIZONTAL)
            s = s.setValue(SIDES.get(d), connects(ctx.getLevel(), ctx.getClickedPos().relative(d)));
        return s;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        if (dir.getAxis() == Direction.Axis.Y) return state;
        return state.setValue(SIDES.get(dir), connects(level, neighborPos));
    }

    /** Плечо к соседу: газосборник или машина у газосборника. */
    static boolean connects(BlockGetter level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof GasMainBlock) return true;
        if (!(level instanceof net.minecraft.world.level.Level l)) return false;
        BlockPos c = Ironworks.controllerOf(l, pos);
        return l.getBlockEntity(c) instanceof GasExhausterBlockEntity || l.getBlockEntity(c) instanceof ByproductPlantBlockEntity;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        VoxelShape shape = CORE;
        for (Map.Entry<Direction, BooleanProperty> e : SIDES.entrySet()) {
            if (!state.getValue(e.getValue())) continue;
            Direction d = e.getKey();
            double x0 = d == Direction.WEST ? 0 : d == Direction.EAST ? 14 : 2, x1 = d == Direction.WEST ? 2 : d == Direction.EAST ? 16 : 14;
            double z0 = d == Direction.NORTH ? 0 : d == Direction.SOUTH ? 14 : 2, z1 = d == Direction.NORTH ? 2 : d == Direction.SOUTH ? 16 : 14;
            shape = Shapes.or(shape, Shapes.box(x0 / 16, 2 / 16.0, z0 / 16, x1 / 16, 14 / 16.0, z1 / 16));
        }
        return shape;
    }
}
