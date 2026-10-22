package dev.denfry.promptcritical.mod.ops;

import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.GridMountedBlock;
import dev.denfry.promptcritical.mod.ironworks.IronworksBlockEntity;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.ops.OpsSubsystem;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Щит обслуживания {@code maintenance_panel} (13 §13.6.5): вешается на любой блок установки (котёл, машина,
 * вентилятор, насос, подъём); 6 ламп категорий горят цветом наивысшей видимой тяжести; ПКМ — щит с пусконаладкой,
 * неисправностями и паспортом ({@link OpsPanels}). Инструменты и запчасти по щиту действуют на его установку.
 */
public final class MaintenancePanelBlockEntity extends IronworksBlockEntity implements PanelHost {
    private static final int UPDATE_TICKS = 20;
    private int ticks;

    public MaintenancePanelBlockEntity(BlockPos pos, BlockState state) {
        super(OpsBlockEntities.MAINTENANCE_PANEL.get(), pos, state);
    }

    /** Установка, на которой висит щит. */
    @Nullable
    public OpsMachine machine() {
        return level == null ? null : OpsWorld.machine(level, GridMountedBlock.mountPos(getBlockState(), worldPosition));
    }

    @Override
    protected void serverTick(SimLevelBinding b) {
        if (ticks++ % UPDATE_TICKS != 0) return;
        OpsMachine m = machine();
        OpsSubsystem.Unit u = m == null ? null : OpsWorld.unit(level, m.simId());
        int sev = u == null ? 0 : u.panel().maxSeverity();
        BlockState s = getBlockState();
        if (s.getValue(PcProperties.SEVERITY) != sev) level.setBlock(worldPosition, s.setValue(PcProperties.SEVERITY, sev), Block.UPDATE_CLIENTS);
    }

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
    }

    @Override
    public PanelLayout panelLayout() {
        return OpsPanels.MAINTENANCE;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        OpsMachine m = machine();
        OpsSubsystem ops = OpsWorld.ops(level);
        OpsSubsystem.Unit u = m == null || ops == null ? null : ops.unit(m.simId());
        if (u != null) OpsPanels.read(out, ops, u, m.opsStopped(), m.opsCurrent());
    }
}
