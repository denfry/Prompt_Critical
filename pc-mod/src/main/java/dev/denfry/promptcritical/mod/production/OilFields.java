package dev.denfry.promptcritical.mod.production;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.production.ProducingReservoir;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Залежи измерения (09 §9.4, §9.4.6): одна ловушка — одно состояние «бака» ({@link ProducingReservoir.State}) для всех
 * скважин на ней. Добывающие тянут давление вниз, нагнетательная поднимает его всем. Ключ — свод ловушки.
 *
 * <p>Журнал помнит устья, вскрывшие залежь. Залежь, у которой не осталось ни одного законченного устья (все сняты),
 * забывается при следующей перфорации: так было и до общего «бака», когда состояние жило в устье [допущение; иначе
 * миры испытаний, которые сохраняются между прогонами, наследовали бы чужую выработку].
 */
public final class OilFields extends SavedData {
    private final Map<Long, ProducingReservoir.State> fields = new HashMap<>();
    private final Map<Long, java.util.Set<Long>> wells = new HashMap<>();

    public static OilFields of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(OilFields::new, OilFields::load, null),
                PromptCritical.MOD_ID + "_oil_fields");
    }

    private static long key(Trap t) {
        return (long) t.crestX() << 32 | (t.crestZ() & 0xFFFFFFFFL);
    }

    /** Состояние залежи ловушки; {@code seed} — сохранённое состояние скважины для залежи, которой ещё нет в журнале. */
    public ProducingReservoir.State state(Trap trap, double[] seed) {
        ProducingReservoir.State s = fields.get(key(trap));
        if (s == null) {
            s = ProducingReservoir.State.restore(seed);
            fields.put(key(trap), s);
            setDirty();
        }
        return s;
    }

    /** Состояние для устья {@code pos}; новая перфорация {@code fresh} при залежи без живых устьев начинает её заново. */
    public ProducingReservoir.State attach(ServerLevel level, Trap trap, net.minecraft.core.BlockPos pos, boolean fresh, double[] seed) {
        long k = key(trap);
        java.util.Set<Long> set = wells.computeIfAbsent(k, x -> new java.util.HashSet<>());
        set.removeIf(p -> {
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(p);
            return !bp.equals(pos) && level.isLoaded(bp) && !(level.getBlockEntity(bp) instanceof WellheadBlockEntity w && w.well() != null);
        });
        set.remove(pos.asLong());
        if (fresh && set.isEmpty() && fields.remove(k) != null) setDirty();
        set.add(pos.asLong());
        setDirty();
        return state(trap, seed);
    }

    private static OilFields load(CompoundTag tag, HolderLookup.Provider registries) {
        OilFields f = new OilFields();
        for (Tag t : tag.getList("fields", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            long[] b = c.getLongArray("state");
            double[] a = new double[b.length];
            for (int i = 0; i < b.length; i++) a[i] = Double.longBitsToDouble(b[i]);
            f.fields.put(c.getLong("crest"), ProducingReservoir.State.restore(a));
            java.util.Set<Long> set = new java.util.HashSet<>();
            for (long w : c.getLongArray("wells")) set.add(w);
            f.wells.put(c.getLong("crest"), set);
        }
        return f;
    }

    @Override
    public boolean isDirty() {
        // состояние меняется каждым шагом скважин: сохраняется вместе с миром
        return !fields.isEmpty() || super.isDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        fields.forEach((k, s) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("crest", k);
            double[] a = s.export();
            long[] bits = new long[a.length];
            for (int i = 0; i < a.length; i++) bits[i] = Double.doubleToRawLongBits(a[i]);
            c.putLongArray("state", bits);
            c.putLongArray("wells", wells.getOrDefault(k, java.util.Set.of()).stream().mapToLong(Long::longValue).toArray());
            list.add(c);
        });
        tag.put("fields", list);
        return tag;
    }
}
