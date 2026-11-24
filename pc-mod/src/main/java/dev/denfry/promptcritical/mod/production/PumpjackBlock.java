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
 * Станок-качалка 3×1×3 (редуктор с кривошипами, балансир, шатуны, головка): контроллер в середине, вал Create —
 * к клетке {@code kinetic_port} на быстроходном валу редуктора, клетка (−1, 0, 0) модели, ось вдоль фасада. От
 * электродвигателя сети его крутят тем же валом ({@code electric_motor}).
 * <p>
 * Двойник реального масштаба {@code pumpjack_real} (СК-8, 9 × 7 × 3, art/blockbench/oil_production/pumpjack_real.author.js)
 * — тот же BE: вал входит с юга в клетку (−3, 1, 1) модели, во входной вал редуктора вдоль z модели; устье ставят в
 * свободную колонну под головкой балансира, клетка (4, 0, 0).
 */
public final class PumpjackBlock extends FoundryBlock {
    public static final BlockPos PORT = new BlockPos(-1, 0, 0);
    public static final BlockPos PORT_REAL = new BlockPos(-3, 1, 1);

    private final BlockPos port;
    /** Вал вдоль фасада (ось z модели), а не поперёк. */
    private final boolean alongFacing;

    private PumpjackBlock(Properties props, Spec spec, BlockPos port, boolean alongFacing) {
        super(props, spec, PumpjackBlockEntity::new);
        this.port = port;
        this.alongFacing = alongFacing;
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new PumpjackBlock(props, spec, PORT, false));
    }

    public static PcDeviceBlock createReal(Properties props, Spec spec) {
        return construct(spec, () -> new PumpjackBlock(props, spec, PORT_REAL, true));
    }

    private Direction.Axis shaftAxis(Direction f) {
        return alongFacing ? f.getAxis() : f.getClockWise().getAxis();
    }

    /** Клетка порта в мире для контроллера в {@code pos}. */
    public BlockPos portPos(BlockState controller, BlockPos pos) {
        return pos.offset(port.rotate(Footprint.rotation(facing(controller))));
    }

    /** Скорость вала Create в клетке-порте качалки, об/мин; не качалка или нет вала — 0 (для рендера частей). */
    public static double portSpeed(BlockEntity be) {
        BlockState s = be.getBlockState();
        if (be.getLevel() == null || !(s.getBlock() instanceof PumpjackBlock b)) return 0;
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
