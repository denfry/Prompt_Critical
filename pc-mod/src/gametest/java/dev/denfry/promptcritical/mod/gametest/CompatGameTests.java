package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.compat.CompatText;
import dev.denfry.promptcritical.mod.compat.MachineProcesses;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Fluids_;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Items_;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Part;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Plant;
import dev.denfry.promptcritical.mod.compat.MachineProcesses.Process;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.registry.PcTags;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Интеграции: каталог процессов для просмотрщика рецептов и экспорт в общие теги {@code c:}. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CompatGameTests {
    private static final String EMPTY = "gametest/empty_3x3x3";

    private CompatGameTests() {
    }

    @GameTest(template = EMPTY)
    public static void processCatalogIsComplete(GameTestHelper h) {
        Set<String> ids = new HashSet<>();
        for (Plant plant : Plant.values()) {
            h.assertTrue(CompatText.LANG.containsKey(plant.titleKey()), "title of " + plant);
            h.assertTrue(MachineProcesses.all().stream().anyMatch(p -> p.plant() == plant), "processes of " + plant);
            h.assertFalse(MachineProcesses.machines(plant).isEmpty(), "machines of " + plant);
        }
        for (Process p : MachineProcesses.all()) {
            h.assertTrue(ids.add(p.id()), "unique id " + p.id());
            String[] note = CompatText.LANG.get(p.noteKey());
            h.assertTrue(note != null && !note[0].isBlank() && !note[1].isBlank(), "note en/ru of " + p.id());
            h.assertFalse(p.machineStacks().isEmpty(), "machine item of " + p.id());
            h.assertFalse(p.inputs().isEmpty() && p.outputs().isEmpty(), "inputs or outputs of " + p.id());
            for (Part part : concat(p.inputs(), p.outputs())) {
                h.assertTrue(part.amount() >= 0, "amount of " + p.id());
                switch (part) {
                    case Items_ items -> h.assertFalse(items.resolve().isEmpty(), "items resolve in " + p.id());
                    case Fluids_ fluids -> {
                        // реагенты улавливания (c:sulfuric_acid, c:wash_oil) дают только другие моды
                        if (!p.id().equals("byproduct_plant")) {
                            h.assertFalse(fluids.fluids().get().isEmpty(), "fluids resolve in " + p.id());
                        }
                    }
                }
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void productsAreExportedToCommonTags(GameTestHelper h) {
        h.assertTrue(PcItems.STEEL_INGOT.get().getDefaultInstance().is(PcTags.Items.common("ingots/steel")), "c:ingots/steel");
        h.assertTrue(PcItems.STEEL_INGOT.get().getDefaultInstance().is(PcTags.Items.common("ingots")), "c:ingots");
        h.assertTrue(PcItems.PIG_IRON.get().getDefaultInstance().is(PcTags.Items.common("ingots/pig_iron")), "c:ingots/pig_iron");
        h.assertTrue(PcItems.STEEL_PLATE.get().getDefaultInstance().is(PcTags.Items.common("plates/steel")), "c:plates/steel");
        h.assertTrue(PcItems.COPPER_WIRE.get().getDefaultInstance().is(PcTags.Items.common("wires/copper")), "c:wires/copper");
        h.assertTrue(PcItems.COKE.get().getDefaultInstance().is(PcTags.Items.common("coal_coke")), "c:coal_coke");
        TagKey<Fluid> oxygen = TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "oxygen"));
        Fluid o2 = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(PromptCritical.id("oxygen"));
        h.assertTrue(o2.defaultFluidState().is(oxygen), "c:oxygen");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void modOresStayOutOfPerMetalTagsUnlessOpenCompatIsEnabled(GameTestHelper h) {
        // строгая совместимость: руды мода в тегах по металлу приходят только из пака open_compat (сервер гейм-тестов
        // включает все доступные паки, поэтому проверяем источник, а не членство)
        var server = h.getLevel().getServer();
        for (String path : List.of("tags/item/raw_materials/iron.json", "tags/item/ores/iron.json", "tags/block/ores/iron.json")) {
            for (var res : server.getResourceManager().getResourceStack(ResourceLocation.fromNamespaceAndPath("c", path))) {
                String json;
                try (var in = res.open()) {
                    json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                } catch (java.io.IOException e) {
                    throw new IllegalStateException(e);
                }
                if (json.contains(PromptCritical.MOD_ID + ":")) {
                    h.assertTrue(res.sourcePackId().contains("open_compat"), "c:" + path + " from " + res.sourcePackId());
                }
            }
        }
        h.assertTrue(PcItems.RAW_MAGNETITE.get().getDefaultInstance().is(PcTags.Items.RAW_MATERIALS), "still c:raw_materials");
        var ore = dev.denfry.promptcritical.mod.registry.PcBlocks.MAGNETITE_ORE.get().defaultBlockState();
        h.assertTrue(ore.is(PcTags.Blocks.ORES), "still c:ores");
        h.assertTrue(ore.is(PcTags.Blocks.IRON_ORES), "route I0 still finds magnetite");
        h.assertTrue(net.minecraft.world.level.block.Blocks.IRON_ORE.defaultBlockState().is(PcTags.Blocks.IRON_ORES),
                "route I0 also accepts any c:ores/iron");
        h.assertTrue(h.getLevel().getServer().getPackRepository().getAvailableIds().stream()
                .anyMatch(id -> id.contains("open_compat")), "open compat pack is available");
        h.succeed();
    }

    /** 16 §16.7.2–§16.7.3: товарная химия и топлива уходят в {@code c:}, ядерные среды и вода контуров — никогда. */
    @GameTest(template = EMPTY)
    public static void fluidExportKeepsTheClosedListUntagged(GameTestHelper h) {
        h.assertTrue(dev.denfry.promptcritical.mod.compat.CommonTags.exportViolations().isEmpty(),
                "closed list: " + dev.denfry.promptcritical.mod.compat.CommonTags.exportViolations());
        dev.denfry.promptcritical.mod.compat.CommonTags.FLUID_EXPORT.forEach((name, tags) -> {
            Fluid f = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(PromptCritical.id(name));
            for (String t : tags)
                h.assertTrue(f.defaultFluidState().is(TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", t))),
                        name + " in c:" + t);
        });
        for (String name : dev.denfry.promptcritical.mod.compat.CommonTags.NEVER_EXPORTED) {
            Fluid f = net.minecraft.core.registries.BuiltInRegistries.FLUID.get(PromptCritical.id(name));
            h.assertTrue(f != net.minecraft.world.level.material.Fluids.EMPTY, "closed-list fluid " + name + " exists");
            h.assertTrue(f.builtInRegistryHolder().tags().noneMatch(t -> t.location().getNamespace().equals("c")),
                    name + " must carry no c: tag");
        }
        h.succeed();
    }

    /** 16 §16.8.5: Carry On не поднимает ни одну нашу блок-сущность вместе с содержимым. */
    @GameTest(template = EMPTY)
    public static void carryOnCannotLiftOurBlockEntities(GameTestHelper h) {
        TagKey<net.minecraft.world.level.block.Block> blacklist = TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("carryon", "block_blacklist"));
        int n = 0;
        for (var b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b);
            if (!key.getNamespace().equals(PromptCritical.MOD_ID) || !(b instanceof net.minecraft.world.level.block.EntityBlock)) continue;
            h.assertTrue(b.defaultBlockState().is(blacklist), key + " in carryon:block_blacklist");
            n++;
        }
        h.assertTrue(n > 50, "block entities checked: " + n);
        h.succeed();
    }

    /** 16 §16.9 и §16.10.1: слоты снаряжения назначены тегами; без Cold Sweat и Tough As Nails переохлаждение наше. */
    @GameTest(template = EMPTY)
    public static void wornGearSlotsAndBodyTemperatureOwner(GameTestHelper h) {
        h.assertTrue(PcItems.DOSIMETER.get().getDefaultInstance().is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("curios", "belt"))), "dosimeter in curios:belt");
        h.assertTrue(PcItems.CAP_LAMP.get().getDefaultInstance().is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("accessories", "hat"))), "cap lamp in accessories:hat");
        h.assertTrue(PcItems.GAS_MASK.get().getDefaultInstance().is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("accessories", "face"))), "gas mask in accessories:face");
        h.assertTrue(PcItems.HEATED_SUIT.get().getDefaultInstance().is(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("toughasnails", "heating_armor"))), "heated suit in toughasnails:heating_armor");
        h.assertFalse(dev.denfry.promptcritical.mod.compat.CompatSwitch.bodyTemperatureIsExternal(),
                "no Cold Sweat or Tough As Nails: hypothermia stays ours");
        var p = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        h.assertTrue(dev.denfry.promptcritical.mod.compat.WornGear.accessories(p).isEmpty(), "no slot mods: no accessories");
        h.succeed();
    }

    @GameTest(template = EMPTY)
    public static void coldSweatHeatSourcesMatchBlockStates(GameTestHelper h) {
        // данные Cold Sweat не проверяются без самого мода: сверяем id блоков и свойства состояний, чтобы
        // переименование блока или свойства не отключало интеграцию молча
        var files = h.getLevel().getServer().getResourceManager().listResources("cold_sweat/block/block_temp",
                id -> id.getNamespace().equals(PromptCritical.MOD_ID) && id.getPath().endsWith(".json"));
        h.assertTrue(files.size() >= 10, "cold sweat heat sources: " + files.size());
        files.forEach((id, res) -> {
            com.google.gson.JsonObject json;
            try (var reader = res.openAsReader()) {
                json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
            h.assertTrue(json.get("temperature").getAsDouble() > 0, "temperature in " + id);
            h.assertTrue(json.get("range").getAsInt() > 0, "range in " + id);
            var block = json.getAsJsonObject("block");
            var state = block.getAsJsonObject("state");
            for (var name : block.getAsJsonArray("blocks")) {
                var blockId = ResourceLocation.parse(name.getAsString());
                h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(blockId), "block " + blockId + " in " + id);
                var definition = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(blockId).getStateDefinition();
                for (var entry : state.entrySet()) {
                    var property = definition.getProperty(entry.getKey());
                    h.assertTrue(property != null, "property " + entry.getKey() + " of " + blockId + " in " + id);
                    var values = entry.getValue().isJsonArray() ? entry.getValue().getAsJsonArray().asList()
                            : List.of(entry.getValue());
                    for (var value : values) {
                        h.assertTrue(property.getValue(value.getAsString()).isPresent(),
                                entry.getKey() + "=" + value + " of " + blockId + " in " + id);
                    }
                }
            }
        });
        h.succeed();
    }

    private static List<Part> concat(List<Part> a, List<Part> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
