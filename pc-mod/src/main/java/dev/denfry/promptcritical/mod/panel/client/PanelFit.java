package dev.denfry.promptcritical.mod.panel.client;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Подгонка подписей щита под свободное место: у каждой подписи считается ширина, до которой её можно растянуть, не
 * залезая на приборы, кнопки, слоты, соседние подписи и край панели; длинная подпись рисуется сжатой. Тот же расчёт
 * отдаёт список наложений, которые сжатием не снять (корпуса друг на друге, подпись мельче {@link #MIN_SCALE}) —
 * для проверки раскладок на всех языках. Координаты — от угла панели. Только клиент.
 */
public final class PanelFit {
    /** Мельче этого подпись не читается — считается наложением. */
    public static final float MIN_SCALE = 0.6f;
    private static final int EDGE = 3, GAP = 2, LINE = 9;

    private final Map<PanelLayout.Element, Integer> room = new IdentityHashMap<>();
    private final List<String> problems = new ArrayList<>();
    private int titleRoom;

    private PanelFit() {
    }

    /** Ширина, в которую надо уложить подпись элемента; без записи — подпись помещается как есть. */
    public int room(PanelLayout.Element e, int natural) {
        Integer r = room.get(e);
        return r == null ? natural : Math.min(natural, r);
    }

    public int titleRoom() {
        return titleRoom;
    }

    public List<String> problems() {
        return problems;
    }

    private record Box(String name, int x0, int y0, int x1, int y1) {
        boolean hits(Box o) {
            return x0 < o.x1 && o.x0 < x1 && y0 < o.y1 && o.y0 < y1;
        }
    }

    /** Подпись: якорь {@code ax} (левый край или центр), строка {@code y}, натуральная ширина. */
    private static final class Label {
        final PanelLayout.Element owner;
        final String name;
        final boolean centered;
        final int ax, y, natural;
        int lo, hi;

        Label(PanelLayout.Element owner, String name, boolean centered, int ax, int y, int natural) {
            this.owner = owner;
            this.name = name;
            this.centered = centered;
            this.ax = ax;
            this.y = y;
            this.natural = natural;
        }

        int width() {
            return centered ? 2 * Math.min(ax - lo, hi - ax) : hi - ax;
        }

        Box box() {
            int w = Math.min(width(), natural);
            int l = centered ? ax - w / 2 : ax;
            return new Box(name, l, y - 1, l + w, y + LINE);
        }
    }

    public static PanelFit of(PanelLayout layout, Font font, Component title) {
        PanelFit fit = new PanelFit();
        int w = layout.width, h = layout.height;
        int titleW = font.width(title);
        fit.titleRoom = Math.min(titleW, w - 2 * EDGE - 16);
        if (titleW > fit.titleRoom && fit.titleRoom < titleW * MIN_SCALE) fit.problems.add(layout.id + ": title too long");
        int plate = fit.titleRoom + 16;
        List<Box> bodies = new ArrayList<>();
        bodies.add(new Box("title", (w - plate) / 2, 5, (w + plate) / 2, 19));
        List<Label> labels = new ArrayList<>();
        for (PanelLayout.Element e : layout.elements) {
            int x = e.x(), y = e.y();
            switch (e) {
                case PanelLayout.Dial d -> {
                    bodies.add(new Box("dial " + str(d.label()), x, y, x + d.size(), y + d.size()));
                    labels.add(new Label(e, str(d.label()), true, x + d.size() / 2, y + d.size() + 2, font.width(d.label())));
                }
                case PanelLayout.Scope s -> {
                    int n = PanelLayout.Scope.SIZE;
                    bodies.add(new Box("scope", x, y, x + n, y + n));
                    labels.add(new Label(e, str(s.label()), true, x + n / 2, y + n + 2, font.width(s.label())));
                }
                case PanelLayout.Glass gl -> {
                    bodies.add(new Box("glass " + str(gl.label()), x - 2, y, x + 14, y + gl.h()));
                    labels.add(new Label(e, str(gl.label()), true, x + 6, y + gl.h() + 2, font.width(gl.label())));
                }
                case PanelLayout.Bar b -> {
                    bodies.add(new Box("bar " + str(b.label()), x, y, x + b.w(), y + 8));
                    labels.add(new Label(e, str(b.label()), false, x, y - 10, font.width(b.label())));
                }
                case PanelLayout.Lamp l -> {
                    bodies.add(new Box("lamp " + str(l.label()), x, y, x + 14, y + 14));
                    labels.add(new Label(e, str(l.label()), false, x + 16, y + 3, font.width(l.label())));
                }
                case PanelLayout.Readout r -> bodies.add(new Box("readout", x, y, x + r.w(), y + 13));
                case PanelLayout.Caption c -> labels.add(new Label(e, str(c.text()), false, x, y, font.width(c.text())));
                case PanelLayout.Button b -> bodies.add(new Box("button " + str(b.label()), x, y, x + b.w(), y + 16));
            }
        }
        for (PanelLayout.SlotSpec s : layout.slots) bodies.add(new Box("slot " + s.index(), s.x() - 1, s.y() - 1, s.x() + 17, s.y() + 17));
        int bottom = h;
        for (int i = 0; i < bodies.size(); i++) {
            Box a = bodies.get(i);
            if (a.x0 < 0 || a.y0 < 0 || a.x1 > w || a.y1 > bottom) fit.problems.add(layout.id + ": " + a.name + " outside panel");
            for (int j = i + 1; j < bodies.size(); j++) {
                Box b = bodies.get(j);
                if (a.hits(b)) fit.problems.add(layout.id + ": " + a.name + " overlaps " + b.name);
            }
        }
        // свободный коридор каждой подписи по корпусам и краям панели
        for (Label l : labels) {
            l.lo = EDGE;
            l.hi = w - EDGE;
            if (l.y + LINE > bottom || l.y - 1 < 0) fit.problems.add(layout.id + ": label '" + l.name + "' outside panel");
            for (Box b : bodies) {
                if (b.y0 >= l.y + LINE || b.y1 <= l.y - 1) continue;
                if (b.x1 <= l.ax) l.lo = Math.max(l.lo, b.x1 + GAP);
                else if (b.x0 >= l.ax) l.hi = Math.min(l.hi, b.x0 - GAP);
                else {
                    l.lo = l.hi = l.ax;
                }
            }
        }
        // соседние подписи на одной строке делят промежуток
        for (int pass = 0; pass < 4; pass++) {
            for (int i = 0; i < labels.size(); i++) {
                for (int j = 0; j < labels.size(); j++) {
                    Label a = labels.get(i), b = labels.get(j);
                    if (i == j || a.ax > b.ax || (a.ax == b.ax && i > j)) continue;
                    if (Math.abs(a.y - b.y) >= LINE + 1) continue;
                    Box ba = a.box(), bb = b.box();
                    if (!ba.hits(bb)) continue;
                    int split = (ba.x1 + bb.x0) / 2;
                    if (a.centered == b.centered) split = (a.ax + b.ax) / 2;
                    else if (!a.centered) split = Math.max(split, a.ax + 1);
                    a.hi = Math.min(a.hi, split - 1);
                    b.lo = Math.max(b.lo, split + 1);
                }
            }
        }
        for (Label l : labels) {
            int r = Math.max(0, l.width());
            if (r < l.natural) {
                fit.room.put(l.owner, r);
                if (r < l.natural * MIN_SCALE) fit.problems.add(layout.id + ": label '" + l.name + "' squeezed to " + r + "/" + l.natural);
            }
        }
        return fit;
    }

    private static String str(Component c) {
        return c.getString();
    }
}
