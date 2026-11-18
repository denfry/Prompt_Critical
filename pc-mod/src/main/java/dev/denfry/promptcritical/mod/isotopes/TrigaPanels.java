package dev.denfry.promptcritical.mod.isotopes;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Пульт импульсного реактора TRIGA (12 §12.6.1): мощность по логарифмической шкале, температура топлива против
 * предела 1000 °C, регулирующие и импульсный стержни, ключ импульсного режима, вес импульса в центах, взвод и выброс,
 * итоги последнего импульса.
 */
public final class TrigaPanels {
    public static final int T_POWER_DIAL = 0, T_POWER_LOG100 = 1, T_FUEL_C = 2, T_CONTROL_PCT = 3, T_TRANSIENT_PCT = 4,
            T_PULSE_CENTS = 5, T_PERIOD10 = 6, T_PEAK_LOG100 = 7, T_ENERGY_KJ = 8, T_FWHM_MS10 = 9, T_PEAK_FUEL_C = 10,
            T_FLAGS = 11, T_CHANNELS = 12;
    public static final int TF_SCRAM = 0, TF_PULSE_MODE = 1, TF_ARMED = 2, TF_PULSING = 3, TF_FUEL_FAILED = 4, TF_NO_POOL = 5;
    public static final int BTN_DOWN = 0, BTN_STOP = 1, BTN_UP = 2, BTN_SCRAM = 3, BTN_RESET = 4, BTN_MODE = 5,
            BTN_LESS = 6, BTN_MORE = 7, BTN_ARM = 8, BTN_FIRE = 9;
    /** Шаг установки веса импульса, центы. */
    public static final int CENTS_STEP = 25;
    /** Нет значения. */
    public static final int NONE = Integer.MIN_VALUE, PERIOD_INF = Integer.MAX_VALUE;

    private static final Tr POWER = PanelText.tr("triga.power", "Power, lg mW", "Мощность, lg мВт"),
            DECADES = PanelText.tr("triga.decades", "lg", "lg"),
            FUEL = PanelText.tr("triga.fuel", "Fuel", "Топливо"),
            FUEL_TIP = PanelText.tr("triga.fuel_tip", "U-ZrH fuel %s °C · limit 1000 °C", "Топливо U-ZrH %s °C · предел 1000 °C"),
            CONTROL = PanelText.tr("triga.control", "Control rods in", "Регулирующие введены"),
            TRANSIENT = PanelText.tr("triga.transient", "Transient rod in", "Импульсный введён"),
            SCRAM_LAMP = PanelText.tr("triga.scram_lamp", "Scram", "АЗ"),
            PULSE_MODE = PanelText.tr("triga.pulse_mode", "Pulse mode", "Импульсный режим"),
            ARMED = PanelText.tr("triga.armed", "Armed", "Взведён"),
            PULSING = PanelText.tr("triga.pulsing", "Pulse", "Импульс"),
            FUEL_FAILED = PanelText.tr("triga.fuel_failed", "Fuel failed", "Твэлы повреждены"),
            NO_POOL = PanelText.tr("triga.no_pool", "No pool", "Нет бассейна"),
            READ = PanelText.tr("triga.read", "%s · period %s · pulse %s ¢", "%s · период %s · импульс %s ¢"),
            LAST = PanelText.tr("triga.last", "Last pulse: peak %s · %s MJ · FWHM %s ms · fuel %s °C",
                    "Последний импульс: пик %s · %s МДж · ПШПВ %s мс · топливо %s °C"),
            NO_PULSE = PanelText.tr("triga.no_pulse", "No pulse yet", "Импульсов не было"),
            SECONDS = PanelText.tr("triga.seconds", "%s s", "%s с"),
            STEADY = PanelText.tr("triga.steady", "∞", "∞"),
            DOWN = PanelText.tr("triga.down", "▼ In", "▼ Вниз"),
            STOP = PanelText.tr("triga.stop", "■ Stop", "■ Стоп"),
            UP = PanelText.tr("triga.up", "▲ Out", "▲ Вверх"),
            DRIVE_TIP = PanelText.tr("triga.drive_tip", "Control rod drives, 2 % of travel a second",
                    "Привод регулирующих стержней, 2 % хода в секунду"),
            SCRAM = PanelText.tr("triga.scram", "SCRAM", "АЗ"),
            RESET = PanelText.tr("triga.reset", "Reset", "Сброс АЗ"),
            RESET_TIP = PanelText.tr("triga.reset_tip", "Clear the scram: needs the pool and sound fuel",
                    "Снять защиту: нужен бассейн и целые твэлы"),
            MODE = PanelText.tr("triga.mode", "Mode key", "Ключ режима"),
            MODE_TIP = PanelText.tr("triga.mode_tip",
                    "Steady or pulse mode. Pulse mode bypasses the period and power trips and is allowed only below 1 kW",
                    "Стационарный или импульсный режим. Импульсный шунтирует защиту по периоду и мощности и разрешён только ниже 1 кВт"),
            LESS = PanelText.tr("triga.less", "−25 ¢", "−25 ¢"),
            MORE = PanelText.tr("triga.more", "+25 ¢", "+25 ¢"),
            CENTS_TIP = PanelText.tr("triga.cents_tip", "Pulse reactivity: how far the transient rod is blown out, up to its worth of $3.00",
                    "Вес импульса: насколько выбросит импульсный стержень, до его веса $3,00"),
            ARM = PanelText.tr("triga.arm", "Arm", "Взвести"),
            ARM_TIP = PanelText.tr("triga.arm_tip", "Charge the transient rod's air cylinder (pulse mode only)",
                    "Подать воздух в цилиндр импульсного стержня (только в импульсном режиме)"),
            FIRE = PanelText.tr("triga.fire", "FIRE", "ИМПУЛЬС"),
            FIRE_TIP = PanelText.tr("triga.fire_tip", "Blow the transient rod out of the core; the pulse timer scrams 15 s later",
                    "Выбросить импульсный стержень из зоны; таймер импульса сбросит стержни через 15 с");

