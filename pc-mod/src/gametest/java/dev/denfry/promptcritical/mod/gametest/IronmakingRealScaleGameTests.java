package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.ironworks.BlastBlowerEngineBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.BlastFurnaceControllerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.ColdBlastSource;
import dev.denfry.promptcritical.mod.ironworks.HotBlastStoveBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.HotBlastStoveRealBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SitePlans;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.sim.metallurgy.FoundrySubsystem;
import dev.denfry.promptcritical.sim.metallurgy.IronmakingSubsystem;
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
 * Доменный цех реального масштаба (08 §8.5): каупер и воздуходувная машина строятся на стройплощадке и работают на
 * юнитах старой серии, лётка и приборный шкаф {@code _real} собирают ту же домну, что старые.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IronmakingRealScaleGameTests {
    private static final String TALL = "gametest/empty_12x24x12";

    private IronmakingRealScaleGameTests() {}

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /** Площадка на каменном основании под объёмом x0..x1, z0..z1 от контроллера (фасад на север). */
    private static ConstructionSiteBlockEntity site(GameTestHelper h, BlockPos p, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, facing(SiteRegistry.SITE.get().defaultBlockState(), Direction.NORTH));
        return (ConstructionSiteBlockEntity) h.getBlockEntity(p);
    }

    /**
     * Каупер 5 × 14 × 5: площадка принимает проект, контроллер рисует цельную модель и работает на юните старого
     * воздухонагревателя (домна находит его тем же классом), газ — только в патрубок горелки.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void realHotBlastStoveRunsTheStoveUnit(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 3);
        var w = site(h, p, -2, 2, 0, 4).accept("hot_blast_stove_real");
        h.assertTrue(w == null, "stove plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_HOT_BLAST_STOVE_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 5 * 14 * 5, "5x14x5 stove");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, facing(PcBlocks.HOT_BLAST_STOVE_REAL.get().defaultBlockState(), Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof HotBlastStoveRealBlockEntity b && "hot_blast_stove_real".equals(b.wholeModelId())
                && IronmakingSubsystem.HOT_BLAST_STOVE.equals(b.deviceType()), "the real stove draws its whole model and runs the stove unit");
        var fluids = Capabilities.FluidHandler.BLOCK;
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(-1, 1, 0)), Direction.NORTH) != null, "gas into the burner port");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(-1, 1, 0)), Direction.WEST) == null,
                "the burner cell takes gas only from the north");
        h.assertTrue(h.getLevel().getCapability(fluids, h.absolutePos(p.offset(1, 5, 0)), Direction.NORTH) == null, "the shell is no gas port");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(p) instanceof HotBlastStoveBlockEntity b && b.unit() != null,
                "the real stove is bound to a HotBlastStoveUnit"));
    }

    /**
     * Воздуходувная машина 7 × 3 × 3: площадка принимает проект, вал Create входит в клетку (0, 1, 0), юнит — та же
     * воздуходувка, что старая, и домна считает её источником холодного дутья.
     */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void realBlowingEngineRunsTheBlowerUnit(GameTestHelper h) {
        BlockPos p = new BlockPos(2, 1, 5);
        var w = site(h, p, 0, 6, -1, 1).accept("blast_blower_engine");
        h.assertTrue(w == null, "blowing engine plan accepted: " + (w == null ? "" : w.getString()));
        h.assertTrue(PcBlocks.FP_BLAST_BLOWER_ENGINE.cells(BlockPos.ZERO, Direction.NORTH).size() == 7 * 3 * 3, "7x3x3 engine");
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, facing(PcBlocks.BLAST_BLOWER_ENGINE.get().defaultBlockState(), Direction.NORTH));
        h.assertTrue(h.getBlockEntity(p) instanceof BlastBlowerEngineBlockEntity b && "blast_blower_engine".equals(b.wholeModelId())
                && FoundrySubsystem.BLAST_BLOWER.equals(b.deviceType()) && b instanceof ColdBlastSource,
                "the engine draws its whole model, runs the blower unit and blows the furnace");
        BlockState port = h.getBlockState(p.offset(0, 1, 0));
        h.assertTrue(port.is(PcBlocks.KINETIC_PORT.get()) && port.getValue(KineticPortBlock.INPUT), "the pinion cell takes the Create shaft");
        h.assertTrue(WholePorts.of("blast_blower_engine").isEmpty(), "no pipe ports: the furnace takes the blast by range");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(p) instanceof BlastBlowerEngineBlockEntity b && b.unit() != null && b.coldNm3S() == 0,
                "bound to its unit and still without a shaft"));
    }

    /**
     * Домна на лётке и приборном шкафу реального масштаба: та же печь 5 × 5 (V_u 99 м³), что у старых блоков в
     * IronworksGameTests, собирается и привязывается к юниту домны.
     */
    @GameTest(template = TALL, timeoutTicks = 300)
    public static void realTapholeAndCabinetFormTheFurnace(GameTestHelper h) {
        BlockState shell = PcBlocks.BLAST_FURNACE_SHELL.get().defaultBlockState();
        for (int y = 1; y <= 12; y++)
            for (int x = 3; x <= 7; x++)
                for (int z = 3; z <= 7; z++)
                    if (y == 1 || x == 3 || x == 7 || z == 3 || z == 7) h.setBlock(new BlockPos(x, y, z), shell);
        h.setBlock(new BlockPos(5, 2, 3), facing(PcBlocks.BLAST_FURNACE_TAPHOLE_REAL.get().defaultBlockState(), Direction.NORTH));
        BlockState tuyere = PcBlocks.BLAST_FURNACE_TUYERE.get().defaultBlockState();
        h.setBlock(new BlockPos(5, 3, 3), facing(tuyere, Direction.NORTH));
        h.setBlock(new BlockPos(5, 3, 7), facing(tuyere, Direction.SOUTH));
        h.setBlock(new BlockPos(3, 3, 5), facing(tuyere, Direction.WEST));
        h.setBlock(new BlockPos(7, 3, 5), facing(tuyere, Direction.EAST));
        h.setBlock(new BlockPos(5, 13, 5), PcBlocks.BLAST_FURNACE_TOP.get().defaultBlockState());
        BlockPos controller = new BlockPos(2, 4, 5);
        h.setBlock(controller, facing(PcBlocks.BLAST_FURNACE_CONTROLLER_REAL.get().defaultBlockState(), Direction.WEST));
        h.assertTrue(h.getBlockEntity(controller) instanceof BlastFurnaceControllerBlockEntity c
                && IronmakingSubsystem.BLAST_FURNACE.equals(c.deviceType()), "the cabinet is the furnace controller");
        h.succeedWhen(() -> h.assertTrue(h.getBlockEntity(controller) instanceof BlastFurnaceControllerBlockEntity c && c.shape() != null
                && c.unit() != null && c.shape().usefulVolumeM3() == 99 && h.absolutePos(new BlockPos(5, 2, 3)).equals(c.shape().taphole()),
                "the furnace forms on the real taphole"));
    }

    /** Проекты каупера и воздуходувной машины — в общем списке и по реальной массе (±50 %). */
    @GameTest(template = "gametest/empty_12x12x12", timeoutTicks = 20)
    public static void ironmakingPlansFollowTheRealMass(GameTestHelper h) {
        for (String id : new String[] {"hot_blast_stove_real", "blast_blower_engine"}) {
            SitePlans.Plan plan = SitePlans.get(id);
            h.assertTrue(plan != null, id + " has a site plan");
            double r = plan.plannedMetalKg() / plan.metalKg();
            h.assertTrue(r > 1 / 1.5 && r < 1.5, id + ": planned metal vs prototype " + r);
            boolean listed = false;
            for (SitePlans.Plan q : SitePlans.all()) listed |= q.id().equals(id);
            h.assertTrue(listed, id + " is in the plan list");
        }
        h.succeed();
    }
}
