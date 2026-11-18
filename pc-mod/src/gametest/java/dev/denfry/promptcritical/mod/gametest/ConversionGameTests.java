package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.refinery.DelayedCokerBlockEntity;
import dev.denfry.promptcritical.mod.refinery.DesalterBlockEntity;
import dev.denfry.promptcritical.mod.refinery.VacuumColumnBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Подготовка и углубление переработки (09 §9.7.3, §9.9–9.10): ЭЛОУ, вакуумная колонна, каткрекинг, амины, смешение
 * бензина, лаборатория. Трубы заменяет заливка в патрубки, сеть — стендовое питание.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConversionGameTests {
    static final String LARGE = "gametest/empty_12x12x12";

    private ConversionGameTests() {
    }

    static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    /** ЭЛОУ под напряжением: нефть в печи — меньше 5 мг/л соли, солёная вода — снизу. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void desalterFeedsFurnaceWithDesaltedCrude(GameTestHelper h) {
        BlockPos ds = new BlockPos(2, 2, 5), furnace = new BlockPos(8, 2, 5);
        h.setBlock(ds, north(PcBlocks.ELECTROSTATIC_DESALTER.get()));
        h.setBlock(furnace, north(PcBlocks.TUBE_FURNACE.get()));
        h.startSequence()
                .thenExecute(() -> ((DesalterBlockEntity) h.getBlockEntity(ds)).setBenchPower(true))
                .thenWaitUntil(() -> {
                    DesalterBlockEntity d = h.getBlockEntity(ds);
                    fill(d.fluids(Direction.NORTH), PcFluids.CRUDE_OIL.source.get(), 8000);
                    fill(d.fluids(Direction.UP), PcFluids.RAW_WATER.source.get(), 8000);
                    TubeFurnaceBlockEntity f = h.getBlockEntity(furnace);
                    h.assertTrue(f.crude().amount() > 200, "desalted crude in furnace " + f.crude().amount() + " L");
                    h.assertTrue(f.crudeSaltMgL() < 5, "salt " + f.crudeSaltMgL() + " mg/l");
                    h.assertTrue(d.brine().amount() > 10, "brine " + d.brine().amount() + " L");
                })
                .thenSucceed();
    }

    /** Без напряжения на электродах соль почти не уходит: в печь приходит больше 100 мг/л. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void unpoweredDesalterBarelyDesalts(GameTestHelper h) {
        BlockPos ds = new BlockPos(2, 2, 5), furnace = new BlockPos(8, 2, 5);
        h.setBlock(ds, north(PcBlocks.ELECTROSTATIC_DESALTER.get()));
        h.setBlock(furnace, north(PcBlocks.TUBE_FURNACE.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    DesalterBlockEntity d = h.getBlockEntity(ds);
                    fill(d.fluids(Direction.NORTH), PcFluids.CRUDE_OIL.source.get(), 8000);
                    fill(d.fluids(Direction.UP), PcFluids.RAW_WATER.source.get(), 8000);
                    TubeFurnaceBlockEntity f = h.getBlockEntity(furnace);
                    h.assertFalse(d.powered(), "no grid, no bench power");
                    h.assertTrue(f.crude().amount() > 200, "crude in furnace " + f.crude().amount() + " L");
                    h.assertTrue(f.crudeSaltMgL() > 100, "salt " + f.crudeSaltMgL() + " mg/l");
                })
                .thenSucceed();
    }

    /**
     * Вакуумная колонна с двумя эжекторами (5 кПа): газойль сверху, гудрон снизу, гудрон — в коксование, которое
     * берёт его первым.
     */
    @GameTest(template = LARGE, timeoutTicks = 800)
    public static void vacuumColumnSplitsMazutAndFeedsTheCoker(GameTestHelper h) {
        BlockPos vc = new BlockPos(5, 2, 5), coker = new BlockPos(5, 2, 9);
        h.setBlock(vc, north(PcBlocks.VACUUM_COLUMN.get()));
        h.setBlock(vc.east(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.setBlock(vc.west(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.setBlock(coker, north(PcBlocks.DELAYED_COKER.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    fill(v.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.BOILER_FEEDWATER.source.get(), 8000);
                    h.assertTrue(v.ejectors() == 2, "ejectors " + v.ejectors());
                    h.assertTrue(v.pressureKpa() == 5, "flash zone " + v.pressureKpa() + " kPa");
                    h.assertTrue(v.vgo().amount() > 300, "vacuum gas oil " + v.vgo().amount() + " L");
                    h.assertTrue(v.residue().amount() > 200, "tar " + v.residue().amount() + " L");
                    h.assertTrue(v.residueCcr() > 0.15, "tar CCR " + v.residueCcr());
                })
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    DelayedCokerBlockEntity c = h.getBlockEntity(coker);
                    int tar = (int) v.residue().amount();
                    fill(c.fluids(Direction.NORTH), PcFluids.VACUUM_RESIDUE.source.get(), tar);
                    fill(c.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 2000);
                    h.assertTrue(c.last().coke() > 0.3, "coke yield on tar " + c.last().coke());
                    h.assertTrue(c.feed().amount() >= 1999, "mazut waits while tar lasts: " + c.feed().amount());
                })
                .thenSucceed();
    }

    /** Без питательной воды эжекторы не работают: атмосферное давление, газойля в разы меньше. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void vacuumColumnWithoutSteamRunsAtAtmosphere(GameTestHelper h) {
        BlockPos vc = new BlockPos(5, 2, 5);
        h.setBlock(vc, north(PcBlocks.VACUUM_COLUMN.get()));
        h.setBlock(vc.east(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    fill(v.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 8000);
                    h.assertTrue(v.residue().amount() > 200, "tar " + v.residue().amount() + " L");
                    h.assertTrue(v.pressureKpa() > 100, "no steam, no vacuum: " + v.pressureKpa());
                    h.assertTrue(v.result().vgo() < 0.3, "atmospheric gas oil yield " + v.result().vgo());
                })
                .thenSucceed();
    }
}
