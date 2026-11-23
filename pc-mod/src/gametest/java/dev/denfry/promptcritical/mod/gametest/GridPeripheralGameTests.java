package dev.denfry.promptcritical.mod.gametest;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.grid.BreakerCellBlockEntity;
import dev.denfry.promptcritical.mod.grid.ShaftGeneratorBlockEntity;
import dev.denfry.promptcritical.mod.grid.TransformerBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.device.DeviceException;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfile;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfiles;
import dev.denfry.promptcritical.sim.grid.GeneratorUnit;
import dev.denfry.promptcritical.sim.grid.VoltageClass;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Периферии CC электросети с собственными методами (07 §7.17.1–7.17.3): то, что видит и может компьютер у щита. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GridPeripheralGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos GEN = new BlockPos(4, 1, 5), TRAFO = new BlockPos(8, 1, 2), CELL = new BlockPos(8, 2, 8);

    private GridPeripheralGameTests() {
    }

    private static BlockState north(BlockState s) {
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
    }

    private static Object read(DeviceProfile p, String method, BlockEntity be, Object... args) {
        try {
            return p.reads.get(method).read().read(be, null, args);
        } catch (DeviceException e) {
            throw new IllegalStateException(method + ": " + e.getMessage(), e);
        }
    }

    /** Команда так, как её исполняет сервер: проверка аргументов, затем действие; код отказа или null. */
    private static String command(DeviceProfile p, String method, BlockEntity be, Object... args) {
        try {
            p.commands.get(method).prepare(args).apply(be);
            return null;
        } catch (DeviceException e) {
            return e.code();
        }
    }

    @GameTest(template = LARGE, timeoutTicks = 1200)
    public static void generatorPeripheralReportsStateCapabilityAndTakesVoltageTarget(GameTestHelper h) {
        h.setBlock(GEN, north(PcBlocks.SHAFT_GENERATOR.get().defaultBlockState()));
        h.setBlock(GEN.above(), north(PcBlocks.GENERATOR_EXCITER.get().defaultBlockState()));
        BlockPos motor = GEN.south();
        h.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        h.startSequence()
                .thenExecute(() -> ((CreativeMotorBlockEntity) h.getBlockEntity(motor)).generatedSpeed.setValue(64))
                .thenWaitUntil(() -> h.assertTrue(((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit() != null, "generator twin"))
                .thenExecute(() -> {
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) h.getBlockEntity(GEN)).unit();
                    g.setVoltageClass(VoltageClass.MV_6_3);
                    while (g.gearRpm() != 64) g.cycleGear();
                    g.setFieldOn(true);
                })
                .thenWaitUntil(() -> {
                    BlockEntity be = h.getBlockEntity(GEN);
                    DeviceProfile p = DeviceProfiles.of(be);
                    h.assertTrue(p != null && "pc_generator".equals(p.type) && "SHAFT".equals(p.subtype), "pc_generator SHAFT");
                    Map<?, ?> st = (Map<?, ?>) read(p, "getState", be);
                    h.assertTrue(Math.abs((double) st.get("freq") - 50) < 1 && (double) st.get("vt") > 5000, "running and excited: " + st);
                    h.assertTrue("NONE".equals(st.get("breaker")), "no generator breaker: " + st.get("breaker"));
                })
                .thenExecute(() -> {
                    BlockEntity be = h.getBlockEntity(GEN);
                    DeviceProfile p = DeviceProfiles.of(be);
                    GeneratorUnit g = ((ShaftGeneratorBlockEntity) be).unit();
                    Map<?, ?> cap = (Map<?, ?>) read(p, "getCapability", be);
                    List<?> curve = (List<?>) cap.get("curve");
                    h.assertTrue(curve.size() == 9 && (double) cap.get("pMax") == 0.8 * g.ratedVa(), "capability diagram: " + cap);
                    Map<?, ?> first = (Map<?, ?>) curve.get(0);
                    h.assertTrue((double) first.get("qMax") > 0 && (double) first.get("qMin") < 0, "reactive range at no load: " + first);
                    h.assertTrue(DeviceException.RANGE.equals(command(p, "setVoltageTarget", be, 7500.0)), "7.5 kV is outside 0.9-1.1 of 6.3 kV");
                    h.assertTrue(command(p, "setVoltageTarget", be, 6600.0) == null
                            && Math.abs(g.voltageReferencePu() - 6600.0 / 6300) < 1e-9, "voltage target applied");
                    h.assertTrue(DeviceException.RANGE.equals(command(p, "setExcitationMode", be, "REACTIVE")), "only voltage regulation");
                    h.assertTrue(command(p, "setExcitationMode", be, "VOLTAGE") == null, "VOLTAGE is accepted");
                    h.assertTrue(DeviceException.FAULT.equals(tryRead(p, "getSyncScope", be)), "no breaker, no synchroscope");
                    h.assertTrue(command(p, "fieldBreaker", be, false) == null && !g.fieldOn(), "field breaker opens the field");
                })
                .thenSucceed();
    }

    private static String tryRead(DeviceProfile p, String method, BlockEntity be) {
        try {
            p.reads.get(method).read().read(be, null, new Object[0]);
            return null;
        } catch (DeviceException e) {
            return e.code();
        }
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void transformerAndBreakerPeripheralsFollowTheirMechanisms(GameTestHelper h) {
        h.setBlock(TRAFO, north(PcBlocks.DISTRIBUTION_TRANSFORMER.get().defaultBlockState()));
        h.setBlock(CELL, north(PcBlocks.SWITCHGEAR_CELL.get().defaultBlockState()));
        h.startSequence()
                .thenWaitUntil(() -> {
                    h.assertTrue(((TransformerBlockEntity) h.getBlockEntity(TRAFO)).unit() != null, "transformer twin");
                    h.assertTrue(((BreakerCellBlockEntity) h.getBlockEntity(CELL)).unit() != null, "breaker twin");
                })
                .thenExecute(() -> {
                    BlockEntity t = h.getBlockEntity(TRAFO);
                    DeviceProfile tp = DeviceProfiles.of(t);
                    h.assertTrue(tp != null && "pc_transformer".equals(tp.type), "pc_transformer");
                    h.assertTrue(command(tp, "setTap", t, 1.0) == null && ((TransformerBlockEntity) t).unit().tap() == 1,
                            "a de-energized transformer changes tap");
                    h.assertTrue(DeviceException.RANGE.equals(command(tp, "setTap", t, 3.0)), "tap 3 is outside -2..2");
                    h.assertTrue(DeviceException.RANGE.equals(command(tp, "setTapMode", t, "AUTO")), "no on-load tap changer");
                    Map<?, ?> ts = (Map<?, ?>) read(tp, "getState", t);
                    h.assertTrue(Integer.valueOf(1).equals(ts.get("tap")) && "MANUAL".equals(ts.get("tapMode")), "transformer state: " + ts);

                    BlockEntity c = h.getBlockEntity(CELL);
                    DeviceProfile cp = DeviceProfiles.of(c);
                    h.assertTrue(cp != null && "pc_breaker".equals(cp.type) && "MV_CELL".equals(cp.subtype), "pc_breaker MV_CELL");
                    h.assertTrue(command(cp, "close", c) == null, "close accepted");
                    h.assertTrue("CLOSED".equals(((Map<?, ?>) read(cp, "getState", c)).get("state")), "breaker closed");
                    h.assertTrue(command(cp, "open", c) == null, "open accepted");
                    h.assertTrue("OPEN".equals(((Map<?, ?>) read(cp, "getState", c)).get("state")), "breaker open");
                    h.assertTrue(read(cp, "getRelays", c) instanceof List<?>, "relays listed");
                    h.assertTrue(DeviceException.ACCESS.equals(command(cp, "resetLockout", c)), "lockout reset needs a shift supervisor");
                })
                .thenSucceed();
    }
}
