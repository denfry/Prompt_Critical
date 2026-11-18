package dev.denfry.promptcritical.mod.scada;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelLayouts;
import dev.denfry.promptcritical.mod.panel.PanelText;
import dev.denfry.promptcritical.sim.alarm.AlarmDefinition;
import dev.denfry.promptcritical.sim.alarm.AlarmKind;
import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * Встроенные сигнализации машин (03c §3c.6.3: «создаются модом автоматически при сборке блоков»): то, что местный щит
 * машины уже считает тревогой. Красная лампа — дискретная сигнализация приоритета 2, жёлтая — 3; зелёные и белые
 * лампы — статусы, а не сигнализации (03c §3c.6.1). Стрелочный прибор с красной зоной — HIGH приоритета 2 с пределом
 * у начала зоны. Матрица §3c.6.3 требует знать время на реакцию и тяжесть, а щит их не знает: принято, что красное на
 * щите машины — «значительные последствия за 3–10 мин» (приоритет 2), жёлтое — 3; приоритет 1 и пределы игрок
 * назначает сам через {@code pc_annunciator}. Зона нечувствительности 2 %, задержки 2 с [BALANCE]: опрос щитов — раз
 * в секунду, задержка в один опрос гасит дребезг на границе.
 *
 * <p>Тег сигнализации — {@code тип@x,y,z/точка}, как у историана (03d §3d.9.5): имя точки — то же, что у шлюза
 * телемеханики ({@link TelemetryPoint}). Ссылка {@link #ref} — номер прибора среди всех ламп и приборов всех щитов в
 * порядке {@link PanelLayouts#all()}: одинакова на сервере и клиенте, по ней окно табло на клиенте показывает
 * переведённую надпись прибора.
 */
public final class AlarmPoints {
    public static final double DEADBAND = 0.02, DELAY_S = 2;

    /** Прибор щита, который может быть сигнализацией: раскладка и элемент. */
    public record Ref(PanelLayout layout, PanelLayout.Element element) {
        public Component label() {
            return switch (element) {
                case PanelLayout.Lamp l -> l.label();
                case PanelLayout.Dial d -> d.label();
                default -> Component.empty();
            };
        }

        /** Название машины: блок с id раскладки, если такой ключ есть. */
        public String machineKey() {
            return "block.promptcritical." + layout.id;
        }
    }

    /** Сигнализация машины на одном опросе: описание, ссылка на прибор и измеренное значение. */
    public record Point(AlarmDefinition definition, int ref, double value) {
    }

    private static List<Ref> refs;
    private static Map<PanelLayout.Element, Integer> index;

    private AlarmPoints() {
    }

    private static synchronized void build() {
        if (refs != null) return;
        List<Ref> r = new ArrayList<>();
        Map<PanelLayout.Element, Integer> idx = new java.util.IdentityHashMap<>();
        for (PanelLayout l : PanelLayouts.all().values())
            for (PanelLayout.Element e : l.elements)
                if (e instanceof PanelLayout.Lamp || e instanceof PanelLayout.Dial) {
                    idx.put(e, r.size());
                    r.add(new Ref(l, e));
                }
        refs = r;
        index = idx;
    }

    @Nullable
    public static Ref ref(int i) {
        build();
        return i >= 0 && i < refs.size() ? refs.get(i) : null;
    }

    /**
     * Цветные лампы положения, а не отклонения: «огонь», «выключатель включён», «контактор», «автомат», «нагрузка»,
     * «насос», «тормоз» и т. п. По электротехнической традиции они красные или жёлтые, но это статусы (03c §3c.6.1).
     */
    private static final java.util.Set<String> STATUS_WORDS = java.util.Set.of("lit", "breaker", "closed", "contactor", "switch", "auto",
            "load", "pump", "sluice", "brake", "goods", "flux", "tree", "disc");

    static boolean status(PanelLayout.Lamp l) {
        String k = PanelText.shortKey(l.label());
        return STATUS_WORDS.contains(k.substring(Math.max(k.lastIndexOf('.'), k.lastIndexOf('_')) + 1));
    }

    /** Сигнализации машины {@code machine} ({@code тип@x,y,z}) по её щиту и снятым каналам. */
    public static List<Point> of(String machine, PanelLayout layout, int[] ch) {
        build();
        List<Point> out = new ArrayList<>();
        List<TelemetryPoint> points = TelemetryPoint.of(layout);
        int p = 0;
        for (PanelLayout.Element e : layout.elements) {
            boolean telemetered = e instanceof PanelLayout.Dial || e instanceof PanelLayout.Glass || e instanceof PanelLayout.Bar
                    || e instanceof PanelLayout.UTube || e instanceof PanelLayout.Lamp || e instanceof PanelLayout.Plot;
            if (!telemetered) continue;
            TelemetryPoint tp = points.get(p++);
            String tag = machine + "/" + tp.name();
            String text = machine.substring(0, machine.indexOf('@')) + ": " + tp.label();
            switch (e) {
                case PanelLayout.Lamp l when (l.color() == PanelLayout.LampColor.RED || l.color() == PanelLayout.LampColor.AMBER)
                        && !status(l) -> {
                    int prio = l.color() == PanelLayout.LampColor.RED ? 2 : 3;
                    double v = l.ch() < ch.length && (ch[l.ch()] & 1 << l.bit()) != 0 ? 1 : 0;
                    out.add(new Point(new AlarmDefinition(0, tag, AlarmKind.DISCRETE, prio, 0, 0, DELAY_S, DELAY_S, false, machine, text,
                            "check " + machine), index.getOrDefault(e, -1), v));
                }
                case PanelLayout.Dial d when !Double.isNaN(d.redFrom()) -> {
                    double v = d.ch() < ch.length ? ch[d.ch()] / d.scale() : Double.NaN;
                    out.add(new Point(new AlarmDefinition(0, tag, AlarmKind.HIGH, 2, d.redFrom(), DEADBAND, DELAY_S, DELAY_S, false, machine,
                            text + " in the red zone", "reduce " + PanelText.english(d.label())), index.getOrDefault(e, -1), v));
                }
                default -> {
                }
            }
        }
        return out;
    }
}
