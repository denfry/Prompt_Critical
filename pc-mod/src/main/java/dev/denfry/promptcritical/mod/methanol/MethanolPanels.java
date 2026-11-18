package dev.denfry.promptcritical.mod.methanol;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.sim.methanol.MethanolLoop;
import dev.denfry.promptcritical.sim.methanol.MethanolSynthesis;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Щиты A0 метанольного производства: агрегат синтеза (печь, контур, колонна синтеза) и ректификация. Каналы — целые.
 */
public final class MethanolPanels {
    // агрегат синтеза
    public static final int M_P_BAR = 0, M_BED_C = 1, M_OUT_MEOH = 2, M_EQ_MEOH = 3, M_SN = 4, M_MAKEUP_SN = 5, M_INERTS = 6,
            M_MEOH_KGH = 7, M_CRUDE = 8, M_PURGE = 9, M_CIRC = 10, M_STEAM_BAR = 11, M_STEAM_KGH = 12, M_SHAFT_KW = 13, M_CU = 14,
            M_NI = 15, M_REF_C = 16, M_SC = 17, M_FEED = 18, M_FLAGS = 19, M_CHANNELS = 20;
    public static final int MF_RUN = 0, MF_HOT = 1, MF_PMAX = 2, MF_DRY = 3, MF_NO_COLUMN = 4, MF_NO_FEED = 5, MF_NO_CU = 6, MF_NO_NI = 7,
            MF_CARBON = 8;
    public static final int MB_RUN = 0, MB_PURGE_DOWN = 1, MB_PURGE_UP = 2, MB_STEAM_DOWN = 3, MB_STEAM_UP = 4, MB_CIRC_DOWN = 5,
            MB_CIRC_UP = 6, MB_SC_DOWN = 7, MB_SC_UP = 8, MB_T_DOWN = 9, MB_T_UP = 10, MB_RATE_DOWN = 11, MB_RATE_UP = 12;
    // ректификация
    public static final int C_WATER_PPM = 0, C_REFLUX = 1, C_REFLUX_ACT = 2, C_FEED_KGH = 3, C_PRODUCT_KGH = 4, C_HEAT_KW = 5,
            C_NEED_KW = 6, C_CRUDE = 7, C_TANK = 8, C_FLAGS = 9, C_CHANNELS = 10;
    public static final int CF_RUN = 0, CF_OFF_SPEC = 1, CF_NO_HEAT = 2, CF_NO_CRUDE = 3;
    public static final int CB_RUN = 0, CB_REFLUX_DOWN = 1, CB_REFLUX_UP = 2;

