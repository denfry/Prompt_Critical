package dev.denfry.promptcritical.mod.content;

import dev.denfry.promptcritical.mod.block.PcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Декор (11 §11.6): статичный блок с формой по модели арт-пайплайна (габарит модели фасадом на север, пиксели,
 * поворачивается по {@code facing}). Светильники с {@code lit} включаются и выключаются рукой, лестница с ограждением
 * — лазаемая.
 */
public class DecorBlock extends PcBlock {
    private final VoxelShape[] shapes = new VoxelShape[4];
    private final boolean climbable;

    protected DecorBlock(Properties props, Spec spec, double[] box, boolean climbable) {
        super(props, spec);
        this.climbable = climbable;
        for (Direction d : Direction.Plane.HORIZONTAL) shapes[d.get2DDataValue()] = rotated(box, d);
    }

    /** @param box {x0, y0, z0, x1, y1, z1} модели фасадом на север, пиксели; {@code null} — целый блок */
    public static DecorBlock create(Properties props, Spec spec, double[] box, boolean climbable) {
        return construct(spec, () -> new DecorBlock(props, spec, box, climbable));
    }

    /** Поворот блокстейта: y = (угол facing + 180) mod 360 (см. генератор состояний), по часовой сверху. */
    static VoxelShape rotated(double[] b, Direction facing) {
        if (b == null) return Shapes.block();
        double x0 = clamp(b[0]), y0 = clamp(b[1]), z0 = clamp(b[2]), x1 = clamp(b[3]), y1 = Math.min(24, Math.max(0, b[4])), z1 = clamp(b[5]);
        int turns = (((int) facing.toYRot() + 180) % 360) / 90;
        for (int i = 0; i < turns; i++) {
            // (x, z) → (16 − z, x)
            double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return Block.box(x0, y0, z0, x1, Math.max(y1, y0 + 0.5), z1);
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(16, v));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shapes[facing(state).get2DDataValue()];
    }

    @Override
    protected BlockState defaults(BlockState state) {
        BlockState s = super.defaults(state);
        // светильник ставится включённым
        return s.hasProperty(BlockStateProperties.LIT) ? s.setValue(BlockStateProperties.LIT, true) : s;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.hasProperty(BlockStateProperties.LIT)) return InteractionResult.PASS;
        if (!level.isClientSide) {
            boolean on = !state.getValue(BlockStateProperties.LIT);
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, on), Block.UPDATE_ALL);
            level.playSound(null, pos, on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                    SoundSource.BLOCKS, 0.5f, 1.2f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return climbable;
    }
}
