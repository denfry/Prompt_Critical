package dev.denfry.promptcritical.mod.ironworks;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.FoundryTags;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;

/** Теги коксохима и домны (08 §8.4–8.5). */
public final class IronworksTags {
    /** Шихта коксования (с {@code coal_assay} — по анализу, иначе — типичная Ж+К+ОС). */
    public static final TagKey<Item> COKING_COAL = mod("coking_coal");
    /** Твёрдое топливо топок разогрева батареи. */
    public static final TagKey<Item> FIREBOX_FUEL = mod("firebox_fuel");
    /** Железосодержащее домны: агломерат и кусковая руда. */
    public static final TagKey<Item> FURNACE_ORE = mod("furnace_ore");
    /** Железосодержащее агломашины: концентрат и руда. */
    public static final TagKey<Item> SINTER_ORE = mod("sinter_ore");
    /** Флюс: известняк Create, кальцит. */
    public static final TagKey<Item> FLUX = mod("flux");
    /** Реагенты улавливания из других модов и будущих вех (02, 09). */
    public static final TagKey<Fluid> SULFURIC_ACID = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "sulfuric_acid"));
    public static final TagKey<Fluid> WASH_OIL = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "wash_oil"));

    private IronworksTags() {
    }

    public static void addItemTags(Function<TagKey<Item>, IntrinsicHolderTagsProvider.IntrinsicTagAppender<Item>> tag) {
        tag.apply(COKING_COAL).add(PcItems.WASHED_COAL.get(), PcItems.RAW_COAL.get(), Items.COAL);
        tag.apply(FIREBOX_FUEL).add(PcItems.RAW_COAL.get(), PcItems.WASHED_COAL.get(), PcItems.COAL_MIDDLINGS.get(),
                PcItems.COKE.get(), PcItems.COKE_BREEZE.get(), Items.COAL, Items.CHARCOAL);
        tag.apply(FURNACE_ORE).add(PcItems.SINTER.get(), PcItems.RAW_HEMATITE.get(), PcItems.RAW_MAGNETITE.get());
        tag.apply(SINTER_ORE).add(PcItems.IRON_ORE_CONCENTRATE.get(), PcItems.RAW_HEMATITE.get(), PcItems.RAW_MAGNETITE.get());
        tag.apply(FLUX).add(Items.CALCITE).addOptional(ResourceLocation.fromNamespaceAndPath("create", "limestone"));
        tag.apply(FoundryTags.CUPOLA_COKE).add(PcItems.COKE.get());
    }

    private static TagKey<Item> mod(String path) {
        return TagKey.create(Registries.ITEM, PromptCritical.id(path));
    }
}
