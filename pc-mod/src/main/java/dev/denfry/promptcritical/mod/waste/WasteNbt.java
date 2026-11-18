package dev.denfry.promptcritical.mod.waste;

import dev.denfry.promptcritical.sim.radiation.ActivityInventory;
import dev.denfry.promptcritical.sim.radiation.Emitter;
import java.util.EnumMap;
import net.minecraft.nbt.CompoundTag;

/** Набор нуклидов в NBT по именам: масса, плотность, активности. */
final class WasteNbt {
    private WasteNbt() {
    }

    static CompoundTag activities(ActivityInventory inv) {
        CompoundTag t = new CompoundTag();
        t.putDouble("mass", inv.massKg());
        t.putDouble("density", inv.densityGcc());
        CompoundTag a = new CompoundTag();
        inv.activities().forEach((k, v) -> a.putDouble(k.name(), v));
        t.put("bq", a);
        return t;
    }

    static ActivityInventory activities(CompoundTag t) {
        EnumMap<Emitter, Double> m = new EnumMap<>(Emitter.class);
        CompoundTag a = t.getCompound("bq");
        for (String k : a.getAllKeys()) {
            try {
                m.put(Emitter.valueOf(k), a.getDouble(k));
            } catch (IllegalArgumentException ignored) {
                // нуклид из другой версии
            }
        }
        return new ActivityInventory(m, t.getDouble("mass"), t.contains("density") ? t.getDouble("density") : 1);
    }
}
