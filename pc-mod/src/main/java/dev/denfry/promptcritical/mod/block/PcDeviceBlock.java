package dev.denfry.promptcritical.mod.block;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Блок с доменным двойником (05 §5.3.1): на контроллере — {@link DeviceBlockEntity}, который регистрирует устройство
 * в топологии мира симуляции. Поведение устройств добавляют этапы 6–8.
 */
public class PcDeviceBlock extends PcBlock implements EntityBlock {
    protected PcDeviceBlock(Properties props, Spec spec) {
        super(props, spec);
    }

    public static PcDeviceBlock create(Properties props, Spec spec) {
        return construct(spec, () -> new PcDeviceBlock(props, spec));
    }

    /**
     * BE контроллера — от {@link #controllerEntity}; якорь другого сегмента получает пустой BE клетки-патрубка, если на
     * нём нарисован патрубок машины реального масштаба (труба Create стыкуется только с клеткой с BE), иначе BE нет.
     */
    @Override
    @Nullable
    public final BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (isController(state)) return controllerEntity(pos, state);
        return dev.denfry.promptcritical.mod.multiblock.PortCellBlock.isPortAnchor(this, state)
                ? new dev.denfry.promptcritical.mod.multiblock.PortCellBlockEntity(pos, state) : null;
    }

    /** BE контроллера машины. */
    @Nullable
    protected BlockEntity controllerEntity(BlockPos pos, BlockState state) {
        return new DeviceBlockEntity(pos, state);
    }

    /** Событие блока ({@code Level#blockEvent}) — его BE, как у ванильных блоков с BE (анимации на клиенте). */
    @Override
    protected boolean triggerEvent(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }
}
