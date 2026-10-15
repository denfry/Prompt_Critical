package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.block.PcDeviceBlock;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Песчаная форма: разбитая вручную остывшая форма отдаёт отливку (08 §8.9). */
public final class SandMoldBlock extends FoundryBlock {
    private SandMoldBlock(Properties props, Spec spec) {
        super(props, spec, SandMoldBlockEntity::new);
    }

    public static BiFunction<Properties, Spec, PcDeviceBlock> factory() {
        return (p, s) -> construct(s, () -> new SandMoldBlock(p, s));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && !newState.is(this) && level.getBlockEntity(pos) instanceof SandMoldBlockEntity m) {
            m.dropCastingOnBreak();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
