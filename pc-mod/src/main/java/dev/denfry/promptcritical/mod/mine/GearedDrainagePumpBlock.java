package dev.denfry.promptcritical.mod.mine;

import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Трёхплунжерный насос водоотлива с зубчатой передачей реального масштаба (08 §8.2.9,
 * docs/research/mine_drainage_pump-dimensions.md): 3 × 2 × 2, контроллер у западного конца, фасад на север. Вал Create
 * входит не в контроллер, а в клетку-порт на восточной грани ({@link #PORT}) — на ней сидит шестерня передачи.
 */
public final class GearedDrainagePumpBlock extends KineticMachineBlock {
    /** Клетка входного вала в модельных координатах (фасад на север): восточная грань, ось вдоль x. */
    public static final BlockPos PORT = new BlockPos(2, 0, 1);

    private GearedDrainagePumpBlock(Properties props, Spec spec) {
        super(props, spec, ShaftSide.X_AXIS, MineDrainagePumpBlockEntity::new);
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> create() {
        return (p, s) -> construct(s, () -> new GearedDrainagePumpBlock(p, s));
    }

    /** Ось входного вала в мире. */
    public static Direction.Axis shaftAxis(Direction facing) {
        return facing.getClockWise().getAxis();
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
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), shaftAxis(f));
                break;
            }
        return super.partState(controller, cell);
    }
}
