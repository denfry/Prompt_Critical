package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Отсадочная машина Баума реального масштаба (08 §8.3, docs/research/coal_jig-dimensions.md): 3 × 3 × 5, контроллер на
 * полу у загрузочного конца, фасад (загрузка) на север. Вал Create входит не в контроллер, а в клетку-порт
 * вентилятора ({@link #PORT}) с северной грани; от неё линия вала ведёт поворотные воздушные клапаны.
 */
public final class CoalJigRealBlock extends KineticMachineBlock {
    /** Клетка входного вала в модельных координатах (фасад на север): северная грань, ось вдоль z. */
    public static final BlockPos PORT = new BlockPos(1, 2, 0);

    private CoalJigRealBlock(Properties props, Spec spec) {
        super(props, spec, ShaftSide.FRONT, CoalJigBlockEntity::new);
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> create() {
        return (p, s) -> construct(s, () -> new CoalJigRealBlock(p, s));
    }

    /** Клетка порта в мире для контроллера в {@code controller} с фасадом {@code facing}. */
    public static BlockPos portPos(BlockPos controller, Direction facing) {
        return controller.offset(PORT.rotate(Footprint.rotation(facing)));
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
        BlockPos at = PORT.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getAxis());
                break;
            }
        return super.partState(controller, cell);
    }
}
