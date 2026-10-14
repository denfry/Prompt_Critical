package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/** Звено дымовой трубы (07 §7.9.2): {@code part} — по соседям сверху и снизу (основание, ствол, оголовок). */
public final class ChimneyBlock extends PcBlock {
    private ChimneyBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static ChimneyBlock create(Properties props) {
        Spec spec = Spec.of(PcProperties.CHIMNEY_PART);
        return construct(spec, () -> new ChimneyBlock(props, spec));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withPart(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        return dir.getAxis() == Direction.Axis.Y ? withPart(state, level, pos) : state;
    }

    private BlockState withPart(BlockState state, BlockGetter level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        boolean above = level.getBlockState(pos.above()).is(this);
        ChimneyPart part = !below ? ChimneyPart.BASE : above ? ChimneyPart.MIDDLE : ChimneyPart.TOP;
        return state.setValue(PcProperties.CHIMNEY_PART, part);
    }
}
