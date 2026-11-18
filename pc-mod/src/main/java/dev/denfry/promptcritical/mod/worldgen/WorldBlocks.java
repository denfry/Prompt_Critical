package dev.denfry.promptcritical.mod.worldgen;

import com.mojang.serialization.MapCodec;
import dev.denfry.promptcritical.mod.content.SimAchievements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Блоки находок мира (11 §11.4): природный битум асфальтовых озёр и горящий выход газа. Ступить на них — находка для
 * достижений {@code pitch_lake} и {@code gas_crater} (скрытые, 11 §11.2.2).
 */
public final class WorldBlocks {
    /** События {@code sim_event}: игрок на битуме асфальтового озера, у горящего газового выхода. */
    public static final String PITCH_LAKE = "promptcritical:pitch_lake", GAS_CRATER = "promptcritical:gas_crater";

    private WorldBlocks() {
    }

    private static void found(Level level, Entity entity, String event) {
        if (!level.isClientSide && entity instanceof ServerPlayer p && level.getGameTime() % 20 == 0)
            SimAchievements.direct(p, event, "", 1);
    }

    /**
     * {@code natural_bitumen}: природный битум — вязкая масса, в которой вязнут ноги (как в мёде); признак нефтеносного
     * бассейна в радиусе 200–500 м (09).
     */
    public static final class NaturalBitumenBlock extends Block {
        public static final MapCodec<NaturalBitumenBlock> CODEC = simpleCodec(NaturalBitumenBlock::new);

        public NaturalBitumenBlock(Properties props) {
            super(props);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        /** Горит, как пролив нефти (класс B), и не выгорает: огонь на нём вечный, пока его не потушат пеной. */
        @Override
        public int getFlammability(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction dir) {
            return 5;
        }

        @Override
        public int getFireSpreadSpeed(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.core.Direction dir) {
            return 5;
        }

        @Override
        public boolean isFireSource(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos, net.minecraft.core.Direction dir) {
            return dir == net.minecraft.core.Direction.UP;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            found(level, entity, PITCH_LAKE);
            super.stepOn(level, pos, state, entity);
        }
    }

    /**
     * {@code gas_seep_vent}: потрескавшаяся порода, сквозь которую идёт метан; горящий выход — вечный огонь газового
     * кратера: жжёт и поджигает того, кто встал на него, светит. Признак газового месторождения (09).
     */
    public static final class GasSeepVentBlock extends Block {
        public static final MapCodec<GasSeepVentBlock> CODEC = simpleCodec(GasSeepVentBlock::new);
        public static final BooleanProperty BURNING = BlockStateProperties.LIT;

        public GasSeepVentBlock(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(BURNING, true));
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(BURNING);
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (state.getValue(BURNING)) {
                found(level, entity, GAS_CRATER);
                if (!entity.fireImmune()) {
                    entity.igniteForSeconds(4);
                    entity.hurt(level.damageSources().hotFloor(), 2);
                }
            }
            super.stepOn(level, pos, state, entity);
        }

        @Override
        public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource r) {
            if (!state.getValue(BURNING)) return;
            for (int i = 0; i < 3; i++)
                level.addParticle(r.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, pos.getX() + r.nextDouble(),
                        pos.getY() + 1.05, pos.getZ() + r.nextDouble(), 0, 0.05 + r.nextDouble() * 0.05, 0);
        }
    }
}
