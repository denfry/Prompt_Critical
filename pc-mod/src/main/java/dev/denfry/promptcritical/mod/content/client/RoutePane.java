package dev.denfry.promptcritical.mod.content.client;

import dev.denfry.promptcritical.mod.content.ContentText;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Вкладка «Маршрут» справочника (11 §11.1.5): слева эпохи и задания со статусом, справа карточка задания — что
 * сделать, как (на {@code TRAINING}), цель и ход удержания, рёбра графа «После» и «Ведёт к» (переходы по клику),
 * кнопки «Глава» и Ponder.
 */
final class RoutePane {
    private static final int DONE = 0xFF3C7A3C, OPEN = 0xFF2A5DB0, BAR = 0xFF6E9A4E;
    private static final int ROW_H = HandbookScreen.ROW_H, LINE_H = HandbookScreen.LINE_H;

    private final HandbookClient.Route route;
    private final Font font;
    private final Consumer<String> openEntry;
    private HandbookClient.RouteTask selected;
    private final List<Row> rows = new ArrayList<>();
    private int indexScroll, bodyScroll;
    private int left, top, w, h, indexW;

    /** Строка карточки; {@code link} — задание, к которому ведёт клик; {@code bar} ≥ 0 — полоска хода, доля. */
    private record Row(FormattedCharSequence seq, int indent, int color, @Nullable String link, float bar) {
    }

    RoutePane(HandbookClient.Route route, Font font, Consumer<String> openEntry) {
        this.route = route;
        this.font = font;
        this.openEntry = openEntry;
        selected = firstOpen();
    }

    /** Первое открытое задание (с него игроку идти дальше), иначе первое вообще. */
    @Nullable
    private HandbookClient.RouteTask firstOpen() {
        HandbookClient.RouteTask first = null;
        for (HandbookClient.RouteEra e : route.eras()) {
            for (HandbookClient.RouteTask t : e.tasks()) {
                if (t.status().equals("open")) return t;
                if (first == null) first = t;
            }
        }
        return first;
    }

    void layout(int left, int top, int w, int h, int indexW) {
        this.left = left;
        this.top = top;
        this.w = w;
        this.h = h;
        this.indexW = indexW;
        rebuild();
    }

    private int cx() {
        return left + indexW + 14;
    }

    private int cw() {
        return w - indexW - 26;
    }

    private int indexTop() {
        return top + HandbookScreen.TABS_H;
    }

    private int bodyTop() {
        return top + 24;
    }

    private int bodyBottom() {
        return top + h - 22;
    }

    private void select(HandbookClient.RouteTask t) {
        selected = t;
        bodyScroll = 0;
        rebuild();
    }

    // --- карточка ---

    private void rebuild() {
        rows.clear();
        HandbookClient.RouteTask t = selected;
        if (t == null) return;
        int cw = cw();
        int statusColor = switch (t.status()) {
            case "done" -> DONE;
            case "open" -> OPEN;
            default -> HandbookScreen.MUTED;
        };
        add(Component.translatable(switch (t.status()) {
            case "done" -> ContentText.ROUTE_STATUS_DONE;
            case "open" -> ContentText.ROUTE_STATUS_OPEN;
            default -> ContentText.ROUTE_STATUS_LOCKED;
        }), 0, statusColor, null);
        String goal = t.target() != null ? name(t.target()) : t.targetTag() != null ? "#" + t.targetTag() : null;
        if (goal != null && t.count() > 0) add(Component.translatable(ContentText.ROUTE_GOAL_COUNT, t.count(), goal), 0, HandbookScreen.INK, null);
        else if (goal != null) add(Component.literal(goal), 0, HandbookScreen.INK, null);
        if (t.durationS() > 0) {
            double held = t.status().equals("done") ? t.durationS() : t.heldS();
            add(Component.translatable(ContentText.ROUTE_GOAL_HOLD, minutes(held), minutes(t.durationS())), 0, HandbookScreen.INK, null);
            rows.add(new Row(FormattedCharSequence.EMPTY, 0, 0, null, (float) Mth.clamp(held / t.durationS(), 0, 1)));
        }
        if (!t.text().isEmpty()) {
            blank();
            add(Component.literal(t.text()), 0, HandbookScreen.INK, null);
        }
        if (t.hint() != null) {
            blank();
            add(Component.translatable(ContentText.ROUTE_HOW).withStyle(s -> s.withItalic(true)), 0, HandbookScreen.MUTED, null);
            add(Component.literal(t.hint()), HandbookScreen.BULLET_INDENT, HandbookScreen.INK, null);
        }
        links(ContentText.ROUTE_OPTIONS, t.options());
        links(ContentText.ROUTE_REQUIRES, t.requires());
        List<String> unlocks = new ArrayList<>();
        for (HandbookClient.RouteEra e : route.eras())
            for (HandbookClient.RouteTask o : e.tasks()) if (o.requires().contains(t.id())) unlocks.add(o.id());
        links(ContentText.ROUTE_UNLOCKS, unlocks);
        if (t.xp() > 0) {
            blank();
            add(Component.translatable(ContentText.ROUTE_REWARD, t.xp()), 0, HandbookScreen.MUTED, null);
        }
        bodyScroll = Mth.clamp(bodyScroll, 0, maxBodyScroll());
        if (cw <= 0) rows.clear();
    }

