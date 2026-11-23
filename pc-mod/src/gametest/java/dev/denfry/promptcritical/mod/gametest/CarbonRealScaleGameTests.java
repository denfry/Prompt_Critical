package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.grid.GridBlocks;
import dev.denfry.promptcritical.mod.ironworks.BakingFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.ElectrodeExtruderBlockEntity;
import dev.denfry.promptcritical.mod.reactor.AchesonFurnaceBlockEntity;
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
 * Углеродная линия реального масштаба (08 §8.4.5): ванна Ачесона, гидравлический электродный пресс и камера обжига —
 * те же BE и двойники, что у старой серии, с цельной моделью, портами и чертежом.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarbonRealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private CarbonRealScaleGameTests() {}

    /** Площадка на каменном основании под нижним рядом объёма принимает чертёж {@code id}; затем убирается. */
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

    private static String model(GameTestHelper h, BlockPos p) {
        return h.getBlockEntity(p) instanceof WholeModelHost w ? w.wholeModelId() : null;
    }

    /** Ванна Ачесона 12 × 2 × 3 — та же печь сети 6 кВ: кабель берёт любая клетка ванны, шихта — только сверху. */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realAchesonFurnaceIsTheGridFurnace(GameTestHelper h) {
        BlockPos p = new BlockPos(6, 2, 2);
        acceptsDrawing(h, p, "acheson_furnace_real", -6, 5, 0, 0, 2);
        h.assertTrue(PcBlocks.FP_ACHESON_FURNACE_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 12 * 2 * 3, "12x2x3 bed");
        h.setBlock(p, north(PcBlocks.ACHESON_FURNACE_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof AchesonFurnaceBlockEntity && "acheson_furnace_real".equals(model(h, p)),
                "the real bed is the Acheson furnace with its whole model");
        BlockPos end = p.offset(-6, 1, 0);
        h.assertTrue(GridBlocks.isGridBlock(h.getLevel(), h.absolutePos(end), h.getBlockState(end)), "the bus bar end is a grid cell");
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(3, 1, 1)), Direction.UP) != null, "charge from the top");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(5, 0, 1)), Direction.EAST) == null,
                "the end wall is no port");
        h.succeed();
    }

    /** Пресс 7 × 2 × 2 — тот же электродный пресс: вал Create — в контроллер с севера, масса — в бункер сверху. */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realExtruderTakesItsShaftAtTheController(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 3);
        acceptsDrawing(h, p, "electrode_extruder_real", -3, 3, 0, 0, 1);
        h.setBlock(p, north(PcBlocks.ELECTRODE_EXTRUDER_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof ElectrodeExtruderBlockEntity && "electrode_extruder_real".equals(model(h, p)),
                "the real press is the electrode extruder with its whole model");
        var block = (dev.denfry.promptcritical.mod.foundry.KineticMachineBlock) PcBlocks.ELECTRODE_EXTRUDER_REAL.get();
        h.assertTrue(block.hasShaftTowards(h.getLevel(), h.absolutePos(p), h.getBlockState(p), Direction.NORTH), "shaft into the north face");
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(-1, 1, 0)), Direction.UP) != null, "paste hopper on top");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(-3, 1, 0)), Direction.WEST) != null, "electrodes out west");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(2, 1, 0)), Direction.UP) == null, "the pump unit is no port");
        BlockPos old = new BlockPos(5, 2, 9);
        h.setBlock(old, north(PcBlocks.ELECTRODE_EXTRUDER.get()));
        h.assertTrue(model(h, old) == null, "the old press keeps its renderer");
        h.succeed();
    }

    /** Камера обжига 5 × 4 × 4 — та же обжиговая печь: газ — с запада в коллектор, электроды — сверху. */
    @GameTest(template = TALL, timeoutTicks = 20)
    public static void realBakingFurnaceIsTheBakingFurnace(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 3);
        acceptsDrawing(h, p, "baking_furnace_real", -2, 2, 0, 0, 3);
        h.setBlock(p, north(PcBlocks.BAKING_FURNACE_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof BakingFurnaceBlockEntity && "baking_furnace_real".equals(model(h, p)),
                "the real chamber is the baking furnace with its whole model");
        var level = h.getLevel();
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(-2, 2, 0)), Direction.WEST) != null, "fuel gas from the west");
        h.assertTrue(level.getCapability(Capabilities.ItemHandler.BLOCK, h.absolutePos(p.offset(0, 3, 1)), Direction.UP) != null, "electrodes under the cover");
        h.assertTrue(level.getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(p.offset(2, 2, 0)), Direction.EAST) == null, "the east wall is no port");
        h.succeed();
    }
}
