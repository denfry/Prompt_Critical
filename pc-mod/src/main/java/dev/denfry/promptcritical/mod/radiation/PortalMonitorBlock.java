package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Колонна портального монитора (04b §4b.7.2): пока в проходе перед ней стоит человек с активностью выше порога, а
 * потом ещё 2 с, она выдаёт сигнал редстоуна — его заводят на дверь выхода из зоны или на сирену.
 */
public class PortalMonitorBlock extends FoundryBlock {
    protected PortalMonitorBlock(Properties props, Spec spec) {
        super(props, spec, PortalMonitorBlockEntity::new);
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> create() {
        return (p, s) -> construct(s, () -> new PortalMonitorBlock(p, s));
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.hasProperty(BlockStateProperties.POWERED) && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }
}
