package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.Electrical;
import dev.denfry.promptcritical.mod.manufacturing.ElectricalData;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.NotchingPressBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.SheetMillBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.SmithyFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.WindingMachineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.steelworks.SteelworksComponents;
import dev.denfry.promptcritical.sim.manufacturing.ElectricalSheet;
import dev.denfry.promptcritical.sim.manufacturing.InsulationClass;
import dev.denfry.promptcritical.sim.manufacturing.SheetCoating;
import dev.denfry.promptcritical.sim.manufacturing.Stator;
import dev.denfry.promptcritical.sim.manufacturing.Wire;
import dev.denfry.promptcritical.sim.manufacturing.WireInsulation;
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

/**
 * MF4, электротехника (18 §18.6.8–18.6.9, §18.13 «двигатель с потерями пакета и классом изоляции»): лист 2212 от
 * подката до 0,5 мм с отжигами, лак в печи, пазовая вырубка с износом штампа, шихтовка и обмотка статора.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectricalPartsGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private ElectricalPartsGameTests() {
    }

    private static ItemStack siliconPlate(GameTestHelper h) {
        SteelGrade g = SteelGrade.of(SteelFamily.ELECTRICAL, "2212", Map.of("Si", 3.0f, "C", 0.04f), HeatTreatState.AS_ROLLED);
        ItemStack s = new ItemStack(PcItems.STEEL_PLATE.get());
        s.set(SteelworksComponents.STEEL_STOCK.get(),
                SteelworksComponents.SteelStock.of(g, 0.002, 1.0, false, false, 1, 293.15, h.getLevel().getGameTime()));
        return s;
    }

    /** Отжечь стек листов в нагревательной печи на угле. */
    private static ItemStack anneal(GameTestHelper h, ItemStack sheets) {
        h.setBlock(A, PcBlocks.HEATING_FURNACE.get().defaultBlockState());
        SmithyFurnaceBlockEntity f = h.getBlockEntity(A);
        f.panelItems().setStackInSlot(0, new ItemStack(Items.COAL, 32));
        f.panelItems().setStackInSlot(1, sheets);
        f.setSetpointK(1098.15);
        for (int s = 0; s < 3600 && Electrical.sheet(f.panelItems().getStackInSlot(1)).strain() > 0; s++) f.step(1);
        return f.panelItems().getStackInSlot(1).copy();
    }

    @GameTest(template = SMALL, timeoutTicks = 400)
    public static void sheetMillRollsSiliconPlateToHalfAMillimetreWithAnnealsBetween(GameTestHelper h) {
        ItemStack in = siliconPlate(h);
        int anneals = 0;
        for (int round = 0; round < 5; round++) {
            h.setBlock(A, PcBlocks.SHEET_MILL.get().defaultBlockState());
            SheetMillBlockEntity mill = h.getBlockEntity(A);
            mill.setSpeed(64);
            mill.panelItems().setStackInSlot(0, in);
            mill.setTarget(2, true);
            h.assertTrue(Math.abs(mill.targetM() - 0.0005) < 1e-12, "target 0.5 mm");
            mill.runSeconds(1200);
            ItemStack out = mill.panelItems().getStackInSlot(1).copy();
            ElectricalSheet sh = Electrical.sheet(out);
            h.assertTrue(sh != null && mill.passes() > 0, "the mill rolled the strip: " + out + " stop " + mill.stop());
            if (mill.stop() == SheetMillBlockEntity.STOP_DONE) {
                h.assertTrue(Math.abs(sh.thicknessM() - 0.0005) < 1e-6, "0.5 mm: " + sh);
                h.assertTrue(out.getCount() == 4, "a 2 mm plate gives four sheets of 0.5 mm: " + out.getCount());
                h.assertTrue(anneals >= 1, "3 % silicon needs an anneal between passes: " + anneals);
                h.assertTrue(sh.strain() > 0, "and leaves the mill work hardened");
                ItemStack done = anneal(h, out);
                h.assertTrue(Electrical.sheet(done).strain() == 0, "the furnace anneals the sheets");
                h.succeed();
                return;
            }
            h.assertTrue(mill.stop() == SheetMillBlockEntity.STOP_ANNEAL, "the mill stops for an anneal: " + mill.stop());
            in = anneal(h, out);
            anneals++;
        }
        h.fail("did not reach 0.5 mm in five rounds");
    }

    @GameTest(template = SMALL)
    public static void furnaceVarnishesSheetsAtBakingHeatAndBurnsTheVarnishOffAbove(GameTestHelper h) {
        h.setBlock(A, PcBlocks.HEATING_FURNACE.get().defaultBlockState());
        SmithyFurnaceBlockEntity f = h.getBlockEntity(A);
        f.panelItems().setStackInSlot(0, new ItemStack(Items.COAL, 16));
        f.panelItems().setStackInSlot(1, Electrical.sheetStack(new ElectricalSheet(0.0005, 0.03, 0, SheetCoating.NONE), 4));
        f.panelItems().setStackInSlot(SmithyFurnaceBlockEntity.COATING_SLOT, new ItemStack(PcItems.PHENOLIC_RESIN.get(), 4));
        f.setSetpointK(473.15);
        for (int s = 0; s < 1800 && Electrical.sheet(f.panelItems().getStackInSlot(1)).coating() == SheetCoating.NONE; s++) f.step(1);
        h.assertTrue(Electrical.sheet(f.panelItems().getStackInSlot(1)).coating() == SheetCoating.PHENOLIC, "varnished at 200 °C");
        h.assertTrue(f.panelItems().getStackInSlot(SmithyFurnaceBlockEntity.COATING_SLOT).isEmpty(), "one resin per sheet");
        f.setSetpointK(1098.15);
        for (int s = 0; s < 1800; s++) f.step(1);
        h.assertTrue(Electrical.sheet(f.panelItems().getStackInSlot(1)).coating() == SheetCoating.NONE, "an anneal burns it off");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void notchingPressPunchesSlotBySlotAndTheBurrGrowsUntilARegrind(GameTestHelper h) {
        h.setBlock(A, PcBlocks.NOTCHING_PRESS.get().defaultBlockState());
        NotchingPressBlockEntity p = h.getBlockEntity(A);
        p.setSpeed(64);
        p.panelItems().setStackInSlot(0, Electrical.sheetStack(new ElectricalSheet(0.0005, 0.03, 0, SheetCoating.NONE), 3));
        p.setOn(true);
        p.runSeconds(400);
        h.assertTrue(p.stop() == NotchingPressBlockEntity.STOP_DONE && p.bundles() == 3, "three sheets, three bundles: "
                + p.bundles() + " stop " + p.stop());
        // 36 пазов + 2 хода на окружности, по 10 пластин с листа
        h.assertTrue(p.dieStrokes() == 3L * 38 * ElectricalData.LAMINATIONS_PER_BUNDLE, "slot by slot: " + p.dieStrokes());
        int bundles = 0;
        for (int i = 1; i <= NotchingPressBlockEntity.OUTPUTS; i++) {
            ItemStack o = p.panelItems().getStackInSlot(i);
            if (o.has(ManufacturingComponents.LAMINATION.get())) bundles += o.getCount();
        }
        h.assertTrue(bundles == 3, "bundles are in the output: " + bundles);
        p.regrind();
        p.runSeconds(NotchingPressBlockEntity.REGRIND_S + 1);
        h.assertTrue(p.dieStrokes() == 0, "a regrind resets the die");
        h.succeed();
    }

    private static Wire annealedCopper() {
        return new Wire(Wire.Metal.COPPER, 0.0014, 20, 0);
    }

    /** Пачки из 25 листов {@code sheet} одной обработки. */
    private static ItemStack bundles(ElectricalSheet sheet, boolean annealAndVarnishAfter, double burrM) {
        var l = new dev.denfry.promptcritical.sim.manufacturing.Lamination(sheet,
                dev.denfry.promptcritical.sim.manufacturing.LaminationShape.STATOR_112, burrM, false, false);
        if (annealAndVarnishAfter) l = l.anneal().coat(SheetCoating.PHENOLIC);
        return Electrical.bundleStack(l, 25);
    }

    private static Stator wind(GameTestHelper h, ItemStack laminations) {
        // тот же блок поверх себя не пересоздаёт блок-сущность: второй станок унаследовал бы статор в выходе
        h.setBlock(A, Blocks.AIR.defaultBlockState());
        h.setBlock(A, PcBlocks.WINDING_MACHINE.get().defaultBlockState());
        WindingMachineBlockEntity w = h.getBlockEntity(A);
        w.setSpeed(64);
        w.panelItems().setStackInSlot(0, laminations);
        w.panelItems().setStackInSlot(WindingMachineBlockEntity.WIRE, Electrical.magnetWireStack(annealedCopper(), WireInsulation.ENAMEL)
                .copyWithCount(16));
        w.setOn(true);
        w.runSeconds(900);
        h.assertTrue(w.stop() == WindingMachineBlockEntity.STOP_DONE, "wound: stop " + w.stop());
        Stator st = Electrical.stator(w.panelItems().getStackInSlot(WindingMachineBlockEntity.OUT));
        h.assertTrue(st != null, "a stator in the output");
        h.assertTrue(w.panelItems().getStackInSlot(WindingMachineBlockEntity.WIRE).getCount() < 16, "wire was used");
        return st;
    }

    @GameTest(template = SMALL, timeoutTicks = 200)
    public static void windingMachineMakesAStatorWhoseEfficiencyAndInsulationFollowTheWork(GameTestHelper h) {
        Stator good = wind(h, bundles(new ElectricalSheet(0.0005, 0.03, 0, SheetCoating.NONE), true, 15e-6));
        h.assertTrue(good.insulationClass() == InsulationClass.B, "phenolic enamel is class B");
        h.assertTrue(good.flawSeverity() == 0 && good.insulationStartMOhm() >= 999, "a clean winding: " + good.insulationStartMOhm());
        h.assertTrue(good.efficiency() > 0.84 && good.efficiency() < 0.88, "near the 4A112M4 catalogue: " + good.efficiency());
        Stator bad = wind(h, bundles(new ElectricalSheet(0.0005, 0.001, 1.0, SheetCoating.NONE), false, 45e-6));
        h.assertTrue(good.efficiency() - bad.efficiency() > 0.02, "careless iron costs efficiency: " + good.efficiency()
                + " vs " + bad.efficiency());
        h.assertTrue(bad.coreLossW() > 2 * good.coreLossW(), "core loss: " + bad.coreLossW() + " vs " + good.coreLossW());
        h.succeed();
    }
}
