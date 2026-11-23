package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Клетка коксовой батареи: у каждой — свой BE; щит открывает контроллер батареи ({@link CokeOvenBlockEntity#use}). Вид
 * клетки (ярус, торец с дверью, ось камеры) — по соседям ({@link CokeBatteryCells}).
 */
public final class CokeOvenBlock extends FoundryBlock {
    private CokeOvenBlock(Properties props, Spec spec) {
        super(props, spec, CokeOvenBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new CokeOvenBlock(props, spec));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        FoundryDevice d = device(level, pos, state);
        if (d == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        return d.use(player);
    }

    @Override
    @org.jetbrains.annotations.Nullable
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
