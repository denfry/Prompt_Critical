package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.GridDispatchCenterBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Пульт диспетчера (07 §7.6.6, pc_grid §7.17.4): видит остров работающего генератора с его частотой и генерацией. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DispatchGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), CENTER = new BlockPos(8, 1, 8);

    private DispatchGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void dispatchCenterSeesTheGeneratorsIsland(GameTestHelper h) {
        h.setBlock(GEN, PcBlocks.SHAFT_GENERATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(GEN.above(), PcBlocks.GENERATOR_EXCITER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.setBlock(CENTER, PcBlocks.GRID_DISPATCH_CENTER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64))
                .thenWaitUntil(() -> h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit() != null, "generator twin"))
                .thenExecute(() -> {
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                })
                .thenWaitUntil(() -> {
                    GridDispatchCenterBlockEntity c = h.getBlockEntity(CENTER);
                    var islands = c.listIslands();
                    h.assertTrue(islands.stream().anyMatch(m -> Boolean.TRUE.equals(m.get("energized"))
                            && Math.abs((double) m.get("freq") - 50) < 1), "an energized island near 50 Hz: " + islands);
                    h.assertTrue(!c.listBuses().isEmpty(), "buses listed");
                    h.assertTrue(c.mainIsland() != null, "main island");
                })
                .thenSucceed();
    }
}
