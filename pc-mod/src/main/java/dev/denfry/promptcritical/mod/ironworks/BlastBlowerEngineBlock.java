package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Воздуходувная машина реального масштаба: цельная модель, BE {@link BlastBlowerEngineBlockEntity} на контроллере.
 * Вал Create входит в западную грань клетки (0, 1, 0) модели — в клетку kinetic_port (ось вала вдоль x модели).
 */
public final class BlastBlowerEngineBlock extends FoundryBlock {
    /** Клетка вала в модели (фасад на север, от контроллера). */
    public static final BlockPos SHAFT_PORT = new BlockPos(0, 1, 0);

    private BlastBlowerEngineBlock(Properties props, Spec spec) {
        super(props, spec, BlastBlowerEngineBlockEntity::new);
    }

    public static BlastBlowerEngineBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new BlastBlowerEngineBlock(props, spec));
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку вала ищем по сегменту и смещению от его якоря: они не зависят от места постройки
        BlockPos at = SHAFT_PORT.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
                break;
            }
        return super.partState(controller, cell);
    }
}
