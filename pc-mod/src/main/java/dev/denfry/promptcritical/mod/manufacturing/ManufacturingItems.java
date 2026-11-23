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

    /** Лист электротехнической стали 1,0 × 0,5 м с компонентом {@code electrical_sheet} (18 §18.6.8). */
    public static final DeferredItem<Item> ELECTRICAL_SHEET = ITEMS.registerSimpleItem("electrical_sheet", new Item.Properties());
    /** Пачка пластин статора — столько, сколько вырубается из листа (18 §18.6.8). */
    public static final DeferredItem<Item> LAMINATION = ITEMS.registerSimpleItem("lamination", new Item.Properties());
    /** Шихтованный пакет статора (18 §18.6.8). */
    public static final DeferredItem<Item> STATOR_CORE = ITEMS.registerSimpleItem("stator_core", new Item.Properties().stacksTo(1));
    /** Намотанный статор эталонного двигателя 5,5 кВт (18 §18.6.9). */
    public static final DeferredItem<Item> STATOR = ITEMS.registerSimpleItem("stator", new Item.Properties().stacksTo(1));

    private ManufacturingItems() {
    }

    /** Для вкладки творческого режима. */
    public static List<DeferredItem<? extends Item>> all() {
        return List.of(SMITH_HAMMER, TONGS, WORKPIECE, WIRE, SHAFT, ELECTRICAL_SHEET, LAMINATION, STATOR_CORE, STATOR);
    }
}
