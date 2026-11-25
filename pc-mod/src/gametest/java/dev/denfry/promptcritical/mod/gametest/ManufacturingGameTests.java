package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.mod.steelworks.SteelworksText;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import java.util.Locale;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Паспорт детали {@code part_spec}: кодек предмета, склейка стеков, терпимый разбор неизвестных дефектов. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ManufacturingGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";

    private ManufacturingGameTests() {
    }

    private static ItemStack withSpec(PartSpec spec) {
        var stack = new ItemStack(Items.IRON_INGOT);
        stack.set(ManufacturingComponents.PART_SPEC.get(), spec);
        return stack;
    }

    @GameTest(template = SMALL)
    public static void partSpecSurvivesTheItemCodec(GameTestHelper h) {
        var spec = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.QUENCHED_TEMPERED, 41, 7, 1.0,
                Map.of(PartDefect.POROSITY, 0.4)).inspect();
        var stack = withSpec(spec);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var back = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(back.get(ManufacturingComponents.PART_SPEC.get()), spec, "part_spec");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void partSpecWithoutDefectsSurvivesTheItemCodec(GameTestHelper h) {
        var spec = PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 21, 0, 40, Map.of());
        var stack = withSpec(spec);
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var back = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, stack).getOrThrow()).getOrThrow();
        h.assertValueEqual(back.get(ManufacturingComponents.PART_SPEC.get()), spec, "part_spec without defects");
        h.succeed();
    }

    /** Ключи семейств строятся динамически, checkTranslations их не видит: проверяем здесь. */
    @GameTest(template = SMALL)
    public static void everySteelFamilyHasAName(GameTestHelper h) {
        for (SteelFamily f : SteelFamily.values()) {
            String key = "message." + PromptCritical.MOD_ID + ".steel.family." + f.name().toLowerCase(Locale.ROOT);
            h.assertTrue(SteelworksText.LANG.containsKey(key), "no lang entry for " + key);
        }
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void equalPassportsStackAndDifferentOnesDoNot(GameTestHelper h) {
        var a = withSpec(PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 21, 0, 40, Map.of()));
        var b = withSpec(PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 19, 0, 33, Map.of()));
        var c = withSpec(PartSpec.of("shaft", "45", PartRoute.FORGED, HeatTreatState.NORMALIZED, 21, 0, 40, Map.of()).inspect());
        h.assertTrue(ItemStack.isSameItemSameComponents(a, b), "quantized equal passports stack");
        h.assertFalse(ItemStack.isSameItemSameComponents(a, c), "inspected and uninspected differ");
        h.succeed();
    }

    /** 18 §18.6.10: обточенная закалённая отливка проверена, когда измерены размер, твёрдость и сделан снимок. */
    @GameTest(template = SMALL)
    public static void measuringToolsInspectACastingStepByStep(GameTestHelper h) {
        var stack = withSpec(PartSpec.of("pump_casing", "grey_iron", PartRoute.CAST, HeatTreatState.QUENCHED_TEMPERED, 30, 11,
                3.2, Map.of(PartDefect.POROSITY, 0.4)));
        var r = dev.denfry.promptcritical.mod.manufacturing.PartInspector.measure(stack,
                dev.denfry.promptcritical.sim.manufacturing.PartInspection.Tool.CALIPER);
        h.assertValueEqual(r.outcome(), dev.denfry.promptcritical.mod.manufacturing.PartInspector.Outcome.NOT_APPLICABLE,
                "caliper cannot prove IT11");
        r = dev.denfry.promptcritical.mod.manufacturing.PartInspector.measure(stack,
                dev.denfry.promptcritical.sim.manufacturing.PartInspection.Tool.MICROMETER);
        h.assertValueEqual(r.outcome(), dev.denfry.promptcritical.mod.manufacturing.PartInspector.Outcome.MEASURED, "micrometer");
        r = dev.denfry.promptcritical.mod.manufacturing.PartInspector.measure(stack,
                dev.denfry.promptcritical.sim.manufacturing.PartInspection.Tool.HARDNESS_TESTER);
        h.assertValueEqual(r.outcome(), dev.denfry.promptcritical.mod.manufacturing.PartInspector.Outcome.MEASURED, "hardness");
        h.assertFalse(stack.get(ManufacturingComponents.PART_SPEC.get()).inspected(), "not yet inspected without film");
        r = dev.denfry.promptcritical.mod.manufacturing.PartInspector.measure(stack,
                dev.denfry.promptcritical.sim.manufacturing.PartInspection.Tool.RADIOGRAPHY);
        h.assertValueEqual(r.outcome(), dev.denfry.promptcritical.mod.manufacturing.PartInspector.Outcome.INSPECTED, "film");
        h.assertTrue(stack.get(ManufacturingComponents.PART_SPEC.get()).inspected(), "inspected");
        h.assertFalse(stack.has(ManufacturingComponents.INSPECTION_CHECKS.get()), "check mask removed");
        h.succeed();
    }

    /** 18 §18.7: напильник доводит одну кованую деталь из стака до IT13, закалённую не берёт. */
    @GameTest(template = SMALL)
    public static void fileFinishesOneForgedShaftOfAStack(GameTestHelper h) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel());
        player.getInventory().clearContent();
        var forged = PartSpec.of("shaft", "medium_carbon", PartRoute.FORGED, HeatTreatState.NORMALIZED, 20, 0, 25, Map.of());
        var shafts = withSpec(forged);
        shafts.setCount(2);
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, shafts);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(dev.denfry.promptcritical.mod.manufacturing.ManufacturingItems.FILE.get()));
        var file = player.getMainHandItem();
        h.assertTrue(file.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(), "file used");
        h.assertValueEqual(player.getOffhandItem().getCount(), 1, "one shaft left unfiled in the hand");
        h.assertValueEqual(player.getOffhandItem().get(ManufacturingComponents.PART_SPEC.get()), forged, "unfiled passport");
        ItemStack filed = ItemStack.EMPTY;
        for (ItemStack s : player.getInventory().items) if (s.has(ManufacturingComponents.PART_SPEC.get())) filed = s;
        h.assertValueEqual(filed.get(ManufacturingComponents.PART_SPEC.get()).itGrade(), 13, "filed to IT13");
        h.assertValueEqual(file.getDamageValue(), 1, "file wears");

        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, withSpec(PartSpec.of("shaft", "medium_carbon",
                PartRoute.FORGED, HeatTreatState.QUENCHED, 55, 0, 25, Map.of())));
        h.assertFalse(file.use(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND).getResult().consumesAction(),
                "hardened shaft refused");
        player.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void unknownDefectIsSkippedNotFatal(GameTestHelper h) {
        var tag = new CompoundTag();
        tag.putString("kind", "shaft");
        tag.putString("material", "45");
        tag.putString("route", "FORGED");
        tag.putString("heat_treat", "NORMALIZED");
        tag.putInt("hardness_hrc", 20);
        tag.putInt("it_grade", 0);
        tag.putDouble("ra_um", 50);
        tag.putBoolean("inspected", false);
        var defects = new CompoundTag();
        defects.putInt("FUTURE_DEFECT", 2);
        defects.putInt("CRACK", 1);
        tag.put("defects", defects);
        var spec = ManufacturingComponents.PART_SPEC_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        h.assertValueEqual(spec.defects(), Map.of(PartDefect.CRACK, 1), "defects");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void hostileTagDecodesToASafePassport(GameTestHelper h) {
        var tag = new CompoundTag();
        tag.putString("kind", "shaft");
        tag.putString("material", "45");
        tag.putString("route", "NOPE");
        tag.putString("heat_treat", "NOPE");
        tag.putInt("hardness_hrc", 52);
        tag.putInt("it_grade", -3);
        tag.putDouble("ra_um", 0.0);
        tag.putBoolean("inspected", false);
        var defects = new CompoundTag();
        defects.putInt("CRACK", 0);
        defects.putInt("POROSITY", 5);
        defects.putInt("BURNT", 2);
        tag.put("defects", defects);
        var spec = ManufacturingComponents.PART_SPEC_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
        h.assertValueEqual(spec.route(), PartRoute.FORGED, "route");
        h.assertValueEqual(spec.heatTreat(), HeatTreatState.AS_ROLLED, "heat_treat");
        h.assertValueEqual(spec.hardnessHrc(), 50, "hardness_hrc");
        h.assertValueEqual(spec.itGrade(), 0, "it_grade");
        h.assertValueEqual(spec.raUm(), 100.0, "ra_um");
        h.assertValueEqual(spec.defects(), Map.of(PartDefect.BURNT, 2), "defects");
        h.succeed();
    }
}
