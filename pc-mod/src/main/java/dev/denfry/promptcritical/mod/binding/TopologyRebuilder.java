package dev.denfry.promptcritical.mod.binding;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Инкрементальная пересборка топологии (05 §5.3.3): изменения блоков мода копятся в наборе «грязных» позиций, в
 * начале следующего тика слушатели (контроллеры мультиблоков, сети) получают те, что попали в их
 * параллелепипед + 1 блок. Лимит — {@code perf.topology_budget_us} на тик, остаток — в следующий; повторная
 * валидация одного слушателя — не чаще раза в 10 тиков (дебаунс).
 */
public final class TopologyRebuilder {
    public static final int DEBOUNCE_TICKS = 10;

    /** Подписчик изменений: мультиблок, гидросеть, шахта. */
    public interface Listener {
        /** Параллелепипед объекта; уведомление приходит для позиций в нём, расширенном на 1 блок. */
        BoundingBox bounds();

        /** Пересобрать объект: позиции в его области изменились. Серверный поток, между тиками домена. */
        void revalidate();
    }

    private static final class Entry {
        final Listener listener;
        long lastRun = Long.MIN_VALUE / 2;
        boolean due;

        Entry(Listener listener) {
            this.listener = listener;
        }
    }

    private final LongOpenHashSet dirtySet = new LongOpenHashSet();
    private final LongArrayFIFOQueue dirtyQueue = new LongArrayFIFOQueue();
    private final List<Entry> listeners = new ArrayList<>();
    private long processedTotal;

    public void markDirty(BlockPos pos) {
        long key = pos.asLong();
        if (dirtySet.add(key)) dirtyQueue.enqueue(key);
    }

    public void addListener(Listener l) {
        listeners.add(new Entry(l));
    }

    public void removeListener(Listener l) {
        listeners.removeIf(e -> e.listener == l);
    }

    public int pending() {
        return dirtyQueue.size();
    }

    public long processedTotal() {
        return processedTotal;
    }

    /**
     * Обработать грязные позиции в пределах бюджета и запустить созревшие пересборки.
     *
     * @param gameTick  тик уровня (для дебаунса)
     * @param budgetNs  бюджет на тик, нс
     */
    public void process(long gameTick, long budgetNs) {
        long start = System.nanoTime();
        while (!dirtyQueue.isEmpty() && System.nanoTime() - start < budgetNs) {
            long key = dirtyQueue.dequeueLong();
            dirtySet.remove(key);
            processedTotal++;
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            for (Entry e : listeners) {
                BoundingBox b = e.listener.bounds();
                if (x >= b.minX() - 1 && x <= b.maxX() + 1 && y >= b.minY() - 1 && y <= b.maxY() + 1
                        && z >= b.minZ() - 1 && z <= b.maxZ() + 1) {
                    e.due = true;
                }
            }
        }
        for (Entry e : listeners) {
            if (!e.due || gameTick - e.lastRun < DEBOUNCE_TICKS) continue;
            if (System.nanoTime() - start >= budgetNs) break;
            e.due = false;
            e.lastRun = gameTick;
            e.listener.revalidate();
        }
    }
}
