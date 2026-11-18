package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.gas.CentrifugalCompressorBlockEntity;
import dev.denfry.promptcritical.mod.gas.GasPlantBlockEntity;
import dev.denfry.promptcritical.mod.refinery.AmineUnitBlockEntity;
import dev.denfry.promptcritical.mod.refinery.ClausUnitBlockEntity;
import dev.denfry.promptcritical.mod.refinery.DelayedCokerBlockEntity;
import dev.denfry.promptcritical.mod.refinery.FccUnitBlockEntity;
import dev.denfry.promptcritical.mod.refinery.GasolineBlenderBlockEntity;
import dev.denfry.promptcritical.mod.refinery.QualityLabBlockEntity;
import dev.denfry.promptcritical.mod.refinery.SampleItem;
import dev.denfry.promptcritical.mod.refinery.RefineryComponents;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.refining.CatalystState;
import net.minecraft.world.item.ItemStack;
import dev.denfry.promptcritical.mod.refinery.DesalterBlockEntity;
import dev.denfry.promptcritical.mod.refinery.VacuumColumnBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Подготовка и углубление переработки (09 §9.7.3, §9.9–9.10): ЭЛОУ, вакуумная колонна, каткрекинг, амины, смешение
 * бензина, лаборатория. Трубы заменяет заливка в патрубки, сеть — стендовое питание.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConversionGameTests {
    static final String LARGE = "gametest/empty_12x12x12";

    private ConversionGameTests() {
    }

    static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    static void fill(IFluidHandler h, Fluid f, int mb) {
        h.fill(new FluidStack(f, mb), IFluidHandler.FluidAction.EXECUTE);
    }

    /** ЭЛОУ под напряжением: нефть в печи — меньше 5 мг/л соли, солёная вода — снизу. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void desalterFeedsFurnaceWithDesaltedCrude(GameTestHelper h) {
        BlockPos ds = new BlockPos(2, 2, 5), furnace = new BlockPos(8, 2, 5);
        h.setBlock(ds, north(PcBlocks.ELECTROSTATIC_DESALTER.get()));
        h.setBlock(furnace, north(PcBlocks.TUBE_FURNACE.get()));
        h.startSequence()
                .thenExecute(() -> ((DesalterBlockEntity) h.getBlockEntity(ds)).setBenchPower(true))
                .thenWaitUntil(() -> {
                    DesalterBlockEntity d = h.getBlockEntity(ds);
                    fill(d.fluids(Direction.NORTH), PcFluids.CRUDE_OIL.source.get(), 8000);
                    fill(d.fluids(Direction.UP), PcFluids.RAW_WATER.source.get(), 8000);
                    TubeFurnaceBlockEntity f = h.getBlockEntity(furnace);
                    h.assertTrue(f.crude().amount() > 200, "desalted crude in furnace " + f.crude().amount() + " L");
                    h.assertTrue(f.crudeSaltMgL() < 5, "salt " + f.crudeSaltMgL() + " mg/l");
                    h.assertTrue(d.brine().amount() > 10, "brine " + d.brine().amount() + " L");
                })
                .thenSucceed();
    }

    /** Без напряжения на электродах соль почти не уходит: в печь приходит больше 100 мг/л. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void unpoweredDesalterBarelyDesalts(GameTestHelper h) {
        BlockPos ds = new BlockPos(2, 2, 5), furnace = new BlockPos(8, 2, 5);
        h.setBlock(ds, north(PcBlocks.ELECTROSTATIC_DESALTER.get()));
        h.setBlock(furnace, north(PcBlocks.TUBE_FURNACE.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    DesalterBlockEntity d = h.getBlockEntity(ds);
                    fill(d.fluids(Direction.NORTH), PcFluids.CRUDE_OIL.source.get(), 8000);
                    fill(d.fluids(Direction.UP), PcFluids.RAW_WATER.source.get(), 8000);
                    TubeFurnaceBlockEntity f = h.getBlockEntity(furnace);
                    h.assertFalse(d.powered(), "no grid, no bench power");
                    h.assertTrue(f.crude().amount() > 200, "crude in furnace " + f.crude().amount() + " L");
                    h.assertTrue(f.crudeSaltMgL() > 100, "salt " + f.crudeSaltMgL() + " mg/l");
                })
                .thenSucceed();
    }

    /**
     * Вакуумная колонна с двумя эжекторами (5 кПа): газойль сверху, гудрон снизу, гудрон — в коксование, которое
     * берёт его первым.
     */
    @GameTest(template = LARGE, timeoutTicks = 800)
    public static void vacuumColumnSplitsMazutAndFeedsTheCoker(GameTestHelper h) {
        BlockPos vc = new BlockPos(5, 2, 5), coker = new BlockPos(5, 2, 9);
        h.setBlock(vc, north(PcBlocks.VACUUM_COLUMN.get()));
        h.setBlock(vc.east(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.setBlock(vc.west(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.setBlock(coker, north(PcBlocks.DELAYED_COKER.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    fill(v.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.BOILER_FEEDWATER.source.get(), 8000);
                    h.assertTrue(v.ejectors() == 2, "ejectors " + v.ejectors());
                    h.assertTrue(v.pressureKpa() == 5, "flash zone " + v.pressureKpa() + " kPa");
                    h.assertTrue(v.vgo().amount() > 300, "vacuum gas oil " + v.vgo().amount() + " L");
                    h.assertTrue(v.residue().amount() > 200, "tar " + v.residue().amount() + " L");
                    h.assertTrue(v.residueCcr() > 0.15, "tar CCR " + v.residueCcr());
                })
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    DelayedCokerBlockEntity c = h.getBlockEntity(coker);
                    int tar = (int) v.residue().amount();
                    fill(c.fluids(Direction.NORTH), PcFluids.VACUUM_RESIDUE.source.get(), tar);
                    fill(c.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 2000);
                    h.assertTrue(c.last().coke() > 0.3, "coke yield on tar " + c.last().coke());
                    h.assertTrue(c.feed().amount() >= 1999, "mazut waits while tar lasts: " + c.feed().amount());
                })
                .thenSucceed();
    }

    /** Без питательной воды эжекторы не работают: атмосферное давление, газойля в разы меньше. */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void vacuumColumnWithoutSteamRunsAtAtmosphere(GameTestHelper h) {
        BlockPos vc = new BlockPos(5, 2, 5);
        h.setBlock(vc, north(PcBlocks.VACUUM_COLUMN.get()));
        h.setBlock(vc.east(), north(PcBlocks.STEAM_EJECTOR.get()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    VacuumColumnBlockEntity v = h.getBlockEntity(vc);
                    fill(v.fluids(Direction.NORTH), PcFluids.FUEL_OIL.source.get(), 8000);
                    fill(v.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 8000);
                    h.assertTrue(v.residue().amount() > 200, "tar " + v.residue().amount() + " L");
                    h.assertTrue(v.pressureKpa() > 100, "no steam, no vacuum: " + v.pressureKpa());
                    h.assertTrue(v.result().vgo() < 0.3, "atmospheric gas oil yield " + v.result().vgo());
                })
                .thenSucceed();
    }

    /**
     * Каткрекинг с воздуходувкой — центробежным компрессором на валу Create: бензин, пропилен, регенератор горячий,
     * подпитка выводит равновесный катализатор с {@code catalyst_state}.
     */
    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void fccCracksGasOilWithAirFromTheCompressor(GameTestHelper h) {
        BlockPos fcc = new BlockPos(2, 2, 2), cc = new BlockPos(7, 2, 7), motor = cc.north();
        h.setBlock(fcc, north(PcBlocks.FCC_UNIT.get()));
        h.setBlock(cc, north(PcBlocks.CENTRIFUGAL_COMPRESSOR.get()));
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.SOUTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.startSequence()
                .thenExecute(() -> {
                    ((CentrifugalCompressorBlockEntity) h.getBlockEntity(cc)).setLine(0.85);
                    FccUnitBlockEntity f = h.getBlockEntity(fcc);
                    f.input().insertItem(0, new ItemStack(PcItems.CATALYST_FCC.get(), 16), false);
                })
                .thenWaitUntil(() -> {
                    FccUnitBlockEntity f = h.getBlockEntity(fcc);
                    fill(f.fluids(Direction.NORTH), PcFluids.VACUUM_GAS_OIL.source.get(), 8000);
                    h.assertTrue(f.blower() && f.airKgS() > 0.2, "air " + f.airKgS() + " kg/s");
                    // оператор держит воздух под кокс: ≈ 0,47 кг/с на 0,68 кг/с газойля (13 кг воздуха на кг кокса)
                    double share = f.airKgS() / (FccUnitBlockEntity.AIR_DESIGN_KGS * Math.max(0.05, f.damper()));
                    if (share > 0.1) f.setDamper(0.47 / (FccUnitBlockEntity.AIR_DESIGN_KGS * share));
                    h.assertTrue(f.gasoline().amount() > 200, "cracked gasoline " + f.gasoline().amount() + " L");
                    h.assertTrue(f.propylene().amount() > 10, "propylene " + f.propylene().amount() + " L");
                    h.assertTrue(f.last().yields().conversion() > 0.6, "conversion " + f.last().yields().conversion());
                    h.assertTrue(f.unit().regenK() - 273.15 > 600, "regenerator " + (f.unit().regenK() - 273.15) + " °C");
                    ItemStack ecat = f.output().getStackInSlot(0);
                    h.assertTrue(ecat.is(PcItems.CATALYST_FCC.get()), "withdrawn equilibrium catalyst");
                    CatalystState s = ecat.get(RefineryComponents.CATALYST_STATE.get());
                    h.assertTrue(s != null && s.ageH() >= 0, "catalyst_state on the withdrawn bag");
                })
                .thenExecute(() -> h.setBlock(cc, net.minecraft.world.level.block.Blocks.AIR))
                .thenWaitUntil(() -> {
                    FccUnitBlockEntity f = h.getBlockEntity(fcc);
                    h.assertTrue(f.unit().tripped(), "blower lost: slide valves shut, air " + f.airKgS());
                })
                .thenSucceed();
    }

    /** Без воздуходувки нет перепада на шиберной задвижке: установка не пускается, сырьё не идёт. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void fccWithoutAirTripsTheSlideValves(GameTestHelper h) {
        BlockPos fcc = new BlockPos(5, 2, 5);
        h.setBlock(fcc, north(PcBlocks.FCC_UNIT.get()));
        h.startSequence()
                .thenExecute(() -> {
                    FccUnitBlockEntity f = h.getBlockEntity(fcc);
                    f.input().insertItem(0, new ItemStack(PcItems.CATALYST_FCC.get(), 16), false);
                })
                .thenWaitUntil(() -> {
                    FccUnitBlockEntity f = h.getBlockEntity(fcc);
                    fill(f.fluids(Direction.NORTH), PcFluids.VACUUM_GAS_OIL.source.get(), 8000);
                    h.assertFalse(f.blower(), "no compressor in range");
                    h.assertTrue(f.unit().inventoryKg() > 0.5 * FccUnitBlockEntity.fullKg(), "charged " + f.unit().inventoryKg());
                    h.assertTrue(f.gasoline().amount() == 0, "no cracking without air");
                })
                .thenSucceed();
    }

    /**
     * Аминовая очистка при установке подготовки газа: весь газ установки очищается до нормы, сероводород уходит в
     * касающуюся установку Клауса, она делает серу.
     */
    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void amineUnitSweetensTheGasPlantFeedForClaus(GameTestHelper h) {
        BlockPos plant = new BlockPos(6, 3, 7), compressor = new BlockPos(2, 2, 2), regenerator = new BlockPos(10, 2, 2);
        BlockPos amine = new BlockPos(6, 2, 10), claus = amine.east();
        h.setBlock(plant, north(PcBlocks.LOW_TEMPERATURE_SEPARATOR.get()));
        h.setBlock(compressor, north(PcBlocks.GAS_COMPRESSOR.get()));
        h.setBlock(regenerator, north(PcBlocks.GLYCOL_REGENERATOR.get()));
        BlockPos motor = compressor.west();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
        h.setBlock(amine, north(PcBlocks.AMINE_UNIT.get()));
        h.setBlock(claus, north(PcBlocks.CLAUS_UNIT.get()));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> ((GasPlantBlockEntity) h.getBlockEntity(plant)).setInhibit(true))
                .thenWaitUntil(() -> {
                    GasPlantBlockEntity p = h.getBlockEntity(plant);
                    fill(p.fluids(Direction.NORTH), PcFluids.ASSOCIATED_GAS.source.get(), 4000);
                    fill(p.fluids(Direction.NORTH), PcFluids.METHANOL.source.get(), 4000);
                    AmineUnitBlockEntity a = h.getBlockEntity(amine);
                    fill(a.fluids(Direction.NORTH), PcFluids.SALES_GAS.source.get(), 4000);
                    h.assertTrue(a.gasNm3h() > 100, "gas treated " + a.gasNm3h() + " m3/h");
                    h.assertTrue(a.last().sweetH2sMgNm3() < 20, "H2S in treated gas " + a.last().sweetH2sMgNm3() + " mg/m3");
                    h.assertTrue(a.totalH2sKg() > 0.5, "acid gas " + a.totalH2sKg() + " kg");
                    ClausUnitBlockEntity c = h.getBlockEntity(claus);
                    h.assertTrue(c.totalSulfurKg() > 0.3, "Claus sulphur " + c.totalSulfurKg() + " kg");
                })
                .thenSucceed();
    }

    /**
     * Станция смешения по рецепту случая A §9.9.14 («Нормаль-80»): смесь в резервуаре, анализатор показывает её ОЧИ,
     * проба в лаборатории даёт паспорт партии; CC видит задачу ЛП через {@code pc_tank.getBlendData()}.
     */
    @GameTest(template = LARGE, timeoutTicks = 1600)
    public static void blendingStationLabAndCertificate(GameTestHelper h) {
        BlockPos bl = new BlockPos(5, 2, 5), lab = new BlockPos(8, 2, 5);
        h.setBlock(bl, north(PcBlocks.GASOLINE_BLENDER.get()));
        h.setBlock(bl.above(), north(PcBlocks.QUALITY_ANALYZER.get()));
        h.setBlock(lab, north(PcBlocks.QUALITY_LAB.get()));
        h.startSequence()
                .thenExecute(() -> {
                    GasolineBlenderBlockEntity b = h.getBlockEntity(bl);
                    double[] caseA = {2.093, 0.636, 5.000, 0};
                    for (int i = 0; i < caseA.length; i++) b.setValve(i, caseA[i]);
                    b.setGrade(0);
                    var profile = dev.denfry.promptcritical.mod.scada.device.DeviceProfiles.of(b);
                    h.assertTrue(profile != null && profile.type.equals("pc_tank") && profile.reads.containsKey("getBlendData")
                            && profile.commands.containsKey("setValves"), "pc_tank with getBlendData and setValves");
                    h.assertTrue(((java.util.List<?>) b.blendData().get("components")).size() == 4, "four components");
                })
                .thenWaitUntil(() -> {
                    GasolineBlenderBlockEntity b = h.getBlockEntity(bl);
                    fill(b.fluids(Direction.NORTH), PcFluids.GASOLINE.source.get(), 8000);
                    fill(b.fluids(Direction.NORTH), PcFluids.REFORMATE.source.get(), 8000);
                    fill(b.fluids(Direction.NORTH), PcFluids.FCC_GASOLINE.source.get(), 8000);
                    h.assertTrue(b.blend().amount() > 500, "blend " + b.blend().amount() + " L");
                    h.assertTrue(b.analyzer() && b.analyzerReading() != null, "analyzer reading");
                    double ron = b.blendProperties().ron();
                    h.assertTrue(Math.abs(ron - 85.7) < 1.5, "blend RON " + ron);
                })
                .thenExecute(() -> {
                    ItemStack bottle = new ItemStack(PcItems.SAMPLE_BOTTLE.get());
                    bottle.set(RefineryComponents.SAMPLE_DATA.get(), SampleItem.sample(h.getLevel(), h.absolutePos(bl), null));
                    QualityLabBlockEntity q = h.getBlockEntity(lab);
                    h.assertTrue(q.input().insertItem(0, bottle, false).isEmpty(), "sample accepted by the lab");
                })
                .thenWaitUntil(() -> {
                    GasolineBlenderBlockEntity b = h.getBlockEntity(bl);
                    QualityLabBlockEntity q = h.getBlockEntity(lab);
                    h.assertTrue(b.passport() != null, "passport written");
                    h.assertTrue(Math.abs(b.passport().ron() - b.blendProperties().ron()) < 2, "measured RON " + b.passport().ron());
                    boolean cert = false;
                    for (int i = 0; i < q.output().getSlots(); i++) cert |= q.output().getStackInSlot(i).is(PcItems.BATCH_CERTIFICATE.get());
                    h.assertTrue(cert, "batch certificate issued");
                })
                .thenSucceed();
    }

    /** Пробоотборник по баку Create с бензином каткрекинга: истинное качество — эталонное §9.9.14 (ОЧИ 91,5). */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void sampleBottleTakesAComponentFromAnyTank(GameTestHelper h) {
        BlockPos tank = new BlockPos(5, 2, 5);
        h.setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState());
        h.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    BlockPos at = h.absolutePos(tank);
                    h.assertTrue(SampleItem.sample(h.getLevel(), at, Direction.UP) == null, "empty tank: nothing to sample");
                    var cap = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, at, Direction.UP);
                    fill(cap, PcFluids.FCC_GASOLINE.source.get(), 1000);
                    RefineryComponents.SampleData d = SampleItem.sample(h.getLevel(), at, Direction.UP);
                    h.assertTrue(d != null && Math.abs(d.truth().ron() - 91.5) < 1e-9 && !d.analysed(), "cracked gasoline sample");
                    h.assertTrue(Math.abs(QualityLabBlockEntity.measure(d).ron() - 91.5) < 1.5, "lab within reproducibility");
                })
                .thenSucceed();
    }
}
