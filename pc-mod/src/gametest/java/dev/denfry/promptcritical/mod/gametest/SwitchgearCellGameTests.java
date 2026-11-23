package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ячейка КРУ 6 кВ реального размера (тип КСО): 1 × 3 × 1, контроллер в средней клетке, снизу отсек выключателя, сверху
 * шинный отсек. Шина — боковые грани всех трёх клеток (ряд ячеек и есть сборные шины), присоединение — задние грани
 * и верх.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SwitchgearCellGameTests {
    private SwitchgearCellGameTests() {}

    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void switchgearCellIsThreeHighWithTheBusOnItsSides(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        h.setBlock(p, PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.assertTrue(h.getBlockState(p.below()).getBlock() instanceof MultiblockPartBlock, "breaker compartment below the controller");
        h.assertTrue(h.getBlockState(p.above()).getBlock() instanceof MultiblockPartBlock, "busbar compartment above the controller");
        BreakerCellBlockEntity be = h.getBlockEntity(p);
        List<List<GridDevice.Face>> t = be.terminals();
        BlockPos a = h.absolutePos(p);
        for (int y = -1; y <= 1; y++) {
            h.assertTrue(t.get(0).contains(new GridDevice.Face(a.above(y), Direction.WEST))
                    && t.get(0).contains(new GridDevice.Face(a.above(y), Direction.EAST)), "bus on both sides at " + y);
            h.assertTrue(t.get(1).contains(new GridDevice.Face(a.above(y), Direction.SOUTH)), "feeder at the back at " + y);
        }
        h.assertTrue(t.get(1).contains(new GridDevice.Face(a.above(), Direction.UP)), "feeder through the top");
        h.assertTrue(!t.get(0).contains(new GridDevice.Face(a.above(2), Direction.WEST)), "nothing above the cell");
        h.succeed();
    }
}
