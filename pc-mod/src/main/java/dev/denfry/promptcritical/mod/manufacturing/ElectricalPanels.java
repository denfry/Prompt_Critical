package dev.denfry.promptcritical.mod.manufacturing;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;

/** Щиты электротехнических станков (18 §18.6.8–18.6.9): листовой стан, пазовырубной пресс, намоточный станок. */
public final class ElectricalPanels {
    public static final int FLAGS = 0;
    public static final int BUTTON_THINNER = 0, BUTTON_THICKER = 1, BUTTON_RUN = 2, BUTTON_REGRIND = 3;

    public static final int S_RPM10 = 1, S_TARGET_UM = 2, S_STOP = 3, S_PASSES = 4, S_THICK_UM = 5, S_SHEETS10 = 6,
            S_STRAIN_PCT = 7, S_SI_PCT10 = 8, S_CHANNELS = 9;
    public static final int SF_ON = 0, SF_SHAFT = 1, SF_STRIP = 2;

    public static final int N_RPM10 = 1, N_STOP = 2, N_BUNDLES = 3, N_FORCE_KN = 4, N_WEAR_PCT = 5, N_REGRIND_S = 6,
            N_BURR_UM = 7, N_THICK_UM = 8, N_PROGRESS_PCT = 9, N_CHANNELS = 10;
    public static final int NF_ON = 0, NF_SHAFT = 1, NF_SHEET = 2;

    public static final int M_RPM10 = 1, M_STOP = 2, M_LAMINATIONS = 3, M_WIRE_NEED_M = 4, M_WIRE_HAVE_M = 5,
            M_FILL_PCT = 6, M_PROGRESS_PCT = 7, M_CHANNELS = 8;
    public static final int MF_ON = 0, MF_SHAFT = 1, MF_CORE = 2, MF_STACKED = 3;

    private static final Tr
            ON = PanelText.tr("electrical.on", "Running", "В работе"),
            SHAFT = PanelText.tr("electrical.shaft", "Shaft", "Вал"),
            RUN = PanelText.tr("electrical.run", "Start / stop", "Пуск / стоп"),
            THINNER = PanelText.tr("electrical.thinner", "Thinner", "Тоньше"),
            THICKER = PanelText.tr("electrical.thicker", "Thicker", "Толще"),
            REGRIND = PanelText.tr("electrical.regrind", "Regrind die", "Переточить штамп"),
            REGRIND_TIP = PanelText.tr("electrical.regrind_tip", "The press stops for two minutes; the burr returns to that of a fresh die",
                    "Пресс встаёт на две минуты; заусенец снова как у свежего штампа"),
            PCT = PanelText.tr("electrical.pct", "%s %%", "%s %%"),
            STRIP = PanelText.tr("electrical.strip", "Strip %s mm, Si %s %%, %s sheets", "Полоса %s мм, Si %s %%, листов %s"),
            NO_STRIP = PanelText.tr("electrical.no_strip", "Put in a steel plate up to 4 mm or electrical sheets",
                    "Положите стальной лист до 4 мм или электротехнические листы"),
            TARGET = PanelText.tr("electrical.target", "Target %s mm · %s rpm", "Цель %s мм · %s об/мин"),
            WORK_HARDENING = PanelText.tr("electrical.work_hardening", "Work hardening", "Наклёп"),
            PASSES = PanelText.tr("electrical.passes", "Passes: %s · %s", "Проходов: %s · %s"),
            SHEET_SLOT = PanelText.tr("electrical.sheet_slot", "Steel plate up to 4 mm or electrical sheets",
                    "Стальной лист до 4 мм или электротехнические листы"),
            OUT_SLOT = PanelText.tr("electrical.out_slot", "Output", "Выход"),
            PRESS_SHEET = PanelText.tr("electrical.press_sheet", "Sheet %s mm · burr now %s µm", "Лист %s мм · заусенец сейчас %s мкм"),
            NO_SHEET = PanelText.tr("electrical.no_sheet", "Put in electrical sheets", "Положите электротехнические листы"),
            PRESS_LINE = PanelText.tr("electrical.press_line", "%s rpm · peak stroke %s kN · die wear %s %%",
                    "%s об/мин · ход до %s кН · износ штампа %s %%"),
            PRESS_STATUS = PanelText.tr("electrical.press_status", "Bundles: %s · %s", "Пачек: %s · %s"),
            REGRINDING = PanelText.tr("electrical.regrinding", "Regrinding the die: %s s", "Переточка штампа: %s с"),
            SHEET_PROGRESS = PanelText.tr("electrical.sheet_progress", "Sheet", "Лист"),
            LAM_SLOT = PanelText.tr("electrical.lam_slot", "Lamination bundles or a stator core", "Пачки пластин или пакет статора"),
            WIRE_SLOT = PanelText.tr("electrical.wire_slot", "Insulated copper wire, one kind", "Медный провод в изоляции, один вид"),
            CORE = PanelText.tr("electrical.core", "Core: %s laminations · needs %s m of wire", "Пакет: %s пластин · нужно %s м провода"),
            NO_CORE = PanelText.tr("electrical.no_core", "Not enough laminations for a 125 mm core",
                    "Пластин мало на пакет 125 мм"),
            WIRE = PanelText.tr("electrical.wire", "Wire: %s m · slot fill %s %% · %s rpm", "Провод: %s м · заполнение паза %s %% · %s об/мин"),
            STAGE_STACK = PanelText.tr("electrical.stage_stack", "Stacking", "Шихтовка"),
            STAGE_WIND = PanelText.tr("electrical.stage_wind", "Winding", "Обмотка"),
            WINDER_STATUS = PanelText.tr("electrical.winder_status", "Status: %s", "Состояние: %s");

