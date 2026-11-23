package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.GridDevice;
import dev.denfry.promptcritical.mod.grid.PowerBoardBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Старые электрические блоки, модель которых выходила за объём (аудит коллизий 2026-11-23): пульт диспетчера 3 × 2 × 1,
 * шкаф ИБП и стеллаж батареи 1 × 2 × 1. Сквозь нарисованное не пройти, а блок, поставленный до роста объёма, работает
 * и не трогает соседей.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElectricalCollisionGameTests {
    private ElectricalCollisionGameTests() {}

    private static void place(GameTestHelper h, BlockPos p, Block b) {
        h.setBlock(p, b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Каждая клетка, где модель что-то рисует (фасад на север, от контроллера), твёрдая для игрока. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void drawnCellsOfOldElectricalBlocksBlockThePlayer(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        StringBuilder bad = new StringBuilder();
        check(h, p, PcBlocks.GRID_DISPATCH_CENTER.get(), bad, -1, 1, 0, 1);
        check(h, p, PcBlocks.UPS_CABINET.get(), bad, 0, 0, 0, 1);
        check(h, p, PcBlocks.GRID_BATTERY_RACK.get(), bad, 0, 0, 0, 1);
        h.assertTrue(bad.isEmpty(), "drawn cells the player walks through:" + bad);
        h.succeed();
    }

    private static void check(GameTestHelper h, BlockPos p, Block b, StringBuilder bad, int x0, int x1, int y0, int y1) {
        place(h, p, b);
        var level = h.getLevel();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++) {
                BlockPos a = h.absolutePos(p.offset(x, y, 0));
                if (level.getBlockState(a).getCollisionShape(level, a).isEmpty())
                    bad.append(' ').append(b.getDescriptionId()).append(new BlockPos(x, y, 0).toShortString());
            }
        h.destroyBlock(p);
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                h.assertTrue(h.getBlockState(p.offset(x, y, 0)).isAir(), b.getDescriptionId() + " leaves nothing behind");
    }

    /**
     * Шкаф ИБП с выводами: новый — верхний вывод у своей верхней клетки; поставленный до 1 × 2 × 1 под чужим кабелем —
     * вывод сверху остаётся у контроллера, а снос не трогает кабель.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void upsCabinetTerminalsFollowItsOwnTopCell(GameTestHelper h) {
        BlockPos fresh = new BlockPos(3, 2, 5), old = new BlockPos(8, 2, 5);
        place(h, fresh, PcBlocks.UPS_CABINET.get());
        h.assertTrue(h.getBlockState(fresh.above()).getBlock() instanceof MultiblockPartBlock, "top cell of a new cabinet");
        BlockPos f = h.absolutePos(fresh);
        List<GridDevice.Face> t = ((PowerBoardBlockEntity) h.getBlockEntity(fresh)).terminals().get(0);
        h.assertTrue(t.contains(new GridDevice.Face(f.above(), Direction.UP)) && !t.contains(new GridDevice.Face(f, Direction.UP)),
                "the top terminal moves to the top cell");
        h.assertTrue(t.contains(new GridDevice.Face(f.above(), Direction.SOUTH)) && !t.contains(new GridDevice.Face(f.above(), Direction.NORTH)),
                "the top cell takes cables at the back, not at the front");

        // старая раскладка: на месте верхней клетки при постановке был кабель
        h.setBlock(old.above(), PcBlocks.POWER_CABLE.get().defaultBlockState());
        place(h, old, PcBlocks.UPS_CABINET.get());
        BlockPos o = h.absolutePos(old);
        List<GridDevice.Face> ot = ((PowerBoardBlockEntity) h.getBlockEntity(old)).terminals().get(0);
        h.assertTrue(ot.contains(new GridDevice.Face(o, Direction.UP)) && !ot.contains(new GridDevice.Face(o.above(), Direction.UP)),
                "an old cabinet keeps its top terminal on the controller under the cable");
        h.destroyBlock(old);
        h.assertBlockPresent(PcBlocks.POWER_CABLE.get(), old.above());
        h.succeed();
    }

    /** Стеллаж, поставленный до 1 × 2 × 1 под другим стеллажом: снос нижнего не уносит верхний. */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void oldStackedBatteryRacksStayApart(GameTestHelper h) {
        BlockPos low = new BlockPos(5, 2, 5);
        h.setBlock(low.above(), Blocks.STONE);
        place(h, low, PcBlocks.GRID_BATTERY_RACK.get());
        place(h, low.above(), PcBlocks.GRID_BATTERY_RACK.get());
        h.assertBlockPresent(PcBlocks.GRID_BATTERY_RACK.get(), low);
        h.assertTrue(h.getBlockState(low.above(2)).getBlock() instanceof MultiblockPartBlock, "the upper rack has its own top");
        h.destroyBlock(low);
        h.assertBlockPresent(PcBlocks.GRID_BATTERY_RACK.get(), low.above());
        h.assertTrue(h.getBlockState(low.above(2)).getBlock() instanceof MultiblockPartBlock, "the upper rack keeps its top");
        h.succeed();
    }
}
