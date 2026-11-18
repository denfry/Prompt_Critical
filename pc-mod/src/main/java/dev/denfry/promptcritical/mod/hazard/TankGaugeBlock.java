package dev.denfry.promptcritical.mod.hazard;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import dev.denfry.promptcritical.mod.block.PcBlock.Spec;
import dev.denfry.promptcritical.mod.foundry.FoundryBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Радарный уровнемер резервуара с независимым сигнализатором верхнего аварийного уровня (09 §9.8.4): при уровне выше
 * 95 % ({@code TANK_HIGH_HIGH_LEVEL}) выдаёт сигнал редстоуна — его заводят на муфту насоса налива или на ПАЗ.
 */
public class TankGaugeBlock extends FoundryBlock {
    protected TankGaugeBlock(Properties props, Spec spec) {
        super(props, spec, TankGaugeBlockEntity::new);
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> create() {
        return (p, s) -> construct(s, () -> new TankGaugeBlock(p, s));
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
