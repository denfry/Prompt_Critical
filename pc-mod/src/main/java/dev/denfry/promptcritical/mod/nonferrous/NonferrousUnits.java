package dev.denfry.promptcritical.mod.nonferrous;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.item.OreAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import net.minecraft.world.item.ItemStack;

/**
 * Единицы цветной металлургии (08 §8.0.2): сыпучие ({@code raw_*}, концентраты, глинозём, гидроксид, криолит, кокс,
 * аноды) — {@code metallurgy.bulk_item_mass_kg}; слитки и катоды ({@code aluminium_ingot}, {@code copper_cathode},
 * {@code nickel_ingot}, черновая и анодная медь, штейн) — {@code metallurgy.ingot_mass_kg}; {@code cobalt_ingot} — 1 кг,
 * {@code platinum_ingot} — 100 г, {@code iridium_ingot} — 10 г (§8.0.2). Олово [решение]: оловянный концентрат
 * {@code tin_dust} и {@code tin_ingot} — по {@value #TIN_ITEM_KG} кг, концентрат {@value #TIN_DUST_SN} Sn [BALANCE:
 * гравитационные концентраты 65–75 %]. Жидкости 1 мБ = 1 л, газы — 1 мБ = 1 нм³.
 */
public final class NonferrousUnits {
    public static final double COBALT_INGOT_KG = 1, PLATINUM_INGOT_KG = 0.1, IRIDIUM_INGOT_KG = 0.01;
    public static final double TIN_ITEM_KG = 10, TIN_DUST_SN = 0.70;
    /** Электролитическая щёлочь хлорного цеха: 11 % NaOH, 1,24 кг/л (как {@code ChlorUnits}). */
    public static final double CAUSTIC_NAOH_KG_L = 0.11 * 1.24;
    /** Топливный газ (природный), Дж/нм³; КПД своей котельной пара автоклава. */
    public static final double FUEL_J_NM3 = 36e6, BOILER_EFFICIENCY = 0.9;
    public static final double OXYGEN_KG_NM3 = 1.429, ACID_KG_L = 1.84;

    private NonferrousUnits() {
    }

    public static double bulkKg() {
        return ServerConfig.METALLURGY_BULK_ITEM_MASS_KG.get();
    }

    public static double ingotKg() {
        return ServerConfig.METALLURGY_INGOT_MASS_KG.get();
    }

    /** Содержание по пробе руды или значение по умолчанию. */
    public static double assay(ItemStack s, double fallback) {
        OreAssayData a = s.get(PcDataComponents.ORE_ASSAY.get());
        if (a == null) return fallback;
        double f = a.toAssay().gradeFrac();
        return f > 0 ? f : fallback;
    }
}
