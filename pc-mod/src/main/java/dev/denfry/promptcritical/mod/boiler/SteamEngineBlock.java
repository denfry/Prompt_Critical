package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.panel.PanelHost;
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
 * Паровая машина 3×3×5 (07 §7.9.7): цилиндр с золотником и рама с коленвалом и маховиком. Конец коленвала со
 * стороны маховика — клетка {@code kinetic_port}: к ней снаружи подводят вал Create (ось коленвала — X модели).
 * Пустая рука — местный щит (вентиль, проворот, передача), с Shift — провернуть машину ломом; катаракт ставится
 * предметом.
 */
public final class SteamEngineBlock extends PcDeviceBlock {
    /** Клетка вала от контроллера в модели (якорь рамы (0, 0, 2) + 1 по оси коленвала). */
    public static final int PORT_X = 1, PORT_Y = 0, PORT_Z = 2;

    private SteamEngineBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static SteamEngineBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new SteamEngineBlock(props, spec));
    }

    /** Ось коленвала в мире. */
    public static Direction.Axis shaftAxis(Direction facing) {
        return facing.getClockWise().getAxis();
    }

    public static BlockPos portPos(BlockPos controller, Direction facing) {
        return BoilerGeometry.offset(controller, facing, PORT_X, PORT_Y, PORT_Z);
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетка вала — в сегменте рамы (1) на 1 по оси коленвала от его якоря
        if (cell.segment() == 1 && cell.offsetFromAnchor().equals(new BlockPos(PORT_X, 0, 0).rotate(Footprint.rotation(f))))
            return KineticPortBlock.forOffset(cell.offsetFromAnchor(), shaftAxis(f));
        return super.partState(controller, cell);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new SteamEngineBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return isController(state) ? (l, p, s, be) -> {
            if (be instanceof SteamEngineBlockEntity e) e.tick();
        } : null;
    }

    @Nullable
    private SteamEngineBlockEntity controller(Level level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(controllerPos(state, pos)) instanceof SteamEngineBlockEntity be ? be : null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        SteamEngineBlockEntity be = controller(level, pos, state);
        if (be == null || !stack.is(PcItems.GOVERNOR_DASHPOT.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        return be.fitDashpot(player, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        SteamEngineBlockEntity be = controller(level, pos, state);
        if (be == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) return be.barOver(player);
        if (player instanceof ServerPlayer sp) PanelHost.open(sp, be);
        return InteractionResult.CONSUME;
    }
}
