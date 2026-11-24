package dev.denfry.promptcritical.mod.hydrogen;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты водородной станции. Фоны нарисованы под эти раскладки (art/blockbench/hydrogen/h2_panels.author.js, координаты —
 * h2_panels.md): при переносе приборов перерисуйте их. Щит рампы и газгольдера общий.
 */
public final class HydrogenPanels {
    // электролизёр
    public static final int E_AMPS = 0, E_VOLTS = 1, E_H2_GLASS = 2, E_O2_GLASS = 3, E_TEMP_C10 = 4, E_KOH_PERMIL = 5, E_HTO_PPM = 6,
            E_NM3H = 7, E_KWH100 = 8, E_TRIP = 9, E_FLAGS = 10, E_CHANNELS = 11;
    public static final int F_RUN = 0, F_HTO_ALARM = 1, F_TRIP = 2, F_PURGE = 3, F_NO_COOLING = 4, F_NO_WATER = 5, F_NO_LINE = 6,
            F_NO_POWER = 7;
    public static final int BUTTON_START = 0, BUTTON_STOP = 1, BUTTON_PURGE = 2, BUTTON_VENT = 3;
    // компрессор
    public static final int C_SUCTION_KPA = 0, C_DISCHARGE_BAR10 = 1, C_STAGE_C = 2, C_NM3H = 3, C_KW = 4, C_FLAGS = 5, C_CHANNELS = 6;
    public static final int F_LOADED = 8, F_NO_SUCTION = 9, F_NO_DISCHARGE = 10;
    // сосуд
    public static final int S_BAR100 = 0, S_NM3 = 1, S_MAX_NM3 = 2, S_TEMP_C = 3, S_KG10 = 4, S_MAX_BAR100 = 5, S_FLAGS = 6, S_LEL_PCT = 7,
            S_CHANNELS = 8;
    public static final int F_RELIEF = 0, F_BURST = 1, F_VENT = 2, F_LEAK = 3;
    // топливный элемент
    public static final int FC_H2_BAR10 = 0, FC_VOLTS = 1, FC_KW = 2, FC_WATER_LH = 3, FC_TEMP_C = 4, FC_EFF_PCT = 5, FC_TRIP = 6,
            FC_FLAGS = 7, FC_CHANNELS = 8;
    public static final int F_FC_RUN = 0, F_FC_FAULT = 1, F_FC_GRID = 2, F_FC_LINE = 3;

