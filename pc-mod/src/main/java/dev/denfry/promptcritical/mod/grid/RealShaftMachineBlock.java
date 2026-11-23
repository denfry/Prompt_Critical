package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Электрическая машина реального масштаба на валу Create (07 §7.3.1, §7.6.3): объём 3 × 3 × 3, контроллер — в
 * нижней средней клетке, ось вала вдоль фасада на высоте 1,5 м. Вал входит не в контроллер, а в клетку-порт на
 * торце: у генератора — потребитель с юга ({@link #GENERATOR_PORT}), у двигателя — источник с севера
 * ({@link #MOTOR_PORT}). Клетки — в пространстве модели (фасад на север), как в шапках
 * art/blockbench/grid/*_real.author.js.
 */
public final class RealShaftMachineBlock extends KineticMachineBlock {
    /** Вал Create входит в южную грань клетки (0, 1, 1) генератора. */
    public static final BlockPos GENERATOR_PORT = new BlockPos(0, 1, 1);
    /** Возбудитель стоит на оси генератора вплотную к северной грани клетки (0, 1, −1). */
    public static final BlockPos EXCITER_CELL = new BlockPos(0, 1, -2);
    /** Вал двигателя выходит из северной грани клетки (0, 1, −1). */
    public static final BlockPos MOTOR_PORT = new BlockPos(0, 1, -1);

    private final BlockPos port;
    private final boolean input;

    private RealShaftMachineBlock(Properties props, Spec spec, BlockPos port, boolean input,
            BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        super(props, spec, input ? ShaftSide.BACK : ShaftSide.FRONT, be);
        this.port = port;
        this.input = input;
    }

    /** Генератор: вал Create — потребитель в клетке {@link #GENERATOR_PORT}. */
    public static BiFunction<Properties, Spec, PcDeviceBlock> generator(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new RealShaftMachineBlock(p, s, GENERATOR_PORT, true, be));
    }

    /** Двигатель: вал Create — источник в клетке {@link #MOTOR_PORT}. */
    public static BiFunction<Properties, Spec, PcDeviceBlock> motor(BiFunction<BlockPos, BlockState, ? extends BlockEntity> be) {
        return (p, s) -> construct(s, () -> new RealShaftMachineBlock(p, s, MOTOR_PORT, false, be));
    }

    /** Клетка модели {@code cell} в мире для контроллера в {@code controller} с фасадом {@code facing}. */
    public static BlockPos at(BlockPos controller, Direction facing, BlockPos cell) {
        return controller.offset(cell.rotate(Footprint.rotation(facing)));
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
                    return input ? KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), f.getAxis())
                            : KineticPortBlock.forOffset(cell.offsetFromAnchor(), f.getAxis());
                break;
            }
        return super.partState(controller, cell);
    }
}
