package dev.denfry.promptcritical.mod.safety;

import dev.denfry.promptcritical.mod.block.ChimneyStackBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.PcProperties.ChimneyPart;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Вентиляционная труба реакторного зала реального масштаба (03a §3a.9.3,
 * art/blockbench/ventilation/vent_stack_real.author.js): звено — 3 × 3 × 3 вокруг контроллера, звенья ставятся друг на
 * друга через три блока, как у {@link ChimneyStackBlock}. Нижнее — основание с патрубком вытяжки на северной грани, оно
 * и есть узел вытяжки ({@link VentStackBlockEntity}); выше — ствол, верхнее — оголовок с заградительными огнями.
 */
public final class VentStackBlock extends FoundryBlock {
    /** Высота звена, блоков. */
    public static final int SEGMENT_H = ChimneyStackBlock.SEGMENT_H;

    private VentStackBlock(Properties props, Spec spec) {
        super(props, spec, VentStackBlockEntity::new);
    }

    public static VentStackBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new VentStackBlock(props, spec));
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
