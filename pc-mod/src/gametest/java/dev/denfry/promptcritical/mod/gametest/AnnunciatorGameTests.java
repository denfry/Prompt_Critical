package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.AlarmHornBlockEntity;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity.AnnunciatorException;
import dev.denfry.promptcritical.mod.scada.AnnunciatorPanels;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.sim.alarm.Alarm;
import dev.denfry.promptcritical.sim.alarm.AlarmKind;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Светосигнальное табло 03c §3c.2.7 на настоящей машине: встроенная сигнализация красной зоны прибора заведена, а
 * сигнализация программы по тегу печи колонны после подъёма температуры через
 * задержку окно мигает (UNACK), табло включает сирену и даёт редстоун; «ЗВУК» глушит, «КВИТ» переводит в ACKED, возврат
 * температуры гасит окно. Программная сигнализация поднимается и снимается, шельфинг P1 запрещён (T-3c-07).
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AnnunciatorGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos COLUMN = new BlockPos(3, 2, 3), ANN = new BlockPos(8, 2, 3), HORN = new BlockPos(9, 2, 3);

    private AnnunciatorGameTests() {
    }

    private static void press(GameTestHelper h, String button, int times) {
        PanelHost host = (PanelHost) h.getBlockEntity(COLUMN);
        int id = -1;
        for (PanelLayout.Element e : host.panelLayout().elements)
            if (e instanceof PanelLayout.Button b && PanelText.shortKey(b.label()).equals(button)) id = b.id();
        h.assertTrue(id >= 0, "button " + button);
        for (int i = 0; i < times; i++) host.onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), id);
    }

    private static Alarm outlet(GameTestHelper h) {
        AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
        String tag = TelemetryGatewayBlockEntity.id("crude_column", h.absolutePos(COLUMN)) + "/prod.outlet";
        return a.engine().find(tag + "|" + AlarmKind.HIGH.name());
    }

    @GameTest(template = LARGE, timeoutTicks = 600)
    public static void machineAlarmRaisesTheWindowAndTheHorn(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(ANN.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(HORN.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(ANN, PcBlocks.ANNUNCIATOR_PANEL.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(HORN, PcBlocks.ALARM_HORN.get().defaultBlockState().setValue(facing, Direction.NORTH));
        String tag = TelemetryGatewayBlockEntity.id("crude_column", h.absolutePos(COLUMN)) + "/prod.outlet";
        int[] id = new int[1];
        h.startSequence()
                .thenWaitUntil(() -> {
                    Alarm a = outlet(h);
                    h.assertTrue(a != null && a.state() == AlarmState.NORMAL, "built-in alarm of the outlet dial's red zone");
                    h.assertTrue(a.definition().limit() == 380 && a.definition().priority() == 2, "limit at the red zone, priority 2");
                })
                // печь колонны не греет выше красной черты 380 °C, поэтому предел ниже ставит программа: 365 °C
                .thenExecute(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    id[0] = a.defineAlarm(Map.of("kind", "HIGH", "tag", tag, "limit", 365.0, "priority", 2.0, "deadband", 0.01,
                            "onDelay", 2.0, "offDelay", 2.0, "message", "Furnace outlet high"));
                    press(h, "prod.t_up", 2);
                })
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    h.assertTrue(a.engine().get(id[0]).state() == AlarmState.UNACK, "window flashes after the on-delay");
                    h.assertTrue(h.getBlockState(ANN).getValue(BlockStateProperties.POWERED), "annunciator powers the wall while the horn sounds");
                    AlarmHornBlockEntity horn = h.getBlockEntity(HORN);
                    h.assertTrue(horn.priority() == 2, "horn sounds priority 2: " + horn.priority());
                })
                .thenExecute(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    int[] ch = new int[AnnunciatorPanels.CHANNELS];
                    a.readPanel(ch);
                    boolean shown = false;
                    for (int i = 0; i < AnnunciatorBlockEntity.WINDOWS; i++) {
                        int f = ch[AnnunciatorPanels.W0 + 2 * i + 1];
                        if (a.window(i) == id[0]) shown = (f & 15) == AlarmState.UNACK.ordinal() && (f >> 4 & 15) == 2
                                && (f & AnnunciatorPanels.F_PRESENT) != 0;
                        if (a.window(i) == outlet(h).id()) h.assertTrue(ch[AnnunciatorPanels.W0 + 2 * i] > 0, "built-in alarm engraved by its dial");
                    }
                    h.assertTrue(shown, "the alarm has its own window");
                    h.assertTrue(ch[AnnunciatorPanels.UNACKED] >= 1, "status counts the unacknowledged alarm");
                    a.onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), AnnunciatorPanels.B_SILENCE);
                    h.assertTrue(a.engine().hornPriority() == 0, "silence stops the horn");
                    h.assertTrue(a.engine().get(id[0]).state() == AlarmState.UNACK, "silence does not acknowledge");
                    a.onPanelButton(FakePlayerFactory.getMinecraft(h.getLevel()), AnnunciatorPanels.B_ACK);
                    h.assertTrue(a.engine().get(id[0]).state() == AlarmState.ACKED, "ACK turns the window steady");
                })
                .thenExecute(() -> press(h, "prod.t_down", 2))
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    h.assertTrue(a.engine().get(id[0]).state() == AlarmState.NORMAL, "back below the deadband: window goes out");
                })
                .thenExecute(() -> h.assertFalse(h.getBlockState(ANN).getValue(BlockStateProperties.POWERED), "no horn, no redstone"))
                .thenSucceed();
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void programAlarmsAndShelving(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(ANN.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(ANN, PcBlocks.ANNUNCIATOR_PANEL.get().defaultBlockState().setValue(facing, Direction.NORTH));
        int[] ids = new int[2];
        h.startSequence()
                .thenExecute(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    ids[0] = a.defineAlarm(Map.of("kind", "SOFT", "priority", 2.0, "message", "Feed pump not running"));
                    ids[1] = a.defineAlarm(Map.of("kind", "SOFT", "priority", 1.0, "message", "Reactor trip"));
                    a.raise(ids[0], 1);
                    a.raise(ids[1], 1);
                    boolean refused = false;
                    try {
                        a.defineAlarm(Map.of("kind", "HIGH", "tag", "nowhere/x", "limit", 1.0));
                    } catch (AnnunciatorException e) {
                        refused = true;
                    }
                    h.assertTrue(refused, "alarm on an unknown tag refused");
                })
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    h.assertTrue(a.engine().get(ids[0]).state() == AlarmState.UNACK, "program alarm raised");
                    h.assertTrue(a.engine().hornPriority() == 1, "priority 1 sounds first");
                })
                .thenExecute(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    boolean refused = false;
                    try {
                        a.shelve(ids[1], 60);
                    } catch (AnnunciatorException e) {
                        refused = e.getMessage().contains("E_ACCESS");
                    }
                    h.assertTrue(refused, "priority 1 cannot be shelved (T-3c-07)");
                    h.assertTrue(a.engine().get(ids[1]).state() == AlarmState.UNACK, "state unchanged");
                    h.assertTrue(a.shelve(ids[0], 60), "priority 2 can be shelved");
                    h.assertTrue(((Number) a.getStatistics().get("shelved")).intValue() == 1, "shelved count visible");
                    h.assertTrue(a.acknowledge("ALL", null) == 1, "ACK ALL acknowledges the visible alarm");
                    a.clear(ids[1]);
                    h.assertTrue(a.deleteAlarm(ids[0]), "program alarms can be deleted");
                })
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    h.assertTrue(a.engine().get(ids[1]).state() == AlarmState.NORMAL, "acknowledged alarm clears with its condition");
                })
                .thenSucceed();
    }

    /**
     * Достижение {@code hmi} (11 §11.2.2): экран {@code pcui} уровня L1 сообщает табло число своих тегов; от 20 —
     * достижение игрокам смены. Заглушка игрока не в списке игроков измерения — событие шлём ей явно, как табло.
     */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void hmiOverviewWithTwentyTagsEarnsTheAchievement(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(ANN.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(ANN, PcBlocks.ANNUNCIATOR_PANEL.get().defaultBlockState().setValue(facing, Direction.NORTH));
        var p = ContentGameTests.player(h, new BlockPos(5, 2, 5));
        AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
        a.reportScreen(2, 40);
        a.reportScreen(1, 25);
        var who = java.util.List.of(p);
        dev.denfry.promptcritical.mod.content.SimAchievements.nearby(who, a.getBlockPos(), dev.denfry.promptcritical.mod.content.SimAchievements.HMI, "tags", 19);
        h.assertFalse(ContentGameTests.done(p, "hmi"), "19 tags are not enough");
        dev.denfry.promptcritical.mod.content.SimAchievements.nearby(who, a.getBlockPos(), dev.denfry.promptcritical.mod.content.SimAchievements.HMI, "tags", 20);
        h.assertTrue(ContentGameTests.done(p, "hmi"), "an L1 screen with 20 tags earns hmi");
        h.succeed();
    }
}
