package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.client.whole.RealScaleBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.mod.steelworks.ArcFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.steelworks.HeatTreatFurnaceBlockEntity;
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
 * Сталь и прокат I2 реального масштаба: дуговая печь, доводка в ковше, изложница, термическая печь, стан ТВЧ и
 * прошивной стан — тот же BE, что у старого блока, но с цельной моделью, портами у нарисованных патрубков и вводов,
 * валом Create в клетке-порте и типом устройства старого блока в симуляции.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteelI2RealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private SteelI2RealScaleGameTests() {}

    private static BlockState north(Block b) {
        BlockState s = b.defaultBlockState();
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH) : s;
    }

    private static boolean real(GameTestHelper h, BlockPos p, String id) {
        return h.getBlockEntity(p) instanceof RealScaleBlockEntity r && r.real() && id.equals(r.wholeModelId());
    }

    /** Клетка — вход вала Create вдоль x. */
    private static boolean shaftPort(GameTestHelper h, BlockPos p) {
        BlockState s = h.getBlockState(p);
        return s.is(PcBlocks.KINETIC_PORT.get()) && s.getValue(KineticPortBlock.INPUT) && s.getValue(BlockStateProperties.AXIS) == Direction.Axis.X;
    }

    @GameTest(template = TALL, timeoutTicks = 40)
    public static void realSteelLineMachinesKeepTheirBlockEntitiesAndPorts(GameTestHelper h) {
        var items = Capabilities.ItemHandler.BLOCK;
        var fluids = Capabilities.FluidHandler.BLOCK;

        // прошивной стан строится на стройплощадке по плану
        BlockPos pm = new BlockPos(6, 1, 4);
        for (int x = -1; x <= 1; x++)
            for (int z = 0; z <= 7; z++) h.setBlock(pm.offset(x, -1, z), Blocks.STONE);
        h.setBlock(pm, SiteRegistry.SITE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(pm)).accept("piercing_mill_real");
        h.assertTrue(w == null, "piercing mill plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(pm, Blocks.AIR);
        h.setBlock(pm, north(PcBlocks.PIERCING_MILL_REAL.get()));
        h.assertTrue(real(h, pm, "piercing_mill_real"), "the real piercing mill draws its whole model");
        h.assertTrue(shaftPort(h, pm.offset(-1, 0, 0)), "the pinion stand takes the shaft along x");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(pm.offset(0, 1, 0)), Direction.NORTH) != null, "billets at the entry trough");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(pm.offset(0, 1, 0)), Direction.WEST) == null, "the trough side is closed");

        BlockPos eaf = new BlockPos(2, 1, 2);
        h.setBlock(eaf, north(PcBlocks.ELECTRIC_ARC_FURNACE_REAL.get()));
        h.assertTrue(real(h, eaf, "electric_arc_furnace_real") && h.getBlockEntity(eaf) instanceof ArcFurnaceBlockEntity a
                && a.terminals().size() == 1 && a.terminals().getFirst().size() == 1
                && a.terminals().getFirst().getFirst().cell().equals(h.absolutePos(eaf.offset(-1, 3, 0)))
                && a.terminals().getFirst().getFirst().dir() == Direction.WEST, "the arc furnace takes its cable at the west entry only");

        BlockPos ls = new BlockPos(6, 1, 1);
        h.setBlock(ls, north(PcBlocks.LADLE_STATION_REAL.get()));
        h.assertTrue(real(h, ls, "ladle_station_real"), "the real ladle station draws its whole model");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(ls.offset(-1, 3, 0)), Direction.WEST) != null, "argon at the west inlet");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(ls.offset(1, 4, 1)), Direction.UP) != null, "alloys into the bins from above");

        BlockPos ht = new BlockPos(10, 1, 1);
        h.setBlock(ht, north(PcBlocks.HEAT_TREATMENT_FURNACE_REAL.get()));
        h.assertTrue(real(h, ht, "heat_treatment_furnace_real") && h.getBlockEntity(ht) instanceof HeatTreatFurnaceBlockEntity f
                && "promptcritical:heat_treatment_furnace".equals(f.deviceType()), "the real heat treatment furnace runs as the old one");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(ht.offset(-1, 1, 2)), Direction.WEST) != null, "gas to the burners");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(ht.offset(-1, 1, 3)), Direction.WEST) == null, "the side wall has no gas inlet");

        BlockPos pw = new BlockPos(2, 1, 6);
        h.setBlock(pw, north(PcBlocks.PIPE_WELDER_REAL.get()));
        h.assertTrue(real(h, pw, "pipe_welder_real"), "the real tube mill draws its whole model");
        h.assertTrue(shaftPort(h, pw.offset(-1, 0, 1)), "the tube mill takes the shaft at its west drive");
        h.assertTrue(h.getLevel().getCapability(items, h.absolutePos(pw.offset(0, 1, 0)), Direction.NORTH) != null, "strip at the entry");

        BlockPos im = new BlockPos(10, 1, 9);
        h.setBlock(im, PcBlocks.INGOT_MOLD_REAL.get().defaultBlockState());
        h.assertTrue(real(h, im, "ingot_mold_real"), "the real ingot mould draws its whole model");

        BlockPos old = new BlockPos(10, 1, 7);
        h.setBlock(old, PcBlocks.INGOT_MOLD.get().defaultBlockState());
        h.assertTrue(h.getBlockEntity(old) instanceof RealScaleBlockEntity r && !r.real(), "the old ingot mould keeps its multipart model");
        h.succeed();
    }
}
