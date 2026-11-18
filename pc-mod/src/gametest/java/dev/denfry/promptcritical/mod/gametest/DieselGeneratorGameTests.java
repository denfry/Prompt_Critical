package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.grid.ConsumerBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.DieselGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.combustion.DieselEngine;
import dev.denfry.promptcritical.sim.grid.ConsumerUnit;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Аварийный ДГ в мире (07 §7.11, критерий M5 «ДГ принимает нагрузку за ≤ 15 с»): агрегат на автомате при обесточенной
 * шине пускается, включается на шину и питает потребителя по кабельным выводам генератора; без аккумулятора
 * оперативного тока пуска нет.
 *
 * <p>Раскладка (фасадом на север): контроллер ДГ (5, 2, 2) — дизель x 4…6, z 1…3, генератор x 4…6, z 4…6;
 * аккумулятор (7, 2, 2) касается дизеля с востока; потребитель 3×3×3 с центром (8, 2, 5) касается генератора с востока.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DieselGeneratorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos DG = new BlockPos(5, 2, 2), BATTERY = new BlockPos(7, 2, 2), LOAD = new BlockPos(8, 2, 5);
    /** Ступень пуска: 1 с выдержки по минимальному напряжению + не больше 15 с до приёма нагрузки (§7.11.2). */
    private static final int ACCEPT_TICKS = 20 * 16;

    private DieselGeneratorGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static GeneratorUnit dg(GameTestHelper h) {
        GeneratorUnit u = ((DieselGeneratorBlockEntity) h.getBlockEntity(DG)).unit();
        h.assertTrue(u != null && u.diesel() != null, "diesel generator twin");
        return u;
    }

    private static ConsumerUnit load(GameTestHelper h) {
        ConsumerUnit u = ((ConsumerBlockEntity) h.getBlockEntity(LOAD)).unit();
        h.assertTrue(u != null, "consumer twin");
        return u;
    }

    private static void place(GameTestHelper h, boolean battery) {
        h.setBlock(DG, north(PcBlocks.DIESEL_GENERATOR.get().defaultBlockState()));
        if (battery) h.setBlock(BATTERY, north(PcBlocks.STATION_BATTERY.get().defaultBlockState()));
        h.setBlock(LOAD, north(PcBlocks.GRID_CONSUMER.get().defaultBlockState()));
    }

    private static void fuel(GameTestHelper h) {
        DieselGeneratorBlockEntity d = h.getBlockEntity(DG);
        int n = d.fluids(null).fill(new FluidStack(PcFluids.DIESEL_FUEL.source.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
        h.assertTrue(n == 1000, "the day tank takes diesel fuel: " + n);
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void dieselOnAutoStartsOnADeadBusAndTakesTheLoadWithinFifteenSeconds(GameTestHelper h) {
        place(h, true);
        double base = ServerConfig.DIESEL_FAIL_TO_START_BASE.get();
        long[] armedAt = {-1};
        h.startSequence()
                .thenWaitUntil(() -> {
                    dg(h);
                    load(h);
                })
                .thenExecute(() -> {
                    ServerConfig.DIESEL_FAIL_TO_START_BASE.set(0.0); // случайный отказ пуска проверяет домен
                    fuel(h);
                    GeneratorUnit g = dg(h);
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    ConsumerUnit c = load(h);
                    c.setDemandKw(200);
                    c.setSwitchedOn(true);
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    h.assertTrue(dg(h).diesel().status() == DieselEngine.Status.STANDBY, "standby before the auto key");
                    dg(h).setAutoStart(true);
                    armedAt[0] = h.getTick();
                })
                .thenWaitUntil(() -> {
                    GeneratorUnit g = dg(h);
                    h.assertTrue(g.breakerClosed() && load(h).energized(), "waiting for the bus: " + g.diesel().status()
                            + ", " + g.speedPu() + " pu, cause " + g.diesel().cause());
                })
                .thenExecute(() -> {
                    long took = h.getTick() - armedAt[0];
                    h.assertTrue(took <= ACCEPT_TICKS, "load accepted in " + took / 20.0 + " s");
                    h.assertTrue(dg(h).diesel().starts() == 1, "one start");
                    h.assertBlockProperty(DG, dev.denfry.promptcritical.mod.block.PcProperties.RUNNING, true);
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    GeneratorUnit g = dg(h);
                    h.assertTrue(Math.abs(g.frequencyHz() - 50) < 0.5, "frequency held: " + g.frequencyHz());
                    h.assertTrue(Math.abs(g.terminalVoltageV() - 6300) < 630, "voltage held: " + g.terminalVoltageV());
                    h.assertTrue(load(h).powerW() > 150e3, "consumer fed: " + load(h).powerW());
                    DieselGeneratorBlockEntity d = h.getBlockEntity(DG);
                    h.assertTrue(d.fuel().amount() < 1000, "fuel burnt: " + d.fuel().amount());
                    ServerConfig.DIESEL_FAIL_TO_START_BASE.set(base);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void withoutAStationBatteryTheDieselCannotStart(GameTestHelper h) {
        place(h, false);
        h.startSequence()
                .thenWaitUntil(() -> {
                    dg(h);
                    load(h);
                })
                .thenExecute(() -> {
                    fuel(h);
                    dg(h).setAutoStart(true);
                })
                .thenIdle(100)
                .thenExecute(() -> {
                    GeneratorUnit g = dg(h);
                    h.assertTrue(g.diesel().status() == DieselEngine.Status.STANDBY, "no cranking: " + g.diesel().status());
                    h.assertTrue(g.diesel().cause() == DieselEngine.Cause.NO_DC, "cause: " + g.diesel().cause());
                    h.assertTrue(!g.breakerClosed() && !load(h).energized(), "bus stays dead");
                })
                .thenSucceed();
    }
}
