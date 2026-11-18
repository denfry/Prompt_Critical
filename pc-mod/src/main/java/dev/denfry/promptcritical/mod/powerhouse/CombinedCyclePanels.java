package dev.denfry.promptcritical.mod.powerhouse;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Щит A0 котла-утилизатора ПГУ: газ на входе и в трубу, пинч, пар, доля тепла топлива ГТУ, ушедшая в пар. */
public final class CombinedCyclePanels {
    public static final int H_GAS_IN_C = 0, H_STACK_C = 1, H_DUTY_KW = 2, H_STEAM_KGH = 3, H_SAT_C = 4, H_PINCH_C = 5, H_GAS_KGS10 = 6,
            H_GT_KW = 7, H_DRUM_BAR10 = 8, H_RECOVERY = 9, H_FLAGS = 10, H_CHANNELS = 11;
    public static final int HF_RUN = 0, HF_NO_GT = 1, HF_NO_BOILER = 2, HF_NO_SINK = 3, HF_COLD = 4;

    private static final Tr CELSIUS = PanelText.CELSIUS, KW = PanelText.tr("unit.kw", "kW", "кВт");
    private static final Tr GAS_IN = PanelText.tr("hrsg.gas_in", "Exhaust", "Выхлоп"),
            STACK = PanelText.tr("hrsg.stack", "Stack", "Уходящие"),
            DUTY = PanelText.tr("hrsg.duty", "Steam heat", "Тепло в пар"),
            WORKING = PanelText.tr("hrsg.working", "Steaming", "Пар идёт"),
            NO_GT = PanelText.tr("hrsg.no_gt", "No GT", "Нет ГТУ"),
            NO_BOILER = PanelText.tr("hrsg.no_boiler", "No drum", "Нет котла"),
            NO_SINK = PanelText.tr("hrsg.no_sink", "Relief", "Сброс"),
            COLD = PanelText.tr("hrsg.cold", "Gas cold", "Газ холодный"),
            GAS_TIP = PanelText.tr("hrsg.gas_tip", "Gas turbine exhaust %s °C, %s kg/s · turbine shaft %s kW",
                    "Выхлоп ГТУ %s °C, %s кг/с · вал ГТУ %s кВт"),
            STACK_TIP = PanelText.tr("hrsg.stack_tip", "Gas after the evaporator %s °C (pinch over %s °C boiling) · stack %s °C",
                    "Газ после испарителя %s °C (пинч над кипением %s °C) · в трубу %s °C"),
            STATUS = PanelText.tr("hrsg.status", "Drum %s bar · steam %s kg/h · %s kW · %s %% of the turbine's fuel heat goes to steam",
                    "Барабан %s бар · пар %s кг/ч · %s кВт · в пар уходит %s %% тепла топлива ГТУ"),
            IDLE = PanelText.tr("hrsg.idle", "Put the duct against the turbine end of a gas turbine and the drum against a boiler",
                    "Поставьте газоход вплотную к турбинной секции ГТУ, а котёл-утилизатор — вплотную к котлу");

    public static final PanelLayout HRSG = PanelLayout.builder("hrsg", 232, 120, H_CHANNELS)
            .dial(8, 24, false, H_GAS_IN_C, 1, 700, Double.NaN, 0, CELSIUS.get(), GAS_IN.get(),
                    v -> GAS_TIP.get(v.raw(H_GAS_IN_C), v.num(H_GAS_KGS10, 10, 1), v.raw(H_GT_KW)))
            .dial(64, 24, false, H_STACK_C, 1, 400, Double.NaN, 0, CELSIUS.get(), STACK.get(),
                    v -> STACK_TIP.get(v.raw(H_PINCH_C), v.raw(H_SAT_C), v.raw(H_STACK_C)))
            .dial(120, 24, false, H_DUTY_KW, 1, 10_000, Double.NaN, 0, KW.get(), DUTY.get(), null)
            .lamp(176, 22, H_FLAGS, HF_RUN, LampColor.GREEN, WORKING.get())
            .lamp(176, 36, H_FLAGS, HF_COLD, LampColor.AMBER, COLD.get())
            .lamp(176, 50, H_FLAGS, HF_NO_GT, LampColor.AMBER, NO_GT.get())
            .lamp(176, 64, H_FLAGS, HF_NO_BOILER, LampColor.AMBER, NO_BOILER.get())
            .lamp(176, 78, H_FLAGS, HF_NO_SINK, LampColor.RED, NO_SINK.get())
            .readout(8, 100, 216, CombinedCyclePanels::status, null)
            .noInventory().build();

    private CombinedCyclePanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(HRSG);
    }

    private static Component status(PanelValues v) {
        if (v.bit(H_FLAGS, HF_NO_GT) || v.bit(H_FLAGS, HF_NO_BOILER)) return IDLE.get().withStyle(ChatFormatting.GOLD);
        Component c = STATUS.get(v.num(H_DRUM_BAR10, 10, 1), v.raw(H_STEAM_KGH), v.raw(H_DUTY_KW), v.num(H_RECOVERY, 10, 1));
        return v.bit(H_FLAGS, HF_NO_SINK) ? c.copy().withStyle(ChatFormatting.GOLD) : c;
    }
}
