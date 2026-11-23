package dev.denfry.promptcritical.mod.site;

import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Масса предмета, кг, для расчёта материалов машин по реальной массе прототипа (решение владельца 2026-11-23).
 * Сталь и прокат — по 08 §8.0.2: слиток, лист, балка — 100 кг; труба — 1 м средней линии (≈ 50 кг/м, Ø 219 × 10);
 * медный обмоточный провод — 10 кг; бетон, графит и кирпичная кладка — блок 1 м³ (2,4 т, 1,7 т и 1,85 т). Ванильные и Create-предметы — та же
 * условность «слиток 100 кг», пока у них нет своей строки в GDD [BALANCE].
 */
public final class ItemMass {
    private static final Map<Supplier<? extends Item>, Double> KG = Map.ofEntries(
            Map.entry(PcItems.STEEL_INGOT, 100.0), Map.entry(PcItems.STEEL_PLATE, 100.0), Map.entry(PcItems.STEEL_BEAM, 100.0),
            Map.entry(PcItems.STEEL_ROD, 100.0), Map.entry(PcItems.STEEL_BILLET, 100.0), Map.entry(PcItems.SEAMLESS_PIPE, 50.0),
            Map.entry(PcItems.WELDED_PIPE, 50.0), Map.entry(PcItems.COPPER_WIRE, 10.0), Map.entry(PcItems.COPPER_CATHODE, 100.0),
            Map.entry(PcItems.ZIRCALOY_INGOT, 100.0), Map.entry(() -> Items.GRAY_CONCRETE, 2400.0),
            Map.entry(() -> PcBlocks.MODERATOR_BLOCK_GRAPHITE.get().asItem(), 1700.0),
            Map.entry(() -> Items.IRON_INGOT, 100.0), Map.entry(() -> Items.BRICKS, 1850.0), Map.entry(() -> Items.COPPER_INGOT, 100.0), Map.entry(() -> Items.IRON_BLOCK, 900.0));
    private static volatile Map<Item, Double> byItem;

    private ItemMass() {}

    /** Масса одного предмета, кг; NaN — предмету масса не назначена. */
    public static double kg(Item item) {
        Map<Item, Double> m = byItem;
        if (m == null) {
            m = new HashMap<>();
            for (var e : KG.entrySet()) m.put(e.getKey().get(), e.getValue());
            byItem = m;
        }
        Double v = m.get(item);
        return v == null ? Double.NaN : v;
    }
}
