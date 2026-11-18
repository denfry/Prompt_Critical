package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.Uf6Charge;
import dev.denfry.promptcritical.mod.nuclear.Uf6CylinderItem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.uranium.AcidPlantBlockEntity;
import dev.denfry.promptcritical.mod.uranium.ConversionBlockEntity;
import dev.denfry.promptcritical.mod.uranium.FluorinationBlockEntity;
import dev.denfry.promptcritical.mod.uranium.FluorineCellBlockEntity;
import dev.denfry.promptcritical.mod.uranium.HfKilnBlockEntity;
import dev.denfry.promptcritical.mod.uranium.IonExchangeBlockEntity;
import dev.denfry.promptcritical.mod.uranium.LeachTankBlockEntity;
import dev.denfry.promptcritical.mod.uranium.PrecipitationBlockEntity;
import dev.denfry.promptcritical.mod.uranium.UraniumUnits;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Передел урана от руды до UF₆: кислота из колчедана, HF из шпата, выщелачивание с пиролюзитом, сорбция, осаждение
 * ДУА, три печи сухой конверсии, фторные ванны на выпрямителе, фторирование в баллон с природным составом. Трубы
 * заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class UraniumFrontEndGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos AT = new BlockPos(6, 2, 6);

    private UraniumFrontEndGameTests() {
    }

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void give(IItemHandler h, Item item, int n) {
        ItemStack left = new ItemStack(item, n);
        for (int i = 0; i < h.getSlots() && !left.isEmpty(); i++) left = h.insertItem(i, left, false);
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void acidPlantAndHfKiln(GameTestHelper h) {
        BlockPos kiln = new BlockPos(2, 2, 2);
        h.setBlock(AT, north(PcBlocks.ACID_PLANT.get()));
        h.setBlock(kiln, north(PcBlocks.HF_KILN.get()));
        h.startSequence()
                .thenExecute(() -> {
                    give(((AcidPlantBlockEntity) h.getBlockEntity(AT)).input(), PcBlocks.PYRITE_ORE.get().asItem(), 8);
                    give(((HfKilnBlockEntity) h.getBlockEntity(kiln)).input(), PcBlocks.FLUORITE_ORE.get().asItem(), 8);
                })
                .thenWaitUntil(() -> {
                    AcidPlantBlockEntity a = h.getBlockEntity(AT);
                    fill(a.fluids(Direction.NORTH), Fluids.WATER, 1000);
                    h.assertTrue(a.acid().amount() > 0.5, "sulphuric acid " + a.acid().amount() + " L");
                    h.assertTrue(a.plant().conversion() > 0.95, "contact conversion " + a.plant().conversion());
                    HfKilnBlockEntity k = h.getBlockEntity(kiln);
                    IFluidHandler in = k.fluids(Direction.NORTH);
                    fill(in, PcFluids.SULFURIC_ACID.source.get(), 500);
                    fill(in, PcFluids.SALES_GAS.source.get(), 200);
                    h.assertTrue(k.hf().amount() > 0.2, "anhydrous HF " + k.hf().amount() + " L");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1500)
    public static void leachingDissolvesUraniumWithPyrolusite(GameTestHelper h) {
        h.setBlock(AT, north(PcBlocks.LEACH_TANK.get()));
        h.startSequence()
                .thenExecute(() -> {
                    LeachTankBlockEntity l = h.getBlockEntity(AT);
                    give(l.input(), PcItems.GROUND_URANIUM_ORE.get(), 16);
                    give(l.input(), PcItems.RAW_PYROLUSITE.get(), 4);
                })
                .thenWaitUntil(() -> {
                    LeachTankBlockEntity l = h.getBlockEntity(AT);
                    IFluidHandler in = l.fluids(Direction.NORTH);
                    fill(in, PcFluids.SULFURIC_ACID.source.get(), 100);
                    fill(in, Fluids.WATER, 1000);
                    h.assertTrue(l.pls().amount() > 20, "pregnant solution " + l.pls().amount() + " L");
                    h.assertTrue(l.tailings().amount() > 0, "tailings slurry");
                    h.assertTrue(l.leach().ledger().inKg() >= UraniumUnits.ORE_KG * UraniumUnits.ORE_U - 1e-9, "uranium fed");
                    h.assertTrue(l.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.PREGNANT_LEACH_SOLUTION.source.get(), "pregnant solution from the top");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void resinSorbsAndAmmoniaPrecipitatesYellowcake(GameTestHelper h) {
        BlockPos adu = new BlockPos(2, 2, 2);
        h.setBlock(AT, north(PcBlocks.URANIUM_ION_EXCHANGE.get()));
        h.setBlock(adu, north(PcBlocks.PRECIPITATION_TANK.get()));
        h.startSequence()
                .thenExecute(() -> give(((IonExchangeBlockEntity) h.getBlockEntity(AT)).input(), PcItems.ION_EXCHANGE_RESIN_SBA.get(), 4))
                .thenWaitUntil(() -> {
                    IonExchangeBlockEntity x = h.getBlockEntity(AT);
                    fill(x.fluids(Direction.NORTH), PcFluids.PREGNANT_LEACH_SOLUTION.source.get(), 500);
                    h.assertTrue(x.column().loadedKg() > 0.05 && x.barren().amount() > 50,
                            "uranium on resin " + x.column().loadedKg() + " kg, barren " + x.barren().amount() + " L");
                    h.assertTrue(x.column().ledger().lossKg() < x.column().ledger().inKg() * 0.01, "no breakthrough on fresh resin");
                    PrecipitationBlockEntity p = h.getBlockEntity(adu);
                    IFluidHandler in = p.fluids(Direction.NORTH);
                    fill(in, PcFluids.URANIUM_ELUATE.source.get(), 500);
                    fill(in, PcFluids.AMMONIA.source.get(), 50);
                    h.assertTrue(p.output().getStackInSlot(0).is(PcItems.YELLOWCAKE.get()) && p.output().getStackInSlot(0).getCount() >= 2,
                            "yellowcake bags " + p.output().getStackInSlot(0));
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 800)
    public static void dryConversionMakesGreenSalt(GameTestHelper h) {
        BlockPos calc = new BlockPos(2, 2, 2), red = new BlockPos(6, 2, 2), hf = new BlockPos(10, 2, 2);
        h.setBlock(calc, north(PcBlocks.ROTARY_CALCINER.get()));
        h.setBlock(red, north(PcBlocks.REDUCTION_FURNACE.get()));
        h.setBlock(hf, north(PcBlocks.HYDROFLUORINATION_REACTOR.get()));
        h.startSequence()
                .thenExecute(() -> {
                    give(((ConversionBlockEntity) h.getBlockEntity(calc)).input(), PcItems.YELLOWCAKE.get(), 8);
                    give(((ConversionBlockEntity) h.getBlockEntity(red)).input(), PcItems.UO3_POWDER.get(), 8);
                    give(((ConversionBlockEntity) h.getBlockEntity(hf)).input(), PcItems.UO2_POWDER.get(), 8);
                })
                .thenWaitUntil(() -> {
                    ConversionBlockEntity c = h.getBlockEntity(calc), r = h.getBlockEntity(red), f = h.getBlockEntity(hf);
                    fill(c.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 200);
                    fill(r.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 200);
                    fill(r.fluids(Direction.NORTH), PcFluids.HYDROGEN.source.get(), 200);
                    fill(f.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 200);
                    fill(f.fluids(Direction.NORTH), PcFluids.HYDROGEN_FLUORIDE.source.get(), 200);
                    h.assertTrue(c.output().getStackInSlot(0).is(PcItems.UO3_POWDER.get()), "UO3 from the calciner");
                    h.assertTrue(r.output().getStackInSlot(0).is(PcItems.UO2_POWDER.get()), "UO2 from hydrogen reduction");
                    h.assertTrue(f.output().getStackInSlot(0).is(PcItems.UF4_POWDER.get()), "UF4 from hydrofluorination");
                    h.assertTrue(f.stage().ledger().lossKg() < f.stage().ledger().inKg() * 0.01, "conversion loses under 1 %");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void fluorinationFillsACylinderWithNaturalUf6(GameTestHelper h) {
        h.setBlock(AT, north(PcBlocks.FLUORINATION_REACTOR.get()));
        h.startSequence()
                .thenExecute(() -> {
                    FluorinationBlockEntity r = h.getBlockEntity(AT);
                    give(r.input(), PcItems.UF6_CYLINDER_5A.get(), 1);
                    give(r.input(), PcItems.UF4_POWDER.get(), 32);
                })
                .thenWaitUntil(() -> {
                    FluorinationBlockEntity r = h.getBlockEntity(AT);
                    fill(r.fluids(Direction.NORTH), PcFluids.FLUORINE.source.get(), 200);
                    ItemStack cyl = ItemStack.EMPTY;
                    for (int i = 0; i < r.input().getSlots(); i++)
                        if (r.input().getStackInSlot(i).getItem() instanceof Uf6CylinderItem) cyl = r.input().getStackInSlot(i);
                    Uf6Charge c = Uf6CylinderItem.charge(cyl);
                    h.assertTrue(c.uf6Kg() > 0.5, "UF6 in the cylinder " + c.uf6Kg() + " kg");
                    h.assertTrue(Math.abs(c.u235() - UraniumUnits.NATURAL_U235) < 1e-9, "natural enrichment " + c.u235());
                    h.assertTrue(r.ventKgS() == 0, "nothing vented while a cylinder takes the product");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void fluorineCellsRunOnTheRectifier(GameTestHelper h) {
        BlockPos gen = new BlockPos(5, 2, 2), rect = new BlockPos(4, 2, 2), cells = new BlockPos(2, 2, 6);
        h.setBlock(gen, north(PcBlocks.SHAFT_GENERATOR.get()));
        h.setBlock(gen.above(), north(PcBlocks.GENERATOR_EXCITER.get()));
        BlockPos motor = gen.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(rect, PcBlocks.RECTIFIER_UNIT.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        h.setBlock(cells, north(PcBlocks.FLUORINE_CELL.get()));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(gen)).unit() != null, "generator twin"))
                .thenExecute(() -> {
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) h.getBlockEntity(gen)).unit();
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    FluorineCellBlockEntity f = h.getBlockEntity(cells);
                    f.cells().setCurrentKa(2); // 12 ванн × 2 кА × ≈ 7,6 В ≈ 0,18 МВт
                    f.use(h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL));
                })
                .thenWaitUntil(() -> {
                    FluorineCellBlockEntity f = h.getBlockEntity(cells);
                    fill(f.fluids(Direction.NORTH), PcFluids.HYDROGEN_FLUORIDE.source.get(), 100);
                    h.assertTrue(f.cells().f2KgS() > 0, "cells on load at " + f.cells().voltageV() + " V");
                    h.assertTrue(f.fluorine().amount() > 0.1, "fluorine " + f.fluorine().amount() + " nm3");
                    h.assertTrue(!f.cells().anodeEffect(), "HF topped up: no anode effect");
                })
                .thenSucceed();
    }
}
