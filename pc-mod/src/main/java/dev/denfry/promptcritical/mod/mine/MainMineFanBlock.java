package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.BoilerGeometry;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Вентилятор главного проветривания 3×3×3 (08 §8.2.7): контроллер в центре, крыльчатка на оси фасада. Вал Create
 * подводят сзади к клетке {@code kinetic_port} на оси (клетка (0, 0, 1) модели). Пустая рука — местный щит.
 * Тот же блок и BE у вентилятора Guibal реального масштаба ({@code guibal_fan}): вал — в клетке {@link #GUIBAL_PORT} у
 * кривошипа, воздух — только через глаз {@link #GUIBAL_EYE}.
 */
public final class MainMineFanBlock extends PcDeviceBlock {
    private MainMineFanBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static MainMineFanBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MainMineFanBlock(props, spec));
    }

    /** Клетка вала Guibal: торец оси за кривошипом, вход с южной грани (docs/research/guibal_fan-dimensions.md). */
    public static final BlockPos GUIBAL_PORT = new BlockPos(0, 2, 3);
    /** Глаз Guibal: клетка на оси у северной стены, к ней подводят канал от ствола. */
    public static final BlockPos GUIBAL_EYE = new BlockPos(0, 2, -2);

    public static BlockPos portPos(BlockPos controller, Direction facing) {
        return BoilerGeometry.offset(controller, facing, 0, 0, 1);
    }

    public static boolean isGuibal(BlockState s) {
        return s.is(dev.denfry.promptcritical.mod.registry.PcBlocks.GUIBAL_FAN.get());
    }

    /** Клетка входного вала вентилятора по состоянию его контроллера. */
    public static BlockPos portPos(BlockState controller, BlockPos pos) {
        Direction f = ((MainMineFanBlock) controller.getBlock()).facing(controller);
        BlockPos p = isGuibal(controller) ? GUIBAL_PORT : new BlockPos(0, 0, 1);
        return BoilerGeometry.offset(pos, f, p.getX(), p.getY(), p.getZ());
    }

    /** Клетка глаза Guibal, через которую вентилятор сообщается с выработкой. */
    public static BlockPos eyePos(BlockState controller, BlockPos pos) {
        Direction f = ((MainMineFanBlock) controller.getBlock()).facing(controller);
        return BoilerGeometry.offset(pos, f, GUIBAL_EYE.getX(), GUIBAL_EYE.getY(), GUIBAL_EYE.getZ());
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (isGuibal(controller)) {
            // клетку вала ищем по сегменту и смещению от его якоря: они не зависят от места постройки
            BlockPos at = GUIBAL_PORT.rotate(Footprint.rotation(f));
            for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
                if (c.pos().equals(at)) {
                    if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                        return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getAxis());
                    break;
                }
            return super.partState(controller, cell);
        }
        if (cell.offsetFromAnchor().equals(new BlockPos(0, 0, 1).rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getAxis());
        return super.partState(controller, cell);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(controllerPos(state, pos)) instanceof MainMineFanBlockEntity be)) return InteractionResult.PASS;
        if (!level.isClientSide && player instanceof ServerPlayer sp) PanelHost.open(sp, be);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new MainMineFanBlockEntity(pos, state) : null;
    }

    /** На клиенте у Guibal крутится кривошип машины и идут её эффекты; сервер считает вентилятор через сеть шахты. */
    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && isController(state) && isGuibal(state) ? (l, p, s, be) -> {
            if (be instanceof MainMineFanBlockEntity f) f.clientTick();
        } : null;
    }
}
