package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity;
import dev.denfry.promptcritical.mod.gas.NglFractionatorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
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
 * Газопереработка I2: установка НТС с компрессором на валу Create и регенератором гликоля делает товарный газ и ШФЛУ,
 * без метанола при охлаждении ниже точки росы штуцер перекрывает гидратная пробка, ГФУ делит ШФЛУ на пропан, бутан и
 * газовый бензин. Трубы Create заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GasProcessingGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos PLANT = new BlockPos(6, 3, 7), COMPRESSOR = new BlockPos(2, 2, 2), REGENERATOR = new BlockPos(10, 2, 2);

    private GasProcessingGameTests() {
    }

    private static void buildPlant(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(PLANT, PcBlocks.LOW_TEMPERATURE_SEPARATOR.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(COMPRESSOR, PcBlocks.GAS_COMPRESSOR.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(REGENERATOR, PcBlocks.GLYCOL_REGENERATOR.get().defaultBlockState().setValue(facing, Direction.NORTH));
        BlockPos motor = COMPRESSOR.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void feed(GasPlantBlockEntity p, boolean methanol) {
        IFluidHandler in = p.fluids(Direction.NORTH);
        in.fill(new FluidStack(PcFluids.ASSOCIATED_GAS.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
        if (methanol) in.fill(new FluidStack(PcFluids.METHANOL.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void ltsMakesSalesGasAndNglWithMethanol(GameTestHelper h) {
        buildPlant(h);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(PLANT);
                    p.lts().setChillK(253.15);
                    p.setInhibit(true);
                })
                .thenWaitUntil(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(PLANT);
                    feed(p, true);
                    h.assertTrue(p.last() != null, "unit runs: compressor and regenerator found");
                    h.assertTrue(p.sales().amount() > 200 && p.ngl().amount() > 20, "sales gas " + p.sales().amount() + " nm3, NGL "
                            + p.ngl().amount() + " L");
                    h.assertTrue(p.last().ltsK() < 253.15, "LTS below the chiller: " + (p.last().ltsK() - 273.15) + " C");
                    h.assertTrue(p.lts().plug() == 0, "methanol keeps the choke clear, plug " + p.lts().plug());
                    h.assertTrue(p.last().methanolWtPct() > 0, "methanol dosed into the free water");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void uninhibitedColdChokePlugsWithHydrate(GameTestHelper h) {
        buildPlant(h);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(PLANT);
                    p.lts().setChillK(253.15);
                    p.setInhibit(false);
                    // пробка почти набрана: в тесте не ждать дни нарастания
                    p.lts().setPlug(0.9999);
                })
                .thenWaitUntil(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(PLANT);
                    feed(p, false);
                    h.assertTrue(p.last() != null && p.last().blocked(), "hydrate plug blocks the choke: plug " + p.lts().plug());
                })
                .thenExecute(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(PLANT);
                    double sales = p.sales().amount();
                    h.runAfterDelay(20, () -> h.assertTrue(p.sales().amount() <= sales + 1e-6, "no gas through a plugged choke"));
                })
                .thenIdle(25)
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void fractionatorSplitsNgl(GameTestHelper h) {
        BlockPos gfu = new BlockPos(6, 2, 6);
        h.setBlock(gfu, PcBlocks.NGL_FRACTIONATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    NglFractionatorBlockEntity f = h.getBlockEntity(gfu);
                    IFluidHandler in = f.fluids(Direction.NORTH);
                    in.fill(new FluidStack(PcFluids.NGL.source.get(), 4000), IFluidHandler.FluidAction.EXECUTE);
                    in.fill(new FluidStack(PcFluids.SALES_GAS.source.get(), 2000), IFluidHandler.FluidAction.EXECUTE);
                    h.assertTrue(f.propane().amount() > 50 && f.butane().amount() > 20 && f.gasoline().amount() > 10,
                            "propane " + f.propane().amount() + ", butane " + f.butane().amount() + ", gasoline " + f.gasoline().amount());
                    h.assertTrue(f.result().propanePurity() > 0.95, "commercial propane: " + f.result().propanePurity());
                    h.assertTrue(f.fluids(Direction.UP).drain(10, IFluidHandler.FluidAction.SIMULATE).getFluid() == PcFluids.PROPANE.source.get(),
                            "propane from the top");
                })
                .thenSucceed();
    }
}
