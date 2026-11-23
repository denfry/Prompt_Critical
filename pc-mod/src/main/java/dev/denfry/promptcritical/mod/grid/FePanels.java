package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Щит {@code grid_fe_interface} (07 §7.7.1): ваттметр со знаком (плюс — из сети в FE), заполнение буфера, лампы
 * «на сети» и «паритет R56», ключи режимов и задания, строка частоты, напряжения и курса FE.
 */
public final class FePanels {
    public static final int FLAGS = 0, MODE = 1, SET_PCT = 2, P_KW10 = 3, FE_T = 4, FILL_PCT = 5, HZ100 = 6, V_PCT = 7,
            J_PER_FE = 8, RATED_KW = 9, CHANNELS = 10;
    public static final int F_ON = 0, F_PARITY = 1;
    /** Ключи режимов — по порядку {@link dev.denfry.promptcritical.sim.grid.FeInterface.Mode}; потом задание. */
    public static final int BUTTON_IMPORT = 0, BUTTON_EXPORT = 1, BUTTON_AUTO = 2, BUTTON_DOWN = 3, BUTTON_UP = 4;

    private static final Tr POWER = PanelText.tr("fe.power", "Grid power", "Мощность сети"),
            KW = PanelText.tr("unit.kw", "kW", "кВт"),
            BUFFER = PanelText.tr("fe.buffer", "Buffer", "Буфер"),
            ON = PanelText.tr("fe.on", "On grid", "На сети"),
            PARITY = PanelText.tr("fe.parity", "Parity", "Паритет"),
            PARITY_TIP = PanelText.tr("fe.parity_tip", "A Create to FE bridge mod is loaded: FE is priced at 2 x W/SU (R56)",
                    "Загружен мод-мост Create и FE: FE считается по 2 x Вт/SU (R56)"),
            IMPORT = PanelText.tr("fe.import", "Import", "Импорт"),
            EXPORT = PanelText.tr("fe.export", "Export", "Экспорт"),
            AUTO = PanelText.tr("fe.auto", "Auto", "Автомат"),
            IMPORT_TIP = PanelText.tr("fe.import_tip", "Grid to FE: a constant power load that fills the buffer",
                    "Сеть в FE: нагрузка постоянной мощности, наполняет буфер"),
            EXPORT_TIP = PanelText.tr("fe.export_tip", "FE to grid: follows the grid, cannot start a dead one, trips in 0.2 s out of the window",
                    "FE в сеть: ведомый сетью, мёртвую сеть не поднимет, вне окна отключается за 0,2 с"),
            AUTO_TIP = PanelText.tr("fe.auto_tip", "Hold the buffer half full", "Держать буфер наполовину полным"),
            LESS = PanelText.tr("fe.less", "-10 %%", "-10 %%"),
            MORE = PanelText.tr("fe.more", "+10 %%", "+10 %%"),
            SET_TIP = PanelText.tr("fe.set_tip", "Power limit %s %% of %s kW", "Предел мощности %s %% от %s кВт"),
            STATUS = PanelText.tr("fe.status", "%s · %s FE/t · %s Hz · %s %% U · %s J/FE",
                    "%s · %s FE/т · %s Гц · %s %% U · %s Дж/FE");
    private static final Tr[] MODES = {IMPORT, EXPORT, AUTO};

    public static final PanelLayout FE_INTERFACE = PanelLayout.builder("grid_fe_interface", 232, 128, CHANNELS)
            .dial(8, 24, false, P_KW10, 10, 11_000, Double.NaN, 0, KW.get(), POWER.get(), null)
            .glass(64, 24, 56, FILL_PCT, 0xFFB04030, Double.NaN, Double.NaN, BUFFER.get(), v -> Component.literal(v.raw(FILL_PCT) + " %"))
            .lamp(92, 26, FLAGS, F_ON, LampColor.GREEN, ON.get())
            .lamp(92, 42, FLAGS, F_PARITY, LampColor.AMBER, PARITY.get())
            .button(8, 84, 44, BUTTON_IMPORT, IMPORT.get(), IMPORT_TIP.get())
            .button(56, 84, 44, BUTTON_EXPORT, EXPORT.get(), EXPORT_TIP.get())
            .button(104, 84, 44, BUTTON_AUTO, AUTO.get(), AUTO_TIP.get())
            .button(188, 60, 36, BUTTON_DOWN, LESS.get(), null)
            .button(188, 84, 36, BUTTON_UP, MORE.get(), null)
            .readout(8, 108, 216, FePanels::status, v -> SET_TIP.get(v.raw(SET_PCT), v.raw(RATED_KW)))
            .noInventory().build();

    private FePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(FE_INTERFACE);
    }

    private static Component status(PanelValues v) {
        int m = v.raw(MODE);
        return STATUS.get(MODES[m >= 0 && m < MODES.length ? m : 2].get(), v.raw(FE_T), v.num(HZ100, 100, 2), v.raw(V_PCT),
                v.num(J_PER_FE, 10, 1));
    }
}
