package dev.denfry.promptcritical.mod.npp;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Кориум, растёкшийся по дну шахты из прорванного днища (04c §4c.10.5): слой расплава около 15 см под коркой. Светится,
 * жжёт и поджигает наступившего, держится только на твёрдом полу; кладёт его слой аварий, предмета и добычи нет.
 */
public final class CoriumPoolBlock extends Block {
    public static final MapCodec<CoriumPoolBlock> CODEC = simpleCodec(CoriumPoolBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 3, 16);

    public CoriumPoolBlock(Properties p) {
        super(p);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return dir == Direction.DOWN && !canSurvive(s, level, pos) ? Blocks.AIR.defaultBlockState() : s;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity) {
            entity.hurt(level.damageSources().hotFloor(), 6);
            entity.igniteForSeconds(5);
        }
        super.stepOn(level, pos, state, entity);
    }
}