    private void links(String header, List<String> ids) {
        List<HandbookClient.RouteTask> known = new ArrayList<>();
        for (String id : ids) {
            HandbookClient.RouteTask o = route.task(id);
            if (o != null) known.add(o);
        }
        if (known.isEmpty()) return;
        blank();
        add(Component.translatable(header).withStyle(s -> s.withItalic(true)), 0, HandbookScreen.MUTED, null);
        for (HandbookClient.RouteTask o : known)
            add(Component.literal(mark(o) + " " + o.title()), HandbookScreen.BULLET_INDENT, HandbookScreen.INK, o.id());
    }

    private void add(Component text, int indent, int color, @Nullable String link) {
        for (FormattedCharSequence s : font.split(text, Math.max(20, cw() - indent))) rows.add(new Row(s, indent, color, link, -1));
    }

    private void blank() {
        rows.add(new Row(FormattedCharSequence.EMPTY, 0, 0, null, -1));
    }

    private static String minutes(double s) {
        return String.format(Locale.ROOT, "%.1f", s / 60);
    }

    private static String name(String item) {
        ResourceLocation rl = ResourceLocation.tryParse(item);
        if (rl == null) return item;
        ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        return s.isEmpty() ? item : s.getHoverName().getString();
    }

    private static String mark(HandbookClient.RouteTask t) {
        return switch (t.status()) {
            case "done" -> "✔";
            case "open" -> "•";
            default -> "·";
        };
    }

    private int maxBodyScroll() {
        return Math.max(0, rows.size() * LINE_H - (bodyBottom() - bodyTop()));
    }

    // --- отрисовка ---

    void render(GuiGraphics g, int mx, int my, HandbookScreen screen) {
        renderIndex(g, mx, my, screen);
        int cx = cx(), cw = cw();
        if (selected == null) {
            g.drawString(font, Component.translatable(ContentText.ROUTE_EMPTY), cx, top + 9, HandbookScreen.MUTED, false);
            return;
        }
        Component title = Component.literal(selected.title()).withStyle(s -> s.withBold(true));
        screen.drawIcon(g, selected.icon(), cx, top + 8);
        g.drawString(font, font.plainSubstrByWidth(title.getString(), cw - 12), cx + 12, top + 9, HandbookScreen.INK, false);
        g.fill(cx, bodyTop() - 3, cx + cw, bodyTop() - 2, HandbookScreen.PAPER_EDGE);
        g.enableScissor(cx, bodyTop(), cx + cw, bodyBottom());
        int y = bodyTop() - bodyScroll;
        for (Row r : rows) {
            if (y + LINE_H > bodyTop() && y < bodyBottom()) {
                if (r.bar() >= 0) {
                    g.fill(cx, y + 2, cx + cw, y + 7, HandbookScreen.SLOT_EDGE);
                    g.fill(cx + 1, y + 3, cx + 1 + Math.round((cw - 2) * r.bar()), y + 6, BAR);
                } else {
                    boolean over = r.link() != null && my >= y && my < y + LINE_H && mx >= cx && mx < cx + cw;
                    g.drawString(font, r.seq(), cx + r.indent(), y, over ? OPEN : r.color(), false);
                }
            }
            y += LINE_H;
        }
        g.disableScissor();
        int fy = top + h - 16;
        int[] chapter = chapterButton(), ponder = ponderButton();
        if (chapter != null) button(g, chapter, Component.translatable(ContentText.ROUTE_CHAPTER), mx, my);
        if (ponder != null) button(g, ponder, Component.literal("Ponder"), mx, my);
        if (maxBodyScroll() > 0) {
            String more = bodyScroll < maxBodyScroll() ? "▼" : "▲";
            g.drawString(font, more, cx + cw - 6, fy, HandbookScreen.MUTED, false);
        }
    }

