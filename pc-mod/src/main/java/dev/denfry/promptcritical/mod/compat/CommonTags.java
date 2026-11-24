package dev.denfry.promptcritical.mod.compat;

import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.registry.PcTags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
        item(tag, "ingots", "bronze", PcItems.BRONZE_INGOT);
        item(tag, "plates", "steel", PcItems.STEEL_PLATE);
        item(tag, "rods", "steel", PcItems.STEEL_ROD);
        item(tag, "wires", "copper", PcItems.COPPER_WIRE);
        item(tag, "dusts", "coal", PcItems.COAL_DUST);
        item(tag, "dusts", "salt", PcItems.SALT);
        tag.apply(PcTags.Items.common("coal_coke")).add(PcItems.COKE.get());
        tag.apply(PcTags.Items.common("slag")).add(PcItems.GRANULATED_SLAG.get(), PcItems.BOILER_SLAG.get());
        // 16 §16.10.1, R60: при Tough As Nails температурой владеет он, костюм с подогревом греет по его тегу
        tag.apply(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("toughasnails", "heating_armor")))
                .add(PcItems.HEATED_SUIT.get());
        // 16 §16.9: слоты снаряжения Curios и Accessories назначаются тегом, код читает их через WornGear
        slot(tag, "curios", "belt", PcItems.DOSIMETER.get(), PcItems.PATROL_READER.get());
        slot(tag, "accessories", "belt", PcItems.DOSIMETER.get(), PcItems.PATROL_READER.get());
        slot(tag, "curios", "head", PcItems.CAP_LAMP.get());
        slot(tag, "accessories", "hat", PcItems.CAP_LAMP.get());
        slot(tag, "accessories", "face", PcItems.GAS_MASK.get());
    }

    private static void slot(Function<TagKey<Item>, IntrinsicTagAppender<Item>> tag, String mod, String slot, Item... items) {
        tag.apply(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(mod, slot))).add(items);
    }

    /** {@code c:<group>/<material>} и родительский {@code c:<group>}. */
    private static void item(Function<TagKey<Item>, IntrinsicTagAppender<Item>> tag, String group, String material,
            DeferredItem<? extends Item> item) {
        TagKey<Item> specific = PcTags.Items.common(group + "/" + material);
        tag.apply(specific).add(item.get());
        tag.apply(PcTags.Items.common(group)).addTag(specific);
    }

    /**
     * Вывоз сред по тегам {@code c:} (00 §0.7.5, 16 §16.7.2–§16.7.3, R61): машины других модов берут нашу товарную
     * химию и топлива сами. Ввоз — никогда: наши аппараты принимают среду по идентификатору. Где имена тегов у модов
     * расходятся ({@code c:fuel_oil} и {@code c:heavy_oil}, {@code c:ethylene} и {@code c:ethene}), среда получает оба.
     */
    public static final Map<String, List<String>> FLUID_EXPORT = fluidExport();

    /**
     * Закрытый список (16 §16.7.2): ядерные и околоядерные среды, вода и пар контуров. Тегов {@code c:} у них нет,
     * иначе тяжёлая вода Mekanism, сделанная насосом из обычной, отменила бы цепочку T3.
     */
    public static final Set<String> NEVER_EXPORTED = Set.of("heavy_water", "downgraded_heavy_water",
            "deuterium_enriched_water", "pregnant_leach_solution", "raffinate", "uranium_tailings", "uranium_eluate",
            "liquid_radioactive_waste", "contaminated_decon_solution", "hydrogen_fluoride", "fluorine", "raw_water",
            "clarified_water", "filtered_water", "softened_water", "boiler_feedwater");

    /** Части имён тегов, которые не выдаются никогда: ровно те, что совпали бы с ядерными тегами Mekanism. */
    public static final List<String> FORBIDDEN_TAG_WORDS = List.of("uranium", "plutonium", "thorium", "heavy_water",
            "deuterium", "tritium", "lithium", "sodium", "steam", "fluorine", "fluoride");

    private static Map<String, List<String>> fluidExport() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("oxygen", List.of("oxygen"));
        m.put("nitrogen", List.of("nitrogen"));
        m.put("argon", List.of("argon"));
        m.put("helium", List.of("helium"));
        m.put("hydrogen", List.of("hydrogen"));
        m.put("chlorine", List.of("chlorine"));
        m.put("hydrogen_chloride", List.of("hydrogen_chloride"));
        m.put("hydrochloric_acid", List.of("hydrochloric_acid"));
        m.put("sulfuric_acid", List.of("sulfuric_acid"));
        m.put("ammonia", List.of("ammonia"));
        m.put("methanol", List.of("methanol"));
        m.put("ethylene", List.of("ethylene", "ethene"));
        m.put("propylene", List.of("propylene"));
        m.put("sales_gas", List.of("methane", "natural_gas"));
        m.put("crude_oil", List.of("crude_oil"));
        m.put("gasoline", List.of("gasoline", "fuel"));
        m.put("motor_gasoline", List.of("gasoline", "fuel"));
        m.put("kerosene", List.of("kerosene"));
        m.put("diesel_fuel", List.of("diesel", "fuel"));
        m.put("fuel_oil", List.of("fuel_oil", "heavy_oil"));
        m.put("propane", List.of("propane", "lpg"));
        m.put("butane", List.of("butane", "lpg"));
        m.put("lpg", List.of("lpg"));
        m.put("coal_tar", List.of("creosote"));
        return Map.copyOf(m);
    }

    /** Нарушения закрытого списка; пусто — экспорт чист. Вызывается из datagen, чтобы {@code runData} падал. */
    public static List<String> exportViolations() {
        List<String> bad = new ArrayList<>();
        FLUID_EXPORT.forEach((fluid, tags) -> {
            if (NEVER_EXPORTED.contains(fluid)) bad.add(fluid + " is on the closed list");
            for (String t : tags)
                for (String w : FORBIDDEN_TAG_WORDS)
                    if (t.contains(w)) bad.add(fluid + " -> c:" + t + " names " + w);
        });
        return bad;
    }

    public static void addFluidTags(Function<TagKey<Fluid>, IntrinsicTagAppender<Fluid>> tag) {
        List<String> bad = exportViolations();
        if (!bad.isEmpty()) throw new IllegalStateException("c: fluid export breaks 16 §16.7.2: " + bad);
        new TreeMap<>(FLUID_EXPORT).forEach((name, tags) -> {
            PcFluids.Entry e = PcFluids.all().stream().filter(x -> x.name.equals(name)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("c: fluid export names unknown fluid " + name));
            for (String t : tags)
                tag.apply(TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", t))).add(e.source.get());
        });
    }
}
