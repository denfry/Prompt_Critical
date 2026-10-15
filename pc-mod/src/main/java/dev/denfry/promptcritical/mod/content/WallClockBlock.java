package dev.denfry.promptcritical.mod.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Настенные часы (11 §11.6.2): стрелки рисует BER по игровому времени. */
public class WallClockBlock extends DecorBlock implements EntityBlock {
    protected WallClockBlock(Properties props, Spec spec, double[] box) {
        super(props, spec, box, false);
    }

    public static WallClockBlock create(Properties props, Spec spec, double[] box) {
        return construct(spec, () -> new WallClockBlock(props, spec, box));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallClockBlockEntity(pos, state);
    }

    /** Без данных: BE нужен только для отрисовки стрелок. */
    public static class WallClockBlockEntity extends BlockEntity {
        public WallClockBlockEntity(BlockPos pos, BlockState state) {
            super(ContentRegistries.WALL_CLOCK.get(), pos, state);
        }
    }
}
