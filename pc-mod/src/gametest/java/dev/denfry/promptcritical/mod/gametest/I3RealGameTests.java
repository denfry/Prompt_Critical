package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * I3 реального масштаба (art/blockbench/i3): площадка принимает чертёж, машина — тот же BE, что у старой, с цельной
 * моделью; вал Create — в клетку-порт, трубы — только к патрубкам из шапки арта.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class I3RealGameTests {
    private static final String SMALL = "gametest/empty_12x12x12", TALL = "gametest/empty_16x24x16";

    private I3RealGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Камень под нижним рядом объёма, чертёж на площадке, затем сама машина; возвращает BE контроллера. */
    private static BlockEntity build(GameTestHelper h, BlockPos p, String id, Footprint fp, Block block) {
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH))
            if (c.pos().getY() == 0) h.setBlock(p.offset(c.pos()).below(), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, north(block));
        h.assertTrue(h.getBlockEntity(p) instanceof WholeModelHost host && id.equals(host.wholeModelId()), id + " has its whole model");
        return h.getBlockEntity(p);
    }

    @GameTest(template = TALL, timeoutTicks = 20)
    public static void heavyGasTurbineIsTheOldTurbine(GameTestHelper h) {
        var be = build(h, new BlockPos(7, 1, 7), "gas_turbine_heavy_real", PcBlocks.FP_GAS_TURBINE_HEAVY_REAL, PcBlocks.GAS_TURBINE_HEAVY_REAL.get());
        h.assertTrue(be instanceof dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity, "the same gas turbine");
        h.assertTrue(WholePorts.allows("gas_turbine_heavy_real", Direction.NORTH, 0, 0, -2, Direction.NORTH), "fuel gas on the north");
        h.assertFalse(WholePorts.allows("gas_turbine_heavy_real", Direction.NORTH, 0, 0, 2, Direction.SOUTH), "no lube oil pipe");
        h.succeed();
    }

    @GameTest(template = TALL, timeoutTicks = 40)
    public static void hrsgWithoutATurbineMakesNoSteam(GameTestHelper h) {
        var be = (dev.denfry.promptcritical.mod.powerhouse.HrsgBlockEntity) build(h, new BlockPos(8, 1, 7), "hrsg_real", PcBlocks.FP_HRSG_REAL,
                PcBlocks.HRSG_REAL.get());
        h.assertFalse(WholePorts.allows("hrsg_real", Direction.NORTH, -6, 3, 0, Direction.WEST), "the exhaust duct takes no pipe");
        h.runAfterDelay(25, () -> {
            h.assertFalse(be.linkedTurbine(), "no turbine at the inlet duct");
            h.assertTrue(be.deliveredW() == 0, "no steam");
            h.succeed();
        });
    }

    @GameTest(template = TALL, timeoutTicks = 20)
    public static void furnacesAreTheOldFurnaces(GameTestHelper h) {
        var reformer = build(h, new BlockPos(5, 1, 3), "steam_reformer_real", PcBlocks.FP_STEAM_REFORMER_REAL, PcBlocks.STEAM_REFORMER_REAL.get());
        h.assertTrue(reformer instanceof dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity, "the same steam reformer");
        var pyrolysis = build(h, new BlockPos(5, 1, 11), "pyrolysis_furnace_real", PcBlocks.FP_PYROLYSIS_FURNACE_REAL,
                PcBlocks.PYROLYSIS_FURNACE_REAL.get());
        h.assertTrue(pyrolysis instanceof dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity, "the same pyrolysis furnace");
        h.assertTrue(WholePorts.allows("steam_reformer_real", Direction.NORTH, -4, 13, 0, Direction.WEST), "reformer feed at the top header");
        h.assertTrue(WholePorts.allows("pyrolysis_furnace_real", Direction.NORTH, 0, 0, 2, Direction.SOUTH), "pyrolysis fuel gas on the south");
        h.succeed();
    }

    @GameTest(template = TALL, timeoutTicks = 20)
    public static void synthesisConvertersTakeTheirCirculatorShaftAtTheSkirt(GameTestHelper h) {
        BlockPos a = new BlockPos(3, 1, 3), m = new BlockPos(10, 1, 9);
        var ammonia = build(h, a, "ammonia_converter_real", PcBlocks.FP_AMMONIA_CONVERTER_REAL, PcBlocks.AMMONIA_CONVERTER_REAL.get());
        h.assertTrue(ammonia instanceof dev.denfry.promptcritical.mod.nitrogen.AmmoniaConverterBlockEntity, "the same ammonia converter");
        h.assertTrue(h.getBlockState(a.offset(0, 0, 1)).getBlock() instanceof KineticPortBlock, "the circulator shaft enters the skirt");
        var methanol = build(h, m, "methanol_converter_real", PcBlocks.FP_METHANOL_CONVERTER_REAL, PcBlocks.METHANOL_CONVERTER_REAL.get());
        h.assertTrue(methanol instanceof dev.denfry.promptcritical.mod.methanol.MethanolConverterBlockEntity, "the same methanol converter");
        h.assertTrue(h.getBlockState(m.offset(1, 0, 2)).getBlock() instanceof KineticPortBlock, "the circulator shaft enters the skirt");
        h.succeed();
    }

    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void electrolyserGivesEachNozzleItsOwnStream(GameTestHelper h) {
        var cells = (CellRoomBlockEntity) build(h, new BlockPos(5, 1, 5), "chlor_alkali_electrolyser_real",
                PcBlocks.FP_CHLOR_ALKALI_ELECTROLYSER_REAL, PcBlocks.CHLOR_ALKALI_ELECTROLYSER_REAL.get());
        var chlorine = cells.fluidsAt(-3, 2, 0, Direction.UP, Direction.UP);
        var hydrogen = cells.fluidsAt(4, 2, 1, Direction.UP, Direction.UP);
        h.assertTrue(chlorine != null && hydrogen != null && chlorine != hydrogen, "chlorine and hydrogen leave by different nozzles on the top");
        h.assertTrue(cells.fluidsAt(0, 0, 1, Direction.SOUTH, Direction.SOUTH) != cells.fluidsAt(0, 0, -1, Direction.NORTH, Direction.NORTH),
                "brine in and caustic out are different nozzles");
        h.assertFalse(WholePorts.allows("chlor_alkali_electrolyser_real", Direction.NORTH, 1, 1, 0, Direction.UP), "no pipe on the rack");
        h.succeed();
    }

    @GameTest(template = SMALL, timeoutTicks = 20)
    public static void pvcReactorIsTheOldReactor(GameTestHelper h) {
        var be = build(h, new BlockPos(5, 1, 5), "pvc_reactor_real", PcBlocks.FP_PVC_REACTOR_REAL, PcBlocks.PVC_REACTOR_REAL.get());
        h.assertTrue(be instanceof dev.denfry.promptcritical.mod.chlor.PvcReactorBlockEntity, "the same PVC reactor");
        h.assertTrue(WholePorts.allows("pvc_reactor_real", Direction.NORTH, 0, 0, 0, Direction.DOWN), "slurry out of the bottom valve");
        h.succeed();
    }
}
