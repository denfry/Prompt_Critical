package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.TelemetryException;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Шлюз телемеханики без CC: находит щит атмосферной колонны рядом, отдаёт приборы по именам с единицами, выполняет
 * команду кнопкой щита (видна на следующем цикле опроса), отказывает неизвестной кнопке и спаму команд.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TelemetryGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos COLUMN = new BlockPos(3, 2, 3), RTU = new BlockPos(7, 2, 3);

    private TelemetryGameTests() {
    }

    @SuppressWarnings("unchecked")
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void gatewayReadsPanelsAndPressesButtons(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(RTU, PcBlocks.RTU_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        BlockPos abs = h.absolutePos(COLUMN);
        String id = TelemetryGatewayBlockEntity.id("crude_column", abs);
        double[] before = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    List<Map<String, Object>> m = g.machines();
                    h.assertTrue(m.stream().anyMatch(x -> id.equals(x.get("id"))), "column on the gateway: " + m);
                    h.assertTrue(h.getBlockState(RTU).getValue(BlockStateProperties.LIT), "gateway lamp on");
                })
                .thenExecute(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    Map<String, Object> d = g.describe(id);
                    List<Map<String, Object>> points = (List<Map<String, Object>>) d.get("points");
                    List<Map<String, Object>> buttons = (List<Map<String, Object>>) d.get("buttons");
                    h.assertTrue(points.stream().anyMatch(p -> "prod.outlet".equals(p.get("name")) && "°C".equals(p.get("unit"))),
                            "furnace outlet point in °C: " + points);
                    h.assertTrue(buttons.stream().anyMatch(b -> "prod.t_up".equals(b.get("name"))), "T+ button: " + buttons);
                    Map<String, Object> v = g.read(id);
                    before[0] = ((Number) v.get("prod.outlet")).doubleValue();
                    h.assertTrue(before[0] == 360, "outlet 360 C by default: " + v);
                    h.assertTrue(v.get("prod.lit") instanceof Boolean, "lamps are status points");
                    h.assertTrue(((Number) v.get("_age")).doubleValue() <= 1.0, "snapshot not older than one scan cycle");
                    g.press(id, "prod.t_up");
                    boolean unknown = false;
                    try {
                        g.press(id, "prod.no_such_button");
                    } catch (TelemetryException e) {
                        unknown = true;
                    }
                    h.assertTrue(unknown, "unknown button refused");
                })
                .thenIdle(TelemetryGatewayBlockEntity.SCAN_TICKS + 2)
                .thenExecute(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    double after = ((Number) g.read(id).get("prod.outlet")).doubleValue();
                    h.assertTrue(after == before[0] + 5, "telecontrol raised the furnace setpoint: " + after);
                    // спам командами: телеуправление идёт не чаще двух в секунду, запас четыре
                    int refused = 0;
                    for (int i = 0; i < 20; i++) {
                        try {
                            g.press(id, "prod.t_down");
                        } catch (TelemetryException e) {
                            refused++;
                        }
                    }
                    h.assertTrue(refused >= 15, "command flood refused: " + refused);
                    boolean missing = false;
                    try {
                        g.read("crude_column@0,0,0");
                    } catch (TelemetryException e) {
                        missing = true;
                    }
                    h.assertTrue(missing, "machines outside the gateway are not readable");
                })
                .thenSucceed();
    }
}
