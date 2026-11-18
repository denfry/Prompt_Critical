package dev.denfry.promptcritical.mod.nuclear;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.registry.PcItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Щиты A0 топливного цеха. На каждом — учёт U-235 участка в граммах (пришло, ушло, потери, в аппаратах) и пуск.
 * Каналы учёта — первые четыре: {@link #L_IN}…{@link #L_HOLD}; дальше — свои у каждого участка.
 */
public final class FuelPanels {
    public static final int L_IN = 0, L_OUT = 1, L_LOSS = 2, L_HOLD = 3, FLAGS = 4;
    public static final int F_RUN = 0, F_LIMIT = 1, F_REFUSED = 2, F_STARVED = 3, F_REJECT = 4, F_ARGON = 5;
    public static final int B_RUN = 0;
    // конверсия
    public static final int C_X = 5, C_FEED_KGH = 6, C_POWDER_KGH = 7, C_SAFE_G = 8, C_NH3 = 9, C_H2 = 10, C_WATER = 11, C_CHANNELS = 12;
    public static final int C_SLOT_UF6 = 0, C_SLOT_POWDER = 1;
    // пресс
    public static final int P_MPA = 5, P_GREEN = 6, P_DEFECT = 7, P_DESIGN = 8, P_BUFFER = 9, P_CHANNELS = 10;
    public static final int PF_NO_FURNACE = 6;
    public static final int PB_P_DOWN = 1, PB_P_UP = 2, PB_DESIGN = 3;
    // печь
    public static final int S_T_C = 5, S_HOURS = 6, S_DENSITY = 7, S_GRAIN = 8, S_OU = 9, S_H2 = 10, S_TRAYS = 11, S_BUFFER = 12, S_CHANNELS = 13;
    public static final int SB_T_DOWN = 1, SB_T_UP = 2, SB_H_DOWN = 3, SB_H_UP = 4;
    public static final int S_SLOT_TRAYS = 0, S_SLOT_SLUDGE = 1;
    // снаряжение
    public static final int R_RODS = 5, R_DEFECTS = 6, R_HE = 7, R_AR = 8, R_CHANNELS = 9;
    public static final int R_SLOT_TRAYS = 0, R_SLOT_TUBES = 1, R_SLOT_RODS = 2;
    // сборка
    public static final int J_LOADED = 5, J_NEEDED = 6, J_GRIDS = 7, J_TUBES = 8, J_U_KG = 9, J_X = 10, J_DESIGN = 11, J_CHANNELS = 12;
    public static final int J_SLOT_RODS = 0, J_SLOT_GRIDS = 1, J_SLOT_TUBES = 2, J_SLOT_OUT = 3;
    public static final int JB_DESIGN = 1;

    private static final Tr RUN = PanelText.tr("fuel.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("fuel.working", "Working", "Работа"),
            LIMIT = PanelText.tr("fuel.limit", "Safe mass", "Безоп. масса"),
            REFUSED = PanelText.tr("fuel.refused", "HEU", "ВОУ"),
            STARVED = PanelText.tr("fuel.starved", "Reagent", "Реагент"),
            REJECT = PanelText.tr("fuel.reject", "Reject", "Брак"),
            ARGON = PanelText.tr("fuel.argon", "Argon", "Аргон"),
            NO_FURNACE = PanelText.tr("fuel.no_furnace", "No furnace", "Нет печи"),
            MPA = PanelText.MPA, CELSIUS = PanelText.CELSIUS,
            LEDGER = PanelText.tr("fuel.ledger", "U-235: in %s g · out %s g · losses %s g · in vessels %s g",
                    "U-235: пришло %s г · ушло %s г · потери %s г · в аппаратах %s г");
    private static final Tr AMMONIA = PanelText.tr("fuel.nh3", "NH₃", "NH₃"), HYDROGEN = PanelText.tr("fuel.h2", "H₂", "H₂"),
            WATER = PanelText.tr("fuel.water", "Water", "Вода"),
            UF6_SLOT = PanelText.tr("fuel.uf6_slot", "UF₆ cylinder", "Баллон UF₆"),
            POWDER_SLOT = PanelText.tr("fuel.powder_slot", "UO₂ powder", "Порошок UO₂"),
            CONV_STATUS = PanelText.tr("fuel.conv_status", "%s %% U-235 · UF₆ %s kg U/h → UO₂ %s kg U/h · safe mass %s g U-235",
                    "%s %% U-235 · UF₆ %s кг U/ч → UO₂ %s кг U/ч · безопасная масса %s г U-235");
    public static final PanelLayout CONVERSION = PanelLayout.builder("uo2_conversion", 232, 124, C_CHANNELS)
            .glass(8, 24, 56, C_NH3, 0xFFE2E8F0, Double.NaN, Double.NaN, AMMONIA.get(), null)
            .glass(36, 24, 56, C_H2, 0xFFF0F2F8, Double.NaN, Double.NaN, HYDROGEN.get(), null)
            .glass(64, 24, 56, C_WATER, 0xFF4060C0, Double.NaN, Double.NaN, WATER.get(), null)
            .lamp(96, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(96, 36, FLAGS, F_LIMIT, LampColor.AMBER, LIMIT.get())
            .lamp(96, 50, FLAGS, F_REFUSED, LampColor.RED, REFUSED.get())
            .lamp(96, 64, FLAGS, F_STARVED, LampColor.AMBER, STARVED.get())
            .slot(180, 26, C_SLOT_UF6, s -> s.getItem() instanceof Uf6CylinderItem, UF6_SLOT.get())
            .slot(180, 56, C_SLOT_POWDER, s -> false, POWDER_SLOT.get())
            .button(8, 86, 44, B_RUN, RUN.get(), null)
            .readout(8, 100, 216, v -> CONV_STATUS.get(pct(v, C_X), v.num(C_FEED_KGH, 10, 1), v.num(C_POWDER_KGH, 10, 1), v.raw(C_SAFE_G)), null)
            .readout(8, 112, 216, FuelPanels::ledger, null)
            .build();

    private static final Tr PRESSURE = PanelText.tr("fuel.pressure", "Pressing", "Прессование"),
            P_DOWN = PanelText.tr("fuel.p_down", "Pressing −25 MPa", "Давление прессования −25 МПа"),
            P_UP = PanelText.tr("fuel.p_up", "Pressing +25 MPa", "Давление прессования +25 МПа"),
            DESIGN_BTN = PanelText.tr("fuel.design_btn", "Design", "Тип"),
            DESIGN_TIP = PanelText.tr("fuel.design_tip", "Pellet design: VVER-440, VVER-1000, PWR 17×17 or AM-1 (with an empty buffer)",
                    "Таблетка: ВВЭР-440, ВВЭР-1000, PWR 17×17 или АМ-1 (при пустом бункере)"),
            PRESS_STATUS = PanelText.tr("fuel.press_status", "%s · green %s %% TD · pressing rejects %s %% · powder in hopper %s kg U",
                    "%s · сырая %s %% ТП · брак прессования %s %% · в бункере %s кг U");
    private static final String[] DESIGNS = {"VVER-440", "VVER-1000", "PWR 17×17", "AM-1"};
    public static final PanelLayout PRESS = PanelLayout.builder("pellet_press", 232, 124, P_CHANNELS)
            .dial(8, 24, false, P_MPA, 1, 600, 450, 0, MPA.get(), PRESSURE.get(), null)
            .lamp(64, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(64, 36, FLAGS, F_LIMIT, LampColor.AMBER, LIMIT.get())
            .lamp(64, 50, FLAGS, PF_NO_FURNACE, LampColor.RED, NO_FURNACE.get())
            .slot(180, 26, 0, FuelPanels::powder, POWDER_SLOT.get())
            .button(8, 86, 44, B_RUN, RUN.get(), null)
            .button(56, 86, 20, PB_P_DOWN, Component.literal("−"), P_DOWN.get())
            .button(78, 86, 20, PB_P_UP, Component.literal("+"), P_UP.get())
            .button(104, 86, 44, PB_DESIGN, DESIGN_BTN.get(), DESIGN_TIP.get())
            .readout(8, 100, 216, v -> PRESS_STATUS.get(DESIGNS[Math.floorMod(v.raw(P_DESIGN), DESIGNS.length)], v.num(P_GREEN, 10, 1),
                    v.num(P_DEFECT, 10, 1), v.num(P_BUFFER, 1000, 2)), null)
            .readout(8, 112, 216, FuelPanels::ledger, null)
            .build();

    private static final Tr FURNACE_T = PanelText.tr("fuel.furnace_t", "Furnace", "Печь"),
            T_DOWN = PanelText.tr("fuel.t_down", "Sintering −25 °C", "Спекание −25 °C"),
            T_UP = PanelText.tr("fuel.t_up", "Sintering +25 °C", "Спекание +25 °C"),
            H_DOWN = PanelText.tr("fuel.h_down", "Hold −0.5 h", "Выдержка −0,5 ч"),
            H_UP = PanelText.tr("fuel.h_up", "Hold +0.5 h", "Выдержка +0,5 ч"),
            T_LABEL = PanelText.tr("fuel.t_label", "T", "T"),
            H_LABEL = PanelText.tr("fuel.h_label", "Hold", "Время"),
            TRAY_SLOT = PanelText.tr("fuel.tray_slot", "Pellet trays (one rod each)", "Поддоны таблеток (на один твэл)"),
            SLUDGE_SLOT = PanelText.tr("fuel.sludge_slot", "Grinding sludge and rejects: back to the press", "Шлам и брак: обратно в пресс"),
            FURNACE_STATUS = PanelText.tr("fuel.furnace_status", "Density %s %% TD · grain %s µm · O/U %s · hold %s h · trays %s · green in furnace %s kg U",
                    "Плотность %s %% ТП · зерно %s мкм · O/U %s · выдержка %s ч · поддонов %s · в печи %s кг U");
    public static final PanelLayout FURNACE = PanelLayout.builder("sintering_furnace", 232, 124, S_CHANNELS)
            .dial(8, 24, false, S_T_C, 1, 2000, 1800, 0, CELSIUS.get(), FURNACE_T.get(), null)
            .glass(64, 24, 56, S_H2, 0xFFF0F2F8, Double.NaN, Double.NaN, HYDROGEN.get(), null)
            .lamp(96, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(96, 36, FLAGS, F_REJECT, LampColor.AMBER, REJECT.get())
            .lamp(96, 50, FLAGS, F_STARVED, LampColor.AMBER, STARVED.get())
            .lamp(96, 64, FLAGS, F_LIMIT, LampColor.AMBER, LIMIT.get())
            .slot(180, 26, S_SLOT_TRAYS, s -> false, TRAY_SLOT.get())
            .slot(180, 56, S_SLOT_SLUDGE, s -> false, SLUDGE_SLOT.get())
            .button(8, 86, 44, B_RUN, RUN.get(), null)
            .caption(56, 76, T_LABEL.get())
            .button(56, 86, 20, SB_T_DOWN, Component.literal("−"), T_DOWN.get())
            .button(78, 86, 20, SB_T_UP, Component.literal("+"), T_UP.get())
            .caption(104, 76, H_LABEL.get())
            .button(104, 86, 20, SB_H_DOWN, Component.literal("−"), H_DOWN.get())
            .button(126, 86, 20, SB_H_UP, Component.literal("+"), H_UP.get())
            .readout(8, 100, 216, v -> FURNACE_STATUS.get(v.num(S_DENSITY, 10, 1), v.num(S_GRAIN, 10, 1), v.num(S_OU, 1000, 3),
                    v.num(S_HOURS, 10, 1), v.raw(S_TRAYS), v.num(S_BUFFER, 1000, 2)), null)
            .readout(8, 112, 216, FuelPanels::ledger, null)
            .build();

    private static final Tr HELIUM = PanelText.tr("fuel.he", "He", "He"), ARGON_GAS = PanelText.tr("fuel.ar", "Ar", "Ar"),
            TUBE_SLOT = PanelText.tr("fuel.tube_slot", "Cladding tubes", "Оболочечные трубы"),
            ROD_SLOT = PanelText.tr("fuel.rod_slot", "Fuel rods", "Твэлы"),
            ROD_STATUS = PanelText.tr("fuel.rod_status", "Rods %s · leak test rejects %s", "Твэлов %s · отбраковано по герметичности %s");
    public static final PanelLayout ROD_STATION = PanelLayout.builder("rod_loading_station", 232, 124, R_CHANNELS)
            .glass(8, 24, 56, R_HE, 0xFFF2EEF6, Double.NaN, Double.NaN, HELIUM.get(), null)
            .glass(36, 24, 56, R_AR, 0xFFE0E4EC, Double.NaN, Double.NaN, ARGON_GAS.get(), null)
            .lamp(68, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(68, 36, FLAGS, F_REJECT, LampColor.AMBER, REJECT.get())
            .lamp(68, 50, FLAGS, F_STARVED, LampColor.AMBER, STARVED.get())
            .lamp(68, 64, FLAGS, F_ARGON, LampColor.AMBER, ARGON.get())
            .slot(150, 26, R_SLOT_TRAYS, s -> s.is(PcItems.FUEL_PELLET_TRAY.get()), TRAY_SLOT.get())
            .slot(170, 26, R_SLOT_TUBES, s -> s.is(PcItems.CLADDING_TUBE.get()), TUBE_SLOT.get())
            .slot(160, 56, R_SLOT_RODS, s -> false, ROD_SLOT.get())
            .button(8, 86, 44, B_RUN, RUN.get(), null)
            .readout(8, 100, 216, v -> ROD_STATUS.get(v.raw(R_RODS), v.raw(R_DEFECTS)), null)
            .readout(8, 112, 216, FuelPanels::ledger, null)
            .build();

    private static final Tr GRID_SLOT = PanelText.tr("fuel.grid_slot", "Spacer grids", "Дистанционирующие решётки"),
            OUT_SLOT = PanelText.tr("fuel.out_slot", "Fuel assembly", "ТВС"),
            JIG_DESIGN_TIP = PanelText.tr("fuel.jig_design_tip", "Assembly design (with the jig empty)", "Тип ТВС (на пустом стапеле)"),
            JIG_STATUS = PanelText.tr("fuel.jig_status", "%s · rods %s of %s · grids %s · tubes %s · %s kg U at %s %%",
                    "%s · твэлов %s из %s · решёток %s · труб %s · %s кг U, %s %%");
    public static final PanelLayout JIG = PanelLayout.builder("assembly_jig", 232, 124, J_CHANNELS)
            .bar(8, 30, 80, J_LOADED, 1, 312, 0xFF6A7A8A, ROD_SLOT.get(), null)
            .lamp(96, 22, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(96, 36, FLAGS, F_ARGON, LampColor.AMBER, ARGON.get())
            .slot(140, 26, J_SLOT_RODS, s -> s.is(PcItems.FUEL_ROD.get()), ROD_SLOT.get())
            .slot(160, 26, J_SLOT_GRIDS, s -> s.is(PcItems.SPACER_GRID.get()), GRID_SLOT.get())
            .slot(180, 26, J_SLOT_TUBES, s -> s.is(PcItems.CLADDING_TUBE.get()), TUBE_SLOT.get())
            .slot(160, 56, J_SLOT_OUT, s -> false, OUT_SLOT.get())
            .button(8, 86, 44, B_RUN, RUN.get(), null)
            .button(56, 86, 44, JB_DESIGN, DESIGN_BTN.get(), JIG_DESIGN_TIP.get())
            .readout(8, 100, 216, v -> JIG_STATUS.get(DESIGNS[Math.floorMod(v.raw(J_DESIGN), DESIGNS.length)], v.raw(J_LOADED), v.raw(J_NEEDED),
                    v.raw(J_GRIDS), v.raw(J_TUBES), v.num(J_U_KG, 10, 1), pct(v, J_X)), null)
            .readout(8, 112, 216, FuelPanels::ledger, null)
            .build();

    private FuelPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(CONVERSION, PRESS, FURNACE, ROD_STATION, JIG);
    }

    private static String pct(PanelValues v, int ch) {
        return v.num(ch, 1000, 3);
    }

    private static Component ledger(PanelValues v) {
        Component c = LEDGER.get(v.raw(L_IN), v.raw(L_OUT), v.raw(L_LOSS), v.raw(L_HOLD));
        return v.bit(FLAGS, F_LIMIT) || v.bit(FLAGS, F_REFUSED) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }

    static boolean powder(ItemStack s) {
        return s.is(PcItems.UO2_POWDER.get()) || s.is(PcItems.UO2_GRINDING_SLUDGE.get());
    }
}
