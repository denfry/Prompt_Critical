package dev.denfry.promptcritical.mod.market;

import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.economy.Commodity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Белый список товаров биржи (14 §14.6.1). Ванильные блоки и «фермерский» товар на биржу не допускаются — только
 * продукты заводов мода. Базовые цены и глубины — стартовые значения [BALANCE]: 14 оставляет их датапаку
 * {@code market/commodities}, который ещё не сделан. Эластичность по §14.6.3: массовое сырьё и металл ε = 2–4 с
 * большой D, редкие продукты ε = 0,5.
 */
public final class MarketCommodities {
    private record Entry(Supplier<? extends Item> item, Commodity commodity) {
    }

    private static final List<Entry> ENTRIES = List.of(
            entry(PcItems.COKE, "coke", "t", 120_00, 0, 64, 3),
            entry(PcItems.STEEL_INGOT, "steel_ingot", "pcs", 25_00, 0, 128, 3),
            entry(PcItems.STEEL_PLATE, "steel_plate", "pcs", 30_00, 0, 128, 3),
            entry(PcItems.BRONZE_INGOT, "bronze_ingot", "pcs", 20_00, 0, 64, 2),
            entry(PcItems.POLYETHYLENE, "polyethylene", "pcs", 40_00, 0, 64, 2),
            entry(PcItems.COBALT_INGOT, "cobalt_ingot", "pcs", 300_00, 0, 8, 0.5),
            entry(PcItems.IRIDIUM_INGOT, "iridium_ingot", "pcs", 900_00, 0, 4, 0.5));

    private static Map<Item, Commodity> byItem;

    private MarketCommodities() {
    }

    private static Entry entry(Supplier<? extends Item> item, String id, String unit, long baseCents, int minEpoch,
                               double depth, double elasticity) {
        return new Entry(item, Commodity.standard(id, unit, baseCents, minEpoch, depth, elasticity, null));
    }

    public static List<Commodity> all() {
        return ENTRIES.stream().map(Entry::commodity).toList();
    }

    @Nullable
    public static Commodity of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (byItem == null) {
            Map<Item, Commodity> m = new LinkedHashMap<>();
            for (Entry e : ENTRIES) m.put(e.item().get(), e.commodity());
            byItem = m;
        }
        return byItem.get(stack.getItem());
    }
}
