package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ops.FireService;
import dev.denfry.promptcritical.mod.ops.FireWaterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.ops.FireSuppression.FireClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Пожар класса B и пена (13 §13.5.4): горящий битум — пролив; вода из гидранта его не тушит, пена с дозатором тушит;
 * пожарный насос пускается сам при разборе воды.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoamGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos WATER = new BlockPos(1, 1, 1), PUMP = new BlockPos(2, 1, 1), HYDRANT = new BlockPos(5, 1, 5),
            PROPORTIONER = new BlockPos(3, 1, 5);

    private FoamGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos p, Block b) {
        h.setBlock(p, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static int fires(GameTestHelper h) {
        return FireService.fires(h.getLevel(), h.absolutePos(new BlockPos(7, 2, 7)), 3).size();
    }

    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void waterSpreadsABurningPoolAndFoamPutsItOut(GameTestHelper h) {
        h.setBlock(WATER, Blocks.WATER.defaultBlockState());
        place(h, PUMP, PcBlocks.FIRE_WATER_PUMP.get());
        place(h, HYDRANT, PcBlocks.FIRE_HYDRANT.get());
        for (int x = 6; x <= 8; x++)
            for (int z = 6; z <= 8; z++) {
                h.setBlock(new BlockPos(x, 1, z), PcBlocks.NATURAL_BITUMEN.get().defaultBlockState());
                h.setBlock(new BlockPos(x, 2, z), Blocks.FIRE.defaultBlockState());
            }
        h.startSequence()
                .thenExecute(() -> {
                    ((FireWaterBlockEntity) h.getBlockEntity(PUMP)).setBenchPower(true);
                    h.assertTrue(FireService.classAt(h.getLevel(), h.absolutePos(new BlockPos(7, 2, 7))) == FireClass.B,
                            "fire over bitumen is class B");
                    ((FireWaterBlockEntity) h.getBlockEntity(HYDRANT)).setOpen(true);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    h.assertTrue(((FireWaterBlockEntity) h.getBlockEntity(PUMP)).running(), "the pump started on the draw");
                    h.assertTrue(fires(h) >= 9, "water does not put out a burning liquid: " + fires(h));
                    place(h, PROPORTIONER, PcBlocks.FOAM_PROPORTIONER.get());
                })
                .thenIdle(2)
                .thenExecute(() -> ((FireWaterBlockEntity) h.getBlockEntity(PROPORTIONER)).concentrate()
                        .fill(new FluidStack(PcFluids.FOAM_CONCENTRATE.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE))
                .thenWaitUntil(() -> h.assertTrue(fires(h) == 0, "foam puts the pool out: " + fires(h)))
                .thenExecute(() -> ((FireWaterBlockEntity) h.getBlockEntity(HYDRANT)).setOpen(false))
                .thenSucceed();
    }
}
