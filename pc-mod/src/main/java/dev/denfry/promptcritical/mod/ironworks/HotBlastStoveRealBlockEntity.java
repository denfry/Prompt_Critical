package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.fx.MachineFx;
import dev.denfry.promptcritical.sim.metallurgy.HotBlastStoveUnit;
import dev.denfry.promptcritical.sim.metallurgy.StoveMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Воздухонагреватель Каупера реального масштаба {@code hot_blast_stove_real} (08 §8.5.2,
 * docs/research/hot_blast_stove-dimensions.md): обечайка ⌀4,5 м, купол на 14 м, 5 × 14 × 5 блоков. Тот же
 * {@link HotBlastStoveUnit}, что у старого воздухонагревателя; домна находит его так же. Состояние для клиента:
 * {@code lit} — «на газе» (горелка горит, дымовой клапан открыт), {@code running} — «на дутье» (открыты клапаны
 * холодного и горячего дутья). Газ — через патрубок горелки у клетки (−1, 1, 0) с севера.
 */
public final class HotBlastStoveRealBlockEntity extends HotBlastStoveBlockEntity implements WholeModelHost {
    /** Точки модели, px (фасад на север, от угла клетки контроллера): купол-свеча, окно горелки. */
    private static final double[] BLEEDER = {8, 226, 40}, BURNER = {-8, 24, -2};

    public HotBlastStoveRealBlockEntity(BlockPos pos, BlockState state) {
        super(IronworksBlockEntities.HOT_BLAST_STOVE_REAL.get(), pos, state);
    }

    @Override
    protected void serverTick(SimLevelBinding b, HotBlastStoveUnit u) {
        stoveTick(b, u);
        StoveMode m = u.stove().mode();
        setStateFlag(BlockStateProperties.LIT, m == StoveMode.ON_GAS);
        setStateFlag(PcProperties.RUNNING, m == StoveMode.ON_BLAST);
    }

    /** Дымок над свечой купола и пламя в окне горелки, пока каупер на газе. */
    @Override
    protected void clientTick() {
        if (!stateFlag(BlockStateProperties.LIT)) return;
        MachineFx fx = fx();
        if (fx == null) return;
        if (fx.chance(0.5)) fx.wisp(fx.at(BLEEDER[0], BLEEDER[1], BLEEDER[2]), 0.4);
        if (fx.chance(1)) fx.flame(fx.at(BURNER[0], BURNER[1], BURNER[2]), 0.1);
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

    /** Свет — с клетки перед задвижкой горячего дутья: сам контроллер внутри глухого объёма. */
    @Override
    public BlockPos wholeModelLightPos() {
        return worldPosition.relative(facing());
    }
}
