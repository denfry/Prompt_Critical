package dev.denfry.promptcritical.mod.binding;

import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.SimulationWorld;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * {@code SavedData} измерения {@code promptcritical_sim} (05 §5.2.8): файл {@code data/promptcritical_sim.dat} в папке
 * измерения. Хранит сохранение домена; при записи мира берёт свежий срез у привязанного {@link SimulationWorld}.
 */
public final class SimSavedData extends SavedData {
    public static final String NAME = "promptcritical_sim";
    public static final Factory<SimSavedData> FACTORY = new Factory<>(SimSavedData::new, SimSavedData::load);

    @Nullable
    private MutableStateTree loaded;
    @Nullable
    private SimulationWorld world;

    private SimSavedData() {
    }

    private static SimSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        SimSavedData d = new SimSavedData();
        d.loaded = NbtStateTrees.fromNbt(tag);
        return d;
    }

    /** Сохранение с диска (до миграций) — один раз, при создании мира симуляции. */
    @Nullable
    MutableStateTree takeLoaded() {
        MutableStateTree t = loaded;
        loaded = null;
        return t;
    }

    void bind(SimulationWorld world) {
        this.world = world;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (world == null) return tag;
        CompoundTag state = NbtStateTrees.toNbt(world.saveTree());
        for (String k : state.getAllKeys()) tag.put(k, state.get(k));
        return tag;
    }
}
