package dev.denfry.promptcritical.mod.radiation;

import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.sim.radiation.PointSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Что лежит в защищённом узле: суммарный источник, остаточное тепло, активность и число предметов на момент {@code tick}. */
record ShieldedLoad(PointSource source, double heatW, double bq, int items) {
    static final ShieldedLoad EMPTY = new ShieldedLoad(PointSource.NONE, 0, 0, 0);

    static ShieldedLoad of(ItemStackHandler slots, long tick) {
        PointSource src = PointSource.NONE;
        double q = 0, a = 0;
        int n = 0;
        for (int i = 0; i < slots.getSlots(); i++) {
            ItemStack st = slots.getStackInSlot(i);
            ActivityData d = st.get(ActivityData.ACTIVITY.get());
            if (d == null) continue;
            var inv = d.inventory(tick);
            src = src.plus(inv.source().scaled(st.getCount()));
            q += inv.decayHeatW() * st.getCount();
            a += inv.totalBq() * st.getCount();
            n += st.getCount();
        }
        return new ShieldedLoad(src, q, a, n);
    }

    /** Слоты под облучённые предметы по одному в гнезде (ТВС, ампулы, блочки). */
    static ItemStackHandler slots(int n, Runnable changed) {
        return new ItemStackHandler(n) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return stack.has(ActivityData.ACTIVITY.get());
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            protected void onContentsChanged(int slot) {
                changed.run();
            }
        };
    }

    /** Положить в первые свободные гнёзда сколько влезет; возвращает, сколько положено. */
    static int insert(ItemStackHandler slots, ItemStack stack) {
        ItemStack left = stack.copy();
        for (int i = 0; i < slots.getSlots() && !left.isEmpty(); i++) left = slots.insertItem(i, left, false);
        return stack.getCount() - left.getCount();
    }

    /** Вынуть последний непустой; пусто — {@link ItemStack#EMPTY}. */
    static ItemStack takeLast(ItemStackHandler slots) {
        for (int i = slots.getSlots() - 1; i >= 0; i--) {
            ItemStack s = slots.extractItem(i, 64, false);
            if (!s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    static int count(ItemStackHandler slots) {
        int n = 0;
        for (int i = 0; i < slots.getSlots(); i++) n += slots.getStackInSlot(i).getCount();
        return n;
    }
}
