package dev.denfry.promptcritical.mod.manufacturing;

import com.mojang.serialization.DynamicOps;
import dev.denfry.promptcritical.sim.manufacturing.assembly.MachinePassport;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

/**
 * Паспорт машины на блок-сущности (18 §18.15.7): читается из предмета при установке, хранится в NBT, возвращается в
 * предмет при сломе. Номинальный паспорт в NBT не пишется, поэтому старые миры и машины из креатива не меняются.
 */
public final class PassportSlot {
    private static final String KEY = "machine_passport";
    private MachinePassport passport = MachinePassport.nominal();

    public MachinePassport get() {
        return passport;
    }

    public void set(MachinePassport p) {
        passport = p == null ? MachinePassport.nominal() : p;
    }

    /** В {@code BlockEntity.collectImplicitComponents}: только не номинальный, чтобы стаки не делились. */
    public void collect(DataComponentMap.Builder builder) {
        if (!passport.equals(MachinePassport.nominal())) builder.set(ManufacturingComponents.MACHINE_PASSPORT.get(), passport);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (passport.equals(MachinePassport.nominal())) return;
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ManufacturingComponents.MACHINE_PASSPORT_CODEC.encodeStart(ops, passport).result().ifPresent(t -> tag.put(KEY, t));
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(KEY)) {
            passport = MachinePassport.nominal();
            return;
        }
        DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        passport = ManufacturingComponents.MACHINE_PASSPORT_CODEC.parse(ops, tag.get(KEY)).result()
                .orElse(MachinePassport.nominal());
    }

    /** Блок-сущность, которая несёт паспорт машины. */
    public interface Holder {
        PassportSlot passportSlot();
    }
}
