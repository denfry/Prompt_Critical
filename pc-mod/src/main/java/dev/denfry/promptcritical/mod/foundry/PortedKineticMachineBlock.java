package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Машина на валу Create реального масштаба, у которой вал входит не в контроллер, а в клетку-порт объёма (как у
 * {@code mine_drainage_pump_geared}): в клетке стоит {@link KineticPortBlock}, он передаёт обороты BE контроллера через
 * {@link dev.denfry.promptcritical.mod.boiler.ShaftConsumer}. BE — тот же, что у старой машины; он берёт скорость с порта,
 * когда у него цельная модель.
 */
public final class PortedKineticMachineBlock extends KineticMachineBlock {
    /** Клетка порта в модели (фасад на север) и ось вала в модели. */
    private final BlockPos port;
    private final Direction.Axis modelAxis;

    private PortedKineticMachineBlock(Properties props, Spec spec, BlockPos port, Direction.Axis modelAxis,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        super(props, spec, ShaftSide.X_AXIS, be);
        this.port = port;
        this.modelAxis = modelAxis;
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> with(BlockPos port, Direction.Axis modelAxis,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new PortedKineticMachineBlock(p, s, port, modelAxis, be));
    }

    /** Ось вала в мире при фасаде {@code facing}. */
    public Direction.Axis shaftAxis(Direction facing) {
        return modelAxis == Direction.Axis.Y ? modelAxis
                : modelAxis == Direction.Axis.Z ? facing.getAxis() : facing.getClockWise().getAxis();
    }

    /** Клетка порта в мире для контроллера в {@code controller}. */
    public BlockPos portPos(BlockState controller, BlockPos pos) {
        return pos.offset(port.rotate(Footprint.rotation(facing(controller))));
    }

    /** Скорость вала Create в клетке-порте машины {@code be}, об/мин; не порт — 0 (для рендера частей). */
    public static double portSpeed(BlockEntity be) {
        BlockState s = be.getBlockState();
        if (be.getLevel() == null || !(s.getBlock() instanceof PortedKineticMachineBlock b)) return 0;
        return be.getLevel().getBlockEntity(b.portPos(s, be.getBlockPos()))
                instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : 0;
    }

    /** Контроллер вала не имеет: сеть Create доходит только до порта. */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return false;
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку порта ищем по сегменту и смещению от его якоря: они не зависят от места постройки
        BlockPos at = port.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), shaftAxis(f));
                break;
            }
        return super.partState(controller, cell);
    }
}
