package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.ops.Lockouts;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity;
import dev.denfry.promptcritical.sim.telemetry.CommandTickets;
import dev.denfry.promptcritical.sim.telemetry.EventGate;
import dev.denfry.promptcritical.sim.telemetry.ModbusMap;
import dev.denfry.promptcritical.sim.telemetry.SetpointWatch;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

/**
 * Состояние периферии одного устройства на стороне мода (03b §3b.1): срез показаний, квитанции команд, подписки и
 * доставка событий. Не знает о CC — периферия {@code mod.scada.cc.DevicePeripheral} лишь переводит вызовы Lua, поэтому
 * мод работает и без CC.
 *
 * <p>Потоки (03b §3b.1.3). Чтения (класс R) идут с потока компьютера по неизменяемому срезу {@link Snapshot}, который
 * поток сервера публикует каждый тик (атомарная замена ссылки). Команды (класс C) проверяются на потоке компьютера и
 * ложатся в очередь; поток сервера исполняет их в начале обработки устройства на следующем тике — тем же путём, что
 * кнопка щита ({@link PanelHost#onPanelButton}), так что защиты, блокировки и LOTO действуют как для игрока у щита.
 * События рождаются только на потоке сервера.
 */
public final class DeviceRuntime {
    /** До 32 подписок на компьютер на устройство (03b §3b.1.9). */
    public static final int MAX_WATCHES = 32;
    /** {@code readMany}: N ≤ 64 (03b §3b.1.9). */
    public static final int MAX_READ_MANY = 64;
    /** {@code cc.commands_per_device_per_s = 10} [BALANCE] (03b §3b.1.4). */
    public static final double COMMANDS_PER_SECOND = 10, COMMAND_BURST = 10;
    /** Сообщение {@code requestTrip} в журнал — не длиннее 64 символов (03b §3b.2.3). */
    public static final int MAX_MESSAGE = 64;
    /** Lua не нажимает кнопки щита, чьи ключи содержат эти слова: байпасы, выгрузка в руки, учения, разрешения. */
    private static final List<String> HARDWARE_KEYS = List.of("bypass", "unload", "accident.", "prot_btn", "permit", "b_key",
            "interlock_btn", "blackout", "refuel", "candu.drain", "low_flux", "sis.");

    /**
     * Кнопка щита с ключом {@code shortKey} — ручная работа, которую не делает ни компьютер, ни телемеханика (03b
     * §3b.1.8, барьер 3): байпасы, вывод защит, разрешения, выгрузка в руки, учения, всё у ПАЗ.
     */
    public static boolean hardwareKey(String shortKey) {
        String lower = shortKey.toLowerCase(Locale.ROOT);
        for (String k : HARDWARE_KEYS) if (lower.contains(k)) return true;
        return false;
    }

    /** Получатель событий: один компьютер, подключённый к устройству (вплотную или через модем). */
    public interface Client {
        Object key();

        int computerId();

        void queue(String event, Map<String, Object> payload);
    }

    /** Кнопка щита, как её видит Lua. */
    public record Button(String name, String label, int id, boolean hardwareOnly) {
    }

    /** Неизменяемый срез устройства на тике {@code clock}. */
    public record Snapshot(List<DeviceTag> tags, Map<String, Integer> index, List<Button> buttons, int[] channels, long clock,
            double simTime, boolean maintenance, boolean present) {
        @Nullable
        public DeviceTag tag(String name) {
            Integer i = index.get(name);
            return i == null ? null : tags.get(i);
        }

        public double numeric(String name) {
            DeviceTag t = tag(name);
            return t == null ? Double.NaN : t.numeric(channels);
        }

        public String quality() {
            return maintenance ? "UNCERTAIN" : "GOOD";
        }
    }

    private record Pending(long ticket, String channel, DeviceProfile.Action action, Object clientKey) {
    }

    private record Outgoing(Object clientKey, CommandTickets.Ticket ticket) {
    }

    private record Watch(int id, String tag, SetpointWatch watch) {
    }

    private static final class ClientState {
        final Client client;
        final EventGate gate = new EventGate();
        final Map<Integer, Watch> watches = new ConcurrentHashMap<>();
        final AtomicInteger nextWatch = new AtomicInteger(1);

