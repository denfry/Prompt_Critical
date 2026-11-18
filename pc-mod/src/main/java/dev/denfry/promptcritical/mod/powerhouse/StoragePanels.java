package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.hydraulics.PumpedStorage;
import dev.denfry.promptcritical.sim.thermal.ElectrodeBoiler;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 накопителей и управляемых нагрузок: ГАЭС (ваттметр, напор, заполнение верхнего бассейна, лампы «насос» и
 * «турбина», ключи режимов и задания турбины) и электродного котла (ваттметр, уровень на электродах, лампы «сеть»,
 * «пар», «нагрузка», ключи режимов и задания). Каналы общие, целые.
 */
public final class StoragePanels {
    public static final int KW10 = 0, FILL_PERMIL = 1, HEAD_DM = 2, HZ100 = 3, RATED_KW = 4, SETPOINT = 5, MODE = 6, KWH_IN = 7,
            KWH_OUT = 8, STORED_KWH = 9, AREA = 10, FLAGS = 11, CHANNELS = 12;
    /** ГАЭС: паспорт есть, насос, турбина; котёл: пар есть куда отдать, нагрузка, сеть есть. */
    public static final int F_READY = 0, F_PUMP = 1, F_GEN = 2;
    /** Кнопки 0…3 — режимы по порядку перечисления; эти — задание. */
    public static final int BUTTON_LESS = 10, BUTTON_MORE = 11;

    private static final int WATER = 0xFF3A6EA5;
    private static final Tr KW = PanelText.tr("unit.kw", "kW", "кВт"),
            METRES = PanelText.tr("unit.m", "m", "м"),
            POWER = PanelText.tr("storage.power", "Power", "Мощность"),
            HEAD = PanelText.tr("storage.head", "Head", "Напор"),
            UPPER = PanelText.tr("storage.upper", "Upper", "Верхний"),
            PUMP = PanelText.tr("storage.pump", "Pump", "Насос"),
            TURBINE = PanelText.tr("storage.turbine", "Turbine", "Турбина"),
            IDLE = PanelText.tr("storage.idle", "Stop", "Стоп"),
            AUTO = PanelText.tr("storage.auto", "Auto", "Автомат"),
            LESS = PanelText.tr("storage.less", "−10 %", "−10 %"),
            MORE = PanelText.tr("storage.more", "+10 %", "+10 %"),
            IDLE_TIP = PanelText.tr("storage.idle_tip", "Unit stopped", "Агрегат остановлен"),
            PUMP_TIP = PanelText.tr("storage.pump_tip", "Pump water up at full speed: in pumping the power is not adjustable",
                    "Качать воду наверх: в насосном режиме мощность не регулируется"),
            TURBINE_TIP = PanelText.tr("storage.turbine_tip", "Generate at the set power", "Вырабатывать с заданной мощностью"),
            AUTO_TIP = PanelText.tr("storage.auto_tip", "Pump above 50.2 Hz, generate below 49.8 Hz with a 4 %% droop",
                    "Качать выше 50,2 Гц, вырабатывать ниже 49,8 Гц со статизмом 4 %%"),
            SETPOINT_TIP = PanelText.tr("storage.setpoint_tip", "Set point %s %%", "Задание %s %%"),
            UPPER_TIP = PanelText.tr("storage.upper_tip", "Upper basin %s %% · %s m² · about %s kWh to generate",
                    "Верхний бассейн %s %% · %s м² · около %s кВт·ч к выработке"),
            PSH_STATUS = PanelText.tr("storage.psh_status", "%s · %s Hz · unit %s kW · pumped %s kWh · generated %s kWh",
                    "%s · %s Гц · агрегат %s кВт · закачано %s кВт·ч · выработано %s кВт·ч"),
            NO_BASIN = PanelText.tr("storage.no_basin", "No upper basin: put a dam gate in water at least 10 m above the unit",
                    "Нет верхнего бассейна: поставьте затвор водозабора в воду не ниже 10 м над агрегатом"),
            MAINS = PanelText.tr("storage.mains", "Mains", "Сеть"),
            STEAM = PanelText.tr("storage.steam", "Steam", "Пар"),
            LOAD = PanelText.tr("storage.load", "Load", "Нагрузка"),
            ELECTRODES = PanelText.tr("storage.electrodes", "Electrodes", "Электроды"),
            OFF = PanelText.tr("storage.off", "Off", "Откл."),
            MANUAL = PanelText.tr("storage.manual", "Manual", "Ручной"),
            OFF_TIP = PanelText.tr("storage.off_tip", "Electrodes dry: no load", "Электроды осушены: нагрузки нет"),
            MANUAL_TIP = PanelText.tr("storage.manual_tip", "Hold the water on the electrodes at the set point", "Держать уровень на электродах по заданию"),
            ABSORB_TIP = PanelText.tr("storage.absorb_tip", "Absorb surplus: from 50.2 Hz up to full load at 51 Hz",
                    "Сброс излишка: от 50,2 Гц до полной нагрузки при 51 Гц"),
            LEVEL_TIP = PanelText.tr("storage.level_tip", "Water on the electrodes %s %%: power goes with the wetted length",
                    "Вода на электродах %s %%: мощность растёт с погружённой длиной"),
            BOILER_STATUS = PanelText.tr("storage.boiler_status", "%s · %s Hz · set point %s %% · rated %s kW",
                    "%s · %s Гц · задание %s %% · номинал %s кВт"),
            NO_STEAM = PanelText.tr("storage.no_steam", "No boiler to take the steam: put it against a boiler of the boiler house",
                    "Пар некуда отдать: поставьте котёл вплотную к котлу котельной");

