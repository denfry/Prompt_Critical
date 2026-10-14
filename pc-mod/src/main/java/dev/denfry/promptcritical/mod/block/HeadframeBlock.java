package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Копёр 3×N×3 (08 §8.2.8): сегменты 3×3×3 ставятся друг на друга; верхний рисуется оголовком ({@code top}),
 * копёр собран ({@code formed}) при N = 6–12, то есть из 2–4 сегментов.
 */
public final class HeadframeBlock extends PcBlock {
    public static final int MIN_SEGMENTS = 2;
    public static final int MAX_SEGMENTS = 4;
    private static final int STEP = 3;

    private HeadframeBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static HeadframeBlock create(Properties props) {
        Spec spec = Spec.facing(PcProperties.TOP, PcProperties.FORMED).footprint(Footprint.of(Footprint.Segment.box(-1, 1, -1, 1, -1, 1)));
        return construct(spec, () -> new HeadframeBlock(props, spec));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) refreshStack(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!level.isClientSide && !newState.is(this)) {
            if (level.getBlockState(pos.below(STEP)).is(this)) refreshStack(level, pos.below(STEP));
            if (level.getBlockState(pos.above(STEP)).is(this)) refreshStack(level, pos.above(STEP));
        }
    }

    /** Пересчитать {@code top}/{@code formed} всей стопки, в которую входит сегмент {@code anyAnchor}. */
    public void refreshStack(Level level, BlockPos anyAnchor) {
        BlockPos bottom = anyAnchor;
        while (sameStack(level, bottom.below(STEP), level.getBlockState(anyAnchor))) bottom = bottom.below(STEP);
        List<BlockPos> stack = new ArrayList<>();
        for (BlockPos p = bottom; level.getBlockState(p).is(this); p = p.above(STEP)) {
            if (!stack.isEmpty() && !sameStack(level, p, level.getBlockState(bottom))) break;
            stack.add(p);
        }
        boolean formed = stack.size() >= MIN_SEGMENTS && stack.size() <= MAX_SEGMENTS;
        for (int i = 0; i < stack.size(); i++) {
            BlockPos p = stack.get(i);
            BlockState s = level.getBlockState(p);
            BlockState t = s.setValue(PcProperties.TOP, i == stack.size() - 1).setValue(PcProperties.FORMED, formed);
            if (t != s) level.setBlock(p, t, Block.UPDATE_CLIENTS);
        }
    }

    private boolean sameStack(Level level, BlockPos p, BlockState reference) {
        BlockState s = level.getBlockState(p);
        return s.is(this) && facing(s) == facing(reference);
    }
}
