package dev.denfry.promptcritical.mod.content.client;

import dev.denfry.promptcritical.mod.content.ContentText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Вкладка «Дерево» справочника: маршрут (11 §11.1.5) как граф, по странице на ветку эпохи (шахта, кокс и чугун,
 * электросеть...). Страницы листаются закладками и стрелками, прокрутки нет: ветка, которая не помещается по высоте,
 * делится на части. Задание стоит на строку ниже своих требований из той же страницы; требования с других страниц
 * показаны блёклыми «входами» в верхней строке. Связи — трубы цвета эпохи, как окраска труб по ГОСТ 14202.
 */
final class TreePane {
    private static final int NODE = 18, COL = 24, ROW = 23;
    private static final int HEAD = 34, FOOT = 26;
    /** Цвет эпохи — окраска труб по ГОСТ 14202: пар, воздух, горючее, кислоты, прочие, вода. */
    private static final Map<String, Integer> ERA_COLOR = Map.of("I0", 0xFFB03A2E, "I1", 0xFF2F6DB0, "I2", 0xFF8A5A2B,
            "I3", 0xFFD9741F, "I4", 0xFF7A3FA0, "T", 0xFF3D7F5A);
    private static final int LOCKED_PIPE = 0xFF9C8C68, LOCKED_FILL = 0xFFE4D9BF;

    private final HandbookClient.Route route;
    private final Font font;
    private final Consumer<String> openInRoute;
    private final List<Page> pages = new ArrayList<>();
    private final Map<String, HandbookClient.RouteEra> eraOf = new HashMap<>();
    private int page;
    @Nullable
    private String selected;
    private int left, top, w, h;
    /** Раскладка текущей страницы: задание → левый верхний угол узла; входы с других страниц отдельно. */
    private final Map<String, int[]> pos = new LinkedHashMap<>();
    private final List<String> entries = new ArrayList<>();
    @Nullable
    private String chipHover;

    /** Страница дерева: ветка эпохи или её часть {@code part} из {@code parts}. */
    private record Page(HandbookClient.RouteEra era, HandbookClient.RouteBranch branch, int part, int parts,
            List<HandbookClient.RouteTask> tasks) {
    }

    TreePane(HandbookClient.Route route, Font font, Consumer<String> openInRoute) {
        this.route = route;
        this.font = font;
        this.openInRoute = openInRoute;
        for (HandbookClient.RouteEra e : route.eras()) for (HandbookClient.RouteTask t : e.tasks()) eraOf.put(t.id(), e);
    }

    void layout(int left, int top, int w, int h) {
        this.left = left;
        this.top = top;
        this.w = w;
        this.h = h;
        paginate();
        // открыть на странице, где игроку идти дальше
        for (int i = 0; i < pages.size() && selected == null; i++)
            for (HandbookClient.RouteTask t : pages.get(i).tasks())
                if (t.status().equals("open")) {
                    page = i;
                    selected = t.id();
                    break;
                }
        place();
    }

    private int graphTop() {
        return top + HEAD;
    }

    private int graphBottom() {
        return top + h - FOOT;
    }

    /** Строк заданий на странице: верхняя строка всегда под входы с других страниц. */
    private int rowsPerPage() {
        return Math.max(2, (graphBottom() - graphTop()) / ROW - 1);
    }

    private void paginate() {
        pages.clear();
        for (HandbookClient.RouteEra e : route.eras()) {
            List<HandbookClient.RouteBranch> branches = e.branches().isEmpty()
                    ? List.of(new HandbookClient.RouteBranch("", e.title())) : e.branches();
            for (HandbookClient.RouteBranch b : branches) {
                List<HandbookClient.RouteTask> ts = new ArrayList<>();
                for (HandbookClient.RouteTask t : e.tasks()) if (b.id().isEmpty() || t.branch().equals(b.id())) ts.add(t);
                if (ts.isEmpty()) continue;
                Map<String, Integer> depth = depths(ts);
                int rows = depth.values().stream().max(Integer::compare).orElse(0) + 1, per = rowsPerPage();
                int parts = (rows + per - 1) / per;
                for (int p = 0; p < parts; p++) {
                    List<HandbookClient.RouteTask> part = new ArrayList<>();
                    for (HandbookClient.RouteTask t : ts) if (depth.get(t.id()) / per == p) part.add(t);
                    pages.add(new Page(e, b, p, parts, List.copyOf(part)));
                }
            }
        }
        page = Math.min(page, Math.max(0, pages.size() - 1));
    }

