package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Звено опоры ВЛ (07 §7.4.2): {@code wooden_pole} и {@code concrete_pole} ставятся стопкой; {@code part} — по соседям
 * (низ с подпятником, ствол, верх с траверсой). Коннектор {@code wire_connector_mv} ставится на верх опоры.
 */
public final class PoleBlock extends PcBlock {
    private final VoxelShape shape;

    private PoleBlock(Properties props, Spec spec, double radius) {
        super(props, spec);
        double r = radius / 16;
        this.shape = Shapes.box(0.5 - r, 0, 0.5 - r, 0.5 + r, 1, 0.5 + r);
    }

    public static PoleBlock create(Properties props, double radiusPx) {
        Spec spec = Spec.of(PcProperties.CHIMNEY_PART);
        return construct(spec, () -> new PoleBlock(props, spec, radiusPx));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return withPart(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return dir.getAxis() == Direction.Axis.Y ? withPart(state, level, pos) : state;
    }

    private BlockState withPart(BlockState state, BlockGetter level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below()).is(this);
        boolean above = level.getBlockState(pos.above()).is(this);
        ChimneyPart part = !below ? ChimneyPart.BASE : above ? ChimneyPart.MIDDLE : ChimneyPart.TOP;
        return state.setValue(PcProperties.CHIMNEY_PART, part);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }
}
