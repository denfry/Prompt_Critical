package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.drilling.DrilledWells;
import dev.denfry.promptcritical.mod.production.CrudeColumnBlockEntity;
import dev.denfry.promptcritical.mod.production.SeparatorBlockEntity;
import dev.denfry.promptcritical.mod.production.TubeFurnaceBlockEntity;
import dev.denfry.promptcritical.mod.production.ProductionPanels;
import dev.denfry.promptcritical.mod.production.PumpjackBlockEntity;
import dev.denfry.promptcritical.mod.production.WellheadBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.drilling.FormationFluid;
import dev.denfry.promptcritical.sim.drilling.Prospect;
import dev.denfry.promptcritical.sim.drilling.Trap;
import dev.denfry.promptcritical.sim.production.ProductionWell;
import dev.denfry.promptcritical.sim.refining.AtmosphericUnit.Product;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Добыча и прямая перегонка I2: пробуренный ствол заканчивают обсадкой и перфорацией, фонтанная скважина отдаёт
 * продукцию в трёхфазный сепаратор, нефть и попутный газ идут в трубчатую печь, колонна выдаёт бензин, керосин,
 * дизельное топливо и мазут. Трубу Create заменяет перелив между баками в самом тесте.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ProductionGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos WELL = new BlockPos(2, 2, 2), SEPARATOR = new BlockPos(6, 2, 2), FURNACE = new BlockPos(6, 3, 8),
            COLUMN = new BlockPos(2, 2, 9);

    private ProductionGameTests() {
    }

    private static Trap oilTrap(GameTestHelper h, double gradientPaM) {
        BlockPos abs = h.absolutePos(WELL);
        return new Trap(abs.getX(), abs.getZ(), 100, 900, 80, 960, 20, 0.22, 300, FormationFluid.OIL, gradientPaM);
    }

    /** Ствол в журнал, устье с арматурой, сепаратор, факел; обсадка и перфорация рукой. */
    private static void completeWell(GameTestHelper h, Trap trap, FakePlayer p) {
        BlockPos abs = h.absolutePos(WELL);
        DrilledWells.of(h.getLevel()).put(abs.getX(), abs.getZ(), new DrilledWells.Bore(930, new Prospect(trap, 900)));
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(WELL, PcBlocks.WELLHEAD.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(WELL.above(), PcBlocks.CHRISTMAS_TREE.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(SEPARATOR, PcBlocks.THREE_PHASE_SEPARATOR.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(new BlockPos(10, 2, 2), PcBlocks.FLARE_TIP.get());
        WellheadBlockEntity w = h.getBlockEntity(WELL);
        ItemStack casing = new ItemStack(PcItems.SEAMLESS_PIPE.get(), 64);
        w.useItem(p, InteractionHand.MAIN_HAND, casing);
        h.assertFalse(w.cased(), "64 joints are not enough for 930 m");
        w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.SEAMLESS_PIPE.get(), 64));
        h.assertTrue(w.cased(), "cased to " + w.jointsNeeded() + " joints");
        w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.SEISMIC_CHARGE.get(), 64));
        h.assertTrue(w.well() != null, "perforated");
    }

    private static void pour(IFluidHandler from, IFluidHandler to, int mb) {
        FluidStack got = from.drain(mb, IFluidHandler.FluidAction.SIMULATE);
        if (got.isEmpty()) return;
        int took = to.fill(got, IFluidHandler.FluidAction.EXECUTE);
        if (took > 0) from.drain(took, IFluidHandler.FluidAction.EXECUTE);
    }

    @GameTest(template = LARGE, timeoutTicks = 3000)
    public static void wellSeparatorAndColumnMakeProducts(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_producer"));
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(FURNACE, PcBlocks.TUBE_FURNACE.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        for (int i = 0; i < 3; i++)
            h.setBlock(COLUMN.above(2 + 2 * i), PcBlocks.COLUMN_TRAY_SECTION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(COLUMN.above(8), PcBlocks.COLUMN_TOP.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> completeWell(h, oilTrap(h, 12_500), p))
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.FLOWING, "flowing: " + w.well().mode() + ", open " + w.open());
                    h.assertTrue(h.getBlockState(WELL.above()).getValue(BlockStateProperties.OPEN), "tree open");
                    SeparatorBlockEntity s = h.getBlockEntity(SEPARATOR);
                    h.assertTrue(s.oil().amount() > 2000 && s.gas().amount() > 50, "separator collects oil " + s.oil().amount()
                            + " L, gas " + s.gas().amount() + " nm3");
                })
                .thenExecute(() -> ((CrudeColumnBlockEntity) h.getBlockEntity(COLUMN)).setOperation(1.5, 360))
                .thenWaitUntil(() -> {
                    SeparatorBlockEntity s = h.getBlockEntity(SEPARATOR);
                    TubeFurnaceBlockEntity f = h.getBlockEntity(FURNACE);
                    pour(s.fluids(Direction.NORTH), f.fluids(Direction.UP), 2000);
                    pour(s.fluids(Direction.UP), f.fluids(Direction.UP), 500);
                    CrudeColumnBlockEntity c = h.getBlockEntity(COLUMN);
                    h.assertTrue(c.ready(), "column with 3 sections and a furnace, sections " + c.sections());
                    for (Product pr : Product.values())
                        h.assertTrue(c.tank(pr).amount() > 100, pr + " drawn: " + c.tank(pr).amount() + " L");
                })
                .thenExecute(() -> {
                    CrudeColumnBlockEntity c = h.getBlockEntity(COLUMN);
                    // массовое отношение бензина к мазуту — как у колонны (по ИТК 25 к 46,5)
                    double gas = c.tank(Product.GASOLINE).amount() * Product.GASOLINE.densityKgM3;
                    double res = c.tank(Product.RESIDUE).amount() * Product.RESIDUE.densityKgM3;
                    double expected = c.result().yield(Product.GASOLINE) / c.result().yield(Product.RESIDUE);
                    h.assertTrue(Math.abs(gas / res - expected) < 0.05 * expected, "gasoline/residue " + gas / res + " vs " + expected);
                    h.assertTrue(c.drawAt(h.absolutePos(COLUMN.above(8))) == c.tank(Product.GASOLINE), "gasoline from the top");
                    h.assertTrue(c.drawAt(h.absolutePos(COLUMN)) == c.tank(Product.RESIDUE), "mazut from the bottom");
                })
                .thenSucceed();
    }

    /** Пласт с пониженным давлением не фонтанирует; станок-качалка на валу Create откачивает и пишет динамограмму. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void deadWellRunsOnAPumpjack(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_pumper"));
        BlockPos jack = new BlockPos(5, 2, 5);
        h.startSequence()
                .thenExecute(() -> completeWell(h, oilTrap(h, 8_000), p))
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.DEAD, "subnormal pressure does not flow: " + w.well().mode());
                })
                .thenExecute(() -> {
                    h.setBlock(jack, PcBlocks.PUMPJACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
                    BlockPos motor = new BlockPos(3, 2, 5);
                    h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
                    h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
                })
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.PUMPING, "rod pumping: " + w.well().mode());
                    h.assertTrue(w.well().oilM3s() > 0, "pump lifts oil");
                    h.assertTrue(w.well().card() != null && w.well().card().peakN() > w.well().fluidLoadN(), "dynamometer card written");
                })
                .thenSucceed();
    }

    /** Качалка на валу Create на устье с пониженным давлением: оснащение для испытаний ШГН. */
    private static void pumpjackOnDeadWell(GameTestHelper h) {
        h.setBlock(new BlockPos(5, 2, 5), PcBlocks.PUMPJACK.get().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos motor = new BlockPos(3, 2, 5);
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    /**
     * 09 §9.6.5: динамограмма из 200 точек, уравновешенная качалка держит момент в паспорте редуктора API 228, эхолот
     * находит уровень над насосом; лишние противовесы ломают зубья, большая шестерня их чинит.
     */
    @GameTest(template = LARGE, timeoutTicks = 900)
    public static void rodPumpCardCounterweightsAndEcho(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_pumper"));
        h.startSequence()
                .thenExecute(() -> {
                    completeWell(h, oilTrap(h, 8_000), p);
                    pumpjackOnDeadWell(h);
                })
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.PUMPING && w.well().card() != null, "rod pumping: " + w.well().mode());
                })
                .thenExecute(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    ProductionWell well = w.well();
                    h.assertTrue(well.card().positionM().length == 200, "200-point card");
                    h.assertTrue(Math.abs(well.counterbalanceN() - well.idealCounterbalanceN()) < 1, "balanced by default");
                    h.assertTrue(well.peakTorqueNm() > 0 && well.peakTorqueNm() < ProductionWell.GEARBOX_RATED_NM,
                            "balanced torque " + well.peakTorqueNm());
                    h.assertTrue(well.goodmanRatio() > 0 && well.goodmanRatio() < 1, "rods within Goodman: " + well.goodmanRatio());
                    h.assertTrue(w.echo(), "echo shot");
                    h.assertTrue(w.echoLevelM() > 0 && w.echoLevelM() <= well.pumpDepthM(), "fluid level " + w.echoLevelM());
                    h.assertTrue(Math.abs(w.echoTimeS() - 2 * w.echoLevelM() / well.annulusSoundSpeedMs()) < 1e-6, "z = c t / 2");
                    // перегруз противовесами кнопками щита качалки: на ходе вниз редуктор поднимает лишние 60 кН
                    PumpjackBlockEntity jack = h.getBlockEntity(new BlockPos(5, 2, 5));
                    for (int i = 0; i < 30; i++) jack.onPanelButton(p, ProductionPanels.WB_CB_UP);
                    h.assertTrue(well.counterbalanceN() > well.idealCounterbalanceN() + 50e3, "weights added: " + well.counterbalanceN());
                })
                .thenWaitUntil(() -> {
                    ProductionWell well = ((WellheadBlockEntity) h.getBlockEntity(WELL)).well();
                    h.assertTrue(well.gearboxBroken(), "unbalanced unit breaks the gearbox: torque " + well.peakTorqueNm());
                    h.assertTrue(well.mode() == ProductionWell.Mode.DEAD, "stopped: " + well.mode());
                })
                .thenExecute(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    w.well().setCounterbalanceN(w.well().idealCounterbalanceN());
                    w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(AllBlocks.LARGE_COGWHEEL.get()));
                    h.assertFalse(w.well().gearboxBroken(), "gearbox repaired");
                })
                .thenWaitUntil(() -> h.assertTrue(((WellheadBlockEntity) h.getBlockEntity(WELL)).well().mode() == ProductionWell.Mode.PUMPING,
                        "pumping again"))
                .thenSucceed();
    }

    /**
     * 09 §9.6.4: заглохшую скважину запускает газлифт — клапан на 600 м с давлением открытия 6 МПа, газ из
     * сепаратора; газ закачки возвращается с продукцией.
     */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void gasLiftKicksOffADeadWell(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_lifter"));
        h.startSequence()
                .thenExecute(() -> completeWell(h, oilTrap(h, 8_000), p))
                .thenWaitUntil(() -> h.assertTrue(((WellheadBlockEntity) h.getBlockEntity(WELL)).well().mode() == ProductionWell.Mode.DEAD,
                        "dead well"))
                .thenExecute(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    ItemStack valve = new ItemStack(PcItems.GAS_LIFT_VALVE.get());
                    valve.set(dev.denfry.promptcritical.mod.registry.PcDataComponents.GAS_LIFT_VALVE.get(),
                            new dev.denfry.promptcritical.mod.production.GasLiftValveItem.Data(600, 6000));
                    w.useItem(p, InteractionHand.MAIN_HAND, valve);
                    h.assertTrue(w.gasLiftValves().size() == 1, "valve in the tubing");
                    SeparatorBlockEntity s = h.getBlockEntity(SEPARATOR);
                    s.gas().add(4000);
                    for (int i = 0; i < 6; i++) w.onPanelButton(p, ProductionPanels.WB_GL_UP);
                    h.assertTrue(Math.abs(w.gasLiftSetStdM3s() - 0.3) < 1e-9, "0.3 nm3/s set");
                })
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.GAS_LIFT, "gas lift: " + w.well().mode());
                    h.assertTrue(w.well().oilM3s() > 0, "lifts oil");
                    h.assertTrue(w.well().gasLiftPoint().injDepthM() == 600, "through the 600 m valve");
                    h.assertTrue(w.well().gasStdM3s() > w.well().gasInjectionStdM3s(), "injected gas returns with production");
                })
                .thenSucceed();
    }

    /**
     * 09 §9.4.6: две скважины на одной ловушке тянут из одного «бака»; вторую переводят под закачку воды — давление
     * залежи растёт для обеих, закачка видна в компенсации отбора.
     */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void waterInjectionLiftsTheSharedReservoir(GameTestHelper h) {
        FakePlayer p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "pc_injector"));
        BlockPos inj = new BlockPos(2, 2, 7);
        double[] before = new double[1];
        h.startSequence()
                .thenExecute(() -> {
                    Trap trap = oilTrap(h, 8_000);
                    completeWell(h, trap, p);
                    BlockPos abs = h.absolutePos(inj);
                    DrilledWells.of(h.getLevel()).put(abs.getX(), abs.getZ(), new DrilledWells.Bore(930, new Prospect(trap, 900)));
                    var facing = BlockStateProperties.HORIZONTAL_FACING;
                    h.setBlock(inj, PcBlocks.WELLHEAD.get().defaultBlockState().setValue(facing, Direction.NORTH));
                    h.setBlock(inj.above(), PcBlocks.CHRISTMAS_TREE.get().defaultBlockState().setValue(facing, Direction.NORTH));
                    WellheadBlockEntity w = h.getBlockEntity(inj);
                    w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.SEAMLESS_PIPE.get(), 64));
                    w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.SEAMLESS_PIPE.get(), 64));
                    w.useItem(p, InteractionHand.MAIN_HAND, new ItemStack(PcItems.SEISMIC_CHARGE.get(), 64));
                    h.assertTrue(w.well() != null, "injector perforated");
                    WellheadBlockEntity prod = h.getBlockEntity(WELL);
                    h.assertTrue(prod.well().reservoir().state() == w.well().reservoir().state(), "one tank for the trap");
                    w.onPanelButton(p, ProductionPanels.WB_INJECT);
                    h.assertTrue(w.injector(), "injector mode");
                    w.fluids(Direction.UP).fill(new FluidStack(PcFluids.INJECTION_WATER.source.get(), WellheadBlockEntity.WATER_MB),
                            IFluidHandler.FluidAction.EXECUTE);
                    before[0] = prod.well().reservoir().pressurePa();
                })
                .thenWaitUntil(() -> {
                    WellheadBlockEntity w = h.getBlockEntity(inj), prod = h.getBlockEntity(WELL);
                    h.assertTrue(w.well().mode() == ProductionWell.Mode.INJECTING && w.well().injectionM3s() > 0,
                            "injecting: " + w.well().mode() + " " + w.well().injectionM3s());
                    h.assertTrue(prod.well().reservoir().injectedM3() > 1, "water in the pay: " + prod.well().reservoir().injectedM3());
                    h.assertTrue(prod.well().reservoir().pressurePa() > before[0], "pressure up for the producer too");
                    h.assertTrue(w.waterTank().getFluidAmount() < WellheadBlockEntity.WATER_MB, "water drawn from the tank");
                })
                .thenSucceed();
    }
}
