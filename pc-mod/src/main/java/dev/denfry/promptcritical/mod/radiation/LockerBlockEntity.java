package dev.denfry.promptcritical.mod.radiation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Шкафы санпропускника (04b §4b.12.2). «Чистый» шкаф — у каждого своя ячейка: своя одежда остаётся в ней, взамен
 * надевается то, что лежало (спецодежда, бахилы, маска), — ПКМ меняет местами четыре слота брони. «Грязный» шкаф
 * принимает снятые в зоне вещи вместе с их загрязнением (ПКМ — снять всё зонное), ПКМ с Shift пустой рукой — забрать
 * последнюю вещь обратно (в спецпрачечную).
 */
public final class LockerBlockEntity extends BlockEntity {
    public static final int DIRTY_SLOTS = 27;
    static final EquipmentSlot[] ARMOR = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    private final Map<UUID, NonNullList<ItemStack>> outfits = new HashMap<>();
    private final List<ItemStack> dirty = new ArrayList<>();

    public LockerBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.LOCKER.get(), pos, state);
    }

    /** Чистый шкаф: поменять броню игрока с его ячейкой. */
    public void swapOutfit(Player p) {
        NonNullList<ItemStack> cell = outfits.computeIfAbsent(p.getUUID(), k -> NonNullList.withSize(ARMOR.length, ItemStack.EMPTY));
        for (int i = 0; i < ARMOR.length; i++) {
            ItemStack worn = p.getItemBySlot(ARMOR[i]);
            p.setItemSlot(ARMOR[i], cell.get(i));
            cell.set(i, worn);
        }
        if (cell.stream().allMatch(ItemStack::isEmpty)) outfits.remove(p.getUUID());
        setChanged();
    }

    /** Грязный шкаф: снять с игрока все зонные вещи; возвращает число снятых. */
    public int depositZoneClothing(Player p) {
        int n = 0;
        for (EquipmentSlot s : ARMOR) {
            ItemStack worn = p.getItemBySlot(s);
            if (!RadGear.zoneClothing(worn) || dirty.size() >= DIRTY_SLOTS) continue;
            dirty.add(worn.copy());
            p.setItemSlot(s, ItemStack.EMPTY);
            n++;
        }
        if (n > 0) setChanged();
        return n;
    }

    /** Грязный шкаф: последняя положенная вещь, вынутая из шкафа, или пустой стек. */
    public ItemStack takeLast() {
        if (dirty.isEmpty()) return ItemStack.EMPTY;
        setChanged();
        return dirty.remove(dirty.size() - 1);
    }

    public int dirtyCount() {
        return dirty.size();
    }

    /** Всё содержимое (при разрушении шкафа). */
    public List<ItemStack> contents() {
        List<ItemStack> all = new ArrayList<>(dirty);
        outfits.values().forEach(all::addAll);
        all.removeIf(ItemStack::isEmpty);
        return all;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag cells = new ListTag();
        for (Map.Entry<UUID, NonNullList<ItemStack>> e : outfits.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.putUUID("owner", e.getKey());
            ContainerHelper.saveAllItems(c, e.getValue(), registries);
            cells.add(c);
        }
        tag.put("outfits", cells);
        NonNullList<ItemStack> d = NonNullList.withSize(DIRTY_SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < dirty.size(); i++) d.set(i, dirty.get(i));
        CompoundTag dt = new CompoundTag();
        ContainerHelper.saveAllItems(dt, d, registries);
        dt.putInt("count", dirty.size());
        tag.put("dirty", dt);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outfits.clear();
        for (Tag t : tag.getList("outfits", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            NonNullList<ItemStack> cell = NonNullList.withSize(ARMOR.length, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(c, cell, registries);
            outfits.put(c.getUUID("owner"), cell);
        }
        dirty.clear();
        CompoundTag dt = tag.getCompound("dirty");
        NonNullList<ItemStack> d = NonNullList.withSize(DIRTY_SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(dt, d, registries);
        for (int i = 0; i < Math.min(dt.getInt("count"), DIRTY_SLOTS); i++) if (!d.get(i).isEmpty()) dirty.add(d.get(i));
    }
}
