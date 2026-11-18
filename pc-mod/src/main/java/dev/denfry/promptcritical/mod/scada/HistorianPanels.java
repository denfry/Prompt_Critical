package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelText.Tr;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.List;
import net.minecraft.network.chat.Component;

/** Щит историана (03d §3d.9.5): число тегов и точек, сжатие, выбранный тег — значение и агрегаты за час. */
public final class HistorianPanels {
    public static final int TAGS = 0, POINTS = 1, RATIO10 = 2, SELECTED = 3, VALUE1000 = 4, AVG1000 = 5, MIN1000 = 6, MAX1000 = 7,
            CHANNELS = 8;
    public static final int B_PREV = 0, B_NEXT = 1;

    private static final Tr STATS = PanelText.tr("historian.stats", "%s tags · %s points in the archive · compression ×%s",
                    "тегов %s · точек в архиве %s · сжатие ×%s"),
            TAG = PanelText.tr("historian.tag", "Tag #%s: now %s · hour avg %s, min %s, max %s",
                    "Тег №%s: сейчас %s · за час среднее %s, мин %s, макс %s"),
            PREV = PanelText.tr("historian.prev", "< Tag", "< Тег"),
            NEXT = PanelText.tr("historian.next", "Tag >", "Тег >"),
            TIP = PanelText.tr("historian.tip", "Archives every analog point of the RTUs within 16 blocks; queries — pc_historian",
                    "Архивирует все аналоговые точки шлюзов КП в 16 блоках; запросы — pc_historian");

    public static final PanelLayout HISTORIAN = PanelLayout.builder("historian_server", 232, 100, CHANNELS)
            .readout(8, 24, 216, v -> STATS.get(v.raw(TAGS), v.raw(POINTS), v.num(RATIO10, 10, 1)), v -> TIP.get())
            .readout(8, 44, 216, HistorianPanels::tag, null)
            .button(8, 70, 60, B_PREV, PREV.get(), null)
            .button(72, 70, 60, B_NEXT, NEXT.get(), null)
            .noInventory().build();

    private HistorianPanels() {
    }

    public static List<PanelLayout> all() {
        return List.of(HISTORIAN);
    }

    private static Component tag(PanelValues v) {
        return TAG.get(v.raw(SELECTED) + 1, v.num(VALUE1000, 1000, 2), v.num(AVG1000, 1000, 2), v.num(MIN1000, 1000, 2), v.num(MAX1000, 1000, 2));
    }
}
