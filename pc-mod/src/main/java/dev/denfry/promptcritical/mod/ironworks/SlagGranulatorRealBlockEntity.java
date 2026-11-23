package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Грануляция шлака реального масштаба {@code slag_granulator_real} (08 §8.5.5, art/blockbench/ironmaking/slag_granulator):
 * конец жёлоба, струйная коробка, бетонный бак и ковшовый элеватор, 3 × 4 × 4 блока. Логика старой грануляции: шлак
 * принимает клетка (0, 0, 0) у жёлоба, вода — в патрубок на востоке, гранулы — из жёлоба элеватора на западе.
 */
public final class SlagGranulatorRealBlockEntity extends SlagGranulatorBlockEntity implements WholeModelHost {
    public SlagGranulatorRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.SLAG_GRANULATOR_REAL.get(), pos, state);
    }

    /** Пар над баком, пока гасится шлак. */
    @Override
    protected void clientTick() {
        if (!stateFlag(PcProperties.RUNNING)) return;
        MachineFx fx = fx();
        if (fx == null) return;
        for (int i = 0; i < 2; i++) fx.smoke(fx.at(8, 24, 24), false, 1.0);
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return facing();
    }

    /** Свет — с клетки перед фасадом вне корпуса: сам контроллер внутри глухого объёма. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing(), 1);
    }
}
