package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.foundry.CoalJigBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.QuenchTowerBlockEntity;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlock;
import dev.denfry.promptcritical.mod.mine.LongwallShearerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Добыча, обогащение и коксохим реального масштаба: комбайн на забойном конвейере, отсадочная машина Баума, башня
 * мокрого тушения — те же BE и юниты симуляции, что у старой серии, с цельной моделью, портами и чертежом; клетки
 * коксовой батареи берут вид по соседям.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MiningRealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private MiningRealScaleGameTests() {}

    /** Площадка на каменном основании под нижним рядом объёма, принимает чертёж {@code id}. */
    private static void acceptsDrawing(GameTestHelper h, BlockPos p, String id, int x0, int x1, int y0, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) h.setBlock(p.offset(x, y0 - 1, z), Blocks.STONE);
        h.setBlock(p, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
    }

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /**
     * Комбайн реального масштаба 12×2×2 — тот же BE, что старый, с цельной моделью; шнеки режут за крайними клетками
     * −6 и 5, уголь падает за забойный конвейер; старый комбайн остаётся 3×1×1 без цельной модели.
     */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realShearerIsTheLongwallShearerWithItsWholeModel(GameTestHelper h) {
        BlockPos p = new BlockPos(6, 2, 4);
        acceptsDrawing(h, p, "longwall_shearer_real", -6, 5, 0, 0, 1);
        h.assertTrue(PcBlocks.FP_LONGWALL_SHEARER_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 12 * 2 * 2, "12x2x2 shearer");
        BlockState real = north(PcBlocks.LONGWALL_SHEARER_REAL.get());
        h.setBlock(p, real);
        h.assertTrue(h.getBlockEntity(p) instanceof LongwallShearerBlockEntity b && "longwall_shearer_real".equals(b.wholeModelId()),
                "the real shearer draws its whole model");
        h.assertTrue(!h.getBlockState(p.offset(-6, 1, 1)).isAir() && !h.getBlockState(p.offset(5, 0, 0)).isAir(), "the whole volume is placed");
        int[] d = LongwallShearerBlock.drumCells(real);
        h.assertTrue(d[0] == -6 && d[1] == 5 && LongwallShearerBlock.depth(real) == 2, "drums at the ends, coal behind the conveyor");
        BlockPos old = new BlockPos(6, 2, 9);
        h.setBlock(old, north(PcBlocks.LONGWALL_SHEARER.get()));
        h.assertTrue(h.getBlockEntity(old) instanceof LongwallShearerBlockEntity b && b.wholeModelId() == null, "the old shearer keeps its renderer");
        int[] od = LongwallShearerBlock.drumCells(h.getBlockState(old));
        h.assertTrue(od[0] == -1 && od[1] == 1, "old drums in the neighbouring cells");
        h.succeed();
    }

    /**
     * Машина Баума реального масштаба — та же отсадочная машина: вал Create входит в клетку-порт вентилятора (1, 2, 0)
     * с севера, сам контроллер вала не имеет; уголь — в воронку сверху, подпиточная вода — с запада у подрешётного
     * отделения, глухие клетки бака закрыты.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void realCoalJigTakesItsShaftAtThePort(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 3);
        acceptsDrawing(h, p, "coal_jig_real", -1, 1, 0, 0, 4);
        h.setBlock(p, north(PcBlocks.COAL_JIG_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof CoalJigBlockEntity b && b.real() && "coal_jig_real".equals(b.wholeModelId()),
                "the real jig is the coal jig with its whole model");
        BlockState port = h.getBlockState(p.offset(1, 2, 0));
        h.assertTrue(port.is(PcBlocks.KINETIC_PORT.get()) && port.getValue(KineticPortBlock.INPUT), "the fan cell takes the Create shaft");
        h.assertTrue(!PcBlocks.COAL_JIG_REAL.get().defaultBlockState().is(PcBlocks.COAL_JIG.get()), "a block of its own");
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(0, 2, 0)), Direction.UP) != null, "feed hopper on top");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(0, 2, 0)), Direction.NORTH) == null,
                "the hopper side is no port");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(-1, 1, 2)), Direction.WEST) != null,
                "make-up water from the west");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(1, 1, 2)), Direction.EAST) == null,
                "the air chamber side takes no water");
        BlockPos old = new BlockPos(1, 2, 9);
        h.setBlock(old, north(PcBlocks.COAL_JIG.get()));
        h.assertTrue(h.getBlockEntity(old) instanceof CoalJigBlockEntity b && !b.real(), "the old jig keeps its shaft in the controller");
        h.succeed();
    }

    /**
     * Башня тушения реального масштаба 3×12×3 — тот же BE, что старая: вагон стоит в той же зоне под камерой, вода — с
     * запада, лоток для кокса — с востока, стенки камеры без портов закрыты.
     */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realQuenchTowerIsTheQuenchTowerWithPorts(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 4, 5);
        acceptsDrawing(h, p, "quench_tower_real", -1, 1, -1, -1, 1);
        h.assertTrue(PcBlocks.FP_QUENCH_TOWER_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 3 * 12 * 3, "3x12x3 tower");
        h.setBlock(p, north(PcBlocks.QUENCH_TOWER_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof QuenchTowerBlockEntity b && "quench_tower_real".equals(b.wholeModelId()),
                "the real tower is the quench tower with its whole model");
        h.assertTrue(!h.getBlockState(p.offset(0, 10, 0)).isAir() && h.getBlockState(p.offset(0, 11, 0)).isAir(),
                "the stack ends 11 m above the controller");
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(-1, -1, 0)), Direction.WEST) != null,
                "water into the spray header from the west");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(1, -1, 0)), Direction.EAST) != null,
                "coke through the east chute");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(0, -1, -1)), Direction.NORTH) == null,
                "the chamber wall is no port");
        h.succeed();
    }

    /**
     * Щитовая секция крепи реального масштаба 2×2×4 — та же секция мехкрепи, что старая: распёрта по умолчанию, якорь
     * второго сегмента — тот же блок, но крепь шахта видит по контроллеру.
     */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realRoofSupportIsThePoweredSupport(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 3);
        acceptsDrawing(h, p, "powered_roof_support_real", 0, 1, 0, 0, 3);
        h.assertTrue(PcBlocks.FP_POWERED_ROOF_SUPPORT_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 2 * 2 * 4, "2x2x4 section");
        h.setBlock(p, north(PcBlocks.POWERED_ROOF_SUPPORT_REAL.get()).setValue(BlockStateProperties.EXTENDED, true));
        h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlockEntity b
                && "powered_roof_support_real".equals(b.wholeModelId()), "the real section is the powered support with its whole model");
        BlockState anchor = h.getBlockState(p.offset(0, 0, 3));
        h.assertTrue(dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlock.isSupport(anchor)
                && anchor.getBlock() instanceof dev.denfry.promptcritical.mod.mine.PoweredRoofSupportBlock b && !b.isController(anchor)
                && b.controllerPos(anchor, h.absolutePos(p.offset(0, 0, 3))).equals(h.absolutePos(p)),
                "the second segment anchor leads to the controller");
        h.succeed();
    }

    /**
     * Клетки коксовой батареи реального масштаба берут вид по соседям: ярус по камерам сверху и снизу, торец с дверью
     * у концов камеры, ось — вдоль камеры; простенок — так же, с анкерной колонной на торце.
     */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void cokeBatteryCellsPickTheirLookFromNeighbours(GameTestHelper h) {
        for (int y = 1; y <= 3; y++)
            for (int z = 1; z <= 4; z++) {
                h.setBlock(new BlockPos(1, y, z), PcBlocks.COKE_BATTERY_WALL.get());
                h.setBlock(new BlockPos(2, y, z), PcBlocks.COKE_OVEN.get());
                h.setBlock(new BlockPos(3, y, z), PcBlocks.COKE_BATTERY_WALL.get());
            }
        check(h, new BlockPos(2, 1, 1), PcProperties.CellLevel.BOTTOM, PcProperties.CellDoor.NEG);
        check(h, new BlockPos(2, 2, 2), PcProperties.CellLevel.MIDDLE, PcProperties.CellDoor.NONE);
        check(h, new BlockPos(2, 3, 4), PcProperties.CellLevel.TOP, PcProperties.CellDoor.POS);
        check(h, new BlockPos(1, 1, 4), PcProperties.CellLevel.BOTTOM, PcProperties.CellDoor.POS);
        check(h, new BlockPos(3, 3, 1), PcProperties.CellLevel.TOP, PcProperties.CellDoor.NEG);
        h.succeed();
    }

    private static void check(GameTestHelper h, BlockPos rel, PcProperties.CellLevel lv, PcProperties.CellDoor door) {
        BlockPos abs = h.absolutePos(rel);
        BlockState s = Block.updateFromNeighbourShapes(h.getLevel().getBlockState(abs), h.getLevel(), abs);
        h.assertTrue(s.getValue(PcProperties.CELL_LEVEL) == lv && s.getValue(PcProperties.CELL_DOOR) == door
                && s.getValue(BlockStateProperties.HORIZONTAL_AXIS) == Direction.Axis.Z, rel + " looks " + s);
    }
}
