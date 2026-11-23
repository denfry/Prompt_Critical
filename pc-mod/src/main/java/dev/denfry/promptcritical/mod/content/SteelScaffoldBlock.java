package dev.denfry.promptcritical.mod.content;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Стальные строительные леса: рама из труб с настилом-решёткой сверху. Ведут себя как ванильные леса — внутрь
 * заходят и лезут вверх прыжком, вниз — приседая; на настиле стоят. Нужны, чтобы подобраться к верху машины реального
 * масштаба, пока она строится и в работе (спецификация real-scale machines, M5).
 */
public final class SteelScaffoldBlock extends Block {
    public static final MapCodec<SteelScaffoldBlock> CODEC = simpleCodec(SteelScaffoldBlock::new);
    /** Рама: четыре стойки и настил; настил — то, на чём стоят сверху. */
    private static final VoxelShape DECK = Block.box(0, 14, 0, 16, 16, 16);
    private static final VoxelShape FRAME = Shapes.or(DECK, Block.box(0, 0, 0, 2, 14, 2), Block.box(14, 0, 0, 16, 14, 2),
            Block.box(0, 0, 14, 2, 14, 16), Block.box(14, 0, 14, 16, 14, 16));

    public SteelScaffoldBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FRAME;
    }

    /** Сверху — настил (если не спускаться); внутри рамы — свободно, как у ванильных лесов. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return context.isAbove(Shapes.block(), pos, true) && !context.isDescending() ? DECK : Shapes.empty();
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    public boolean isScaffolding(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
