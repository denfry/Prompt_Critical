package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ops.FireDeviceBlockEntity;
import dev.denfry.promptcritical.mod.ops.Lockouts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Пожаротушение в мире (13 §13.5): колба спринклера над очагом вскрывается по потолочной струе и гасит его водой из
 * трубы; ППК по двум извещателям объявляет пожар и открывает дренчер зоны; запертый LOTO модуль газового тушения
 * газ не выпускает.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FireGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos FIRE = new BlockPos(5, 2, 5);

    private FireGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block b) {
        h.setBlock(pos, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Негаснущий очаг: огонь на незерраке. */
    private static void ignite(GameTestHelper h) {
        h.setBlock(FIRE.below(), Blocks.NETHERRACK);
        h.setBlock(FIRE, Blocks.FIRE);
    }

    private static void water(GameTestHelper h, BlockPos pos) {
        FireDeviceBlockEntity d = h.getBlockEntity(pos);
        d.water().fill(new FluidStack(Fluids.WATER, 2000), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void sprinklerBulbOpensOverTheFireAndPutsItOut(GameTestHelper h) {
        BlockPos sprinkler = FIRE.above(3);
        place(h, sprinkler, PcBlocks.SPRINKLER_HEAD.get());
        ignite(h);
        h.startSequence()
                .thenExecute(() -> water(h, sprinkler))
                .thenWaitUntil(() -> h.assertTrue(((FireDeviceBlockEntity) h.getBlockEntity(sprinkler)).alarm(), "bulb burst"))
                .thenWaitUntil(() -> h.assertBlockNotPresent(Blocks.FIRE, FIRE))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void twoDetectorsMakeTheFirePanelOpenTheDeluge(GameTestHelper h) {
        BlockPos d1 = FIRE.offset(-2, 3, 0), d2 = FIRE.offset(2, 3, 0), panel = new BlockPos(1, 1, 1), deluge = FIRE.offset(0, 4, 2);
        place(h, d1, PcBlocks.SMOKE_DETECTOR.get());
        place(h, d2, PcBlocks.SMOKE_DETECTOR.get());
        place(h, panel, PcBlocks.FIRE_ALARM_PANEL.get());
        place(h, deluge, PcBlocks.DELUGE_NOZZLE.get());
        ignite(h);
        h.startSequence()
                .thenExecute(() -> water(h, deluge))
                .thenWaitUntil(() -> h.assertTrue(((FireDeviceBlockEntity) h.getBlockEntity(panel)).fire(), "fire confirmed by two detectors"))
                .thenExecute(() -> h.assertTrue(((FireDeviceBlockEntity) h.getBlockEntity(deluge)).running(), "deluge opened by the panel"))
                .thenWaitUntil(() -> h.assertBlockNotPresent(Blocks.FIRE, FIRE))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void lockedGasModuleDoesNotRelease(GameTestHelper h) {
        BlockPos gas = new BlockPos(2, 1, 2);
        place(h, gas, PcBlocks.GAS_SUPPRESSION_CYLINDER.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    FireDeviceBlockEntity g = h.getBlockEntity(gas);
                    // мир GameTest сохраняется между прогонами вместе с замками: игрок — новый на каждый прогон
                    var p = net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "fire-test"));
                    g.use(p); // ручная блокировка — изолированное положение
                    h.assertTrue(g.isolatedPosition(), "blocked by hand");
                    g.use(p); // снова взведён
                    var r = Lockouts.registry(h.getLevel());
                    var key = Lockouts.key(h.absolutePos(gas));
                    g.use(p);
                    h.assertTrue(r.apply(key, g.isolatedPosition(), p.getUUID(), "fp", "", 0, 0, 8)
                            == dev.denfry.promptcritical.sim.ops.LockoutRegistry.LockResult.OK, "locked out for the works");
                    g.use(p); // запертый не взвести
                    h.assertTrue(g.isolatedPosition(), "a locked module stays blocked");
                    r.remove(key, p.getUUID());
                })
                .thenSucceed();
    }
}
