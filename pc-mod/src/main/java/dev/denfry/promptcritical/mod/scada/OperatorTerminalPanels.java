package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayouts;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.scada.device.DeviceTag;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Видеокадры терминала оператора {@code operator_terminal} (16 §16.3.4, R58): типовой кадр на устройство, собранный из
 * его щита ({@link DeviceTag#of}, 03b §3b.7.2): значения с единицами и долей шкалы, тренд выбранного тега из историана,
 * сводка сигнализаций и команды щита. Сервер шлёт номер раскладки выбранной машины и копию её каналов; подписи
 * клиент берёт из той же раскладки, поэтому кадр строится для любой машины без отдельного описания.
 */
public final class OperatorTerminalPanels {
    /** Строк в списке машин, строк тегов, точек тренда, команд, каналов копии щита. */
    public static final int LIST_ROWS = 8, TAG_ROWS = 8, TREND_POINTS = 32, COMMANDS = 6, MIRROR = 128;
    /** Окно тренда, с. */
    public static final int TREND_WINDOW_S = 600;

    public static final int FLAGS = 0, COUNT = 1, PAGE = 2, SELECTED = 3, ROW0 = 4,
            S_LAYOUT = ROW0 + 4 * LIST_ROWS, S_X = S_LAYOUT + 1, S_Y = S_LAYOUT + 2, S_Z = S_LAYOUT + 3, S_AGE = S_LAYOUT + 4,
            TAG_FIRST = S_LAYOUT + 5, TREND_TAG = S_LAYOUT + 6, P1 = S_LAYOUT + 7, P2 = S_LAYOUT + 8, P3 = S_LAYOUT + 9,
            UNACK = S_LAYOUT + 10, TREND_MIN = S_LAYOUT + 11, TREND_MAX = S_LAYOUT + 12, TREND0 = S_LAYOUT + 13,
            CMD0 = TREND0 + TREND_POINTS, MIRROR0 = CMD0 + COMMANDS, CHANNELS = MIRROR0 + MIRROR;
    /** Биты {@link #FLAGS}: питание есть, тренд есть (историан ведёт тег). */
    public static final int F_POWERED = 1, F_TREND = 2;

    /** Кнопки: выбор строки списка {@code 0…7}, страницы, тег тренда, команды {@code CMD_B0…}. */
    public static final int B_ROW0 = 0, B_PAGE_PREV = 10, B_PAGE_NEXT = 11, B_TAG_PREV = 12, B_TAG_NEXT = 13, CMD_B0 = 20;

    private static final Tr OFF = PanelText.tr("terminal.off", "No power", "Нет питания"),
            EMPTY = PanelText.tr("terminal.empty", "No machines on the RTUs within 16 blocks",
                    "Нет машин на КП в 16 блоках"),
            ROW = PanelText.tr("terminal.row", "%s %s", "%s %s"),
            HEADER = PanelText.tr("terminal.header", "%s · %s, %s, %s · %s s", "%s · %s, %s, %s · %s с"),
            ALARMS = PanelText.tr("terminal.alarms", "P1 %s  P2 %s  P3 %s", "P1 %s  P2 %s  P3 %s"),
            UNACKED = PanelText.tr("terminal.unacked", "Unacknowledged: %s", "Не квитировано: %s"),
            TREND = PanelText.tr("terminal.trend", "%s: %s…%s over %s min", "%s: %s…%s за %s мин"),
            NO_TREND = PanelText.tr("terminal.no_trend", "No historian archive for this tag", "Тег не в архиве историана"),
            ON = PanelText.tr("terminal.on", "ON", "ВКЛ"),
            OFF_STATE = PanelText.tr("terminal.off_state", "off", "откл"),
            PREV = PanelText.tr("terminal.prev", "<", "<"),
            NEXT = PanelText.tr("terminal.next", ">", ">"),
            SELECT = PanelText.tr("terminal.select", "Show this machine", "Показать машину"),
            TAG_PREV = PanelText.tr("terminal.tag_prev", "< Trend", "< Тренд"),
            TAG_NEXT = PanelText.tr("terminal.tag_next", "Trend >", "Тренд >"),
            CMD_TIP = PanelText.tr("terminal.cmd_tip", "Telecontrol through the RTU: the panel button, pressed in your name",
                    "Телеуправление через КП: кнопка щита нажимается от вашего имени"),
            TIP = PanelText.tr("terminal.tip", "Standard displays of every machine on the RTUs within 16 blocks; no programs (R58)",
                    "Типовые видеокадры всех машин на КП в 16 блоках; без программ (R58)");

    public static final PanelLayout TERMINAL = build();

    private OperatorTerminalPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(TERMINAL);
    }

    private static PanelLayout build() {
        PanelLayout.Builder b = PanelLayout.builder("operator_terminal", 324, 230, CHANNELS);
        for (int i = 0; i < LIST_ROWS; i++) {
            int row = i, y = 24 + 17 * i;
            b.button(8, y, 14, B_ROW0 + i, Component.literal("▸"), SELECT.get());
            b.readout(24, y + 2, 76, v -> listRow(v, row), null);
        }
        b.button(8, 164, 44, B_PAGE_PREV, PREV.get(), null).button(56, 164, 44, B_PAGE_NEXT, NEXT.get(), null);
        b.readout(8, 186, 92, v -> ALARMS.get(v.raw(P1), v.raw(P2), v.raw(P3)), null);
        b.readout(8, 199, 92, v -> UNACKED.get(v.raw(UNACK)), null);
        b.button(8, 212, 44, B_TAG_PREV, TAG_PREV.get(), null).button(56, 212, 44, B_TAG_NEXT, TAG_NEXT.get(), null);
        b.readout(108, 22, 208, OperatorTerminalPanels::header, v -> TIP.get());
        for (int i = 0; i < TAG_ROWS; i++) {
            int row = i, y = 38 + 14 * i;
            b.readout(108, y, 96, v -> tagLabel(v, row), null);
            b.readout(206, y, 64, v -> tagValue(v, row), null);
            b.trace(272, y + 3, 44, 8, v -> new double[] {tagFraction(v, row)}, null);
        }
        b.trace(108, 152, 208, 30, OperatorTerminalPanels::trend, OperatorTerminalPanels::trendTip);
        for (int i = 0; i < COMMANDS; i++) {
            int k = i, x = 108 + 36 * i;
            b.readout(x, 186, 32, v -> commandLabel(v, k), null);
            b.button(x, 204, 32, CMD_B0 + i, Component.literal("●"), CMD_TIP.get());
        }
        return b.noInventory().build();
    }

    // ------------------------------------------------------------------ клиент: подписи из раскладки машины

    @Nullable
    private static PanelLayout layout(PanelValues v, int ch) {
        return PanelLayouts.byIndex(v.raw(ch) - 1);
    }

    private static Component machineName(@Nullable PanelLayout l) {
        if (l == null) return Component.literal("?");
        var block = BuiltInRegistries.BLOCK.get(dev.denfry.promptcritical.mod.PromptCritical.id(l.id));
        return block == net.minecraft.world.level.block.Blocks.AIR ? Component.literal(l.id) : block.getName();
    }

    private static Component listRow(PanelValues v, int row) {
        if ((v.raw(FLAGS) & F_POWERED) == 0) return row == 0 ? OFF.get() : Component.empty();
        if (v.raw(COUNT) == 0) return row == 0 ? EMPTY.get() : Component.empty();
        int base = ROW0 + 4 * row;
        if (v.raw(base) == 0) return Component.empty();
        boolean sel = v.raw(SELECTED) == v.raw(PAGE) * LIST_ROWS + row;
        return ROW.get(sel ? "▶" : " ", machineName(layout(v, base)));
    }

    private static Component header(PanelValues v) {
        PanelLayout l = layout(v, S_LAYOUT);
        if (l == null) return Component.empty();
        return HEADER.get(machineName(l), v.raw(S_X), v.raw(S_Y), v.raw(S_Z), PanelText.num(v.raw(S_AGE) / 20.0, 0));
    }

    /** Теги кадра: всё, что показывает щит, кроме кривых (динамограмма на кадр не помещается). */
    public static List<DeviceTag> tags(@Nullable PanelLayout l) {
        if (l == null) return List.of();
        List<DeviceTag> out = new ArrayList<>();
        for (DeviceTag t : DeviceTag.of(l)) if (!DeviceTag.CURVE.equals(t.kind())) out.add(t);
        return out;
    }

    /**
     * Подписи тегов {@link #tags} на языке игрока: {@link DeviceTag#of} идёт по элементам щита в том же порядке, а в
     * самом теге подпись английская (её видит Lua).
     */
    private static List<Component> labels(@Nullable PanelLayout l) {
        if (l == null) return List.of();
        List<Component> out = new ArrayList<>();
        for (PanelLayout.Element e : l.elements) {
            switch (e) {
                case PanelLayout.Dial d -> out.add(d.label());
                case PanelLayout.Glass g -> out.add(g.label());
                case PanelLayout.Bar br -> out.add(br.label());
                case PanelLayout.UTube u -> out.add(u.label());
                case PanelLayout.Lamp lp -> out.add(lp.label());
                default -> {
                }
            }
        }
        return out;
    }

    @Nullable
    private static DeviceTag tag(PanelValues v, int row) {
        List<DeviceTag> tags = tags(layout(v, S_LAYOUT));
        int i = v.raw(TAG_FIRST) + row;
        return i >= 0 && i < tags.size() ? tags.get(i) : null;
    }

    private static int[] mirror(PanelValues v) {
        int[] ch = new int[MIRROR];
        for (int i = 0; i < MIRROR; i++) ch[i] = v.raw(MIRROR0 + i);
        return ch;
    }

    private static Component tagLabel(PanelValues v, int row) {
        DeviceTag t = tag(v, row);
        if (t == null) return Component.empty();
        int i = v.raw(TAG_FIRST) + row;
        List<Component> labels = labels(layout(v, S_LAYOUT));
        Component label = i < labels.size() ? labels.get(i) : Component.literal(t.label());
        return i == v.raw(TREND_TAG) ? Component.literal("~ ").append(label) : label;
    }

    private static Component tagValue(PanelValues v, int row) {
        DeviceTag t = tag(v, row);
        if (t == null) return Component.empty();
        double x = t.numeric(mirror(v));
        if (Double.isNaN(x)) return Component.literal("—");
        if (DeviceTag.DISCRETE.equals(t.kind())) return x > 0 ? ON.get() : OFF_STATE.get();
        return Component.literal(PanelText.num(x, Math.abs(x) >= 100 ? 0 : Math.abs(x) >= 10 ? 1 : 2) + (t.unit().isEmpty() ? "" : " " + t.unit()));
    }

    private static double tagFraction(PanelValues v, int row) {
        DeviceTag t = tag(v, row);
        if (t == null) return Double.NaN;
        double x = t.numeric(mirror(v));
        return t.hi() > t.lo() ? (x - t.lo()) / (t.hi() - t.lo()) : Double.NaN;
    }

    private static double[] trend(PanelValues v) {
        double[] out = new double[TREND_POINTS];
        if ((v.raw(FLAGS) & F_TREND) == 0) {
            java.util.Arrays.fill(out, Double.NaN);
            return out;
        }
        float lo = Float.intBitsToFloat(v.raw(TREND_MIN)), hi = Float.intBitsToFloat(v.raw(TREND_MAX));
        double span = hi > lo ? hi - lo : 1;
        for (int i = 0; i < TREND_POINTS; i++) {
            float y = Float.intBitsToFloat(v.raw(TREND0 + i));
            out[i] = Float.isNaN(y) ? Double.NaN : hi > lo ? (y - lo) / span : 0.5;
        }
        return out;
    }

    private static Component trendTip(PanelValues v) {
        List<DeviceTag> tags = tags(layout(v, S_LAYOUT));
        int i = v.raw(TREND_TAG);
        if ((v.raw(FLAGS) & F_TREND) == 0 || i < 0 || i >= tags.size()) return NO_TREND.get();
        List<Component> labels = labels(layout(v, S_LAYOUT));
        return TREND.get(i < labels.size() ? labels.get(i) : Component.literal(tags.get(i).label()), PanelText.num(Float.intBitsToFloat(v.raw(TREND_MIN)), 2),
                PanelText.num(Float.intBitsToFloat(v.raw(TREND_MAX)), 2), TREND_WINDOW_S / 60);
    }

    private static Component commandLabel(PanelValues v, int k) {
        int id = v.raw(CMD0 + k) - 1;
        PanelLayout l = layout(v, S_LAYOUT);
        if (id < 0 || l == null) return Component.empty();
        for (PanelLayout.Element e : l.elements) if (e instanceof PanelLayout.Button b && b.id() == id) return b.label();
        return Component.empty();
    }
}
