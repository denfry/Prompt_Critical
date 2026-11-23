package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Машина реального масштаба с цельной моделью и валом Create: клетка {@code port} модели (фасад на север, от
 * контроллера) — kinetic_port на входе, ось вала вдоль x модели. BE на контроллере получает вал как
 * {@link dev.denfry.promptcritical.mod.boiler.ShaftConsumer}.
 */
public final class RealShaftBlock extends FoundryBlock {
    private final BlockPos port;

    private RealShaftBlock(Properties props, Spec spec, BiFunction<BlockPos, BlockState, ? extends BlockEntity> be, BlockPos port) {
        super(props, spec, be);
        this.port = port;
    }

    /** Фабрика для {@code PcBlocks.device(name, props, spec, factory)}. */
    public static BiFunction<Properties, Spec, PcDeviceBlock> with(BlockPos port, BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new RealShaftBlock(p, s, be, port));
    }

    /** Клетка вала в модели. */
    public BlockPos port() {
        return port;
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку вала ищем по сегменту и смещению от его якоря: они не зависят от места постройки
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
