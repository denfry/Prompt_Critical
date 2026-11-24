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
                    ItemStack s = bundle(FuelDesign.NPD_19);
                    s.setCount(4);
                    npd(h).panelItems().insertItem(NpdPanels.SLOT_FRESH, s, false);
                })
                .thenIdle(45)
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
