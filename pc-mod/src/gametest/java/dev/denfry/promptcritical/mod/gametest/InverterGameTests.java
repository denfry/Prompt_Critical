package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.BatteryInverterBlockEntity;
import dev.denfry.promptcritical.mod.grid.FeInterfaceBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.grid.SolarInverterBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.sim.grid.FeInterface;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.InverterUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import dev.denfry.promptcritical.sim.storage.BatteryRoom;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Инверторные источники (15 §15.5, §15.7.1, §15.9): батарейный инвертор 6,3 кВ вплотную к западной грани
 * генератора, стеллажи батареи рядом; солнечный инвертор считает панели своей строки.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InverterGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(5, 1, 5), INV = new BlockPos(4, 1, 5);

    private InverterGameTests() {
    }

    private static BlockState facing(BlockState s, Direction d) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, d);
    }

    private static void station(GameTestHelper h) {
        h.setBlock(GEN, facing(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(GEN.above(), facing(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState(), Direction.NORTH));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64));
    }

    /** Шкаф-нагрузка отдаёт свой буфер FE, как потребитель: иначе через минуту буфер полон, нагрузка пропадает и остров идёт в разнос. */
    private static void drainLoad(GameTestHelper h) {
        var s = h.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK,
                h.absolutePos(INV.above()), Direction.UP);
        if (s != null) s.extractEnergy(Integer.MAX_VALUE, false);
    }

    private static boolean drained(GameTestHelper h) {
        drainLoad(h);
        return true;
    }

    private static GeneratorUnit gen(GameTestHelper h) {
        GeneratorUnit u = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
        h.assertTrue(u != null, "generator twin");
        return u;
    }

    private static BatteryInverterBlockEntity inv(GameTestHelper h) {
        return h.getBlockEntity(INV);
    }

    private static InverterUnit unit(GameTestHelper h) {
        InverterUnit u = inv(h).unit();
        h.assertTrue(u != null && u.source() != null, "inverter twin");
        return u;
    }

    @GameTest(template = LARGE, timeoutTicks = 2400)
    public static void batteryDischargesIntoTheGridAndChargesFromIt(GameTestHelper h) {
        station(h);
        h.setBlock(INV, facing(PcBlocks.BATTERY_INVERTER.get().defaultBlockState(), Direction.NORTH));
        // нагрузка острова 150 кВт, как в тесте шкафа FE: инвертор набирает отдачу не быстрее батареи за ним, нагрузка его
        // опережает, и генератор на обгонной муфте не уходит выше окна 51 Гц. На заряде нагрузка 50 кВт: при статизме
        // привода 4 % остров от 49,97 Гц до края окна 49 Гц несёт ≈ 250 кВт, 150 кВт с зарядом 129 кВт в него не входят
        h.setBlock(INV.above(), facing(PcBlocks.GRID_FE_INTERFACE_LARGE.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(new BlockPos(4, 1, 3), facing(PcBlocks.GRID_BATTERY_RACK.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(new BlockPos(3, 1, 3), facing(PcBlocks.GRID_BATTERY_RACK.get().defaultBlockState(), Direction.NORTH));
        h.startSequence()
                .thenWaitUntil(() -> {
                    gen(h);
                    unit(h);
                    FeInterfaceBlockEntity f = h.getBlockEntity(INV.above());
                    h.assertTrue(f.unit() != null && f.unit().iface() != null, "load cabinet twin");
                })
                .thenExecute(() -> {
                    GeneratorUnit g = gen(h);
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                    inv(h).setMode(BatteryInverterBlockEntity.Mode.DISCHARGE);
                    FeInterface load = ((FeInterfaceBlockEntity) h.getBlockEntity(INV.above())).unit().iface();
                    load.setMode(FeInterface.Mode.IMPORT);
                    load.setSetpointW(150e3);
                })
                .thenWaitUntil(() -> h.assertTrue(drained(h) && inv(h).battery() != null && inv(h).battery().modules() == 2,
                        "two racks found: " + (inv(h).battery() == null ? 0 : inv(h).battery().modules())))
                .thenWaitUntil(() -> h.assertTrue(drained(h) && unit(h).powerW() > 100e3, "discharge reaches a quarter C of two modules: "
                        + unit(h).powerW() + " W, on " + unit(h).source().connected() + ", " + unit(h).frequencyHz() + " Hz, " + unit(h).voltagePu() + " pu, in window " + unit(h).source().secondsInWindow() + " s, bound " + unit(h).bound()))
                .thenExecute(() -> {
                    h.assertTrue(unit(h).powerW() < 2 * 250e3 / 4 + 1, "not above the battery limit");
                    h.assertTrue(inv(h).battery().soc() < 0.5, "the battery gives energy: " + inv(h).battery().soc());
                    ((FeInterfaceBlockEntity) h.getBlockEntity(INV.above())).unit().iface().setSetpointW(50e3);
                    inv(h).setMode(BatteryInverterBlockEntity.Mode.CHARGE);
                })
                .thenWaitUntil(() -> h.assertTrue(drained(h) && unit(h).powerW() < -40e3, "charging draws from the grid: " + unit(h).powerW()))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void solarInverterCountsItsPanels(GameTestHelper h) {
        h.setBlock(INV, facing(PcBlocks.SOLAR_INVERTER.get().defaultBlockState(), Direction.NORTH));
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 2; z++) h.setBlock(new BlockPos(1 + x, 1, 8 + z), PcBlocks.SOLAR_PANEL.get().defaultBlockState());
        h.setBlock(new BlockPos(1, 3, 8), PcBlocks.LEAD_BLOCK.get().defaultBlockState()); // крыша над одной панелью
        h.succeedWhen(() -> {
            SolarInverterBlockEntity s = h.getBlockEntity(INV);
            h.assertTrue(s.panels() == 6, "six panels in the string: " + s.panels());
        });
    }

    @GameTest(template = LARGE, timeoutTicks = 40)
    public static void batteryRoomIsTheAirAroundTheInverter(GameTestHelper h) {
        // каменная коробка 7×5×7 снаружи, внутри 5×3×5 = 75 м³ воздуха без инвертора и стеллажа
        for (int x = 1; x <= 7; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 1; z <= 7; z++) {
                    boolean shell = x == 1 || x == 7 || y == 1 || y == 5 || z == 1 || z == 7;
                    h.setBlock(new BlockPos(x, y, z), shell ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        BlockPos inv = new BlockPos(4, 2, 4);
        h.setBlock(inv, facing(PcBlocks.BATTERY_INVERTER.get().defaultBlockState(), Direction.NORTH));
        h.setBlock(inv.north(), facing(PcBlocks.GRID_BATTERY_RACK.get().defaultBlockState(), Direction.NORTH));
        BatteryRoom room = new BatteryRoom(1, 0);
        BatteryInverterBlockEntity.measureRoom(h.getLevel(), h.absolutePos(inv), room);
        h.assertTrue(room.volumeM3() == 73, "sealed room volume: " + room.volumeM3());
        h.assertTrue(room.airChangesPerHour() == BatteryRoom.ACH_SEALED, "sealed room air changes: " + room.airChangesPerHour());
        h.setBlock(new BlockPos(4, 5, 4), Blocks.AIR.defaultBlockState()); // проём в крыше
        BatteryInverterBlockEntity.measureRoom(h.getLevel(), h.absolutePos(inv), room);
        h.assertTrue(room.airChangesPerHour() >= BatteryRoom.ACH_OPEN, "a hole to the sky ventilates: " + room.airChangesPerHour());
        h.succeed();
    }

    @GameTest(template = LARGE, timeoutTicks = 700)
    public static void trackerPanelsFollowTheSunOnlyWhileTheDriveTurns(GameTestHelper h) {
        h.setBlock(INV, facing(PcBlocks.SOLAR_INVERTER.get().defaultBlockState(), Direction.NORTH));
        for (int x = 0; x < 3; x++) h.setBlock(new BlockPos(1 + x, 1, 8), PcBlocks.SOLAR_PANEL_TRACKER.get().defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 9), PcBlocks.SOLAR_PANEL.get().defaultBlockState());
        BlockPos drive = new BlockPos(7, 1, 7);
        h.setBlock(drive, facing(PcBlocks.SOLAR_TRACKER_DRIVE.get().defaultBlockState(), Direction.NORTH));
        BlockPos motor = drive.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.runAfterDelay(1, () -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(16));
        h.startSequence()
                .thenWaitUntil(() -> {
                    SolarInverterBlockEntity s = h.getBlockEntity(INV);
                    h.assertTrue(s.panels() == 4 && s.trackingPanels() == 3,
                            "four panels, three on the turning tracker: " + s.panels() + ", " + s.trackingPanels());
                })
                .thenExecute(() -> h.setBlock(motor, Blocks.AIR.defaultBlockState()))
                .thenWaitUntil(() -> {
                    SolarInverterBlockEntity s = h.getBlockEntity(INV);
                    h.assertTrue(s.trackingPanels() == 0, "a stopped drive leaves the panels flat: " + s.trackingPanels());
                })
                .thenSucceed();
    }
}
