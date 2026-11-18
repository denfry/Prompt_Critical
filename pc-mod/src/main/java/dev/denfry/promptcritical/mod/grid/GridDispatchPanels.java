package dev.denfry.promptcritical.mod.grid;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayout.LampColor;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * Щит диспетчера энергосистемы (07 §7.6.6): частота, генерация, нагрузка и потери основного острова, скорость
 * изменения частоты, острова под напряжением и обесточенные, прогноз нагрузки; лампа «режим нарушен».
 */
public final class GridDispatchPanels {
    public static final int ISLANDS = 0, DEAD = 1, HZ100 = 2, GEN_KW = 3, LOAD_KW = 4, LOSS_KW = 5, ROCOF1000 = 6, FORECAST_KW = 7,
            FLAGS = 8, CHANNELS = 9;
    public static final int F_ALARM = 0;

    private static final Tr HZ = PanelText.tr("unit.hz", "Hz", "Гц"),
            KW = PanelText.tr("unit.kw", "kW", "кВт"),
            FREQUENCY = PanelText.tr("dispatch.frequency", "Frequency", "Частота"),
            GENERATION = PanelText.tr("dispatch.generation", "Generation", "Генерация"),
            LOAD = PanelText.tr("dispatch.load", "Load", "Нагрузка"),
            ALARM = PanelText.tr("dispatch.alarm", "Off-normal", "Режим нарушен"),
            STATUS = PanelText.tr("dispatch.status",
                    "%s islands live, %s dead · losses %s kW · df/dt %s Hz/s · forecast %s ± 3 %% kW",
                    "островов под напряжением %s, обесточено %s · потери %s кВт · df/dt %s Гц/с · прогноз %s ± 3 %% кВт"),
            TIP = PanelText.tr("dispatch.tip", "The dispatcher sees the whole grid; the plan, AGC and reserves are yours (pc_grid)",
                    "Диспетчер видит всю сеть; план, АРЧМ и резервы — ваши (pc_grid)");

    public static final PanelLayout DISPATCH = PanelLayout.builder("grid_dispatch_center", 232, 120, CHANNELS)
            .dial(8, 24, false, HZ100, 100, 52, 50.5, 2, HZ.get(), FREQUENCY.get(), null)
            .dial(64, 24, false, GEN_KW, 1, 20000, Double.NaN, 0, KW.get(), GENERATION.get(), null)
            .dial(120, 24, false, LOAD_KW, 1, 20000, Double.NaN, 0, KW.get(), LOAD.get(), null)
            .lamp(176, 26, FLAGS, F_ALARM, LampColor.RED, ALARM.get())
            .readout(8, 96, 216, GridDispatchPanels::status, v -> TIP.get())
            .noInventory().build();

    private GridDispatchPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(DISPATCH);
    }

    private static Component status(PanelValues v) {
        return STATUS.get(v.raw(ISLANDS), v.raw(DEAD), v.raw(LOSS_KW), v.num(ROCOF1000, 1000, 3), v.raw(FORECAST_KW));
    }
}
