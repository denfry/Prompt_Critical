package dev.denfry.promptcritical.mod.steelworks;

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
 * Машина на валу Create, у которой вал входит не в контроллер, а в клетку-порт {@code kinetic_port} на краю объёма
 * (как агломашина): конвертер (цапфа), клеть (валки), прошивной и сварочный станы, компрессор ВРУ. Контроллер —
 * {@link dev.denfry.promptcritical.mod.boiler.ShaftConsumer}.
 */
public final class PortMachineBlock extends FoundryBlock {
    private final BlockPos port;
    /** Ось вала порта: вдоль фасада (Z модели) или поперёк (X модели). */
    private final boolean alongFacing;

    private PortMachineBlock(Properties props, Spec spec, BlockPos port, boolean alongFacing,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        super(props, spec, be);
        this.port = port;
        this.alongFacing = alongFacing;
    }

    /** Фабрика для {@code PcBlocks.device}: порт в модельных координатах (фасад на север). */
    public static BiFunction<Properties, Spec, PcDeviceBlock> with(BlockPos port, boolean alongFacing,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new PortMachineBlock(p, s, port, alongFacing, be));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        if (cell.segment() == 0 && cell.offsetFromAnchor().equals(port.rotate(Footprint.rotation(f))))
            return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), alongFacing ? f.getAxis() : f.getClockWise().getAxis());
        return super.partState(controller, cell);
    }
}
