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

    /** Детали сборки машин (18 §18.5, MF5); путь изготовления у каждой — в справочнике i0/assembly. */
    public static final DeferredItem<Item> ROLLING_BEARING = ITEMS.registerSimpleItem("rolling_bearing", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> COUPLING = ITEMS.registerSimpleItem("coupling", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> FASTENER_KIT = ITEMS.registerSimpleItem("fastener_kit", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> SPRING = ITEMS.registerSimpleItem("spring", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> PULLEY = ITEMS.registerSimpleItem("pulley", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> MACHINE_BED = ITEMS.registerSimpleItem("machine_bed", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PUMP_CASING = ITEMS.registerSimpleItem("pump_casing", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> VALVE_BODY = ITEMS.registerSimpleItem("valve_body", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> FLANGE = ITEMS.registerSimpleItem("flange", new Item.Properties().stacksTo(16));
    public static final DeferredItem<Item> END_COVER = ITEMS.registerSimpleItem("end_cover", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> ROTOR_CORE = ITEMS.registerSimpleItem("rotor_core", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> FIELD_COIL = ITEMS.registerSimpleItem("field_coil", new Item.Properties().stacksTo(8));
    // детали машин I0, переведённых на сборку (веха MF5)
    public static final DeferredItem<Item> FAN_WHEEL = ITEMS.registerSimpleItem("fan_wheel", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> ROPE_DRUM = ITEMS.registerSimpleItem("rope_drum", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BRAKE_SHOE = ITEMS.registerSimpleItem("brake_shoe", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> CYLINDER_BLOCK = ITEMS.registerSimpleItem("cylinder_block", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> PISTON = ITEMS.registerSimpleItem("piston", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> CONNECTING_ROD = ITEMS.registerSimpleItem("connecting_rod", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> CROSSHEAD = ITEMS.registerSimpleItem("crosshead", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> FLYWHEEL_RIM = ITEMS.registerSimpleItem("flywheel_rim", new Item.Properties().stacksTo(4));
    public static final DeferredItem<Item> GOVERNOR = ITEMS.registerSimpleItem("governor", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> COMMUTATOR = ITEMS.registerSimpleItem("commutator", new Item.Properties().stacksTo(1));
    public static final DeferredItem<Item> BRUSH_HOLDER = ITEMS.registerSimpleItem("brush_holder", new Item.Properties().stacksTo(8));
    public static final DeferredItem<Item> SLIP_RING = ITEMS.registerSimpleItem("slip_ring", new Item.Properties().stacksTo(4));

    private ManufacturingItems() {
    }

    /** Для вкладки творческого режима. */
    public static List<DeferredItem<? extends Item>> all() {
        return List.of(SMITH_HAMMER, TONGS, WORKPIECE, WIRE, SHAFT, ELECTRICAL_SHEET, LAMINATION, STATOR_CORE, STATOR,
                ROLLING_BEARING, COUPLING, FASTENER_KIT, SPRING, PULLEY, MACHINE_BED, PUMP_CASING, VALVE_BODY, FLANGE, END_COVER, ROTOR_CORE, FIELD_COIL,
                FAN_WHEEL, ROPE_DRUM, BRAKE_SHOE, CYLINDER_BLOCK, PISTON, CONNECTING_ROD, CROSSHEAD, FLYWHEEL_RIM, GOVERNOR, COMMUTATOR, BRUSH_HOLDER, SLIP_RING);
    }
}
