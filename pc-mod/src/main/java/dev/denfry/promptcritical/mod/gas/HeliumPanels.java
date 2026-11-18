package dev.denfry.promptcritical.mod.gas;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит гелиевой установки: температура первой ступени, извлечение, чистота концентрата, выработка, расход азота. */
public final class HeliumPanels {
    public static final int T1_K = 0, RECOVERY = 1, CRUDE = 2, HE_LH = 3, LN2_KGH = 4, TANK = 5, FLAGS = 6, CHANNELS = 7;
    public static final int F_ON = 0, F_RUN = 1, F_NO_LN2 = 2;
    public static final int B_RUN = 0, B_T_DOWN = 1, B_T_UP = 2;

    private static final Tr STAGE1 = PanelText.tr("helium.stage1", "Stage 1", "1-я ступень"),
            RECOVERY_L = PanelText.tr("helium.recovery", "He yield", "Извлеч. He"),
            HE = PanelText.tr("helium.he", "He", "He"),
            ON = PanelText.tr("helium.on", "On", "Вкл."),
            RUN = PanelText.tr("helium.run", "Run", "Пуск/стоп"),
            WORKING = PanelText.tr("helium.working", "Helium", "Гелий"),
            NO_LN2 = PanelText.tr("helium.no_ln2", "No LN₂", "Нет LN₂"),
            T_DOWN = PanelText.tr("helium.t_down", "Stage 1 −5 K", "1-я ступень −5 К"),
            T_UP = PanelText.tr("helium.t_up", "Stage 1 +5 K", "1-я ступень +5 К"),
            STATUS = PanelText.tr("helium.status", "Helium 99.995 %%: %s l/h · concentrate %s %% He · liquid nitrogen %s kg/h",
                    "Гелий 99,995 %%: %s л/ч · концентрат %s %% He · жидкий азот %s кг/ч");

    public static final PanelLayout HELIUM = PanelLayout.builder("helium_recovery_unit", 216, 110, CHANNELS)
            .dial(8, 24, false, T1_K, 1, 300, Double.NaN, 0, PanelText.tr("unit.k", "K", "К").get(), STAGE1.get(), null)
            .dial(64, 24, false, RECOVERY, 10, 100, Double.NaN, 1, PanelText.tr("unit.pct", "%", "%").get(), RECOVERY_L.get(), null)
            .glass(120, 24, 56, TANK, 0xFFE8E0F0, Double.NaN, Double.NaN, HE.get(), null)
            .lamp(150, 22, FLAGS, F_ON, LampColor.GREEN, ON.get())
            .lamp(150, 36, FLAGS, F_RUN, LampColor.GREEN, WORKING.get())
            .lamp(150, 50, FLAGS, F_NO_LN2, LampColor.AMBER, NO_LN2.get())
            .button(8, 80, 44, B_RUN, RUN.get(), null)
            .button(56, 80, 20, B_T_DOWN, Component.literal("−"), T_DOWN.get())
            .button(78, 80, 20, B_T_UP, Component.literal("+"), T_UP.get())
            .readout(8, 96, 200, v -> STATUS.get(v.raw(HE_LH), v.num(CRUDE, 10, 1), v.raw(LN2_KGH)), null)
            .noInventory().build();

    private HeliumPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(HELIUM);
    }
}
