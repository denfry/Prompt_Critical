package dev.denfry.promptcritical.mod.reactor;

import static dev.denfry.promptcritical.mod.panel.PanelText.CELSIUS;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import dev.denfry.promptcritical.sim.reactor.PileLayout;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Щиты графитового котла: пульт (счётчик, мощность, период, стержни, защита), топливный канал (кассеты блочков) и
 * печь Ачесона. Мощность и счёт — в логарифмических каналах (сотые доли декады), период — в десятых секунды.
 */
public final class ReactorPanels {
    /** Нет значения (котёл не собран, нет счётчика). */
    public static final int NONE = Integer.MIN_VALUE;
    /** Период ∞ (стационар). */
    public static final int PERIOD_INF = Integer.MAX_VALUE;

    // пульт котла
    public static final int P_POWER_LOG100 = 0, P_CPS_LOG100 = 1, P_PERIOD10 = 2, P_GRAPHITE_C10 = 3, P_FUEL_C10 = 4,
            P_CONTROL_PCT = 5, P_ZIP_PCT = 6, P_FLAGS = 7, P_TRIP = 8, P_NODES = 9, P_FUEL_NODES = 10, P_LOAD = 11,
            P_CONTROL_RODS = 12, P_ZIP_RODS = 13, P_DETECTORS = 14, P_SOURCES = 15, P_DRIVE = 16, P_POWER_DIAL = 17,
            P_CPS_DIAL = 18, P_CHANNELS = 19;
    public static final int PF_TRIPPED = 0, PF_COCKED = 1, PF_WINDING = 2, PF_INTERLOCK = 3, PF_CLAD = 4, PF_FIRE = 5,
            PF_TOO_BIG = 6, PF_FORMED = 7;
    public static final int BTN_DOWN = 0, BTN_STOP = 1, BTN_UP = 2, BTN_WIND = 3, BTN_SCRAM = 4, BTN_RESET = 5;

    // топливный канал
    public static final int C_LOAD = 0, C_CHANNELS = 1;

    // печь Ачесона
    public static final int A_POWER_KW = 0, A_PROGRESS_PERMIL = 1, A_FLAGS = 2, A_CHANNELS = 3;
    public static final int AF_ENERGIZED = 0, AF_SWITCH = 1, AF_RUNNING = 2;
    public static final int BTN_SWITCH = 10;

