package dev.denfry.promptcritical.mod.panel;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * Меню местного щита. Каналы — 32-битные целые; пакет данных меню передаёт short, поэтому канал идёт двумя слотами
 * данных (младшие и старшие 16 бит). Сервер читает показания хоста раз за рассылку; кнопки — {@code clickMenuButton}.
 */
public final class PanelMenu extends AbstractContainerMenu implements PanelValues {
    private final PanelLayout layout;
    @Nullable private final PanelHost host;
    private final BlockPos pos;
    private final int[] values;
    private final int machineSlots;
    /** Инвентарь справа от поля (экран низкий для щита с инвентарём снизу). */
    private final boolean side;
    /**
     * Клиент решает, ставить ли инвентарь справа: экран знает свою высоту, меню строит слоты раньше экрана. Сервер
     * позиции слотов не использует.
     */
    public static Predicate<PanelLayout> sideInventory = l -> false;

    /** Сервер. */
    PanelMenu(int id, Inventory inv, PanelHost host) {
        this(id, inv, host.panelLayout(), host, host.panelPos(), host.panelItems());
        host.readPanel(values);
    }

    /** Клиент: раскладка и позиция из пакета открытия, склад — пустая копия того же размера. */
    public PanelMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, PanelLayouts.get(buf.readUtf()), null, buf.readBlockPos(), null);
    }

    private PanelMenu(int id, Inventory inv, PanelLayout layout, @Nullable PanelHost host, BlockPos pos,
            @Nullable IItemHandlerModifiable items) {
        super(PanelLayouts.MENU.get(), id);
        this.layout = layout;
        this.side = host == null && layout.inventory && sideInventory.test(layout);
        this.host = host;
        this.pos = pos;
        this.values = new int[layout.channels];
        IItemHandlerModifiable store = items != null ? items : new ItemStackHandler(Math.max(1, layout.itemSlots()));
        for (PanelLayout.SlotSpec s : layout.slots) addSlot(new PanelSlot(store, s));
        machineSlots = slots.size();
        if (layout.inventory) {
            int ix = layout.inventoryX(side), iy = layout.inventoryY(side);
            for (int r = 0; r < 3; r++)
                for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, ix + c * 18, iy + r * 18));
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, ix + c * 18, iy + 58));
        }
        for (int ch = 0; ch < layout.channels; ch++) {
            addDataSlot(new Half(ch, false));
            addDataSlot(new Half(ch, true));
        }
    }

    public PanelLayout layout() {
        return layout;
    }

    public boolean side() {
        return side;
    }

    public BlockPos pos() {
        return pos;
    }

    public int machineSlots() {
        return machineSlots;
    }

    @Override
    public int raw(int ch) {
        return ch >= 0 && ch < values.length ? values[ch] : 0;
    }

    @Override
    public void broadcastChanges() {
        if (host != null) host.readPanel(values);
        super.broadcastChanges();
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (host != null && player instanceof ServerPlayer sp) host.onPanelButton(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < machineSlots) {
            if (!moveItemStackTo(stack, machineSlots, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, machineSlots, false)) {
            // из инвентаря в хотбар и обратно, как у сундука
            boolean hotbar = index >= slots.size() - 9;
            if (!moveItemStackTo(stack, hotbar ? machineSlots : slots.size() - 9, hotbar ? slots.size() - 9 : slots.size(), false))
                return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        if (stack.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return host == null || host.panelValid(player);
    }

    /** Слот машины: выходы только забирают, входы берут то, что пропускает раскладка (и склад на сервере). */
    private static final class PanelSlot extends SlotItemHandler {
        private final PanelLayout.SlotSpec spec;

        PanelSlot(IItemHandlerModifiable handler, PanelLayout.SlotSpec spec) {
            super(handler, spec.index(), spec.x(), spec.y());
            this.spec = spec;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !spec.output() && spec.accepts().test(stack) && super.mayPlace(stack);
        }

        /** Изменение стопки на месте (досыпка shift-кликом) — через склад, чтобы BE пометил себя изменённым. */
        @Override
        public void setChanged() {
            ((IItemHandlerModifiable) getItemHandler()).setStackInSlot(getSlotIndex(), getItem());
        }
    }

    /** Половина канала: младшие или старшие 16 бит. */
    private final class Half extends DataSlot {
        private final int ch;
        private final boolean high;

        Half(int ch, boolean high) {
            this.ch = ch;
            this.high = high;
        }

        @Override
        public int get() {
            return high ? values[ch] >> 16 : (short) values[ch];
        }

        @Override
        public void set(int v) {
            values[ch] = high ? values[ch] & 0xFFFF | v << 16 : values[ch] & 0xFFFF0000 | v & 0xFFFF;
        }
    }
}
