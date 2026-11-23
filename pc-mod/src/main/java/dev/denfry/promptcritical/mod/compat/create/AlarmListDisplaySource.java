package dev.denfry.promptcritical.mod.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import dev.denfry.promptcritical.mod.compat.CompatText;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import dev.denfry.promptcritical.mod.scada.AnnunciatorPanels;
import dev.denfry.promptcritical.sim.alarm.AlarmState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Источник Display Link на светосигнальном табло: окна, что горят, первопричина первой, затем неквитированные, затем
 * по приоритету (16 §16.2.1, 03c §3c.2.7). Строк — сколько вмещает дисплей.
 */
public final class AlarmListDisplaySource extends DisplaySource {
    /** Окон на табло: 4 ряда по 3 (каналы {@code W0 + 2i}, {@code W0 + 2i + 1}). */
    static final int WINDOWS = 12;

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        return lines(context.getSourceBlockEntity(), Math.max(1, stats.maxRows()));
    }

    /** Строки табло {@code be}, не больше {@code max}; одна строка «нет сигнализаций», если ничего не горит. */
    public static List<MutableComponent> lines(@Nullable BlockEntity be, int max) {
        if (!(be instanceof PanelHost host) || be.isRemoved() || host.panelLayout() != AnnunciatorPanels.ANNUNCIATOR)
            return List.of(Component.literal(PanelReadings.DASHES));
        int[] raw = new int[host.panelLayout().channels];
        host.readPanel(raw);
        PanelValues v = ch -> raw[ch];
        List<Integer> lit = new ArrayList<>();
        for (int i = 0; i < WINDOWS; i++)
            if ((flags(raw, i) & AnnunciatorPanels.F_PRESENT) != 0) lit.add(i);
        if (lit.isEmpty()) return List.of(Component.translatable(CompatText.key("display.no_alarms")));
        lit.sort(Comparator.<Integer>comparingInt(i -> (flags(raw, i) & AnnunciatorPanels.F_FIRST_OUT) != 0 ? 0 : 1)
                .thenComparingInt(i -> (flags(raw, i) & 15) == AlarmState.UNACK.ordinal() ? 0 : 1)
                .thenComparingInt(i -> priority(flags(raw, i)))
                .thenComparingInt(i -> i));
        List<MutableComponent> out = new ArrayList<>();
        for (int i : lit) {
            if (out.size() == max) break;
            // Окно табло мигает цветом на клиенте; дисплею Create нужна только надпись.
            out.add(Component.literal(AnnunciatorPanels.windowAt(v, AnnunciatorPanels.W0 + 2 * i, AnnunciatorPanels.W0 + 2 * i + 1).getString()));
        }
        return out;
    }

    private static int flags(int[] raw, int window) {
        return raw[AnnunciatorPanels.W0 + 2 * window + 1];
    }

    /** Приоритет 1 — самый важный; 0 (без приоритета) — в конец. */
    private static int priority(int flags) {
        int p = flags >> 4 & 15;
        return p == 0 ? 16 : p;
    }
}