    private static final Tr POWER = PanelText.tr("pile.power", "Power, lg mW", "Мощность, lg мВт"),
            COUNTS = PanelText.tr("pile.counts", "BF₃, lg cps", "BF₃, lg имп/с"),
            DECADES = PanelText.tr("pile.decades", "lg", "lg"),
            GRAPHITE = PanelText.tr("pile.graphite", "Graphite", "Графит"),
            FUEL = PanelText.tr("pile.fuel", "Slugs", "Блочки"),
            CONTROL = PanelText.tr("pile.control", "Control rods in", "Регулирующие введены"),
            ZIP = PanelText.tr("pile.zip", "Zip rods in", "Аварийные введены"),
            TRIPPED = PanelText.tr("pile.tripped", "Scram", "АЗ"),
            COCKED = PanelText.tr("pile.cocked", "Zip cocked", "АЗ взведена"),
            WINDING = PanelText.tr("pile.winding", "Winding", "Взвод"),
            INTERLOCK = PanelText.tr("pile.interlock", "Withdrawal blocked", "Подъём запрещён"),
            CLAD = PanelText.tr("pile.clad", "Slug failure", "Разрушение блочков"),
            FIRE = PanelText.tr("pile.fire", "Graphite fire", "Горит графит"),
            READ_POWER = PanelText.tr("pile.read_power", "Power %s", "Мощность %s"),
            READ_COUNTS = PanelText.tr("pile.read_counts", "Count rate %s cps", "Счёт %s имп/с"),
            READ_PERIOD = PanelText.tr("pile.read_period", "Period %s", "Период %s"),
            PERIOD_STEADY = PanelText.tr("pile.period_steady", "∞", "∞"),
            SECONDS = PanelText.tr("pile.seconds", "%s s", "%s с"),
            READ_TEMPS = PanelText.tr("pile.read_temps", "Graphite %s °C · slugs %s °C", "Графит %s °C · блочки %s °C"),
            READ_STRUCT = PanelText.tr("pile.read_struct", "Pile %s m³ · fuel %s m³ (%s of %s cassettes) · sources %s",
                    "Кладка %s м³ · топливо %s м³ (%s из %s кассет) · источников %s"),
            READ_RODS = PanelText.tr("pile.read_rods", "Rods: control %s · zip %s · counters %s",
                    "Стержни: регулирующих %s · аварийных %s · счётчиков %s"),
            NOT_FORMED = PanelText.tr("pile.not_formed", "No pile: put graphite against the desk", "Котла нет: кладка должна касаться пульта"),
            TOO_BIG = PanelText.tr("pile.too_big", "Pile too large (max %s blocks)", "Кладка слишком велика (не больше %s блоков)"),
            DOWN = PanelText.tr("pile.down", "▼ In", "▼ Вниз"),
            STOP = PanelText.tr("pile.stop", "■ Stop", "■ Стоп"),
            UP = PanelText.tr("pile.up", "▲ Out", "▲ Вверх"),
            DRIVE_TIP = PanelText.tr("pile.drive_tip", "Control rod drive, 5 cm/s", "Привод регулирующих стержней, 5 см/с"),
            WIND = PanelText.tr("pile.wind", "Wind zip", "Взвести АЗ"),
            WIND_TIP = PanelText.tr("pile.wind_tip", "Wind the zip rods out on the winch (10 cm/s)",
                    "Поднять аварийные стержни лебёдкой (10 см/с)"),
            SCRAM = PanelText.tr("pile.scram", "SCRAM", "АЗ"),
            SCRAM_TIP = PanelText.tr("pile.scram_tip", "Drop the zip rods, drive the control rods in",
                    "Сбросить аварийные стержни, регулирующие — вниз"),
            RESET = PanelText.tr("pile.reset", "Reset", "Сброс АЗ"),
            RESET_TIP = PanelText.tr("pile.reset_tip", "Clear the scram once power and period are below the setpoints",
                    "Снять защиту, когда мощность и период ниже уставок"),
            TRIP_REASON = PanelText.tr("pile.trip_reason", "Scram: %s", "АЗ: %s");
    private static final Tr[] TRIPS = {PanelText.tr("pile.trip.none", "none", "нет"),
            PanelText.tr("pile.trip.manual", "button", "кнопка"),
            PanelText.tr("pile.trip.short_period", "short period", "малый период"),
            PanelText.tr("pile.trip.high_power", "high power", "превышение мощности")};

    // сообщения в чат и строку действия
    public static final Tr MSG_SCRAM = PanelText.tr("pile.msg.scram", "Scram! The zip rods are falling", "АЗ! Аварийные стержни падают"),
            MSG_NO_PILE = PanelText.tr("pile.msg.no_pile", "No pile control desk within %s blocks", "Нет пульта котла ближе %s блоков"),
            MSG_CHANNEL = PanelText.tr("pile.msg.channel", "Fuel channel: %s of %s cassettes", "Топливный канал: %s из %s кассет"),
            MSG_RESET_REFUSED = PanelText.tr("pile.msg.reset_refused", "Scram cannot be reset: power or period above the setpoint",
                    "Защиту не снять: мощность или период выше уставки"),
            MSG_WIND_REFUSED = PanelText.tr("pile.msg.wind_refused", "Reset the scram before winding the zip rods",
                    "Сначала снимите защиту, потом взводите аварийные стержни"),
            MSG_INTERLOCK = PanelText.tr("pile.msg.interlock", "Control rods stay in until the zip rods are cocked",
                    "Регулирующие не поднимутся, пока аварийные не взведены");

    private static final Tr SLUG = PanelText.tr("pile.slug", "Uranium slug cassette", "Кассета урановых блочков"),
            CHANNEL_LOAD = PanelText.tr("pile.channel_load", "Loaded %s of %s cassettes", "Загружено %s из %s кассет");

