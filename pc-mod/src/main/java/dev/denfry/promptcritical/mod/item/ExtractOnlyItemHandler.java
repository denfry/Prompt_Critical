package dev.denfry.promptcritical.mod.item;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Склад продукта снаружи: воронки и трубы только забирают. Сам аппарат кладёт продукт во внутренний обработчик
 * напрямую — запрет вставки в нём через {@code isItemValid} закрыл бы и аппарату дорогу в собственный склад.
 */
public record ExtractOnlyItemHandler(IItemHandler inner) implements IItemHandler {
    @Override
    public int getSlots() {
        return inner.getSlots();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return inner.getStackInSlot(slot);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return inner.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return inner.getSlotLimit(slot);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return false;
    }
}