        ClientState(Client client) {
            this.client = client;
        }
    }

    private final BlockEntity be;
    private final DeviceProfile profile;
    private final String blockId, deviceId;
    private volatile Snapshot snapshot;
    private final Object lock = new Object();
    private final CommandTickets tickets = new CommandTickets();
    private final TokenBucket commandBucket = new TokenBucket(COMMANDS_PER_SECOND, COMMAND_BURST);
    private final ConcurrentLinkedQueue<Pending> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Outgoing> outbox = new ConcurrentLinkedQueue<>();
    private final Map<Object, ClientState> clients = new ConcurrentHashMap<>();
    private final Map<String, Long> lastLua = new ConcurrentHashMap<>();
    /** Кто подал команду: результат квитанции уходит только этому компьютеру. */
    private final Map<Long, Object> owners = new ConcurrentHashMap<>();
    // только поток сервера
    private PanelLayout lastLayout;
    private List<DeviceTag> tags = List.of();
    private List<Button> buttons = List.of();
    private Map<String, Integer> index = Map.of();
    private long seq;
    private boolean faultLogged;
    @Nullable
    private final DeviceProfile.Probe probe;

    DeviceRuntime(BlockEntity be, DeviceProfile profile) {
        this.be = be;
        this.profile = profile;
        this.blockId = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).toString();
        this.deviceId = TelemetryGatewayBlockEntity.id(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath(), be.getBlockPos());
        this.probe = profile.probe == null ? null : profile.probe.get();
        sample(DeviceHub.clock());
    }

    public BlockEntity blockEntity() {
        return be;
    }

    public DeviceProfile profile() {
        return profile;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    // ------------------------------------------------------------------ поток сервера

    private boolean present() {
        Level level = be.getLevel();
        return !be.isRemoved() && level instanceof ServerLevel && level.isLoaded(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }

    private static String buttonName(PanelLayout.Button b) {
        String key = PanelText.shortKey(b.label());
        boolean translatable = b.label().getContents() instanceof TranslatableContents;
        if (!translatable && b.tip() != null && b.tip().getContents() instanceof TranslatableContents) key = PanelText.shortKey(b.tip());
        else if (!translatable) key = "button_" + b.id();
        return key;
    }

    private boolean hardwareOnly(String name, int id) {
        return profile.readOnly || profile.hardwareButtons.contains(id) || hardwareKey(name);
    }

    private void relayout(PanelLayout layout) {
        lastLayout = layout;
        tags = DeviceTag.of(layout);
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < tags.size(); i++) idx.put(tags.get(i).name(), i);
        index = Collections.unmodifiableMap(idx);
        List<Button> bs = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (PanelLayout.Element e : layout.elements) {
            if (!(e instanceof PanelLayout.Button b)) continue;
            String name = buttonName(b);
            if (!used.add(name)) {
                name = name + "_" + b.id();
                used.add(name);
            }
            bs.add(new Button(name, PanelText.english(b.label()), b.id(), hardwareOnly(name, b.id())));
        }
        buttons = List.copyOf(bs);
    }

    private void sample(long clock) {
        boolean present = present();
        int[] ch = new int[0];
        if (present && be instanceof PanelHost host) {
            try {
                PanelLayout layout = host.panelLayout();
                if (layout != lastLayout) relayout(layout);
                ch = new int[layout.channels];
                host.readPanel(ch);
            } catch (RuntimeException e) {
                if (!faultLogged) PromptCritical.LOGGER.warn("Device {} failed to read its panel for CC", deviceId, e);
                faultLogged = true;
                present = false;
            }
        }
        Level level = be.getLevel();
        double simTime = level == null ? 0 : level.getGameTime() / TokenBucket.TICKS_PER_SECOND;
        boolean maintenance = present && Lockouts.locked(level, be.getBlockPos());
        snapshot = new Snapshot(tags, index, buttons, ch, clock, simTime, maintenance, present);
    }

    /** Тик устройства: исполнить команды, снять срез, проверить подписки и зонды, разослать события. */
    void tick(long clock) {
        Outgoing o;
        while ((o = outbox.poll()) != null) result(o.clientKey(), o.ticket());
        Pending p;
        while ((p = queue.poll()) != null) execute(p, clock);
        sample(clock);
        Snapshot s = snapshot;
        if (s.present() && probe != null) {
            try {
                probe.tick(be, s, emitter(clock));
            } catch (RuntimeException e) {
                if (!faultLogged) PromptCritical.LOGGER.warn("Device {} event probe failed", deviceId, e);
                faultLogged = true;
            }
        }
        for (ClientState cs : clients.values()) {
            for (Watch w : cs.watches.values()) {
                DeviceTag t = s.tag(w.tag());
                if (t == null) continue;
                double v = t.numeric(s.channels());
                SetpointWatch.Direction d = w.watch().update(v, clock);
                if (d == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("watchId", w.id());
                m.put("tag", w.tag());
                m.put("direction", d.name());
                m.put("threshold", w.watch().threshold(d));
                m.put("value", v);
                emit(cs, "pc_setpoint_crossed", m, clock);
            }
            Map<String, Integer> dropped = cs.gate.overflow(clock);
            if (dropped != null) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dropped", new LinkedHashMap<>(dropped));
                stamp(m);
                cs.client.queue("pc_event_overflow", Collections.unmodifiableMap(m));
            }
        }
    }

    private DeviceProfile.Emitter emitter(long clock) {
        return new DeviceProfile.Emitter() {
            @Override
            public void emit(String event, Map<String, Object> payload) {
                broadcast(event, payload, clock);
            }

            @Override
            public boolean luaRequested(String channel, long ticks) {
                Long t = lastLua.get(channel);
                return t != null && clock - t <= ticks && clock >= t;
            }
        };
    }

    private void stamp(Map<String, Object> m) {
        Snapshot s = snapshot;
        m.put("t", s == null ? 0.0 : s.simTime());
        m.put("seq", ++seq);
    }

    private void emit(ClientState cs, String event, Map<String, Object> payload, long clock) {
        Map<String, Object> m = new LinkedHashMap<>(payload);
        stamp(m);
        if (cs.gate.admit(event, clock)) cs.client.queue(event, Collections.unmodifiableMap(m));
    }

    /** Событие всем компьютерам устройства (03b §3b.3.1); номер {@code seq} — один на устройство. */
    private void broadcast(String event, Map<String, Object> payload, long clock) {
        Map<String, Object> m = new LinkedHashMap<>(payload);
        stamp(m);
        Map<String, Object> frozen = Collections.unmodifiableMap(m);
        for (ClientState cs : clients.values()) if (cs.gate.admit(event, clock)) cs.client.queue(event, frozen);
    }

    private void result(Object clientKey, CommandTickets.Ticket t) {
        ClientState cs = clients.get(clientKey);
        if (cs == null) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ticket", t.id());
        m.put("state", t.state().name());
        if (t.error() != null) m.put("err", t.error());
        emit(cs, "pc_command_result", m, DeviceHub.clock());
    }

    private void execute(Pending p, long clock) {
        synchronized (lock) {
            CommandTickets.Ticket t = tickets.get(p.ticket(), clock);
            if (t == null || t.state() != CommandTickets.State.QUEUED) {
                owners.remove(p.ticket());
                return;
            }
        }
        CommandTickets.State state = CommandTickets.State.DONE;
        String err = null;
        if (!present()) {
            state = CommandTickets.State.REJECTED;
            err = DeviceException.UNLOADED + ": device is not loaded";
        } else if (Lockouts.locked(be.getLevel(), be.getBlockPos())) {
            state = CommandTickets.State.REJECTED;
            err = DeviceException.ACCESS + ": MAINTENANCE (locked out)";
        } else {
            try {
                p.action().apply(be);
                lastLua.put(p.channel(), clock);
            } catch (DeviceException e) {
                state = DeviceException.INTERLOCK.equals(e.code()) || DeviceException.ACCESS.equals(e.code())
                        || DeviceException.RANGE.equals(e.code()) ? CommandTickets.State.REJECTED : CommandTickets.State.FAILED;
                err = e.getMessage();
            } catch (RuntimeException e) {
                PromptCritical.LOGGER.warn("Device {} command {} failed", deviceId, p.channel(), e);
                state = CommandTickets.State.FAILED;
                err = DeviceException.FAULT + ": internal error";
            }
        }
        CommandTickets.Transition tr;
        synchronized (lock) {
            tr = tickets.move(p.ticket(), state, err, clock);
        }
        owners.remove(p.ticket());
        if (tr != null) result(p.clientKey(), tr.ticket());
    }

    // ------------------------------------------------------------------ подключение компьютеров

    public void attach(Client c) {
        clients.put(c.key(), new ClientState(c));
        DeviceHub.live(this);
    }

    public void detach(Object key) {
        clients.remove(key);
        if (clients.isEmpty()) DeviceHub.idle(this);
    }

    boolean hasClients() {
        return !clients.isEmpty();
    }

    // ------------------------------------------------------------------ поток компьютера: общие методы pc_device

    /** Общие методы {@code pc_device} (03b §3b.1.9) и расширения этого мода ({@code getState}, {@code listCommands}, {@code command}). */
    public static final List<String> BASE_METHODS = List.of("getDeviceInfo", "getControlMode", "getAccessLevel", "listTags", "getTagInfo",
            "read", "readMany", "watch", "unwatch", "getCommandStatus", "cancel", "simTime", "readInputRegisters", "readHoldingRegisters",
            "writeRegister", "readCoils", "readDiscreteInputs", "writeCoil", "getState", "listCommands", "command");

    /** Все имена методов Lua этого устройства в постоянном порядке. */
    public List<String> methodNames() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>(BASE_METHODS);
        names.addAll(profile.reads.keySet());
        names.addAll(profile.commands.keySet());
        names.addAll(profile.buttonCommands.keySet());
        names.addAll(profile.hardwareMethods);
        return List.copyOf(names);
    }

    /** Чтение профиля, которое надо исполнить на потоке сервера: цена — в токенах, {@code < 0} — не такое чтение. */
    public double mainThreadCost(String method) {
        if (profile.hardwareMethods.contains(method)) return -1;
        DeviceProfile.CostedRead r = profile.reads.get(method);
        return r == null ? -1 : r.cost();
    }

    /** Исполнить чтение профиля; только на потоке сервера. */
    public Object[] readOnServer(String method, Object[] a) throws DeviceException {
        if (!present()) throw new DeviceException(DeviceException.UNLOADED, "device is not loaded");
        Snapshot s = snapshot;
        Object v = profile.reads.get(method).read().read(be, s, a);
        return v instanceof Object[] arr ? arr : new Object[] {v};
    }

    private Snapshot live() throws DeviceException {
        Snapshot s = snapshot;
        if (s == null || !s.present()) throw new DeviceException(DeviceException.UNLOADED, "device is not loaded");
        return s;
    }

    /** Списать цену чтения профиля с бюджета компьютера до постановки задачи на поток сервера. */
    public void charge(Client c, double cost) throws DeviceException {
        admit(c, cost);
    }

    private static void admit(Client c, double cost) throws DeviceException {
        if (!DeviceHub.admit(c.computerId(), cost)) throw new DeviceException(DeviceException.RATE, "call budget exhausted, sleep between calls");
    }

    private DeviceTag tag(Snapshot s, String name) throws DeviceException {
        DeviceTag t = s.tag(name);
        if (t == null) throw new DeviceException(DeviceException.UNKNOWN_TAG, name);
        return t;
    }

    /** Вызов метода Lua на потоке компьютера; чтения профиля сюда не попадают ({@link #mainThreadCost}). */
    public Object[] call(Client c, String method, Object[] a) throws DeviceException {
        if (profile.hardwareMethods.contains(method)) {
            admit(c, 1);
            throw new DeviceException(DeviceException.HARDWARE_ONLY, method + " is done only at the cabinet, by hand");
        }
        DeviceProfile.Command cmd = profile.commands.get(method);
        if (cmd != null) {
            admit(c, 8);
            DeviceProfile.Action act = cmd.prepare(a);
            return new Object[] {submit(c, method, act)};
        }
        Integer button = profile.buttonCommands.get(method);
        if (button != null) {
            admit(c, 8);
            if ("requestTrip".equals(method) && a.length > 0 && a[0] != null) {
                String msg = LuaArgs.string(a, 0);
                if (msg.length() > MAX_MESSAGE) throw new DeviceException(DeviceException.RANGE, "message longer than " + MAX_MESSAGE);
                PromptCritical.LOGGER.info("Computer #{} requested a trip of {}: {}", c.computerId(), deviceId, msg);
            }
            return new Object[] {pressById(c, button)};
        }
        return switch (method) {
            case "getDeviceInfo" -> {
                admit(c, 1);
                yield new Object[] {deviceInfo()};
            }
            case "getControlMode" -> {
                admit(c, 1);
                yield new Object[] {live().maintenance() ? "MAINTENANCE" : "REMOTE"};
            }
            case "getAccessLevel" -> {
                admit(c, 1);
                // карт доступа (03b §3b.1.8) в моде пока нет: компьютер в сети устройства — оператор у щита
                yield new Object[] {"OPERATOR"};
            }
            case "simTime" -> {
                admit(c, 1);
                Snapshot s = snapshot;
                yield new Object[] {s == null ? 0.0 : s.simTime()};
            }
            case "listTags" -> {
                admit(c, 4);
                List<String> out = new ArrayList<>();
                for (DeviceTag t : live().tags()) out.add(t.name());
                yield new Object[] {out};
            }
            case "getTagInfo" -> {
                admit(c, 1);
                yield new Object[] {tag(live(), LuaArgs.string(a, 0)).info()};
            }
            case "read" -> {
                admit(c, 1);
                Snapshot s = live();
                DeviceTag t = tag(s, LuaArgs.string(a, 0));
                yield new Object[] {t.value(s.channels()), s.quality(), s.simTime()};
            }
            case "readMany" -> {
                List<String> names = LuaArgs.strings(a, 0, MAX_READ_MANY);
                admit(c, 4 + 0.25 * names.size());
                Snapshot s = live();
                Map<String, Object> out = new LinkedHashMap<>();
                for (String n : names) {
                    DeviceTag t = tag(s, n);
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("v", t.value(s.channels()));
                    e.put("q", s.quality());
                    e.put("t", s.simTime());
                    out.put(n, e);
                }
                yield new Object[] {out};
            }
            case "getState" -> {
                admit(c, 4);
                yield new Object[] {state(live())};
            }
            case "watch" -> {
                admit(c, 8);
                yield new Object[] {watch(c, a)};
            }
            case "unwatch" -> {
                admit(c, 1);
                ClientState cs = clients.get(c.key());
                int id = LuaArgs.integer(a, 0, 1, Integer.MAX_VALUE);
                yield new Object[] {cs != null && cs.watches.remove(id) != null};
            }
            case "getCommandStatus" -> {
                admit(c, 1);
                yield new Object[] {commandStatus(LuaArgs.ticket(a, 0))};
            }
            case "cancel" -> {
                admit(c, 8);
                long id = LuaArgs.ticket(a, 0);
                CommandTickets.Transition tr;
                synchronized (lock) {
                    tr = tickets.cancel(id, DeviceHub.clock());
                }
                if (tr != null) {
                    Object owner = owners.remove(id);
                    outbox.add(new Outgoing(owner == null ? c.key() : owner, tr.ticket()));
                }
                yield new Object[] {tr != null};
            }
            case "listCommands" -> {
                admit(c, 4);
                List<Map<String, Object>> out = new ArrayList<>();
                List<Button> allowed = allowedButtons(live());
                for (int i = 0; i < allowed.size(); i++) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", allowed.get(i).name());
                    m.put("label", allowed.get(i).label());
                    m.put("coil", i);
                    out.add(m);
                }
                yield new Object[] {out};
            }
            case "command" -> {
                admit(c, 8);
                String name = LuaArgs.string(a, 0);
                Button b = null;
                for (Button x : live().buttons()) if (x.name().equals(name)) b = x;
                if (b == null) throw new DeviceException(DeviceException.ARG, "no command " + name + " on this device");
                if (b.hardwareOnly()) throw new DeviceException(DeviceException.HARDWARE_ONLY, name + " is done only at the panel, by hand");
                yield new Object[] {pressById(c, b.id())};
            }
            case "readInputRegisters" -> {
                admit(c, 4);
                int addr = LuaArgs.integer(a, 0, 0, ModbusMap.FLOAT_BASE + ModbusMap.FLOAT_COUNT - 1);
                int n = LuaArgs.integer(a, 1, 1, ModbusMap.MAX_REGISTERS);
                if (addr + n > ModbusMap.FLOAT_BASE + ModbusMap.FLOAT_COUNT) throw new DeviceException(DeviceException.RANGE, "registers past 2999");
                Snapshot s = live();
                int k = s.tags().size();
                double[] v = new double[k], lo = new double[k], hi = new double[k];
                for (int i = 0; i < k; i++) {
                    DeviceTag t = s.tags().get(i);
                    v[i] = t.numeric(s.channels());
                    lo[i] = t.lo();
                    hi[i] = t.hi();
                }
                List<Integer> out = new ArrayList<>(n);
                for (int i = 0; i < n; i++) out.add(ModbusMap.inputRegister(addr + i, v, lo, hi));
                yield new Object[] {out};
            }
            case "readDiscreteInputs" -> {
                admit(c, 4);
                int addr = LuaArgs.integer(a, 0, 0, 1999);
                int n = LuaArgs.integer(a, 1, 1, ModbusMap.MAX_BITS);
                if (addr + n > 2000) throw new DeviceException(DeviceException.RANGE, "discrete inputs past 1999");
                Snapshot s = live();
                List<DeviceTag> discrete = new ArrayList<>();
                for (DeviceTag t : s.tags()) if (DeviceTag.DISCRETE.equals(t.kind())) discrete.add(t);
                List<Boolean> out = new ArrayList<>(n);
                for (int i = addr; i < addr + n; i++) {
                    if (i < ModbusMap.QUALITY_BASE) out.add(i < discrete.size() && discrete.get(i).numeric(s.channels()) > 0.5);
                    else out.add(i - ModbusMap.QUALITY_BASE < s.tags().size() && s.maintenance());
                }
                yield new Object[] {out};
            }
            case "readCoils" -> {
                admit(c, 4);
                LuaArgs.integer(a, 0, 0, 1999);
                int n = LuaArgs.integer(a, 1, 1, ModbusMap.MAX_BITS);
                // катушки — кнопки щита: команда без фиксации, читается всегда «отпущена»
                yield new Object[] {new ArrayList<>(Collections.nCopies(n, Boolean.FALSE))};
            }
            case "writeCoil" -> {
                admit(c, 8);
                int addr = LuaArgs.integer(a, 0, 0, 1999);
                if (!LuaArgs.bool(a, 1)) throw new DeviceException(DeviceException.RANGE, "coils are momentary panel buttons: write true");
                List<Button> allowed = allowedButtons(live());
                if (addr >= allowed.size()) throw new DeviceException(DeviceException.RANGE, "no coil " + addr + " (see listCommands)");
                yield new Object[] {pressById(c, allowed.get(addr).id())};
            }
            case "readHoldingRegisters", "writeRegister" -> {
                admit(c, method.startsWith("read") ? 4 : 8);
                throw new DeviceException(DeviceException.RANGE, "this device has no holding registers: setpoints are panel buttons");
            }
            default -> throw new DeviceException(DeviceException.ARG, "no method " + method);
        };
    }

    private static List<Button> allowedButtons(Snapshot s) {
        List<Button> out = new ArrayList<>();
        for (Button b : s.buttons()) if (!b.hardwareOnly()) out.add(b);
        return out;
    }

    private Map<String, Object> deviceInfo() {
        Map<String, Object> m = new LinkedHashMap<>();
        BlockPos p = be.getBlockPos();
        m.put("id", deviceId);
        m.put("label", deviceId);
        m.put("type", profile.type);
        m.put("subtype", profile.subtype);
        m.put("block", blockId);
        m.put("station", "");
        Map<String, Object> pos = new LinkedHashMap<>();
        pos.put("x", p.getX());
        pos.put("y", p.getY());
        pos.put("z", p.getZ());
        m.put("pos", pos);
        m.put("firmware", 1);
        return m;
    }

    private Map<String, Object> state(Snapshot s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", s.maintenance() ? "MAINTENANCE" : "REMOTE");
        Map<String, Object> values = new LinkedHashMap<>();
        for (DeviceTag t : s.tags()) values.put(t.name(), t.value(s.channels()));
        m.put("values", values);
        m.put("quality", s.quality());
        m.put("t", s.simTime());
        return m;
    }

    private int watch(Client c, Object[] a) throws DeviceException {
        Snapshot s = live();
        String name = LuaArgs.string(a, 0);
        DeviceTag t = tag(s, name);
        if (DeviceTag.CURVE.equals(t.kind())) throw new DeviceException(DeviceException.ARG, "a curve tag cannot be watched");
        double lo = LuaArgs.number(a, 1), hi = LuaArgs.number(a, 2), db = LuaArgs.finite(a, 3);
        double on = LuaArgs.optFinite(a, 4, 0), off = LuaArgs.optFinite(a, 5, 0);
        SetpointWatch w;
        try {
            w = new SetpointWatch(lo, hi, db, on, off);
        } catch (IllegalArgumentException e) {
            throw new DeviceException(DeviceException.RANGE, e.getMessage());
        }
        ClientState cs = clients.get(c.key());
        if (cs == null) throw new DeviceException(DeviceException.ACCESS, "computer is not attached");
        if (cs.watches.size() >= MAX_WATCHES) throw new DeviceException(DeviceException.RATE, "more than " + MAX_WATCHES + " watches on this device");
        int id = cs.nextWatch.getAndIncrement();
        cs.watches.put(id, new Watch(id, name, w));
        return id;
    }

    private Map<String, Object> commandStatus(long id) throws DeviceException {
        long clock = DeviceHub.clock();
        CommandTickets.Ticket t;
        synchronized (lock) {
            t = tickets.get(id, clock);
        }
        if (t == null) throw new DeviceException(DeviceException.ARG, "unknown or expired ticket " + id);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("state", t.state().name());
        if (t.error() != null) m.put("err", t.error());
        Snapshot s = snapshot;
        double now = s == null ? 0 : s.simTime();
        m.put("t", now - Math.max(0, clock - t.tick()) / TokenBucket.TICKS_PER_SECOND);
        return m;
    }

    private long pressById(Client c, int id) throws DeviceException {
        Snapshot s = live();
        if (!(be instanceof PanelHost)) throw new DeviceException(DeviceException.ARG, "this device has no panel buttons");
        Button b = null;
        for (Button x : s.buttons()) if (x.id() == id) b = x;
        if (b == null) throw new DeviceException(DeviceException.BUSY, "the panel does not show this button now");
        if (b.hardwareOnly()) throw new DeviceException(DeviceException.HARDWARE_ONLY, b.name() + " is done only at the panel, by hand");
        return submit(c, "button:" + id, target -> {
            if (!(target instanceof PanelHost host) || !(target.getLevel() instanceof ServerLevel sl))
                throw new DeviceException(DeviceException.UNLOADED, "device is not loaded");
            host.onPanelButton(FakePlayerFactory.getMinecraft(sl), id);
        });
    }

    /** Принять команду: права, частота, квитанция; исполнение — на ближайшем тике сервера. */
    private long submit(Client c, String channel, DeviceProfile.Action action) throws DeviceException {
        Snapshot s = live();
        if (s.maintenance()) throw new DeviceException(DeviceException.ACCESS, "MAINTENANCE (locked out)");
        if (!clients.containsKey(c.key())) throw new DeviceException(DeviceException.ACCESS, "computer is not attached");
        long clock = DeviceHub.clock();
        List<CommandTickets.Transition> superseded = new ArrayList<>();
        long id;
        synchronized (lock) {
            if (!commandBucket.tryTake(clock, 1))
                throw new DeviceException(DeviceException.RATE, "more than " + (int) COMMANDS_PER_SECOND + " commands a second to one device");
            id = tickets.submit(channel, clock, superseded);
        }
        if (id < 0) throw new DeviceException(DeviceException.RATE, "too many open tickets");
        owners.put(id, c.key());
        for (CommandTickets.Transition tr : superseded) {
            Object owner = owners.remove(tr.ticket().id());
            if (owner != null) outbox.add(new Outgoing(owner, tr.ticket()));
        }
        queue.add(new Pending(id, channel, action, c.key()));
        return id;
    }
}
