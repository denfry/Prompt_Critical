package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.ElectrodeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.PumpedStorageBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.StoragePanels;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.ElectrodeBoilerUnit;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Излишки энергии в мире: электродный котёл вплотную к жаротрубному котлу берёт мощность из сети 6,3 кВ по ручному
 * заданию и отдаёт тепло в коллектор; без котла рядом электроды осушены; агрегат ГАЭС без затвора водозабора выше —
 * без паспорта и стоит.
 *
 * <p>Раскладка (фасадом на север): жаротрубный котёл (2, 2, 2) — клетки x 1…3, z 1…6; электродный котёл (4, 2, 2)
 * касается его восточной грани; генератор 6,3 кВ (5, 2, 2) — западной гранью к электродному котлу, возбудитель сверху,
 * творческий мотор сзади.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StorageGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos BOILER = new BlockPos(2, 2, 2), ELECTRODE = new BlockPos(4, 2, 2), GEN = new BlockPos(5, 2, 2);

    private StorageGameTests() {
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

    private static ElectrodeBoilerUnit electrode(GameTestHelper h) {
        ElectrodeBoilerUnit u = ((ElectrodeBoilerBlockEntity) h.getBlockEntity(ELECTRODE)).unit();
        h.assertTrue(u != null, "electrode boiler twin");
        return u;
    }

    private static void startGenerator(GameTestHelper h) {
        GeneratorUnit g = gen(h);
        g.setVoltageClass(VoltageClass.MV_6_3);
        while (g.gearRpm() != 64) g.cycleGear();
        g.setFieldOn(true);
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void electrodeBoilerDrawsFromTheGridIntoTheBoilerHouse(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
        h.setBlock(ELECTRODE, north(PcBlocks.ELECTRODE_BOILER.get().defaultBlockState()));
        station(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    electrode(h);
                })
                .thenExecute(() -> {
                    startGenerator(h);
                    ElectrodeBoilerUnit e = electrode(h);
                    e.boiler().setMode(ElectrodeBoiler.Mode.MANUAL);
                    e.boiler().setManualSetpoint(0.2);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95 && gen(h).terminalVoltageV() > 6000, "station runs"))
                .thenWaitUntil(() -> h.assertTrue(electrode(h).steamSink(), "the fire-tube boiler takes the steam"))
                .thenIdle(400) // уровень на электродах поднимается до задания (полный ход — минута)
                .thenExecute(() -> {
                    ElectrodeBoilerUnit e = electrode(h);
                    h.assertTrue(e.boiler().level() > 0.19, "electrodes wetted to the set point: " + e.boiler().level());
                    h.assertTrue(e.powerW() > 150e3 && e.powerW() < 250e3, "draws about 200 kW: " + e.powerW());
                    h.assertTrue(gen(h).electricalPowerW() > 150e3, "the station carries it: " + gen(h).electricalPowerW());
                    int[] out = new int[StoragePanels.CHANNELS];
                    ((ElectrodeBoilerBlockEntity) h.getBlockEntity(ELECTRODE)).readPanel(out);
                    h.assertTrue((out[StoragePanels.FLAGS] & 1 << StoragePanels.F_READY) != 0, "panel: steam lamp lit");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void electrodeBoilerWithoutABoilerKeepsItsElectrodesDry(GameTestHelper h) {
        h.setBlock(ELECTRODE, north(PcBlocks.ELECTRODE_BOILER.get().defaultBlockState()));
        station(h);
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    electrode(h);
                })
                .thenExecute(() -> {
                    startGenerator(h);
                    electrode(h).boiler().setMode(ElectrodeBoiler.Mode.MANUAL);
                    electrode(h).boiler().setManualSetpoint(1);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95, "station runs"))
                .thenIdle(200)
                .thenExecute(() -> {
                    ElectrodeBoilerUnit e = electrode(h);
                    h.assertTrue(!e.steamSink(), "no boiler to take the steam");
                    h.assertTrue(e.boiler().level() == 0 && e.powerW() < 1e3, "electrodes dry, no load: " + e.powerW());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void pumpedStorageUnitWithoutAnUpperBasinStandsStill(GameTestHelper h) {
        BlockPos at = new BlockPos(6, 2, 6);
        h.setBlock(at, north(PcBlocks.PUMPED_STORAGE_UNIT.get().defaultBlockState()));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((PumpedStorageBlockEntity) h.getBlockEntity(at)).unit() != null, "unit twin"))
                .thenIdle(220) // пересканирование мира
                .thenExecute(() -> {
                    PumpedStorageBlockEntity b = h.getBlockEntity(at);
                    h.assertTrue(b.unit().plant() == null, "no dam gate above: no plant");
                    int[] out = new int[StoragePanels.CHANNELS];
                    b.readPanel(out);
                    h.assertTrue((out[StoragePanels.FLAGS] & 1 << StoragePanels.F_READY) == 0, "panel reports no upper basin");
                })
                .thenSucceed();
    }
}
