package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import dev.denfry.promptcritical.sim.automation.plc.PlcCpu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ПЛК (A3): блок с клеммами модулей ввода-вывода и встроенным программатором. Пустая рука — окно программатора
 * (текст программы на IL, ошибки, ключ RUN/STOP, онлайн-индикация входов и выходов). Лампа RUN на модели —
 * свойство {@code running} блока.
 */
public class PlcBlockEntity extends AutomationBlockEntity {
    public PlcBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, AutomationKind.PLC);
    }

    @Override
    protected void serverTick(AutomationUnit u) {
        super.serverTick(u);
        setFlag(PcProperties.RUNNING, u.plc() != null && u.plc().mode() == PlcCpu.Mode.RUN);
    }

    @Override
    public InteractionResult use(Player player) {
        AutomationUnit u = unit();
        if (u == null || u.plc() == null) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp) PlcNet.open(sp, worldPosition, u);
        return InteractionResult.SUCCESS;
    }
}
