package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.nonferrous.AluminaCalcinerBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.AluminaPrecipitatorBlockEntity;
import dev.denfry.promptcritical.mod.nonferrous.BayerDigesterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.nonferrous.BayerProcess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Цветная металлургия (08 §8.8): автоклав Байера растворяет боксит щёлочью только в окне температур и отдаёт раствор
 * сверху, шлам снизу; декомпозёр осаждает гидроксид и возвращает щёлочь; печь прокаливает гидроксид в глинозём только
 * с газом. Трубы заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NonferrousGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos A = new BlockPos(3, 3, 3), B = new BlockPos(7, 3, 7);

    private NonferrousGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static int fill(IFluidHandler h, Fluid f, int mb) {
        return h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void bayerDigesterLeachesBauxiteInsideItsWindow(GameTestHelper h) {
        place(h, A, PcBlocks.BAYER_DIGESTER.get());
        place(h, B, PcBlocks.BAYER_DIGESTER.get());
        h.startSequence()
                .thenExecute(() -> {
                    for (BlockPos p : new BlockPos[] {A, B}) {
                        BayerDigesterBlockEntity d = h.getBlockEntity(p);
                        d.input().insertItem(0, new ItemStack(PcItems.RAW_BAUXITE.get(), 16), false);
                        fill(d.fluids(Direction.NORTH), PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), 8000);
                        fill(d.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 4000);
                    }
                    // второй автоклав холодный: 100 °C не берёт ни гиббсит, ни бёмит
                    BayerDigesterBlockEntity cold = h.getBlockEntity(B);
                    cold.digester().setTempK(373.15);
                })
                .thenWaitUntil(() -> {
                    BayerDigesterBlockEntity d = h.getBlockEntity(A);
                    h.assertTrue(d.liquor().amount() > 20, "aluminate liquor " + d.liquor().amount() + " L");
                    h.assertTrue(d.mud().amount() > 1, "red mud " + d.mud().amount() + " L");
                    h.assertTrue(d.caustic().amount() < 8000 && d.fuel().amount() < 4000, "caustic and steam used");
                    h.assertTrue(d.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.SODIUM_ALUMINATE_LIQUOR.source.get(), "liquor from the top");
                    h.assertTrue(d.fluids(Direction.DOWN).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.RED_MUD.source.get(), "red mud from the bottom");
                    // боксит на тонну глинозёма — по балансу: 4–5 т на гиббситовом режиме
                    double t = d.digester().totalBauxiteKg() / d.digester().totalAl2o3Kg();
                    h.assertTrue(t > 4 && t < 5, "bauxite per alumina " + t);
                    BayerDigesterBlockEntity cold = h.getBlockEntity(B);
                    h.assertTrue(cold.liquor().amount() == 0 && cold.digester().totalBauxiteKg() == 0, "a cold digester dissolves nothing");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void precipitatorDropsHydrateAndReturnsCaustic(GameTestHelper h) {
        place(h, A, PcBlocks.ALUMINA_PRECIPITATOR.get());
        h.startSequence()
                .thenExecute(() -> {
                    AluminaPrecipitatorBlockEntity p = h.getBlockEntity(A);
                    int in = fill(p.fluids(Direction.NORTH), PcFluids.SODIUM_ALUMINATE_LIQUOR.source.get(), 20_000);
                    h.assertTrue(in == 20_000, "the decomposer holds 20 m3: " + in);
                })
                .thenWaitUntil(() -> {
                    AluminaPrecipitatorBlockEntity p = h.getBlockEntity(A);
                    h.assertTrue(p.precipitator().totalHydroxideKg() > 5, "hydrate " + p.precipitator().totalHydroxideKg() + " kg");
                    // щёлочь возвращается по массе NaOH: 200 г/л раствора в 11 %-ю щёлочь
                    double used = 20_000 - p.liquor().amount();
                    h.assertTrue(Math.abs(p.spent().amount() - used * AluminaPrecipitatorBlockEntity.causticPerLiquorL()) < 1e-3,
                            "spent caustic " + p.spent().amount() + " for " + used + " L of liquor");
                    double hydrate = p.precipitator().totalHydroxideKg() / used;
                    h.assertTrue(hydrate < BayerProcess.NET_AL2O3_KG_L * BayerProcess.HYDROXIDE_PER_AL2O3 && hydrate > 0.1,
                            "hydrate per litre " + hydrate);
                    h.assertTrue(p.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), "caustic back from the top");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void calcinerNeedsFuelToMakeAlumina(GameTestHelper h) {
        place(h, A, PcBlocks.ALUMINA_CALCINER.get());
        h.startSequence()
                .thenExecute(() -> {
                    AluminaCalcinerBlockEntity k = h.getBlockEntity(A);
                    k.input().insertItem(0, new ItemStack(PcItems.ALUMINIUM_HYDROXIDE.get(), 16), false);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    AluminaCalcinerBlockEntity k = h.getBlockEntity(A);
                    h.assertTrue(k.kiln().totalKg() == 0, "no gas, no calcination");
                    fill(k.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 4000);
                })
                .thenWaitUntil(() -> {
                    AluminaCalcinerBlockEntity k = h.getBlockEntity(A);
                    h.assertTrue(k.kiln().totalKg() > 5, "alumina " + k.kiln().totalKg() + " kg");
                    h.assertTrue(k.fuel().amount() < 4000, "gas burnt");
                })
                .thenSucceed();
    }
}
