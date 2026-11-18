package dev.denfry.promptcritical.mod.gametest;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.scada.AnnunciatorBlockEntity;
import dev.denfry.promptcritical.mod.scada.ControlDeskBlockEntity;
import dev.denfry.promptcritical.mod.scada.ControlRoomCoreBlockEntity;
import dev.denfry.promptcritical.mod.scada.ControlRoomPanels;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.Cause;
import dev.denfry.promptcritical.sim.scada.ControlRoomHabitability.State;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Щитовая 03c §3c.9.1: ядро находит замкнутый объём, огонь в зале делает его «ухудшенным» по дыму, табло в зале само
 * заводит сигнализацию по лампе ядра, а пульт показывает её в списке и обитаемость зала и квитирует все табло.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ControlRoomGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos CORE = new BlockPos(2, 2, 2), DESK = new BlockPos(4, 2, 4), ANN = new BlockPos(6, 3, 4),
            FIRE = new BlockPos(3, 2, 5);

    private ControlRoomGameTests() {
    }

    @GameTest(template = LARGE, timeoutTicks = 400)
    public static void smokeDegradesTheRoomAndTheDeskShowsTheAlarm(GameTestHelper h) {
        // зал 5×3×5 воздуха в каменной коробке
        for (int x = 1; x <= 7; x++)
            for (int y = 1; y <= 5; y++)
                for (int z = 1; z <= 7; z++) {
                    boolean wall = x == 1 || x == 7 || y == 1 || y == 5 || z == 1 || z == 7;
                    h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        h.setBlock(CORE, PcBlocks.CONTROL_ROOM_CORE.get().defaultBlockState().setValue(facing, Direction.SOUTH));
        h.setBlock(DESK, PcBlocks.CONTROL_DESK.get().defaultBlockState().setValue(facing, Direction.NORTH));
        h.setBlock(ANN, PcBlocks.ANNUNCIATOR_PANEL.get().defaultBlockState().setValue(facing, Direction.WEST));
        h.startSequence()
                .thenWaitUntil(() -> {
                    ControlRoomCoreBlockEntity core = h.getBlockEntity(CORE);
                    h.assertTrue(core.enclosed() && core.volume() == 75 - 3, "closed room of 72 blocks of air: " + core.volume());
                    h.assertTrue(core.habitability().state() == State.NORMAL, "habitable");
                })
                .thenExecute(() -> {
                    h.setBlock(FIRE.below(), Blocks.SOUL_SAND.defaultBlockState());
                    h.setBlock(FIRE, Blocks.SOUL_FIRE.defaultBlockState());
                })
                .thenWaitUntil(() -> {
                    ControlRoomCoreBlockEntity core = h.getBlockEntity(CORE);
                    h.assertTrue(core.fires() == 1, "one fire in the room");
                    h.assertTrue(core.habitability().state() == State.DEGRADED && core.habitability().cause() == Cause.SMOKE,
                            "smoke degrades the room: " + core.habitability());
                })
                .thenWaitUntil(() -> {
                    AnnunciatorBlockEntity ann = h.getBlockEntity(ANN);
                    h.assertTrue(ann.engine().alarms().stream().anyMatch(a -> a.definition().tag().endsWith("/mcr.degraded")
                            && a.state() == AlarmState.UNACK && a.definition().priority() == 3), "annunciator alarms on the core's amber lamp");
                })
                .thenExecute(() -> {
                    ControlDeskBlockEntity desk = h.getBlockEntity(DESK);
                    int[] ch = new int[ControlRoomPanels.D_CHANNELS];
                    desk.readPanel(ch);
                    h.assertTrue(ch[ControlRoomPanels.D_TOTAL] >= 1 && ch[ControlRoomPanels.R0] > 0, "desk lists the alarm with its lamp");
                    h.assertTrue(ch[ControlRoomPanels.D_MCR] == State.DEGRADED.ordinal(), "desk shows the degraded room");
                    h.assertTrue(ch[ControlRoomPanels.D_IN_ROOM] == 1, "desk stands inside the room");
                    h.assertTrue(ch[ControlRoomPanels.D_UNACKED] >= 1 && ch[ControlRoomPanels.D_P3] >= 1, "summary counts");
                    h.assertTrue(desk.acknowledgeAll() >= 1, "ACK ALL from the desk");
                    AnnunciatorBlockEntity ann = h.getBlockEntity(ANN);
                    h.assertTrue(ann.engine().statistics().unacked() == 0, "every alarm acknowledged on the annunciator");
                })
                .thenSucceed();
    }
}
