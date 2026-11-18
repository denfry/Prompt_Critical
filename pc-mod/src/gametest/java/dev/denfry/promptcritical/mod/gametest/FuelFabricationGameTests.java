package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.nuclear.AssemblyJigBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.nuclear.FuelPanels;
import dev.denfry.promptcritical.mod.nuclear.PelletPressBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.RodStationBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.SinteringFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.nuclear.Uo2ConversionBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Топливный цех: UF₆ 4,4 % превращается в мешки порошка UO₂ с той же партией; пресс вплотную к печи даёт поддоны
 * таблеток ВВЭР-1000 с плотностью в допуске; участок снаряжения делает твэлы с гелием; стапель собирает ТВС ВВЭР-440
 * из 126 твэлов, и уран в ней сходится с ураном твэлов.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FuelFabricationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos UNIT = new BlockPos(5, 2, 5), FURNACE = new BlockPos(6, 2, 5);
    private static final double X = 0.044;

    private FuelFabricationGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void fill(IFluidHandler in, net.minecraft.world.level.material.Fluid fluid, int mb) {
        in.fill(new FluidStack(fluid, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void conversionMakesEnrichedPowder(GameTestHelper h) {
        place(h, UNIT, PcBlocks.UO2_CONVERSION.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    Uo2ConversionBlockEntity c = h.getBlockEntity(UNIT);
                    c.items().setStackInSlot(FuelPanels.C_SLOT_UF6,
                            Uf6CylinderItem.fill(new ItemStack(PcItems.UF6_CYLINDER_30B.get()), 100, X, 0.0004));
                    c.setRunning(true);
                })
                .thenWaitUntil(() -> {
                    Uo2ConversionBlockEntity c = h.getBlockEntity(UNIT);
                    fill(c.fluids(Direction.UP), PcFluids.AMMONIA.source.get(), 1000);
                    fill(c.fluids(Direction.UP), PcFluids.HYDROGEN.source.get(), 1000);
                    fill(c.fluids(Direction.UP), PcFluids.BOILER_FEEDWATER.source.get(), 2000);
                    ItemStack bag = c.items().getStackInSlot(FuelPanels.C_SLOT_POWDER);
                    h.assertTrue(bag.is(PcItems.UO2_POWDER.get()) && bag.getCount() >= 2, "UO2 bags: " + bag);
                    FuelLot l = FuelLot.of(bag);
                    h.assertTrue(Math.abs(l.u235() - X) < 1e-9 && l.uKg() == 1, "lot carried: " + l);
                    double in = c.balance().u235().inKg(), out = c.balance().u235().outKg(), loss = c.balance().u235().lossKg();
                    h.assertTrue(out > 0 && loss < 0.01 * in, "U-235 books: in " + in + ", out " + out + ", loss " + loss);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void pressAndFurnaceMakePelletTrays(GameTestHelper h) {
        place(h, UNIT, PcBlocks.PELLET_PRESS.get());
        place(h, FURNACE, PcBlocks.SINTERING_FURNACE.get());
        BlockPos motor = UNIT.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    PelletPressBlockEntity p = h.getBlockEntity(UNIT);
                    p.hopper().setStackInSlot(0, FuelLot.powder(1, X, 0.0004).on(new ItemStack(PcItems.UO2_POWDER.get(), 16)));
                    p.setDesign(FuelDesign.VVER_1000);
                    p.setRunning(true);
                    ((SinteringFurnaceBlockEntity) h.getBlockEntity(FURNACE)).setRunning(true);
                })
                .thenWaitUntil(() -> {
                    SinteringFurnaceBlockEntity f = h.getBlockEntity(FURNACE);
                    fill(f.fluids(Direction.UP), PcFluids.HYDROGEN.source.get(), 2000);
                    ItemStack tray = f.items().getStackInSlot(FuelPanels.S_SLOT_TRAYS);
                    h.assertTrue(tray.is(PcItems.FUEL_PELLET_TRAY.get()), "pellet tray made: " + tray);
                    FuelLot l = FuelLot.of(tray);
                    h.assertTrue(FuelDesign.VVER_1000.densityInSpec(l.density()), "density in spec: " + l.density());
                    h.assertTrue(l.designOrNull() == FuelDesign.VVER_1000 && l.count() == FuelDesign.VVER_1000.pelletsPerRod(), "VVER pellets");
                    h.assertTrue(Math.abs(l.uKg() - FuelDesign.VVER_1000.uKgPerRod(l.density(), X)) < 1e-9, "one rod of uranium per tray");
                })
                .thenSucceed();
    }

    private static ItemStack trays(FuelDesign d, int n) {
        double density = 0.955;
        return new FuelLot(d.uKgPerRod(density, X), X, 0.0004, density, d.pelletsPerRod(), d.name(), "")
                .on(new ItemStack(PcItems.FUEL_PELLET_TRAY.get(), n));
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void rodStationSealsRodsWithHelium(GameTestHelper h) {
        place(h, UNIT, PcBlocks.ROD_LOADING_STATION.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    RodStationBlockEntity r = h.getBlockEntity(UNIT);
                    r.items().setStackInSlot(FuelPanels.R_SLOT_TRAYS, trays(FuelDesign.VVER_1000, 4));
                    r.items().setStackInSlot(FuelPanels.R_SLOT_TUBES, new ItemStack(PcItems.CLADDING_TUBE.get(), 8));
                    fill(r.fluids(Direction.UP), PcFluids.HELIUM.source.get(), 10);
                    r.setRunning(true);
                })
                .thenWaitUntil(() -> {
                    RodStationBlockEntity r = h.getBlockEntity(UNIT);
                    ItemStack rods = r.items().getStackInSlot(FuelPanels.R_SLOT_RODS);
                    h.assertTrue(rods.is(PcItems.FUEL_ROD.get()) && rods.getCount() >= 2, "rods: " + rods);
                    FuelLot l = FuelLot.of(rods);
                    h.assertTrue("he".equals(l.gas()) && l.designOrNull() == FuelDesign.VVER_1000, "helium-filled VVER rod: " + l);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void jigAssemblesAVver440Assembly(GameTestHelper h) {
        place(h, UNIT, PcBlocks.ASSEMBLY_JIG.get());
        FuelDesign d = FuelDesign.VVER_440;
        double perRod = d.uKgPerRod(0.955, X);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    AssemblyJigBlockEntity j = h.getBlockEntity(UNIT);
                    j.setDesign(d);
                    j.items().setStackInSlot(FuelPanels.J_SLOT_GRIDS, new ItemStack(PcItems.SPACER_GRID.get(), d.grids));
                    j.items().setStackInSlot(FuelPanels.J_SLOT_TUBES, new ItemStack(PcItems.CLADDING_TUBE.get(), AssemblyJigBlockEntity.tubesFor(d)));
                    j.setRunning(true);
                })
                .thenWaitUntil(() -> {
                    AssemblyJigBlockEntity j = h.getBlockEntity(UNIT);
                    ItemStack rods = j.items().getStackInSlot(FuelPanels.J_SLOT_RODS);
                    if (rods.isEmpty() && j.assemblies() == 0)
                        j.items().setStackInSlot(FuelPanels.J_SLOT_RODS, new FuelLot(perRod, X, 0.0004, 0.955, d.pelletsPerRod(), d.name(), "he")
                                .on(new ItemStack(PcItems.FUEL_ROD.get(), 64)));
                    ItemStack fa = j.items().getStackInSlot(FuelPanels.J_SLOT_OUT);
                    h.assertTrue(fa.is(PcItems.FUEL_ASSEMBLY.get()), "assembly made, rods loaded " + j.loaded());
                    FuelLot l = FuelLot.of(fa);
                    h.assertTrue(l.count() == d.rods && Math.abs(l.uKg() - d.rods * perRod) < 1e-6, "uranium of 126 rods: " + l.uKg());
                    h.assertTrue(Math.abs(l.u235() - X) < 1e-9 && "he".equals(l.gas()), "lot " + l);
                    h.assertTrue(j.balance().holdupU235Kg() < 1e-9, "nothing left on the books");
                })
                .thenSucceed();
    }
}
