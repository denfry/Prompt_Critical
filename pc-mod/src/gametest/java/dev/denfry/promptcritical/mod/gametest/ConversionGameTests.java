package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.refinery.DesalterBlockEntity;
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
}
