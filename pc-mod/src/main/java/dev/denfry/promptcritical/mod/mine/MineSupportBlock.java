package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Крепь выработки {@code mine_support} (деревянный оклад или арка СВП). Сломанная ({@code damaged}) больше не держит
 * кровлю как следует: сквозь пролом верхняка сыплется порода, изредка срывается камешек (только клиентский эффект,
 * обрушение считает шахта).
 */
public final class MineSupportBlock extends PcBlock {
    private MineSupportBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MineSupportBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MineSupportBlock(props, spec));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PcProperties.DAMAGED) || !isController(state) || random.nextInt(3) != 0) return;
        BlockState roof = level.getBlockState(pos.above(2));
        if (roof.isAir()) return;
        double x = pos.getX() + 0.3 + random.nextDouble() * 0.4, z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
        level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, roof), x, pos.getY() + 1.95, z, 0, 0, 0);
        if (random.nextInt(20) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, SoundEvents.GRAVEL_FALL, SoundSource.BLOCKS,
                    0.4f, 0.8f + random.nextFloat() * 0.3f, false);
        }
    }
}
