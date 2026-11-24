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

    /** Историан (03d §3d.9.5) рядом со шлюзом сам архивирует его аналоговые точки как теги «машина/точка». */
    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void historianArchivesTheGatewaysAnalogPoints(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockPos hist = new BlockPos(9, 2, 7);
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(RTU, PcBlocks.RTU_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(hist, PcBlocks.HISTORIAN_SERVER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        String tag = TelemetryGatewayBlockEntity.id("crude_column", h.absolutePos(COLUMN)) + "/prod.outlet";
        h.startSequence()
                .thenWaitUntil(() -> {
                    var server = (dev.denfry.promptcritical.mod.scada.HistorianBlockEntity) h.getBlockEntity(hist);
                    var a = server.archive(tag);
                    h.assertTrue(a != null && a.size() >= 1, "tag archived: " + server.listTags());
                    h.assertTrue(Math.abs(a.valueAt(a.lastT()) - 360) < 1, "outlet 360 C in the archive");
                    h.assertTrue(((Number) server.stats().get("tags")).intValue() > 1, "several points of the column archived");
                })
                .thenSucceed();
    }

    /** 16 §16.3.4, R58: терминал без CC видит машину на КП, тренд из историана и нажимает её кнопку от имени игрока. */
    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void operatorTerminalShowsAndControlsTheMachine(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockPos hist = new BlockPos(9, 2, 7), term = new BlockPos(5, 2, 9);
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(RTU, PcBlocks.RTU_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(hist, PcBlocks.HISTORIAN_SERVER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(term, PcBlocks.OPERATOR_TERMINAL.get().defaultBlockState().setValue(facing, Direction.NORTH));
        String id = TelemetryGatewayBlockEntity.id("crude_column", h.absolutePos(COLUMN));
        int[] view = new int[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.CHANNELS];
        double[] before = new double[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    var t = (dev.denfry.promptcritical.mod.scada.OperatorTerminalBlockEntity) h.getBlockEntity(term);
                    t.readPanel(view);
                    h.assertTrue((view[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.FLAGS]
                            & dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.F_POWERED) != 0, "terminal powered");
                    h.assertTrue(view[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.COUNT] >= 1, "machines on the terminal");
                    var layout = dev.denfry.promptcritical.mod.panel.PanelLayouts.byIndex(
                            view[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.S_LAYOUT] - 1);
                    h.assertTrue(layout != null && layout.id.equals("crude_column"), "column selected: " + (layout == null ? null : layout.id));
                    h.assertTrue((view[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.FLAGS]
                            & dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.F_TREND) != 0, "trend from the historian");
                    h.assertTrue(h.getBlockState(term).getValue(BlockStateProperties.POWERED), "screen lit");
                })
                .thenExecute(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    before[0] = ((Number) g.read(id).get("prod.outlet")).doubleValue();
                    var layout = dev.denfry.promptcritical.mod.panel.PanelLayouts.get("crude_column");
                    int up = -1;
                    for (var e : layout.elements)
                        if (e instanceof dev.denfry.promptcritical.mod.panel.PanelLayout.Button b
                                && dev.denfry.promptcritical.mod.panel.PanelText.shortKey(b.label()).equals("prod.t_up")) up = b.id();
                    int column = -1;
                    for (int k = 0; k < dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.COMMANDS; k++)
                        if (view[dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.CMD0 + k] - 1 == up) column = k;
                    h.assertTrue(up >= 0 && column >= 0, "T+ among the terminal commands");
                    var t = (dev.denfry.promptcritical.mod.scada.OperatorTerminalBlockEntity) h.getBlockEntity(term);
                    t.onPanelButton(net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(h.getLevel()),
                            dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.CMD_B0 + column);
                })
                .thenWaitUntil(() -> {
                    TelemetryGatewayBlockEntity g = h.getBlockEntity(RTU);
                    double now = ((Number) g.read(id).get("prod.outlet")).doubleValue();
                    h.assertTrue(now > before[0], "T+ from the terminal raised the outlet setpoint: " + before[0] + " -> " + now);
                })
                .thenSucceed();
    }
}
