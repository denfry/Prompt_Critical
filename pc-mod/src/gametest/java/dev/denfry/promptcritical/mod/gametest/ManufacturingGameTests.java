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
