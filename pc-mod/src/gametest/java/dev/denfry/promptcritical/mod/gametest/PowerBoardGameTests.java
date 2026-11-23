package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.grid.MachinePower;
import dev.denfry.promptcritical.mod.grid.PowerBoardBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.PowerBoard;
import dev.denfry.promptcritical.sim.grid.PowerBoardUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Питание машин от сети 0,4 кВ ({@link MachinePower}): стенд щитка освещения — генератор 6,3 кВ, трансформатор, кабель
 * к стене за щитом. «Машина» — заявка мощности из точки {@link #MACHINE} в зоне щита; коробка стенда отмечена как
 * строгая, поэтому на сервере GameTest питание здесь настоящее.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerBoardGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), TRAFO = new BlockPos(1, 1, 5), BOARD = new BlockPos(1, 1, 7),
            MACHINE = new BlockPos(3, 1, 9);

    private PowerBoardGameTests() {
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    /** Генератор, трансформатор, кабели и щит {@code board}; коробка стенда — строгая. */
    private static void stand(GameTestHelper h, Block board) {
        MachinePower.strictArea(h.getLevel(), h.absolutePos(BlockPos.ZERO), h.absolutePos(new BlockPos(11, 11, 11)));
        h.setBlock(GEN, facing(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(GEN.above(), facing(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState(), Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        for (BlockPos c : new BlockPos[] {new BlockPos(3, 1, 5), new BlockPos(3, 2, 5), new BlockPos(2, 2, 5), new BlockPos(1, 1, 6)})
            h.setBlock(c, PcBlocks.POWER_CABLE.get().defaultBlockState());
        h.setBlock(TRAFO, facing(PcBlocks.DISTRIBUTION_TRANSFORMER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(BOARD, facing(board.defaultBlockState(), Direction.SOUTH));
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    private static PowerBoardUnit board(GameTestHelper h) {
        PowerBoardUnit u = ((PowerBoardBlockEntity) h.getBlockEntity(BOARD)).unit();
        h.assertTrue(u != null && u.board() != null, "board twin");
        return u;
    }

    private static void start(GameTestHelper h) {
        GeneratorUnit g = gen(h);
        g.setVoltageClass(VoltageClass.MV_6_3);
        while (g.gearRpm() != 64) g.cycleGear();
        g.setFieldOn(true);
    }

    /** Машина заявляет {@code watts} и получает долю. */
    private static double feed(GameTestHelper h, double watts) {
        return MachinePower.supply(h.getLevel(), h.absolutePos(MACHINE), watts);
    }

    @GameTest(template = LARGE, timeoutTicks = 3000, batch = "power_board")
    public static void boardSlowsOverloadedMachinesAndDropsOutOnBlackout(GameTestHelper h) {
        stand(h, PcBlocks.POWER_BOARD.get());
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    board(h);
                })
                .thenExecute(() -> {
                    h.assertTrue(feed(h, 30e3) == 0, "no voltage, the machine stands");
                    start(h);
                })
                .thenWaitUntil(() -> h.assertTrue(feed(h, 30e3) > 0.95, "the machine runs: " + feed(h, 30e3) + ", input "
                        + board(h).inputPu() + " pu, demand " + board(h).board().demandW()))
                .thenWaitUntil(() -> {
                    double f = feed(h, 150e3);
                    h.assertTrue(f > 0.55 && f < 0.7, "100 kW incomer gives two thirds of 150 kW: " + f);
                })
                .thenExecute(() -> {
                    feed(h, 30e3);
                    gen(h).setFieldOn(false);
                })
                .thenWaitUntil(() -> {
                    feed(h, 30e3);
                    h.assertTrue(board(h).board().tripped(), "undervoltage drops the contactor");
                })
                .thenExecute(() -> gen(h).setFieldOn(true))
                .thenWaitUntil(() -> h.assertTrue(board(h).inputPu() > 0.95, "voltage is back"))
                .thenIdle(40)
                .thenExecute(() -> {
                    h.assertTrue(feed(h, 30e3) == 0, "no self-start");
                    h.assertFalse(h.getBlockState(BOARD).getValue(BlockStateProperties.LIT), "lamp out");
                    board(h).board().start();
                })
                .thenWaitUntil(() -> h.assertTrue(feed(h, 30e3) > 0.95, "Start runs the group again"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 3000, batch = "power_board")
    public static void upsCarriesItsLoadThroughABlackout(GameTestHelper h) {
        stand(h, PcBlocks.UPS_CABINET.get());
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    board(h);
                })
                .thenExecute(() -> start(h))
                .thenWaitUntil(() -> h.assertTrue(feed(h, 5e3) > 0.99, "UPS output on mains: " + feed(h, 5e3)))
                .thenExecute(() -> gen(h).setFieldOn(false))
                .thenWaitUntil(() -> {
                    double f = feed(h, 5e3);
                    PowerBoard b = board(h).board();
                    h.assertTrue(b.onBattery() && f > 0.99, "on battery the load still runs: " + f);
                    h.assertTrue(h.getBlockState(BOARD).getValue(PcProperties.ALARM), "battery lamp");
                })
                .thenWaitUntil(() -> {
                    double f = feed(h, 5e3);
                    PowerBoard b = board(h).board();
                    h.assertTrue(b.batterySoc() < 0.9999, "battery discharges: soc " + b.batterySoc() + ", on battery "
                            + b.onBattery() + ", demand " + b.demandW() + ", factor " + f + ", input " + board(h).inputPu() + " pu");
                })
                .thenSucceed();
    }
}
