package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Вагранка реального масштаба: цельная модель, BE {@link CupolaRealBlockEntity} на контроллере. Клетка вентилятора
 * воздуходувки (1, 1, 1) модели — вход вала Create с востока (ось вала вдоль x модели).
 */
public final class CupolaRealBlock extends FoundryBlock {
    /** Клетка вала в модели (фасад на север, от контроллера). */
    public static final BlockPos SHAFT_PORT = new BlockPos(1, 1, 1);

    private CupolaRealBlock(Properties props, Spec spec) {
        super(props, spec, CupolaRealBlockEntity::new);
    }

    public static CupolaRealBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new CupolaRealBlock(props, spec));
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