    /** Глубина задания в наборе: на строку ниже самого глубокого требования из того же набора. */
    private static Map<String, Integer> depths(List<HandbookClient.RouteTask> ts) {
        Map<String, HandbookClient.RouteTask> by = new HashMap<>();
        for (HandbookClient.RouteTask t : ts) by.put(t.id(), t);
        Map<String, Integer> d = new HashMap<>();
        for (HandbookClient.RouteTask t : ts) depth(t, by, d);
        return d;
    }

    private static int depth(HandbookClient.RouteTask t, Map<String, HandbookClient.RouteTask> by, Map<String, Integer> d) {
        Integer known = d.get(t.id());
        if (known != null) return known;
        d.put(t.id(), 0); // граф без циклов (проверяется при загрузке), заглушка только от повторного входа
        int v = 0;
        for (String r : t.requires()) {
            HandbookClient.RouteTask p = by.get(r);
            if (p != null) v = Math.max(v, depth(p, by, d) + 1);
        }
        d.put(t.id(), v);
        return v;
    }

    /** Раскладка страницы: строки по глубине, в строке узлы по среднему положению своих требований, по центру. */
    private void place() {
        pos.clear();
        entries.clear();
        if (pages.isEmpty()) return;
        Page pg = pages.get(page);
        Map<String, Integer> depth = depths(pg.tasks());
        int base = depth.values().stream().min(Integer::compare).orElse(0);
        java.util.Set<String> here = new java.util.HashSet<>();
        for (HandbookClient.RouteTask t : pg.tasks()) here.add(t.id());
        for (HandbookClient.RouteTask t : pg.tasks())
            for (String r : t.requires()) if (!here.contains(r) && !entries.contains(r) && route.task(r) != null) entries.add(r);
        int cx = left + w / 2;
        rowAt(entries, cx, graphTop());
        int rows = depth.values().stream().max(Integer::compare).orElse(0) - base + 1;
        for (int r = 0; r < rows; r++) {
            List<String> row = new ArrayList<>();
            for (HandbookClient.RouteTask t : pg.tasks()) if (depth.get(t.id()) - base == r) row.add(t.id());
            row.sort((a, b) -> Double.compare(parentX(a), parentX(b)));
            rowAt(row, cx, graphTop() + (r + 1) * ROW);
        }
    }

    private double parentX(String id) {
        HandbookClient.RouteTask t = route.task(id);
        double s = 0;
        int n = 0;
        if (t != null) for (String r : t.requires()) {
            int[] p = pos.get(r);
            if (p != null) {
                s += p[0];
                n++;
            }
        }
        return n == 0 ? Double.MAX_VALUE / 2 : s / n;
    }

    private void rowAt(List<String> ids, int cx, int y) {
        int x0 = cx - (ids.size() * COL - (COL - NODE)) / 2;
        for (int i = 0; i < ids.size(); i++) pos.put(ids.get(i), new int[] {x0 + i * COL, y});
    }

    // --- отрисовка ---

