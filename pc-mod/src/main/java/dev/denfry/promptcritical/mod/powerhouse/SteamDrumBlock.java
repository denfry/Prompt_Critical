package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.block.PcProperties.DrumEnd;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Барабан водотрубного котла: секции по 1 м вдоль оси x модели (art/blockbench/boilers/steam_drum_real.author.js).
 * {@link PcProperties#DRUM_END} — по соседям вдоль оси: где ряд кончается, секция закрыта днищем с лазом и клапаном.
 */
public final class SteamDrumBlock extends PcBlock {
    private SteamDrumBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static SteamDrumBlock create(Properties props) {
        Spec spec = Spec.facing(PcProperties.DRUM_END);
        return construct(spec, () -> new SteamDrumBlock(props, spec));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : withEnd(s, ctx.getLevel(), ctx.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
            BlockPos pos, BlockPos neighborPos) {
        BlockState s = super.updateShape(state, dir, neighbor, level, pos, neighborPos);
        return s.is(this) && dir.getAxis() == facing(s).getClockWise().getAxis() ? withEnd(s, level, pos) : s;
    }

    /** Секция ряда, если сосед вдоль оси — барабан с той же осью. */
    private boolean drumAt(BlockGetter level, BlockPos pos, Direction.Axis axis) {
        BlockState n = level.getBlockState(pos);
        return n.is(this) && facing(n).getClockWise().getAxis() == axis;
    }

    /** Конец ряда: восток модели — по часовой от фасада. Одиночная секция — днище на востоке. */
    public BlockState withEnd(BlockState state, BlockGetter level, BlockPos pos) {
        Direction east = facing(state).getClockWise();
        boolean e = drumAt(level, pos.relative(east), east.getAxis());
        boolean w = drumAt(level, pos.relative(east.getOpposite()), east.getAxis());
        DrumEnd end = !e ? DrumEnd.EAST : !w ? DrumEnd.WEST : DrumEnd.NONE;
        return state.setValue(PcProperties.DRUM_END, end);
    }
}
