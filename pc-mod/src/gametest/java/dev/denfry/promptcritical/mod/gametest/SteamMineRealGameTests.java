package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
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
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Машины реального масштаба котельной, дегазации и обогащения (art/blockbench/mining, boilers, ore_processing):
 * площадка принимает чертёж, машина — тот же BE, что у старой, с цельной моделью; вал Create — в клетку-порт из шапки
 * арта; барабан котла закрывает днищем концы ряда.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SteamMineRealGameTests {
    private static final String TEMPLATE = "gametest/empty_12x12x12";

    private SteamMineRealGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /**
     * Камень под нижним рядом объёма, площадка в клетке контроллера принимает чертёж {@code id}, затем на её место
     * ставится сама машина; возвращает BE контроллера.
     */
    private static WholeModelHost build(GameTestHelper h, BlockPos p, String id, Footprint fp, Block block) {
        int minY = Integer.MAX_VALUE;
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH)) minY = Math.min(minY, c.pos().getY());
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH))
            if (c.pos().getY() == minY) h.setBlock(p.offset(c.pos()).below(), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, north(block));
        h.assertTrue(h.getBlockEntity(p) instanceof WholeModelHost host && id.equals(host.wholeModelId()), id + " has its whole model");
        return (WholeModelHost) h.getBlockEntity(p);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void liquidRingPumpTakesItsShaftAtTheCouplingAndGasAtTheSuction(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 2, 3);
        var be = build(h, p, "degasification_pump_real", PcBlocks.FP_DEGASIFICATION_PUMP_REAL, PcBlocks.DEGASIFICATION_PUMP_REAL.get());
        h.assertTrue(be instanceof dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity, "the same degasification pump");
        h.assertTrue(h.getBlockState(p.offset(1, 0, 2)).getBlock() instanceof KineticPortBlock, "the Create shaft enters the coupling cell");
        BlockPos bore = h.absolutePos(p.offset(1, 1, -1));
        h.assertTrue(dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity.at(h.getLevel(), bore.south(), Direction.NORTH) != null,
                "a borehole north of the suction cell reaches the pump");
        h.assertTrue(dev.denfry.promptcritical.mod.mine.DegasificationPumpBlockEntity.at(h.getLevel(), h.absolutePos(p.offset(0, 1, 0)),
                Direction.NORTH) == null, "the separator side is no suction");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void draughtFansKeepTheirShaftAtTheFront(GameTestHelper h) {
        BlockPos fd = new BlockPos(2, 3, 2), id = new BlockPos(7, 3, 2);
        build(h, fd, "forced_draft_fan_real", PcBlocks.FP_DRAFT_FAN_REAL, PcBlocks.FORCED_DRAFT_FAN_REAL.get());
        build(h, id, "induced_draft_fan_real", PcBlocks.FP_DRAFT_FAN_REAL, PcBlocks.INDUCED_DRAFT_FAN_REAL.get());
        for (BlockPos p : new BlockPos[] {fd, id}) {
            BlockState s = h.getBlockState(p);
            h.assertTrue(((dev.denfry.promptcritical.mod.foundry.KineticMachineBlock) s.getBlock()).shaftFace(s) == Direction.NORTH,
                    "the Create shaft enters the north face of the controller");
            h.assertTrue(h.getBlockEntity(p) instanceof dev.denfry.promptcritical.mod.powerhouse.DraftFanBlockEntity, "the same draft fan");
        }
        h.assertTrue(((dev.denfry.promptcritical.mod.powerhouse.DraftFanBlockEntity) h.getBlockEntity(fd)).forcedDraft()
                && !((dev.denfry.promptcritical.mod.powerhouse.DraftFanBlockEntity) h.getBlockEntity(id)).forcedDraft(),
                "the blower blows and the exhauster draws");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void drumSeparatorTakesItsShaftAtTheWormReducer(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 2, 3);
        var be = build(h, p, "magnetic_separator_real", PcBlocks.FP_MAGNETIC_SEPARATOR_REAL, PcBlocks.MAGNETIC_SEPARATOR_REAL.get());
        h.assertTrue(be instanceof dev.denfry.promptcritical.mod.ironworks.MagneticSeparatorBlockEntity, "the same magnetic separator");
        h.assertTrue(h.getBlockState(p.offset(-2, 1, 0)).getBlock() instanceof KineticPortBlock, "the Create shaft enters the reducer cell");
        var caps = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(0, 1, 0)), Direction.UP) != null, "ore goes into the feed box");
        h.assertTrue(h.getLevel().getCapability(caps, h.absolutePos(p.offset(-1, 1, 0)), Direction.UP) == null, "the drum cover takes nothing");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void flotationCellAndElectrodeBoilerAreTheOldMachines(GameTestHelper h) {
        BlockPos f = new BlockPos(2, 2, 2), e = new BlockPos(7, 2, 2);
        var cell = build(h, f, "flotation_cell_real", PcBlocks.FP_FLOTATION_CELL_REAL, PcBlocks.FLOTATION_CELL_REAL.get());
        h.assertTrue(cell instanceof dev.denfry.promptcritical.mod.nonferrous.FlotationCellBlockEntity, "the same flotation cell");
        var boiler = build(h, e, "electrode_boiler_real", PcBlocks.FP_ELECTRODE_BOILER_REAL, PcBlocks.ELECTRODE_BOILER_REAL.get());
        h.assertTrue(boiler instanceof dev.denfry.promptcritical.mod.powerhouse.ElectrodeBoilerBlockEntity b
                && b.terminals().getFirst().size() == 1
                && b.terminals().getFirst().getFirst().cell().equals(h.absolutePos(e.offset(1, 3, 1)))
                && b.terminals().getFirst().getFirst().dir() == Direction.UP, "the 6.3 kV cable enters the gland on top only");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void steamDrumRowIsClosedByHeadsAtItsEnds(GameTestHelper h) {
        BlockPos a = new BlockPos(3, 2, 5);
        for (int i = 0; i < 3; i++) {
            BlockPos p = a.east(i);
            BlockState s = north(PcBlocks.STEAM_DRUM.get());
            h.setBlock(p, s);
        }
        // форма по соседям — как при постановке игроком (getStateForPlacement и updateShape зовут то же)
        for (int i = 0; i < 3; i++) {
            BlockPos w = h.absolutePos(a.east(i));
            h.getLevel().setBlock(w, PcBlocks.STEAM_DRUM.get().withEnd(h.getLevel().getBlockState(w), h.getLevel(), w), 3);
        }
        var end = PcProperties.DRUM_END;
        h.assertTrue(h.getBlockState(a.east(2)).getValue(end) == PcProperties.DrumEnd.EAST, "head on the east end");
        h.assertTrue(h.getBlockState(a).getValue(end) == PcProperties.DrumEnd.WEST, "head on the west end");
        h.assertTrue(h.getBlockState(a.east(1)).getValue(end) == PcProperties.DrumEnd.NONE, "plain section in the middle");
        h.succeed();
    }
}
