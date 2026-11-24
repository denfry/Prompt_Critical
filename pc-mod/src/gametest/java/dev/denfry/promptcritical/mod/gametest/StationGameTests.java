package dev.denfry.promptcritical.mod.gametest;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import dev.denfry.promptcritical.mod.station.StationRegistry;
import dev.denfry.promptcritical.mod.station.Stations;
import dev.denfry.promptcritical.sim.journal.Actor;
import dev.denfry.promptcritical.sim.journal.JournalAction;
import dev.denfry.promptcritical.sim.journal.JournalEntry;
import dev.denfry.promptcritical.sim.journal.JournalFilter;
import dev.denfry.promptcritical.sim.journal.Role;
import dev.denfry.promptcritical.sim.journal.StationRecord;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Права станции для компьютеров (04d §4d.9.2): команда Lua устройству на станции проходит с правами игрока, поставившего
 * компьютер. Без роли — квитанция {@code E_ACCESS} и запись UNAUTHORIZED_ACCESS от {@link Actor.Computer}; с ролью
 * оператора — команда выполняется и пишется в журнал.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos COLUMN = new BlockPos(3, 2, 3);
    private static final int COMPUTER = 902;

    private StationGameTests() {
    }

    private static Object[] call(IDynamicPeripheral p, CcDeviceGameTests.FakeComputer c, String method, Object... args) {
        int i = Arrays.asList(p.getMethodNames()).indexOf(method);
        if (i < 0) throw new GameTestAssertException("no method " + method);
        try {
            return p.callMethod(c, c, i, new ObjectArguments(args)).getResult();
        } catch (LuaException e) {
            throw new GameTestAssertException(method + ": " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(CcDeviceGameTests.FakeComputer c, long ticket) {
        for (Object[] e : c.named("pc_command_result")) {
            Map<String, Object> pl = (Map<String, Object>) e[2];
            if (((Number) pl.get("ticket")).longValue() == ticket) return pl;
        }
        return null;
    }

    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void computerCommandsUseTheOwnersStationRole(GameTestHelper h) {
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos abs = h.absolutePos(COLUMN);
        var server = h.getLevel().getServer();
        StationRegistry reg = StationRegistry.of(server);
        UUID owner = UUID.nameUUIDFromBytes("pc-test-station-owner".getBytes()), crew = UUID.nameUUIDFromBytes("pc-test-crew".getBytes());
        StationRecord st = StationRecord.create(UUID.randomUUID(), reg.uniqueName("gametest"), owner,
                reg.dimensionIndex(h.getLevel().dimension()), new ChunkPos(abs).toLong(), h.getLevel().getGameTime());
        reg.put(st);
        Stations.bindComputer(server, COMPUTER, crew, "crew");
        CcDeviceGameTests.FakeComputer c = new CcDeviceGameTests.FakeComputer(COMPUTER);
        IDynamicPeripheral[] dev = new IDynamicPeripheral[1];
        long[] ticket = new long[2];
        h.startSequence()
                .thenExecute(() -> {
                    IPeripheral p = h.getLevel().getCapability(PeripheralCapability.get(), abs, null);
                    h.assertTrue(p instanceof IDynamicPeripheral, "column peripheral: " + p);
                    dev[0] = (IDynamicPeripheral) p;
                    dev[0].attach(c);
                })
                .thenIdle(2)
                .thenExecute(() -> ticket[0] = ((Number) call(dev[0], c, "command", "prod.t_up")[0]).longValue())
                .thenWaitUntil(() -> {
                    Map<String, Object> r = result(c, ticket[0]);
                    h.assertTrue(r != null, "result of the refused command");
                    h.assertTrue("REJECTED".equals(r.get("state")) && String.valueOf(r.get("err")).startsWith("E_ACCESS"),
                            "E_ACCESS without a role: " + r);
                    List<JournalEntry> denied = reg.journal().query(st.stationId(), JournalFilter.ALL, 50).stream()
                            .filter(e -> e.action() == JournalAction.UNAUTHORIZED_ACCESS).toList();
                    h.assertTrue(denied.size() == 1 && denied.get(0).actor() instanceof Actor.Computer a
                            && a.computerId() == COMPUTER && crew.equals(a.cardHolder()), "journal warning from the computer: " + denied);
                })
                .thenExecute(() -> {
                    reg.put(reg.get(st.stationId()).withRole(crew, Role.OPERATOR));
                    ticket[1] = ((Number) call(dev[0], c, "command", "prod.t_up")[0]).longValue();
                })
                .thenWaitUntil(() -> {
                    Map<String, Object> r = result(c, ticket[1]);
                    h.assertTrue(r != null && "DONE".equals(r.get("state")), "operator's computer command done: " + r);
                    h.assertTrue(reg.journal().query(st.stationId(), JournalFilter.ALL, 50).stream()
                            .anyMatch(e -> e.action() == JournalAction.COMMAND && e.actor() instanceof Actor.Computer),
                            "accepted command journalled");
                })
                .thenExecute(() -> {
                    dev[0].detach(c);
                    reg.remove(st.stationId());
                })
                .thenSucceed();
    }
}
