package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.heavywater.GsExchangeTowerBlockEntity;
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterColumnBlockEntity;
import dev.denfry.promptcritical.mod.npp.CanduPanels;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Тяжёлая вода T3: сероводородный завод на берегу делает воду 15 % D из озера, без озера стоит; колонна доводки
 * превращает её в реакторную D₂O и не берёт природную воду; CANDU ставится сухим, заливается трубами (каландр сбоку,
 * контур снизу), разбавляется лёгкой водой и сливает замедлитель в бак сорта; ведро тяжёлой воды льётся в мир.
 * Трубы заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HeavyWaterGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos TOWER = new BlockPos(6, 3, 6), COLUMN = new BlockPos(3, 3, 3), CANDU = new BlockPos(6, 2, 6);

    private HeavyWaterGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Озеро вокруг основания завода: каменное дно и слой воды-источника, кроме клеток самого завода. */
    private static void lake(GameTestHelper h) {
        for (int x = 1; x <= 11; x++)
            for (int z = 1; z <= 11; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                boolean inside = x >= 2 && x <= 10 && z >= 2 && z <= 10;
                boolean tower = Math.abs(x - TOWER.getX()) <= 1 && Math.abs(z - TOWER.getZ()) <= 1;
                h.setBlock(new BlockPos(x, 1, z), inside && !tower ? Blocks.WATER : Blocks.STONE);
            }
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void gsPlantMakesEnrichedWaterFromALake(GameTestHelper h) {
        lake(h);
        place(h, TOWER, PcBlocks.GS_EXCHANGE_TOWER.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((GsExchangeTowerBlockEntity) h.getBlockEntity(TOWER)).setRunning(true))
                .thenWaitUntil(() -> {
                    GsExchangeTowerBlockEntity t = h.getBlockEntity(TOWER);
                    h.assertTrue(t.lakeBlocks() >= GsExchangeTowerBlockEntity.LAKE_BLOCKS, "lake blocks " + t.lakeBlocks());
                    h.assertTrue(t.product().amount() > 1.5, "15 % water " + t.product().amount() + " L");
                    double r = t.plant().lastRecovery();
                    h.assertTrue(r > 0.18 && r < 0.23, "deuterium recovery " + r);
                    FluidStack out = t.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE);
                    h.assertTrue(out.getFluid() == PcFluids.DEUTERIUM_ENRICHED_WATER.source.get(), "product into pipes: " + out.getFluid());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void gsPlantWithoutALakeStands(GameTestHelper h) {
        place(h, TOWER, PcBlocks.GS_EXCHANGE_TOWER.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((GsExchangeTowerBlockEntity) h.getBlockEntity(TOWER)).setRunning(true))
                .thenIdle(60)
                .thenExecute(() -> {
                    GsExchangeTowerBlockEntity t = h.getBlockEntity(TOWER);
                    h.assertTrue(t.lakeBlocks() == 0, "no lake: " + t.lakeBlocks());
                    h.assertTrue(t.product().amount() == 0, "nothing made without feed water: " + t.product().amount());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void finishingColumnMakesReactorGrade(GameTestHelper h) {
        place(h, COLUMN, PcBlocks.HEAVY_WATER_COLUMN.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    HeavyWaterColumnBlockEntity c = h.getBlockEntity(COLUMN);
                    IFluidHandler port = c.fluids(Direction.NORTH);
                    h.assertTrue(port.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                            "natural water is too lean for the column");
                    int in = port.fill(new FluidStack(PcFluids.DEUTERIUM_ENRICHED_WATER.source.get(), 8000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(in == 8000, "the still takes 15 % water: " + in);
                })
                .thenWaitUntil(() -> {
                    HeavyWaterColumnBlockEntity c = h.getBlockEntity(COLUMN);
                    h.assertTrue(c.product().amount() > 1.5, "reactor grade " + c.product().amount() + " L");
                    h.assertTrue(c.column().lastRecovery() > 0.8, "recovery " + c.column().lastRecovery());
                    FluidStack out = c.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE);
                    h.assertTrue(out.getFluid() == PcFluids.HEAVY_WATER.source.get(), "heavy water into pipes: " + out.getFluid());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void canduIsFilledDowngradedAndDrained(GameTestHelper h) {
        place(h, CANDU, PcBlocks.CANDU_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    CanduReactorBlockEntity r = h.getBlockEntity(CANDU);
                    h.assertTrue(r.reactor().moderatorKg() == 0, "a new CANDU is dry");
                    var heavy = PcFluids.HEAVY_WATER.source.get();
                    int mod = r.fluids(Direction.NORTH).fill(new FluidStack(heavy, 200_000), IFluidHandler.FluidAction.EXECUTE);
                    int cool = r.fluids(Direction.DOWN).fill(new FluidStack(heavy, 10_000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(r.reactor().moderatorLevel() > 0.99, "calandria full after " + mod + " L");
                    h.assertTrue(r.reactor().coolantFill() > 0.99, "circuit full after " + cool + " L");
                    h.assertTrue(mod > 90_000 && mod < 110_000 && cool > 2_000 && cool < 4_000, "litres " + mod + " / " + cool);
                    // лишнее не влезает
                    h.assertTrue(r.fluids(Direction.NORTH).fill(new FluidStack(heavy, 1000), IFluidHandler.FluidAction.EXECUTE) == 0, "full calandria");
                    // слив кубометра — затем обычная вода в освободившееся место
                    h.assertTrue(r.drainModerator(null), "drained");
                    h.assertTrue(r.drainTank(HeavyWaterGrade.REACTOR).amount() > 990, "reactor grade drained into its tank");
                    r.fluids(Direction.EAST).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                    double x = r.reactor().moderator().purity();
                    h.assertTrue(x < HeavyWater.REACTOR_GRADE_X - 0.005 && x > 0.98, "light water dilutes the moderator: " + x);
                    int[] v = new int[CanduPanels.CHANNELS];
                    r.readPanel(v);
                    h.assertTrue((v[CanduPanels.FLAGS] & 1 << CanduPanels.F_DOWNGRADED) != 0, "the desk lights D2O diluted");
                    h.assertTrue(r.drainModerator(null), "drained again");
                    h.assertTrue(r.drainTank(HeavyWaterGrade.DOWNGRADED).amount() > 990, "now it drains as downgraded");
                    FluidStack out = r.fluids(Direction.NORTH).drain(500, IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(!out.isEmpty(), "drain tanks give to pipes");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void heavyWaterBucketPoursAndEmptiesIntoTanks(GameTestHelper h) {
        ItemStack bucket = new ItemStack(PcItems.HEAVY_WATER_BUCKET.get());
        IFluidHandlerItem cap = bucket.getCapability(Capabilities.FluidHandler.ITEM);
        h.assertTrue(cap != null, "the bucket is a fluid container");
        FluidStack inside = cap.getFluidInTank(0);
        h.assertTrue(inside.getFluid() == PcFluids.HEAVY_WATER.source.get() && inside.getAmount() == 1000, "1000 mB of heavy water: " + inside);
        BlockPos at = new BlockPos(2, 2, 2);
        h.setBlock(at.below(), Blocks.STONE);
        boolean poured = ((BucketItem) bucket.getItem()).emptyContents(null, h.getLevel(), h.absolutePos(at), null);
        h.assertTrue(poured, "poured into the world");
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(at)).getType() == PcFluids.HEAVY_WATER.source.get(), "a heavy water source");
        h.succeed();
    }
}
