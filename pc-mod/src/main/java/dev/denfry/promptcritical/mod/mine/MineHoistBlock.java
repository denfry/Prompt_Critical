package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.registry.PcItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Подъёмная машина Ц-2,5 {@code mine_hoist} 3×2×3 (08 §8.2.8): барабан на оси X модели в верхнем ряду, вал Create
 * подводят к клетке {@code kinetic_port} на конце барабана (клетка (1, 0, 0) модели). Канат ставят предметом
 * {@code wire_rope}, пустая рука открывает панель посадок.
 */
public final class MineHoistBlock extends PcDeviceBlock {
    public static final int PORT_X = 1;

    private MineHoistBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MineHoistBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MineHoistBlock(props, spec));
    }

    /** Ось барабана в мире. */
    public static Direction.Axis drumAxis(Direction facing) {
        return facing.getClockWise().getAxis();
    }

    public static BlockPos portPos(BlockPos controller, Direction facing) {
        return BoilerGeometry.offset(controller, facing, PORT_X, 0, 0);
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.offsetFromAnchor().equals(new BlockPos(PORT_X, 0, 0).rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), drumAxis(f));
        return super.partState(controller, cell);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new MineHoistBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isController(state) ? (l, p, s, be) -> {
            if (be instanceof MineHoistBlockEntity h) h.tick();
        } : null;
    }

    @Nullable
    private MineHoistBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(state, pos)) instanceof MineHoistBlockEntity be ? be : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        MineHoistBlockEntity be = controller(level, pos, state);
        if (be == null || !stack.is(PcItems.WIRE_ROPE.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return be.installRope(player, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        MineHoistBlockEntity be = controller(level, pos, state);
        if (be == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) be.openPanel(sp);
        return InteractionResult.CONSUME;
    }
}
