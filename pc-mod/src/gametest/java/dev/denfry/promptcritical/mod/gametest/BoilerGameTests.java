package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.binding.SimBindings;
import dev.denfry.promptcritical.mod.binding.SimLevelBinding;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.boiler.FireTubeBoilerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.GrateStokerBlockEntity;
import dev.denfry.promptcritical.mod.boiler.KineticPortBlock;
import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlockEntity;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlock;
import dev.denfry.promptcritical.mod.boiler.SteamEngineBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.combustion.BoilerRupture;
import dev.denfry.promptcritical.sim.combustion.FireTubeBoiler;
import dev.denfry.promptcritical.sim.core.CommandSource;
import dev.denfry.promptcritical.sim.core.SimCommand;
import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.plant.BoilerUnit;
import dev.denfry.promptcritical.sim.plant.PlantFuels;
import dev.denfry.promptcritical.sim.plant.SteamPlant;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Котельная I0 в мире (этап 6 M2, 07 §7.9.4, §7.9.7): котёл → машина → вал Create, ПК, упуск воды с пробкой и без,
 * цепная решётка на валу Create. Котёл «пускается» с готовой водой под давлением ({@link SteamPlant#presetWater}),
 * чтобы не ждать растопки — её проверяет {@code SteamPlantTest}.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BoilerGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    /** Котёл фасадом на север: клетки x 1…3, y 1…3, z 1…6. */
    private static final BlockPos BOILER = new BlockPos(2, 2, 2);
    /** Машина вплотную к котлу: x 4…6; вал Create — на (7, 2, 4) по оси X. */
    private static final BlockPos ENGINE = new BlockPos(5, 2, 2);
    private static final CommandSource TEST = new CommandSource.Automatic("gametest");

    private BoilerGameTests() {
    }

    private static SimLevelBinding binding(GameTestHelper h) {
        SimLevelBinding b = SimBindings.get(h.getLevel());
        if (b == null) throw new IllegalStateException("no simulation binding for the test level");
        return b;
    }

    private static SteamPlant plant(GameTestHelper h) {
        return binding(h).world().subsystem(SteamPlant.class);
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static FireTubeBoilerBlockEntity boiler(GameTestHelper h) {
        return h.getBlockEntity(BOILER);
    }

    private static BoilerUnit unit(GameTestHelper h) {
        BoilerUnit u = plant(h).boiler(boiler(h).simId());
        h.assertTrue(u != null, "boiler twin exists");
        return u;
    }

    private static void placeBoiler(GameTestHelper h) {
        h.setBlock(BOILER, north(PcBlocks.FIRE_TUBE_BOILER.get().defaultBlockState()));
    }

    /** Котёл с водой под давлением и горящим слоем угля. */
    private static void fire(GameTestHelper h, double pressure, double level) {
        SimId id = boiler(h).simId();
        plant(h).presetWater(id, pressure, level);
        binding(h).submit(new SimCommand.ChargeFuel(id, PlantFuels.COAL, 0.3 * 600), TEST);
        binding(h).submit(new SimCommand.Operate(id, SteamPlant.IGNITE), TEST);
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void engineDrivesCreateShaftAndGaugeReadsPressure(GameTestHelper h) {
        placeBoiler(h);
        h.setBlock(ENGINE, north(PcBlocks.STEAM_ENGINE_CYLINDER.get().defaultBlockState()));
        BlockPos port = ENGINE.offset(SteamEngineBlock.PORT_X, SteamEngineBlock.PORT_Y, SteamEngineBlock.PORT_Z);
        h.assertBlockPresent(PcBlocks.KINETIC_PORT.get(), port);
        h.assertTrue(h.getBlockState(port).getValue(KineticPortBlock.AXIS) == Direction.Axis.X, "port along the crankshaft");
        BlockPos shaft = port.east();
        h.setBlock(shaft, AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        h.setBlock(shaft.east(), AllBlocks.SHAFT.getDefaultState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        BlockPos gauge = new BlockPos(2, 2, 0);
        h.setBlock(gauge, north(PcBlocks.INSTRUMENT_PRESSURE.get().defaultBlockState()));

        h.startSequence()
                .thenWaitUntil(() -> {
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    h.assertTrue(!boiler(h).simId().isNone() && !e.simId().isNone(), "devices attached");
                    h.assertTrue(plant(h).engine(e.simId()) != null
                            && plant(h).engine(e.simId()).boiler().equals(boiler(h).simId()), "engine takes steam from the boiler");
                })
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, boiler(h)), "boiler without installation defects"))
                .thenExecute(() -> {
                    fire(h, 1.2e6, 0.67);
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    binding(h).submit(new SimCommand.Operate(e.simId(), SteamPlant.TOGGLE_STOP_VALVE), TEST);
                })
                .thenWaitUntil(() -> {
                    KineticBlockEntity s = h.getBlockEntity(shaft.east());
                    SteamEngineBlockEntity e = h.getBlockEntity(ENGINE);
                    h.assertTrue(s.getSpeed() != 0, "Create shaft turns: " + e.describe());
                })
                .thenExecute(() -> {
                    KineticPortBlockAccess.check(h, port);
                    PressureGaugeBlockEntity g = h.getBlockEntity(gauge);
                    h.assertTrue(g.readingPa() > 0.8e6 && g.readingPa() < 1.3e6, "gauge reads " + g.readingPa());
                })
                .thenSucceed();
    }

    /** Доступ к ёмкости источника на клетке вала. */
    private static final class KineticPortBlockAccess {
        static void check(GameTestHelper h, BlockPos port) {
            KineticBlockEntity k = h.getBlockEntity(port);
            h.assertTrue(k.isSource(), "port is a Create source");
            h.assertTrue(k.calculateAddedStressCapacity() > 0, "port offers stress capacity");
        }
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void reliefValveLiftsWithoutSteamDemand(GameTestHelper h) {
        placeBoiler(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!boiler(h).simId().isNone() && plant(h).boiler(boiler(h).simId()) != null, "boiler twin"))
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, boiler(h)), "boiler without installation defects"))
                .thenExecute(() -> fire(h, 1.3e6, 0.67))
                .thenWaitUntil(() -> h.assertTrue(unit(h).reliefLifts() > 0, "relief valve lifts at " + unit(h).pressurePa()))
                .thenExecute(() -> {
                    BoilerUnit u = unit(h);
                    h.assertTrue(u.pressurePa() < 1.03 * u.spec().reliefSetPressure(), "pressure held by the valve");
                    h.assertTrue(u.rupture() == BoilerRupture.NONE, "boiler intact");
                    BlockState st = h.getBlockState(BOILER);
                    h.assertTrue(st.getValue(BlockStateProperties.LIT) && !st.getValue(PcProperties.LOW_WATER)
                            && !st.getValue(PcProperties.EXPLODED), "lit, water in the glass");
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 4000)
    public static void lowWaterMeltsFusiblePlug(GameTestHelper h) {
        placeBoiler(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!boiler(h).simId().isNone() && plant(h).boiler(boiler(h).simId()) != null, "boiler twin"))
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, boiler(h)), "boiler without installation defects"))
                .thenExecute(() -> fire(h, 1.2e6, 0.25))
                .thenWaitUntil(() -> h.assertTrue(unit(h).plugMelted(), "plug melted, crown at " + unit(h).boiler().crownTempK()))
                .thenExecute(() -> {
                    h.assertTrue(!unit(h).lit(), "furnace quenched");
                    BlockState st = h.getBlockState(BOILER);
                    h.assertTrue(st.getValue(PcProperties.LOW_WATER) && !st.getValue(PcProperties.EXPLODED), "low water shown");
                    h.assertTrue(unit(h).rupture() == BoilerRupture.NONE, "boiler intact");
                    h.assertBlockPresent(PcBlocks.FIRE_TUBE_BOILER.get(), BOILER);
                })
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 1500)
    public static void lowWaterWithoutPlugBlowsBoilerUp(GameTestHelper h) {
        placeBoiler(h);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!boiler(h).simId().isNone() && plant(h).boiler(boiler(h).simId()) != null, "boiler twin"))
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, boiler(h)), "boiler without installation defects"))
                .thenExecute(() -> {
                    BoilerUnit u = unit(h);
                    u.boiler().setPlugFitted(false);
                    fire(h, 1.2e6, 0.25);
                    u.boiler().setMetalTemperature(FireTubeBoiler.CROWN_FAILURE_K - 1);   // потолок уже раскалён
                })
                .thenWaitUntil(() -> h.assertTrue(!h.getBlockState(BOILER).is(PcBlocks.FIRE_TUBE_BOILER.get()), "boiler destroyed by the blast"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void chainGrateStokerFeedsTheFurnace(GameTestHelper h) {
        placeBoiler(h);
        BlockPos stoker = new BlockPos(2, 1, 0);                 // задом к нижнему ряду фасада котла
        h.setBlock(stoker, north(PcBlocks.GRATE_STOKER.get().defaultBlockState()));
        BlockPos motor = stoker.west();                           // вал входит слева от фасада
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.EAST));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(32));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!boiler(h).simId().isNone() && plant(h).boiler(boiler(h).simId()) != null, "boiler twin"))
                .thenWaitUntil(() -> h.assertTrue(OpsGameTests.defects(h, boiler(h)), "boiler without installation defects"))
                .thenExecute(() -> {
                    GrateStokerBlockEntity g = h.getBlockEntity(stoker);
                    ItemStack rest = g.items(null).insertItem(0, new ItemStack(Items.COAL, 8), false);
                    h.assertTrue(rest.isEmpty(), "hopper takes coal");
                })
                .thenWaitUntil(() -> h.assertTrue(unit(h).boiler().bedKg() > 1, "coal on the grate: " + unit(h).boiler().bedKg()))
                .thenExecute(() -> h.assertTrue(h.getBlockState(stoker).getValue(dev.denfry.promptcritical.mod.block.PcProperties.RUNNING), "chain runs"))
                .thenSucceed();
    }
}
