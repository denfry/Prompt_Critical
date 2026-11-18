package dev.denfry.promptcritical.mod.refinery;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Щиты подготовки и углубления переработки НПЗ (09 §9.7.3, §9.9.2–9.9.5, §9.9.13–9.9.14, §9.10.3): ЭЛОУ, вакуумная
 * колонна, каткрекинг, аминовая очистка, станция смешения бензина, лаборатория и поточный анализатор. Подписи приборов
 * — имена тегов периферии CC.
 */
public final class ConversionPanels {
    /** Общие биты флагов и кнопки. */
    public static final int F_ON = 0, F_RUN = 1, F_WARN = 2, F_AUX = 3, F_ALARM = 4;
    public static final int B_RUN = 0, B_DOWN = 1, B_UP = 2, B_DOWN2 = 3, B_UP2 = 4, B_ACTION = 5, B_ACTION2 = 6;

    private static final Tr RUN = PanelText.tr("conv.run", "Run", "Пуск/стоп"), ON = PanelText.tr("conv.on", "On", "Вкл."),
            WORKING = PanelText.tr("conv.working", "Working", "Работа"), PCT = PanelText.tr("conv.pct", "%", "%");

    // ------------------------------------------------------------------ ЭЛОУ (09 §9.7.3)
    public static final int DS_SALT_X100 = 0, DS_WATER_X100 = 1, DS_DP_KPA = 2, DS_FEED_LH = 3, DS_TANK = 4, DS_STAGE1_X10 = 5,
            DS_FLAGS = 6, DS_CHANNELS = 7;
    private static final Tr MGL = PanelText.tr("conv.mgl", "mg/l", "мг/л"), SALT = PanelText.tr("conv.salt_out", "Salt out", "Соль на выходе"),
            WATER = PanelText.tr("conv.water_out", "Water in crude", "Вода в нефти"), KPA = PanelText.tr("conv.kpa", "kPa", "кПа"),
            DP = PanelText.tr("conv.mix_dp", "Mixing valve", "Смесительный клапан"),
            BRINE = PanelText.tr("conv.brine", "Brine", "Солёная вода"),
            FIELD = PanelText.tr("conv.field", "Electrodes energised", "Электроды под напряжением"),
            SALT_HIGH = PanelText.tr("conv.salt_high", "Salt above 5 mg/l", "Соль выше 5 мг/л"),
            NO_FURNACE = PanelText.tr("conv.no_furnace", "No tube furnace within 6 blocks", "Нет трубчатой печи в 6 блоках"),
            DP_DOWN = PanelText.tr("conv.dp_down", "Valve Δp −10 kPa", "Перепад клапана −10 кПа"),
            DP_UP = PanelText.tr("conv.dp_up", "Valve Δp +10 kPa", "Перепад клапана +10 кПа"),
            DESALTER_STATUS = PanelText.tr("conv.desalter_status", "Crude %s l/h · after stage 1 %s mg/l · water %s %%",
                    "Нефть %s л/ч · после 1-й ступени %s мг/л · вода %s %%");

    public static final PanelLayout DESALTER = PanelLayout.builder("electrostatic_desalter", 232, 140, DS_CHANNELS)
            .dial(8, 24, false, DS_SALT_X100, 100, 20, 5, 1, MGL.get(), SALT.get(), null)
            .dial(64, 24, false, DS_WATER_X100, 100, 2, 0.5, 2, PCT.get(), WATER.get(), null)
            .dial(120, 24, false, DS_DP_KPA, 1, 160, Double.NaN, 0, KPA.get(), DP.get(), null)
            .glass(176, 24, 56, DS_TANK, 0xFF4A6A8A, Double.NaN, Double.NaN, BRINE.get(), null)
            .lamp(196, 22, DS_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(196, 36, DS_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(196, 50, DS_FLAGS, F_AUX, LampColor.GREEN, FIELD.get()).lamp(196, 64, DS_FLAGS, F_WARN, LampColor.AMBER, SALT_HIGH.get())
            .lamp(196, 78, DS_FLAGS, F_ALARM, LampColor.RED, NO_FURNACE.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), DP_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), DP_UP.get())
            .readout(8, 118, 216, v -> DESALTER_STATUS.get(v.raw(DS_FEED_LH), v.num(DS_STAGE1_X10, 10, 1), v.num(DS_WATER_X100, 100, 2)), null)
            .noInventory().build();

