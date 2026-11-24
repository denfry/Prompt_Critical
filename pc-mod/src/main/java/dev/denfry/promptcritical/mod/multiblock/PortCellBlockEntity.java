package dev.denfry.promptcritical.mod.multiblock;

import dev.denfry.promptcritical.mod.boiler.BoilerBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Пустой BE клетки-патрубка машины реального масштаба ({@link PortCellBlock} или якорь сегмента, на котором стоит
 * порт {@link WholePorts}). Своих данных и тика нет: Create стыкует трубу только с клеткой, у которой есть BE
 * ({@code FluidPropagator.hasFluidCapability}), а бак за патрубком отдают capability-провайдеры блоков (привязки машин),
 * которые ищут контроллер по якорю.
 */
public final class PortCellBlockEntity extends BlockEntity {
    public PortCellBlockEntity(BlockPos pos, BlockState state) {
        super(BoilerBlockEntities.PORT_CELL.get(), pos, state);
    }
}
