package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.chem.CrackedGasUnitBlockEntity;
import dev.denfry.promptcritical.mod.chem.LdpeReactorBlockEntity;
import dev.denfry.promptcritical.mod.chem.PyrolysisFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFeed;
import dev.denfry.promptcritical.sim.petrochem.PyrolysisFurnace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Химия I3: печь пиролиза с газоразделением вплотную делает из пропана этилен и пропилен и возвращает себе этан;
 * без газоразделения печь не работает; установка ПЭВД на валу Create полимеризует этилен, а при перегреве
 * (много кислорода, высокое давление) этилен разлагается — авария. Трубы заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PyrolysisGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos FURNACE = new BlockPos(3, 3, 5), SEPARATION = new BlockPos(6, 3, 5), LDPE = new BlockPos(6, 2, 6);

    private PyrolysisGameTests() {
    }

    private static void placeFurnace(GameTestHelper h, boolean withSeparation) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(FURNACE, PcBlocks.PYROLYSIS_FURNACE.get().defaultBlockState().setValue(facing, Direction.NORTH));
        if (withSeparation)
            h.setBlock(SEPARATION, PcBlocks.CRACKED_GAS_UNIT.get().defaultBlockState().setValue(facing, Direction.NORTH));
    }

    private static void feedPropane(PyrolysisFurnaceBlockEntity f) {
        IFluidHandler in = f.fluids(Direction.NORTH);
        in.fill(new FluidStack(PcFluids.PROPANE.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.BOILER_FEEDWATER.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        in.fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void start(PyrolysisFurnaceBlockEntity f) {
        f.furnace().setFeed(PyrolysisFeed.PROPANE);
        f.furnace().setCotK(1103.15);
        f.furnace().setMode(PyrolysisFurnace.Mode.RUN);
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void furnaceCracksPropaneIntoOlefins(GameTestHelper h) {
        placeFurnace(h, true);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> start(h.getBlockEntity(FURNACE)))
                .thenWaitUntil(() -> {
                    PyrolysisFurnaceBlockEntity f = h.getBlockEntity(FURNACE);
                    feedPropane(f);
                    CrackedGasUnitBlockEntity s = h.getBlockEntity(SEPARATION);
                    h.assertFalse(f.noSeparation(), "the separation unit touching the furnace is found");
                    h.assertTrue(f.furnace().feedKgS() > 0, "propane is cracking");
                    h.assertTrue(s.ethylene().amount() > 10 && s.propylene().amount() > 1,
                            "ethylene " + s.ethylene().amount() + " nm3, propylene " + s.propylene().amount() + " L");
                    h.assertTrue(s.lastResult() != null && s.lastResult().ethylenePurity() > 0.99,
                            "polymer-grade ethylene: " + (s.lastResult() == null ? 0 : s.lastResult().ethylenePurity()));
                    h.assertTrue(s.fluids(Direction.UP).drain(10, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.ETHYLENE.source.get(), "ethylene from the top");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void furnaceWithoutSeparationStaysCold(GameTestHelper h) {
        placeFurnace(h, false);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> start(h.getBlockEntity(FURNACE)))
                .thenExecute(() -> feedPropane(h.getBlockEntity(FURNACE)))
                .thenIdle(40)
                .thenExecute(() -> {
                    PyrolysisFurnaceBlockEntity f = h.getBlockEntity(FURNACE);
                    h.assertTrue(f.noSeparation(), "no separation unit next to the furnace");
                    h.assertTrue(f.furnace().feedKgS() == 0, "no feed without separation: " + f.furnace().feedKgS());
                })
                .thenSucceed();
    }

    private static void placeLdpe(GameTestHelper h) {
        h.setBlock(LDPE, PcBlocks.LDPE_REACTOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = LDPE.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void feedEthylene(LdpeReactorBlockEntity r) {
        r.fluids(Direction.NORTH).fill(new FluidStack(PcFluids.ETHYLENE.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void ldpePolymerisesEthylene(GameTestHelper h) {
        placeLdpe(h);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    LdpeReactorBlockEntity r = h.getBlockEntity(LDPE);
                    r.reactor().setPressureMpa(250);
                    r.reactor().setOxygenPpm(40);
                    r.reactor().setRunning(true);
                })
                .thenWaitUntil(() -> {
                    LdpeReactorBlockEntity r = h.getBlockEntity(LDPE);
                    feedEthylene(r);
                    h.assertFalse(r.reactor().decomposed(), "normal duty: no decomposition");
                    h.assertTrue(r.reactor().totalPolymerKg() > 1, "polyethylene made: " + r.reactor().totalPolymerKg() + " kg");
                    h.assertTrue(r.reactor().density() > 0.91 && r.reactor().density() < 0.94, "LDPE density " + r.reactor().density());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void ldpeOverheatDecomposesEthylene(GameTestHelper h) {
        placeLdpe(h);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    LdpeReactorBlockEntity r = h.getBlockEntity(LDPE);
                    r.reactor().setPressureMpa(300);
                    r.reactor().setOxygenPpm(150);
                    r.reactor().setRunning(true);
                })
                .thenWaitUntil(() -> {
                    if (h.getBlockEntity(LDPE) instanceof LdpeReactorBlockEntity r) {
                        feedEthylene(r);
                        h.assertTrue(r.reactor().decomposed(), "ethylene decomposes above the limit, peak "
                                + (r.reactor().peakK() - 273.15) + " C");
                    }
                })
                .thenSucceed();
    }
}
