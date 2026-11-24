package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.PressurizerBlockEntity;
import dev.denfry.promptcritical.mod.npp.PwrPanels;
import dev.denfry.promptcritical.mod.npp.PwrReactorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.npp.PressurizedWaterReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * ВВЭР-70 (В-2 Райнсберга) реального масштаба: корпус берёт конструкцию {@link PressurizedWaterReactor#VVER70} (265 МВт(т),
 * 132 кассеты ВВЭР-440, три петли), находит газовый КД и три ГЦН, а на горячем старте с номинала держит мощность и
 * давление 10 МПа азотной подушкой без ТЭНов. Полный баланс с ПГ и вторым контуром — {@code Vver70Test} в pc-sim.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class Vver70GameTests {
    private static final String TALL = "gametest/empty_16x24x16";
    private static final BlockPos REACTOR = new BlockPos(8, 1, 8), PZR = new BlockPos(13, 1, 2);
    private static final BlockPos[] PUMPS = {new BlockPos(1, 1, 1), new BlockPos(3, 1, 1), new BlockPos(5, 1, 1)};

    private Vver70GameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void pump(GameTestHelper h, BlockPos pos) {
        place(h, pos, PcBlocks.PRIMARY_PUMP.get());
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static PwrReactorBlockEntity pwr(GameTestHelper h) {
        return h.getBlockEntity(REACTOR);
    }

    private static ItemStack assembly(FuelDesign d) {
        return new FuelLot(d.uKgPerAssembly(0.96, 0.03), 0.03, 0.0003, 0.96, d.rods, d.name(), "").on(new ItemStack(PcItems.FUEL_ASSEMBLY.get()));
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void vver70VesselTakesTheRheinsbergDesignAndOnlyVver440Cassettes(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.VVER70_REACTOR_REAL.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    h.assertTrue(r.plant() == PressurizedWaterReactor.VVER70, "design " + r.plant().id());
                    h.assertTrue(r.plant().positions() == 132 && r.plant().loops() == 3, "132 cassettes, three loops");
                    h.assertTrue(r.pressurizer().gas(), "gas volume compensator");
                    h.assertTrue(pwr(h).wholeModelId() != null, "whole model");
                    h.assertTrue(pwr(h).fuelSlots().isItemValid(PwrPanels.SLOT_FRESH, assembly(FuelDesign.VVER_440)), "VVER-440 cassette taken");
                    h.assertFalse(pwr(h).fuelSlots().isItemValid(PwrPanels.SLOT_FRESH, assembly(FuelDesign.VVER_1000)), "VVER-1000 refused");
                })
                .thenSucceed();
    }

    @GameTest(template = TALL, timeoutTicks = 1200)
    public static void vver70ReachesRatedPowerAndTheGasCompensatorHoldsPressure(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.VVER70_REACTOR_REAL.get());
        place(h, PZR, PcBlocks.VVER70_PRESSURIZER_REAL.get());
        for (BlockPos p : PUMPS) pump(h, p);
        PressurizedWaterReactor.Design d = PressurizedWaterReactor.VVER70;
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.03);
                    r.setRods(1, 1);
                    for (int i = 0; i < d.positions(); i++) r.load(i, FuelDesign.VVER_440, kg, 0.03, 0.0003);
                })
                .thenWaitUntil(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    for (int l = 0; l < 3; l++) h.assertTrue(r.pumpSpeed(l) > 0.99, "pump " + l + " at speed: " + r.pumpSpeed(l));
                    PressurizerBlockEntity k = h.getBlockEntity(PZR);
                    h.assertTrue(k.connected(), "gas compensator linked");
                })
                .thenIdle(240)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    h.assertTrue(r.resetTrip(), "trip reset with three pumps and the compensator: " + r.protection().activeReasons());
                    double rodM = r.core().rodLengthM();
                    double fuel = (d.inletK() + d.outletK()) / 2 + d.nominalW() / (rodM * 1000 / PressurizedWaterReactor.FUEL_R_K_PER_KW_M);
                    r.setThermalState(fuel, d.outletK(), d.inletK());
                    r.setRods(0, 0.3);
                    double lo = 0, hi = 30;
                    for (int i = 0; i < 40; i++) {
                        double m = (lo + hi) / 2;
                        r.setBoronGkg(m);
                        if (r.evaluateReactivity() > 0) lo = m;
                        else hi = m;
                    }
                    r.equilibrate(1);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = pwr(h).reactor();
                    h.assertFalse(r.protection().isTripped(), "no trip: " + r.protection().firstOut());
                    h.assertTrue(Math.abs(r.power() - 1) < 0.1, "at 265 MWt: " + r.power() * 265 + " MW");
                    h.assertTrue(r.pressure() > 9.7e6 && r.pressure() < d.reliefPa(), "pressure held by gas: " + r.pressure() / 1e6 + " MPa");
                    h.assertTrue(r.heaterW() == 0 && !r.pressurizer().reliefOpen(), "no heaters, relief shut");
                    h.assertTrue(r.pressurizer().nitrogenKg() > 100, "nitrogen cushion " + r.pressurizer().nitrogenKg());
                })
                .thenSucceed();
    }

    /** Бассейн выдержки: отработавшая ТВС стоит под 4 м воды, её тепло уходит в воду; облучённую рукой не забрать. */
    @GameTest(template = TALL, timeoutTicks = 100)
    public static void vver70PoolHoldsSpentFuelUnderWater(GameTestHelper h) {
        BlockPos at = new BlockPos(8, 1, 8);
        place(h, at, PcBlocks.VVER70_FUEL_POOL_REAL.get());
        h.runAfterDelay(1, () -> {
            dev.denfry.promptcritical.mod.npp.SpentFuelPoolBlockEntity pool = h.getBlockEntity(at);
            ItemStack spent = assembly(FuelDesign.VVER_440);
            spent.set(dev.denfry.promptcritical.mod.environment.ActivityData.ACTIVITY.get(),
                    dev.denfry.promptcritical.mod.environment.SpentFuel.discharge(120, 30, 30, PwrReactorBlockEntity.ASSEMBLY_DENSITY,
                            h.getLevel().getGameTime()));
            h.assertTrue(pool.store(spent), "rack takes the assembly");
            h.assertTrue(pool.takeFresh().isEmpty(), "an irradiated assembly is not handed out");
        });
        h.runAfterDelay(45, () -> {
            dev.denfry.promptcritical.mod.npp.SpentFuelPoolBlockEntity pool = h.getBlockEntity(at);
            h.assertTrue(pool.assemblies() == 1 && pool.decayW() > 1e3, "decay heat " + pool.decayW() + " W");
            h.assertTrue(Math.abs(pool.pool().levelM() - 7.5) < 0.01 && pool.pool().shieldWaterCm() > 390, "four metres of water over the heads");
            h.succeed();
        });
    }

    /**
     * Перегрузочная машина над остановленным реактором и бассейном: ТВС из зоны уходит в стеллаж бассейна, машина
     * возвращается на стоянку. Реактор под мостом на 3 блока к северу, бассейн на 3 к югу.
     */
    @GameTest(template = TALL, timeoutTicks = 6000)
    public static void vver70RefuellingMachineMovesAnAssemblyToThePool(GameTestHelper h) {
        BlockPos reactor = new BlockPos(8, 1, 5), pool = new BlockPos(8, 9, 11), machine = new BlockPos(8, 20, 8);
        place(h, reactor, PcBlocks.VVER70_REACTOR_REAL.get());
        place(h, pool, PcBlocks.VVER70_FUEL_POOL_REAL.get());
        place(h, machine, PcBlocks.VVER70_REFUELLING_MACHINE_REAL.get());
        h.runAfterDelay(2, () -> {
            PwrReactorBlockEntity r = h.getBlockEntity(reactor);
            r.reactor().scram();
            h.assertTrue(r.loadAssembly(assembly(FuelDesign.VVER_440)), "assembly in the core");
            dev.denfry.promptcritical.mod.npp.RefuellingMachineBlockEntity m = h.getBlockEntity(machine);
            String refusal = m.start(true);
            h.assertTrue(refusal == null, "cycle started, refusal " + refusal);
        });
        h.runAfterDelay(4, () -> h.succeedWhen(() -> {
            dev.denfry.promptcritical.mod.npp.RefuellingMachineBlockEntity m = h.getBlockEntity(machine);
            dev.denfry.promptcritical.mod.npp.SpentFuelPoolBlockEntity p = h.getBlockEntity(pool);
            PwrReactorBlockEntity r = h.getBlockEntity(reactor);
            h.assertTrue(!m.busy(), "machine back in park, phase " + m.phase());
            h.assertTrue(r.reactor().loadedAssemblies() == 0, "core emptied");
            h.assertTrue(p.racks().getStackInSlot(0).is(PcItems.FUEL_ASSEMBLY.get()), "assembly in the pool rack");
        }));
    }
}