    private static final Tr[] SHEET_STOPS = {PanelText.tr("electrical.mill.none", "working", "в работе"),
        PanelText.tr("electrical.mill.done", "target thickness reached", "толщина достигнута"),
        PanelText.tr("electrical.mill.anneal", "work hardened: anneal at 800 °C", "наклёп: отжечь при 800 °C"),
        PanelText.tr("electrical.mill.brittle", "too much silicon to roll cold", "слишком много кремния для холодной прокатки")};
    private static final Tr[] PRESS_STOPS = {PanelText.tr("electrical.press.none", "working", "в работе"),
        PanelText.tr("electrical.press.done", "out of sheets", "листы кончились"),
        PanelText.tr("electrical.press.output", "output full or a different bundle in it", "выход полон или в нём другие пачки"),
        PanelText.tr("electrical.press.weak", "the press cannot cut this sheet", "пресс не берёт этот лист")};
    private static final Tr[] WINDER_STOPS = {PanelText.tr("electrical.winder.none", "working", "в работе"),
        PanelText.tr("electrical.winder.done", "stator wound", "статор намотан"),
        PanelText.tr("electrical.winder.laminations", "not enough laminations", "мало пластин"),
        PanelText.tr("electrical.winder.wire", "not enough insulated wire: bare core out", "мало провода в изоляции: голый пакет на выход"),
        PanelText.tr("electrical.winder.output", "take the item from the output", "заберите предмет с выхода")};

    public static final PanelLayout SHEET_MILL = PanelLayout.builder("sheet_mill", 200, 150, S_CHANNELS)
            .art(dev.denfry.promptcritical.mod.PromptCritical.id("textures/gui/panel/sheet_mill.png"))
            .readout(8, 8, 184, v -> v.bit(FLAGS, SF_STRIP) ? STRIP.get(v.num(S_THICK_UM, 1000, 3), v.num(S_SI_PCT10, 10, 1),
                    v.num(S_SHEETS10, 10, 1)) : NO_STRIP.get(), null)
            .readout(8, 22, 184, v -> TARGET.get(v.num(S_TARGET_UM, 1000, 2), v.num(S_RPM10, 10, 0)), null)
            .bar(84, 38, 108, S_STRAIN_PCT, 1, 100, 0xFFB0B0C0, WORK_HARDENING.get(), v -> PCT.get(v.raw(S_STRAIN_PCT)))
            .readout(8, 60, 184, v -> PASSES.get(v.raw(S_PASSES), stop(SHEET_STOPS, v.raw(S_STOP))), null)
            .lamp(8, 76, FLAGS, SF_ON, LampColor.GREEN, ON.get())
            .lamp(48, 76, FLAGS, SF_SHAFT, LampColor.WHITE, SHAFT.get())
            .button(8, 102, 56, BUTTON_THINNER, THINNER.get(), null)
            .button(68, 102, 56, BUTTON_THICKER, THICKER.get(), null)
            .button(128, 102, 56, BUTTON_RUN, RUN.get(), null)
            .slot(150, 126, 0, SheetMillBlockEntity::accepted, SHEET_SLOT.get())
            .slot(170, 126, 1, s -> false, OUT_SLOT.get())
            .build();

