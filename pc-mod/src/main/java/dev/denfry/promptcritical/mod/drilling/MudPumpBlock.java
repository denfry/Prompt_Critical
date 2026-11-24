package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Буровой насос 3×2×2: контроллер в середине, вал Create подводят к клетке {@code kinetic_port} на приводном конце
 * (клетка (−1, 0, 1) модели, ось коленчатого вала — вдоль фасада). Пустая рука — местный щит.
 * <p>
 * Двойник реального масштаба {@code mud_pump_real} (6 × 3 × 3, art/blockbench/drilling/mud_pump_real.author.js) — тот же
 * BE: вал входит с севера в клетку (−2, 0, −1) модели, в приводной вал вдоль z модели.
 */
public final class MudPumpBlock extends FoundryBlock {
    /** Клетка входа вала в координатах модели. */
    public static final BlockPos PORT = new BlockPos(-1, 0, 1);
    /** Клетка входа вала у насоса реального масштаба. */
    public static final BlockPos PORT_REAL = new BlockPos(-2, 0, -1);

    private final BlockPos port;
    /** Вал вдоль фасада (ось z модели), а не поперёк. */
    private final boolean alongFacing;

    private MudPumpBlock(Properties props, Spec spec, BlockPos port, boolean alongFacing) {
        super(props, spec, MudPumpBlockEntity::new);
        this.port = port;
        this.alongFacing = alongFacing;
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new MudPumpBlock(props, spec, PORT, false));
    }

    public static PcDeviceBlock createReal(Properties props, Spec spec) {
        return construct(spec, () -> new MudPumpBlock(props, spec, PORT_REAL, true));
    }

    private Direction.Axis shaftAxis(Direction f) {
        return alongFacing ? f.getAxis() : f.getClockWise().getAxis();
    }

    /** Клетка порта в мире для контроллера в {@code pos}. */
    public BlockPos portPos(BlockState controller, BlockPos pos) {
        return pos.offset(port.rotate(Footprint.rotation(facing(controller))));
    }

    /** Скорость вала Create в клетке-порте насоса, об/мин; не насос или нет вала — 0 (для рендера частей). */
    public static double portSpeed(BlockEntity be) {
        BlockState s = be.getBlockState();
        if (be.getLevel() == null || !(s.getBlock() instanceof MudPumpBlock b)) return 0;
        return be.getLevel().getBlockEntity(b.portPos(s, be.getBlockPos()))
                instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity k ? k.getSpeed() : 0;
    }

    @Override
    protected BlockState partState(BlockState controller, Footprint.Cell cell) {
        Direction f = facing(controller);
        // клетку порта ищем по сегменту и смещению от его якоря: они не зависят от места постройки
        BlockPos at = port.rotate(Footprint.rotation(f));
        for (Footprint.Cell c : spec().footprint().cells(BlockPos.ZERO, f))
            if (c.pos().equals(at)) {
                if (c.segment() == cell.segment() && c.offsetFromAnchor().equals(cell.offsetFromAnchor()))
                    return KineticPortBlock.inputForOffset(cell.offsetFromAnchor(), shaftAxis(f));
                break;
            }
        return super.partState(controller, cell);
    }
}
