package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.heavywater.HeavyWaterFluids;
import dev.denfry.promptcritical.mod.npp.NpdFuellingMachineBlockEntity;
import dev.denfry.promptcritical.mod.npp.NpdPanels;
import dev.denfry.promptcritical.mod.npp.NpdReactorBlockEntity;
import dev.denfry.promptcritical.mod.nuclear.FuelLot;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.fuel.FuelDesign;
import dev.denfry.promptcritical.sim.heavywater.HeavyWater;
import dev.denfry.promptcritical.sim.heavywater.HeavyWaterGrade;
import dev.denfry.promptcritical.sim.npp.NpdFuellingMachine;
import dev.denfry.promptcritical.sim.npp.NpdReactor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * CANDU NPD реального масштаба: реактор берёт только 19-элементные пучки NPD, сброс замедлителя глушит его с номинала,
 * машины у обоих торцов связываются с ним и по кнопке едут к самому выгоревшему каналу. Трубы к портам цельных
 * моделей пока не цепляются (исправляется отдельно), поэтому D₂O заливается прямо через обработчики.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpdGameTests {
    private static final String TALL = "gametest/empty_16x24x16";
    private static final BlockPos REACTOR = new BlockPos(8, 1, 8), FM_NORTH = new BlockPos(8, 1, 3), FM_SOUTH = new BlockPos(8, 1, 13);

    private NpdGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block, Direction facing) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing));
    }

    private static NpdReactorBlockEntity npd(GameTestHelper h) {
        return h.getBlockEntity(REACTOR);
    }

    private static ItemStack bundle(FuelDesign d) {
        return new FuelLot(d.uKgPerAssembly(0.96, 0.00711), 0.00711, 0.000054, 0.96, d.rods, d.name(), "")
                .on(new ItemStack(PcItems.FUEL_ASSEMBLY.get()));
    }

    private static void insertBundle(GameTestHelper h) {
        ItemStack left = npd(h).panelItems().insertItem(NpdPanels.SLOT_FRESH, bundle(FuelDesign.NPD_19), false);
        h.assertTrue(left.isEmpty(), "bundle into the fresh slot");
    }

    /** Полная зона природного урана, контур и каландр залиты, горячий старт на номинале. */
    private static void hot(NpdReactor r) {
        double kg = FuelDesign.NPD_19.uKgPerAssembly(0.96, 0.00711);
        for (int i = 0; i < NpdReactor.CHANNELS; i++) r.loadChannel(i, kg, 0.00711, 0.000054);
        r.addCoolant(r.coolantCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        r.addModerator(r.moderatorCapacityKg(), HeavyWater.REACTOR_GRADE_X);
        r.hotStart(1);
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void npdTakesOnlyNpdBundlesAndFillsTheDumpTankFromBelow(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.NPD_REACTOR_REAL.get(), Direction.NORTH);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    NpdReactorBlockEntity be = npd(h);
                    h.assertTrue(be.wholeModelId() != null, "whole model");
                    h.assertTrue(be.accepts(bundle(FuelDesign.NPD_19)), "NPD bundle taken");
                    h.assertFalse(be.accepts(bundle(FuelDesign.CANDU_37)), "CANDU-6 bundle refused");
                    IFluidHandler bottom = be.fluids(Direction.DOWN);
                    int in = bottom.fill(new FluidStack(HeavyWaterFluids.fluid(HeavyWaterGrade.REACTOR), 1000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(in > 0, "D2O into the dump tank");
                    h.assertTrue(be.reactor().dumpTank().kg() > 1 && be.reactor().moderator().kg() == 0,
                            "it lands in the dump tank, not the calandria: " + be.reactor().dumpTank().kg());
                    h.assertTrue(be.panelLayout() == NpdPanels.REACTOR, "NPD board");
                })
                .thenSucceed();
    }

    @GameTest(template = TALL, timeoutTicks = 600)
    public static void npdModeratorDumpShutsTheReactorDown(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.NPD_REACTOR_REAL.get(), Direction.NORTH);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    NpdReactor r = npd(h).reactor();
                    hot(r);
                    r.scram();
                })
                .thenIdle(200)
                .thenExecute(() -> {
                    NpdReactor r = npd(h).reactor();
                    h.assertTrue(r.dumping(), "dump valves open");
                    h.assertTrue(r.power() < 0.05, "power after the dump " + r.power());
                    h.assertTrue(r.dumpTank().kg() > 0.3 * r.moderatorCapacityKg(), "moderator in the dump tank " + r.dumpTank().kg());
                })
                .thenSucceed();
    }

    /**
     * Первый пуск: зона загружена, контур и бак сброса залиты через патрубки, «Взвод» закрывает сбросные клапаны,
     * ▲ гонит замедлитель из бака в каландр. Уровень проходит критический, мощность растёт от источника; насосы не
     * остановлены, период становится короче 10 с — защита сбрасывает замедлитель обратно в бак, и реактор глохнет.
     * Ручной ▲ идёт тонким клапаном (7 кг/с), поэтому каландр заранее налит до 99,5 % критического уровня: до
     * критического ~50 с, до периода короче 10 с (≈ +0,7 % уровня) ещё ~100 с.
     */
    @GameTest(template = TALL, timeoutTicks = 4000)
    public static void npdGoesCriticalOnModeratorLevelAndDumpsOnAShortPeriod(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.NPD_REACTOR_REAL.get(), Direction.NORTH);
        double[] at = new double[3]; // критический уровень, мощность на нём, мощность при сбросе
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    NpdReactorBlockEntity be = npd(h);
                    NpdReactor r = be.reactor();
                    double kg = FuelDesign.NPD_19.uKgPerAssembly(0.96, 0.00711);
                    for (int i = 0; i < NpdReactor.CHANNELS; i++) r.loadChannel(i, kg, 0.00711, 0.000054);
                    var d2o = HeavyWaterFluids.fluid(HeavyWaterGrade.REACTOR);
                    int coolantMb = (int) Math.ceil(HeavyWater.litres(r.coolantCapacityKg(), HeavyWaterGrade.REACTOR.x)) + 10;
                    be.fluids(Direction.EAST).fill(new FluidStack(d2o, coolantMb), IFluidHandler.FluidAction.EXECUTE);
                    int moderatorMb = (int) Math.ceil(HeavyWater.litres(r.moderatorCapacityKg(), HeavyWaterGrade.REACTOR.x));
                    be.fluids(Direction.DOWN).fill(new FluidStack(d2o, moderatorMb), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(r.coolantFill() > 0.999, "coolant circuit full: " + r.coolantFill());
                    h.assertTrue(r.moderator().kg() == 0 && r.dumpTank().kg() > 0.99 * r.moderatorCapacityKg(),
                            "all moderator in the dump tank: " + r.dumpTank().kg());
                    at[0] = r.criticalLevel();
                    h.assertTrue(at[0] > 0.2 && at[0] < 0.9, "fresh core critical part-full: " + at[0]);
                    double pre = 0.995 * at[0] * r.moderatorCapacityKg(), total = r.dumpTank().kg();
                    r.moderator().set(pre, r.dumpTank().purity());
                    r.dumpTank().set(total - pre, r.moderator().purity());
                    ServerPlayer op = FakePlayerFactory.getMinecraft((ServerLevel) h.getLevel());
                    h.assertTrue(r.dumping(), "a new reactor stands with the dump valves open");
                    be.onPanelButton(op, NpdPanels.B_RESET);
                    h.assertFalse(r.dumping(), "reset closes the dump valves");
                    be.onPanelButton(op, NpdPanels.B_UP);
                })
                .thenWaitUntil(() -> h.assertTrue(npd(h).reactor().moderatorLevel() >= at[0], "level below critical"))
                .thenExecute(() -> {
                    NpdReactor r = npd(h).reactor();
                    h.assertFalse(r.dumping(), "no trip below critical: " + r.trip());
                    at[1] = r.power();
                })
                .thenWaitUntil(() -> h.assertTrue(npd(h).reactor().dumping(), "no trip yet"))
                .thenExecute(() -> {
                    NpdReactor r = npd(h).reactor();
                    at[2] = r.power();
                    h.assertTrue(r.trip() == NpdReactor.Trip.PERIOD, "tripped on the period: " + r.trip());
                    h.assertTrue(at[2] > 10 * at[1], "power grew above critical: " + at[1] + " -> " + at[2]);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    NpdReactor r = npd(h).reactor();
                    h.assertTrue(r.moderatorLevel() < 0.5 * at[0], "moderator dumped: level " + r.moderatorLevel());
                    h.assertTrue(r.power() < 0.1 * at[2], "power falls after the dump: " + r.power());
                    h.assertTrue(r.dumpTank().kg() > 0.8 * r.moderatorCapacityKg(), "back in the dump tank: " + r.dumpTank().kg());
                })
                .thenSucceed();
    }

    @GameTest(template = TALL, timeoutTicks = 400)
    public static void npdFuellingMachinesAtBothFacesStartOnPowerFuelling(GameTestHelper h) {
        place(h, REACTOR, PcBlocks.NPD_REACTOR_REAL.get(), Direction.NORTH);
        place(h, FM_NORTH, PcBlocks.NPD_FUELLING_MACHINE_REAL.get(), Direction.NORTH);
        place(h, FM_SOUTH, PcBlocks.NPD_FUELLING_MACHINE_REAL.get(), Direction.SOUTH);
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    hot(npd(h).reactor());
                    for (BlockPos p : new BlockPos[] {FM_NORTH, FM_SOUTH}) {
                        NpdFuellingMachineBlockEntity m = h.getBlockEntity(p);
                        m.fluids(Direction.WEST).fill(new FluidStack(HeavyWaterFluids.fluid(HeavyWaterGrade.REACTOR), 4000),
                                IFluidHandler.FluidAction.EXECUTE);
                    }
                })
                // пучок не складывается в стопку: слот берёт по одному, лоток забирает его за тик
                .thenExecute(() -> insertBundle(h))
                .thenIdle(2)
                .thenExecute(() -> insertBundle(h))
                .thenIdle(2)
                .thenExecute(() -> insertBundle(h))
                .thenIdle(2)
                .thenExecute(() -> insertBundle(h))
                .thenIdle(40)
                .thenExecute(() -> {
                    NpdReactorBlockEntity be = npd(h);
                    h.assertTrue(be.linkedMachines() == 2, "two machines linked: " + be.linkedMachines());
                    h.assertTrue(be.machine().fresh() == 4, "magazine loaded from the bay: " + be.machine().fresh());
                    be.onPanelButton(FakePlayerFactory.getMinecraft((ServerLevel) h.getLevel()), NpdPanels.B_FUEL);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    NpdFuellingMachine m = npd(h).machine();
                    h.assertTrue(m.phase() == NpdFuellingMachine.Phase.TRAVEL, "machines travelling: " + m.phase());
                    h.assertTrue(m.toPush() == 4 && m.channel() >= 0, "four bundles for channel " + m.channel());
                    h.assertFalse(m.holdNoPower() || m.holdNoD2o(), "powered and supplied");
                })
                .thenSucceed();
    }
}
