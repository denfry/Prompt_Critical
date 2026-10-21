package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GridSubsystem;
import dev.denfry.promptcritical.sim.grid.RelaySetting;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Потребитель-посёлок ({@code grid_consumer}, 3×3×3, 07 §7.6.1): ТП 6 кВ, кабель — к любой наружной грани. Мощность и
 * рубильник — на щите; два места под модули АЧР, 27, 81, 51 ({@code relay_ufls} ставит в очередь АЧР).
 */
public class ConsumerBlockEntity extends GridDeviceBlockEntity<ConsumerUnit> implements PanelHost {
    public static final int SLOTS = 2;

    private final ItemStackHandler modules = new ItemStackHandler(SLOTS) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof RelayModuleItem m && accepts(m.kind());
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public ConsumerBlockEntity(BlockPos pos, BlockState state) {
        super(GridBlockEntities.GRID_CONSUMER.get(), pos, state);
    }

    static boolean accepts(RelaySetting.Kind k) {
        return k == RelaySetting.Kind.UFLS || k == RelaySetting.Kind.UNDERVOLTAGE || k == RelaySetting.Kind.UNDERFREQUENCY
                || k == RelaySetting.Kind.OVERCURRENT;
    }

    @Override
    protected ConsumerUnit unitOf(GridSubsystem grid) {
        return grid.consumer(simId());
    }

    @Override
    public List<List<Face>> terminals() {
        List<Face> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    for (Direction d : Direction.values()) {
                        int nx = x + d.getStepX(), ny = y + d.getStepY(), nz = z + d.getStepZ();
                        if (Math.abs(nx) > 1 || Math.abs(ny) > 1 || Math.abs(nz) > 1)
                            out.add(new Face(worldPosition.offset(x, y, z), d));
                    }
                }
            }
        }
        return List.of(out);
    }

    /** Модули в слотах как уставки. */
    List<RelaySetting> settings() {
        List<RelaySetting> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            RelaySetting s = RelayModuleItem.setting(modules.getStackInSlot(i));
            if (s != null) out.add(s);
        }
        return out;
    }

    @Override
    protected void serverTick(ConsumerUnit u) {
        if (ticks % 20 == 7) u.setRelays(settings());
        if (ticks % 10 == 0) setFlag(BlockStateProperties.LIT, u.energized());
    }

    @Override
    public PanelLayout panelLayout() {
        return GridPanels.CONSUMER;
    }

    @Override
    @Nullable
    public IItemHandlerModifiable panelItems() {
        return modules;
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        ConsumerUnit u = unit();
        if (u == null) return;
        out[GridPanels.C_DEMAND_KW] = (int) Math.round(u.demandW() / 1e3);
        out[GridPanels.C_POWER_KW10] = (int) Math.round(u.powerW() / 100);
        out[GridPanels.C_FLAGS] = (u.energized() ? 1 : 0) | (u.shed() ? 2 : 0) | (u.switchedOn() ? 4 : 0);
        out[GridPanels.C_SHED_COUNT] = u.shedCount();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        ConsumerUnit u = unit();
        if (u == null) return;
        if (id == GridPanels.BUTTON_DEMAND) u.cycleDemand();
        else if (id == GridPanels.BUTTON_SWITCH) u.setSwitchedOn(!u.switchedOn());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("modules", modules.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("modules")) modules.deserializeNBT(registries, tag.getCompound("modules"));
    }
}
