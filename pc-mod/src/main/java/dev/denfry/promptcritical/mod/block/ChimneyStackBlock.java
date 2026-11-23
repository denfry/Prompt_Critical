package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Кирпичная дымовая труба котельной реального масштаба (07 §7.9.2, docs/research/chimney_stack-dimensions.md): звено —
 * 3 × 3 × 3 вокруг контроллера, звенья ставятся друг на друга через три блока. Нижнее — квадратный пьедестал с аркой
 * газохода на северной грани, верхнее — оголовок, между ними — круглый ствол. Котёл считает высоту трубы по клеткам
 * над своим газоходом (3 м на звено) так же, как звенья старой трубы {@link ChimneyBlock}.
 */
public final class ChimneyStackBlock extends PcBlock {
    /** Высота звена, блоков. */
    public static final int SEGMENT_H = 3;
    public static final Footprint FP_SEGMENT = Footprint.of(Footprint.Segment.box(-1, 1, -1, 1, -1, 1));

    private ChimneyStackBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static ChimneyStackBlock create(Properties props) {
        Spec spec = Spec.facing(PcProperties.CHIMNEY_PART).footprint(FP_SEGMENT);
        return construct(spec, () -> new ChimneyStackBlock(props, spec));
    }

    /** Клетка трубы: звено старой трубы или любая клетка звена реального масштаба. */
    public static boolean isChimneyCell(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.getBlock() instanceof ChimneyBlock || s.getBlock() instanceof ChimneyStackBlock) return true;
        if (!(s.getBlock() instanceof MultiblockPartBlock)) return false;
        return level.getBlockState(MultiblockPartBlock.anchor(s, pos)).getBlock() instanceof ChimneyStackBlock;
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : withPart(s, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide || oldState.is(this)) return;
        refresh(level, pos);
        refresh(level, pos.below(SEGMENT_H));
        refresh(level, pos.above(SEGMENT_H));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (level.isClientSide || newState.is(this)) return;
        refresh(level, pos.below(SEGMENT_H));
        refresh(level, pos.above(SEGMENT_H));
    }

    /** Пересчитать вид звена по соседним звеньям (контроллеры через три блока по вертикали). */
    private void refresh(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!s.is(this)) return;
        BlockState t = withPart(s, level, pos);
        if (t != s) level.setBlock(pos, t, Block.UPDATE_CLIENTS);
    }

    private BlockState withPart(BlockState state, BlockGetter level, BlockPos pos) {
        boolean below = level.getBlockState(pos.below(SEGMENT_H)).is(this);
        boolean above = level.getBlockState(pos.above(SEGMENT_H)).is(this);
        ChimneyPart part = !below ? ChimneyPart.BASE : above ? ChimneyPart.MIDDLE : ChimneyPart.TOP;
        return state.setValue(PcProperties.CHIMNEY_PART, part);
    }
}
