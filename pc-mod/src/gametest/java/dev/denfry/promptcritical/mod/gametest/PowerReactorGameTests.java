package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.environment.RadiationService;
import dev.denfry.promptcritical.mod.npp.NppPanels;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
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
 * АЭС T1: свежая ТВС канала АМ-1 из слота встаёт в канал заглушенного реактора и выгружается кнопкой; два ГЦН на валу
 * Create дают номинальный расход; без расхода и с выведенными защитами кризис теплообмена разрушает каналы, и в зале
 * растёт мощность дозы.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerReactorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6), PUMP_A = new BlockPos(1, 2, 1), PUMP_B = new BlockPos(3, 2, 1);

    private PowerReactorGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static ItemStack am1(int n) {
        FuelDesign d = FuelDesign.AM_1;
        return new FuelLot(d.uKgPerAssembly(0.955, 0.05), 0.05, 0.0004, 0.955, d.rods, d.name(), "")
                .on(new ItemStack(PcItems.FUEL_ASSEMBLY.get(), n));
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void freshFuelLoadsAndUnloads(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.POWER_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR)).fuelSlots().setStackInSlot(NppPanels.SLOT_FRESH, am1(1)))
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().loadedChannels() == 1, "one channel loaded: " + r.reactor().loadedChannels());
                    h.assertTrue(r.fuelSlots().getStackInSlot(NppPanels.SLOT_FRESH).isEmpty(), "slot emptied");
                })
                .thenExecute(() -> ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR)).onPanelButton(h.makeMockServerPlayerInLevel(), NppPanels.B_UNLOAD))
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    ItemStack spent = r.fuelSlots().getStackInSlot(NppPanels.SLOT_SPENT);
                    h.assertTrue(spent.is(PcItems.FUEL_ASSEMBLY.get()) && FuelLot.of(spent).designOrNull() == FuelDesign.AM_1, "unloaded: " + spent);
                    h.assertTrue(r.reactor().loadedChannels() == 0, "channel empty");
                })
                .thenSucceed();
    }

    private static void pump(GameTestHelper h, BlockPos pos) {
        place(h, pos, PcBlocks.PRIMARY_PUMP.get());
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void twoPumpsGiveNominalFlow(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.POWER_REACTOR.get());
        pump(h, PUMP_A);
        pump(h, PUMP_B);
        h.startSequence()
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(Math.abs(r.reactor().flowCommand() - 1.0) < 0.02, "two pumps, full flow: " + r.reactor().flowCommand());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void dryoutWithoutProtectionBreaksChannelsAndDosesTheHall(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.POWER_REACTOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    ChannelReactor r = ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    r.setRods(1, 1, 1);
                    for (int i = 0; i < ChannelReactor.CHANNELS; i++) r.load(i, FuelDesign.AM_1.uKgPerAssembly(0.955, 0.05), 0.05, 0.0004);
                    // горячий старт на номинале: РР — на критику, защиты выведены, насосов нет
                    r.setTemperatures(700, 800, 508);
                    r.setRods(0, 0.5, 0.5);
                    r.equilibrate(1);
                    r.resetTrip();
                    r.setProtection(false);
                })
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.reactor().failedChannels() > 0, "dryout breaks channels");
                    double rate = RadiationService.rateSvH(h.getLevel(), h.absoluteVec(REACTOR.getCenter()).add(4, 0, 0));
                    h.assertTrue(rate > 1e-3, "dose rate in the hall " + rate + " Sv/h");
                })
                .thenSucceed();
    }
}
