package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.nitrogen.AcidConcentratorBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.AmmoniaConverterBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.Co2AbsorberBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.NitricAcidBlockEntity;
import dev.denfry.promptcritical.mod.nitrogen.SteamReformerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Азотное производство: печь риформинга с колонной очистки вплотную делает азотоводородную смесь H₂:N₂ ≈ 3, без
 * никеля — ничего; колонна синтеза на валу Create набирает давление и выдаёт жидкий аммиак; агрегат Оствальда делает
 * 56 % кислоту, а богатая смесь вспыхивает и рвёт сетки; колонна с плавом нитрата магния даёт 98 % кислоту. Трубы
 * заменяет заливка в патрубки в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NitrogenGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos REFORMER = new BlockPos(4, 3, 5), ABSORBER = new BlockPos(6, 2, 5), UNIT = new BlockPos(6, 2, 5);

    private NitrogenGameTests() {
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    private static void fill(IFluidHandler in, net.minecraft.world.level.material.Fluid fluid, int mb) {
        in.fill(new FluidStack(fluid, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static void motorBehind(GameTestHelper h, BlockPos unit) {
        BlockPos motor = unit.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    private static void feedReformer(SteamReformerBlockEntity r) {
        fill(r.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 2000);
        fill(r.fluids(Direction.NORTH), PcFluids.BOILER_FEEDWATER.source.get(), 2000);
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void reformerMakesAmmoniaSyngas(GameTestHelper h) {
        place(h, REFORMER, PcBlocks.STEAM_REFORMER.get());
        place(h, ABSORBER, PcBlocks.CO2_ABSORBER.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    SteamReformerBlockEntity r = h.getBlockEntity(REFORMER);
                    r.train().loadPrimaryCatalyst();
                    r.train().loadHts();
                    r.train().loadLts();
                    r.setRunning(true);
                })
                .thenWaitUntil(() -> {
                    SteamReformerBlockEntity r = h.getBlockEntity(REFORMER);
                    feedReformer(r);
                    Co2AbsorberBlockEntity a = h.getBlockEntity(ABSORBER);
                    h.assertFalse(r.noAbsorber(), "the absorber touching the reformer is found");
                    h.assertTrue(r.ch4MolS() > 0, "methane is reforming");
                    h.assertTrue(a.lastResult() != null && a.lastResult().onSpec(), "syngas on spec");
                    double ratio = a.lastResult().ratio();
                    h.assertTrue(ratio > 2.9 && ratio < 3.15, "H2:N2 " + ratio);
                    h.assertTrue(a.syngas().amount() > 20, "syngas " + a.syngas().amount() + " nm3");
                    h.assertTrue(a.fluids(Direction.UP).drain(10, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.SYNGAS.source.get(), "syngas from the column");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void reformerWithoutNickelMakesNothing(GameTestHelper h) {
        place(h, REFORMER, PcBlocks.STEAM_REFORMER.get());
        place(h, ABSORBER, PcBlocks.CO2_ABSORBER.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    SteamReformerBlockEntity r = h.getBlockEntity(REFORMER);
                    r.train().loadHts();
                    r.train().loadLts();
                    r.setRunning(true);
                    feedReformer(r);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    SteamReformerBlockEntity r = h.getBlockEntity(REFORMER);
                    Co2AbsorberBlockEntity a = h.getBlockEntity(ABSORBER);
                    h.assertTrue(r.ch4MolS() == 0, "no reforming without nickel: " + r.ch4MolS());
                    h.assertTrue(a.syngas().amount() == 0, "no syngas: " + a.syngas().amount());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void synthesisLoopMakesLiquidAmmonia(GameTestHelper h) {
        place(h, UNIT, PcBlocks.AMMONIA_CONVERTER.get());
        motorBehind(h, UNIT);
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((AmmoniaConverterBlockEntity) h.getBlockEntity(UNIT)).loop().setRunning(true))
                .thenWaitUntil(() -> {
                    AmmoniaConverterBlockEntity c = h.getBlockEntity(UNIT);
                    fill(c.fluids(Direction.NORTH), PcFluids.SYNGAS.source.get(), 4000);
                    h.assertTrue(c.loop().pressureBar() > 60, "loop pressure " + c.loop().pressureBar() + " bar");
                    h.assertTrue(c.ammonia().amount() > 5, "liquid ammonia " + c.ammonia().amount() + " L");
                    h.assertTrue(c.fluids(Direction.NORTH).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.AMMONIA.source.get(), "ammonia from the port");
                })
                .thenSucceed();
    }

    private static void startNitric(GameTestHelper h, double ammoniaPercent) {
        place(h, UNIT, PcBlocks.NITRIC_ACID_UNIT.get());
        motorBehind(h, UNIT);
        h.runAfterDelay(5, () -> {
            NitricAcidBlockEntity n = h.getBlockEntity(UNIT);
            n.plant().loadGauze();
            n.plant().setAmmoniaPercent(ammoniaPercent);
            n.plant().setRunning(true);
        });
    }

    private static void feedNitric(NitricAcidBlockEntity n) {
        fill(n.fluids(Direction.NORTH), PcFluids.AMMONIA.source.get(), 2000);
        fill(n.fluids(Direction.NORTH), PcFluids.BOILER_FEEDWATER.source.get(), 4000);
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void ostwaldUnitMakesNitricAcid(GameTestHelper h) {
        startNitric(h, 10.5);
        h.startSequence()
                .thenIdle(10)
                .thenWaitUntil(() -> {
                    NitricAcidBlockEntity n = h.getBlockEntity(UNIT);
                    feedNitric(n);
                    h.assertFalse(n.flashed(), "normal mixture does not flash");
                    h.assertTrue(n.acid().amount() > 5, "nitric acid " + n.acid().amount() + " L");
                    h.assertTrue(Math.abs(n.plant().last().strength() - 0.58) < 0.005, "acid strength " + n.plant().last().strength());
                    h.assertTrue(n.plant().last().tailNoxPpm() < 3000, "tail NOx " + n.plant().last().tailNoxPpm());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void richAmmoniaMixtureFlashes(GameTestHelper h) {
        startNitric(h, 13.5);
        h.startSequence()
                .thenIdle(10)
                .thenWaitUntil(() -> {
                    if (h.getBlockEntity(UNIT) instanceof NitricAcidBlockEntity n) {
                        feedNitric(n);
                        h.assertTrue(n.flashed(), "the mixture above the lower explosive limit flashes");
                        h.assertFalse(n.plant().running(), "the unit trips");
                        h.assertTrue(n.plant().gauzePtG() == 0, "the gauze is torn");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void magnesiumNitrateColumnConcentratesAcid(GameTestHelper h) {
        place(h, UNIT, PcBlocks.ACID_CONCENTRATOR.get());
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((AcidConcentratorBlockEntity) h.getBlockEntity(UNIT)).setRunning(true))
                .thenWaitUntil(() -> {
                    AcidConcentratorBlockEntity c = h.getBlockEntity(UNIT);
                    fill(c.fluids(Direction.NORTH), PcFluids.NITRIC_ACID.source.get(), 4000);
                    fill(c.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 1000);
                    h.assertTrue(c.strong().amount() > 2, "98 % acid " + c.strong().amount() + " L");
                    h.assertTrue(c.fluids(Direction.NORTH).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid()
                            == PcFluids.CONCENTRATED_NITRIC_ACID.source.get(), "strong acid from the port");
                })
                .thenSucceed();
    }
}
