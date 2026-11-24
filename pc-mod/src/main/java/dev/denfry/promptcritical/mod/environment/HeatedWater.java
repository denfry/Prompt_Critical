package dev.denfry.promptcritical.mod.environment;

import dev.denfry.promptcritical.mod.block.PcBlock;
import dev.denfry.promptcritical.sim.thermal.CoolingPond;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import org.jetbrains.annotations.Nullable;

/**
 * Сбросное тепло в водоёмах (07 §7.13.4): конденсатор, охлаждаемый рекой или прудом, отдаёт тепло воде, и вода этого
 * водоёма теплеет. Пруд — по площади зеркала (заливка по поверхности, 1 блок — 1 м²) и теплоотдаче K·F; река — по
 * расходу {@link CoolingPond#RIVER_FLOW_M3S}; водоём больше {@value #MAX_AREA} м² или океан считается бесконечным.
 * Сбросы забываются, если их не обновляли {@value #STALE_TICKS} тиков (турбина встала или выгружена).
 */
public final class HeatedWater {
    public static final int MAX_AREA = 65_536, RESCAN_TICKS = 600, STALE_TICKS = 100;

    /** Водоём: ключ (наименьшая клетка зеркала), площадь, река ли. */
    record Body(long key, int areaM2, boolean river) {
    }

    private record Discharge(Body body, double heatW, long tick) {
    }

    private record CachedBody(@Nullable Body body, long tick) {
    }

    private static final Map<ServerLevel, Map<Long, Discharge>> DISCHARGES = new WeakHashMap<>();
    private static final Map<ServerLevel, Map<Long, CachedBody>> BODIES = new WeakHashMap<>();

    private HeatedWater() {
    }

    /** Водоём у {@code waterPos} (кэш на {@value #RESCAN_TICKS} тиков); {@code null} — воды нет или водоём бесконечный. */
    @Nullable
    static Body body(ServerLevel sl, BlockPos waterPos) {
        Map<Long, CachedBody> cache = BODIES.computeIfAbsent(sl, l -> new HashMap<>());
        long now = sl.getGameTime();
        CachedBody c = cache.get(waterPos.asLong());
        if (c != null && now - c.tick() < RESCAN_TICKS) return c.body();
        Body b = scan(sl, waterPos);
        cache.put(waterPos.asLong(), new CachedBody(b, now));
        return b;
    }

    @Nullable
    private static Body scan(ServerLevel sl, BlockPos at) {
        BlockPos s = WaterBodies.surface(sl, at);
        if (s == null || sl.getBiome(s).is(BiomeTags.IS_OCEAN)) return null;
        boolean river = sl.getBiome(s).is(BiomeTags.IS_RIVER);
        int y = s.getY();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(s);
        seen.add(s.asLong());
        long key = s.asLong();
        while (!q.isEmpty()) {
            BlockPos p = q.poll();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = p.relative(d);
                if (seen.contains(n.asLong())) continue;
                if (!sl.isLoaded(n)) return null;
                if (!sl.getFluidState(n).is(FluidTags.WATER) || sl.getBlockState(n).getBlock() instanceof PcBlock) continue;
                seen.add(n.asLong());
                if (seen.size() > MAX_AREA) return null;
                key = Math.min(key, new BlockPos(n.getX(), y, n.getZ()).asLong());
                q.add(n);
            }
        }
        return new Body(key, seen.size(), river);
    }

    /**
     * Конденсатор {@code condenser} сбрасывает {@code heatW} в воду у {@code waterPos}; вызывать каждый тик работы.
     *
     * @return температура воды у водозабора с учётом всех сбросов в этот водоём, °C
     */
    public static double discharge(ServerLevel sl, BlockPos condenser, BlockPos waterPos, double heatW) {
        Body b = body(sl, waterPos);
        if (b != null) DISCHARGES.computeIfAbsent(sl, l -> new HashMap<>())
                .put(condenser.asLong(), new Discharge(b, Math.max(0, heatW), sl.getGameTime()));
        return waterTempC(sl, waterPos);
    }

    /** Перегрев водоёма у {@code waterPos} сбросным теплом, К. */
    public static double riseK(ServerLevel sl, BlockPos waterPos) {
        Body b = body(sl, waterPos);
        if (b == null) return 0;
        Map<Long, Discharge> m = DISCHARGES.get(sl);
        if (m == null) return 0;
        long now = sl.getGameTime();
        double heat = 0;
        var it = m.values().iterator();
        while (it.hasNext()) {
            Discharge d = it.next();
            if (now - d.tick() > STALE_TICKS) {
                it.remove();
                continue;
            }
            if (d.body().key() == b.key()) heat += d.heatW();
        }
        return b.river() ? CoolingPond.riverRiseK(heat, CoolingPond.RIVER_FLOW_M3S) : CoolingPond.pondRiseK(heat, b.areaM2());
    }

    /** Температура воды у {@code waterPos}: природная плюс перегрев сбросным теплом, °C. */
    public static double waterTempC(ServerLevel sl, BlockPos waterPos) {
        return EnvironmentService.waterTempC(sl, waterPos) + riseK(sl, waterPos);
    }
}
