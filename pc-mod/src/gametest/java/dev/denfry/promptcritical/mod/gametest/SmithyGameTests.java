package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.BloomeryBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ForgeHearthBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingItems;
import dev.denfry.promptcritical.mod.manufacturing.Smithy;
import dev.denfry.promptcritical.mod.manufacturing.SmithingAnvilBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.Workpiece;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Ручная ступень машиностроения (18 §18.6.1–18.6.4, §18.7): горн, кузнечный горн, наковальня. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmithyGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final BlockPos A = new BlockPos(0, 1, 0), B = new BlockPos(1, 1, 0), C = new BlockPos(2, 1, 0);

    private SmithyGameTests() {
    }

    private static SteelGrade steel45() {
        return SteelGrade.of(SteelFamily.MEDIUM_CARBON, "45", Map.of("C", 0.45f, "Mn", 0.65f), HeatTreatState.NORMALIZED);
    }

    @GameTest(template = SMALL)
    public static void bloomeryMakesASlaggyBloomFromOreAndCharcoal(GameTestHelper h) {
        h.setBlock(A, PcBlocks.BLOOMERY.get().defaultBlockState());
        BloomeryBlockEntity b = h.getBlockEntity(A);
        b.panelItems().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 5));
        b.panelItems().setStackInSlot(1, new ItemStack(Items.CHARCOAL, 16));
        for (int s = 0; s < 4000 && !b.furnace().done(); s++) {
            if (b.blast() < 0.4) b.pump();   // кузнец держит дутьё около половины: горн около 1200 °C, без перегрева
            b.step(1);
        }
        h.assertTrue(b.furnace().done(), "the charge must be reduced within an hour of play");
        h.assertFalse(b.furnace().castIron(), "half blast must not carburize the bloom");
        h.assertTrue(b.take(), "a done bloom can be taken");
        ItemStack bloom = b.panelItems().getStackInSlot(2);
        var w = Smithy.read(bloom, h.getLevel().getGameTime());
        h.assertTrue(w != null && w.grade().family() == SteelFamily.WROUGHT_IRON, "bloom is wrought iron");
        h.assertTrue(w.severity(PartDefect.SLAG_INCLUSION) > 0.5, "a raw bloom is full of slag");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void overblownBloomeryGivesCastIron(GameTestHelper h) {
        h.setBlock(A, PcBlocks.BLOOMERY.get().defaultBlockState());
        BloomeryBlockEntity b = h.getBlockEntity(A);
        b.panelItems().setStackInSlot(0, new ItemStack(Items.RAW_IRON, 5));
        b.panelItems().setStackInSlot(1, new ItemStack(Items.CHARCOAL, 20));
        for (int s = 0; s < 1200; s++) {
            b.pump();
            b.step(1);
        }
        h.assertTrue(b.furnace().castIron(), "full blast for twenty minutes carburizes the iron");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void whiteHeatForgingSqueezesTheSlagOut(GameTestHelper h) {
        h.setBlock(A, PcBlocks.FORGE_HEARTH.get().defaultBlockState());
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        ForgeHearthBlockEntity forge = h.getBlockEntity(A);
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        long now = h.getLevel().getGameTime();
        // кусок крицы на 2 кг, отрубленный от большой
        Workpiece piece = Workpiece.bar(SteelGrade.of(SteelFamily.WROUGHT_IRON, SteelFamily.WROUGHT_IRON.designation, Map.of("C", 0.05f),
                HeatTreatState.AS_ROLLED), Workpiece.Section.SQUARE, 0.06, 0.06, 0.07, 293.15).withDefect(PartDefect.SLAG_INCLUSION, 0.8);
        forge.panelItems().setStackInSlot(0, new ItemStack(Items.CHARCOAL, 8));
        forge.panelItems().setStackInSlot(1, Smithy.stack(piece, now));
        anvil.setOp(Forging.Op.UPSET);
        int series = 0;
        for (int round = 0; round < 60; round++) {
            for (int s = 0; s < 300; s++) {
                if (forge.blast() < 0.85) forge.pump();
                forge.step(1);
            }
            anvil.panelItems().setStackInSlot(0, forge.panelItems().getStackInSlot(1));
            forge.panelItems().setStackInSlot(1, ItemStack.EMPTY);
            for (int k = 0; k < 2; k++) {
                anvil.strike(null);
                series++;
            }
            var w = Smithy.read(anvil.panelItems().getStackInSlot(0), now);
            if (w.severity(PartDefect.SLAG_INCLUSION) == 0) {
                h.assertTrue(series <= 60, "slag must come out in tens of series, took " + series);
                h.succeed();
                return;
            }
            forge.panelItems().setStackInSlot(1, anvil.panelItems().getStackInSlot(0));
            anvil.panelItems().setStackInSlot(0, ItemStack.EMPTY);
        }
        h.fail("slag was never forged out");
    }

    @GameTest(template = SMALL)
    public static void hotSteelBarBecomesAHardenedTemperedShaft(GameTestHelper h) {
        h.setBlock(A, PcBlocks.FORGE_HEARTH.get().defaultBlockState());
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        h.setBlock(C, Blocks.WATER_CAULDRON.defaultBlockState());
        ForgeHearthBlockEntity forge = h.getBlockEntity(A);
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        long now = h.getLevel().getGameTime();
        anvil.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.012, 0.012, 0.2, 1123.15), now));
        var q = anvil.quench(null);
        h.assertTrue(q.contains(ForgeEvent.OK), "a 12 mm bar of steel 45 hardens through in water: " + q);
        // отпуск на краю горна: огонь без дутья, край около 330 °C — синий цвет побежалости
        forge.panelItems().setStackInSlot(0, new ItemStack(Items.CHARCOAL, 8));
        forge.panelItems().setStackInSlot(2, anvil.panelItems().getStackInSlot(0));
        anvil.panelItems().setStackInSlot(0, ItemStack.EMPTY);
        for (int s = 0; s < 3600; s++) forge.step(1);
        var tempered = Smithy.read(forge.panelItems().getStackInSlot(2), now);
        h.assertTrue(tempered.grade().state() == HeatTreatState.QUENCHED_TEMPERED, "the edge of the hearth tempers");
        h.assertTrue(tempered.hardnessHrc() > 40 && tempered.hardnessHrc() < tempered.quenchHrc(), "low temper keeps most of the hardness");
        anvil.panelItems().setStackInSlot(0, forge.panelItems().getStackInSlot(2));
        forge.panelItems().setStackInSlot(2, ItemStack.EMPTY);
        anvil.quench(null);   // остудить в воде: ниже Ac3 это уже не закалка
        h.assertTrue(anvil.finishShaft(null), "a cold slender bar is accepted as a shaft");
        ItemStack shaft = anvil.panelItems().getStackInSlot(0);
        h.assertTrue(shaft.is(ManufacturingItems.SHAFT.get()), "the anvil gives a shaft item");
        var spec = shaft.get(ManufacturingComponents.PART_SPEC.get());
        h.assertTrue(spec != null && spec.route() == PartRoute.FORGED && spec.heatTreat() == HeatTreatState.QUENCHED_TEMPERED
                && "medium_carbon".equals(spec.material()) && spec.passesGate(), "shaft passport: " + spec);
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void anvilRefusesToQuenchWithoutWaterAndToFinishAHotPiece(GameTestHelper h) {
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        anvil.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.012, 0.012, 0.2, 1123.15),
                h.getLevel().getGameTime()));
        h.assertTrue(anvil.quench(null).isEmpty(), "no water, no quench");
        h.assertFalse(anvil.finishShaft(null), "a red-hot bar is not a part yet");
        h.succeed();
    }
}
