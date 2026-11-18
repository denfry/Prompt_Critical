package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.sim.nonferrous.ReductionPot.State;
import dev.denfry.promptcritical.sim.nonferrous.ReductionPot;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.environment.EmissionLedger;
import dev.denfry.promptcritical.mod.nonferrous.ReductionPotBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.environment.AirQualityService;
import dev.denfry.promptcritical.mod.chlor.RectifierBlockEntity;
import dev.denfry.promptcritical.mod.block.PcProperties;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.AllBlocks;
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

    // --- Холл–Эру (08 §8.8.2) ---

    private static final BlockPos GEN = new BlockPos(5, 2, 2), RECT = new BlockPos(4, 2, 2), POT = new BlockPos(3, 2, 8);

    /** Ванна в работе: аноды, криолит, глинозём на уставке, рабочая температура. */
    private static ReductionPotBlockEntity runningPot(GameTestHelper h, BlockPos at, double aluminaKg) {
        ReductionPotBlockEntity p = h.getBlockEntity(at);
        ReductionPot pot = p.pot();
        pot.addAnodeKg(2000);
        pot.addCryoliteKg(ReductionPot.BATH_START_KG + 500);
        pot.addAlumina(aluminaKg);
        pot.forceState(State.NORMAL, ReductionPot.NOMINAL_K);
        return p;
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void reductionPotOnTheRectifierMakesAluminium(GameTestHelper h) {
        h.setBlock(GEN, PcBlocks.SHAFT_GENERATOR.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        h.setBlock(GEN.above(), PcBlocks.GENERATOR_EXCITER.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(RECT, PcBlocks.RECTIFIER_UNIT.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH));
        place(h, POT, PcBlocks.REDUCTION_POT.get());
        h.startSequence()
                .thenWaitUntil(() -> gen(h))
                .thenExecute(() -> {
                    GeneratorUnit g = gen(h);
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    ReductionPotBlockEntity p = runningPot(h, POT, 240);
                    p.pot().setCurrentSetA(50e3); // ≈ 2,5 В × 50 кА ≈ 0,13 МВт — по силам генератору
                    p.input().insertItem(0, new ItemStack(PcItems.ALUMINA.get(), 4), false);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95, "station runs"))
                .thenWaitUntil(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    ReductionPot pot = p.pot();
                    h.assertTrue(Math.abs(pot.lastAmps() - 50e3) < 1, "series current " + pot.lastAmps());
                    h.assertTrue(pot.state() == State.NORMAL, "pot " + pot.state());
                    h.assertTrue(pot.totalAlKg() > 0.5, "aluminium " + pot.totalAlKg() + " kg");
                    h.assertTrue(Math.abs(pot.lastVoltage() - ReductionPot.cellVoltage(50e3)) < 1e-6, "pot volts " + pot.lastVoltage());
                    RectifierBlockEntity r = h.getBlockEntity(RECT);
                    h.assertTrue(r.unit() != null && r.unit().powerW() > 100e3, "rectifier draws the pot from the grid");
                    h.assertTrue(h.getBlockState(POT).getValue(PcProperties.RUNNING), "pot shown running");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1800)
    public static void t820PotWithoutCurrentFreezesAndNeedsAReline(GameTestHelper h) {
        place(h, POT, PcBlocks.REDUCTION_POT.get());
        h.startSequence()
                .thenExecute(() -> runningPot(h, POT, 240))
                // freeze_grace_s = 60: минуту без тока ванна в норме
                .thenIdle(1180)
                .thenExecute(() -> {
                    ReductionPot pot = ((ReductionPotBlockEntity) h.getBlockEntity(POT)).pot();
                    h.assertTrue(pot.state() == State.NORMAL, "within the grace: " + pot.state());
                    h.assertTrue(pot.tempK() < ReductionPot.NOMINAL_K - 3, "but cooling: " + pot.tempK());
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    ReductionPot pot = ((ReductionPotBlockEntity) h.getBlockEntity(POT)).pot();
                    h.assertTrue(pot.state() == State.FREEZING, "after the grace: " + pot.state());
                    // дальше — как через 3–4 минуты: ванна у ликвидуса
                    pot.forceState(State.FREEZING, ReductionPot.LIQUIDUS_K + 0.3);
                })
                .thenWaitUntil(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    h.assertTrue(p.pot().state() == State.FROZEN, "frozen: " + p.pot().state());
                    h.assertTrue(!h.getBlockState(POT).getValue(PcProperties.RUNNING), "pot shown frozen");
                })
                .thenExecute(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    h.assertTrue(!p.reline(), "no reline without cathode blocks and bricks");
                    p.input().insertItem(0, new ItemStack(PcItems.CATHODE_BLOCK.get(), ReductionPotBlockEntity.RELINE_CATHODES), false);
                    p.input().insertItem(1, new ItemStack(PcItems.REFRACTORY_BRICK.get(), ReductionPotBlockEntity.RELINE_BRICKS), false);
                    h.assertTrue(p.reline(), "relined");
                    h.assertTrue(p.pot().state() == State.COLD && p.pot().metalKg() == 0, "a cold empty pot after the reline");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void starvedPotGoesIntoAnodeEffectAndLogsPfc(GameTestHelper h) {
        place(h, POT, PcBlocks.REDUCTION_POT.get());
        String id = "reduction_pot@" + h.absolutePos(POT).toShortString();
        h.startSequence()
                // Al₂O₃ на волосок выше порога 1,8 %, бункер пуст
                .thenExecute(() -> runningPot(h, POT, ReductionPot.ANODE_EFFECT_ALUMINA * ReductionPot.BATH_KG + 0.4))
                .thenWaitUntil(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    p.supply(150e3);
                    h.assertTrue(p.pot().state() == State.ANODE_EFFECT, "anode effect: " + p.pot().state() + " at "
                            + p.pot().aluminaFraction());
                })
                .thenWaitUntil(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    p.supply(150e3);
                    h.assertTrue(p.pot().lastVoltage() > 20, "anode effect volts " + p.pot().lastVoltage());
                    h.assertTrue(h.getBlockState(POT).getValue(PcProperties.ALARM), "pot shows the anode effect");
                    EmissionLedger.Source src = AirQualityService.ledger(h.getLevel()).sources().get(id);
                    h.assertTrue(src != null && src.periodKg(EmissionLedger.Pollutant.CF4) > 0
                            && src.periodKg(EmissionLedger.Pollutant.C2F6) > 0, "PFC in the emission ledger");
                })
                .thenExecute(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    p.input().insertItem(0, new ItemStack(PcItems.ALUMINA.get(), 2), false);
                })
                .thenWaitUntil(() -> {
                    ReductionPotBlockEntity p = h.getBlockEntity(POT);
                    p.supply(150e3);
                    h.assertTrue(p.pot().state() == State.NORMAL, "alumina feed kills the anode effect: " + p.pot().aluminaFraction());
                })
                .thenSucceed();
    }
}
