package dev.denfry.promptcritical.mod.gametest;

import dan200.computercraft.api.filesystem.Mount;
import dan200.computercraft.api.filesystem.WritableMount;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaTask;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dan200.computercraft.api.peripheral.WorkMonitor;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.automation.AutomationBlockEntity;
import dev.denfry.promptcritical.mod.registry.PcBlocks;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Периферии устройств CC (03b §3b.1–3b.3, 03d §3d.14) через настоящий интерфейс CC: подставной компьютер вызывает
 * методы по именам, получает квитанции и события; задачи главного потока исполняются сразу.
 */
@GameTestHolder(PromptCritical.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CcDeviceGameTests {
    private static final String LARGE = "gametest/empty_12x12x12";

    private CcDeviceGameTests() {
    }

    /** Подставной компьютер: копит события. */
    static final class FakeComputer implements IComputerAccess, ILuaContext {
        final int id;
        final List<Object[]> events = new ArrayList<>();

        FakeComputer(int id) {
            this.id = id;
        }

        @Override
        public @Nullable String mount(String desiredLocation, Mount mount, String driveName) {
            return null;
        }

        @Override
        public @Nullable String mountWritable(String desiredLocation, WritableMount mount, String driveName) {
            return null;
        }

        @Override
        public void unmount(@Nullable String location) {
        }

        @Override
        public int getID() {
            return id;
        }

        @Override
        public synchronized void queueEvent(String event, @Nullable Object... arguments) {
            Object[] e = new Object[1 + (arguments == null ? 0 : arguments.length)];
            e[0] = event;
            if (arguments != null) System.arraycopy(arguments, 0, e, 1, arguments.length);
            events.add(e);
        }

        @Override
        public String getAttachmentName() {
            return "test_" + id;
        }

        @Override
        public Map<String, IPeripheral> getAvailablePeripherals() {
            return Map.of();
        }

        @Override
        public @Nullable IPeripheral getAvailablePeripheral(String name) {
            return null;
        }

        @Override
        public WorkMonitor getMainThreadMonitor() {
            return new WorkMonitor() {
                @Override
                public boolean canWork() {
                    return true;
                }

                @Override
                public boolean shouldWork() {
                    return true;
                }

                @Override
                public void trackWork(long time, TimeUnit unit) {
                }
            };
        }

        @Override
        public long issueMainThreadTask(LuaTask task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MethodResult executeMainThreadTask(LuaTask task) throws LuaException {
            return MethodResult.of(task.execute());
        }

        synchronized List<Object[]> named(String name) {
            List<Object[]> out = new ArrayList<>();
            for (Object[] e : events) if (name.equals(e[0])) out.add(e);
            return out;
        }
    }

    private static IDynamicPeripheral peripheral(GameTestHelper h, BlockPos rel) {
        IPeripheral p = h.getLevel().getCapability(PeripheralCapability.get(), h.absolutePos(rel), null);
        h.assertTrue(p instanceof IDynamicPeripheral, "device peripheral at " + rel + ": " + p);
        return (IDynamicPeripheral) p;
    }

    private static Object[] call(IDynamicPeripheral p, FakeComputer c, String method, Object... args) throws LuaException {
        int i = Arrays.asList(p.getMethodNames()).indexOf(method);
        if (i < 0) throw new LuaException("no method " + method);
        return p.callMethod(c, c, i, new ObjectArguments(args)).getResult();
    }

    /** Ошибка вызова с кодом {@code code}. */
    private static void expectError(GameTestHelper h, IDynamicPeripheral p, FakeComputer c, String code, String method, Object... args) {
        try {
            call(p, c, method, args);
        } catch (LuaException e) {
            h.assertTrue(e.getMessage() != null && e.getMessage().startsWith(code), method + " → " + code + ", got " + e.getMessage());
            return;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException(method + " should fail with " + code);
    }

    private static void place(GameTestHelper h, BlockPos pos, Block block) {
        h.setBlock(pos, block.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    /**
     * Колонна со щитом — {@code pc_column} и {@code pc_device}: чтение тега, подписка, команда-кнопка с квитанцией и
     * событием, проверка аргументов и лимит команд.
     */
    @SuppressWarnings("unchecked")
    @GameTest(template = LARGE, timeoutTicks = 300)
    public static void columnPeripheralReadsWatchesAndPressesButtons(GameTestHelper h) {
        BlockPos col = new BlockPos(3, 2, 3);
        place(h, col, PcBlocks.CRUDE_COLUMN.get());
        FakeComputer c = new FakeComputer(901);
        long[] ticket = new long[1];
        IDynamicPeripheral[] dev = new IDynamicPeripheral[1];
        h.startSequence()
                .thenExecute(() -> {
                    IDynamicPeripheral p = peripheral(h, col);
                    dev[0] = p;
                    h.assertTrue("pc_column".equals(p.getType()), "type " + p.getType());
                    h.assertTrue(p.getAdditionalTypes().contains("pc_device"), "pc_device: " + p.getAdditionalTypes());
                    p.attach(c);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    IDynamicPeripheral p = dev[0];
                    try {
                        List<String> tags = (List<String>) call(p, c, "listTags")[0];
                        h.assertTrue(tags.contains("prod.outlet"), "outlet tag: " + tags);
                        Object[] r = call(p, c, "read", "prod.outlet");
                        h.assertTrue(((Number) r[0]).doubleValue() == 360 && "GOOD".equals(r[1]), "outlet 360 GOOD: " + Arrays.toString(r));
                        Map<String, Object> info = (Map<String, Object>) call(p, c, "getTagInfo", "prod.outlet")[0];
                        h.assertTrue("ANALOG".equals(info.get("kind")), "analog: " + info);
                        Map<String, Object> many = (Map<String, Object>) call(p, c, "readMany", Map.of(1.0, "prod.outlet"))[0];
                        h.assertTrue(many.containsKey("prod.outlet"), "readMany: " + many);
                        call(p, c, "watch", "prod.outlet", Double.NEGATIVE_INFINITY, 362.0, 1.0);
                        List<Map<String, Object>> cmds = (List<Map<String, Object>>) call(p, c, "listCommands")[0];
                        h.assertTrue(cmds.stream().anyMatch(m -> "prod.t_up".equals(m.get("name"))), "T+ command: " + cmds);
                        ticket[0] = ((Number) call(p, c, "command", "prod.t_up")[0]).longValue();
                        h.assertTrue(ticket[0] > 0, "ticket issued");
                        expectError(h, p, c, "E_UNKNOWN_TAG", "read", "no.such.tag");
                        expectError(h, p, c, "E_ARG", "read", 5.0);
                        expectError(h, p, c, "E_RANGE", "watch", "prod.outlet", Double.NaN, 1.0, 0.0);
                        expectError(h, p, c, "E_ARG", "command", "prod.no_such_button");
                        expectError(h, p, c, "E_RANGE", "readInputRegisters", 0.0, 500.0);
                        expectError(h, p, c, "E_RANGE", "writeRegister", 0.0, 1.0);
                    } catch (LuaException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(e.getMessage());
                    }
                })
                .thenWaitUntil(() -> {
                    List<Object[]> res = c.named("pc_command_result");
                    h.assertTrue(!res.isEmpty(), "command result event");
                    Map<String, Object> pl = (Map<String, Object>) res.get(0)[2];
                    h.assertTrue("test_901".equals(res.get(0)[1]), "side is the attachment name");
                    h.assertTrue("DONE".equals(pl.get("state")) && ((Number) pl.get("ticket")).longValue() == ticket[0], "DONE: " + pl);
                })
                .thenWaitUntil(() -> {
                    List<Object[]> w = c.named("pc_setpoint_crossed");
                    h.assertTrue(!w.isEmpty(), "watch fired after the setpoint went up");
                    Map<String, Object> pl = (Map<String, Object>) w.get(0)[2];
                    h.assertTrue("HIGH".equals(pl.get("direction")) && pl.get("seq") != null && pl.get("t") != null, "HIGH with t and seq: " + pl);
                })
                .thenExecute(() -> {
                    IDynamicPeripheral p = dev[0];
                    try {
                        Map<String, Object> st = (Map<String, Object>) call(p, c, "getCommandStatus", (double) ticket[0])[0];
                        h.assertTrue("DONE".equals(st.get("state")), "status DONE: " + st);
                        List<Integer> regs = (List<Integer>) call(p, c, "readInputRegisters", 1000.0, 2.0)[0];
                        h.assertTrue(regs.size() == 2, "two float words");
                        // лимит команд на устройство: 10 в секунду
                        int refused = 0;
                        for (int i = 0; i < 20; i++) {
                            try {
                                call(p, c, "command", "prod.t_down");
                            } catch (LuaException e) {
                                if (e.getMessage().startsWith("E_RATE")) refused++;
                            }
                        }
                        h.assertTrue(refused >= 9, "command spam refused, got " + refused);
                    } catch (LuaException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(e.getMessage());
                    }
                    p.detach(c);
                })
                .thenSucceed();
    }

    /** ПАЗ — {@code pc_sis} только для чтения (03d §3d.14.2): состояние читается, всё изменяемое — руками. */
    @SuppressWarnings("unchecked")
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void sisPeripheralIsReadOnly(GameTestHelper h) {
        BlockPos sis = new BlockPos(5, 1, 5);
        place(h, sis, PcBlocks.SIS_LOGIC.get());
        FakeComputer c = new FakeComputer(902);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((AutomationBlockEntity) h.getBlockEntity(sis)).unit() != null, "SIS twin"))
                .thenExecute(() -> {
                    IDynamicPeripheral p = peripheral(h, sis);
                    h.assertTrue("pc_sis".equals(p.getType()), "type " + p.getType());
                    p.attach(c);
                    try {
                        Map<String, Object> st = (Map<String, Object>) call(p, c, "getState")[0];
                        h.assertTrue(st.containsKey("state") && st.containsKey("voting"), "state: " + st);
                        List<Map<String, Object>> causes = (List<Map<String, Object>>) call(p, c, "getCauses")[0];
                        h.assertTrue(!causes.isEmpty() && causes.get(0).containsKey("channels"), "causes: " + causes);
                        h.assertTrue(((List<?>) call(p, c, "listCommands")[0]).isEmpty(), "no commands on the SIS");
                    } catch (LuaException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(e.getMessage());
                    }
                    for (String m : List.of("reset", "setBypass", "requestTrip", "setSetpoint", "setMatrix"))
                        expectError(h, p, c, "E_HARDWARE_ONLY", m);
                    expectError(h, p, c, "E_HARDWARE_ONLY", "command", "automation.sis.bypass");
                    expectError(h, p, c, "E_RANGE", "writeCoil", 0.0, true);
                    p.detach(c);
                })
                .thenSucceed();
    }

    /** ПЛК — {@code pc_plc} (03d §3d.14.1): запись маркера командой с квитанцией видна в образе ввода-вывода. */
    @SuppressWarnings("unchecked")
    @GameTest(template = LARGE, timeoutTicks = 200)
    public static void plcPeripheralWritesMarkers(GameTestHelper h) {
        BlockPos plc = new BlockPos(5, 1, 5);
        place(h, plc, PcBlocks.PLC.get());
        FakeComputer c = new FakeComputer(903);
        IDynamicPeripheral[] dev = new IDynamicPeripheral[1];
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(((AutomationBlockEntity) h.getBlockEntity(plc)).unit() != null, "PLC twin"))
                .thenExecute(() -> {
                    IDynamicPeripheral p = peripheral(h, plc);
                    dev[0] = p;
                    h.assertTrue("pc_plc".equals(p.getType()), "type " + p.getType());
                    p.attach(c);
                    try {
                        Map<String, Object> st = (Map<String, Object>) call(p, c, "getState")[0];
                        h.assertTrue(st.containsKey("mode"), "mode: " + st);
                        call(p, c, "writeMarker", 3.0, true);
                        call(p, c, "writeWord", 2.0, 42.5);
                    } catch (LuaException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(e.getMessage());
                    }
                    expectError(h, p, c, "E_RANGE", "writeMarker", 9999.0, true);
                    expectError(h, p, c, "E_ARG", "writeMarker", 1.5, true);
                    expectError(h, p, c, "E_RANGE", "writeWord", 0.0, Double.POSITIVE_INFINITY);
                    expectError(h, p, c, "E_HARDWARE_ONLY", "setMode", "RUN");
                })
                .thenWaitUntil(() -> h.assertTrue(c.named("pc_command_result").size() >= 2, "two results"))
                .thenExecute(() -> {
                    try {
                        Map<String, Object> io = (Map<String, Object>) call(dev[0], c, "readIO")[0];
                        h.assertTrue(Boolean.TRUE.equals(((List<Object>) io.get("mx")).get(3)), "%MX3 set: " + io.get("mx"));
                        h.assertTrue(((Number) ((List<Object>) io.get("mw")).get(2)).doubleValue() == 42.5, "%MW2 = 42.5");
                    } catch (LuaException e) {
                        throw new net.minecraft.gametest.framework.GameTestAssertException(e.getMessage());
                    }
                    dev[0].detach(c);
                })
                .thenSucceed();
    }

    /** Страницы {@code help} и примеры программ лежат в ROM CC (03b §3b.6, 05 §5.8.3). */
    @GameTest(template = LARGE)
    public static void romHelpPagesShip(GameTestHelper h) {
        var rm = h.getLevel().getServer().getResourceManager();
        for (String f : List.of("lua/rom/help/pc_device.txt", "lua/rom/help/pc_reactor.txt", "lua/rom/help/pc_events.txt",
                "lua/rom/programs/pc/examples/trip_watch.lua"))
            h.assertTrue(rm.getResource(ResourceLocation.fromNamespaceAndPath("computercraft", f)).isPresent(), "ROM file " + f);
        h.succeed();
    }
}
