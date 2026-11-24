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

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new DeviceBlockEntity(pos, state) : null;
    }

    /** Событие блока ({@code Level#blockEvent}) — его BE, как у ванильных блоков с BE (анимации на клиенте). */
    @Override
    protected boolean triggerEvent(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.triggerEvent(id, param);
    }
}
