package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.ActivityData;
import dev.denfry.promptcritical.mod.environment.SpentFuel;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.reprocessing.PurexCellBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Линия PUREX в мире: каньон с аппаратами принимает облучённое топливо с партией, выдаёт регенерат с U-236 и копит
 * стекло; без осадителя оксалата плутоний в буфере доходит до СЦР, и раствор сливается. Проверяются только свои
 * объекты, без глобальных полей мира.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PurexGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CELL = new BlockPos(6, 2, 6);

    private PurexGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos p, Block b) {
        var st = b.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        h.setBlock(p, st);
    }

    private static void line(GameTestHelper h, boolean oxalate) {
        place(h, CELL, PcBlocks.PUREX_CELL.get());
        place(h, new BlockPos(4, 2, 6), PcBlocks.SHEARING_MACHINE.get());
        place(h, new BlockPos(8, 2, 6), PcBlocks.DISSOLVER.get());
        place(h, new BlockPos(6, 2, 4), PcBlocks.MIXER_SETTLER.get());
        place(h, new BlockPos(6, 2, 8), PcBlocks.MIXER_SETTLER.get());
        if (oxalate) place(h, new BlockPos(4, 2, 4), PcBlocks.OXALATE_PRECIPITATOR.get());
        place(h, new BlockPos(8, 2, 8), PcBlocks.DENITRATION_REACTOR.get());
        place(h, new BlockPos(4, 2, 8), PcBlocks.VITRIFICATION_MELTER.get());
    }

    /** Облучённое топливо: 30 кг урана после 33 МВт·сут/кг, три года выдержки. */
    private static ItemStack spent(GameTestHelper h, double puKg) {
        ItemStack s = new ItemStack(PcItems.UO2_POWDER.get());
        s.set(FuelLot.TYPE.get(), new FuelLot(30, 0.0084, 0.00025, 0.95, 0, "", "", 33, 0.0041, puKg, 0.57, 0.23, 0.13, 0.07));
        long tick = h.getLevel().getGameTime() - (long) (3 * 365.25 * 86400 / ActivityData.GAME_S_PER_TICK);
        s.set(ActivityData.ACTIVITY.get(), SpentFuel.discharge(31, 33, 38, 3, tick));
        return s;
    }

    private static void feed(PurexCellBlockEntity c) {
        IFluidHandler in = c.fluids(Direction.NORTH);
        in.fill(new FluidStack(PcFluids.NITRIC_ACID.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.KEROSENE.source.get(), 100), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void lineMakesReprocessedUraniumAndGlass(GameTestHelper h) {
        line(h, true);
        h.startSequence()
                .thenIdle(45) // аппараты находятся при обходе раз в 40 тиков
                .thenExecute(() -> {
                    PurexCellBlockEntity c = h.getBlockEntity(CELL);
                    c.input().insertItem(0, spent(h, 0.28), false);
                    c.input().insertItem(PurexCellBlockEntity.CHARCOAL_SLOT, new ItemStack(Items.CHARCOAL, 4), false);
                })
                .thenWaitUntil(() -> {
                    PurexCellBlockEntity c = h.getBlockEntity(CELL);
                    feed(c);
                    h.assertTrue(c.plant().ledger().inKg() > 29, "fuel taken: U in " + c.plant().ledger().inKg() + " kg");
                    ItemStack u = c.output().getStackInSlot(0);
                    h.assertTrue(u.is(PcItems.UO3_POWDER.get()) && u.getCount() >= 20, "reprocessed uranium " + u);
                    FuelLot l = u.get(FuelLot.TYPE.get());
                    h.assertTrue(l != null && l.u236() > 0.003, "U-236 in reprocessed uranium: " + (l == null ? 0 : l.u236()));
                    h.assertTrue(c.glassKg() > 5, "high-level waste glass " + c.glassKg() + " kg");
                    h.assertTrue(c.plant().ledger().puInKg() > 0.27, "plutonium entered: " + c.plant().ledger().puInKg());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void stalledOxalateGoesCritical(GameTestHelper h) {
        line(h, false);
        h.startSequence()
                .thenIdle(45)
                .thenExecute(() -> h.<PurexCellBlockEntity>getBlockEntity(CELL).input().insertItem(0, spent(h, 0.9), false))
                .thenWaitUntil(() -> {
                    PurexCellBlockEntity c = h.getBlockEntity(CELL);
                    feed(c);
                    h.assertTrue(c.plant().puInFlowKg() > 0.5, "plutonium flowing to the buffer: " + c.plant().puInFlowKg());
                })
                // осадителя нет: весь плутоний порции разом в буфер (ускорение того, что сделали бы минуты)
                .thenExecute(() -> h.<PurexCellBlockEntity>getBlockEntity(CELL).plant().meter(10))
                .thenIdle(2)
                .thenExecute(() -> {
                    PurexCellBlockEntity c = h.getBlockEntity(CELL);
                    h.assertTrue(c.plant().puTankKg() == 0, "after the excursion the buffer is dumped: " + c.plant().puTankKg());
                    h.assertTrue(c.plant().ledger().puLossKg() > 0.5, "dumped plutonium counted as loss: " + c.plant().ledger().puLossKg());
                })
                .thenSucceed();
    }
}