    // сообщения
    public static final Tr MSG_PULSE_REFUSED = PanelText.tr("triga.msg.pulse_refused", "Pulse mode refused: scram set or power above 1 kW",
            "Импульсный режим не включается: АЗ или мощность выше 1 кВт"),
            MSG_ARM_REFUSED = PanelText.tr("triga.msg.arm_refused", "The transient rod is armed only in pulse mode",
                    "Импульсный стержень взводится только в импульсном режиме"),
            MSG_RESET_REFUSED = PanelText.tr("triga.msg.reset_refused", "Scram cannot be reset: no pool of water under the bridge, failed fuel or a pulse running",
                    "Защиту не снять: нет бассейна под мостом, твэлы повреждены или идёт импульс"),
            MSG_PULSE = PanelText.tr("triga.msg.pulse", "Pulse: peak %s MW · %s MJ · FWHM %s ms · fuel %s °C",
                    "Импульс: пик %s МВт · %s МДж · ПШПВ %s мс · топливо %s °C"),
            MSG_FUEL = PanelText.tr("triga.msg.fuel", "TRIGA fuel failed at %s °C: hydrogen pressure split the cladding",
                    "Твэлы TRIGA повреждены при %s °C: водород разорвал оболочки"),
            MSG_SCRAM = PanelText.tr("triga.msg.scram", "TRIGA scram: %s", "АЗ TRIGA: %s");
    public static final Tr[] REASONS = {PanelText.tr("triga.reason.manual", "button", "кнопка"),
            PanelText.tr("triga.reason.short_period", "short period", "малый период"),
            PanelText.tr("triga.reason.high_power", "high power", "превышение мощности"),
            PanelText.tr("triga.reason.pulse_timer", "pulse timer", "таймер импульса")};

