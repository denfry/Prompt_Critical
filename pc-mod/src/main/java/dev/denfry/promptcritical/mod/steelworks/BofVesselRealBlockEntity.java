package dev.denfry.promptcritical.mod.steelworks;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Кислородный конвертер реального масштаба {@code bof_vessel_real} на плавку ≈ 15 т (08 §8.6.1,
 * docs/research/bof_vessel-dimensions.md): корпус ⌀3,6 × 4,8 м в цапфовом кольце на двух опорах, 7 × 6 × 5 блоков.
 * Тот же {@link dev.denfry.promptcritical.sim.metallurgy.ConverterUnit} и та же логика, что у старого конвертера
 * ({@link BofVesselBlockEntity}): меняется только размещение. Вал Create входит соосно цапфе в редуктор наклона на
 * западной опоре (клетка (−3, 2, 0)), фурма {@code oxygen_lance} стоит над камином в (0, 6, 0), ковш с чугуном —
 * перед объёмом (z −4…−3), сталеразливочный ковш — позади (z 3…4), конвертерный газ — из газохода сверху (0, 5, 1).
 */
public final class BofVesselRealBlockEntity extends BofVesselBlockEntity implements WholeModelHost {
    /** Ось цапф в модели, px от угла клетки контроллера (фасад на север): вдоль x через (·, 46, 8). */
    public static final double TRUNNION_Y_PX = 46, TRUNNION_Z_PX = 8;
    static final Layout REAL = new Layout(6, -4, -3, 3, 4, 2, 84);

    public BofVesselRealBlockEntity(BlockPos pos, BlockState state) {
        super(SteelworksBlockEntities.BOF_VESSEL_REAL.get(), pos, state, REAL);
    }

    @Override
    @Nullable
    public String wholeModelId() {
        return WholeModelHost.idOf(getBlockState());
    }

    @Override
    public Direction wholeModelFacing() {
        return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    /** Свет — с клетки перед объёмом со стороны завалки: контроллер под корпусом. */
    @Override
    public BlockPos wholeModelLightPos() {
        return Steelworks.world(worldPosition, wholeModelFacing(), 0, 0, -3);
    }
}
