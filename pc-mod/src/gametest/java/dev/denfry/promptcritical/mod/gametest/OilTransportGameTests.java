package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.client.whole.WholeModelHost;
import dev.denfry.promptcritical.mod.multiblock.Footprint;
import dev.denfry.promptcritical.mod.multiblock.WholePorts;
import dev.denfry.promptcritical.mod.production.LineHeaterBlockEntity;
import dev.denfry.promptcritical.mod.production.MainlinePumpBlockEntity;
import dev.denfry.promptcritical.mod.production.OilTankBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.registry.PcFluids;
import dev.denfry.promptcritical.mod.site.ConstructionSiteBlockEntity;
import dev.denfry.promptcritical.mod.site.SiteRegistry;
import dev.denfry.promptcritical.sim.pipeline.CentrifugalPump;
import dev.denfry.promptcritical.sim.pipeline.CrudeViscosity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Транспорт нефти I2 реального масштаба (09 §9.8): магистральный насос НМ 1250-260 на валу Create, путевой
 * подогреватель ПП-0,63 и резервуары РВС-1000 — площадка принимает чертёж, машина берёт вал и трубы только на своих
 * патрубках и считает по модели в pc-sim.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OilTransportGameTests {
    private static final String TEMPLATE = "gametest/empty_12x12x12", TALL = "gametest/empty_16x24x16";

    private OilTransportGameTests() {}

    private static BlockState north(Block b) {
        return b.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    /** Камень под нижним рядом объёма, чертёж на площадке, затем сама машина; возвращает BE контроллера. */
    private static WholeModelHost build(GameTestHelper h, BlockPos p, String id, Footprint fp, Block block) {
        for (Footprint.Cell c : fp.cells(BlockPos.ZERO, Direction.NORTH))
            if (c.pos().getY() == 0) h.setBlock(p.offset(c.pos()).below(), Blocks.STONE);
        h.setBlock(p, north(SiteRegistry.SITE.get()));
        var w = ((ConstructionSiteBlockEntity) h.getBlockEntity(p)).accept(id);
        h.assertTrue(w == null, id + " plan accepted: " + (w == null ? "" : w.getString()));
        h.setBlock(p, Blocks.AIR);
        h.setBlock(p, north(block));
        h.assertTrue(h.getBlockEntity(p) instanceof WholeModelHost host && id.equals(host.wholeModelId()), id + " has its whole model");
        return (WholeModelHost) h.getBlockEntity(p);
    }

    private static FluidStack crude(int mb) {
        return new FluidStack(PcFluids.CRUDE_OIL.source.get(), mb);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void mainlinePumpRunsOnItsCurveFromTheCreateShaft(GameTestHelper h) {
        BlockPos p = new BlockPos(4, 1, 4);
        var pump = (MainlinePumpBlockEntity) build(h, p, "mainline_pump_real", PcBlocks.FP_MAINLINE_PUMP_REAL, PcBlocks.MAINLINE_PUMP_REAL.get());
        h.assertTrue(h.getBlockState(p.offset(3, 0, 0)).getBlock() instanceof KineticPortBlock, "the Create shaft enters the motor tail");
        h.assertTrue(WholePorts.allows("mainline_pump_real", Direction.NORTH, -1, 0, -1, Direction.NORTH), "suction on the north nozzle");
        h.assertTrue(WholePorts.allows("mainline_pump_real", Direction.NORTH, -1, 0, 1, Direction.SOUTH), "discharge on the south nozzle");
        h.assertFalse(WholePorts.allows("mainline_pump_real", Direction.NORTH, 2, 0, 1, Direction.SOUTH), "no pipe on the motor");
        pump.suction().fill(crude(MainlinePumpBlockEntity.SUCTION_MB), IFluidHandler.FluidAction.EXECUTE);
        pump.setOn(true);
        var level = h.getLevel();
        h.succeedWhen(() -> {
            // вал Create на номинальной скорости
            pump.sampleShaft(MainlinePumpBlockEntity.NOMINAL_RPM, true, SimBindings.get(level).world().clock().tick());
            h.assertTrue(pump.discharge().amount() > 0, "the pump moved crude to the discharge");
            double q = pump.flowM3s();
            h.assertTrue(Math.abs(q - pump.operatingFlowM3s()) < 1e-9, "the flow is the operating point while the suction is full: " + q);
            h.assertTrue(Math.abs(pump.headM() - MainlinePumpBlockEntity.PUMP.headM(q, 1)) < 1e-6, "the head is read off the Q-H curve");
            h.assertTrue(pump.dischargePa() > 1.5e6, "a mainline pump gives MPa: " + pump.dischargePa());
            h.assertTrue(pump.shaftPowerW() > 0.5e6, "the shaft carries hundreds of kW: " + pump.shaftPowerW());
        });
    }

    @GameTest(template = "gametest/empty_3x3x3", timeoutTicks = 20)
    public static void coldCrudeRunsTheLineSlowerThanHeatedCrude(GameTestHelper h) {
        double rho = MainlinePumpBlockEntity.DENSITY;
        double cold = MainlinePumpBlockEntity.operatingFlow(1, CrudeViscosity.deadOilCp(rho, 20) * 1e-3);
        double warm = MainlinePumpBlockEntity.operatingFlow(1, CrudeViscosity.deadOilCp(rho, 60) * 1e-3);
        double qn = CentrifugalPump.NM_1250_260.nominalFlowM3s();
        // участок рассчитан так, что холодная нефть 20 °C идёт около номинала; нагретая до 60 °C — на десятки процентов больше
        h.assertTrue(Math.abs(cold / qn - 1) < 0.15, "cold crude near the rating: " + cold * 3600 + " m3/h");
        h.assertTrue(warm > cold * 1.1, "warm crude flows faster: " + cold * 3600 + " vs " + warm * 3600 + " m3/h");
        h.assertTrue(MainlinePumpBlockEntity.operatingFlow(0.5, 0.0365) < cold * 0.6, "half speed gives under 60 % of the flow on a friction line");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void lineHeaterWarmsASmallFlowToTheBathAndBurnsGas(GameTestHelper h) {
        BlockPos p = new BlockPos(6, 1, 4);
        var heater = (LineHeaterBlockEntity) build(h, p, "line_heater_real", PcBlocks.FP_LINE_HEATER_REAL, PcBlocks.LINE_HEATER_REAL.get());
        h.assertTrue(WholePorts.allows("line_heater_real", Direction.NORTH, 5, 1, -1, Direction.EAST), "crude in on the east head");
        h.assertTrue(WholePorts.allows("line_heater_real", Direction.NORTH, -5, 0, 1, Direction.WEST), "fuel gas on the west head");
        IFluidHandler ports = heater.fluids(Direction.EAST);
        h.assertTrue(ports.fill(crude(2000), IFluidHandler.FluidAction.EXECUTE) == 2000, "crude goes into the coil");
        h.assertTrue(ports.fill(new FluidStack(PcFluids.ASSOCIATED_GAS.source.get(), 100), IFluidHandler.FluidAction.EXECUTE) == 100,
                "gas goes to the burner");
        heater.setBurner(true);
        h.succeedWhen(() -> {
            h.assertTrue(heater.outlet().amount() >= 2000, "the crude passed the coil");
            h.assertTrue(Math.abs(heater.crudeC() - 85) < 1e-6, "a small flow leaves at the 95 °C bath less 10 K: " + heater.crudeC());
            h.assertTrue(heater.fuel().amount() < 100, "the burner burns gas");
            h.assertTrue(h.getBlockState(p).getValue(BlockStateProperties.LIT), "the burner is lit");
            h.assertTrue(heater.fluids(Direction.EAST).drain(500, IFluidHandler.FluidAction.SIMULATE).getAmount() == 500, "hot crude drains out");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void fixedRoofTankBreathesVapourWhileFilling(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 1, 7);
        var tank = (OilTankBlockEntity) build(h, p, "oil_tank_real", PcBlocks.FP_OIL_TANK_REAL, PcBlocks.OIL_TANK_REAL.get());
        h.assertTrue(tank.oil().capacity() == 1_000_000, "1000 m3");
        h.assertTrue(WholePorts.allows("oil_tank_real", Direction.NORTH, -6, 0, 0, Direction.WEST), "receive on the west");
        h.assertFalse(WholePorts.allows("oil_tank_real", Direction.NORTH, 0, 0, -6, Direction.NORTH), "no pipe through the shell");
        h.succeedWhen(() -> {
            // налив 0,4 м³/с через приёмный патрубок — как с магистрального насоса
            h.assertTrue(tank.fluids(Direction.WEST).fill(crude(20), IFluidHandler.FluidAction.EXECUTE) == 20, "the tank takes crude by pipe");
            h.assertFalse(tank.floatingRoof(), "fixed roof");
            h.assertTrue(tank.venting(), "the breather valve vents vapour while filling");
            h.assertTrue(tank.lossKgS() > 0, "breathing losses");
        });
    }

    @GameTest(template = TALL, timeoutTicks = 200)
    public static void floatingRoofTankKeepsTheVapourIn(GameTestHelper h) {
        BlockPos p = new BlockPos(7, 1, 7);
        var tank = (OilTankBlockEntity) build(h, p, "oil_tank_floating_real", PcBlocks.FP_OIL_TANK_REAL, PcBlocks.OIL_TANK_FLOATING_REAL.get());
        h.assertTrue(tank.fluids(Direction.WEST).fill(crude(300_000), IFluidHandler.FluidAction.EXECUTE) == 300_000, "the tank takes 300 m3");
        h.runAfterDelay(60, () -> {
            tank.fluids(Direction.WEST).fill(crude(20), IFluidHandler.FluidAction.EXECUTE);
            h.succeedWhen(() -> {
                h.assertTrue(tank.floatingRoof(), "floating roof");
                h.assertTrue(tank.lossKgS() > 0, "the rim seal still loses some vapour");
                h.assertFalse(tank.venting(), "no vapour cloud over a floating roof");
                h.assertTrue(tank.oil().fill() > 0.29, "the deck rides on 300 m3");
            });
        });
    }
}
