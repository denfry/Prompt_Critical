package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.registry.PcTags;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Экспорт продуктов мода в общие теги {@code c:} — чтобы другие моды (Create и аддоны, IE, Mekanism, TFMG и др.)
 * принимали нашу сталь, кокс, прокат и газы. Только экспорт: рецепты и машины мода по-прежнему требуют свои предметы,
 * так что чужие аналоги через эти теги в нашу цепочку не попадают.
 */
public final class CommonTags {
    private CommonTags() {
    }

    public static void addItemTags(Function<TagKey<Item>, IntrinsicTagAppender<Item>> tag) {
        item(tag, "ingots", "steel", PcItems.STEEL_INGOT);
        item(tag, "ingots", "pig_iron", PcItems.PIG_IRON);
        item(tag, "plates", "steel", PcItems.STEEL_PLATE);
        item(tag, "rods", "steel", PcItems.STEEL_ROD);
        item(tag, "wires", "copper", PcItems.COPPER_WIRE);
        item(tag, "dusts", "coal", PcItems.COAL_DUST);
        item(tag, "dusts", "salt", PcItems.SALT);
        tag.apply(PcTags.Items.common("coal_coke")).add(PcItems.COKE.get());
        tag.apply(PcTags.Items.common("slag")).add(PcItems.GRANULATED_SLAG.get(), PcItems.BOILER_SLAG.get());
    }

    /** {@code c:<group>/<material>} и родительский {@code c:<group>}. */
    private static void item(Function<TagKey<Item>, IntrinsicTagAppender<Item>> tag, String group, String material,
            DeferredItem<? extends Item> item) {
        TagKey<Item> specific = PcTags.Items.common(group + "/" + material);
        tag.apply(specific).add(item.get());
        tag.apply(PcTags.Items.common(group)).addTag(specific);
    }

    public static void addFluidTags(Function<TagKey<Fluid>, IntrinsicTagAppender<Fluid>> tag) {
        for (String gas : new String[] {"oxygen", "nitrogen", "argon"}) {
            PcFluids.Entry e = PcFluids.all().stream().filter(x -> x.name.equals(gas)).findFirst().orElseThrow();
            tag.apply(TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", gas))).add(e.source.get());
        }
    }
}