    private static final Tr ACHESON_POWER = PanelText.tr("acheson.power", "Furnace power", "Мощность печи"),
            KW = PanelText.tr("acheson.kw", "kW", "кВт"),
            ENERGIZED = PanelText.tr("acheson.energized", "Energized", "Напряжение"),
            SWITCH = PanelText.tr("acheson.switch", "Switch", "Рубильник"),
            RUNNING = PanelText.tr("acheson.running", "Graphitizing", "Графитация"),
            PROGRESS = PanelText.tr("acheson.progress", "Electrode", "Электрод"),
            BAKED = PanelText.tr("acheson.baked", "Baked electrode (sound)", "Обожжённый электрод (без трещин)"),
            GRAPHITE_OUT = PanelText.tr("acheson.out", "Graphite electrode", "Графитированный электрод"),
            ACHESON_NOTE = PanelText.tr("acheson.note", "Grid load 500 kW, 6 kV cable to any face",
                    "Нагрузка сети 500 кВт, кабель 6 кВ к любой грани");

    public static final PanelLayout PILE = PanelLayout.builder("reactor_controller", 240, 200, P_CHANNELS)
            .dial(8, 24, true, P_POWER_DIAL, 10, 9, 7.5, 0, DECADES.get(), POWER.get(),
                    v -> READ_POWER.get(power(v.raw(P_POWER_LOG100))))
            .dial(84, 24, true, P_CPS_DIAL, 10, 6, 5.5, 0, DECADES.get(), COUNTS.get(),
                    v -> READ_COUNTS.get(counts(v.raw(P_CPS_LOG100))))
            .lamp(162, 24, P_FLAGS, PF_TRIPPED, LampColor.RED, TRIPPED.get())
            .lamp(162, 38, P_FLAGS, PF_COCKED, LampColor.GREEN, COCKED.get())
            .lamp(162, 52, P_FLAGS, PF_WINDING, LampColor.AMBER, WINDING.get())
            .lamp(162, 66, P_FLAGS, PF_INTERLOCK, LampColor.AMBER, INTERLOCK.get())
            .lamp(162, 80, P_FLAGS, PF_CLAD, LampColor.RED, CLAD.get())
            .lamp(162, 94, P_FLAGS, PF_FIRE, LampColor.RED, FIRE.get())
            .readout(8, 102, 150, ReactorPanels::periodLine, null)
            .readout(8, 114, 224, v -> READ_TEMPS.get(v.num(P_GRAPHITE_C10, 10, 1), v.num(P_FUEL_C10, 10, 1)), null)
            .bar(8, 128, 108, P_CONTROL_PCT, 1, 100, 0xFF707070, CONTROL.get(), v -> Component.literal(v.raw(P_CONTROL_PCT) + " %"))
            .bar(124, 128, 108, P_ZIP_PCT, 1, 100, 0xFFB03020, ZIP.get(), v -> Component.literal(v.raw(P_ZIP_PCT) + " %"))
            .button(8, 144, 40, BTN_DOWN, DOWN.get(), DRIVE_TIP.get())
            .button(50, 144, 40, BTN_STOP, STOP.get(), DRIVE_TIP.get())
            .button(92, 144, 40, BTN_UP, UP.get(), DRIVE_TIP.get())
            .button(140, 144, 44, BTN_WIND, WIND.get(), WIND_TIP.get())
            .button(188, 144, 44, BTN_SCRAM, SCRAM.get(), SCRAM_TIP.get())
            .button(188, 160, 44, BTN_RESET, RESET.get(), RESET_TIP.get())
            .readout(8, 164, 176, ReactorPanels::structureLine, null)
            .readout(8, 176, 176, v -> READ_RODS.get(v.raw(P_CONTROL_RODS), v.raw(P_ZIP_RODS), v.raw(P_DETECTORS)), null)
            .noInventory()
            .build();

    public static final PanelLayout CHANNEL = PanelLayout.builder("fuel_channel", 176, 70, C_CHANNELS)
            .readout(8, 22, 160, v -> CHANNEL_LOAD.get(v.raw(C_LOAD), PileLayout.FULL_LOAD), null)
            .slot(8, 40, 0, ReactorPanels::slug, SLUG.get()).slot(26, 40, 1, ReactorPanels::slug, SLUG.get())
            .slot(44, 40, 2, ReactorPanels::slug, SLUG.get()).slot(62, 40, 3, ReactorPanels::slug, SLUG.get())
            .slot(80, 40, 4, ReactorPanels::slug, SLUG.get()).slot(98, 40, 5, ReactorPanels::slug, SLUG.get())
            .slot(116, 40, 6, ReactorPanels::slug, SLUG.get()).slot(134, 40, 7, ReactorPanels::slug, SLUG.get())
            .build();

