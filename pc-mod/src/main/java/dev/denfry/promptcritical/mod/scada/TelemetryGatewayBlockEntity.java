package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.config.ServerConfig;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.sim.telemetry.ScanCycle;
import dev.denfry.promptcritical.sim.telemetry.TokenBucket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
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
        scan(tick);
        cycle.scanned(tick);
        BlockState st = getBlockState();
        boolean lit = !snapshots.isEmpty();
        if (st.getValue(BlockStateProperties.LIT) != lit) level.setBlock(worldPosition, st.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_ALL);
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
        if (!commands.tryTake(now(), 1)) throw new TelemetryException("Telecontrol channel is busy: wait for the previous command");
        if (!(level.getBlockEntity(s.pos()) instanceof PanelHost host) || !(level instanceof ServerLevel sl))
            throw new TelemetryException("Machine " + id + " is gone");
        host.onPanelButton(FakePlayerFactory.getMinecraft(sl), buttonId);
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
        player.displayClientMessage(ScadaText.msg("status", snapshots.size(), RADIUS, ScadaText.cc()), true);
        return InteractionResult.SUCCESS;
    }
}
