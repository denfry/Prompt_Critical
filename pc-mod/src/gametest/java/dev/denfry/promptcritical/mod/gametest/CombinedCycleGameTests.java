package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.HrsgBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.combustion.HeatRecoverySteamGenerator;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
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
 * ПГУ: котёл-утилизатор у турбинной секции ГТУ находит её и жаротрубный котёл рядом и отдаёт в его воду тепло
 * выхлопа по пинч-точке. Котёл держит 2 бар, чтобы пар шёл и от выхлопа холостого хода (≈ 175 °C).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CombinedCycleGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /**
     * ГТУ фасадом на север: x 4…6, y 1…3, z 0…8, турбина — z 6…8. Котёл-утилизатор за ней: газоход x 4…6, y 1…3, z 9…11,
     * барабан — y 4…6. Жаротрубный котёл к западу: x 1…3, y 1…3, z 6…11.
     */
    private static final BlockPos GT = new BlockPos(5, 2, 1), HRSG = new BlockPos(5, 2, 10), BOILER = new BlockPos(2, 2, 7);

    private CombinedCycleGameTests() {
    }

    private static void north(GameTestHelper h, BlockPos pos, net.minecraft.world.level.block.Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static SteamPlant plant(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b.world().subsystem(SteamPlant.class);
    }

    @GameTest(template = LARGE, timeoutTicks = 3000)
    public static void hrsgTurnsGasTurbineExhaustIntoBoilerSteam(GameTestHelper h) {
        north(h, GT, PcBlocks.GAS_TURBINE.get());
        north(h, HRSG, PcBlocks.HRSG.get());
        north(h, BOILER, PcBlocks.FIRE_TUBE_BOILER.get());
        h.startSequence()
                .thenWaitUntil(() -> {
                    FireTubeBoilerBlockEntity b = h.getBlockEntity(BOILER);
                    h.assertTrue(!b.simId().isNone(), "boiler twin attached");
                })
                .thenExecute(() -> {
                    FireTubeBoilerBlockEntity b = h.getBlockEntity(BOILER);
                    plant(h).presetWater(b.simId(), 2e5, 0.67);
                    GasTurbineBlockEntity g = h.getBlockEntity(GT);
                    int filled = g.fluids(null).fill(new FluidStack(GasBuffer.fluid("mine_methane"), 4000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(filled > 0, "the turbine takes mine methane");
                    g.turbine().setRun(true);
                })
                .thenWaitUntil(() -> {
                    HrsgBlockEntity r = h.getBlockEntity(HRSG);
                    GasTurbineBlockEntity g = h.getBlockEntity(GT);
                    h.assertTrue(r.linkedTurbine(), "the duct touches the turbine section");
                    h.assertTrue(r.linkedBoiler(), "the heat recovery boiler touches the fire-tube boiler");
                    h.assertTrue(g.turbine().lit(), "gas turbine lit");
                    h.assertTrue(r.gasKgS() > 0, "exhaust flows through the duct");
                    h.assertTrue(r.deliveredW() > 1e4, "steam heat into the boiler: " + r.deliveredW() + " W");
                    HeatRecoverySteamGenerator.Result res = r.result();
                    h.assertTrue(res.stackK() < r.gasInK(), "gas cools in the duct");
                    h.assertTrue(res.pinchGasK() - res.saturationK() >= HeatRecoverySteamGenerator.PINCH_K - 1e-6,
                            "gas stays above boiling water by the pinch");
                })
                .thenSucceed();
    }
}
