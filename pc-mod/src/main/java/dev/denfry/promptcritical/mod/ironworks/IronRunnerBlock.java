package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Жёлоб литейного двора {@code iron_runner} (08 §8.5.5): связные жёлоба по горизонтали и уступом на клетку вниз
 * ведут чугун от лётки к ковшу или на песок; {@code filled} — по жёлобу идёт металл (светится).
 */
public final class IronRunnerBlock extends PcBlock {
    private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 7 / 16.0, 1);

    private IronRunnerBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static IronRunnerBlock create(Properties props) {
        Spec spec = Spec.facing(PcProperties.FILLED);
        return construct(spec, () -> new IronRunnerBlock(props, spec));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }
}
