package dev.denfry.promptcritical.mod.hazard;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Блоки разлива нефти (09 §9.11.9, 14 §14.5): плёнка на воде, бон, замазученный берег. */
public final class SpillBlocks {
    private SpillBlocks() {
    }

    /** Лежит на воде: под клеткой вода. */
    static boolean onWater(LevelReader level, BlockPos pos) {
        return level.getFluidState(pos.below()).is(FluidTags.WATER) && level.getFluidState(pos).isEmpty();
    }

    /**
     * {@code oil_slick} — видимая часть пятна на воде: несплошной слой толщиной полпикселя над водой, без коллизии.
     * {@code age} 0…3 — выветривание («мусс» темнеет), ставит {@link OilSpills} при раскладке.
     */
    public static final class OilSlickBlock extends Block {
        public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
        public static final MapCodec<OilSlickBlock> CODEC = simpleCodec(OilSlickBlock::new);
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 0.5, 16);

        public OilSlickBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(AGE, 0));
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AGE);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
            return onWater(level, pos);
        }

        @Override
        protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos pos, BlockPos np) {
            return d == Direction.DOWN && !canSurvive(s, level, pos) ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    /**
     * {@code oil_boom} — звено бонового заграждения на воде: плёнка через линию бонов не растекается (14 §14.5.4). Ось —
     * вдоль линии бона. Звено держит, пока нормальная к нему скорость воды не больше 0,35 м/с; течения в мире нет,
     * поэтому на стоячей воде бон держит всегда.
     */
    public static final class OilBoomBlock extends Block {
        public static final MapCodec<OilBoomBlock> CODEC = simpleCodec(OilBoomBlock::new);
        private static final VoxelShape Z = Block.box(5, 0, 0, 11, 5, 16), X = Block.box(0, 0, 5, 16, 5, 11);

        public OilBoomBlock(Properties p) {
            super(p);
            registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.Z));
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(BlockStateProperties.HORIZONTAL_AXIS);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction.Axis a = ctx.getHorizontalDirection().getAxis();
            return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_AXIS, a == Direction.Axis.X ? Direction.Axis.X : Direction.Axis.Z);
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return s.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.X ? X : Z;
        }

        @Override
        protected boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
            return onWater(level, pos);
        }

        @Override
        protected BlockState updateShape(BlockState s, Direction d, BlockState n, LevelAccessor level, BlockPos pos, BlockPos np) {
            return d == Direction.DOWN && !canSurvive(s, level, pos) ? Blocks.AIR.defaultBlockState() : s;
        }
    }

    /**
     * {@code oiled_sand}, {@code oiled_gravel} — замазученный берег (14 §14.5.4): выветривается естественно с полупериодом
     * {@code env.shore_half_life_days} = 30 суток физики (1 игровые сутки = 1 сутки физики, K_slow 00 §0.7): стадия
     * {@code age} растёт случайным тиком, после третьей блок снова чистый. Сорбент очищает сразу.
     */
    public static final class OiledShoreBlock extends Block {
        public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
        public static final MapCodec<OiledShoreBlock> CODEC = simpleCodec(p -> new OiledShoreBlock(p, false));
        /** Случайный тик приходит блоку в среднем раз в 68 с (3 тика на 4096 блоков секции). */
        static final double RANDOM_TICK_S = 4096 / 3.0 / 20;
        /** Три стадии по трети полупериода: вероятность шага за случайный тик. */
        static final double STEP_P = RANDOM_TICK_S / (dev.denfry.promptcritical.sim.hazard.OilSpill.SHORE_HALF_LIFE_DAYS * 1200 / 3);
        private final boolean gravel;

        public OiledShoreBlock(Properties p, boolean gravel) {
            super(p);
            this.gravel = gravel;
            registerDefaultState(stateDefinition.any().setValue(AGE, 0));
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(AGE);
        }

        public BlockState clean() {
            return gravel ? Blocks.GRAVEL.defaultBlockState() : Blocks.SAND.defaultBlockState();
        }

        @Override
        protected boolean isRandomlyTicking(BlockState s) {
            return true;
        }

        @Override
        protected void randomTick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
            if (random.nextDouble() >= STEP_P) return;
            int age = s.getValue(AGE);
            level.setBlock(pos, age >= 2 ? clean() : s.setValue(AGE, age + 1), Block.UPDATE_ALL);
        }
    }
}