    public static final PanelLayout ACHESON = PanelLayout.builder("acheson_furnace", 200, 130, A_CHANNELS)
            .dial(8, 24, true, A_POWER_KW, 1, 600, Double.NaN, 0, KW.get(), ACHESON_POWER.get(), null)
            .lamp(84, 24, A_FLAGS, AF_ENERGIZED, LampColor.WHITE, ENERGIZED.get())
            .lamp(84, 38, A_FLAGS, AF_SWITCH, LampColor.RED, SWITCH.get())
            .lamp(84, 52, A_FLAGS, AF_RUNNING, LampColor.AMBER, RUNNING.get())
            .button(140, 24, 52, BTN_SWITCH, SWITCH.get(), null)
            .bar(84, 70, 108, A_PROGRESS_PERMIL, 10, 100, 0xFFE0A040, PROGRESS.get(), null)
            .readout(8, 94, 184, v -> ACHESON_NOTE.get(), null)
            .slot(16, 108, 0, AchesonFurnaceBlockEntity::isBaked, BAKED.get())
            .caption(40, 113, Component.literal("→"))
            .output(64, 108, 1, GRAPHITE_OUT.get()).output(82, 108, 2, GRAPHITE_OUT.get())
            .output(100, 108, 3, GRAPHITE_OUT.get()).output(118, 108, 4, GRAPHITE_OUT.get())
            .build();

    private ReactorPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(PILE, CHANNEL, ACHESON);
    }

    static boolean slug(net.minecraft.world.item.ItemStack s) {
        return s.is(PcItems.URANIUM_SLUG.get());
    }

    /** Логарифмический канал: сотые доли декады; {@link #NONE} для нуля. */
    public static int log100(double v) {
        if (!(v > 0)) return NONE;
        return (int) Math.round(100 * Math.log10(v));
    }

    static Component power(int log100) {
        if (log100 == NONE) return Component.literal("—");
        return Component.literal(si(Math.pow(10, log100 / 100.0)) + "W");
    }

    static Component counts(int log100) {
        if (log100 == NONE) return Component.literal("—");
        double c = Math.pow(10, log100 / 100.0);
        return Component.literal(c < 1000 ? PanelText.num(c, c < 10 ? 1 : 0) : si(c));
    }

    /** 3 значащие цифры с приставкой СИ (м…Г). */
    static String si(double v) {
        String[] p = {"p", "n", "µ", "m", "", "k", "M", "G"};
        int e = (int) Math.floor(Math.log10(Math.max(v, 1e-12)) / 3);
        e = Math.max(-4, Math.min(3, e));
        double m = v / Math.pow(1000, e);
        return String.format(Locale.ROOT, m < 10 ? "%.2f " : m < 100 ? "%.1f " : "%.0f ", m) + p[e + 4];
    }

    private static Component periodLine(PanelValues v) {
        int t = v.raw(P_PERIOD10);
        Component period = t == PERIOD_INF || t == NONE ? PERIOD_STEADY.get() : SECONDS.get(PanelText.num(t / 10.0, 1));
        if (v.bit(P_FLAGS, PF_TRIPPED)) {
            int r = v.raw(P_TRIP);
            Component reason = r >= 0 && r < TRIPS.length ? TRIPS[r].get() : Component.literal("?");
            return READ_PERIOD.get(period).copy().append(" · ").append(TRIP_REASON.get(reason));
        }
        return READ_PERIOD.get(period);
    }

    private static Component structureLine(PanelValues v) {
        if (v.bit(P_FLAGS, PF_TOO_BIG)) return TOO_BIG.get(PileLayout.MAX_NODES);
        if (!v.bit(P_FLAGS, PF_FORMED)) return NOT_FORMED.get();
        return READ_STRUCT.get(v.raw(P_NODES), v.raw(P_FUEL_NODES), v.raw(P_LOAD), v.raw(P_FUEL_NODES) * PileLayout.FULL_LOAD,
                v.raw(P_SOURCES));
    }
}
