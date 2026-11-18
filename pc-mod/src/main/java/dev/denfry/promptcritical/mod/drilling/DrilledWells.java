package dev.denfry.promptcritical.mod.drilling;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Журнал пробуренных стволов измерения: точка устья (X, Z), забой и пласт под ним. Буровая записывает ствол, пока
 * бурит; устьевое оборудование добывающей скважины ставят на ту же точку и заканчивают ствол обсадкой и
 * перфорацией. Ствол, на котором был открытый фонтан, не записывается: он потерян.
 */
public final class DrilledWells extends SavedData {
    /**
     * Пробуренный ствол.
     *
     * @param depthM   забой, м
     * @param prospect пласт под устьем; {@code null} — коллектора нет
     */
    public record Bore(double depthM, @Nullable Prospect prospect) {
    }

    private final Map<Long, Bore> bores = new HashMap<>();

    public static DrilledWells of(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(DrilledWells::new, DrilledWells::load, null),
                PromptCritical.MOD_ID + "_drilled_wells");
    }

    private static long key(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    @Nullable
    public Bore get(int x, int z) {
        return bores.get(key(x, z));
    }

    public void put(int x, int z, Bore bore) {
        Bore old = bores.put(key(x, z), bore);
        if (!bore.equals(old)) setDirty();
    }

    public void remove(int x, int z) {
        if (bores.remove(key(x, z)) != null) setDirty();
    }

    private static DrilledWells load(CompoundTag tag, HolderLookup.Provider registries) {
        DrilledWells d = new DrilledWells();
        for (Tag t : tag.getList("bores", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            Prospect p = null;
            if (c.contains("trap")) {
                long[] b = c.getLongArray("trap");
                if (b.length == 12) {
                    double[] a = new double[12];
                    for (int i = 0; i < 12; i++) a[i] = Double.longBitsToDouble(b[i]);
                    try {
                        p = new Prospect(new Trap((int) a[0], (int) a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8],
                                FormationFluid.values()[(int) a[9]], a[10]), a[11]);
                    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
                        p = null;
                    }
                }
            }
            d.bores.put(c.getLong("pos"), new Bore(c.getDouble("depth"), p));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        bores.forEach((k, b) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("pos", k);
            c.putDouble("depth", b.depthM());
            if (b.prospect() != null) {
                Trap t = b.prospect().trap();
                double[] a = {t.crestX(), t.crestZ(), t.radiusM(), t.crestDepthM(), t.closureM(), t.contactDepthM(), t.thicknessM(),
                        t.porosity(), t.permeabilityMd(), t.fluid().ordinal(), t.gradientPaM(), b.prospect().topM()};
                long[] bits = new long[a.length];
                for (int i = 0; i < a.length; i++) bits[i] = Double.doubleToRawLongBits(a[i]);
                c.putLongArray("trap", bits);
            }
            list.add(c);
        });
        tag.put("bores", list);
        return tag;
    }
}
