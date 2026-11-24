package dev.denfry.promptcritical.mod.npp;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * Блочный пульт ВВЭР-70 (БПУ АЭС Райнсберг, art/blockbench/core/reactor_panels.md, текстура vver70_bpu.png): мощность,
 * период, давление первого контура, уровень газового КД, шесть групп АРК, температуры и расход трёх петель, бор, лампы
 * ПЗ-1 (снижение мощности) и ПЗ-2 (запрет вывода стержней), ключи трёх ГЦН и ключ АЗ под откидной крышкой — срабатывает,
 * если держать его {@value #AZ_HOLD_MS} мс. Каналы {@link PwrPanels} плюс расходы петель, третий ГЦН и флаги ПЗ.
 *
 * <p>Модель реактора ведёт аварийную группу и одну регулирующую: группы АРК 1–5 показывают аварийную (компенсирующие
 * группы выводятся и вводятся вместе), группа 6 — регулирующую. Шкала мощности линейная.
 */
public final class Vver70Panels {
    public static final int LOOP_FLOW = PwrPanels.CHANNELS, PUMP3 = LOOP_FLOW + 3, V70_FLAGS = PUMP3 + 1, CHANNELS = V70_FLAGS + 1;
    /** {@link #V70_FLAGS}: ПЗ-1 (ступенчатое снижение мощности), ПЗ-2 (запрет вывода стержней), ключи ГЦН 1–3 включены. */
    public static final int F_PZ1 = 0, F_PZ2 = 1, F_MCP0 = 2;
    /** Ключи ГЦН: пуск петли i — {@code B_MCP_START + i}, стоп — {@code B_MCP_STOP + i}. */
    public static final int B_MCP_START = 40, B_MCP_STOP = 43;
    public static final int AZ_HOLD_MS = 1500;

    private static final Tr PCT = PanelText.PERCENT, SEC = PanelText.tr("unit.s", "s", "с"), MPA = PanelText.MPA,
            N = PanelText.tr("npp.n", "Power", "Мощность"),
            T_PERIOD = PanelText.tr("npp.period", "Period", "Период"),
            P1 = PanelText.tr("pwr.p1", "Primary P", "Давление 1К"),
            KD = PanelText.tr("v70.kd", "KD", "КД"),
            KD_TIP = PanelText.tr("v70.kd_tip", "Gas volume compensator level %s %% · nitrogen cushion holds the pressure",
                    "Уровень газового КД %s %% · давление держит азотная подушка"),
            ARK = PanelText.tr("v70.ark", "ARK %s", "АРК %s"),
            ARK_TIP = PanelText.tr("v70.ark_tip", "Groups 1–5 move with the scram bank, group 6 is the regulating group",
                    "Группы 1–5 ходят с аварийной группой, группа 6 — регулирующая"),
            LOOP = PanelText.tr("v70.loop", "L%s %s/%s °C %s%%", "П%s %s/%s °C %s%%"),
            BORON = PanelText.tr("v70.boron", "Boron %s g/kg", "Бор %s г/кг"),
            PZ1 = PanelText.tr("v70.pz1", "PZ-1", "ПЗ-1"),
            PZ2 = PanelText.tr("v70.pz2", "PZ-2", "ПЗ-2"),
            ON = PanelText.tr("v70.mcp_on", "MCP%s ▶", "ГЦН%s ▶"),
            OFF = PanelText.tr("v70.mcp_off", "■", "■"),
            ON_TIP = PanelText.tr("v70.mcp_on_tip", "Close the breaker of main coolant pump %s", "Включить выключатель ГЦН-%s"),
            OFF_TIP = PanelText.tr("v70.mcp_off_tip", "Trip main coolant pump %s", "Отключить ГЦН-%s"),
            AZ = PanelText.tr("v70.az", "AZ", "АЗ"),
            AZ_TIP = PanelText.tr("v70.az_tip", "Lift the guard and hold for 1.5 s to scram the reactor",
                    "Откинуть крышку и держать 1,5 с — аварийная защита реактора");

    public static final PanelLayout BPU = PanelLayout.builder("vver70_bpu", 220, 240, CHANNELS)
            .art(PromptCritical.id("textures/gui/panel/vver70_bpu.png"))
            .dial(12, 24, false, PwrPanels.POWER, 10, 130, 107, 0, PCT.get(), N.get(), null)
            .dial(66, 24, false, PwrPanels.PERIOD, 10, 100, Double.NaN, 0, SEC.get(), T_PERIOD.get(), null)
            .dial(120, 24, false, PwrPanels.PRESSURE, 100, 16, 11.5, 1, MPA.get(), P1.get(), null)
            .glass(184, 24, 56, PwrPanels.PZR_LEVEL, 0xFF4060A0, 0.2, 0.5, KD.get(), v -> KD_TIP.get(v.num(PwrPanels.PZR_LEVEL, 10, 1)))
            .bar(12, 98, 92, PwrPanels.AZ, 10, 100, 0xFFB03030, ARK.get(1), v -> ARK_TIP.get())
            .bar(116, 98, 92, PwrPanels.AZ, 10, 100, 0xFFB03030, ARK.get(2), v -> ARK_TIP.get())
            .bar(12, 116, 92, PwrPanels.AZ, 10, 100, 0xFFB03030, ARK.get(3), v -> ARK_TIP.get())
            .bar(116, 116, 92, PwrPanels.AZ, 10, 100, 0xFFB03030, ARK.get(4), v -> ARK_TIP.get())
            .bar(12, 134, 92, PwrPanels.AZ, 10, 100, 0xFFB03030, ARK.get(5), v -> ARK_TIP.get())
            .bar(116, 134, 92, PwrPanels.RG, 10, 100, 0xFF707070, ARK.get(6), v -> ARK_TIP.get())
            .readout(12, 148, 62, v -> loop(v, 0), null)
            .readout(79, 148, 62, v -> loop(v, 1), null)
            .readout(146, 148, 62, v -> loop(v, 2), null)
            .readout(12, 166, 96, v -> BORON.get(v.num(PwrPanels.BORON, 100, 2)), null)
            .lamp(116, 165, V70_FLAGS, F_PZ1, LampColor.AMBER, PZ1.get())
            .lamp(162, 165, V70_FLAGS, F_PZ2, LampColor.AMBER, PZ2.get())
            .button(12, 188, 30, B_MCP_START, ON.get(1), ON_TIP.get(1))
            .button(46, 188, 30, B_MCP_STOP, OFF.get(), OFF_TIP.get(1))
            .button(80, 188, 30, B_MCP_START + 1, ON.get(2), ON_TIP.get(2))
            .button(114, 188, 30, B_MCP_STOP + 1, OFF.get(), OFF_TIP.get(2))
            .button(148, 188, 30, B_MCP_START + 2, ON.get(3), ON_TIP.get(3))
            .button(182, 188, 30, B_MCP_STOP + 2, OFF.get(), OFF_TIP.get(3))
            .holdButton(80, 214, 60, PwrPanels.B_SCRAM, AZ_HOLD_MS, AZ.get(), AZ_TIP.get())
            .noInventory()
            .build();

    private Vver70Panels() {
    }

    /** Петля: горячая и холодная нитки (общие для зоны), расход петли; без ключа ГЦН — серым. */
    private static Component loop(PanelValues v, int i) {
        Component c = LOOP.get(i + 1, v.raw(PwrPanels.HOT_C), v.raw(PwrPanels.COLD_C), v.raw(LOOP_FLOW + i));
        return v.bit(V70_FLAGS, F_MCP0 + i) ? c : c.copy().withStyle(ChatFormatting.DARK_GRAY);
    }
}
