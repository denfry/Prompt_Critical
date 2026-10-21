package dev.denfry.promptcritical.mod.automation;

import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.automation.AutomationKind;
import dev.denfry.promptcritical.sim.automation.AutomationUnit;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блок автоматики со щитом настроек ({@link AutomationPanels}): реле, реле времени, искатель, командоаппарат, реле
 * процесса, блок питания, газоанализатор, блоки A2 и станция. Пустая рука — щит, Shift — состояние в строке.
 */
public class AutomationPanelBlockEntity extends AutomationBlockEntity implements PanelHost {
    /** Ручка станции «▼/▲» держится полсекунды на клик. */
    public static final int RAMP_TICKS = 10;

    private int cursor, rampTicks, rampDir;

    public AutomationPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, AutomationKind kind) {
        super(type, pos, state, kind);
    }

    private AutomationPanels.Spec spec() {
        return AutomationPanels.of(kind);
    }

    @Override
    protected void serverTick(AutomationUnit u) {
        super.serverTick(u);
        if (kind == AutomationKind.MANUAL_STATION) {
            if (rampTicks > 0) rampTicks--;
            u.setInput(AutomationUnit.IN_MAIN, rampTicks > 0 ? rampDir : 0);
        }
    }

    @Override
    public PanelLayout panelLayout() {
        return spec().layout();
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        AutomationUnit u = unit();
        if (u == null) return;
        List<AutomationPanels.Row> rows = spec().rows();
        for (int i = 0; i < rows.size() && i < out.length; i++) out[i] = rows.get(i).read().read(u, cursor);
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        AutomationUnit u = unit();
        if (u == null) return;
        List<AutomationPanels.Row> rows = spec().rows();
        if (id >= 0 && id < 2 * rows.size()) {
            AutomationPanels.Adjust a = rows.get(id / 2).adjust();
            if (a != null) a.adjust(u, new AutomationPanels.Cursor() {
                @Override
                public int get() {
                    return cursor;
                }

                @Override
                public void set(int v) {
                    cursor = v;
                }
            }, id % 2 == 0 ? -1 : 1, player.isShiftKeyDown());
        } else {
            switch (id) {
                case AutomationPanels.BUTTON_REPLACE -> {
                    u.replace();
                    player.displayClientMessage(AutomationText.msg(AutomationText.REPLACED), true);
                }
                case AutomationPanels.BUTTON_CALIBRATE -> player.displayClientMessage(AutomationText.msg(u.calibrate()
                        ? AutomationText.CALIBRATED : AutomationText.CALIBRATE_REFUSED), true);
                case AutomationPanels.BUTTON_AUTO -> u.setStationManual(false);
                case AutomationPanels.BUTTON_MAN -> u.setStationManual(true);
                case AutomationPanels.BUTTON_DOWN, AutomationPanels.BUTTON_UP -> {
                    rampDir = id == AutomationPanels.BUTTON_UP ? 1 : -1;
                    rampTicks = RAMP_TICKS;
                }
                default -> {
                }
            }
        }
        sync();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("cursor", cursor);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cursor = tag.getInt("cursor");
    }
}
