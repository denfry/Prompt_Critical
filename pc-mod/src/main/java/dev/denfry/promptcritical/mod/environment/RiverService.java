package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.sim.environment.RiverCatchment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Реки измерения: один водосбор {@link RiverCatchment} на измерение, шаг раз в {@link #STEP_TICKS} по погоде у точки
 * появления; хранится в сохранении мира. Расход в точке реки — удельный сток × площадь водосбора, которая растёт
 * вниз по течению; в ванильном мире течения нет, поэтому площадь задаёт детерминированный шум положения:
 * 20–400 км² у рек, 2–10 км² у прочей воды (ручьи, пруды) [BALANCE].
 */
public final class RiverService {
    static final int STEP_TICKS = 200;
    private static final String NAME = "promptcritical_rivers";

    private RiverService() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post e) -> {
            if (e.getLevel() instanceof ServerLevel l && l.getGameTime() % STEP_TICKS == 0) step(l);
        });
    }

    private static void step(ServerLevel level) {
        Data d = data(level);
        BlockPos at = level.getSharedSpawnPos();
        double airC = EnvironmentService.sample(level, at, 2).airTempC();
        d.catchment.step(STEP_TICKS, level.isRaining(), level.isThundering(), airC);
        d.setDirty();
    }

    public static RiverCatchment catchment(ServerLevel level) {
        return data(level).catchment;
    }

    /** Площадь водосбора у воды в {@code pos}, км². */
    public static double catchmentAreaKm2(ServerLevel level, BlockPos pos) {
        boolean river = level.getBiome(pos).is(BiomeTags.IS_RIVER);
        long h = level.getSeed() ^ (pos.getX() >> 6) * 0x9E3779B97F4A7C15L ^ (pos.getZ() >> 6) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double u = (h >>> 11) * 0x1.0p-53;
        return river ? 20 * Math.pow(20, u) : 2 + 8 * u;
    }

    /** Затвор поставлен или снят: реестр затворов измерения (турбины ищут водозабор по нему, а не перебором блоков). */
    public static void gatePlaced(ServerLevel level, BlockPos pos, boolean present) {
        Data d = data(level);
        if (present ? d.gates.add(pos.asLong()) : d.gates.remove(pos.asLong())) d.setDirty();
    }

    /** Затворы в кубе ±{@code radius} вокруг {@code center}. */
    public static java.util.List<BlockPos> gatesNear(ServerLevel level, BlockPos center, int radius) {
        java.util.List<BlockPos> out = new java.util.ArrayList<>();
        for (long g : data(level).gates) {
            BlockPos p = BlockPos.of(g);
            if (Math.abs(p.getX() - center.getX()) <= radius && Math.abs(p.getY() - center.getY()) <= radius
                    && Math.abs(p.getZ() - center.getZ()) <= radius) out.add(p);
        }
        return out;
    }

    /** Расход воды у {@code pos} сейчас, м³/с. */
    public static double dischargeM3s(ServerLevel level, BlockPos pos) {
        return catchment(level).dischargeM3s(catchmentAreaKm2(level, pos));
    }

    private static Data data(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
    }

    private static final class Data extends SavedData {
        final RiverCatchment catchment = new RiverCatchment();
        final java.util.Set<Long> gates = new java.util.TreeSet<>();

        static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data d = new Data();
            d.catchment.restore(tag.getDouble("snow_mm"), tag.getDouble("store_mm"));
            for (long g : tag.getLongArray("gates")) d.gates.add(g);
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putDouble("snow_mm", catchment.snowMm());
            tag.putDouble("store_mm", catchment.storeMm());
            tag.putLongArray("gates", gates.stream().mapToLong(Long::longValue).toArray());
            return tag;
        }
    }
}
