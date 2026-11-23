package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.manufacturing.DrawPlateBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.ManufacturingComponents;
import dev.denfry.promptcritical.mod.manufacturing.PowerHammerBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.Smithy;
import dev.denfry.promptcritical.mod.manufacturing.SmithyFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.manufacturing.WireMillBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.manufacturing.ForgeEvent;
import dev.denfry.promptcritical.sim.manufacturing.Forging;
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
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** MF3, механизированная ступень (18 §18.7): молот, пресс, волочильный стан, нагревательная и закалочная печи. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PoweredSmithyGameTests {
    private static final String SMALL = "gametest/empty_3x3x3";
    private static final BlockPos A = new BlockPos(1, 1, 1);

    private PoweredSmithyGameTests() {
    }

    private static SteelGrade iron() {
        return SteelGrade.of(SteelFamily.WROUGHT_IRON, SteelFamily.WROUGHT_IRON.designation, Map.of("C", 0.05f), HeatTreatState.AS_ROLLED);
    }

    private static SteelGrade steel45() {
        return SteelGrade.of(SteelFamily.MEDIUM_CARBON, "45", Map.of("C", 0.45f, "Mn", 0.65f), HeatTreatState.NORMALIZED);
    }

    @GameTest(template = SMALL)
    public static void powerHammerDrawsAHeavyBarToTheTargetAndStops(GameTestHelper h) {
        h.setBlock(A, PcBlocks.POWER_HAMMER.get().defaultBlockState());
        PowerHammerBlockEntity hammer = h.getBlockEntity(A);
        hammer.setSpeed(64);
        long now = h.getLevel().getGameTime();
        hammer.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.08, 0.08, 0.25, 1473.15), now));
        hammer.setMode(Forging.Op.DRAW, 40, 100, true);
        h.assertTrue(hammer.shaftW() > 15_000, "the shaft gives the hammer its rated power: " + hammer.shaftW());
        hammer.runSeconds(120);
        var w = Smithy.read(hammer.panelItems().getStackInSlot(0), now);
        h.assertTrue(w.aM() <= 0.040 + 1e-9, "drawn down to 40 mm: " + w.aM());
        h.assertTrue(hammer.series() > 0 && hammer.series() <= 10, "in a few series, not hundreds: " + hammer.series());
        int done = hammer.series();
        hammer.runSeconds(60);
        h.assertTrue(hammer.series() == done, "at the target the hammer stops striking");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void powerHammerDoesNotStrikeACoolPieceOrRunWithoutTheShaft(GameTestHelper h) {
        h.setBlock(A, PcBlocks.POWER_HAMMER.get().defaultBlockState());
        PowerHammerBlockEntity hammer = h.getBlockEntity(A);
        long now = h.getLevel().getGameTime();
        hammer.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.SQUARE, 0.05, 0.05, 0.2, 1373.15), now));
        hammer.setMode(Forging.Op.DRAW, 20, 100, true);
        hammer.runSeconds(30);
        h.assertTrue(hammer.series() == 0, "no shaft, no blows");
        hammer.setSpeed(64);
        hammer.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.SQUARE, 0.05, 0.05, 0.2, 1073.15), now));
        hammer.runSeconds(30);
        var w = Smithy.read(hammer.panelItems().getStackInSlot(0), now);
        h.assertTrue(hammer.series() == 0 && w.defects().isEmpty(), "a piece below the forging range is not struck and does not crack");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void crankPressUpsetsAShortBillet(GameTestHelper h) {
        h.setBlock(A, PcBlocks.CRANK_PRESS.get().defaultBlockState());
        PowerHammerBlockEntity press = h.getBlockEntity(A);
        press.setSpeed(32);
        long now = h.getLevel().getGameTime();
        press.panelItems().setStackInSlot(0, Smithy.stack(Workpiece.bar(iron(), Workpiece.Section.SQUARE, 0.06, 0.06, 0.12, 1473.15), now));
        press.setMode(Forging.Op.UPSET, 80, 25, true);
        press.runSeconds(60);
        var w = Smithy.read(press.panelItems().getStackInSlot(0), now);
        h.assertTrue(w.aM() >= 0.080 && w.lengthM() < 0.12, "upset to 80 mm: " + w.aM() + " x " + w.lengthM());
        h.assertTrue(w.severity(dev.denfry.promptcritical.sim.manufacturing.PartDefect.BUCKLED) == 0, "a short billet does not buckle");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void wireMillDrawsCopperToSizeAndStopsBeforeItSnaps(GameTestHelper h) {
        h.setBlock(A, PcBlocks.WIRE_MILL.get().defaultBlockState());
        WireMillBlockEntity mill = h.getBlockEntity(A);
        mill.setSpeed(64);
        mill.panelItems().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT));
        int twoMm = indexOf(2.0);
        mill.setTarget(twoMm, true);
        mill.runSeconds(600);
        var wire = mill.panelItems().getStackInSlot(0).get(ManufacturingComponents.WIRE.get()).wire();
        h.assertTrue(mill.stop() == WireMillBlockEntity.STOP_DONE && Math.abs(wire.diameterM() - 0.002) < 1e-9,
                "drawn to 2 mm and stopped: " + wire + " stop " + mill.stop());
        mill.setTarget(indexOf(0.5), true);
        mill.runSeconds(2000);
        var hard = mill.panelItems().getStackInSlot(0).get(ManufacturingComponents.WIRE.get()).wire();
        h.assertTrue(mill.stop() == WireMillBlockEntity.STOP_ANNEAL, "the mill stops for an anneal: " + mill.stop());
        h.assertTrue(hard.strain() <= hard.metal().maxStrain && hard.diameterM() > 0.0005, "and the wire is whole: " + hard);
        h.succeed();
    }

    private static int indexOf(double mm) {
        for (int i = 0; i < DrawPlateBlockEntity.DIES_MM.length; i++) if (Math.abs(DrawPlateBlockEntity.DIES_MM[i] - mm) < 1e-9) return i;
        throw new IllegalArgumentException("no die " + mm);
    }

    @GameTest(template = SMALL)
    public static void heatingFurnaceHoldsForgingHeatOnCoal(GameTestHelper h) {
        h.setBlock(A, PcBlocks.HEATING_FURNACE.get().defaultBlockState());
        SmithyFurnaceBlockEntity f = h.getBlockEntity(A);
        long now = h.getLevel().getGameTime();
        f.panelItems().setStackInSlot(0, new ItemStack(Items.COAL, 16));
        f.panelItems().setStackInSlot(1, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.04, 0.04, 0.3, 293.15), now));
        f.setSetpointK(1473.15);
        for (int s = 0; s < 1800; s++) f.step(1);
        h.assertTrue(Math.abs(f.furnace().tempK() - 1473.15) < 15, "the chamber holds 1200 °C: " + f.furnace().tempK());
        var w = Smithy.read(f.panelItems().getStackInSlot(1), now);
        h.assertTrue(w.tempK() > 1373.15, "the piece is at forging heat: " + w.tempK());
        h.assertTrue(f.panelItems().getStackInSlot(0).getCount() < 16, "coal was burnt");
        h.succeed();
    }

    @GameTest(template = SMALL)
    public static void hardeningFurnaceQuenchesInItsTankAndTempersAtALowSetpoint(GameTestHelper h) {
        h.setBlock(A, PcBlocks.HARDENING_FURNACE.get().defaultBlockState());
        SmithyFurnaceBlockEntity f = h.getBlockEntity(A);
        long now = h.getLevel().getGameTime();
        f.panelItems().setStackInSlot(0, new ItemStack(Items.COAL, 32));
        f.panelItems().setStackInSlot(1, Smithy.stack(Workpiece.bar(steel45(), Workpiece.Section.ROUND, 0.012, 0.012, 0.2, 293.15), now));
        h.assertTrue(f.quench().isEmpty(), "an empty tank does not quench");
        f.tank().fill(new FluidStack(Fluids.WATER, 4000), IFluidHandler.FluidAction.EXECUTE);
        f.setSetpointK(1123.15);
        for (int s = 0; s < 1200; s++) f.step(1);
        var ev = f.quench();
        h.assertTrue(ev.contains(ForgeEvent.OK), "hardened in the tank: " + ev);
        var q = Smithy.read(f.panelItems().getStackInSlot(1), now);
        h.assertTrue(q.grade().state() == HeatTreatState.QUENCHED, "quenched");
        // уставка отпуска: камера остывает медленно, поэтому новый холодный цикл — та же печь после выдержки
        h.setBlock(A, PcBlocks.HARDENING_FURNACE.get().defaultBlockState());
        SmithyFurnaceBlockEntity t = h.getBlockEntity(A);
        t.panelItems().setStackInSlot(0, new ItemStack(Items.COAL, 8));
        t.panelItems().setStackInSlot(1, Smithy.stack(q.withTemp(293.15), now));
        t.setSetpointK(573.15);
        for (int s = 0; s < 3600; s++) t.step(1);
        var tempered = Smithy.read(t.panelItems().getStackInSlot(1), now);
        h.assertTrue(tempered.grade().state() == HeatTreatState.QUENCHED_TEMPERED && tempered.hardnessHrc() < q.hardnessHrc(),
                "a low setpoint tempers: " + tempered.hardnessHrc() + " < " + q.hardnessHrc());
        h.assertTrue(Quenching.Medium.WATER.grossmannH > Quenching.Medium.OIL.grossmannH, "sanity");
        h.succeed();
    }
}
