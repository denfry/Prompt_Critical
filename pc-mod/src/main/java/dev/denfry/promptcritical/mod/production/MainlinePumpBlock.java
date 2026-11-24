package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Магистральный насосный агрегат НМ 1250-260 реального масштаба 6 × 2 × 3
 * (art/blockbench/oil_transport/mainline_pump_real.author.js): вал Create подводят к хвостовику двигателя — клетке
 * {@code kinetic_port} (3, 0, 0) модели, вал вдоль x модели; всас — северная грань клетки (−1, 0, −1), нагнетание —
 * южная грань (−1, 0, 1). Пустая рука — местный щит.
 */
public final class MainlinePumpBlock extends FoundryBlock {
    /** Клетка входа вала в координатах модели. */
    public static final BlockPos PORT = new BlockPos(3, 0, 0);

    private MainlinePumpBlock(Properties props, Spec spec) {
        super(props, spec, MainlinePumpBlockEntity::new);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MainlinePumpBlock(props, spec));
    }

    /** Клетка порта в мире для контроллера в {@code pos}. */
    public BlockPos portPos(BlockState controller, BlockPos pos) {
        return pos.offset(PORT.rotate(Footprint.rotation(facing(controller))));
    }

    /** Скорость вала Create в клетке-порте, об/мин; не насос или нет вала — 0 (для рендера частей). */
    public static double portSpeed(BlockEntity be) {
        BlockState s = be.getBlockState();
        if (be.getLevel() == null || !(s.getBlock() instanceof MainlinePumpBlock b)) return 0;
        return be.getLevel().getBlockEntity(b.portPos(s, be.getBlockPos()))
                instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : 0;
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку порта ищем по сегменту и смещению от его якоря: они не зависят от места постройки
        BlockPos at = PORT.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getClockWise().getAxis());
                break;
            }
        return super.partState(controller, cell);
    }
}
