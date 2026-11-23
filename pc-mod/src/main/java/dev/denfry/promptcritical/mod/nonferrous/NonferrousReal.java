package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Цветная металлургия реального масштаба (08 §8.8): старые BE работают и на блоке {@code <id>_real} с цельной моделью.
 * Состояние машины (горит, дутьё, анодный эффект, никелевая серия) у такого блока — отдельная цельная модель
 * {@code <id>_real_<вариант>}, а не partial: корпуса шире диапазона partial-модели (3 м).
 */
final class NonferrousReal {
    private NonferrousReal() {
    }

    /** Id цельной модели: у старого блока null, у блока реального масштаба — его id с суффиксом варианта ("" — основной). */
    @Nullable
    static String model(BlockState state, String variant) {
        String id = WholeModelHost.idOf(state);
        return id == null || variant.isEmpty() ? id : id + "_" + variant;
    }

    static Direction facing(BlockState state) {
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    /** Клетка со светом перед фасадом: {@code front} клеток от контроллера, за передней гранью объёма. */
    static BlockPos lightPos(BlockPos controller, BlockState state, int front) {
        return controller.relative(facing(state), front);
    }
}