    public static final PanelLayout PSH = PanelLayout.builder("pumped_storage_unit", 232, 128, CHANNELS)
            .dial(8, 24, false, KW10, 10, 1100, Double.NaN, 0, KW.get(), POWER.get(), null)
            .dial(64, 24, false, HEAD_DM, 10, 100, Double.NaN, 1, METRES.get(), HEAD.get(), null)
            .glass(120, 24, 56, FILL_PERMIL, WATER, Double.NaN, Double.NaN, UPPER.get(),
                    v -> UPPER_TIP.get(v.num(FILL_PERMIL, 10, 0), v.raw(AREA), v.raw(STORED_KWH)))
            .lamp(146, 26, FLAGS, F_PUMP, LampColor.AMBER, PUMP.get())
            .lamp(146, 42, FLAGS, F_GEN, LampColor.GREEN, TURBINE.get())
            .button(8, 84, 40, PumpedStorage.Mode.IDLE.ordinal(), IDLE.get(), IDLE_TIP.get())
            .button(52, 84, 40, PumpedStorage.Mode.PUMP.ordinal(), PUMP.get(), PUMP_TIP.get())
            .button(96, 84, 44, PumpedStorage.Mode.GENERATE.ordinal(), TURBINE.get(), TURBINE_TIP.get())
            .button(144, 84, 40, PumpedStorage.Mode.AUTO.ordinal(), AUTO.get(), AUTO_TIP.get())
            .button(188, 60, 36, BUTTON_LESS, LESS.get(), null)
            .button(188, 84, 36, BUTTON_MORE, MORE.get(), null)
            .readout(8, 108, 216, StoragePanels::psh, v -> SETPOINT_TIP.get(v.raw(SETPOINT)))
            .noInventory().build();

    public static final PanelLayout ELECTRODE = PanelLayout.builder("electrode_boiler", 232, 128, CHANNELS)
            .dial(8, 24, false, KW10, 10, 1100, Double.NaN, 0, KW.get(), POWER.get(), null)
            .glass(64, 24, 56, FILL_PERMIL, WATER, Double.NaN, Double.NaN, ELECTRODES.get(),
                    v -> LEVEL_TIP.get(v.num(FILL_PERMIL, 10, 0)))
            .lamp(92, 26, FLAGS, F_PUMP, LampColor.GREEN, MAINS.get())
            .lamp(92, 42, FLAGS, F_READY, LampColor.GREEN, STEAM.get())
            .lamp(92, 58, FLAGS, F_GEN, LampColor.AMBER, LOAD.get())
            .button(8, 84, 44, ElectrodeBoiler.Mode.OFF.ordinal(), OFF.get(), OFF_TIP.get())
            .button(56, 84, 52, ElectrodeBoiler.Mode.MANUAL.ordinal(), MANUAL.get(), MANUAL_TIP.get())
            .button(112, 84, 44, ElectrodeBoiler.Mode.AUTO.ordinal(), AUTO.get(), ABSORB_TIP.get())
            .button(188, 60, 36, BUTTON_LESS, LESS.get(), null)
            .button(188, 84, 36, BUTTON_MORE, MORE.get(), null)
            .readout(8, 108, 216, StoragePanels::boiler, v -> SETPOINT_TIP.get(v.raw(SETPOINT)))
            .noInventory().build();

    private StoragePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(PSH, ELECTRODE);
    }

    private static Component psh(PanelValues v) {
        if (!v.bit(FLAGS, F_READY)) return NO_BASIN.get().withStyle(ChatFormatting.GOLD);
        Tr mode = switch (Math.floorMod(v.raw(MODE), 4)) {
            case 0 -> IDLE;
            case 1 -> PUMP;
            case 2 -> TURBINE;
            default -> AUTO;
        };
        return PSH_STATUS.get(mode.get(), v.num(HZ100, 100, 2), v.raw(RATED_KW), v.raw(KWH_IN), v.raw(KWH_OUT));
    }

    private static Component boiler(PanelValues v) {
        if (!v.bit(FLAGS, F_READY)) return NO_STEAM.get().withStyle(ChatFormatting.GOLD);
        Tr mode = switch (Math.floorMod(v.raw(MODE), 3)) {
            case 0 -> OFF;
            case 1 -> MANUAL;
            default -> AUTO;
        };
        return BOILER_STATUS.get(mode.get(), v.num(HZ100, 100, 2), v.raw(SETPOINT), v.raw(RATED_KW));
    }
}
