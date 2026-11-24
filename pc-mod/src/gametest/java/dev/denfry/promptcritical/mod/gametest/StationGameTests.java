package dev.denfry.promptcritical.mod.gametest;

import com.mojang.authlib.GameProfile;
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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Права станции для компьютеров (04d §4d.9.2, R74): команда Lua устройству на станции идёт с правами игрока, поставившего
 * компьютер, и только если компьютер стоит в чанках этой станции; открыть чужой исполнитель на станции можно только с
 * правом управления.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";
    private static final BlockPos COLUMN = new BlockPos(3, 2, 3), COMPUTER_AT = new BlockPos(5, 2, 3), MES = new BlockPos(7, 2, 3);

    private StationGameTests() {
    }

    private static UUID uuid(String name) {
        return UUID.nameUUIDFromBytes(("pc-test-" + name).getBytes(StandardCharsets.UTF_8));
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

    /** Станция на чанках колонны и компьютера (шаблон может лечь на границу чанков). */
    private static StationRecord station(GameTestHelper h, UUID owner, BlockPos... rel) {
        StationRegistry reg = StationRegistry.of(h.getLevel().getServer());
        StationRecord st = StationRecord.create(UUID.randomUUID(), reg.uniqueName("gametest"), owner,
                reg.dimensionIndex(h.getLevel().dimension()), new ChunkPos(h.absolutePos(rel[0])).toLong(), h.getLevel().getGameTime());
        for (BlockPos p : rel) st = st.withChunk(new ChunkPos(h.absolutePos(p)).toLong());
        reg.put(st);
        return st;
    }

    private static void column(GameTestHelper h) {
        h.setBlock(COLUMN, PcBlocks.CRUDE_COLUMN.get().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Настоящий компьютер CC с заданным id: права проверяют, что компьютер стоит там, где записан. */
    private static void computer(GameTestHelper h, BlockPos rel, int id) {
        Block block = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("computercraft", "computer_normal"));
        h.assertTrue(block != Blocks.AIR, "CC computer block registered");
        h.setBlock(rel, block.defaultBlockState());
        BlockEntity be = h.getBlockEntity(rel);
        var reg = h.getLevel().registryAccess();
        CompoundTag t = be.saveWithoutMetadata(reg);
        t.putInt("ComputerId", id);
        be.loadWithComponents(t, reg);
        h.assertTrue(be.saveWithoutMetadata(reg).getInt("ComputerId") == id, "computer id " + id + " set");
    }

    private static IDynamicPeripheral attach(GameTestHelper h, CcDeviceGameTests.FakeComputer c) {
        IPeripheral p = h.getLevel().getCapability(PeripheralCapability.get(), h.absolutePos(COLUMN), null);
        h.assertTrue(p instanceof IDynamicPeripheral, "column peripheral: " + p);
        ((IDynamicPeripheral) p).attach(c);
        return (IDynamicPeripheral) p;
    }

    /** Компьютер на станции: без роли владельца — E_ACCESS и запись от компьютера, с ролью оператора — команда идёт. */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void computerCommandsUseTheOwnersStationRole(GameTestHelper h) {
        int id = 902;
        column(h);
        computer(h, COMPUTER_AT, id);
        var server = h.getLevel().getServer();
        StationRegistry reg = StationRegistry.of(server);
        UUID crew = uuid("crew");
        StationRecord st = station(h, uuid("owner"), COLUMN, COMPUTER_AT);
        Stations.bindComputer(server, id, crew, "crew", GlobalPos.of(h.getLevel().dimension(), h.absolutePos(COMPUTER_AT)));
        CcDeviceGameTests.FakeComputer c = new CcDeviceGameTests.FakeComputer(id);
        IDynamicPeripheral[] dev = new IDynamicPeripheral[1];
        long[] ticket = new long[2];
        h.startSequence()
                .thenExecute(() -> dev[0] = attach(h, c))
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
                            && a.computerId() == id && crew.equals(a.cardHolder()), "journal warning from the computer: " + denied);
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

    /**
     * Компьютер вне чанков станции, даже поставленный её владельцем, получает отказ: иначе любой, кто сел за него снаружи,
     * действовал бы с правами владельца.
     */
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void computerOutsideTheStationIsRefused(GameTestHelper h) {
        int id = 903;
        column(h);
        var server = h.getLevel().getServer();
        StationRegistry reg = StationRegistry.of(server);
        UUID owner = uuid("outside-owner");
        StationRecord st = station(h, owner, COLUMN);
        // место компьютера — в соседнем чанке, который станции не принадлежит
        BlockPos away = h.absolutePos(COLUMN).offset(16, 0, 0);
        Stations.bindComputer(server, id, owner, "owner", GlobalPos.of(h.getLevel().dimension(), away));
        CcDeviceGameTests.FakeComputer c = new CcDeviceGameTests.FakeComputer(id);
        IDynamicPeripheral[] dev = new IDynamicPeripheral[1];
        long[] ticket = new long[1];
        h.startSequence()
                .thenExecute(() -> dev[0] = attach(h, c))
                .thenIdle(2)
                .thenExecute(() -> ticket[0] = ((Number) call(dev[0], c, "command", "prod.t_up")[0]).longValue())
                .thenWaitUntil(() -> {
                    Map<String, Object> r = result(c, ticket[0]);
                    h.assertTrue(r != null, "result of the command");
                    String err = String.valueOf(r.get("err"));
                    h.assertTrue("REJECTED".equals(r.get("state")) && err.startsWith("E_ACCESS") && err.contains("outside"),
                            "owner's computer outside the station refused: " + r);
                })
                .thenExecute(() -> {
                    dev[0].detach(c);
                    reg.remove(st.stationId());
                })
                .thenSucceed();
    }

    /** Посетитель без права управления не открывает сервер MES на станции; владелец станции открывает. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void visitorCannotUseAnExecutorInTheStation(GameTestHelper h) {
        h.setBlock(MES, PcBlocks.MES_SERVER.get().defaultBlockState());
        StationRegistry reg = StationRegistry.of(h.getLevel().getServer());
        UUID owner = uuid("mes-owner");
        StationRecord st = station(h, owner, MES);
        BlockPos abs = h.absolutePos(MES);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        var visitor = FakePlayerFactory.get(h.getLevel(), new GameProfile(uuid("visitor"), "visitor"));
        var boss = FakePlayerFactory.get(h.getLevel(), new GameProfile(owner, "boss"));
        h.startSequence()
                .thenExecute(() -> {
                    var refused = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(visitor, InteractionHand.MAIN_HAND, abs, hit));
                    h.assertTrue(refused.getUseBlock().isFalse(), "visitor may not use the MES server");
                    List<JournalEntry> denied = reg.journal().query(st.stationId(), JournalFilter.ALL, 20).stream()
                            .filter(e -> e.action() == JournalAction.UNAUTHORIZED_ACCESS).toList();
                    h.assertTrue(denied.size() == 1 && denied.get(0).actor() instanceof Actor.Player p && "visitor".equals(p.nameAtTime()),
                            "journal warning from the visitor: " + denied);
                    var allowed = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(boss, InteractionHand.MAIN_HAND, abs, hit));
                    h.assertTrue(!allowed.getUseBlock().isFalse(), "station owner uses the MES server");
                })
                .thenExecute(() -> reg.remove(st.stationId()))
                .thenSucceed();
    }

    /** Дисковод CC на станции посетитель не открывает: загрузочный диск выполнился бы на соседнем компьютере. */
    @GameTest(template = LARGE, timeoutTicks = 100)
    public static void visitorCannotUseADiskDriveInTheStation(GameTestHelper h) {
        Block drive = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("computercraft", "disk_drive"));
        h.assertTrue(drive != Blocks.AIR, "CC disk drive registered");
        h.setBlock(MES, drive.defaultBlockState());
        StationRegistry reg = StationRegistry.of(h.getLevel().getServer());
        StationRecord st = station(h, uuid("drive-owner"), MES);
        BlockPos abs = h.absolutePos(MES);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        var visitor = FakePlayerFactory.get(h.getLevel(), new GameProfile(uuid("drive-visitor"), "visitor"));
        h.startSequence()
                .thenExecute(() -> {
                    var e = NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(visitor, InteractionHand.MAIN_HAND, abs, hit));
                    h.assertTrue(e.getUseBlock().isFalse(), "visitor may not use the disk drive");
                })
                .thenExecute(() -> reg.remove(st.stationId()))
                .thenSucceed();
    }
}
