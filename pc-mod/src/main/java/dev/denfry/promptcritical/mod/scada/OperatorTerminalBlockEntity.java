package dev.denfry.promptcritical.mod.scada;

import static dev.denfry.promptcritical.mod.scada.OperatorTerminalPanels.*;

import dev.denfry.promptcritical.mod.block.PcProperties;
import dev.denfry.promptcritical.mod.blockentity.DeviceBlockEntity;
import dev.denfry.promptcritical.mod.foundry.FoundryDevice;
import dev.denfry.promptcritical.mod.ironworks.Ironworks;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayouts;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.Station;
import dev.denfry.promptcritical.mod.scada.TelemetryGatewayBlockEntity.TelemetryException;
import dev.denfry.promptcritical.mod.scada.device.DeviceRuntime;
import dev.denfry.promptcritical.mod.scada.device.DeviceTag;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import dev.denfry.promptcritical.sim.automation.historian.TagArchive;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Терминал оператора {@code operator_terminal} (16 §16.3.4, R58) — непрограммируемое рабочее место уровня A4 без CC:
 * Tweaked. Видит машины на шлюзах телемеханики в {@value #RADIUS} блоках, показывает по каждой типовой видеокадр
 * ({@link OperatorTerminalPanels}) с трендом из историана рядом и сводкой сигнализаций табло, и подаёт команды
 * телеуправления — те же кнопки щитов, что доступны Lua, от имени игрока. Скриптов нет: программируемым уровнем без CC
 * остаётся ПЛК (R33).
 */
public class OperatorTerminalBlockEntity extends DeviceBlockEntity implements FoundryDevice, PanelHost {
    /** Рабочее место с ЭЛТ, Вт. */
    public static final double POWER_W = 300;
    /** Радиус поиска КП, историана и табло, блоков. */
    public static final int RADIUS = 16;
    /** Полупериод мигания лампы тревоги, тиков. */
    static final int BLINK_TICKS = 10;

    private final int[] view = new int[CHANNELS];
    private String selectedId = "";
    private int page, trendTag;
    private boolean powered;
    private int ticks;
    /** Кнопки команд в столбцах кадра — номера кнопок щита выбранной машины. */
    private final int[] commandIds = new int[COMMANDS];

    public OperatorTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ScadaBlockEntities.OPERATOR_TERMINAL.get(), pos, state);
    }

    @Override
    public void tickDevice() {
        if (!(level instanceof ServerLevel) || ++ticks % BLINK_TICKS != 0) return;
        if (ticks % 20 == 0) {
            powered = dev.denfry.promptcritical.mod.grid.MachinePower.powered(level, worldPosition, POWER_W);
            refresh();
        }
        BlockState s = getBlockState();
        // неквитированная сигнализация: лампа на корпусе мигает, модели _on и _alarm сменяются
        boolean alarm = powered && view[UNACK] > 0 && ticks / BLINK_TICKS % 2 == 0;
        BlockState next = s;
        if (s.hasProperty(BlockStateProperties.POWERED)) next = next.setValue(BlockStateProperties.POWERED, powered);
        if (s.hasProperty(PcProperties.ALARM)) next = next.setValue(PcProperties.ALARM, alarm);
        if (next != s) level.setBlock(worldPosition, next, Block.UPDATE_CLIENTS);
    }

    /** Машины всех КП в радиусе, по порядку идентификаторов; одна машина на двух КП — один раз. */
    List<Station> stations() {
        List<Station> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (TelemetryGatewayBlockEntity rtu : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), TelemetryGatewayBlockEntity.class))
            for (Station s : rtu.stations()) if (seen.add(s.id())) out.add(s);
        out.sort(Comparator.comparing(Station::id));
        return out;
    }

    @Nullable
    private TelemetryGatewayBlockEntity gatewayOf(String id) {
        for (TelemetryGatewayBlockEntity rtu : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), TelemetryGatewayBlockEntity.class))
            for (Station s : rtu.stations()) if (s.id().equals(id)) return rtu;
        return null;
    }

    /** Пересобрать кадр: список, выбранная машина, тренд, сигнализации и команды. */
    void refresh() {
        Arrays.fill(view, 0);
        Arrays.fill(commandIds, -1);
        view[FLAGS] = powered ? F_POWERED : 0;
        if (!powered) return;
        List<Station> all = stations();
        view[COUNT] = all.size();
        int sel = -1;
        for (int i = 0; i < all.size(); i++) if (all.get(i).id().equals(selectedId)) sel = i;
        if (sel < 0 && !all.isEmpty()) {
            sel = 0;
            selectedId = all.get(0).id();
            trendTag = 0;
        }
        int pages = Math.max(1, (all.size() + LIST_ROWS - 1) / LIST_ROWS);
        page = Math.floorMod(page, pages);
        view[PAGE] = page;
        view[SELECTED] = sel;
        for (int r = 0; r < LIST_ROWS; r++) {
            int i = page * LIST_ROWS + r;
            if (i >= all.size()) break;
            Station s = all.get(i);
            view[ROW0 + 4 * r] = PanelLayouts.index(s.layout()) + 1;
            view[ROW0 + 4 * r + 1] = s.pos().getX();
            view[ROW0 + 4 * r + 2] = s.pos().getY();
            view[ROW0 + 4 * r + 3] = s.pos().getZ();
        }
        alarms();
        if (sel < 0) return;
        Station s = all.get(sel);
        view[S_LAYOUT] = PanelLayouts.index(s.layout()) + 1;
        view[S_X] = s.pos().getX();
        view[S_Y] = s.pos().getY();
        view[S_Z] = s.pos().getZ();
        view[S_AGE] = (int) Math.min(Integer.MAX_VALUE, s.ageTicks());
        System.arraycopy(s.channels(), 0, view, MIRROR0, Math.min(MIRROR, s.channels().length));
        List<DeviceTag> tags = OperatorTerminalPanels.tags(s.layout());
        trendTag = tags.isEmpty() ? 0 : Math.floorMod(trendTag, tags.size());
        view[TREND_TAG] = trendTag;
        view[TAG_FIRST] = trendTag / TAG_ROWS * TAG_ROWS;
        if (!tags.isEmpty()) trend(s.id() + "/" + tags.get(trendTag).name());
        int k = 0;
        for (PanelLayout.Element e : s.layout().elements) {
            if (k >= COMMANDS) break;
            if (!(e instanceof PanelLayout.Button b) || DeviceRuntime.hardwareKey(PanelText.shortKey(b.label()))) continue;
            commandIds[k] = b.id();
            view[CMD0 + k] = b.id() + 1;
            k++;
        }
    }

    /** Тренд тега за {@value OperatorTerminalPanels#TREND_WINDOW_S} с из ближайшего историана, который его ведёт. */
    private void trend(String tag) {
        double now = level.getGameTime() / 20.0, t0 = now - TREND_WINDOW_S;
        for (HistorianBlockEntity h : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), HistorianBlockEntity.class)) {
            TagArchive a = h.archive(tag);
            if (a == null || a.size() == 0) continue;
            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            float[] y = new float[TREND_POINTS];
            for (int i = 0; i < TREND_POINTS; i++) {
                double t = t0 + TREND_WINDOW_S * i / (double) (TREND_POINTS - 1);
                y[i] = t < a.firstT() ? Float.NaN : (float) a.valueAt(Math.min(t, a.lastT()));
                if (!Float.isNaN(y[i])) {
                    lo = Math.min(lo, y[i]);
                    hi = Math.max(hi, y[i]);
                }
            }
            if (lo > hi) continue;
            view[FLAGS] |= F_TREND;
            view[TREND_MIN] = Float.floatToIntBits(lo);
            view[TREND_MAX] = Float.floatToIntBits(hi);
            for (int i = 0; i < TREND_POINTS; i++) view[TREND0 + i] = Float.floatToIntBits(y[i]);
            return;
        }
    }

    /** Сводка табло в радиусе: показанные сигнализации по приоритетам и неквитированные. */
    private void alarms() {
        for (AnnunciatorBlockEntity a : Ironworks.blockEntities(level, new AABB(worldPosition).inflate(RADIUS), AnnunciatorBlockEntity.class))
            for (var al : a.engine().alarms()) {
                if (!al.state().annunciated()) continue;
                switch (al.definition().priority()) {
                    case 1 -> view[P1]++;
                    case 2 -> view[P2]++;
                    case 3 -> view[P3]++;
                    default -> {
                    }
                }
                if (al.state() == AlarmState.UNACK || al.state() == AlarmState.RTNUN) view[UNACK]++;
            }
    }

    // ------------------------------------------------------------------ щит

    @Override
    public PanelLayout panelLayout() {
        return OperatorTerminalPanels.TERMINAL;
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
        System.arraycopy(view, 0, out, 0, Math.min(out.length, view.length));
    }

    @Override
    public void onPanelButton(ServerPlayer player, int id) {
        if (!powered) return;
        List<Station> all = stations();
        if (id >= B_ROW0 && id < B_ROW0 + LIST_ROWS) {
            int i = page * LIST_ROWS + id - B_ROW0;
            if (i < all.size() && !all.get(i).id().equals(selectedId)) {
                selectedId = all.get(i).id();
                trendTag = 0;
            }
        } else if (id == B_PAGE_PREV || id == B_PAGE_NEXT) {
            page += id == B_PAGE_NEXT ? 1 : -1;
        } else if (id == B_TAG_PREV || id == B_TAG_NEXT) {
            trendTag += id == B_TAG_NEXT ? 1 : -1;
        } else if (id >= CMD_B0 && id < CMD_B0 + COMMANDS) {
            int button = commandIds[id - CMD_B0];
            TelemetryGatewayBlockEntity rtu = button < 0 ? null : gatewayOf(selectedId);
            if (rtu == null) return;
            try {
                rtu.pressAs(player, selectedId, button);
            } catch (TelemetryException e) {
                player.displayClientMessage(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED), true);
            }
        }
        refresh();
        setChanged();
    }

    // ------------------------------------------------------------------ руки и сохранение

    @Override
    public InteractionResult use(Player player) {
        return InteractionResult.PASS;
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
        tag.putString("selected", selectedId);
        tag.putInt("page", page);
        tag.putInt("trend", trendTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selectedId = tag.getString("selected");
        page = tag.getInt("page");
        trendTag = tag.getInt("trend");
    }
}
