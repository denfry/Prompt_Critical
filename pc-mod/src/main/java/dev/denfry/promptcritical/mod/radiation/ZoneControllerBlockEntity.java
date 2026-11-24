package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.radiation.RadZone;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Контроллер радиационной зоны (04b §4b.12.1): помещение, в которое смотрит контроллер, — все клетки без коллизии,
 * связанные с клеткой перед ним (стены и двери, открытые или закрытые, его замыкают), не больше {@value #MAX_CELLS}.
 * Помещение получает класс; фактическая мощность дозы — среднее по стационарным блокам детектирования внутри (без
 * них — у самого контроллера) — сравнивается с проектной для класса. Объём пересчитывается раз в 5 с.
 */
public final class ZoneControllerBlockEntity extends BlockEntity {
    public static final int MAX_CELLS = 4096, RESCAN_TICKS = 100;
    private static final Map<Level, Set<ZoneControllerBlockEntity>> LOADED = new WeakHashMap<>();

    private RadZone zone = RadZone.CONTROLLED_SERVICED;
    private Set<Long> cells = Set.of();
    private boolean enclosed;
    private double meanSvH;
    private int instruments;
    private long nextScan;

    public ZoneControllerBlockEntity(BlockPos pos, BlockState state) {
        super(RadiationBlockEntities.ZONE_CONTROLLER.get(), pos, state);
    }

    /** Зона, которой принадлежит клетка {@code pos}, или {@code null}. */
    @Nullable
    public static ZoneControllerBlockEntity zoneAt(Level level, BlockPos pos) {
        Set<ZoneControllerBlockEntity> s = LOADED.get(level);
        if (s == null) return null;
        long key = pos.asLong();
        for (ZoneControllerBlockEntity z : s) {
            if (z.isRemoved()) continue;
            z.refresh();
            if (z.enclosed && z.cells.contains(key)) return z;
        }
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        Set<ZoneControllerBlockEntity> s = level == null ? null : LOADED.get(level);
        if (s != null) s.remove(this);
    }

    public RadZone zone() {
        return zone;
    }

    public boolean enclosed() {
        refresh();
        return enclosed;
    }

    public int volume() {
        refresh();
        return cells.size();
    }

    public double meanSvH() {
        refresh();
        return meanSvH;
    }

    public int instruments() {
        return instruments;
    }

    public boolean exceeded() {
        refresh();
        return enclosed && zone.exceeded(meanSvH);
    }

    public void cycleZone() {
        zone = zone.next();
        setChanged();
    }

    /** Пересчитать помещение и среднюю мощность дозы, если пора. */
    private void refresh() {
        if (!(level instanceof ServerLevel sl) || level.getGameTime() < nextScan) return;
        nextScan = level.getGameTime() + RESCAN_TICKS;
        Direction facing = getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos start = worldPosition.relative(facing);
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        boolean open = isOpen(sl, start);
        if (open) {
            seen.add(start.asLong());
            queue.add(start);
        }
        double sum = 0;
        int n = 0;
        while (!queue.isEmpty() && seen.size() <= MAX_CELLS) {
            BlockPos p = queue.poll();
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (seen.contains(q.asLong())) continue;
                if (sl.getBlockState(q).is(PcBlocks.INSTRUMENT_DOSE_RATE.get())) {
                    sum += SanitaryBlocks.DoseRateInstrument.readingSvH(sl, q);
                    n++;
                }
                if (!sl.isLoaded(q) || !isOpen(sl, q)) continue;
                seen.add(q.asLong());
                queue.add(q);
            }
        }
        enclosed = open && seen.size() <= MAX_CELLS;
        cells = enclosed ? Set.copyOf(seen) : Set.of();
        instruments = n;
        meanSvH = n > 0 ? sum / n : SanitaryBlocks.DoseRateInstrument.readingSvH(sl, start);
    }

    private static boolean isOpen(ServerLevel level, BlockPos p) {
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("zone", zone.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            zone = RadZone.valueOf(tag.getString("zone"));
        } catch (IllegalArgumentException e) {
            zone = RadZone.CONTROLLED_SERVICED;
        }
    }
}