    public static final PanelLayout TRIGA = PanelLayout.builder("transient_rod_drive", 240, 200, T_CHANNELS)
            .dial(8, 24, true, T_POWER_DIAL, 10, 12, 9, 0, DECADES.get(), POWER.get(), v -> Component.literal(power(v.raw(T_POWER_LOG100))))
            .dial(84, 24, true, T_FUEL_C, 1, 1100, 1000, 0, PanelText.CELSIUS.get(), FUEL.get(), v -> FUEL_TIP.get(v.raw(T_FUEL_C)))
            .lamp(162, 24, T_FLAGS, TF_SCRAM, LampColor.RED, SCRAM_LAMP.get())
            .lamp(162, 38, T_FLAGS, TF_PULSE_MODE, LampColor.AMBER, PULSE_MODE.get())
            .lamp(162, 52, T_FLAGS, TF_ARMED, LampColor.AMBER, ARMED.get())
            .lamp(162, 66, T_FLAGS, TF_PULSING, LampColor.WHITE, PULSING.get())
            .lamp(162, 80, T_FLAGS, TF_FUEL_FAILED, LampColor.RED, FUEL_FAILED.get())
            .lamp(162, 94, T_FLAGS, TF_NO_POOL, LampColor.RED, NO_POOL.get())
            .readout(8, 104, 150, TrigaPanels::readLine, null)
            .readout(8, 116, 224, TrigaPanels::lastLine, null)
            .bar(8, 130, 108, T_CONTROL_PCT, 1, 100, 0xFF707070, CONTROL.get(), v -> Component.literal(v.raw(T_CONTROL_PCT) + " %"))
            .bar(124, 130, 108, T_TRANSIENT_PCT, 1, 100, 0xFFB03020, TRANSIENT.get(), v -> Component.literal(v.raw(T_TRANSIENT_PCT) + " %"))
            .button(8, 146, 40, BTN_DOWN, DOWN.get(), DRIVE_TIP.get())
            .button(50, 146, 40, BTN_STOP, STOP.get(), DRIVE_TIP.get())
            .button(92, 146, 40, BTN_UP, UP.get(), DRIVE_TIP.get())
            .button(140, 146, 44, BTN_SCRAM, SCRAM.get(), null)
            .button(188, 146, 44, BTN_RESET, RESET.get(), RESET_TIP.get())
            .button(8, 166, 56, BTN_MODE, MODE.get(), MODE_TIP.get())
            .button(68, 166, 36, BTN_LESS, LESS.get(), CENTS_TIP.get())
            .button(106, 166, 36, BTN_MORE, MORE.get(), CENTS_TIP.get())
            .button(146, 166, 40, BTN_ARM, ARM.get(), ARM_TIP.get())
            .button(188, 166, 44, BTN_FIRE, FIRE.get(), FIRE_TIP.get())
            .noInventory()
            .build();

    private TrigaPanels() {
    }

    /** Логарифмический канал: сотые доли декады; {@link #NONE} для нуля. */
    static int log100(double v) {
        return v > 0 ? (int) Math.round(100 * Math.log10(v)) : NONE;
    }

    static String power(int log100) {
        if (log100 == NONE) return "—";
        return si(Math.pow(10, log100 / 100.0)) + "W";
    }

    /** 3 значащие цифры с приставкой СИ (м…Т). */
    static String si(double v) {
        String[] p = {"m", "", "k", "M", "G", "T"};
        int e = (int) Math.floor(Math.log10(Math.max(v, 1e-3)) / 3);
        e = Math.max(-1, Math.min(4, e));
        double m = v / Math.pow(1000, e);
        return String.format(Locale.ROOT, m < 10 ? "%.2f " : m < 100 ? "%.1f " : "%.0f ", m) + p[e + 1];
    }

    private static Component readLine(PanelValues v) {
        int t = v.raw(T_PERIOD10);
        Component period = t == PERIOD_INF || t == NONE ? STEADY.get() : SECONDS.get(PanelText.num(t / 10.0, 1));
        return READ.get(power(v.raw(T_POWER_LOG100)), period, v.raw(T_PULSE_CENTS));
    }

    private static Component lastLine(PanelValues v) {
        if (v.raw(T_PEAK_LOG100) == NONE) return NO_PULSE.get();
        return LAST.get(power(v.raw(T_PEAK_LOG100)), v.num(T_ENERGY_KJ, 1000, 1), v.num(T_FWHM_MS10, 10, 1), v.raw(T_PEAK_FUEL_C));
    }
}
