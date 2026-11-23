package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.DrawPlateBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ForgeHearthBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.HotHandling;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingItems;
import dev.denfry.promptcritical.mod.manufacturing.Smithy;
import dev.denfry.promptcritical.mod.manufacturing.SmithingAnvilBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.manufacturing.Drawing;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.Quenching;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MF3, ручные хвосты (18 §18.6.3, §18.6.4, §18.6.7, §18.6.10): рубка, кузнечная сварка, масло, клещи, волочение. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SmithyMf3GameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final BlockPos A = new BlockPos(0, 1, 0), B = new BlockPos(1, 1, 0), C = new BlockPos(2, 1, 0);

    private SmithyMf3GameTests() {
    }

    private static SteelGrade iron() {
        return SteelGrade.of(SteelFamily.WROUGHT_IRON, SteelFamily.WROUGHT_IRON.designation, Map.of("C", 0.05f), HeatTreatState.AS_ROLLED);
    }

    private static SteelGrade steel45() {
        return SteelGrade.of(SteelFamily.MEDIUM_CARBON, "45", Map.of("C", 0.45f, "Mn", 0.65f), HeatTreatState.NORMALIZED);
    }

    @GameTest(template = SMALL)
    public static void anvilCutsAWhiteHotBarAndWeldsItBack(GameTestHelper h) {
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        long now = h.getLevel().getGameTime();
        anvil.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.06, 0.06, 0.2, 1590), now));
        h.assertTrue(anvil.cut(null) == ForgeEvent.OK, "a white-hot bar is cut in half");
        var a = Smithy.read(anvil.panelItems().getStackInSlot(0), now);
        var b = Smithy.read(anvil.panelItems().getStackInSlot(1), now);
        h.assertTrue(a != null && b != null && Math.abs(a.lengthM() - 0.1) < 1e-4 && Math.abs(b.lengthM() - 0.1) < 1e-4, "two halves");
        h.assertTrue(anvil.cut(null) == null, "the second slot is busy, no second cut");
        var weld = anvil.forgeWeld(null);
        h.assertFalse(weld.isEmpty(), "welded: " + weld);
        var joined = Smithy.read(anvil.panelItems().getStackInSlot(0), now);
        h.assertTrue(anvil.panelItems().getStackInSlot(1).isEmpty(), "the second piece went into the weld");
        h.assertTrue(Math.abs(joined.lengthM() - 0.2) < 1e-3, "length is back: " + joined.lengthM());
        h.assertTrue(joined.severity(PartDefect.LACK_OF_FUSION) < 0.3, "a fluxed weld at white heat is nearly sound");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void coldPiecesAreNeitherCutNorWelded(GameTestHelper h) {
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        long now = h.getLevel().getGameTime();
        anvil.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.03, 0.03, 0.1, 293.15), now));
        h.assertTrue(anvil.cut(null) == ForgeEvent.TOO_COLD, "the chisel does not take cold iron");
        anvil.panelItems().setStackInSlot(1, Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.03, 0.03, 0.1, 293.15), now));
        h.assertTrue(anvil.forgeWeld(null).contains(ForgeEvent.TOO_COLD), "cold pieces do not weld");
        h.assertFalse(anvil.panelItems().getStackInSlot(1).isEmpty(), "both pieces stay");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void oilQuenchNeedsOilAndIsGentlerThanWater(GameTestHelper h) {
        h.setBlock(B, PcBlocks.SMITHING_ANVIL.get().defaultBlockState());
        SmithingAnvilBlockEntity anvil = h.getBlockEntity(B);
        long now = h.getLevel().getGameTime();
        anvil.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.005, 0.005, 0.1, 1123.15), now));
        h.assertTrue(anvil.quench(null, Quenching.Medium.OIL).isEmpty(), "no oil, no oil quench");
        h.setBlock(C, PcBlocks.CRUDE_OIL.get().defaultBlockState());
        var ev = anvil.quench(null, Quenching.Medium.OIL);
        h.assertTrue(ev.contains(ForgeEvent.OK), "a 5 mm bar of steel 45 hardens through in oil: " + ev);
        var w = Smithy.read(anvil.panelItems().getStackInSlot(0), now);
        h.assertTrue(w.grade().state() == HeatTreatState.QUENCHED && w.severity(PartDefect.CRACK) == 0, "hard and sound");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void hotPiecesBurnWithoutTongs(GameTestHelper h) {
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        player.getInventory().clearContent();
        long now = h.getLevel().getGameTime();
        player.getInventory().add(Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.03, 0.03, 0.1, 1200), now));
        h.assertTrue(HotHandling.hottestK(player.getInventory(), now) > HotHandling.RED_HOT_K, "a red-hot piece is in the inventory");
        h.assertFalse(HotHandling.hasTongs(player.getInventory()), "no tongs yet");
        player.getInventory().add(new ItemStack(ManufacturingItems.TONGS.get()));
        h.assertTrue(HotHandling.hasTongs(player.getInventory()), "tongs protect the hands");
        player.getInventory().clearContent();
        h.succeed();
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void copperIsDrawnUntilItHardensAndAnAnnealLetsItGoOn(GameTestHelper h) {
        h.setBlock(A, PcBlocks.DRAW_PLATE.get().defaultBlockState());
        h.setBlock(C, PcBlocks.FORGE_HEARTH.get().defaultBlockState());
        DrawPlateBlockEntity plate = h.getBlockEntity(A);
        ForgeHearthBlockEntity forge = h.getBlockEntity(C);
        long now = h.getLevel().getGameTime();
        plate.panelItems().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        plate.panelItems().setStackInSlot(1, new ItemStack(Items.HONEYCOMB, 4));
        int ok = 0;
        Drawing.Outcome o = null;
        for (int i = 0; i < 40; i++) {
            plate.nextDieFor(Smithy.readWire(plate.panelItems().getStackInSlot(0), now));
            o = plate.pull(null);
            if (o != Drawing.Outcome.OK) break;
            ok++;
        }
        h.assertTrue(o == Drawing.Outcome.BROKE, "copper breaks once it is hard: " + o + " after " + ok + " passes");
        var hard = Smithy.readWire(plate.panelItems().getStackInSlot(0), now);
        h.assertTrue(ok >= 8 && hard.diameterM() < 0.002, "copper goes below 2 mm before it needs an anneal: " + hard);
        // отжиг в огне горна
        forge.panelItems().setStackInSlot(0, new ItemStack(Items.CHARCOAL, 4));
        forge.panelItems().setStackInSlot(1, plate.panelItems().getStackInSlot(0));
        plate.panelItems().setStackInSlot(0, ItemStack.EMPTY);
        for (int s = 0; s < 240; s++) {
            if (forge.blast() < 0.3) forge.pump();
            forge.step(1);
        }
        var annealed = forge.panelItems().getStackInSlot(1).get(ManufacturingComponents.WIRE.get()).wire();
        h.assertTrue(annealed.strain() == 0, "the fire anneals the wire: " + annealed);
        plate.panelItems().setStackInSlot(0, forge.panelItems().getStackInSlot(1));
        plate.nextDieFor(annealed);
        h.assertTrue(plate.pull(null) == Drawing.Outcome.OK, "annealed wire draws again");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void aSteelRodIsTooStrongForTheWindlass(GameTestHelper h) {
        h.setBlock(A, PcBlocks.DRAW_PLATE.get().defaultBlockState());
        DrawPlateBlockEntity plate = h.getBlockEntity(A);
        long now = h.getLevel().getGameTime();
        plate.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.010, 0.010, 0.3, 293.15), now));
        plate.setDie(1);   // 9 мм
        h.assertTrue(plate.pull(null) == Drawing.Outcome.TOO_STRONG, "a 10 mm steel rod does not go by hand");
        h.assertTrue(plate.panelItems().getStackInSlot(0).has(ManufacturingComponents.WORKPIECE.get()), "the rod is left as it was");
        h.succeed();
    }
}
