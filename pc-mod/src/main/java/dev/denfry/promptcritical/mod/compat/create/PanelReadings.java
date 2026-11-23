package dev.denfry.promptcritical.mod.compat.create;

import dev.denfry.promptcritical.mod.boiler.PressureGaugeBlockEntity;
import dev.denfry.promptcritical.mod.compat.CompatText;
import dev.denfry.promptcritical.mod.panel.PanelHost;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.mod.panel.PanelValues;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Показания местного щита для чужих дисплеев (16 §16.2.1): те же каналы, что видит игрок у щита, а не истинные
 * значения модели (16 N4). Прибор выбирается по номеру среди шкал, стёкол, табло раскладки; манометр котла — один
 * прибор без щита.
 */
public final class PanelReadings {
    /** Показание недоступно: источник снят, не прибор или номер вне раскладки (16 N4 — прочерки). */
    public static final String DASHES = "---";

    private PanelReadings() {
    }

    /** Приборы раскладки, которые дают одно показание; порядок — порядок раскладки. */
    public static List<PanelLayout.Element> gauges(PanelLayout layout) {
        List<PanelLayout.Element> out = new ArrayList<>();
        for (PanelLayout.Element e : layout.elements)
            if (e instanceof PanelLayout.Dial || e instanceof PanelLayout.Glass || e instanceof PanelLayout.Bar
                    || e instanceof PanelLayout.UTube || e instanceof PanelLayout.Scope || e instanceof PanelLayout.Firebox
                    || e instanceof PanelLayout.Readout)
                out.add(e);
        return out;
    }

    /** Названия приборов источника для выбора в Display Link; пусто — источник не наш прибор. */
    public static List<Component> names(@Nullable BlockEntity be) {
        if (be instanceof PressureGaugeBlockEntity) return List.of(Component.translatable(CompatText.key("display.gauge_pressure")));
        if (!(be instanceof PanelHost host)) return List.of();
        List<Component> out = new ArrayList<>();
        int readout = 0;
        for (PanelLayout.Element e : gauges(host.panelLayout()))
            out.add(e instanceof PanelLayout.Readout ? Component.translatable(CompatText.key("display.readout"), ++readout) : label(e));
        return out;
    }

    /** Строка показания прибора {@code index}; {@link #DASHES}, если показания нет. */
    public static MutableComponent reading(@Nullable BlockEntity be, int index) {
        if (be == null || be.isRemoved()) return Component.literal(DASHES);
        if (be instanceof PressureGaugeBlockEntity gauge)
            return Component.literal(PanelText.num(gauge.readingPa() / 1e6, 2) + " ").append(Component.translatable(CompatText.key("display.mpa")));
        if (!(be instanceof PanelHost host)) return Component.literal(DASHES);
        PanelLayout layout = host.panelLayout();
        List<PanelLayout.Element> gauges = gauges(layout);
        if (index < 0 || index >= gauges.size()) return Component.literal(DASHES);
        int[] raw = new int[layout.channels];
        host.readPanel(raw);
        PanelValues v = ch -> ch >= 0 && ch < raw.length ? raw[ch] : 0;
        return format(gauges.get(index), v);
    }

    static MutableComponent format(PanelLayout.Element e, PanelValues v) {
        return switch (e) {
            case PanelLayout.Dial d -> Component.literal(v.num(d.ch(), d.scale(), d.decimals()) + " ").append(d.unit().copy());
            case PanelLayout.Glass g -> Component.literal(v.num(g.ch(), 10, 0) + " %");
            case PanelLayout.Bar b -> Component.literal(v.num(b.ch(), b.scale(), 1) + " / " + PanelText.num(b.max(), 0));
            case PanelLayout.UTube u -> Component.literal(v.num(u.ch(), u.scale(), 0) + " ").append(Component.translatable(CompatText.key("display.pa")));
            case PanelLayout.Scope s -> v.raw(s.ch()) == PanelLayout.Scope.OFF ? Component.literal(DASHES)
                    : Component.literal(v.num(s.ch(), 10, 0) + "°");
            case PanelLayout.Firebox f -> Component.literal(v.num(f.bedCh(), f.bedScale(), 2) + " / " + PanelText.num(f.bedMax(), 2));
            // Табло красит строку и мигает на клиенте; дисплею Create нужен только текст.
            case PanelLayout.Readout r -> {
                String text = r.text().apply(v).getString();
                yield Component.literal(text.isBlank() ? DASHES : text); // тёмное окно табло — нет показания
            }
            default -> Component.literal(DASHES);
        };
    }

    private static Component label(PanelLayout.Element e) {
        return switch (e) {
            case PanelLayout.Dial d -> d.label();
            case PanelLayout.Glass g -> g.label();
            case PanelLayout.Bar b -> b.label();
            case PanelLayout.UTube u -> u.label();
            case PanelLayout.Scope s -> s.label();
            case PanelLayout.Firebox f -> f.label();
            default -> Component.literal("?");
        };
    }
}
