package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.pa.IntercomStationBlockEntity;
import dev.denfry.promptcritical.mod.pa.PaChannel;
import dev.denfry.promptcritical.mod.pa.PaMessage;
import dev.denfry.promptcritical.mod.pa.PaPanels;
import dev.denfry.promptcritical.mod.pa.PaSystem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.device.DeviceException;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfile;
import dev.denfry.promptcritical.mod.scada.device.DeviceProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ГГС (13 §13.3.4, 16 §16.4.1): сообщения идут на громкоговорители зоны, важнее прерывает, менее важное ждёт. */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PaGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private PaGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void prerecordedMessagesFollowPriority(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockPos station = new BlockPos(2, 2, 2), a = new BlockPos(8, 2, 2), b = new BlockPos(8, 2, 8);
        h.setBlock(station, PcBlocks.INTERCOM_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(a, PcBlocks.PLANT_LOUDSPEAKER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(b, PcBlocks.PLANT_LOUDSPEAKER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    var level = h.getLevel();
                    h.assertValueEqual(PaSystem.announce(level, null, 1, PaMessage.FIRE), 2, "fire out of both zone-1 loudspeakers");
                    h.assertValueEqual(PaSystem.announce(level, null, 1, PaMessage.ALL_CLEAR), 0, "all clear waits for the fire message");
                    h.assertValueEqual(PaSystem.announce(level, null, 1, PaMessage.EVACUATE), 2, "evacuation overrides fire");
                    h.assertValueEqual(PaSystem.announce(level, null, 7, PaMessage.GAS), 0, "no loudspeakers in zone 7");
                    IntercomStationBlockEntity ic = h.getBlockEntity(station);
                    int[] out = new int[PaPanels.INTERCOM_CHANNELS];
                    ic.onPanelButton(FakePlayerFactory.getMinecraft(level), PaPanels.B_NEXT);
                    ic.readPanel(out);
                    h.assertValueEqual(out[PaPanels.I_ZONE], 2, "zone button moves the station to zone 2");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(a).getValue(PcProperties.RUNNING), "loudspeaker lit while on air"))
                .thenSucceed();
    }

    /**
     * Текстовый вызов (13 §13.3.4): строка из поля пульта уходит на громкоговорители зоны его канала, следующий вызов
     * ждёт паузы {@code ops.pa_page_cooldown_s}, а сообщение важнее вызов не перебивает.
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void typedCallGoesOutAndWaitsItsTurn(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockPos station = new BlockPos(2, 2, 2), a = new BlockPos(8, 2, 2);
        h.setBlock(station, PcBlocks.INTERCOM_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(a, PcBlocks.PLANT_LOUDSPEAKER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    var level = h.getLevel();
                    h.assertTrue(PaPanels.INTERCOM.textField(PaPanels.T_PAGE) != null, "the station panel has the call field");
                    IntercomStationBlockEntity ic = h.getBlockEntity(station);
                    var player = FakePlayerFactory.getMinecraft(level);
                    h.assertValueEqual(ic.page(player, "Pump 3 to maintenance"), 1, "the call goes out of the zone-1 loudspeaker");
                    h.assertValueEqual(ic.page(player, "Pump 3 to maintenance"), 0, "the next call waits for the cooldown");
                    h.assertValueEqual(PaSystem.announce(level, null, 1, PaMessage.FIRE), 1, "fire overrides the call");
                    h.assertValueEqual(PaSystem.page(level, null, PaChannel.CONTROL, 1, "x"), 0, "a control call waits for the fire message");
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(a).getValue(PcProperties.RUNNING), "loudspeaker lit while on air"))
                .thenSucceed();
    }

    /**
     * Lua {@code pc_ops} на пульте (13 §13.3.4): вызов проходит те же проверки, что поле щита, и делит с ним паузу;
     * плохой канал, пустой и длинный текст отвергаются до сервера, пауза и занятая зона — кодами отказа.
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void luaCallsGoThroughTheStation(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        BlockPos station = new BlockPos(2, 2, 2), a = new BlockPos(8, 2, 2);
        h.setBlock(station, PcBlocks.INTERCOM_STATION.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(a, PcBlocks.PLANT_LOUDSPEAKER.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    IntercomStationBlockEntity ic = h.getBlockEntity(station);
                    DeviceProfile p = DeviceProfiles.of(ic);
                    h.assertTrue(p != null && "pc_ops".equals(p.type) && p.commands.containsKey("page") && p.commands.containsKey("announce"),
                            "the station is a pc_ops peripheral with page and announce");
                    refused(h, p, "page", new Object[] {"NOPE", 1.0, "x"}, DeviceException.ARG, "unknown channel");
                    refused(h, p, "page", new Object[] {"CONTROL", 17.0, "x"}, DeviceException.RANGE, "zone outside 1..16");
                    refused(h, p, "page", new Object[] {"CONTROL", 1.0, "  "}, DeviceException.ARG, "text that filters to nothing");
                    refused(h, p, "page", new Object[] {"CONTROL", 1.0, "x".repeat(PaPanels.PAGE_MAX_CHARS + 1)}, DeviceException.RANGE,
                            "text longer than the subtitle line");
                    try {
                        p.commands.get("page").prepare(new Object[] {"control", 1.0, "Pump 3 to maintenance"}).apply(ic);
                    } catch (DeviceException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("the first call goes out: " + e.getMessage());
                    }
                    applyRefused(h, p, "page", new Object[] {"CONTROL", 1.0, "again"}, ic, DeviceException.RATE,
                            "the next call waits for the cooldown it shares with the panel");
                    h.assertValueEqual(ic.page(FakePlayerFactory.getMinecraft(h.getLevel()), "from the panel"), 0,
                            "the panel waits for the same cooldown");
                    applyRefused(h, p, "announce", new Object[] {"GAS", 7.0}, ic, DeviceException.BUSY, "no loudspeakers in zone 7");
                    try {
                        p.commands.get("announce").prepare(new Object[] {"FIRE", 1.0}).apply(ic);
                    } catch (DeviceException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException("fire goes out: " + e.getMessage());
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(h.getBlockState(a).getValue(PcProperties.RUNNING), "loudspeaker lit while on air"))
                .thenSucceed();
    }

    /** Команда отвергнута ещё при проверке аргументов. */
    private static void refused(GameTestHelper h, DeviceProfile p, String method, Object[] args, String code, String what) {
        try {
            p.commands.get(method).prepare(args);
        } catch (DeviceException e) {
            h.assertValueEqual(e.code(), code, what);
            return;
        }
        h.fail(what + ": accepted");
    }

    /** Команда принята, но отказ пришёл от пульта на потоке сервера. */
    private static void applyRefused(GameTestHelper h, DeviceProfile p, String method, Object[] args, IntercomStationBlockEntity ic,
            String code, String what) {
        try {
            p.commands.get(method).prepare(args).apply(ic);
        } catch (DeviceException e) {
            h.assertValueEqual(e.code(), code, what);
            return;
        }
        h.fail(what + ": sent");
    }
}
