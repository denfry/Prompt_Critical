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
import dev.denfry.promptcritical.sim.protection.TripReason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * АЭС T2a (ВВЭР): свежая ТВС ВВЭР-440 из слота встаёт в заглушенный корпус и выгружается кнопкой, чужая конструкция не
 * принимается; компенсатор давления в радиусе связывается с реактором, без него реактор держится в АЗ; два ГЦН на валу
 * Create разгоняют роторы обеих петель; потеря привода ГЦН на мощности даёт АЗ по оборотам или расходу.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PwrReactorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6), PUMP_A = new BlockPos(1, 2, 1), PUMP_B = new BlockPos(3, 2, 1),
            PZR = new BlockPos(10, 2, 2);

    private PwrReactorGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static ItemStack assembly(FuelDesign d, int n) {
        return new FuelLot(d.uKgPerAssembly(0.96, 0.044), 0.044, 0.0003, 0.96, d.rods, d.name(), "")
                .on(new ItemStack(PcItems.FUEL_ASSEMBLY.get(), n));
    }

    private static void pump(GameTestHelper h, BlockPos pos) {
        place(h, pos, PcBlocks.PRIMARY_PUMP.get());
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void vverAssemblyLoadsAndUnloads(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertFalse(r.fuelSlots().isItemValid(PwrPanels.SLOT_FRESH, assembly(FuelDesign.AM_1, 1)), "AM-1 channel fuel refused");
                    r.fuelSlots().setStackInSlot(PwrPanels.SLOT_FRESH, assembly(FuelDesign.VVER_440, 2));
                })
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().loadedAssemblies() == 2, "two assemblies loaded: " + r.reactor().loadedAssemblies());
                    h.assertTrue(r.reactor().design() == FuelDesign.VVER_440, "design fixed");
                    h.assertFalse(r.fuelSlots().isItemValid(PwrPanels.SLOT_FRESH, assembly(FuelDesign.PWR_17X17, 1)), "one design per vessel");
                })
                .thenExecute(() -> ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), PwrPanels.B_UNLOAD))
                .thenWaitUntil(() -> {
                    PwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    ItemStack spent = r.fuelSlots().getStackInSlot(PwrPanels.SLOT_SPENT);
                    h.assertTrue(spent.is(PcItems.FUEL_ASSEMBLY.get()) && FuelLot.of(spent).designOrNull() == FuelDesign.VVER_440, "unloaded: " + spent);
                    h.assertTrue(r.reactor().loadedAssemblies() == 1, "one left");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void pressurizerLinksAndWithoutItTheReactorStaysTripped(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        h.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    PwrReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().protection().isTripped(), "no pressurizer — tripped");
                    h.assertFalse(r.reactor().resetTrip(), "reset refused without a pressurizer");
                    place(h, PZR, PcBlocks.PRESSURIZER.get());
                })
                .thenWaitUntil(() -> {
                    PressurizerBlockEntity k = h.getBlockEntity(PZR);
                    h.assertTrue(k.connected(), "pressurizer reported by the reactor");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void twoPumpsSpinBothLoops(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        pump(h, PUMP_A);
        pump(h, PUMP_B);
        h.startSequence()
                .thenWaitUntil(() -> {
                    PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    h.assertTrue(r.pumpSpeed(0) > 0.97 && r.pumpSpeed(1) > 0.97, "both rotors up: " + r.pumpSpeed(0) + " " + r.pumpSpeed(1));
                    h.assertTrue(r.flowFraction() > 0.97, "full flow " + r.flowFraction());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void losingPumpDriveAtPowerTrips(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.PWR_REACTOR.get());
        place(h, PZR, PcBlocks.PRESSURIZER.get());
        pump(h, PUMP_A);
        pump(h, PUMP_B);
        h.startSequence()
                .thenWaitUntil(() -> {
                    PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    h.assertTrue(r.pumpSpeed(0) > 0.99 && r.pumpSpeed(1) > 0.99, "pumps at speed");
                })
                .thenExecute(() -> {
                    PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    double kg = FuelDesign.VVER_440.uKgPerAssembly(0.96, 0.044);
                    r.setRods(1, 1);
                    for (int i = 0; i < PressurizedWaterReactor.POSITIONS; i++) r.load(i, FuelDesign.VVER_440, kg, 0.044, 0.0003);
                })
                // насосы на ходу, КД на месте: причин АЗ нет, выдержка сброса 10 с
                .thenIdle(240)
                .thenExecute(() -> {
                    PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    h.assertTrue(r.resetTrip(), "trip reset with pumps and pressurizer: " + r.protection().activeReasons());
                    // горячий старт на 50 %: бор на критику при РГ на 30 %
                    r.setThermalState(800, 588, 563);
                    r.setRods(0, 0.3);
                    double lo = 0, hi = 30;
                    for (int i = 0; i < 40; i++) {
                        double m = (lo + hi) / 2;
                        r.setBoronGkg(m);
                        if (r.evaluateReactivity() > 0) lo = m;
                        else hi = m;
                    }
                    r.equilibrate(0.5);
                })
                .thenIdle(20)
                .thenExecute(() -> h.destroyBlock(PUMP_A.south()))
                .thenWaitUntil(() -> {
                    PressurizedWaterReactor r = ((PwrReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    TripReason first = r.protection().firstOut();
                    h.assertTrue(r.protection().isTripped(), "tripped");
                    h.assertTrue(first == TripReason.LOSS_OF_DRIVE_POWER || first == TripReason.LOW_COOLANT_FLOW, "first-out " + first);
                })
                .thenSucceed();
    }
}
