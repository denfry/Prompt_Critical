package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Клеть {@code mine_cage} 2×3×2 (08 §8.2.8): пол, крыша и сетчатые стены, дверь на фасаде. Внутрь можно войти —
 * клетки дают форму пола, крыши и стен, а не полный блок; закрытая дверь — стена фасада. Пустая рука открывает и
 * закрывает дверь. В стволе клеть ездит контрапцией {@link CageContraption}.
 */
public final class MineCageBlock extends PcBlock {
    private static final double WALL = 1, SLAB = 2;
    /** Формы по [facing][open][клетка модели (x, y+1, z)]. */
    private static final VoxelShape[][][] SHAPES = new VoxelShape[4][2][12];

    private MineCageBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MineCageBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MineCageBlock(props, spec));
    }

    public boolean open(BlockState state) {
        return state.getValue(BlockStateProperties.OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!isController(state)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        BlockState next = state.cycle(BlockStateProperties.OPEN);
        level.setBlock(pos, next, Block.UPDATE_ALL);
        level.playSound(null, pos, open(next) ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1, 1);
        return InteractionResult.CONSUME;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isController(state) ? shape(state, BlockPos.ZERO) : Shapes.block();
    }

    @Override
    public VoxelShape partShape(BlockState anchor, BlockGetter level, BlockPos anchorPos, BlockPos offsetFromAnchor) {
        return shape(anchor, offsetFromAnchor);
    }

    private VoxelShape shape(BlockState controller, BlockPos worldOffset) {
        Direction f = facing(controller);
        BlockPos m = worldOffset.rotate(inverse(Footprint.rotation(f)));
        if (m.getX() < 0 || m.getX() > 1 || m.getY() < -1 || m.getY() > 1 || m.getZ() < 0 || m.getZ() > 1) return Shapes.block();
        int open = open(controller) ? 1 : 0, cell = m.getX() * 6 + (m.getY() + 1) * 2 + m.getZ();
        VoxelShape[] row = SHAPES[f.get2DDataValue()][open];
        if (row[cell] == null) row[cell] = build(m, open == 1, Footprint.rotation(f));
        return row[cell];
    }

    /** Клетка модели (фасад на север): пол внизу, крыша вверху, стены по внешним граням, дверь — северная. */
    private static VoxelShape build(BlockPos m, boolean open, Rotation rot) {
        VoxelShape s = Shapes.empty();
        if (m.getY() == -1) s = Shapes.or(s, box(0, 0, 0, 16, SLAB, 16, rot));
        if (m.getY() == 1) s = Shapes.or(s, box(0, 16 - SLAB, 0, 16, 16, 16, rot));
        if (m.getX() == 0) s = Shapes.or(s, box(0, 0, 0, WALL, 16, 16, rot));
        if (m.getX() == 1) s = Shapes.or(s, box(16 - WALL, 0, 0, 16, 16, 16, rot));
        if (m.getZ() == 1) s = Shapes.or(s, box(0, 0, 16 - WALL, 16, 16, 16, rot));
        if (m.getZ() == 0 && !open) s = Shapes.or(s, box(0, 0, 0, 16, 16, WALL, rot));
        return s.optimize();
    }

    /** Коробка в пикселях модели, повёрнутая вокруг центра блока. */
    private static VoxelShape box(double x0, double y0, double z0, double x1, double y1, double z1, Rotation rot) {
        double[] a = rotate(x0, z0, rot), b = rotate(x1, z1, rot);
        return Block.box(Math.min(a[0], b[0]), y0, Math.min(a[1], b[1]), Math.max(a[0], b[0]), y1, Math.max(a[1], b[1]));
    }

    private static double[] rotate(double x, double z, Rotation rot) {
        return switch (rot) {
            case CLOCKWISE_90 -> new double[] {16 - z, x};
            case CLOCKWISE_180 -> new double[] {16 - x, 16 - z};
            case COUNTERCLOCKWISE_90 -> new double[] {z, 16 - x};
            default -> new double[] {x, z};
        };
    }

    static Rotation inverse(Rotation r) {
        return switch (r) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> r;
        };
    }
}