    // ------------------------------------------------------------------ вакуумная колонна (09 §9.9.2)
    public static final int VC_T_C = 0, VC_P_X10 = 1, VC_VGO_PERMIL = 2, VC_CCR_PERMIL = 3, VC_TANK = 4, VC_FEED_LH = 5, VC_FLASH_C = 6,
            VC_EJECTORS = 7, VC_FLAGS = 8, VC_CHANNELS = 9;
    private static final Tr HEATER = PanelText.tr("conv.heater", "Heater outlet", "Выход печи"),
            FLASH_P = PanelText.tr("conv.flash_p", "Flash zone", "Зона питания"),
            VGO_YIELD = PanelText.tr("conv.vgo_yield", "Gas oil yield", "Выход газойля"),
            VGO = PanelText.tr("conv.vgo", "Vacuum gas oil", "Вакуумный газойль"),
            VACUUM_OK = PanelText.tr("conv.vacuum_ok", "Vacuum held", "Вакуум есть"),
            NO_VACUUM = PanelText.tr("conv.no_vacuum", "No vacuum: ejectors or feedwater missing", "Нет вакуума: нет эжекторов или воды"),
            T_DOWN = PanelText.tr("conv.t_down", "−5 °C", "−5 °C"), T_UP = PanelText.tr("conv.t_up", "+5 °C", "+5 °C"),
            VACUUM_STATUS = PanelText.tr("conv.vacuum_status", "Mazut %s l/h · cut to %s °C · tar CCR %s %% · ejector stages %s",
                    "Мазут %s л/ч · выкипает до %s °C · коксуемость гудрона %s %% · ступеней эжекторов %s");

    public static final PanelLayout VACUUM = PanelLayout.builder("vacuum_column", 232, 140, VC_CHANNELS)
            .dial(8, 24, false, VC_T_C, 1, 450, 420, 0, PanelText.CELSIUS.get(), HEATER.get(), null)
            .dial(64, 24, false, VC_P_X10, 10, 110, 20, 0, KPA.get(), FLASH_P.get(), null)
            .dial(120, 24, false, VC_VGO_PERMIL, 10, 100, Double.NaN, 0, PCT.get(), VGO_YIELD.get(), null)
            .glass(176, 24, 56, VC_TANK, 0xFF4A3420, Double.NaN, Double.NaN, VGO.get(), null)
            .lamp(196, 22, VC_FLAGS, F_ON, LampColor.GREEN, ON.get()).lamp(196, 36, VC_FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(196, 50, VC_FLAGS, F_AUX, LampColor.GREEN, VACUUM_OK.get()).lamp(196, 64, VC_FLAGS, F_WARN, LampColor.AMBER, NO_VACUUM.get())
            .button(8, 92, 44, B_RUN, RUN.get(), null)
            .button(56, 92, 20, B_DOWN, Component.literal("−"), T_DOWN.get()).button(78, 92, 20, B_UP, Component.literal("+"), T_UP.get())
            .readout(8, 118, 216, v -> VACUUM_STATUS.get(v.raw(VC_FEED_LH), v.raw(VC_FLASH_C), v.num(VC_CCR_PERMIL, 10, 1), v.raw(VC_EJECTORS)), null)
            .noInventory().build();

    private ConversionPanels() {
    }

    public static List<PanelLayout> all() {
        List<PanelLayout> out = new ArrayList<>();
        out.add(DESALTER);
        out.add(VACUUM);
        return List.copyOf(out);
    }
}