    void render(GuiGraphics g, int mx, int my, HandbookScreen screen) {
        if (pages.isEmpty()) {
            Component none = Component.translatable(ContentText.ROUTE_EMPTY);
            g.drawString(font, none, left + (w - font.width(none)) / 2, top + h / 2, HandbookScreen.MUTED, false);
            return;
        }
        Page pg = pages.get(page);
        int era = color(pg.era().era());
        chipHover = null;
        renderHeader(g, pg, era, mx, my);
        // трубы: от низа требования вниз до промежутка над заданием, вбок, вниз в задание
        for (HandbookClient.RouteTask t : pg.tasks()) {
            int[] b = pos.get(t.id());
            List<String> req = t.requires();
            for (int i = 0; i < req.size(); i++) {
                int[] a = pos.get(req.get(i));
                if (a == null) continue;
                HandbookClient.RouteTask rt = route.task(req.get(i));
                int c = rt != null && rt.status().equals("done") ? color(eraOf.get(rt.id()).era()) : LOCKED_PIPE;
                int x1 = a[0] + NODE / 2, y1 = a[1] + NODE, x2 = b[0] + NODE / 2 + (i - (req.size() - 1) / 2) * 2, my2 = b[1] - 3;
                g.fill(x1, y1, x1 + 1, my2 + 1, c);
                g.fill(Math.min(x1, x2), my2, Math.max(x1, x2) + 1, my2 + 1, c);
                g.fill(x2, my2, x2 + 1, b[1], c);
            }
        }
        String hover = null;
        for (Map.Entry<String, int[]> e : pos.entrySet()) {
            HandbookClient.RouteTask t = route.task(e.getKey());
            if (t == null) continue;
            int x = e.getValue()[0], y = e.getValue()[1];
            node(g, t, x, y, entries.contains(t.id()));
            if (mx >= x && mx < x + NODE && my >= y && my < y + NODE) hover = t.id();
        }
        renderFooter(g, era, mx, my);
        if (hover != null) tooltip(g, route.task(hover), mx, my);
        else if (chipHover != null) g.renderTooltip(font, Component.literal(chipHover), mx, my);
    }

    private void renderHeader(GuiGraphics g, Page pg, int era, int mx, int my) {
        // строка 1: закладки эпох, название страницы, стрелки
        int x = left + 6, y = top + 5;
        for (HandbookClient.RouteEra e : route.eras()) {
            String s = e.era();
            int bw = font.width(s) + 8, c = color(e.era());
            boolean active = e == pg.era(), over = in(mx, my, x, y, bw, 12);
            g.fill(x, y, x + bw, y + 12, c);
            if (!active) g.fill(x + 1, y + 1, x + bw - 1, y + 11, over ? 0xFFEFE5CC : HandbookScreen.SLOT);
            g.drawString(font, s, x + 4, y + 2, active ? 0xFFFFFFFF : c, false);
            x += bw + 2;
        }
        String title = pg.era().title() + (pg.parts() > 1 ? "  " + (pg.part() + 1) + "/" + pg.parts() : "");
        int done = 0;
        for (HandbookClient.RouteTask t : pg.era().tasks()) if (t.status().equals("done")) done++;
        String count = done + " / " + pg.era().tasks().size();
        int ax = left + w - 16;
        arrow(g, left + w - 30, y + 2, "<", page > 0, mx, my);
        arrow(g, ax, y + 2, ">", page < pages.size() - 1, mx, my);
        int tx = x + 6, room = left + w - 36 - font.width(count) - 8 - tx;
        g.drawString(font, font.plainSubstrByWidth(title, Math.max(0, room)), tx, y + 2, HandbookScreen.INK, false);
        g.drawString(font, count, left + w - 36 - font.width(count), y + 2, HandbookScreen.MUTED, false);
        // строка 2: закладки веток этой эпохи
        List<HandbookClient.RouteBranch> bs = pg.era().branches();
        if (bs.size() > 1) {
            int y2 = top + 20, x2 = left + 6;
            int[] widths = chipWidths(pg);
            for (int i = 0; i < bs.size(); i++) {
                HandbookClient.RouteBranch b = bs.get(i);
                int bw = widths[i];
                boolean active = b.id().equals(pg.branch().id()), over = in(mx, my, x2, y2, bw, 11);
                if (over && font.width(b.title()) + 6 > bw) chipHover = b.title();
                g.fill(x2, y2, x2 + bw, y2 + 11, active ? era : HandbookScreen.PAPER_EDGE);
                g.fill(x2 + 1, y2 + 1, x2 + bw - 1, y2 + 10, active ? 0xFFFFF8E8 : over ? 0xFFEFE5CC : HandbookScreen.PAPER);
                String s = font.plainSubstrByWidth(b.title(), bw - 6);
                g.drawString(font, s, x2 + (bw - font.width(s)) / 2, y2 + 2, active ? HandbookScreen.INK : HandbookScreen.MUTED, false);
                x2 += bw + 2;
            }
        }
        g.fill(left + 4, graphTop() - 3, left + w - 4, graphTop() - 2, HandbookScreen.PAPER_EDGE);
    }

