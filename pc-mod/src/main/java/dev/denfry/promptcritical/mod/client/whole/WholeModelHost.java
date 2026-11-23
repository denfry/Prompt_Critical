package dev.denfry.promptcritical.mod.client.whole;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Контроллер машины в реальном масштабе, рисующий цельную модель (спецификация real-scale machines, §1): id модели
 * из {@code wholemodels/<id>.json}, фасад и клетка, по которой берётся освещение.
 */
public interface WholeModelHost {
    @org.jetbrains.annotations.Nullable
    String wholeModelId();

    Direction wholeModelFacing();

    /** Клетка со светом снаружи корпуса (сам контроллер внутри глухого объёма). */
    BlockPos wholeModelLightPos();

    /** Id цельной модели для состояния блока: у машин реального масштаба — id блока, у старой серии — null. */
    @org.jetbrains.annotations.Nullable
    static String idOf(net.minecraft.world.level.block.state.BlockState state) {
        if (!dev.denfry.promptcritical.mod.registry.PcBlocks.isWholeModel(state.getBlock())) return null;
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }
}
