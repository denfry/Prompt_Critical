package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.FeEnergy;
import dev.denfry.promptcritical.mod.grid.FeInterfaceBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.FeInterface;
import dev.denfry.promptcritical.sim.grid.FeInterfaceUnit;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * {@code grid_fe_interface} (07 §7.7.1) на станции 6,3 кВ: генератор (5, 2, 2) с творческим мотором сзади, шкаф на 1 МВт
 * вплотную к его западной грани. Импорт наполняет буфер и отдаёт FE через capability, экспорт из полного буфера
 * отдаёт мощность в сеть и разгружает генератор.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FeInterfaceGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos IFACE = new BlockPos(4, 2, 2), GEN = new BlockPos(5, 2, 2), LOAD = new BlockPos(4, 3, 2);

    private FeInterfaceGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static void station(GameTestHelper h) {
        h.setBlock(GEN, north(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState()));
        h.setBlock(GEN.above(), north(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState()));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    private static FeInterfaceUnit iface(GameTestHelper h) {
        FeInterfaceUnit u = ((FeInterfaceBlockEntity) h.getBlockEntity(IFACE)).unit();
        h.assertTrue(u != null && u.iface() != null, "interface twin");
        return u;
    }

    private static FeInterfaceUnit load(GameTestHelper h) {
        FeInterfaceUnit u = ((FeInterfaceBlockEntity) h.getBlockEntity(LOAD)).unit();
        h.assertTrue(u != null && u.iface() != null, "load cabinet twin");
        return u;
    }

    private static IEnergyStorage fe(GameTestHelper h) {
        IEnergyStorage s = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, h.absolutePos(IFACE), Direction.UP);
        h.assertTrue(s != null, "FE capability on the cabinet");
        return s;
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void interfaceImportsIntoFeAndExportsBackToTheGrid(GameTestHelper h) {
        h.setBlock(IFACE, north(PcBlocks.GRID_FE_INTERFACE.get().defaultBlockState()));
        // нагрузка острова: большой шкаф на импорте, 100 кВт в свой буфер на 10 МДж — экспорту есть куда отдать.
        // Вместе со шкафом 150 кВт: регулятор генератора статический, при 250 кВт частота уходит ниже 49 Гц и окно
        // отключает оба шкафа
        h.setBlock(LOAD, north(PcBlocks.GRID_FE_INTERFACE_LARGE.get().defaultBlockState()));
        station(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    iface(h);
                    load(h);
                })
                .thenExecute(() -> {
                    GeneratorUnit g = gen(h);
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    FeInterface f = iface(h).iface();
                    f.setMode(FeInterface.Mode.IMPORT);
                    f.setSetpointW(50e3);
                    FeInterface l = load(h).iface();
                    l.setMode(FeInterface.Mode.IMPORT);
                    l.setSetpointW(100e3);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95 && gen(h).terminalVoltageV() > 6000, "station runs"))
                .thenWaitUntil(() -> h.assertTrue(iface(h).iface().connected(), "the interface reconnects after 5 s in the window"))
                .thenWaitUntil(() -> h.assertTrue(iface(h).iface().fill() > 0.99, "the buffer fills: " + iface(h).iface().fill() + " mode " + iface(h).iface().mode() + " on "
                        + iface(h).iface().connected() + " p " + iface(h).powerW() + " load " + load(h).iface().mode() + " " + load(h).powerW()
                        + " gen " + gen(h).electricalPowerW() + " f " + iface(h).frequencyHz() + " v " + iface(h).voltagePu()))
                .thenExecute(() -> {
                    IEnergyStorage s = fe(h);
                    double j = FeEnergy.jPerFe();
                    h.assertTrue(Math.abs(s.getMaxEnergyStored() - 1e6 / j) < 2, "one second of 1 MW in FE: " + s.getMaxEnergyStored());
                    h.assertFalse(s.canReceive(), "import does not take FE in");
                    int got = s.extractEnergy(200_000, false);
                    h.assertTrue(got == 200_000, "FE comes out of the buffer: " + got);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    double p = iface(h).powerW();
                    h.assertTrue(p > 40e3 && p < 60e3, "refilling draws the 50 kW limit from the grid: " + p + " (iface " + iface(h).iface().gridW()
                            + ", fill " + iface(h).iface().fill() + ", on " + iface(h).iface().connected() + ", "
                            + iface(h).voltagePu() + " pu, " + iface(h).frequencyHz() + " Hz, load " + load(h).powerW() + ")");
                    iface(h).iface().setMode(FeInterface.Mode.EXPORT);
                    IEnergyStorage s = fe(h);
                    h.assertTrue(s.canReceive() && !s.canExtract(), "export takes FE in only");
                    s.receiveEnergy(Integer.MAX_VALUE, false);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    double p = iface(h).powerW();
                    FeInterface f = iface(h).iface();
                    h.assertTrue(p < -40e3, "the cabinet feeds the grid: " + p + " (on " + f.connected() + ", "
                            + iface(h).frequencyHz() + " Hz, " + iface(h).voltagePu() + " pu)");
                    h.assertTrue(load(h).powerW() > 80e3, "the load still draws: " + load(h).powerW());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void exportCannotLiveWithoutAGrid(GameTestHelper h) {
        h.setBlock(IFACE, north(PcBlocks.GRID_FE_INTERFACE.get().defaultBlockState()));
        h.startSequence()
                .thenWaitUntil(() -> iface(h))
                .thenExecute(() -> {
                    iface(h).iface().setMode(FeInterface.Mode.EXPORT);
                    fe(h).receiveEnergy(Integer.MAX_VALUE, false);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    FeInterface f = iface(h).iface();
                    h.assertFalse(f.connected(), "no voltage, no connection");
                    h.assertTrue(f.gridW() == 0 && f.fill() > 0.99, "FE stays in the buffer: " + f.gridW());
                })
                .thenSucceed();
    }
}
