package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.sim.radiation.SurfaceContamination;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Загрязнение верхних граней блоков мира (пол, площадки, крышки аппаратов), Бк/см², снимаемые покрытия, нанесённые
 * на них (тик игры, когда плёнка высохнет), и отметки окислительной стадии дезактивации. Хранится в данных уровня,
 * только ненулевые блоки.
 */
public final class ContaminationMap extends SavedData {
    public static final String NAME = "promptcritical_contamination";
    public static final Factory<ContaminationMap> FACTORY = new Factory<>(ContaminationMap::new, ContaminationMap::load);

    private final Map<Long, SurfaceContamination> surfaces = new HashMap<>();
    /** Покрытие на блоке: игровой тик готовности плёнки к снятию. */
    private final Map<Long, Long> coatings = new HashMap<>();
    /** Блоки после окислительной стадии (щелочной перманганат): ждут кислоты. */
    private final java.util.Set<Long> oxidised = new java.util.HashSet<>();

    public static ContaminationMap of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public SurfaceContamination get(BlockPos pos) {
        return surfaces.getOrDefault(pos.asLong(), SurfaceContamination.NONE);
    }

    public void set(BlockPos pos, SurfaceContamination c) {
        long k = pos.asLong();
        if (c.negligible()) {
            if (surfaces.remove(k) != null) setDirty();
        } else {
            surfaces.put(k, c);
            setDirty();
        }
    }

    public void add(BlockPos pos, SurfaceContamination c) {
        set(pos, get(pos).plus(c));
    }

    /** Все загрязнённые блоки (ключ — {@link BlockPos#asLong()}), только чтение. */
    public Map<Long, SurfaceContamination> all() {
        return Collections.unmodifiableMap(surfaces);
    }

    public void coat(BlockPos pos, long readyTick) {
        coatings.put(pos.asLong(), readyTick);
        setDirty();
    }

    /** Тик готовности покрытия на блоке; {@code -1} — покрытия нет. */
    public long coating(BlockPos pos) {
        return coatings.getOrDefault(pos.asLong(), -1L);
    }

    public void removeCoating(BlockPos pos) {
        if (coatings.remove(pos.asLong()) != null) setDirty();
    }

    public boolean oxidised(BlockPos pos) {
        return oxidised.contains(pos.asLong());
    }

    public void setOxidised(BlockPos pos, boolean v) {
        if (v ? oxidised.add(pos.asLong()) : oxidised.remove(pos.asLong())) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        surfaces.forEach((k, c) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("p", k);
            e.putDouble("a", c.alphaLoose());
            e.putDouble("af", c.alphaFixed());
            e.putDouble("b", c.betaLoose());
            e.putDouble("bf", c.betaFixed());
            list.add(e);
        });
        tag.put("surfaces", list);
        ListTag coat = new ListTag();
        coatings.forEach((k, t) -> {
            CompoundTag e = new CompoundTag();
            e.putLong("p", k);
            e.putLong("t", t);
            coat.add(e);
        });
        tag.put("coatings", coat);
        tag.putLongArray("oxidised", oxidised.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static ContaminationMap load(CompoundTag tag, HolderLookup.Provider registries) {
        ContaminationMap m = new ContaminationMap();
        for (Tag t : tag.getList("surfaces", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            m.surfaces.put(e.getLong("p"), new SurfaceContamination(Math.max(0, e.getDouble("a")), Math.max(0, e.getDouble("af")),
                    Math.max(0, e.getDouble("b")), Math.max(0, e.getDouble("bf"))));
        }
        for (Tag t : tag.getList("coatings", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            m.coatings.put(e.getLong("p"), e.getLong("t"));
        }
        for (long k : tag.getLongArray("oxidised")) m.oxidised.add(k);
        return m;
    }
}
