package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Единая точка «что надето» (16 §16.9): ванильные слоты и слоты Curios и Accessories. Оба мода назначают предмет
 * слоту тегом ({@code curios:belt}, {@code accessories:face} …), код нужен только для чтения. Моды читаются через
 * отражение по их публичному API, без зависимости при сборке (R62); любая ошибка — интеграция выключается с одним
 * предупреждением в журнале, мод работает по ванильным слотам.
 *
 * <p>Возвращаемые стеки живые: изменение компонентов стека сохраняется в слоте того мода.
 */
public final class WornGear {
    private static volatile Method curiosInventory, curiosEquipped;
    private static volatile Method accessoriesGet, accessoriesAll, entryStack;
    private static volatile boolean curiosBroken, accessoriesBroken;

    private WornGear() {}

    /** Стеки в слотах Curios и Accessories, без пустых. */
    public static List<ItemStack> accessories(LivingEntity e) {
        List<ItemStack> out = new ArrayList<>();
        if (!curiosBroken && CompatSwitch.active(ServerConfig.COMPAT_CURIOS, "curios")) curios(e, out);
        if (!accessoriesBroken && CompatSwitch.active(ServerConfig.COMPAT_ACCESSORIES, "accessories")) accessoriesMod(e, out);
        return out;
    }

    /** Надетый предмет: сначала ванильный слот {@code slot}, затем слоты модов; {@link ItemStack#EMPTY} — нет. */
    public static ItemStack find(LivingEntity e, EquipmentSlot slot, Predicate<ItemStack> what) {
        ItemStack vanilla = e.getItemBySlot(slot);
        if (!vanilla.isEmpty() && what.test(vanilla)) return vanilla;
        for (ItemStack s : accessories(e))
            if (what.test(s)) return s;
        return ItemStack.EMPTY;
    }

    /** Лежит ли именно этот стек в слоте Curios или Accessories. */
    public static boolean inAccessorySlot(LivingEntity e, ItemStack stack) {
        for (ItemStack s : accessories(e))
            if (s == stack) return true;
        return false;
    }

    private static void curios(LivingEntity e, List<ItemStack> out) {
        try {
            if (curiosInventory == null) {
                curiosInventory = Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosInventory", LivingEntity.class);
                curiosEquipped = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler").getMethod("getEquippedCurios");
            }
            Optional<?> inv = (Optional<?>) curiosInventory.invoke(null, e);
            if (inv.isEmpty()) return;
            IItemHandler h = (IItemHandler) curiosEquipped.invoke(inv.get());
            for (int i = 0; i < h.getSlots(); i++) {
                ItemStack s = h.getStackInSlot(i);
                if (!s.isEmpty()) out.add(s);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            curiosBroken = true;
            PromptCritical.LOGGER.warn("Curios present but its API could not be read; worn gear uses vanilla slots only (16 §16.9)", ex);
        }
    }

    private static void accessoriesMod(LivingEntity e, List<ItemStack> out) {
        try {
            if (accessoriesGet == null) {
                Class<?> cap = Class.forName("io.wispforest.accessories.api.AccessoriesCapability");
                accessoriesAll = cap.getMethod("getAllEquipped");
                entryStack = Class.forName("io.wispforest.accessories.api.slot.SlotEntryReference").getMethod("stack");
                accessoriesGet = cap.getMethod("get", LivingEntity.class);
            }
            Object cap = accessoriesGet.invoke(null, e);
            if (cap == null) return;
            for (Object ref : (List<?>) accessoriesAll.invoke(cap)) {
                ItemStack s = (ItemStack) entryStack.invoke(ref);
                if (s != null && !s.isEmpty()) out.add(s);
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            accessoriesBroken = true;
            PromptCritical.LOGGER.warn("Accessories present but its API could not be read; worn gear uses vanilla slots only (16 §16.9)", ex);
        }
    }
}