    private static final int H2_COLOR = 0xFFD8E4EE, O2_COLOR = 0xFF9FC3E6, HEAT = 0xFFD06A2C, GAS = 0xFF6FA86A;
    private static final Tr AMPS = PanelText.tr("h2.amps", "kA", "кА"),
            VOLTS = PanelText.tr("h2.volts", "V", "В"),
            BAR = PanelText.tr("h2.bar", "bar", "бар"),
            KPA = PanelText.tr("h2.kpa", "kPa", "кПа"),
            DC_CURRENT = PanelText.tr("h2.dc_current", "DC current", "Ток"),
            DC_VOLTAGE = PanelText.tr("h2.dc_voltage", "Stack voltage", "Напряжение"),
            H2_SEP = PanelText.tr("h2.h2_sep", "H₂", "H₂"),
            O2_SEP = PanelText.tr("h2.o2_sep", "O₂", "O₂"),
            SEP_TIP = PanelText.tr("h2.sep_tip", "Separator lye level %s %%", "Уровень щёлочи в сепараторе %s %%"),
            STACK_T = PanelText.tr("h2.stack_t", "Stack temperature", "Температура батареи"),
            STACK_T_TIP = PanelText.tr("h2.stack_t_tip", "%s °C · held at 80 °C, trip at 90 °C", "%s °C · держится 80 °C, отключение 90 °C"),
            HTO = PanelText.tr("h2.hto", "H₂ in O₂", "H₂ в O₂"),
            START = PanelText.tr("h2.start", "Start", "Пуск"),
            STOP = PanelText.tr("h2.stop", "Stop", "Стоп"),
            PURGE = PanelText.tr("h2.purge", "N₂ purge", "Продувка"),
            PURGE_TIP = PanelText.tr("h2.purge_tip", "Purge the stack with nitrogen for two minutes: clears a trip once its cause is gone",
                    "Продуть батарею азотом две минуты: снимает отключение, если его причина ушла"),
            E_STATUS = PanelText.tr("h2.e_status", "KOH %s %% · H₂ in O₂ %s %% · %s Nm³/h · %s kWh/Nm³",
                    "KOH %s %% · H₂ в O₂ %s %% · %s Нм³/ч · %s кВт·ч/Нм³"),
            NO_LINE = PanelText.tr("h2.no_line", "No gas line: hydrogen goes to the vent", "Нет газовой линии: водород уходит в свечу"),
            NO_COOLING = PanelText.tr("h2.no_cooling", "No cooling water at the lye cooler", "Нет воды охлаждения на охладителе щёлочи"),
            NO_WATER = PanelText.tr("h2.no_water", "No demineralised water: the lye gets stronger", "Нет обессоленной воды: щёлочь крепчает"),
            NO_POWER = PanelText.tr("h2.no_power", "No 6.3 kV supply to the rectifier", "Нет питания 6,3 кВ на выпрямителе"),
            PURGING = PanelText.tr("h2.purging", "Nitrogen purge…", "Продувка азотом…"),
            SUCTION = PanelText.tr("h2.suction", "Suction", "Всас"),
            DISCHARGE = PanelText.tr("h2.discharge", "Discharge", "Нагнетание"),
            STAGE_T = PanelText.tr("h2.stage_t", "Stage temp.", "Темп. ступени"),
            STAGE_T_TIP = PanelText.tr("h2.stage_t_tip", "%s °C at the hotter stage · trip at 230 °C", "%s °C на горячей ступени · отключение 230 °C"),
            RUN = PanelText.tr("h2.run", "Run", "Работа"),
            C_STATUS = PanelText.tr("h2.c_status", "%s · %s Nm³/h · %s kW", "%s · %s Нм³/ч · %s кВт"),
            LOADED = PanelText.tr("h2.loaded", "Compressing", "Сжимает"),
            UNLOADED = PanelText.tr("h2.unloaded", "Standby on the pressure switch", "Ждёт по реле давления"),
            NO_SUCTION = PanelText.tr("h2.no_suction", "No suction line to a gasholder", "Нет линии всаса к газгольдеру"),
            NO_DISCHARGE = PanelText.tr("h2.no_discharge", "No discharge line to a tube bank", "Нет линии нагнетания к рампе"),
            PRESSURE = PanelText.tr("h2.pressure", "Pressure", "Давление"),
            STORED = PanelText.tr("h2.stored", "Stored", "Запас"),
            STORED_TIP = PanelText.tr("h2.stored_tip", "%s Nm³ of %s · %s kg", "%s Нм³ из %s · %s кг"),
            OVERPRESSURE = PanelText.tr("h2.overpressure", "Relief", "Сброс"),
            VENT = PanelText.tr("h2.vent", "Vent valve", "Клапан на свечу"),
            VENT_TIP = PanelText.tr("h2.vent_tip", "Open or close the vent to the stack", "Открыть или закрыть сброс на свечу"),
            S_STATUS = PanelText.tr("h2.s_status", "%s °C · %s", "%s °C · %s"),
            S_OK = PanelText.tr("h2.s_ok", "normal", "норма"),
            S_RELIEF = PanelText.tr("h2.s_relief", "relief valve open", "открыт предохранительный клапан"),
            S_BURST = PanelText.tr("h2.s_burst", "burst disc open: replace it (Shift + right click)", "мембрана разорвана: замените (Shift + ПКМ)"),
            S_VENT = PanelText.tr("h2.s_vent", "venting to the stack", "сброс на свечу"),
            S_LEAK = PanelText.tr("h2.s_leak", "LEAK: open line end · H₂ in air %s %% LEL", "УТЕЧКА: открытый конец линии · H₂ в воздухе %s %% НКПР"),
            S_GAS = PanelText.tr("h2.s_gas", "GAS ALARM: H₂ in air %s %% LEL", "ЗАГАЗОВАННОСТЬ: H₂ в воздухе %s %% НКПР"),
            H2_IN = PanelText.tr("h2.h2_in", "H₂ in", "H₂ на входе"),
            STACK_V = PanelText.tr("h2.stack_v", "Stack", "Батарея"),
            AC_OUT = PanelText.tr("h2.ac_out", "AC out", "Выдача"),
            AC_TIP = PanelText.tr("h2.ac_tip", "%s kW of 200 · LHV efficiency %s %%", "%s кВт из 200 · КПД по НТС %s %%"),
            WATER_OUT = PanelText.tr("h2.water_out", "%s L/h water", "%s л/ч воды"),
            FAULT = PanelText.tr("h2.fault", "Fault", "Авария"),
            FC_STATUS = PanelText.tr("h2.fc_status", "%s · stack %s °C", "%s · батарея %s °C"),
            FC_RUNNING = PanelText.tr("h2.fc_running", "Generating", "Выдаёт"),
            FC_STOPPED = PanelText.tr("h2.fc_stopped", "Stopped", "Остановлен"),
            FC_NO_GRID = PanelText.tr("h2.fc_no_grid", "No grid at the inverter", "Нет сети на инверторе"),
            FC_NO_LINE = PanelText.tr("h2.fc_no_line", "No hydrogen line to a tube bank", "Нет линии водорода к рампе"),
            TRIP_HTO = PanelText.tr("h2.trip.hto", "TRIP: 2 % hydrogen in oxygen — purge", "ОТКЛ.: 2 % водорода в кислороде — продуть"),
            TRIP_TEMP = PanelText.tr("h2.trip.temp", "TRIP: overtemperature", "ОТКЛ.: перегрев"),
            TRIP_PRESSURE = PanelText.tr("h2.trip.pressure", "TRIP: gasholder pressure too high", "ОТКЛ.: высокое давление в газгольдере"),
            TRIP_LYE = PanelText.tr("h2.trip.lye", "TRIP: lye too strong, add water", "ОТКЛ.: щёлочь слишком крепкая, нужна вода"),
            TRIP_LOW_H2 = PanelText.tr("h2.trip.low_h2", "TRIP: hydrogen below 3 bar", "ОТКЛ.: водород ниже 3 бар");