    private static final Tr CELSIUS = PanelText.CELSIUS, BAR = PanelText.tr("unit.bar", "bar", "бар"),
            PPM = PanelText.tr("unit.ppm", "ppm", "ppm");
    private static final Tr RUN = PanelText.tr("methanol.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("methanol.working", "Working", "Работа");

    // --- агрегат синтеза ---
    private static final Tr LOOP_P = PanelText.tr("methanol.loop_p", "Loop", "Контур"),
            BED = PanelText.tr("methanol.bed", "Bed", "Слой"),
            COPPER = PanelText.tr("methanol.copper", "Cu", "Cu"),
            NICKEL = PanelText.tr("methanol.nickel", "Ni", "Ni"),
            HOT = PanelText.tr("methanol.hot", "Sintering", "Спекание"),
            PMAX = PanelText.tr("methanol.pmax", "P max", "P макс"),
            DRY = PanelText.tr("methanol.dry", "No water", "Нет воды"),
            NO_COLUMN = PanelText.tr("methanol.no_column", "No column", "Нет колонны"),
            NO_FEED = PanelText.tr("methanol.no_feed", "No gas", "Нет газа"),
            NO_CU = PanelText.tr("methanol.no_cu", "No Cu", "Нет Cu"),
            NO_NI = PanelText.tr("methanol.no_ni", "No Ni", "Нет Ni"),
            CARBON = PanelText.tr("methanol.carbon", "Carbon", "Углерод"),
            LOOP_TIP = PanelText.tr("methanol.loop_tip", "Loop %s bar · methanol out of the converter %s %%, equilibrium %s %% · inerts %s %%",
                    "Контур %s бар · метанол на выходе колонны %s %%, равновесие %s %% · инерты %s %%"),
            BED_TIP = PanelText.tr("methanol.bed_tip", "Catalyst %s °C, boiling water in the shell %s bar · copper sinters above %s °C",
                    "Катализатор %s °C, кипящая вода в межтрубье %s бар · медь спекается выше %s °C"),
            CU_TIP = PanelText.tr("methanol.cu_tip", "Copper catalyst activity %s %%", "Активность медного катализатора %s %%"),
            NI_TIP = PanelText.tr("methanol.ni_tip", "Reformer nickel activity %s %%: below steam to carbon 2.5 carbon grows on it",
                    "Активность никеля печи %s %%: при пар/газ ниже 2,5 на нём растёт углерод"),
            PURGE_LABEL = PanelText.tr("methanol.purge_label", "Purge", "Продувка"),
            STEAM_LABEL = PanelText.tr("methanol.steam_label", "Shell", "Пар"),
            CIRC_LABEL = PanelText.tr("methanol.circ_label", "Circ.", "Цирк."),
            SC_LABEL = PanelText.tr("methanol.sc_label", "S/C", "Пар/газ"),
            T_LABEL = PanelText.tr("methanol.t_label", "Tubes", "Трубы"),
            RATE_LABEL = PanelText.tr("methanol.rate_label", "Gas", "Газ"),
            PURGE_DOWN = PanelText.tr("methanol.purge_down", "Purge −40 nm³/h", "Продувка −40 нм³/ч"),
            PURGE_UP = PanelText.tr("methanol.purge_up", "Purge +40 nm³/h", "Продувка +40 нм³/ч"),
            STEAM_DOWN = PanelText.tr("methanol.steam_down", "Shell steam −2 bar: cooler bed", "Пар межтрубья −2 бар: слой холоднее"),
            STEAM_UP = PanelText.tr("methanol.steam_up", "Shell steam +2 bar: hotter bed", "Пар межтрубья +2 бар: слой горячее"),
            CIRC_DOWN = PanelText.tr("methanol.circ_down", "Circulation −10 %", "Циркуляция −10 %"),
            CIRC_UP = PanelText.tr("methanol.circ_up", "Circulation +10 %", "Циркуляция +10 %"),
            SC_DOWN = PanelText.tr("methanol.sc_down", "Steam to carbon −0.1", "Пар/газ −0,1"),
            SC_UP = PanelText.tr("methanol.sc_up", "Steam to carbon +0.1", "Пар/газ +0,1"),
            T_DOWN = PanelText.tr("methanol.t_down", "Reformer outlet −5 °C", "Выход труб печи −5 °C"),
            T_UP = PanelText.tr("methanol.t_up", "Reformer outlet +5 °C", "Выход труб печи +5 °C"),
            RATE_DOWN = PanelText.tr("methanol.rate_down", "Gas rate −10 %", "Расход газа −10 %"),
            RATE_UP = PanelText.tr("methanol.rate_up", "Gas rate +10 %", "Расход газа +10 %"),
            STATUS = PanelText.tr("methanol.status",
                    "Methanol %s kg/h, crude %s %% · S make-up %s, loop %s · gas %s nm³/h, tubes %s °C, S/C %s · purge %s nm³/h · steam %s kg/h · shaft %s kW",
                    "Метанол %s кг/ч, сырец %s %% · S свежего %s, в контуре %s · газ %s нм³/ч, трубы %s °C, пар/газ %s · продувка %s нм³/ч · пар %s кг/ч · вал %s кВт");

    public static final PanelLayout CONVERTER = PanelLayout.builder("methanol_converter", 264, 180, M_CHANNELS)
            .dial(8, 24, false, M_P_BAR, 1, 120, MethanolLoop.MAX_BAR, 0, BAR.get(), LOOP_P.get(),
                    v -> LOOP_TIP.get(v.raw(M_P_BAR), v.num(M_OUT_MEOH, 100, 2), v.num(M_EQ_MEOH, 100, 2), v.num(M_INERTS, 100, 1)))
            .dial(64, 24, false, M_BED_C, 1, 350, MethanolSynthesis.SINTER_K - 273.15, 0, CELSIUS.get(), BED.get(),
                    v -> BED_TIP.get(v.raw(M_BED_C), v.num(M_STEAM_BAR, 10, 0), Math.round(MethanolSynthesis.SINTER_K - 273.15)))
            .bar(120, 26, 60, M_CU, 10, 100, 0xFFB87333, COPPER.get(), v -> CU_TIP.get(v.num(M_CU, 10, 0)))
            .bar(120, 48, 60, M_NI, 10, 100, 0xFF4F7A4A, NICKEL.get(), v -> NI_TIP.get(v.num(M_NI, 10, 0)))
            .lamp(120, 70, M_FLAGS, MF_NO_CU, LampColor.RED, NO_CU.get())
            .lamp(120, 84, M_FLAGS, MF_NO_NI, LampColor.RED, NO_NI.get())
            .lamp(192, 22, M_FLAGS, MF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(192, 36, M_FLAGS, MF_HOT, LampColor.RED, HOT.get())
            .lamp(192, 50, M_FLAGS, MF_PMAX, LampColor.AMBER, PMAX.get())
            .lamp(192, 64, M_FLAGS, MF_DRY, LampColor.RED, DRY.get())
            .lamp(192, 78, M_FLAGS, MF_NO_COLUMN, LampColor.AMBER, NO_COLUMN.get())
            .lamp(192, 92, M_FLAGS, MF_NO_FEED, LampColor.AMBER, NO_FEED.get())
            .lamp(120, 98, M_FLAGS, MF_CARBON, LampColor.AMBER, CARBON.get())
            .button(8, 118, 44, MB_RUN, RUN.get(), null)
            .caption(56, 108, PURGE_LABEL.get())
            .button(56, 118, 20, MB_PURGE_DOWN, Component.literal("−"), PURGE_DOWN.get())
            .button(78, 118, 20, MB_PURGE_UP, Component.literal("+"), PURGE_UP.get())
            .caption(104, 108, STEAM_LABEL.get())
            .button(104, 118, 20, MB_STEAM_DOWN, Component.literal("−"), STEAM_DOWN.get())
            .button(126, 118, 20, MB_STEAM_UP, Component.literal("+"), STEAM_UP.get())
            .caption(152, 108, CIRC_LABEL.get())
            .button(152, 118, 20, MB_CIRC_DOWN, Component.literal("−"), CIRC_DOWN.get())
            .button(174, 118, 20, MB_CIRC_UP, Component.literal("+"), CIRC_UP.get())
            .caption(56, 134, SC_LABEL.get())
            .button(56, 144, 20, MB_SC_DOWN, Component.literal("−"), SC_DOWN.get())
            .button(78, 144, 20, MB_SC_UP, Component.literal("+"), SC_UP.get())
            .caption(104, 134, T_LABEL.get())
            .button(104, 144, 20, MB_T_DOWN, Component.literal("−"), T_DOWN.get())
            .button(126, 144, 20, MB_T_UP, Component.literal("+"), T_UP.get())
            .caption(152, 134, RATE_LABEL.get())
            .button(152, 144, 20, MB_RATE_DOWN, Component.literal("−"), RATE_DOWN.get())
            .button(174, 144, 20, MB_RATE_UP, Component.literal("+"), RATE_UP.get())
            .readout(8, 166, 248, MethanolPanels::converter, null)
            .noInventory().build();

    // --- ректификация ---
    private static final Tr WATER = PanelText.tr("methanol_column.water", "Water", "Вода"),
            CRUDE = PanelText.tr("methanol_column.crude", "Crude", "Сырец"),
            TANK = PanelText.tr("methanol_column.tank", "AA", "AA"),
            OFF_SPEC = PanelText.tr("methanol_column.off_spec", "Off spec", "Не марка"),
            NO_HEAT = PanelText.tr("methanol_column.no_heat", "Low steam", "Мало пара"),
            NO_CRUDE = PanelText.tr("methanol_column.no_crude", "No crude", "Нет сырца"),
            REFLUX_LABEL = PanelText.tr("methanol_column.reflux_label", "Reflux", "Флегма"),
            REFLUX_DOWN = PanelText.tr("methanol_column.reflux_down", "Reflux ratio −0.1", "Флегмовое число −0,1"),
            REFLUX_UP = PanelText.tr("methanol_column.reflux_up", "Reflux ratio +0.1", "Флегмовое число +0,1"),
            WATER_TIP = PanelText.tr("methanol_column.water_tip", "Water in the product %s ppm · grade AA allows 1000 ppm",
                    "Вода в продукте %s ppm · марка AA допускает 1000 ppm"),
            CRUDE_TIP = PanelText.tr("methanol_column.crude_tip", "Crude methanol hold-up %s %%", "Запас сырца %s %%"),
            TANK_TIP = PanelText.tr("methanol_column.tank_tip", "Grade AA methanol tank %s %%", "Бак метанола марки AA %s %%"),
            COLUMN_STATUS = PanelText.tr("methanol_column.status",
                    "Crude %s kg/h → methanol %s kg/h · reflux %s (set %s) · reboilers %s of %s kW",
                    "Сырец %s кг/ч → метанол %s кг/ч · флегма %s (задано %s) · кипятильники %s из %s кВт"),
            COLUMN_IDLE = PanelText.tr("methanol_column.idle", "Waiting for crude: stand the column against a methanol unit",
                    "Ждёт сырец: поставьте колонну вплотную к метанольному агрегату");

    public static final PanelLayout COLUMN = PanelLayout.builder("methanol_column", 232, 128, C_CHANNELS)
            .dial(8, 24, false, C_WATER_PPM, 1, 5000, 1000, 0, PPM.get(), WATER.get(), v -> WATER_TIP.get(v.raw(C_WATER_PPM)))
            .bar(64, 26, 60, C_CRUDE, 10, 100, 0xFF8A7F5A, CRUDE.get(), v -> CRUDE_TIP.get(v.num(C_CRUDE, 10, 0)))
            .bar(64, 48, 60, C_TANK, 10, 100, 0xFFD8D8E0, TANK.get(), v -> TANK_TIP.get(v.num(C_TANK, 10, 0)))
            .lamp(150, 22, C_FLAGS, CF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(150, 36, C_FLAGS, CF_OFF_SPEC, LampColor.RED, OFF_SPEC.get())
            .lamp(150, 50, C_FLAGS, CF_NO_HEAT, LampColor.AMBER, NO_HEAT.get())
            .lamp(150, 64, C_FLAGS, CF_NO_CRUDE, LampColor.AMBER, NO_CRUDE.get())
            .button(8, 88, 44, CB_RUN, RUN.get(), null)
            .caption(64, 78, REFLUX_LABEL.get())
            .button(64, 88, 20, CB_REFLUX_DOWN, Component.literal("−"), REFLUX_DOWN.get())
            .button(86, 88, 20, CB_REFLUX_UP, Component.literal("+"), REFLUX_UP.get())
            .readout(8, 112, 216, MethanolPanels::column, null)
            .noInventory().build();

    private MethanolPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CONVERTER, COLUMN);
    }

    private static Component converter(PanelValues v) {
        Component c = STATUS.get(v.raw(M_MEOH_KGH), v.num(M_CRUDE, 10, 1), v.num(M_MAKEUP_SN, 100, 2), v.num(M_SN, 100, 2),
                v.raw(M_FEED), v.raw(M_REF_C), v.num(M_SC, 100, 2), v.raw(M_PURGE), v.raw(M_STEAM_KGH), v.raw(M_SHAFT_KW));
        return v.bit(M_FLAGS, MF_HOT) || v.bit(M_FLAGS, MF_DRY) || v.bit(M_FLAGS, MF_CARBON) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    private static Component column(PanelValues v) {
        if (v.bit(C_FLAGS, CF_NO_CRUDE) && v.raw(C_FEED_KGH) == 0) return COLUMN_IDLE.get().withStyle(ChatFormatting.GOLD);
        Component c = COLUMN_STATUS.get(v.raw(C_FEED_KGH), v.raw(C_PRODUCT_KGH), v.num(C_REFLUX_ACT, 10, 1), v.num(C_REFLUX, 10, 1),
                v.raw(C_HEAT_KW), v.raw(C_NEED_KW));
        return v.bit(C_FLAGS, CF_OFF_SPEC) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }
}
