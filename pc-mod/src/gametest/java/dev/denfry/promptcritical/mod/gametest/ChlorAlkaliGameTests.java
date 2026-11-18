package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.chlor.BrineTreaterBlockEntity;
import dev.denfry.promptcritical.mod.chlor.CellRoomBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcCrackerBlockEntity;
import dev.denfry.promptcritical.mod.chlor.EdcUnitBlockEntity;
import dev.denfry.promptcritical.mod.chlor.LiquefierBlockEntity;
import dev.denfry.promptcritical.mod.chlor.PvcReactorBlockEntity;
import dev.denfry.promptcritical.mod.environment.EnvironmentService;
import dev.denfry.promptcritical.mod.environment.ToxicReleaseService;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.consequence.ToxicGas;
import dev.denfry.promptcritical.sim.chlor.PvcReactor;
import dev.denfry.promptcritical.sim.environment.Environment;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Хлор-щелочное производство и ПВХ: генератор 6,3 кВ кормит выпрямитель, серия ванн делает хлор, водород и щёлочь;
 * рассольное отделение очищает рассол только с реагентами; сжижение даёт жидкий хлор, а без щёлочи абгаз уходит
 * облаком, которое по ветру опасно, а против ветра — нет; ДХЭ, его пиролиз и автоклав ПВХ. Трубы заменяет заливка.
 *
 * <p>Раскладка электролиза (фасадом на юг у выпрямителя): генератор (5, 2, 2) с возбудителем и мотором; выпрямитель
 * (4, 2, 2) 2×2×1 — контроллер касается генератора восточной гранью; серия ванн (2, 2, 6).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChlorAlkaliGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(5, 2, 2), RECT = new BlockPos(4, 2, 2), CELLS = new BlockPos(2, 2, 6);

    private ChlorAlkaliGameTests() {
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void rectifierPowersCellsThatMakeChlorine(GameTestHelper h) {
        h.setBlock(GEN, facing(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(GEN.above(), facing(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState(), Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(RECT, facing(PcBlocks.RECTIFIER_UNIT.get().defaultBlockState(), Direction.SOUTH));
        h.setBlock(CELLS, facing(PcBlocks.CHLOR_ALKALI_CELL.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> gen(h))
                .thenExecute(() -> {
                    GeneratorUnit g = gen(h);
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    CellRoomBlockEntity c = h.getBlockEntity(CELLS);
                    c.cells().setCurrentKa(5); // 20 ванн × 5 кА × ≈ 2,7 В ≈ 0,27 МВт — по силам генератору
                    c.cells().setOn(true);
                })
                .thenWaitUntil(() -> h.assertTrue(gen(h).speedPu() > 0.95, "station runs"))
                .thenWaitUntil(() -> {
                    CellRoomBlockEntity c = h.getBlockEntity(CELLS);
                    fill(c.fluids(Direction.EAST), PcFluids.BRINE.source.get(), 4000);
                    h.assertTrue(c.cells().cl2KgS() > 0, "cells on load: " + c.cells().voltageV() + " V");
                    h.assertTrue(c.chlorine().amount() > 0.5 && c.hydrogen().amount() > 0.5 && c.liquor().amount() > 5,
                            "Cl2 " + c.chlorine().amount() + " nm3, H2 " + c.hydrogen().amount() + " nm3, liquor " + c.liquor().amount() + " L");
                    h.assertTrue(c.cells().voltageV() > 2.4 && c.cells().voltageV() < 3.2, "cell voltage at 0.33 kA/m2: " + c.cells().voltageV());
                    h.assertTrue(c.fluids(Direction.UP).drain(1, IFluidHandler.FluidAction.SIMULATE).getFluid() == PcFluids.CHLORINE.source.get(),
                            "chlorine from the top");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void cellsWithoutRectifierStayDead(GameTestHelper h) {
        h.setBlock(CELLS, facing(PcBlocks.CHLOR_ALKALI_CELL.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> {
                    CellRoomBlockEntity c = h.getBlockEntity(CELLS);
                    c.cells().setOn(true);
                    fill(c.fluids(Direction.EAST), PcFluids.BRINE.source.get(), 4000);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    CellRoomBlockEntity c = h.getBlockEntity(CELLS);
                    h.assertTrue(c.cells().cl2KgS() == 0 && c.chlorine().amount() == 0, "no DC, no chlorine");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void brineIsTreatedOnlyWithReagents(GameTestHelper h) {
        BlockPos treater = new BlockPos(6, 2, 6);
        h.setBlock(treater, facing(PcBlocks.BRINE_TREATER.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> {
                    BrineTreaterBlockEntity b = h.getBlockEntity(treater);
                    b.salt().insertItem(0, new ItemStack(PcItems.SALT.get(), 16), false);
                })
                // без щёлочи и дымовых газов — сырой рассол
                .thenWaitUntil(() -> {
                    BrineTreaterBlockEntity b = h.getBlockEntity(treater);
                    fill(b.fluids(Direction.NORTH), Fluids.WATER, 4000);
                    h.assertTrue(b.rawBrine().amount() > 20 && b.brine().amount() == 0, "raw brine " + b.rawBrine().amount());
                })
                .thenWaitUntil(() -> {
                    BrineTreaterBlockEntity b = h.getBlockEntity(treater);
                    IFluidHandler in = b.fluids(Direction.NORTH);
                    fill(in, Fluids.WATER, 4000);
                    fill(in, PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), 1000);
                    fill(in, PcFluids.FLUE_GAS.source.get(), 1000);
                    h.assertTrue(b.brine().amount() > 20, "treated brine " + b.brine().amount());
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void liquefierMakesLiquidChlorineAndVentsTailGasWithoutCaustic(GameTestHelper h) {
        BlockPos liq = new BlockPos(6, 2, 6);
        h.setBlock(liq, facing(PcBlocks.CHLORINE_LIQUEFIER.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> {
                    LiquefierBlockEntity l = h.getBlockEntity(liq);
                    l.unit().setOn(true);
                    l.unit().setCondK(253.15);
                })
                .thenWaitUntil(() -> {
                    LiquefierBlockEntity l = h.getBlockEntity(liq);
                    IFluidHandler in = l.fluids(Direction.NORTH);
                    fill(in, PcFluids.CHLORINE.source.get(), 1000);
                    fill(in, PcFluids.SODIUM_HYDROXIDE_SOLUTION.source.get(), 1000);
                    h.assertTrue(l.liquid().amount() > 1, "liquid chlorine " + l.liquid().amount() + " L");
                    h.assertTrue(l.unit().degree() > 0.98 && l.ventKgS() == 0, "tail gas absorbed: vent " + l.ventKgS());
                })
                .thenExecute(() -> {
                    LiquefierBlockEntity l = h.getBlockEntity(liq);
                    l.naoh().take(l.naoh().amount());
                })
                .thenWaitUntil(() -> {
                    LiquefierBlockEntity l = h.getBlockEntity(liq);
                    fill(l.fluids(Direction.NORTH), PcFluids.CHLORINE.source.get(), 1000);
                    h.assertTrue(l.ventKgS() > 0, "no caustic: tail gas vents");
                    h.assertTrue(ToxicReleaseService.ventKgS(h.getLevel(), h.absolutePos(liq).above(2), ToxicGas.CHLORINE) > 0, "release registered");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void chlorineCloudDriftsDownwind(GameTestHelper h) {
        BlockPos src = h.absolutePos(new BlockPos(6, 2, 6));
        h.startSequence()
                .thenExecute(() -> ToxicReleaseService.vent(h.getLevel(), src, ToxicGas.CHLORINE, 1.0))
                .thenExecute(() -> {
                    Environment.Sample e = EnvironmentService.sample(h.getLevel(), src, 10);
                    double ux = -Math.sin(e.windDirRad()), uz = Math.cos(e.windDirRad());
                    double down = ToxicReleaseService.ppmAt(h.getLevel(), ToxicGas.CHLORINE, src.getX() + 0.5 + ux * 50, src.getY() + 1, src.getZ() + 0.5 + uz * 50);
                    double up = ToxicReleaseService.ppmAt(h.getLevel(), ToxicGas.CHLORINE, src.getX() + 0.5 - ux * 50, src.getY() + 1, src.getZ() + 0.5 - uz * 50);
                    h.assertTrue(down > ToxicGas.CHLORINE.idlhPpm, "1 kg/s at 50 m downwind: " + down + " ppm");
                    h.assertTrue(up == 0, "nothing upwind: " + up);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 800)
    public static void edcCrackerAndPvcAutoclave(GameTestHelper h) {
        BlockPos edc = new BlockPos(2, 2, 2), cracker = new BlockPos(6, 2, 2), pvc = new BlockPos(9, 3, 8);
        h.setBlock(edc, facing(PcBlocks.EDC_UNIT.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(cracker, facing(PcBlocks.EDC_CRACKER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(pvc, facing(PcBlocks.PVC_REACTOR.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> ((EdcCrackerBlockEntity) h.getBlockEntity(cracker)).cracker().setOn(true))
                .thenWaitUntil(() -> {
                    EdcUnitBlockEntity u = h.getBlockEntity(edc);
                    IFluidHandler in = u.fluids(Direction.NORTH);
                    fill(in, PcFluids.ETHYLENE.source.get(), 500);
                    fill(in, PcFluids.CHLORINE.source.get(), 500);
                    h.assertTrue(u.edc().amount() > 5 && u.unit().edcDirectMolS() > 0, "EDC by direct chlorination " + u.edc().amount() + " L");
                })
                .thenWaitUntil(() -> {
                    EdcCrackerBlockEntity k = h.getBlockEntity(cracker);
                    IFluidHandler in = k.fluids(Direction.NORTH);
                    fill(in, PcFluids.ETHYLENE_DICHLORIDE.source.get(), 2000);
                    fill(in, PcFluids.SALES_GAS.source.get(), 500);
                    h.assertTrue(k.vcm().amount() > 5 && k.hcl().amount() > 1, "VCM " + k.vcm().amount() + " L, HCl " + k.hcl().amount() + " nm3");
                    h.assertTrue(k.cracker().conversion() > 0.5 && k.cracker().conversion() < 0.6, "conversion " + k.cracker().conversion());
                })
                .thenWaitUntil(() -> {
                    PvcReactorBlockEntity r = h.getBlockEntity(pvc);
                    IFluidHandler in = r.fluids(Direction.NORTH);
                    fill(in, PcFluids.VINYL_CHLORIDE.source.get(), 4000);
                    fill(in, Fluids.WATER, 4000);
                    h.assertTrue(r.reactor().phase() == PvcReactor.Phase.REACTING && r.reactor().conversion() > 0.01,
                            "batch polymerising: " + r.reactor().phase() + " " + r.reactor().conversion());
                    h.assertTrue(r.reactor().pressureMpa() > 0.6, "autoclave at VCM vapour pressure " + r.reactor().pressureMpa());
                })
                .thenSucceed();
    }
}
