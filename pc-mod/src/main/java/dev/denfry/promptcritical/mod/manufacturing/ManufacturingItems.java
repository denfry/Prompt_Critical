package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.PromptCritical;
import java.util.List;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Предметы ручной ступени машиностроения (18 §18.5, §18.7): поковка, кузнечный молот, первые детали. */
public final class ManufacturingItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(PromptCritical.MOD_ID);

    public static final DeferredItem<WorkpieceItem> WORKPIECE = ITEMS.register("workpiece", () -> new WorkpieceItem(new Item.Properties()));
    /** Ручник кузнеца: серия ударов изнашивает его на единицу [BALANCE: 250 серий]. */
    public static final DeferredItem<Item> SMITH_HAMMER = ITEMS.registerSimpleItem("smith_hammer", new Item.Properties().durability(250));
    /** Вал (18 §18.5) — деталь с паспортом {@code part_spec}. */
    public static final DeferredItem<Item> SHAFT = ITEMS.registerSimpleItem("shaft", new Item.Properties().stacksTo(16));

    /** Кузнечные клещи: с ними горячая поковка в инвентаре не жжёт руки (18 §18.7). */
    public static final DeferredItem<Item> TONGS = ITEMS.registerSimpleItem("tongs", new Item.Properties().stacksTo(1));
    /** Пруток или проволока с компонентом {@code wire} (18 §18.6.7). */
    public static final DeferredItem<WireItem> WIRE = ITEMS.register("wire", () -> new WireItem(new Item.Properties()));

    private ManufacturingItems() {
    }

    /** Для вкладки творческого режима. */
    public static List<DeferredItem<? extends Item>> all() {
        return List.of(SMITH_HAMMER, TONGS, WORKPIECE, WIRE, SHAFT);
    }
}
