package dev.denfry.promptcritical.mod.radiation;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Узел радзащиты с облучёнными предметами внутри (горячая камера, контейнер, тележка, модуль хранения): при сносе
 * контроллера содержимое выпадает, а не пропадает ({@code PcBlock#onRemove}).
 */
public interface ShieldedContents {
    ItemStackHandler contents();

    /** Что ещё выпадает кроме содержимого (тележка — контейнер на ложементе). */
    default ItemStack extraDrop() {
        return ItemStack.EMPTY;
    }
}
