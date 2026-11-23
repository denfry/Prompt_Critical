package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.boiler.KineticInputBlockEntity;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlockEntity;
import dev.denfry.promptcritical.mod.grid.ElectricMotorBlockEntity;
import dev.denfry.promptcritical.mod.grid.RealShaftMachineBlock;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.multiblock.MultiblockPartBlock;
import dev.denfry.promptcritical.mod.ops.WaterTreatmentBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.DeaeratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.sim.utilities.UtilitiesSubsystem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Энергетика и водоподготовка реального масштаба: деаэратор ДА-25, Na-катионитовый фильтр, генератор 500 кВ·А и
 * двигатель 1 МВт 6 кВ — те же BE и юниты симуляции, что у одноблочных машин, с цельной моделью, портами и
 * площадкой; возбудитель 1 × 2 × 1 и трансформатор 2 × 2 × 1 под перерисованные модели.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerRealScaleGameTests {
    private static final String ROOM = "gametest/empty_12x12x12";

    private PowerRealScaleGameTests() {}

    private static BlockState north(net.minecraft.world.level.block.Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Площадка на каменном основании {@code x0..x1 × z0..z1} от неё принимает чертёж {@code id}. */
    private static void siteAccepts(GameTestHelper h, BlockPos p, String id, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) h.setBlock(p.offset(x, -1, z), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
    }

    private static boolean fluidAt(GameTestHelper h, BlockPos rel, Direction side) {
        return h.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, h.absolutePos(rel), side) != null;
    }

    /** ДА-25 6 × 5 × 3 — тот же деаэратор с цельной моделью; вода подключается только у патрубков. */
    @GameTest(template = ROOM, timeoutTicks = 40)
    public static void deaeratorRealIsTheSameDeaeratorWithItsPorts(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        siteAccepts(h, p, "deaerator_real", -2, 3, -1, 1);
        h.assertTrue(PcBlocks.FP_DEAERATOR_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 6 * 5 * 3, "6x5x3 deaerator");
        h.setBlock(p, north(PcBlocks.DEAERATOR_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof DeaeratorBlockEntity d && "deaerator_real".equals(d.wholeModelId()),
                "the real deaerator is the deaerator with its whole model");
        h.assertTrue(fluidAt(h, p.offset(2, 4, -1), Direction.NORTH), "feedwater in at the north face of the column");
        h.assertTrue(fluidAt(h, p.offset(2, 4, 1), Direction.SOUTH), "condensate in at the south face of the column");
        h.assertTrue(!fluidAt(h, p.offset(2, 4, -1), Direction.EAST), "the column cell is closed from the side");
        h.assertTrue(!fluidAt(h, p.offset(-1, 1, 0), Direction.WEST), "the tank shell has no port");
        BlockPos old = new BlockPos(1, 1, 9);
        h.setBlock(old, north(PcBlocks.DEAERATOR.get()));
        h.assertTrue(h.getBlockEntity(old) instanceof DeaeratorBlockEntity d && d.wholeModelId() == null, "the old deaerator keeps its model");
        h.succeed();
    }

    /** Na-катионитовый фильтр 1 × 4 × 1 — ступень умягчения той же колонки: вода сверху, умягчённая — снизу спереди. */
    @GameTest(template = ROOM, timeoutTicks = 40)
    public static void ionExchangeFilterRealIsTheSofteningStage(GameTestHelper h) {
        BlockPos p = new BlockPos(5, 1, 5);
        siteAccepts(h, p, "ion_exchange_column_real", 0, 0, 0, 0);
        h.assertTrue(PcBlocks.FP_ION_EXCHANGE_COLUMN_REAL.cells(BlockPos.ZERO, Direction.NORTH).size() == 4, "1x4x1 filter");
        h.setBlock(p, north(PcBlocks.ION_EXCHANGE_COLUMN_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof WaterTreatmentBlockEntity w && "ion_exchange_column_real".equals(w.wholeModelId())
                && UtilitiesSubsystem.ION_EXCHANGE_COLUMN.equals(w.deviceType()) && w.accepts(new ItemStack(PcItems.SALT.get())),
                "the real filter is the sodium ion exchange stage and takes salt");
        h.assertTrue(fluidAt(h, p.offset(0, 3, 0), Direction.UP), "water in through the top head");
        h.assertTrue(!fluidAt(h, p.offset(0, 2, 0), Direction.EAST), "the shell has no port");
        h.assertTrue(fluidAt(h, p, Direction.NORTH), "softened water out at the front of the bottom cell");
        h.succeed();
    }

    /** Генератор 3 × 3 × 3: вал Create входит в порт на южном торце, возбудитель стоит на оси у северного. */
    @GameTest(template = ROOM, timeoutTicks = 40)
    public static void generatorRealTakesItsShaftAtTheSouthPort(GameTestHelper h) {
        Direction f = Direction.NORTH;
        BlockPos p = new BlockPos(4, 1, 4);
        siteAccepts(h, p, "shaft_generator_real", -1, 1, -1, 1);
        h.assertTrue(PcBlocks.FP_ELECTRIC_MACHINE_REAL.cells(BlockPos.ZERO, f).size() == 27, "3x3x3 machine");
        h.setBlock(p, north(PcBlocks.SHAFT_GENERATOR_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof ShaftGeneratorBlockEntity g && g.real()
                && "shaft_generator_real".equals(g.wholeModelId()), "the real alternator is the shaft generator with its whole model");
        BlockPos port = RealShaftMachineBlock.at(h.absolutePos(p), f, RealShaftMachineBlock.GENERATOR_PORT);
        var ps = h.getLevel().getBlockState(port);
        h.assertTrue(ps.is(PcBlocks.KINETIC_PORT.get()) && ps.getValue(KineticPortBlock.INPUT)
                && ps.getValue(KineticPortBlock.AXIS) == Direction.Axis.Z, "input port at the south end, shaft along the facade");
        h.assertTrue(h.getLevel().getBlockEntity(port) instanceof KineticInputBlockEntity in && in.owner() == h.getBlockEntity(p),
                "the port drives the generator");
        h.assertFalse(((RealShaftMachineBlock) PcBlocks.SHAFT_GENERATOR_REAL.get()).hasShaftTowards(h.getLevel(), h.absolutePos(p),
                h.getBlockState(p), Direction.SOUTH), "the controller has no shaft of its own");
        // возбудитель 1 × 2 × 1 на оси: контроллер на высоте вала, постамент — клетка под ним
        BlockPos ex = p.offset(RealShaftMachineBlock.EXCITER_CELL);
        h.setBlock(ex, north(PcBlocks.GENERATOR_EXCITER.get()));
        h.assertTrue(h.getBlockState(ex.below()).getBlock() instanceof MultiblockPartBlock, "the exciter pedestal fills the cell below");
        h.runAfterDelay(5, () -> {
            h.assertTrue(h.getBlockEntity(p) instanceof ShaftGeneratorBlockEntity g && !g.simId().isNone(), "bound to the simulation");
            h.succeed();
        });
    }

    /** Двигатель 1 МВт 6 кВ 3 × 3 × 3: вращение отдаёт порт-источник на северном торце, исполнение всегда 6 кВ. */
    @GameTest(template = ROOM, timeoutTicks = 40)
    public static void motorRealDrivesTheShaftFromTheNorthPort(GameTestHelper h) {
        Direction f = Direction.NORTH;
        BlockPos p = new BlockPos(4, 1, 4);
        siteAccepts(h, p, "electric_motor_mv_real", -1, 1, -1, 1);
        h.setBlock(p, north(PcBlocks.ELECTRIC_MOTOR_MV_REAL.get()));
        h.assertTrue(h.getBlockEntity(p) instanceof ElectricMotorBlockEntity m && m.real()
                && "electric_motor_mv_real".equals(m.wholeModelId()) && m.getGeneratedSpeed() == 0,
                "the real motor is the electric motor with its whole model and no shaft at the controller");
        BlockPos port = RealShaftMachineBlock.at(h.absolutePos(p), f, RealShaftMachineBlock.MOTOR_PORT);
        var ps = h.getLevel().getBlockState(port);
        h.assertTrue(ps.is(PcBlocks.KINETIC_PORT.get()) && !ps.getValue(KineticPortBlock.INPUT)
                && ps.getValue(KineticPortBlock.AXIS) == Direction.Axis.Z, "source port at the north end, shaft along the facade");
        h.assertTrue(h.getLevel().getBlockEntity(port) instanceof KineticPortBlockEntity, "the port is a Create source");
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getBlockEntity(p) instanceof ElectricMotorBlockEntity m && m.unit() != null && m.unit().mediumVoltage(),
                    "the 1 MW motor is a 6 kV machine");
            h.succeed();
        });
    }

    /** Трансформатор перерисован 2 × 2 × 1: клетки справа и сверху — его части. */
    @GameTest(template = ROOM, timeoutTicks = 20)
    public static void transformerTakesItsTwoByTwoFootprint(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        h.setBlock(p, north(PcBlocks.DISTRIBUTION_TRANSFORMER.get()));
        for (BlockPos c : new BlockPos[] {p.above(), p.east(), p.east().above()})
            h.assertTrue(h.getBlockState(c).getBlock() instanceof MultiblockPartBlock, "transformer cell " + c);
        h.succeed();
    }
}
