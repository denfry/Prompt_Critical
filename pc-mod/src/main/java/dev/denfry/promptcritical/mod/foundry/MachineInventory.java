package dev.denfry.promptcritical.mod.foundry;

import java.util.function.BiPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Склад машины: слоты-входы (принимают то, что пропускает фильтр), слоты-выходы (только забирать) и служебные
 * (резец: и класть, и забирать). Снаружи (воронки, трубы Create, хопперы) виден через {@link #external()} с этими
 * правилами; машина сама пишет в любые слоты напрямую.
 */
public final class MachineInventory extends ItemStackHandler {
    public enum Role { INPUT, OUTPUT, SERVICE }

    private final Role[] roles;
    private final BiPredicate<Integer, ItemStack> filter;
    private final Runnable onChange;
    private final IItemHandler external = new External();

    public MachineInventory(Role[] roles, BiPredicate<Integer, ItemStack> filter, Runnable onChange) {
        super(roles.length);
        this.roles = roles.clone();
        this.filter = filter;
        this.onChange = onChange;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return roles[slot] != Role.OUTPUT && filter.test(slot, stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChange.run();
    }

    public IItemHandler external() {
        return external;
    }

    /** Положить в выходы; возвращает не поместившееся. */
    public ItemStack output(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < roles.length && !rest.isEmpty(); i++) {
            if (roles[i] == Role.OUTPUT) rest = putOutput(i, rest, false);
        }
        return rest;
    }

    /** Поместится ли {@code stack} в выходы целиком. */
    public boolean outputFits(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < roles.length && !rest.isEmpty(); i++) {
            if (roles[i] == Role.OUTPUT) rest = putOutput(i, rest, true);
        }
        return rest.isEmpty();
    }

    /** Положить в выходной слот мимо фильтра входов. */
    private ItemStack putOutput(int slot, ItemStack stack, boolean simulate) {
        ItemStack cur = getStackInSlot(slot);
        if (!cur.isEmpty() && !ItemStack.isSameItemSameComponents(cur, stack)) return stack;
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());
        int move = Math.min(stack.getCount(), limit - cur.getCount());
        if (move <= 0) return stack;
        if (!simulate) setStackInSlot(slot, stack.copyWithCount(cur.getCount() + move));
        return stack.copyWithCount(stack.getCount() - move);
    }

    /** Игрок кладёт стопку из руки во входы; возвращает остаток. */
    public ItemStack insertFromPlayer(ItemStack stack) {
        ItemStack rest = stack;
        for (int i = 0; i < roles.length && !rest.isEmpty(); i++) {
            if (roles[i] != Role.OUTPUT) rest = insertItem(i, rest, false);
        }
        return rest;
    }

    /** Отдать игроку выходы; {@code true} — что-то отдано. */
    public boolean giveOutputs(Player player) {
        boolean any = false;
        for (int i = 0; i < roles.length; i++) {
            if (roles[i] != Role.OUTPUT || getStackInSlot(i).isEmpty()) continue;
            ItemHandlerHelper.giveItemToPlayer(player, extractItem(i, getStackInSlot(i).getCount(), false));
            any = true;
        }
        return any;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        return serializeNBT(registries);
    }

    public void load(HolderLookup.Provider registries, CompoundTag tag) {
        int size = getSlots();
        deserializeNBT(registries, tag);
        if (getSlots() != size) setSize(size);
    }

    private final class External implements IItemHandler {
        @Override
        public int getSlots() {
            return MachineInventory.this.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return MachineInventory.this.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return roles[slot] == Role.OUTPUT ? stack : MachineInventory.this.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return roles[slot] == Role.INPUT ? ItemStack.EMPTY : MachineInventory.this.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return MachineInventory.this.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return MachineInventory.this.isItemValid(slot, stack);
        }
    }
}