    public static final PanelLayout ELECTROLYSER = PanelLayout.builder("h2_electrolyser", 220, 170, E_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/h2_electrolyser.png"))
            .dial(10, 24, true, E_AMPS, 1000, 6, 5.6, 2, AMPS.get(), DC_CURRENT.get(), null)
            .dial(84, 32, false, E_VOLTS, 1, 600, 520, 0, VOLTS.get(), DC_VOLTAGE.get(), null)
            .glass(150, 26, 64, E_H2_GLASS, H2_COLOR, 0.3, 0.6, H2_SEP.get(), v -> SEP_TIP.get(v.num(E_H2_GLASS, 10, 0)))
            .glass(182, 26, 64, E_O2_GLASS, O2_COLOR, 0.3, 0.6, O2_SEP.get(), v -> SEP_TIP.get(v.num(E_O2_GLASS, 10, 0)))
            .bar(12, 112, 128, E_TEMP_C10, 10, 100, HEAT, STACK_T.get(), v -> STACK_T_TIP.get(v.num(E_TEMP_C10, 10, 1)))
            .readout(12, 126, 128, HydrogenPanels::electrolyserStatus, null)
            .lamp(150, 110, E_FLAGS, F_HTO_ALARM, LampColor.RED, HTO.get())
            .button(12, 146, 60, BUTTON_START, START.get(), null)
            .button(78, 146, 60, BUTTON_STOP, STOP.get(), null)
            .button(144, 146, 64, BUTTON_PURGE, PURGE.get(), PURGE_TIP.get())
            .noInventory().build();

    public static final PanelLayout COMPRESSOR = PanelLayout.builder("h2_compressor", 220, 130, C_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/h2_compressor.png"))
            .dial(14, 26, false, C_SUCTION_KPA, 1, 200, 150, 0, KPA.get(), SUCTION.get(), null)
            .dial(78, 26, false, C_DISCHARGE_BAR10, 10, 250, 200, 0, BAR.get(), DISCHARGE.get(), null)
            .bar(138, 40, 70, C_STAGE_C, 1, 250, HEAT, STAGE_T.get(), v -> STAGE_T_TIP.get(v.raw(C_STAGE_C)))
            .lamp(138, 58, C_FLAGS, F_LOADED, LampColor.GREEN, RUN.get())
            .readout(12, 88, 196, HydrogenPanels::compressorStatus, null)
            .button(12, 108, 96, BUTTON_START, START.get(), null)
            .button(112, 108, 96, BUTTON_STOP, STOP.get(), null)
            .noInventory().build();

    public static final PanelLayout STORAGE = PanelLayout.builder("h2_storage", 220, 120, S_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/h2_storage.png"))
            .dial(12, 24, true, S_BAR100, 100, 250, 200, 1, BAR.get(), PRESSURE.get(), null)
            .bar(88, 40, 120, S_NM3, 1, 2500, GAS, STORED.get(),
                    v -> STORED_TIP.get(v.raw(S_NM3), v.raw(S_MAX_NM3), v.num(S_KG10, 10, 1)))
            .lamp(88, 54, S_FLAGS, F_RELIEF, LampColor.RED, OVERPRESSURE.get())
            .readout(88, 76, 120, HydrogenPanels::storageStatus, null)
            .button(88, 96, 120, BUTTON_VENT, VENT.get(), VENT_TIP.get())
            .noInventory().build();

    public static final PanelLayout FUEL_CELL = PanelLayout.builder("h2_fuel_cell", 220, 136, FC_CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/h2_fuel_cell.png"))
            .dial(12, 26, false, FC_H2_BAR10, 10, 250, 200, 0, BAR.get(), H2_IN.get(), null)
            .dial(70, 26, false, FC_VOLTS, 1, 700, Double.NaN, 0, VOLTS.get(), STACK_V.get(), null)
            .bar(128, 36, 80, FC_KW, 1, 200, GAS, AC_OUT.get(), v -> AC_TIP.get(v.raw(FC_KW), v.raw(FC_EFF_PCT)))
            .readout(128, 50, 80, v -> WATER_OUT.get(v.raw(FC_WATER_LH)), null)
            .lamp(128, 68, FC_FLAGS, F_FC_FAULT, LampColor.RED, FAULT.get())
            .readout(12, 94, 196, HydrogenPanels::fuelCellStatus, null)
            .button(12, 114, 96, BUTTON_START, RUN.get(), null)
            .button(112, 114, 96, BUTTON_STOP, STOP.get(), null)
            .noInventory().build();

    private HydrogenPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(ELECTROLYSER, COMPRESSOR, STORAGE, FUEL_CELL);
    }

    private static Component electrolyserStatus(PanelValues v) {
        int trip = v.raw(E_TRIP);
        if (trip != 0) return tripText(trip).get().withStyle(ChatFormatting.RED);
        if (v.bit(E_FLAGS, F_PURGE)) return PURGING.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(E_FLAGS, F_NO_POWER)) return NO_POWER.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(E_FLAGS, F_NO_LINE)) return NO_LINE.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(E_FLAGS, F_NO_COOLING)) return NO_COOLING.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(E_FLAGS, F_NO_WATER)) return NO_WATER.get().withStyle(ChatFormatting.GOLD);
        return E_STATUS.get(v.num(E_KOH_PERMIL, 10, 1), v.num(E_HTO_PPM, 10_000, 2), v.raw(E_NM3H), v.num(E_KWH100, 100, 2));
    }

    /** Текст отключения по коду: 1 HTO, 2 перегрев, 3 давление, 4 щёлочь, 5 мало водорода. */
    static Tr tripText(int code) {
        return switch (code) {
            case 1 -> TRIP_HTO;
            case 2 -> TRIP_TEMP;
            case 3 -> TRIP_PRESSURE;
            case 4 -> TRIP_LYE;
            default -> TRIP_LOW_H2;
        };
    }

    private static Component compressorStatus(PanelValues v) {
        if (v.bit(C_FLAGS, F_TRIP)) return TRIP_TEMP.get().withStyle(ChatFormatting.RED);
        if (v.bit(C_FLAGS, F_NO_SUCTION)) return NO_SUCTION.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(C_FLAGS, F_NO_DISCHARGE)) return NO_DISCHARGE.get().withStyle(ChatFormatting.GOLD);
        if (v.bit(C_FLAGS, F_NO_COOLING)) return NO_COOLING.get().withStyle(ChatFormatting.GOLD);
        return C_STATUS.get((v.bit(C_FLAGS, F_LOADED) ? LOADED : UNLOADED).get(), v.raw(C_NM3H), v.raw(C_KW));
    }

    private static Component storageStatus(PanelValues v) {
        if (v.bit(S_FLAGS, F_LEAK)) return S_LEAK.get(v.raw(S_LEL_PCT)).withStyle(ChatFormatting.RED);
        if (v.raw(S_LEL_PCT) >= 25) return S_GAS.get(v.raw(S_LEL_PCT)).withStyle(ChatFormatting.RED);
        Tr s = v.bit(S_FLAGS, F_BURST) ? S_BURST : v.bit(S_FLAGS, F_RELIEF) ? S_RELIEF : v.bit(S_FLAGS, F_VENT) ? S_VENT : S_OK;
        return S_STATUS.get(v.raw(S_TEMP_C), s.get());
    }

    private static Component fuelCellStatus(PanelValues v) {
        int trip = v.raw(FC_TRIP);
        if (trip != 0) return tripText(trip).get().withStyle(ChatFormatting.RED);
        if (!v.bit(FC_FLAGS, F_FC_LINE)) return FC_NO_LINE.get().withStyle(ChatFormatting.GOLD);
        if (!v.bit(FC_FLAGS, F_FC_GRID)) return FC_NO_GRID.get().withStyle(ChatFormatting.GOLD);
        return FC_STATUS.get((v.bit(FC_FLAGS, F_FC_RUN) ? FC_RUNNING : FC_STOPPED).get(), v.raw(FC_TEMP_C));
    }
}
