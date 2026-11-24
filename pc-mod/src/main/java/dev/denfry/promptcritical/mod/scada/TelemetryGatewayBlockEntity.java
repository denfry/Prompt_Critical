package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.sim.telemetry.RtuPower;
import dev.denfry.promptcritical.sim.telemetry.ScanCycle;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Шлюз телемеханики — контролируемый пункт (КП, УСО) у машин: опрашивает местные щиты ({@link PanelHost}) в
 * {@link #RADIUS} блоках раз в {@link #SCAN_TICKS} тиков и держит срез их приборов; диспетчерский компьютер CC:
 * Tweaked читает срез через периферию ({@code mod.scada.cc}) и подаёт команды телеуправления — нажатия кнопок
 * щитов, ровно то, что игрок может сделать рукой.
 *
 * <p>Защита сервера: любой вызов тратит токен общего запаса шлюза ({@code cc.token_rate}/{@code token_burst} в
 * конфиге сервера), команды — ещё и свой запас {@link #COMMANDS_PER_SECOND} в секунду (телеуправление идёт по одной
 * команде с квитированием). Чтение не трогает машины: оно отдаёт срез последнего цикла опроса и его возраст.
 * Логика шлюза не знает о CC — периферия только переводит вызовы, поэтому мод работает и без CC.
 *
 * <p>Питание (12 §12.5.1, {@link RtuPower}): КП считается запитанным от сети объекта, пока рядом нет РИТЭГа. КП с
 * РИТЭГом вплотную — автономный, удалённой скважины: он берёт у РИТЭГа {@link RtuPower#DEMAND_W} Вт и гаснет, когда
 * РИТЭГ их не даёт. КП на РИТЭГе, минуту без перерыва опрашивающий устье или станок-качалку, — «Вечная батарейка» (11).
 */
public final class TelemetryGatewayBlockEntity extends DeviceBlockEntity implements FoundryDevice {
    public static final int RADIUS = 8, SCAN_TICKS = 20;
    public static final double COMMANDS_PER_SECOND = 2, COMMAND_BURST = 4;

    /** Отказ шлюза: Lua получает это сообщение ошибкой вызова. */
    public static final class TelemetryException extends RuntimeException {
        public TelemetryException(String message) {
            super(message);
        }
    }

    private record Snapshot(String type, BlockPos pos, PanelLayout layout, List<TelemetryPoint> points, int[] channels, long tick) {
    }

    private final ScanCycle cycle = new ScanCycle(SCAN_TICKS);
    private final Map<String, Snapshot> snapshots = new LinkedHashMap<>();
    private TokenBucket calls, commands;
    private final RtuPower power = new RtuPower();
    private boolean offGrid;

    public TelemetryGatewayBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.TELEMETRY_GATEWAY.get(), pos, state);
    }

    private long now() {
        return level == null ? 0 : level.getGameTime();
    }

    /** Идентификатор машины для Lua: {@code тип@x,y,z}. */
    public static String id(String type, BlockPos pos) {
        return type + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel)) return;
        long tick = now();
        if (!cycle.due(tick)) return;
        offGrid = dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity.adjacent(level, worldPosition);
        double pu = offGrid ? dev.denfry.promptcritical.mod.isotopes.RtgBlockEntity.supplyPu(level, worldPosition, RtuPower.DEMAND_W, tick)
                : dev.denfry.promptcritical.mod.grid.MachinePower.supply(level, worldPosition, RtuPower.DEMAND_W);
        boolean wasEverlasting = power.everlasting();
        if (power.step(SCAN_TICKS / 20.0, offGrid, pu, pollsWell())) {
            scan(tick);
        } else {
            snapshots.clear(); // без питания КП молчит: диспетчер видит пустой срез
        }
        cycle.scanned(tick);
        if (power.everlasting() && !wasEverlasting) setChanged();
        if (power.everlasting()) dev.denfry.promptcritical.mod.content.SimAchievements.once(this,
                dev.denfry.promptcritical.mod.content.SimAchievements.EVERLASTING, "rtu_w", RtuPower.DEMAND_W);
        BlockState st = getBlockState();
        boolean lit = !snapshots.isEmpty();
        if (st.getValue(BlockStateProperties.LIT) != lit) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
    }

    /** На прошлом опросе среди щитов было устье скважины или станок-качалка. */
    private boolean pollsWell() {
        for (Snapshot s : snapshots.values()) if (WELL_TYPES.contains(s.type())) return true;
        // КП только что включился: срез пуст — смотрим щиты рядом без опроса
        if (snapshots.isEmpty())
            for (BlockEntity be : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), BlockEntity.class))
                if (be instanceof PanelHost && WELL_TYPES.contains(BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath()))
                    return true;
        return false;
    }

    /** Щиты скважин для «Вечной батарейки». */
    public static final java.util.Set<String> WELL_TYPES = java.util.Set.of("wellhead", "pumpjack");

    /** КП питается от РИТЭГа вплотную. */
    public boolean offGrid() {
        return offGrid;
    }

    /** Питание КП (12 §12.5.1). */
    public RtuPower power() {
        return power;
    }

    /** Цикл опроса: найти щиты рядом и снять показания всех приборов. */
    void scan(long tick) {
        snapshots.clear();
        for (BlockEntity be : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), BlockEntity.class)) {
            if (!(be instanceof PanelHost host) || be == this) continue;
            PanelLayout layout = host.panelLayout();
            int[] ch = new int[layout.channels];
            host.readPanel(ch);
            String type = BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).getPath();
            snapshots.put(id(type, be.getBlockPos()), new Snapshot(type, be.getBlockPos().immutable(), layout, TelemetryPoint.of(layout), ch, tick));
        }
    }

    private void admit() {
        if (calls == null) calls = new TokenBucket(ServerConfig.CC_TOKEN_RATE.get(), ServerConfig.CC_TOKEN_BURST.get());
        if (!calls.tryTake(now(), 1)) throw new TelemetryException("Telemetry gateway is busy: too many requests");
    }

    private Snapshot snapshot(String id) {
        Snapshot s = snapshots.get(id);
        if (s == null) throw new TelemetryException("No machine " + id + " on this gateway");
        return s;
    }

    /** Машины на шлюзе: {@code id}, {@code type}, координаты и щит. */
    public List<Map<String, Object>> machines() {
        admit();
        List<Map<String, Object>> out = new ArrayList<>();
        snapshots.forEach((id, s) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("type", s.type());
            m.put("panel", s.layout().id);
            m.put("x", s.pos().getX());
            m.put("y", s.pos().getY());
            m.put("z", s.pos().getZ());
            out.add(m);
        });
        return out;
    }

    /** Приборы и кнопки щита машины. */
    public Map<String, Object> describe(String id) {
        admit();
        Snapshot s = snapshot(id);
        List<Map<String, Object>> points = new ArrayList<>();
        for (TelemetryPoint p : s.points()) points.add(p.describe());
        List<Map<String, Object>> buttons = new ArrayList<>();
        for (PanelLayout.Element e : s.layout().elements) {
            if (!(e instanceof PanelLayout.Button b)) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", PanelText.shortKey(b.label()));
            m.put("label", PanelText.english(b.label()));
            buttons.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("points", points);
        out.put("buttons", buttons);
        return out;
    }

    /** Срез приборов машины по именам точек и его возраст {@code _age}, с. */
    public Map<String, Object> read(String id) {
        admit();
        Snapshot s = snapshot(id);
        Map<String, Object> out = new LinkedHashMap<>();
        for (TelemetryPoint p : s.points()) out.put(p.name(), p.value(s.channels()));
        out.put("_age", cycle.ageSeconds(now()));
        return out;
    }

    /** Команда телеуправления: нажать кнопку {@code button} на щите машины. */
    public void press(String id, String button) {
        admit();
        if (commands == null) commands = new TokenBucket(COMMANDS_PER_SECOND, COMMAND_BURST);
        Snapshot s = snapshot(id);
        int buttonId = -1;
        for (PanelLayout.Element e : s.layout().elements)
            if (e instanceof PanelLayout.Button b && PanelText.shortKey(b.label()).equals(button)) buttonId = b.id();
        if (buttonId < 0) throw new TelemetryException("No button " + button + " on " + id);
        if (dev.denfry.promptcritical.mod.scada.device.DeviceRuntime.hardwareKey(button))
            throw new TelemetryException(button + " is done only at the panel, by hand");
        if (!commands.tryTake(now(), 1)) throw new TelemetryException("Telecontrol channel is busy: wait for the previous command");
        if (!(level.getBlockEntity(s.pos()) instanceof PanelHost host) || !(level instanceof ServerLevel sl))
            throw new TelemetryException("Machine " + id + " is gone");
        host.onPanelButton(FakePlayerFactory.getMinecraft(sl), buttonId);
    }

    /** Машина последнего цикла опроса для терминала оператора (16 §16.3.4): щит, показания и возраст среза в тиках. */
    public record Station(String id, String type, BlockPos pos, PanelLayout layout, int[] channels, long ageTicks) {
    }

    /** Срез всех машин шлюза без расхода токенов: терминал оператора — часть мода, а не вызов Lua. */
    public List<Station> stations() {
        List<Station> out = new ArrayList<>();
        long now = now();
        snapshots.forEach((id, s) -> out.add(new Station(id, s.type(), s.pos(), s.layout(), s.channels().clone(), now - s.tick())));
        return out;
    }

    /**
     * Телеуправление с терминала оператора (16 §16.3.4): кнопка {@code buttonId} щита машины нажимается от имени
     * игрока, так что права и блокировки щита 03c действуют как у рукояти. Аппаратные ключи — только рукой у щита.
     */
    public void pressAs(ServerPlayer player, String id, int buttonId) {
        if (commands == null) commands = new TokenBucket(COMMANDS_PER_SECOND, COMMAND_BURST);
        Snapshot s = snapshot(id);
        PanelLayout.Button button = null;
        for (PanelLayout.Element e : s.layout().elements)
            if (e instanceof PanelLayout.Button b && b.id() == buttonId) button = b;
        if (button == null) throw new TelemetryException("No button " + buttonId + " on " + id);
        if (dev.denfry.promptcritical.mod.scada.device.DeviceRuntime.hardwareKey(PanelText.shortKey(button.label())))
            throw new TelemetryException(PanelText.shortKey(button.label()) + " is done only at the panel, by hand");
        if (!commands.tryTake(now(), 1)) throw new TelemetryException("Telecontrol channel is busy: wait for the previous command");
        if (!(level.getBlockEntity(s.pos()) instanceof PanelHost host)) throw new TelemetryException("Machine " + id + " is gone");
        host.onPanelButton(player, buttonId);
    }

    /** Телеизмерение для верхнего уровня (сервер завода): машина, её тип, точка и значение последнего опроса. */
    public record Reading(String machine, String type, TelemetryPoint point, double value) {
    }

    /** Все телеизмерения последнего цикла опроса, без расхода токенов: это срез внутри мода, а не вызов Lua. */
    public List<Reading> analogReadings() {
        List<Reading> out = new ArrayList<>();
        snapshots.forEach((id, s) -> {
            for (TelemetryPoint p : s.points())
                if (TelemetryPoint.ANALOG.equals(p.kind()) && p.value(s.channels()) instanceof Double v) out.add(new Reading(id, s.type(), p, v));
        });
        return out;
    }

    /** Период опроса, с. */
    public double cycleSeconds() {
        return SCAN_TICKS / TokenBucket.TICKS_PER_SECOND;
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
    public InteractionResult use(Player player) {
        if (offGrid && !power.on())
            player.displayClientMessage(ScadaText.msg("rtg_dark", Math.round(RtuPower.DEMAND_W)), true);
        else if (offGrid)
            player.displayClientMessage(ScadaText.msg("rtg_status", snapshots.size(), RADIUS, Math.round(RtuPower.DEMAND_W)), true);
        else
            player.displayClientMessage(ScadaText.msg("status", snapshots.size(), RADIUS, ScadaText.cc()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("rtu_on", power.on());
        tag.putDouble("rtu_autonomous_s", power.autonomousS());
        tag.putBoolean("rtu_everlasting", power.everlasting());
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        power.restore(!tag.contains("rtu_on") || tag.getBoolean("rtu_on"), tag.getDouble("rtu_autonomous_s"),
                tag.getBoolean("rtu_everlasting"));
    }
}
