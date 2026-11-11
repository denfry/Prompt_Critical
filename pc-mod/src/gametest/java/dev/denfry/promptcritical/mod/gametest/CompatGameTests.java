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

    private static List<Part> concat(List<Part> a, List<Part> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }
}
