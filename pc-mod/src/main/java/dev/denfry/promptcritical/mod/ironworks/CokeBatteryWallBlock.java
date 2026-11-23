package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Простенок коксовой батареи: клетка без BE, вид (ярус, торец с анкерной колонной) — по соседям ({@link CokeBatteryCells}). */
public final class CokeBatteryWallBlock extends PcBlock {
    private CokeBatteryWallBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static CokeBatteryWallBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new CokeBatteryWallBlock(props, spec));
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : CokeBatteryCells.look(s, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos,
            BlockPos neighborPos) {
        return CokeBatteryCells.look(super.updateShape(state, dir, neighbor, level, pos, neighborPos), level, pos);
    }
}
