package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.foundry.KineticMachineBlock;
import dev.denfry.promptcritical.mod.foundry.MoldingStationBlockEntity;
import dev.denfry.promptcritical.mod.foundry.ShakeoutGrateBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Формовочная машина и выбивная решётка реального масштаба (08 §8.9, art/blockbench/foundry/*_real.author.js): площадка
 * принимает чертёж, машина — тот же BE, что у старой, с цельной моделью, вал Create — в западную грань контроллера,
 * воронки — только у нарисованных бункера и фасада.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundryRealGameTests {
    private static final String TEMPLATE = "gametest/empty_12x12x12";

    private FoundryRealGameTests() {}

    private static BlockState north(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Площадка на каменном основании под клетками {@code x0..x1, z0..z1} модели принимает чертёж {@code id}. */
    private static void siteAccepts(GameTestHelper h, BlockPos p, String id, int x1, int z1) {
        for (int x = 0; x <= x1; x++)
            for (int z = 0; z <= z1; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void joltSqueezeMouldingMachineIsTheMouldingStation(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        siteAccepts(h, p, "molding_station_real", 1, 0);
        h.assertTrue(PcBlocks.FP_MOLDING_STATION_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 2 * 2, "2x2x1 machine");
        h.setBlock(p, north(PcBlocks.MOLDING_STATION_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof MoldingStationBlockEntity m && "molding_station_real".equals(m.wholeModelId()),
                "the real machine is the moulding station with its whole model");
        BlockState s = h.getBlockState(p);
        h.assertTrue(((KineticMachineBlock) s.getBlock()).shaftFace(s) == Direction.WEST, "the Create shaft enters the west face");
        var caps = Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(1, 1, 0)), Direction.UP) != null, "sand goes into the hopper");
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(0, 1, 0)), Direction.UP) == null, "the yoke takes nothing");
        BlockPos old = new BlockPos(1, 2, 1);
        h.setBlock(old, north(PcBlocks.MOLDING_STATION.get()));
        h.assertTrue(h.getBlockEntity(old) instanceof MoldingStationBlockEntity m && m.wholeModelId() == null,
                "the old moulding station keeps its own renderer");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void vibratingShakeoutGrateIsTheShakeoutGrate(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 5);
        siteAccepts(h, p, "shakeout_grate_real", 1, 1);
        h.assertTrue(PcBlocks.FP_SHAKEOUT_GRATE_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 2 * 2 + 2, "deck 2x2, hood 1x2 above it");
        h.setBlock(p, north(PcBlocks.SHAKEOUT_GRATE_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof ShakeoutGrateBlockEntity g && "shakeout_grate_real".equals(g.wholeModelId()),
                "the real grate is the shakeout grate with its whole model");
        BlockState s = h.getBlockState(p);
        h.assertTrue(((KineticMachineBlock) s.getBlock()).shaftFace(s) == Direction.WEST, "the Create shaft enters the west face");
        var caps = Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(1, 0, 0)), Direction.NORTH) != null, "castings come out at the front");
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(1, 1, 1)), Direction.EAST) == null, "the dust hood takes nothing");
        h.assertTrue(h.getBlockState(p.offset(0, 1, 0)).isAir() && h.getBlockState(p.offset(0, 1, 1)).isAir(),
                "the west half of the deck is open for moulds");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void realFoundryMachinesHaveDrawingRecipes(GameTestHelper h) {
        var recipes = h.getLevel().getRecipeManager();
        for (String id : new String[] {"foundry/molding_station_real_drawing", "foundry/shakeout_grate_real_drawing"})
            h.assertTrue(recipes.byKey(PromptCritical.id(id)).isPresent(), "drawing recipe " + id);
        h.succeed();
    }
}
