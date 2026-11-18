package dev.denfry.promptcritical.mod.scada.device;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelText;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;

/**
 * Тег устройства (03b §3b.4) — прибор местного щита машины, как его видит компьютер: то же, что видит игрок у щита, и
 * ничего сверх (03b §3b.0, правило 1: истинные переменные симуляции Lua недоступны). Имя — короткий ключ надписи
 * щита, как у шлюза {@code pc_rtu}; шкала {@code lo…hi} — шкала прибора (для Modbus-регистров §3b.4.5).
 *
 * @param kind  {@code ANALOG}, {@code DISCRETE} или {@code CURVE} (динамограмма: таблица точек)
 * @param scale делитель канала (у дискретного — номер бита)
 */
public record DeviceTag(String name, String kind, String label, String unit, int ch, double scale, double lo, double hi, int count) {
    public static final String ANALOG = "ANALOG", DISCRETE = "DISCRETE", CURVE = "CURVE";

    /** Теги щита по раскладке; имена совпадают с точками шлюза телемеханики. */
    public static List<DeviceTag> of(PanelLayout layout) {
        List<DeviceTag> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (PanelLayout.Element e : layout.elements) {
            switch (e) {
                case PanelLayout.Dial d -> out.add(tag(used, d.label(), ANALOG, PanelText.english(d.unit()), d.ch(), d.scale(), d.max(), 1, d.ch()));
                case PanelLayout.Glass g -> out.add(tag(used, g.label(), ANALOG, "", g.ch(), 1000, 1, 1, g.ch()));
                case PanelLayout.Bar b -> out.add(tag(used, b.label(), ANALOG, "", b.ch(), b.scale(), b.max(), 1, b.ch()));
                case PanelLayout.UTube u -> out.add(tag(used, u.label(), ANALOG, "", u.ch(), u.scale(), u.max(), 1, u.ch()));
                case PanelLayout.Lamp l -> out.add(tag(used, l.label(), DISCRETE, "", l.ch(), l.bit(), 1, 1, l.ch() * 32 + l.bit()));
                case PanelLayout.Plot p -> out.add(tag(used, p.label(), CURVE, "", p.ch0(), 1, Double.NaN, p.points(), p.ch0()));
                default -> {
                }
            }
        }
        return List.copyOf(out);
    }

    private static DeviceTag tag(Set<String> used, Component label, String kind, String unit, int ch, double scale, double hi, int count,
            int disambiguator) {
        String name = PanelText.shortKey(label);
        if (!used.add(name)) {
            name = name + "_" + disambiguator;
            used.add(name);
        }
        double top = Double.isFinite(hi) && hi > 0 ? hi : 1;
        return new DeviceTag(name, kind, PanelText.english(label), unit, ch, scale, 0, CURVE.equals(kind) ? Double.NaN : top, count);
    }

    /** Число для подписок и регистров: аналог — показание, дискрет — 0/1, кривая — NaN. */
    public double numeric(int[] channels) {
        if (ch < 0 || ch >= channels.length) return Double.NaN;
        return switch (kind) {
            case DISCRETE -> (channels[ch] & 1 << (int) scale) != 0 ? 1 : 0;
            case CURVE -> Double.NaN;
            default -> channels[ch] / scale;
        };
    }

    /** Значение для Lua: число, логическое или таблица точек {@code {x, y}}. */
    public Object value(int[] channels) {
        return switch (kind) {
            case DISCRETE -> ch >= 0 && ch < channels.length && (channels[ch] & 1 << (int) scale) != 0;
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
            default -> ch >= 0 && ch < channels.length ? channels[ch] / scale : 0.0;
        };
    }

    /** {@code getTagInfo}: {@code unit, eu, lo, hi, accuracyClass, tau, kind, description} (03b §3b.1.9). */
    public Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("unit", unit);
        m.put("eu", unit);
        if (!CURVE.equals(kind)) {
            m.put("lo", lo);
            m.put("hi", hi);
        }
        // показание щита: класс точности и инерция — у прибора, а не у канала; канал передаёт как есть
        m.put("accuracyClass", 0.0);
        m.put("tau", 0.0);
        m.put("kind", kind);
        m.put("description", label);
        return m;
    }
}
