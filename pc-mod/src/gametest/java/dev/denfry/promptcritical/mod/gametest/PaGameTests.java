package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.pa.IntercomStationBlockEntity;
import dev.denfry.promptcritical.mod.pa.PaChannel;
import dev.denfry.promptcritical.mod.pa.PaMessage;
import dev.denfry.promptcritical.mod.pa.PaPanels;
import dev.denfry.promptcritical.mod.pa.PaSystem;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
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
}
