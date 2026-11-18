package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.hazard.CpRectifierBlockEntity;
import dev.denfry.promptcritical.mod.hazard.CpTestPostBlockEntity;
import dev.denfry.promptcritical.mod.hazard.PigTrapBlockEntity;
import dev.denfry.promptcritical.mod.hazard.TankGaugeBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.pipeline.CathodicProtection;
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
 * Резервуарный парк и трубопроводы (09 §9.8): перелив резервуара Create под уровнемером и сигнал аварийного уровня;
 * скребок по трубам Create от камеры запуска к камере приёма — очистка, застревание, блокировка дверцы; катодная
 * защита участка станцией и перезащита.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PipelineGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos TANK = new BlockPos(5, 1, 5), GAUGE = new BlockPos(5, 2, 5);
    private static final BlockPos LAUNCHER = new BlockPos(2, 1, 2), RECEIVER = new BlockPos(2, 1, 9), RECTIFIER = new BlockPos(8, 1, 2),
            POST = new BlockPos(8, 1, 5);

    private PipelineGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos p, Block b) {
        h.setBlock(p, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void anOverfilledTankSpillsAndTripsTheHighHighLevel(GameTestHelper h) {
        for (int x = 0; x <= 11; x++)
            for (int z = 0; z <= 11; z++) h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        h.setBlock(TANK, AllBlocks.FLUID_TANK.get().defaultBlockState());
        place(h, GAUGE, PcBlocks.TANK_GAUGE.get());
        // насос налива: 50 мБ за тик (1000 л/с), пока его не остановят
        h.onEachTick(() -> {
            if (h.getLevel().getBlockEntity(h.absolutePos(TANK)) instanceof FluidTankBlockEntity t && t.getControllerBE() != null)
                t.getControllerBE().getTankInventory().fill(new FluidStack(PcFluids.CRUDE_OIL.source.get(), 50), IFluidHandler.FluidAction.EXECUTE);
        });
        h.succeedWhen(() -> {
            TankGaugeBlockEntity g = (TankGaugeBlockEntity) h.getBlockEntity(GAUGE);
            h.assertTrue(g.getBlockState().getValue(BlockStateProperties.POWERED), "the high-high level trips the redstone output");
            h.assertTrue(g.lossKgS() > 0, "filling a fixed-roof tank pushes vapour out of the breather");
            h.assertTrue(g.overflowing() && g.spilledL() > 0, "a full tank still being filled overflows");
            boolean oil = false;
            for (BlockPos p : BlockPos.betweenClosed(h.absolutePos(new BlockPos(0, 1, 0)), h.absolutePos(new BlockPos(11, 2, 11))))
                oil |= h.getLevel().getBlockState(p).is(PcBlocks.CRUDE_OIL.get());
            h.assertTrue(oil, "the overflow runs down onto the ground as crude");
        });
    }

    private static void line(GameTestHelper h) {
        place(h, LAUNCHER, PcBlocks.PIG_LAUNCHER.get());
        place(h, RECEIVER, PcBlocks.PIG_RECEIVER.get());
        for (int z = 3; z <= 8; z++) h.setBlock(new BlockPos(2, 1, z), AllBlocks.FLUID_PIPE.get().defaultBlockState());
    }

    @GameTest(template = LARGE, timeoutTicks = 800)
    public static void aCleaningPigRunsTheLineAndAWaxedLineSticksIt(GameTestHelper h) {
        line(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    h.assertTrue(l.receiver() != null && l.lengthM() == 6, "the section is the six Create pipes: " + l.lengthM());
                })
                .thenExecute(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    l.setDepositFraction(0.1);
                    l.load(PigTrapBlockEntity.CLEANING);
                    h.assertTrue(!l.launch(null), "a vented trap does not launch");
                    l.setVented(false);
                    h.assertTrue(!l.setDoor(true, null), "the door is locked under pressure");
                    h.assertTrue(l.launch(null), "launched");
                })
                .thenWaitUntil(() -> h.assertTrue(((PigTrapBlockEntity) h.getBlockEntity(RECEIVER)).held() == PigTrapBlockEntity.CLEANING,
                        "the pig arrives at the receiver"))
                .thenExecute(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    h.assertTrue(l.depositFraction() < 0.01 && l.waxKg() > 0, "the pig pushed the wax out: " + l.waxKg());
                    l.setDepositFraction(0.25);
                    l.load(PigTrapBlockEntity.SMART);
                    h.assertTrue(l.launch(null), "relaunched");
                })
                .thenWaitUntil(() -> h.assertTrue(((PigTrapBlockEntity) h.getBlockEntity(LAUNCHER)).stuck(), "wax over 20 % of the bore sticks the pig"))
                .thenExecute(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    h.assertTrue(!l.cutOut(null), "no cut-out under pressure");
                    l.setVented(true);
                    h.assertTrue(l.cutOut(null) && !l.stuck(), "cut out once vented");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void aRectifierProtectsTheSectionAndTooMuchCurrentOverprotects(GameTestHelper h) {
        line(h);
        place(h, RECTIFIER, PcBlocks.CP_RECTIFIER.get());
        place(h, POST, PcBlocks.CP_TEST_POST.get());
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((PigTrapBlockEntity) h.getBlockEntity(LAUNCHER)).receiver() != null, "section traced"))
                .thenExecute(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    h.assertTrue(!CathodicProtection.protectedAt(l.offV()), "bare section is not protected: " + l.offV());
                    CpRectifierBlockEntity r = (CpRectifierBlockEntity) h.getBlockEntity(RECTIFIER);
                    r.setBenchPower(true);
                    r.setCurrentA(l.requiredA() * 1.1);
                })
                .thenWaitUntil(() -> {
                    PigTrapBlockEntity l = (PigTrapBlockEntity) h.getBlockEntity(LAUNCHER);
                    h.assertTrue(CathodicProtection.protectedAt(l.offV()) && !CathodicProtection.overprotected(l.offV()), "protected: " + l.offV());
                    h.assertTrue(((CpTestPostBlockEntity) h.getBlockEntity(POST)).nearest(h.getLevel()) == l, "the test post reads this section");
                })
                .thenExecute(() -> ((CpRectifierBlockEntity) h.getBlockEntity(RECTIFIER)).setCurrentA(1))
                .thenWaitUntil(() -> h.assertTrue(CathodicProtection.overprotected(((PigTrapBlockEntity) h.getBlockEntity(LAUNCHER)).offV()),
                        "far too much current overprotects"))
                .thenSucceed();
    }
}
