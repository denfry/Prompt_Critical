package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.binding.NbtStateTrees;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.sim.alarm.Alarm;
import dev.denfry.promptcritical.sim.alarm.AlarmAccessException;
import dev.denfry.promptcritical.sim.alarm.AlarmDefinition;
import dev.denfry.promptcritical.sim.alarm.AlarmEngine;
import dev.denfry.promptcritical.sim.alarm.AlarmEvent;
import dev.denfry.promptcritical.sim.alarm.AlarmKind;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import dev.denfry.promptcritical.sim.alarm.AlarmStatistics;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Светосигнальное табло {@code annunciator_panel} (03c §3c.2.7) и сервер сигнализации {@code pc_annunciator}
 * (03b §3b.2.14): собственный {@link AlarmEngine} над машинами в {@value #RADIUS} блоках. Раз в секунду табло снимает
 * щиты машин ({@link PanelHost}) и заводит их встроенные сигнализации ({@link AlarmPoints}); программа CC добавляет свои
 * ({@code SOFT} и по тегам) и управляет подавлением. Квитирование и состояния живут в блоке, а не в Lua: переживают
 * перезагрузку компьютера.
 *
 * <p>Упрощение против 03c §3c.2: элементы не привязываются инструментом {@code wiring_tool} — табло видит все щиты в
 * радиусе (это «аппаратная» связь проводами длиной до {@value #RADIUS} м, меньше {@code scada.max_virtual_wire_m = 64}).
 * 12 окон 4×3 привязываются к сигнализациям {@code setWindow} или сами — свободное окно берёт следующую
 * неназначенную сигнализацию по приоритету и номеру и держит её, пока сигнализация есть (гравировка окна не прыгает).
 * Кнопки «КВИТ», «ЗВУК», «ПРОВ» — на щите табло; Shift + ПКМ — «КВИТ». Пока звучит сирена (неквитированная P1–P3 после
 * гашения), табло даёт редстоун 15 в стену и включает {@code alarm_horn} рядом.
 */
public class AnnunciatorBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    public static final int RADIUS = 16, WINDOWS = 12, SCAN_TICKS = 20, LAMP_TEST_TICKS = 60;
    /** Сколько опросов подряд встроенная сигнализация может не находиться (машина снесена, чанк выгружен). */
    public static final int MISSING_SCANS = 30;

    /** Отказ табло: Lua получает это сообщение ошибкой вызова. */
    public static final class AnnunciatorException extends RuntimeException {
        public AnnunciatorException(String message) {
            super(message);
        }
    }

    private final AlarmEngine engine = new AlarmEngine(false);
    /** Окна, привязанные явно ({@code setWindow}), и окна, взятые автоматически; 0 — пусто. */
    private final int[] bound = new int[WINDOWS], auto = new int[WINDOWS];
    private final Map<Integer, Integer> refs = new HashMap<>(), missing = new HashMap<>();
    private final Map<String, Double> values = new HashMap<>();
    private final Map<Object, Consumer<AlarmEvent>> sinks = new ConcurrentHashMap<>();
    private long lampTestUntil;
    private int ticks, machines;
    private TokenBucket calls;

    public AnnunciatorBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.ANNUNCIATOR.get(), pos, state);
        engine.setListener(e -> sinks.values().forEach(s -> s.accept(e)));
    }

    public AlarmEngine engine() {
        return engine;
    }

    double nowS() {
        return level == null ? 0 : level.getGameTime() / 20.0;
    }

    /** Подписка на события движка (периферия CC при {@code attach}); {@code null} — отписка. */
    public void sink(Object key, @Nullable Consumer<AlarmEvent> sink) {
        if (sink == null) sinks.remove(key);
        else sinks.put(key, sink);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) return;
        if (++ticks % SCAN_TICKS != 0) return;
        scan();
        engine.step(nowS(), tag -> values.getOrDefault(tag, Double.NaN));
        assignWindows();
        BlockState s = getBlockState();
        boolean horn = engine.hornPriority() > 0;
        if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED) != horn)
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, horn), Block.UPDATE_ALL);
        if (ticks % 200 == 0) setChanged();
    }

    /** Опрос щитов: значения и встроенные сигнализации машин в радиусе. */
    void scan() {
        values.keySet().removeIf(k -> k.indexOf('@') >= 0);
        Set<Integer> seen = new HashSet<>();
        machines = 0;
        for (BlockEntity be : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), BlockEntity.class)) {
            if (!(be instanceof PanelHost host) || be == this || be instanceof AnnunciatorBlockEntity) continue;
            PanelLayout layout = host.panelLayout();
            int[] ch = new int[layout.channels];
            host.readPanel(ch);
            String type = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
            String machine = TelemetryGatewayBlockEntity.id(type, be.getBlockPos());
            // все телеизмерения и телесигналы — для сигнализаций программы по любому тегу машины
            for (TelemetryPoint tp : TelemetryPoint.of(layout)) {
                Object v = tp.value(ch);
                if (v instanceof Double d) values.put(machine + "/" + tp.name(), d);
                else if (v instanceof Boolean b) values.put(machine + "/" + tp.name(), b ? 1.0 : 0.0);
            }
            List<AlarmPoints.Point> points = AlarmPoints.of(machine, layout, ch);
            if (!points.isEmpty()) machines++;
            for (AlarmPoints.Point p : points) {
                int id = engine.upsert(p.definition());
                seen.add(id);
                refs.put(id, p.ref());
                values.put(p.definition().tag(), p.value());
            }
        }
        for (Alarm a : new ArrayList<>(engine.alarms())) {
            if (a.program() || seen.contains(a.id())) {
                missing.remove(a.id());
                continue;
            }
            int n = missing.merge(a.id(), 1, Integer::sum);
            if (a.state() == AlarmState.NORMAL || n >= MISSING_SCANS) {
                engine.remove(a.id());
                refs.remove(a.id());
                missing.remove(a.id());
            }
        }
    }

    private void assignWindows() {
        Set<Integer> used = new HashSet<>();
        for (int i = 0; i < WINDOWS; i++) {
            if (bound[i] != 0 && engine.get(bound[i]) == null) bound[i] = 0;
            if (auto[i] != 0 && (bound[i] != 0 || engine.get(auto[i]) == null)) auto[i] = 0;
            if (bound[i] != 0) used.add(bound[i]);
            if (auto[i] != 0) used.add(auto[i]);
        }
        List<Alarm> free = new ArrayList<>();
        for (Alarm a : engine.alarms()) if (!used.contains(a.id())) free.add(a);
        free.sort((x, y) -> x.definition().priority() != y.definition().priority()
                ? Integer.compare(x.definition().priority(), y.definition().priority()) : Integer.compare(x.id(), y.id()));
        int k = 0;
        for (int i = 0; i < WINDOWS && k < free.size(); i++) if (bound[i] == 0 && auto[i] == 0) auto[i] = free.get(k++).id();
    }

    /** Сигнализация окна {@code i}, 0 — окно пусто. */
    public int window(int i) {
        return bound[i] != 0 ? bound[i] : auto[i];
    }

    /** Ссылка на прибор щита ({@link AlarmPoints#ref}) у встроенной сигнализации, −1 — нет. */
    public int refOf(int alarmId) {
        Integer r = refs.get(alarmId);
        return r == null ? -1 : r;
    }

    public int machines() {
        return machines;
    }

    // ------------------------------------------------------------------ кнопки табло и оператор

    public int acknowledgeAll() {
        return engine.acknowledgeAll(null, nowS());
    }

    public void silence() {
        engine.silence(nowS());
        BlockState s = getBlockState();
        if (s.hasProperty(BlockStateProperties.POWERED) && s.getValue(BlockStateProperties.POWERED))
            level.setBlock(worldPosition, s.setValue(BlockStateProperties.POWERED, false), Block.UPDATE_ALL);
    }

    public void lampTest() {
        lampTestUntil = level == null ? 0 : level.getGameTime() + LAMP_TEST_TICKS;
    }

    boolean lampTesting() {
        return level != null && level.getGameTime() < lampTestUntil;
    }

    /** Окна, не уместившиеся на табло: показанные сигнализации вне окон. */
    int hiddenAnnunciated() {
        Set<Integer> shown = new HashSet<>();
        for (int i = 0; i < WINDOWS; i++) shown.add(window(i));
        int n = 0;
        for (Alarm a : engine.alarms()) if (a.state().annunciated() && !shown.contains(a.id())) n++;
        return n;
    }

    // ------------------------------------------------------------------ pc_annunciator (03b §3b.2.14)

    private void admit() {
        if (calls == null) calls = new TokenBucket(ServerConfig.CC_TOKEN_RATE.get(), ServerConfig.CC_TOKEN_BURST.get());
        if (!calls.tryTake(level == null ? 0 : level.getGameTime(), 1)) throw new AnnunciatorException("Annunciator is busy: too many requests");
    }

    private Alarm alarm(int id) {
        Alarm a = engine.get(id);
        if (a == null) throw new AnnunciatorException("No alarm " + id);
        return a;
    }

    /** Описание сигнализации для Lua ({@code getAlarms}, события). */
    public Map<String, Object> describe(Alarm a) {
        AlarmDefinition d = a.definition();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("tag", d.tag());
        m.put("kind", d.kind().name());
        m.put("priority", d.priority());
        m.put("state", a.state().name());
        m.put("message", d.message());
        m.put("action", d.operatorAction());
        if (!Double.isNaN(a.value())) m.put("value", a.value());
        if (d.kind().analog()) m.put("limit", d.limit());
        if (!Double.isNaN(a.tRaise())) m.put("tRaise", a.tRaise());
        if (!Double.isNaN(a.tAck())) m.put("tAck", a.tAck());
        m.put("count", a.count());
        m.put("group", d.group());
        m.put("active", a.active());
        m.put("firstOut", a.firstOut());
        m.put("program", a.program());
        return m;
    }

    public List<Map<String, Object>> getAlarms(@Nullable Map<?, ?> filter) {
        admit();
        String state = filter == null || filter.get("state") == null ? null : filter.get("state").toString();
        String group = filter == null || filter.get("group") == null ? null : filter.get("group").toString();
        int prio = filter != null && filter.get("priority") instanceof Number n ? n.intValue() : 0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Alarm a : engine.ranked()) {
            if (state != null && !a.state().name().equals(state)) continue;
            if (group != null && !a.definition().group().equals(group)) continue;
            if (prio != 0 && a.definition().priority() != prio) continue;
            out.add(describe(a));
        }
        return out;
    }

    /** Квитировать {@code id} или {@code "ALL"} (с группой); число квитированных. */
    public int acknowledge(Object target, @Nullable String group) {
        admit();
        if (target instanceof Number n) return engine.acknowledge(n.intValue(), nowS()) ? 1 : 0;
        if ("ALL".equals(String.valueOf(target))) return engine.acknowledgeAll(group, nowS());
        throw new AnnunciatorException("acknowledge expects an alarm id or \"ALL\"");
    }

    public void silenceLua() {
        admit();
        silence();
    }

    public boolean shelve(int id, double seconds) {
        admit();
        alarm(id);
        try {
            return engine.shelve(id, seconds, nowS());
        } catch (AlarmAccessException | IllegalArgumentException e) {
            throw new AnnunciatorException(e.getMessage());
        }
    }

    public boolean unshelve(int id) {
        admit();
        return engine.unshelve(id, nowS());
    }

    /** {@code defineAlarm(spec)}: {@code spec = {tag?, message, priority, kind, limit?, deadband?, onDelay?, offDelay?, group?, action?}}. */
    public int defineAlarm(Map<?, ?> spec) {
        admit();
        try {
            AlarmKind kind = AlarmKind.valueOf(String.valueOf(spec.containsKey("kind") ? spec.get("kind") : "SOFT").toUpperCase(Locale.ROOT));
            String tag = spec.get("tag") == null ? "lua" : spec.get("tag").toString();
            if (kind != AlarmKind.SOFT && !values.containsKey(tag))
                throw new AnnunciatorException("Unknown tag " + tag + ": use a machine/point tag from getTags()");
            AlarmDefinition d = new AlarmDefinition(0, tag, kind, num(spec, "priority", 3) > 0 ? (int) num(spec, "priority", 3) : 3,
                    num(spec, "limit", kind.analog() ? Double.NaN : 0), num(spec, "deadband", 0), num(spec, "onDelay", 0), num(spec, "offDelay", 0),
                    Boolean.TRUE.equals(spec.get("latching")), str(spec, "group"), str(spec, "message"), str(spec, "action"));
            return engine.define(d, true);
        } catch (IllegalArgumentException e) {
            throw new AnnunciatorException("Bad alarm spec: " + e.getMessage());
        }
    }

    private static double num(Map<?, ?> m, String k, double def) {
        return m.get(k) instanceof Number n ? n.doubleValue() : def;
    }

    private static String str(Map<?, ?> m, String k) {
        return m.get(k) == null ? "" : m.get(k).toString();
    }

    public boolean deleteAlarm(int id) {
        admit();
        try {
            return engine.deleteProgram(id);
        } catch (AlarmAccessException e) {
            throw new AnnunciatorException(e.getMessage());
        }
    }

    public void raise(int id, double value) {
        admit();
        try {
            engine.raiseSoft(id, value);
        } catch (IllegalArgumentException e) {
            throw new AnnunciatorException(e.getMessage());
        }
    }

    public void clear(int id) {
        admit();
        try {
            engine.clearSoft(id);
        } catch (IllegalArgumentException e) {
            throw new AnnunciatorException(e.getMessage());
        }
    }

    public void setSuppression(String group, boolean active) {
        admit();
        try {
            engine.setSuppression(group, active);
        } catch (IllegalArgumentException e) {
            throw new AnnunciatorException(e.getMessage());
        }
    }

    public Map<String, Object> getStatistics() {
        admit();
        AlarmStatistics s = engine.statistics();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rate10min", s.rate10min());
        m.put("peak10min", s.peak10min());
        m.put("standing", s.standing());
        m.put("stale", s.stale());
        List<Map<String, Object>> chatter = new ArrayList<>();
        s.chattering().forEach((id, n) -> chatter.add(Map.of("id", id, "count", n)));
        m.put("chattering", chatter);
        m.put("flood", s.flood());
        if (s.flood()) m.put("floodSince", s.floodSince());
        m.put("shelved", s.shelved());
        m.put("suppressed", s.suppressed());
        m.put("unacked", s.unacked());
        m.put("active", s.active());
        m.put("alarms", engine.size());
        return m;
    }

    /** Теги машин последнего опроса и их значения (расширение 03b: откуда брать теги для {@code defineAlarm}). */
    public Map<String, Object> getTags() {
        admit();
        return new java.util.TreeMap<>(values);
    }

    /**
     * Значения тегов одним вызовом ({@code readMany}, 03c §3c.4.2: {@code pcui} читает устройство пакетно); нет тега или
     * значения — {@code nil} в ответе.
     */
    public Map<String, Object> readMany(List<?> tags) {
        admit();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object o : tags) {
            Double v = values.get(String.valueOf(o));
            if (v != null && !v.isNaN()) out.put(String.valueOf(o), v);
        }
        out.put("_t", nowS());
        return out;
    }

    /**
     * Экран {@code pcui} сообщает о себе (расширение 03b): уровень ISA-101 страницы и число различных тегов, которые она
     * читает. Уровень — тот, с которым страница объявлена в {@code app:page(id, {level = 1})} (03c §3c.4.2,
     * §3c.5.1). Страница L1 с 20 тегами и больше — достижение {@code hmi} (11 §11.2.2) игрокам в радиусе смены.
     */
    public void reportScreen(int screenLevel, int tags) {
        admit();
        if (screenLevel == 1 && level instanceof ServerLevel sl)
            dev.denfry.promptcritical.mod.content.SimAchievements.nearby(sl, worldPosition, dev.denfry.promptcritical.mod.content.SimAchievements.HMI,
                    "tags", tags);
    }

    /** Окно {@code n} (1–12) — сигнализации {@code alarmId}; 0 — снова автоматически. */
    public void setWindow(int n, int alarmId) {
        admit();
        if (n < 1 || n > WINDOWS) throw new AnnunciatorException("Window must be 1.." + WINDOWS);
        if (alarmId != 0) alarm(alarmId);
        for (int i = 0; i < WINDOWS; i++) if (alarmId != 0 && bound[i] == alarmId) bound[i] = 0;
        bound[n - 1] = alarmId;
        assignWindows();
        setChanged();
    }

    public void lampTestLua() {
        admit();
        lampTest();
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return AnnunciatorPanels.ANNUNCIATOR;
    }

    @Override
    public Component panelTitle() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public BlockPos panelPos() {
        return worldPosition;
    }

    @Override
    public boolean panelValid(Player player) {
        return PanelHost.near(this, player);
    }

    @Override
    public void readPanel(int[] out) {
        Arrays.fill(out, 0);
        boolean test = lampTesting();
        for (int i = 0; i < WINDOWS; i++) {
            Alarm a = engine.get(window(i));
            int flags = test ? AnnunciatorPanels.F_TEST : 0;
            if (a != null) {
                Integer ref = refs.get(a.id());
                out[AnnunciatorPanels.W0 + 2 * i] = ref != null && ref >= 0 ? ref + 1 : -a.id();
                flags |= a.state().ordinal() | a.definition().priority() << 4 | AnnunciatorPanels.F_PRESENT;
                if (a.firstOut()) flags |= AnnunciatorPanels.F_FIRST_OUT;
            }
            out[AnnunciatorPanels.W0 + 2 * i + 1] = flags;
        }
        AlarmStatistics s = engine.statistics();
        out[AnnunciatorPanels.UNACKED] = s.unacked();
        out[AnnunciatorPanels.ACTIVE] = s.active();
        out[AnnunciatorPanels.SHELVED] = s.shelved();
        out[AnnunciatorPanels.SUPPRESSED] = s.suppressed();
        out[AnnunciatorPanels.RATE] = s.rate10min();
        out[AnnunciatorPanels.FLAGS] = (s.flood() ? 1 : 0) | engine.hornPriority() << 1;
        out[AnnunciatorPanels.TOTAL] = engine.size();
        out[AnnunciatorPanels.MACHINES] = machines;
        out[AnnunciatorPanels.MORE] = hiddenAnnunciated();
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        switch (id) {
            case AnnunciatorPanels.B_ACK -> acknowledgeAll();
            case AnnunciatorPanels.B_SILENCE -> silence();
            case AnnunciatorPanels.B_TEST -> lampTest();
            default -> {
            }
        }
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public InteractionResult use(Player player) {
        int n = acknowledgeAll();
        player.displayClientMessage(AnnunciatorPanels.ACKED_MSG.get(n, engine.size(), machines), true);
        setChanged();
        return InteractionResult.SUCCESS;
    }

    @Override
    @Nullable
    public IItemHandler items(@Nullable Direction side) {
        return null;
    }

    @Override
    public boolean accepts(ItemStack stack) {
        return false;
    }

    @Override
    public ItemInteractionResult useItem(Player player, InteractionHand hand, ItemStack stack) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        MutableStateTree tree = new MutableStateTree();
        engine.save(new TreeStateWriter(tree));
        tag.put("engine", NbtStateTrees.toNbt(tree));
        tag.putIntArray("bound", bound);
        tag.putIntArray("auto", auto);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("engine")) engine.load(new TreeStateReader(NbtStateTrees.fromNbt(tag.getCompound("engine"))));
        int[] b = tag.getIntArray("bound"), a = tag.getIntArray("auto");
        for (int i = 0; i < WINDOWS; i++) {
            bound[i] = i < b.length ? b[i] : 0;
            auto[i] = i < a.length ? a[i] : 0;
        }
    }
}
