package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.npp.CanduPanels;
import dev.denfry.promptcritical.mod.npp.CanduReactorBlockEntity;
import dev.denfry.promptcritical.mod.npp.NppPanels;
import dev.denfry.promptcritical.mod.npp.PowerReactorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.accident.PlantAccident;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.npp.CanduReactor;
import dev.denfry.promptcritical.sim.reactor.ChannelReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Слой аварий 04c на канальных реакторах (04c §4c.11.2): у АМ-1 и CANDU, как у ВВЭР, учебный разрыв со щита глушит
 * реактор, САОЗ на валу заливает зону, а потеря теплоносителя доходит до нейтроники — у АМ-1 осушение каналов
 * (паровой эффект воды отрицательный), у CANDU пар в каналах (пустотный эффект положительный).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChannelReactorAccidentGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REACTOR = new BlockPos(6, 3, 6), PUMP_A = new BlockPos(1, 2, 1), PUMP_B = new BlockPos(3, 2, 1),
            ECCS = new BlockPos(9, 2, 1), ACCUMULATOR = new BlockPos(10, 2, 10);

    private ChannelReactorAccidentGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void driven(GameTestHelper h, BlockPos pos, Block block) {
        place(h, pos, block);
        BlockPos motor = pos.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void plant(GameTestHelper h, Block reactor) {
        place(h, REACTOR, reactor);
        driven(h, PUMP_A, PcBlocks.PRIMARY_PUMP.get());
        driven(h, PUMP_B, PcBlocks.PRIMARY_PUMP.get());
        driven(h, ECCS, PcBlocks.ECCS_PUMP.get());
        h.setBlock(ACCUMULATOR, PcBlocks.ECCS_ACCUMULATOR.get().defaultBlockState());
    }

    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void am1SmallLocaScramsDriesTheChannelsAndIsHeldByTheEccs(GameTestHelper h) {
        plant(h, PcBlocks.POWER_REACTOR.get());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    ChannelReactor r = ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    r.setRods(1, 1, 1);
                    for (int i = 0; i < ChannelReactor.CHANNELS; i++) r.load(i, 4.3, 0.05, 0.0004);
                })
                .thenWaitUntil(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.accident().eccsTrains() == 1, "ECCS train linked: " + r.accident().eccsTrains());
                    h.assertTrue(r.accident().accident().acPower(), "AC from the turning shafts");
                })
                .thenExecute(() -> ((PowerReactorBlockEntity) h.getBlockEntity(REACTOR))
                        .onPanelButton(TestPlayers.of(h), NppPanels.B_LOCA))
                .thenIdle(400)
                .thenExecute(() -> {
                    PowerReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    PlantAccident a = r.accident().accident();
                    h.assertTrue(a.active() && a.breakArea() > 0, "LOCA under way");
                    h.assertTrue(r.reactor().trip() != ChannelReactor.Trip.NONE, "scrammed: " + r.reactor().trip());
                    h.assertTrue(a.eccsFlowKgS() > 0.5, "ECCS injecting " + a.eccsFlowKgS());
                    h.assertTrue(r.reactor().coolantLoss() > 0, "channels partly dry: " + r.reactor().coolantLoss());
                    h.assertFalse(a.coreUncovered(), "core covered, level " + a.levelFraction());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1400)
    public static void canduSmallLocaVoidsTheChannelsAndIsHeldByTheEccs(GameTestHelper h) {
        plant(h, PcBlocks.CANDU_REACTOR.get());
        double bundleKg = FuelDesign.CANDU_37.uKgPerAssembly(0.96, 0.00711);
        double[] density = {0};
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    CanduReactor r = ((CanduReactorBlockEntity) h.getBlockEntity(REACTOR)).reactor();
                    r.addModerator(r.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
                    r.addCoolant(r.coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
                    for (int i = 0; i < CanduReactor.CHANNELS; i++) r.loadChannel(i, bundleKg, 0.00711, 0.000054);
                    r.setRods(1, 1);
                })
                .thenWaitUntil(() -> {
                    CanduReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    h.assertTrue(r.accident().eccsTrains() == 1, "ECCS train linked: " + r.accident().eccsTrains());
                    h.assertTrue(r.accident().accumulators() == 1, "high-pressure ECC tank found");
                })
                .thenExecute(() -> {
                    CanduReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    density[0] = r.reactor().coolantDensity();
                    r.onPanelButton(TestPlayers.of(h), CanduPanels.B_LOCA);
                })
                .thenIdle(400)
                .thenExecute(() -> {
                    CanduReactorBlockEntity r = h.getBlockEntity(REACTOR);
                    PlantAccident a = r.accident().accident();
                    h.assertTrue(a.active() && a.breakArea() > 0, "LOCA under way");
                    h.assertTrue(r.reactor().trip() != CanduReactor.Trip.NONE, "scrammed: " + r.reactor().trip());
                    h.assertTrue(a.eccsFlowKgS() + a.accumulatorFlowKgS() > 0.5 || a.levelFraction() > 0.9,
                            "ECC injecting " + a.eccsFlowKgS() + " / level " + a.levelFraction());
                    h.assertFalse(a.coreUncovered(), "channels covered, level " + a.levelFraction());
                    h.assertTrue(a.levelFraction() >= 1 || r.reactor().coolantDensity() < density[0], "void in the channels: "
                            + density[0] + " -> " + r.reactor().coolantDensity() + " at level " + a.levelFraction());
                })
                .thenSucceed();
    }
}
