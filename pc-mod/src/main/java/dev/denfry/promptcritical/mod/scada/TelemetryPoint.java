package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Точка телемеханики — один прибор местного щита машины, как его видит диспетчер: телеизмерение (стрелочный
 * прибор, указатель уровня, шкала), телесигнал (сигнальная лампа) или кривая (динамограмма). Имя — короткий ключ
 * надписи щита ({@code drill.spp}), единицы — английский текст единицы прибора. Табло со строками в телемеханику не
 * идут: КП передаёт значения, а не текст.
 *
 * @param name  имя точки, уникальное в пределах щита
 * @param kind  {@code analog}, {@code status} или {@code curve}
 * @param label надпись прибора на английском
 * @param unit  единицы измерения (пусто — безразмерная доля)
 * @param ch    канал щита (у кривой — первый)
 * @param scale делитель канала (у телесигнала — номер бита)
 * @param count точек кривой
 */
public record TelemetryPoint(String name, String kind, String label, String unit, int ch, double scale, int count) {
    public static final String ANALOG = "analog", STATUS = "status", CURVE = "curve";

    /** Точки щита по его раскладке. */
    public static List<TelemetryPoint> of(PanelLayout layout) {
        List<TelemetryPoint> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (PanelLayout.Element e : layout.elements) {
            switch (e) {
                case PanelLayout.Dial d -> out.add(point(used, d.label(), ANALOG, PanelText.english(d.unit()), d.ch(), d.scale(), 1, d.ch()));
                case PanelLayout.Glass g -> out.add(point(used, g.label(), ANALOG, "", g.ch(), 1000, 1, g.ch()));
                case PanelLayout.Bar b -> out.add(point(used, b.label(), ANALOG, "", b.ch(), b.scale(), 1, b.ch()));
                case PanelLayout.UTube u -> out.add(point(used, u.label(), ANALOG, "", u.ch(), u.scale(), 1, u.ch()));
                case PanelLayout.Lamp l -> out.add(point(used, l.label(), STATUS, "", l.ch(), l.bit(), 1, l.ch() * 32 + l.bit()));
                case PanelLayout.Plot p -> out.add(point(used, p.label(), CURVE, "", p.ch0(), 1, p.points(), p.ch0()));
                default -> {
                }
            }
        }
        return out;
    }

    private static TelemetryPoint point(Set<String> used, net.minecraft.network.chat.Component label, String kind, String unit, int ch,
            double scale, int count, int disambiguator) {
        String name = PanelText.shortKey(label);
        if (!used.add(name)) {
            name = name + "_" + disambiguator;
            used.add(name);
        }
        return new TelemetryPoint(name, kind, PanelText.english(label), unit, ch, scale, count);
    }

    /** Значение точки по каналам щита: число, логическое или таблица точек кривой {@code {x, y}}. */
    public Object value(int[] channels) {
        return switch (kind) {
            case STATUS -> ch < channels.length && (channels[ch] & 1 << (int) scale) != 0;
            case CURVE -> {
                List<Map<String, Object>> pts = new ArrayList<>();
                for (int i = 0; i < count && ch + 2 * i + 1 < channels.length; i++) {
                    Map<String, Object> p = new LinkedHashMap<>();
                    p.put("x", channels[ch + 2 * i]);
                    p.put("y", channels[ch + 2 * i + 1]);
                    pts.add(p);
                }
                yield pts;
            }
            default -> ch < channels.length ? channels[ch] / scale : 0.0;
        };
    }

    /** Описание точки для Lua. */
    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("kind", kind);
        m.put("label", label);
        m.put("unit", unit);
        return m;
    }
}
