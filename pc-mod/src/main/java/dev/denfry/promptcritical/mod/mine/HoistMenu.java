package dev.denfry.promptcritical.mod.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Панель подъёмной машины (08 §8.2.8): посадки ствола приходят при открытии, показания — слотами данных меню,
 * команды — кнопками меню ({@code clickMenuButton}): номер посадки, стоп, сброс защиты, предел скорости.
 */
public final class HoistMenu extends AbstractContainerMenu {
    public static final int BUTTON_STOP = 100, BUTTON_RESET = 101, BUTTON_SLOWER = 102, BUTTON_FASTER = 103;
    public static final int D_STATE = 0, D_DEPTH_DM = 1, D_SPEED_CMS = 2, D_TARGET = 3, D_AT = 4, D_LIMIT_DMS = 5,
            D_PAYLOAD_KG = 6, D_SF_X10 = 7, D_SF_NEED_X10 = 8, D_POWER_KW = 9, D_AVAILABLE_KW = 10, D_ROPE_MM = 11,
            D_ROPE_WEAR = 12, D_PROBLEM = 13, D_DUTY = 14, DATA_COUNT = 15;

    @Nullable private final MineHoistBlockEntity be;
    private final ContainerData data;
    private final BlockPos pos;
    private final int[] landings;

    /** Сервер. */
    HoistMenu(int id, Inventory inv, MineHoistBlockEntity be, int[] landings) {
        super(MineHoisting.HOIST_MENU.get(), id);
        this.be = be;
        this.data = be.data;
        this.pos = be.getBlockPos();
        this.landings = landings;
        addDataSlots(data);
    }

    /** Клиент: позиция и посадки из пакета открытия. */
    public HoistMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        super(MineHoisting.HOIST_MENU.get(), id);
        this.be = null;
        this.pos = buf.readBlockPos();
        this.landings = buf.readVarIntArray();
        this.data = new SimpleContainerData(DATA_COUNT);
        addDataSlots(data);
    }

    public int get(int index) {
        return data.get(index);
    }

    public int[] landings() {
        return landings.clone();
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be != null && player instanceof ServerPlayer sp) be.onButton(sp, id);
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return be == null || be.stillValid(player);
    }
}