    public static final PanelLayout NOTCHING_PRESS = PanelLayout.builder("notching_press", 200, 150, N_CHANNELS)
            .art(dev.denfry.promptcritical.mod.PromptCritical.id("textures/gui/panel/notching_press.png"))
            .readout(8, 8, 184, v -> v.bit(FLAGS, NF_SHEET) ? PRESS_SHEET.get(v.num(N_THICK_UM, 1000, 2), v.raw(N_BURR_UM))
                    : NO_SHEET.get(), null)
            .readout(8, 22, 184, v -> PRESS_LINE.get(v.num(N_RPM10, 10, 0), v.raw(N_FORCE_KN), v.raw(N_WEAR_PCT)), null)
            .bar(84, 38, 108, N_PROGRESS_PCT, 1, 100, 0xFF80A0C0, SHEET_PROGRESS.get(), v -> PCT.get(v.raw(N_PROGRESS_PCT)))
            .readout(8, 60, 184, v -> v.raw(N_REGRIND_S) > 0 ? REGRINDING.get(v.raw(N_REGRIND_S))
                    : PRESS_STATUS.get(v.raw(N_BUNDLES), stop(PRESS_STOPS, v.raw(N_STOP))), null)
            .lamp(8, 76, FLAGS, NF_ON, LampColor.GREEN, ON.get())
            .lamp(48, 76, FLAGS, NF_SHAFT, LampColor.WHITE, SHAFT.get())
            .button(8, 102, 76, BUTTON_REGRIND, REGRIND.get(), REGRIND_TIP.get())
            .button(88, 102, 56, BUTTON_RUN, RUN.get(), null)
            .slot(90, 126, 0, s -> s.has(ManufacturingComponents.ELECTRICAL_SHEET.get()), NO_SHEET.get())
            .slot(110, 126, 1, s -> false, OUT_SLOT.get())
            .slot(130, 126, 2, s -> false, OUT_SLOT.get())
            .slot(150, 126, 3, s -> false, OUT_SLOT.get())
            .slot(170, 126, 4, s -> false, OUT_SLOT.get())
            .build();

    public static final PanelLayout WINDING_MACHINE = PanelLayout.builder("winding_machine", 200, 150, M_CHANNELS)
            .art(dev.denfry.promptcritical.mod.PromptCritical.id("textures/gui/panel/winding_machine.png"))
            .readout(8, 8, 184, v -> v.bit(FLAGS, MF_CORE) ? CORE.get(v.raw(M_LAMINATIONS), v.raw(M_WIRE_NEED_M)) : NO_CORE.get(), null)
            .readout(8, 22, 184, v -> WIRE.get(v.raw(M_WIRE_HAVE_M), v.raw(M_FILL_PCT), v.num(M_RPM10, 10, 0)), null)
            .bar(84, 38, 108, M_PROGRESS_PCT, 1, 100, 0xFFC08040, PanelText.tr("electrical.progress", "Progress", "Ход работы").get(),
                    v -> (v.bit(FLAGS, MF_STACKED) ? STAGE_WIND : STAGE_STACK).get().copy().append(" " + v.raw(M_PROGRESS_PCT) + " %"))
            .readout(8, 60, 184, v -> WINDER_STATUS.get(stop(WINDER_STOPS, v.raw(M_STOP))), null)
            .lamp(8, 76, FLAGS, MF_ON, LampColor.GREEN, ON.get())
            .lamp(48, 76, FLAGS, MF_SHAFT, LampColor.WHITE, SHAFT.get())
            .button(128, 102, 56, BUTTON_RUN, RUN.get(), null)
            .slot(70, 126, 0, WindingMachineBlockEntity::isCoreInput, LAM_SLOT.get())
            .slot(90, 126, 1, WindingMachineBlockEntity::isCoreInput, LAM_SLOT.get())
            .slot(110, 126, 2, WindingMachineBlockEntity::isCoreInput, LAM_SLOT.get())
            .slot(130, 126, 3, WindingMachineBlockEntity::isCoreInput, LAM_SLOT.get())
            .slot(150, 126, WindingMachineBlockEntity.WIRE, WindingMachineBlockEntity::isMagnetWire, WIRE_SLOT.get())
            .slot(170, 126, WindingMachineBlockEntity.OUT, s -> false, OUT_SLOT.get())
            .build();

    private ElectricalPanels() {
    }

    private static net.minecraft.network.chat.Component stop(Tr[] stops, int i) {
        return stops[Math.max(0, Math.min(stops.length - 1, i))].get();
    }

    public static List<PanelLayout> all() {
        return List.of(SHEET_MILL, NOTCHING_PRESS, WINDING_MACHINE);
    }
}