    /** Ширины закладок веток: все по тексту, если влезают; иначе открытая — по тексту, остальные делят остаток. */
    private int[] chipWidths(Page pg) {
        List<HandbookClient.RouteBranch> bs = pg.era().branches();
        int avail = w - 12 - 2 * (bs.size() - 1), natural = 0;
        int[] out = new int[bs.size()];
        for (int i = 0; i < bs.size(); i++) natural += out[i] = font.width(bs.get(i).title()) + 8;
        if (natural <= avail) return out;
        int active = 0;
        for (int i = 0; i < bs.size(); i++) if (bs.get(i).id().equals(pg.branch().id())) active = i;
        int rest = (avail - out[active]) / Math.max(1, bs.size() - 1);
        for (int i = 0; i < bs.size(); i++) if (i != active) out[i] = Math.max(14, rest);
        return out;
    }

    private void node(GuiGraphics g, HandbookClient.RouteTask t, int x, int y, boolean entry) {
        int c = color(eraOf.get(t.id()).era());
        boolean done = t.status().equals("done"), open = t.status().equals("open");
        boolean blink = open && (Util.getMillis() / 600) % 2 == 0;
        int border = done || blink ? c : open ? HandbookScreen.SLOT_EDGE : HandbookScreen.PAPER_EDGE;
        g.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, t.id().equals(selected) ? HandbookScreen.INK : HandbookScreen.PAPER);
        g.fill(x, y, x + NODE, y + NODE, border);
        g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, done ? mix(c, HandbookScreen.PAPER, 0.22f) : open ? 0xFFFFF8E8 : LOCKED_FILL);
        ResourceLocation rl = ResourceLocation.tryParse(t.icon());
        ItemStack s = rl == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(rl));
        if (!s.isEmpty()) g.renderItem(s, x + 1, y + 1);
        // закрытые и входы с других страниц приглушены
        if (!done && !open || entry) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 1, y + 1, x + NODE - 1, y + NODE - 1, entry ? 0x66F3EBD7 : 0x88F3EBD7);
            g.pose().popPose();
        }
    }

    private void renderFooter(GuiGraphics g, int era, int mx, int my) {
        int y = graphBottom() + 2;
        g.fill(left + 4, y - 1, left + w - 4, y, HandbookScreen.PAPER_EDGE);
        HandbookClient.RouteTask t = selected == null ? null : route.task(selected);
        if (t == null) {
            Component hint = Component.translatable(ContentText.TREE_HINT);
            g.drawString(font, hint, left + 8, y + 8, HandbookScreen.MUTED, false);
            return;
        }
        ResourceLocation rl = ResourceLocation.tryParse(t.icon());
        if (rl != null) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)), left + 8, y + 4);
        Component open = Component.translatable(ContentText.TREE_OPEN);
        int bw = font.width(open) + 10, bx = left + w - 8 - bw, by = y + 6;
        int room = bx - (left + 30) - 6;
        g.drawString(font, font.plainSubstrByWidth(t.title(), room), left + 30, y + 3, HandbookScreen.INK, false);
        String st = Component.translatable(switch (t.status()) {
            case "done" -> ContentText.ROUTE_STATUS_DONE;
            case "open" -> ContentText.ROUTE_STATUS_OPEN;
            default -> ContentText.ROUTE_STATUS_LOCKED;
        }).getString();
        HandbookClient.RouteEra e = eraOf.get(t.id());
        String where = e.era() + " · " + branchTitle(e, t.branch()) + " · " + st;
        g.drawString(font, font.plainSubstrByWidth(where, room), left + 30, y + 13, HandbookScreen.MUTED, false);
        boolean over = in(mx, my, bx, by, bw, 12);
        g.fill(bx, by, bx + bw, by + 12, era);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + 11, over ? 0xFFEFE5CC : HandbookScreen.PAPER);
        g.drawString(font, open, bx + 5, by + 2, HandbookScreen.INK, false);
    }

    private void tooltip(GuiGraphics g, HandbookClient.RouteTask t, int mx, int my) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(t.title()).getVisualOrderText());
        HandbookClient.RouteEra e = eraOf.get(t.id());
        if (entries.contains(t.id()))
            lines.add(Component.translatable(ContentText.TREE_FROM, e.era() + " · " + branchTitle(e, t.branch()))
                    .withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText());
        g.renderTooltip(font, lines, mx, my);
    }

    private static String branchTitle(HandbookClient.RouteEra e, String id) {
        for (HandbookClient.RouteBranch b : e.branches()) if (b.id().equals(id)) return b.title();
        return e.title();
    }

    private void arrow(GuiGraphics g, int x, int y, String s, boolean enabled, int mx, int my) {
        boolean over = enabled && in(mx, my, x - 2, y - 2, 12, 12);
        g.drawString(font, s, x + 2, y, !enabled ? HandbookScreen.PAPER_EDGE : over ? HandbookScreen.INK : HandbookScreen.MUTED, false);
    }

    private static boolean in(double mx, double my, int x, int y, int bw, int bh) {
        return mx >= x && mx < x + bw && my >= y && my < y + bh;
    }

    private static int color(String era) {
        return ERA_COLOR.getOrDefault(era, 0xFF5A6A60);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * t + ((b >> 16) & 255) * (1 - t));
        int gg = (int) (((a >> 8) & 255) * t + ((b >> 8) & 255) * (1 - t));
        int bl = (int) ((a & 255) * t + (b & 255) * (1 - t));
        return 0xFF000000 | r << 16 | gg << 8 | bl;
    }

    // --- ввод ---

    boolean click(double mx, double my) {
        if (pages.isEmpty()) return false;
        Page pg = pages.get(page);
        int y = top + 5;
        if (in(mx, my, left + w - 32, y, 12, 12)) return turn(-1);
        if (in(mx, my, left + w - 18, y, 12, 12)) return turn(1);
        int x = left + 6;
        for (HandbookClient.RouteEra e : route.eras()) {
            int bw = font.width(e.era()) + 8;
            if (in(mx, my, x, y, bw, 12)) return jump(e, null);
            x += bw + 2;
        }
        List<HandbookClient.RouteBranch> bs = pg.era().branches();
        if (bs.size() > 1) {
            int y2 = top + 20, x2 = left + 6;
            int[] widths = chipWidths(pg);
            for (int i = 0; i < bs.size(); i++) {
                if (in(mx, my, x2, y2, widths[i], 11)) return jump(pg.era(), bs.get(i).id());
                x2 += widths[i] + 2;
            }
        }
        for (Map.Entry<String, int[]> e : pos.entrySet()) {
            int[] p = e.getValue();
            if (!in(mx, my, p[0], p[1], NODE, NODE)) continue;
            String id = e.getKey();
            if (entries.contains(id)) return focus(id);         // вход с другой страницы: перейти туда
            if (id.equals(selected)) openInRoute.accept(id);    // второй щелчок — карточка в «Маршруте»
            selected = id;
            return true;
        }
        HandbookClient.RouteTask t = selected == null ? null : route.task(selected);
        if (t != null) {
            Component open = Component.translatable(ContentText.TREE_OPEN);
            int bw = font.width(open) + 10, bx = left + w - 8 - bw, by = graphBottom() + 8;
            if (in(mx, my, bx, by, bw, 12)) {
                openInRoute.accept(t.id());
                return true;
            }
        }
        return false;
    }

    /** Перейти на страницу задания и выделить его. */
    private boolean focus(String id) {
        for (int i = 0; i < pages.size(); i++)
            for (HandbookClient.RouteTask t : pages.get(i).tasks())
                if (t.id().equals(id)) {
                    page = i;
                    selected = id;
                    place();
                    return true;
                }
        return false;
    }

    private boolean jump(HandbookClient.RouteEra era, @Nullable String branch) {
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            if (p.era() == era && (branch == null || p.branch().id().equals(branch)) && p.part() == 0) {
                page = i;
                selected = null;
                place();
                return true;
            }
        }
        return false;
    }

    boolean turn(int d) {
        int next = Math.max(0, Math.min(pages.size() - 1, page + d));
        if (next == page) return false;
        page = next;
        selected = null;
        place();
        return true;
    }
}
