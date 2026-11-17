package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ironworks.GasBuffer;
import dev.denfry.promptcritical.mod.powerhouse.GasTurbineBlockEntity;
import dev.denfry.promptcritical.mod.powerhouse.TurbineGearboxBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ГТУ на шахтном метане: пуск, разгон до холостого хода, вал через редуктор, расход газа. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasTurbineGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Контроллер — центр компрессора; фасад на север, редуктор перед ним. */
    private static final BlockPos GT = new BlockPos(5, 2, 2), GEARBOX = new BlockPos(5, 2, 0);

    private GasTurbineGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 3000)
    public static void gasTurbineStartsOnMineMethaneAndDrivesTheGearbox(GameTestHelper h) {
        h.setBlock(GT, PcBlocks.GAS_TURBINE.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(GEARBOX, PcBlocks.TURBINE_GEARBOX.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        int[] filled = {0};
        h.startSequence()
                .thenExecute(() -> {
                    GasTurbineBlockEntity g = h.getBlockEntity(GT);
                    filled[0] = g.fluids(null).fill(new FluidStack(GasBuffer.fluid("mine_methane"), 4000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(filled[0] > 0, "the turbine takes mine methane");
                    g.turbine().setRun(true);
                })
                .thenWaitUntil(() -> h.assertTrue(((GasTurbineBlockEntity) h.getBlockEntity(GT)).turbine().lit(), "light-off"))
                .thenWaitUntil(() -> {
                    GasTurbineBlockEntity g = h.getBlockEntity(GT);
                    h.assertTrue(g.turbine().speedPu() > 0.97, "runs up to no-load speed: " + g.turbine().speedPu());
                })
                .thenExecute(() -> {
                    h.assertTrue(((TurbineGearboxBlockEntity) h.getBlockEntity(GEARBOX)).outputRpm() > 0, "the gearbox turns the Create shaft");
                    GasTurbineBlockEntity g = h.getBlockEntity(GT);
                    h.assertTrue(g.fluids(null).getFluidInTank(0).getAmount() < filled[0], "gas is burnt");
                })
                .thenSucceed();
    }
}