    private void button(GuiGraphics g, int[] b, Component label, int mx, int my) {
        boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], HandbookScreen.SLOT_EDGE);
        g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, over ? 0xFFE8DCBC : HandbookScreen.SLOT);
        g.drawString(font, label, b[0] + (b[2] - font.width(label)) / 2, b[1] + 2, over ? OPEN : HandbookScreen.INK, false);
    }

    @Nullable
    private int[] chapterButton() {
        if (selected == null || selected.entry() == null) return null;
        int bw = font.width(Component.translatable(ContentText.ROUTE_CHAPTER)) + 12;
        return new int[] {cx(), top + h - 18, bw, 12};
    }

    @Nullable
    private int[] ponderButton() {
        if (selected == null || selected.ponder() == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(selected.ponder());
        if (rl == null || !PonderIndex.getSceneAccess().doScenesExistForId(rl)) return null;
        int[] c = chapterButton();
        int x = c == null ? cx() : c[0] + c[2] + 4;
        return new int[] {x, top + h - 18, font.width("Ponder") + 12, 12};
    }

    private void renderIndex(GuiGraphics g, int mx, int my, HandbookScreen screen) {
        int x = left + 6, y0 = indexTop(), bottom = top + h - 6;
        g.enableScissor(left, y0, left + indexW, bottom);
        int y = y0 - indexScroll;
        for (HandbookClient.RouteEra e : route.eras()) {
            if (y + ROW_H > y0 && y < bottom) {
                String head = e.era() + " " + e.title();
                String count = e.done() + "/" + e.total();
                g.drawString(font, font.plainSubstrByWidth(head, indexW - 18 - font.width(count)), x, y + 2,
                        HandbookScreen.HEADING, false);
                g.drawString(font, count, left + indexW - 6 - font.width(count), y + 2, HandbookScreen.MUTED, false);
            }
            y += ROW_H + 1;
            for (HandbookClient.RouteTask t : e.tasks()) {
                if (y + ROW_H > y0 && y < bottom) {
                    boolean hovered = mx >= left && mx < left + indexW && my >= y && my < y + ROW_H && my >= y0 && my < bottom;
                    if (t == selected) g.fill(left + 2, y, left + indexW - 2, y + ROW_H, HandbookScreen.SELECTED);
                    int markColor = t.status().equals("done") ? DONE : t.status().equals("open") ? OPEN : HandbookScreen.MUTED;
                    g.drawString(font, mark(t), x + 1, y + 2, markColor, false);
                    screen.drawIcon(g, t.icon(), x + 8, y);
                    int color = hovered ? OPEN : t.status().equals("locked") ? HandbookScreen.MUTED : HandbookScreen.INK;
                    g.drawString(font, font.plainSubstrByWidth(t.title(), indexW - 32), x + 20, y + 2, color, false);
                }
                y += ROW_H;
            }
            y += 4;
        }
        g.disableScissor();
        HandbookClient.RouteTask hovered = taskAt(mx, my);
        if (hovered != null && font.width(hovered.title()) > indexW - 32)
            g.renderTooltip(font, font.split(Component.literal(hovered.title()), 200), mx, my);
    }

    private int indexHeight() {
        int n = 0;
        for (HandbookClient.RouteEra e : route.eras()) n += ROW_H + 1 + 4 + e.tasks().size() * ROW_H;
        return n;
    }

    @Nullable
    private HandbookClient.RouteTask taskAt(double mx, double my) {
        if (mx < left || mx >= left + indexW || my < indexTop() || my >= top + h - 6) return null;
        int y = indexTop() - indexScroll;
        for (HandbookClient.RouteEra e : route.eras()) {
            y += ROW_H + 1;
            for (HandbookClient.RouteTask t : e.tasks()) {
                if (my >= y && my < y + ROW_H) return t;
                y += ROW_H;
            }
            y += 4;
        }
        return null;
    }

    // --- ввод ---

    boolean click(double mx, double my) {
        HandbookClient.RouteTask t = taskAt(mx, my);
        if (t != null) {
            select(t);
            return true;
        }
        int cx = cx(), cw = cw();
        if (mx >= cx && mx < cx + cw && my >= bodyTop() && my < bodyBottom()) {
            int i = (int) Math.floor((my - bodyTop() + bodyScroll) / LINE_H);
            if (i >= 0 && i < rows.size() && rows.get(i).link() != null) {
                HandbookClient.RouteTask o = route.task(rows.get(i).link());
                if (o != null) select(o);
                return true;
            }
        }
        int[] c = chapterButton();
        if (c != null && inside(c, mx, my)) {
            openEntry.accept(selected.entry());
            return true;
        }
        int[] p = ponderButton();
        if (p != null && inside(p, mx, my)) {
            ScreenOpener.transitionTo(PonderUI.of(ResourceLocation.parse(selected.ponder())));
            return true;
        }
        return false;
    }

    private static boolean inside(int[] b, double mx, double my) {
        return mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
    }

    boolean scroll(double mx, double sy) {
        if (mx < left + indexW) {
            indexScroll = Mth.clamp(indexScroll - (int) (sy * ROW_H * 2), 0, Math.max(0, indexHeight() - (top + h - 6 - indexTop())));
        } else {
            bodyScroll = Mth.clamp(bodyScroll - (int) (sy * LINE_H * 2), 0, maxBodyScroll());
        }
        return true;
    }
}
