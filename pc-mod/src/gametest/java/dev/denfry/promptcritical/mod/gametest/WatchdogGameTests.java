package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.WatchdogRelayBlockEntity;
import dev.denfry.promptcritical.mod.scada.WatchdogRelayBlockEntity.WatchdogException;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import dev.denfry.promptcritical.sim.scada.WatchdogTimer;
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
 * Сторожевое реле 03c §3c.7.2 в мире: без корма срабатывает через таймаут (T-3c-10), даёт редстоун в стену и
 * сигнализацию на табло; чужой компьютер не кормит (T-3c-12); резерв перехватывает сработавшее реле; кнопка «Сброс».
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WatchdogGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos WD = new BlockPos(3, 2, 3), ANN = new BlockPos(6, 2, 3);

    private WatchdogGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void silentControllerTripsTheRelay(GameTestHelper h) {
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(WD.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(ANN.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(WD, PcBlocks.WATCHDOG_RELAY.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(ANN, PcBlocks.ANNUNCIATOR_PANEL.get().defaultBlockState().setValue(facing, Direction.NORTH));
        long[] armedAt = new long[1];
        h.startSequence()
                .thenExecute(() -> {
                    WatchdogRelayBlockEntity w = h.getBlockEntity(WD);
                    w.configure(2);
                    w.arm(1);
                    armedAt[0] = h.getLevel().getGameTime();
                    boolean refused = false;
                    try {
                        w.kick(2);
                    } catch (WatchdogException e) {
                        refused = e.getMessage().contains("NOT_MASTER");
                    }
                    h.assertTrue(refused, "T-3c-12: kick from a foreign computer refused");
                })
                .thenWaitUntil(() -> {
                    WatchdogRelayBlockEntity w = h.getBlockEntity(WD);
                    h.assertTrue(w.timer().tripped(), "relay tripped");
                    long ticks = Math.round((w.timer().trippedAt() * 20) - armedAt[0]);
                    h.assertTrue(Math.abs(ticks - 40) <= 1, "T-3c-10: tripped 2.00 s after the last kick: " + ticks + " ticks");
                    h.assertTrue(h.getBlockState(WD).getValue(BlockStateProperties.POWERED), "relay output powers the wall");
                })
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity a = h.getBlockEntity(ANN);
                    h.assertTrue(a.engine().alarms().stream().anyMatch(al -> al.definition().tag().endsWith("/watchdog.tripped")
                            && al.state() == AlarmState.UNACK && al.definition().priority() == 2), "control system failure alarm, priority 2");
                })
                .thenExecute(() -> {
                    WatchdogRelayBlockEntity w = h.getBlockEntity(WD);
                    w.takeover(2);
                    w.reset(2);
                    w.arm(2);
                    w.kick(2);
                    h.assertTrue(w.timer().owner() == 2 && w.timer().state() == WatchdogTimer.State.ARMED, "standby computer is the master now");
                    h.assertFalse(h.getBlockState(WD).getValue(BlockStateProperties.POWERED), "output released after reset");
                })
                .thenWaitUntil(() -> {
                    WatchdogRelayBlockEntity w = h.getBlockEntity(WD);
                    h.assertTrue(w.timer().tripped(), "trips again without kicks");
                })
                .thenExecute(() -> {
                    WatchdogRelayBlockEntity w = h.getBlockEntity(WD);
                    w.onPanelButton(TestPlayers.of(h), 0);
                    h.assertTrue(w.timer().state() == WatchdogTimer.State.DISARMED, "local reset button");
                    h.assertFalse(h.getBlockState(WD).getValue(BlockStateProperties.POWERED), "no output after reset");
                })
                .thenSucceed();
    }
}
