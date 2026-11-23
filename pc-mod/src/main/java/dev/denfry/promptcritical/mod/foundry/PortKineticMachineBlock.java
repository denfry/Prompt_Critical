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
 * Машина на валу Create реального масштаба, у которой вал входит не в контроллер, а в клетку-порт {@code kinetic_port}
 * (станки цеха под трансмиссией: вал — в западную грань контрпривода над станком). BE контроллера — наследник
 * {@link PortKineticMachineBlockEntity}: обороты и исправность сети он берёт с порта.
 */
public final class PortKineticMachineBlock extends KineticMachineBlock {
    /** Клетка порта в модельных координатах (фасад на север); ось вала — x модели. */
    private final BlockPos port;

    private PortKineticMachineBlock(Properties props, Spec spec, BlockPos port, BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        super(props, spec, ShaftSide.X_AXIS, be);
        this.port = port;
    }

    /** Фабрика для {@code PcBlocks.device}: порт в модельных координатах (фасад на север). */
    public static BiFunction<Properties, Spec, PcDeviceBlock> with(BlockPos port, BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new PortKineticMachineBlock(p, s, port, be));
    }

    /** Клетка порта в модельных координатах (фасад на север). */
    public BlockPos port() {
        return port;
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
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
                break;
            }
        return super.partState(controller, cell);
    }
}
