package dev.denfry.promptcritical.mod.boiler;

import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.item.CoalAssayData;
import dev.denfry.promptcritical.mod.registry.PcDataComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Твёрдое топливо котла (07 §7.9.1) по предмету: уголь с компонентом {@code coal_assay} — по анализу, остальное — по
 * типичному составу {@link PlantFuels}. Один предмет — {@code combustion.solid_fuel_item_mass_kg} (07 §7.0.2).
 */
public final class BoilerFuels {
    private BoilerFuels() {
    }

    /** Ключ топлива подсистемы; {@code null} — предмет не топливо котла. */
    @Nullable
    public static String key(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CoalAssayData assay = stack.get(PcDataComponents.COAL_ASSAY.get());
        if (assay != null) return PlantFuels.assayKey(assay.volatilePct(), assay.ashPct(), assay.moisturePct(), assay.sulfurPm());
        if (stack.is(Items.COAL)) return PlantFuels.COAL;
        if (stack.is(Items.CHARCOAL)) return PlantFuels.CHARCOAL;
        if (stack.is(PcItems.RAW_COAL.get())) return PlantFuels.RAW_COAL;
        if (stack.is(PcItems.WASHED_COAL.get())) return PlantFuels.WASHED_COAL;
        if (stack.is(PcItems.COAL_MIDDLINGS.get())) return PlantFuels.MIDDLINGS;
        if (stack.is(ItemTags.LOGS_THAT_BURN)) return PlantFuels.WOOD;
        return null;
    }

    /** Масса одного предмета топлива, кг. */
    public static double itemMassKg() {
        return ServerConfig.SPEC.isLoaded() ? ServerConfig.COMBUSTION_SOLID_FUEL_ITEM_MASS_KG.get() : 100;
    }
}
