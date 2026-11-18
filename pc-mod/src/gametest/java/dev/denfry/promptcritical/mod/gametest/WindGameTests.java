package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.WindTurbineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.WindTurbineUnit;
import dev.denfry.promptcritical.sim.wind.WindTurbine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Ветроэлектростанция в мире: генератор на валу 0,4 кВ держит напряжение и частоту, потребитель берёт 200 кВт;
 * ВЭУ вплотную к генератору при ровном ветре 9 м/с разгоняется, включается у синхронной скорости и отдаёт мощность
 * в сеть, разгружая генератор.
 *
 * <p>Раскладка (фасадом на север): потребитель 3×3×3 с центром (2, 2, 5), генератор (4, 1, 5) с возбудителем сверху и
 * творческим мотором сзади, башня ВЭУ (5, 1…9, 5) — контроллер (5, 2, 5), нижний блок касается генератора западной
 * гранью.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos LOAD = new BlockPos(2, 2, 5), GEN = new BlockPos(4, 1, 5), WT = new BlockPos(5, 2, 5);

    private WindGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    private static WindTurbineUnit wind(GameTestHelper h) {
        WindTurbineUnit u = ((WindTurbineBlockEntity) h.getBlockEntity(WT)).unit();
        h.assertTrue(u != null, "wind turbine twin");
        return u;
    }

    @GameTest(template = LARGE, timeoutTicks = 3600)
    public static void windTurbineExportsPowerIntoTheGrid(GameTestHelper h) {
        h.setBlock(LOAD, north(PcBlocks.GRID_CONSUMER.get().defaultBlockState()));
        h.setBlock(GEN, north(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState()));
        h.setBlock(GEN.above(), north(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState()));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.setBlock(WT, north(PcBlocks.WIND_TURBINE.get().defaultBlockState()));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        double[] loadW = {0};
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    wind(h);
                    h.assertTrue(((ConsumerBlockEntity) h.getBlockEntity(LOAD)).unit() != null, "consumer twin");
                })
                .thenExecute(() -> {
                    h.assertBlockPresent(PcBlocks.MULTIBLOCK_PART.get(), WT.above(7)); // башня собрана целиком, до оголовка
                    ((WindTurbineBlockEntity) h.getBlockEntity(WT)).setTestWind(9);
                    GeneratorUnit g = gen(h);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    ConsumerUnit c = ((ConsumerBlockEntity) h.getBlockEntity(LOAD)).unit();
                    c.setDemandKw(200);
                    c.setSwitchedOn(true);
                    wind(h).setEnabled(true);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95 && gen(h).terminalVoltageV() > 360, "station runs"))
                .thenWaitUntil(() -> {
                    WindTurbineUnit w = wind(h);
                    h.assertTrue(w.turbine().state() == WindTurbine.State.GENERATING && w.contactorClosed(),
                            "wind turbine on the grid: " + w.turbine().state() + " " + Math.round(w.rotorRpm()) + " rpm, pitch "
                                    + Math.round(w.turbine().pitchDeg()));
                })
                .thenIdle(200) // 10 с на установившийся режим
                .thenExecute(() -> {
                    WindTurbineUnit w = wind(h);
                    loadW[0] = ((ConsumerBlockEntity) h.getBlockEntity(LOAD)).unit().powerW();
                    h.assertTrue(Math.abs(Math.toDegrees(w.turbine().yawErrorRad())) < 10, "rotor faces the wind");
                    h.assertTrue(w.exportW() > 60e3 && w.exportW() < 110e3, "wind export at 9 m/s: " + Math.round(w.exportW() / 1e3) + " kW");
                    h.assertTrue(w.slip() < 0, "negative slip: " + w.slip());
                    h.assertTrue(w.reactiveVar() > 0, "draws magnetising reactive power");
                    h.assertTrue(gen(h).electricalPowerW() < loadW[0] - 0.5 * w.exportW(),
                            "the station generator is relieved: gen " + Math.round(gen(h).electricalPowerW() / 1e3) + " kW, load "
                                    + Math.round(loadW[0] / 1e3) + " kW");
                    h.assertTrue(h.getBlockState(WT).getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING), "running lamp");
                })
                .thenSucceed();
    }
}
