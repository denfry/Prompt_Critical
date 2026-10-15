package dev.denfry.promptcritical.mod.foundry;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcTags;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Теги предметов литейки, цеха и углеобогащения (08 §8.3, §8.9). */
public final class FoundryTags {
    /** Металлическая шихта вагранки: {@code pig_iron}, железо (до домны — ванильное, 08 §8.9). */
    public static final TagKey<Item> CUPOLA_IRON = mod("cupola_iron");
    /** Кокс вагранки; до коксохима (M3) — древесный уголь [допущение]. */
    public static final TagKey<Item> CUPOLA_COKE = mod("cupola_coke");
    /** Твёрдое топливо горна тигельной печи. */
    public static final TagKey<Item> FURNACE_COAL = mod("furnace_coal");
    /** Питание отсадочной машины. */
    public static final TagKey<Item> JIG_FEED = mod("jig_feed");
    public static final TagKey<Item> COPPER_INGOTS = PcTags.Items.common("ingots/copper");
    public static final TagKey<Item> BRONZE_INGOTS = PcTags.Items.common("ingots/bronze");
    public static final TagKey<Item> ALUMINUM_INGOTS = PcTags.Items.common("ingots/aluminum");
    public static final TagKey<Item> IRON_INGOTS = PcTags.Items.common("ingots/iron");
    public static final TagKey<Item> COAL_COKE = PcTags.Items.common("coal_coke");

    private FoundryTags() {
    }

    /** Наполнение тегов для datagen ({@code ItemTagsProvider#tag}). */
    public static void addItemTags(Function<TagKey<Item>, IntrinsicHolderTagsProvider.IntrinsicTagAppender<Item>> tag) {
        tag.apply(CUPOLA_IRON).add(PcItems.PIG_IRON.get()).addTag(IRON_INGOTS);
        tag.apply(CUPOLA_COKE).add(Items.CHARCOAL).addOptionalTag(COAL_COKE);
        tag.apply(FURNACE_COAL).add(PcItems.RAW_COAL.get(), PcItems.WASHED_COAL.get(), PcItems.COAL_MIDDLINGS.get(),
                Items.COAL, Items.CHARCOAL);
        tag.apply(JIG_FEED).add(PcItems.RAW_COAL.get());
    }

    private static TagKey<Item> mod(String path) {
        return TagKey.create(Registries.ITEM, PromptCritical.id(path));
    }
}
