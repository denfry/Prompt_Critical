package dev.denfry.promptcritical.mod.client.whole;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Цельная модель с горящим вариантом: пока у контроллера {@code lit}, рисуется {@code <id>_lit} (накал печи —
 * элементы {@code glow_} в модели). Обе модели регистрирует вызывающий ({@link WholeModels#register}).
 */
public class LitWholeModelRenderer<T extends BlockEntity & WholeModelHost> extends WholeModelRenderer<T> {
    @Override
    @Nullable
    protected String modelId(T be) {
        String id = be.wholeModelId();
        BlockState s = be.getBlockState();
        return id != null && s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) ? id + "_lit" : id;
    }
}
