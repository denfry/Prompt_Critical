package dev.denfry.promptcritical.mod.content.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.content.ContentText;
import dev.denfry.promptcritical.mod.content.HandbookMarkdown;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.createmod.catnip.gui.ScreenOpener;
import net.createmod.ponder.foundation.PonderIndex;
import net.createmod.ponder.foundation.ui.PonderUI;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

/**
 * «Справочник инженера» (11 §11.1.2): слева оглавление по разделам (закрытые главы — «???» с намёком), справа страницы
 * главы. Текст переносится и режется на страницы по высоте; ссылки ведут к главам, наведение на предмет показывает
 * его. Рецепт — сетка верстака или вход → выход; мультиблок — вращаемая мышью схема со слоями. Режим документа —
 * одна глава во весь лист (инструкция по эксплуатации). Формула — PNG из сборки с расшифровкой обозначений; Ponder —
 * кнопка «Показать», открывающая сцены предмета. Закладка «Маршрут» — задания по эпохам ({@link RoutePane}).
 */
public class HandbookScreen extends Screen {
    static final int PAPER = 0xFFF3EBD7, PAPER_EDGE = 0xFFB9A57E, COVER = 0xFF1F2F4A, INK = 0xFF2B2B2B,
            MUTED = 0xFF7A715F, SELECTED = 0x332A5DB0, SLOT = 0xFFD9CCAE, SLOT_EDGE = 0xFF8C7B57,
            HEADING = HandbookMarkdown.HEADING_COLOR | 0xFF000000;
    static final int LINE_H = 10, ROW_H = 12, INDEX_W = 116, BULLET_INDENT = 8;
    /** Над оглавлением — закладки «Справочник» и «Маршрут» (11 §11.1.5). */
    static final int TABS_H = 20;

    private final HandbookClient.View view;
    private final Deque<String> history = new ArrayDeque<>();
    private HandbookClient.Entry entry;
    private final List<Page> pages = new ArrayList<>();
    private int page, indexScroll;
    private int left, top, w, h;
    private float rotY = 225, rotX = 30;
    private int layer = -1;
    private RoutePane routePane;
    private boolean routeMode;

    private sealed interface Page permits TextPage, ImagePage, RecipePage, MultiblockPage, FormulaPage, PonderPage {
    }

    private record Line(FormattedCharSequence seq, int indent, boolean bullet) {
    }

    private record TextPage(List<Line> lines) implements Page {
    }

    private record ImagePage(JsonObject json) implements Page {
    }

    private record RecipePage(JsonObject json) implements Page {
    }

    private record MultiblockPage(JsonObject json, List<int[]> pos, List<BlockState> states) implements Page {
    }

    /** Формула: PNG вдвое крупнее листа ({@code w}×{@code h} — его размер в пикселях) и расшифровка. */
    private record FormulaPage(JsonObject json, ResourceLocation texture, int w, int h, List<Line> legend) implements Page {
    }

    private record PonderPage(JsonObject json, ResourceLocation item) implements Page {
    }

    public HandbookScreen(HandbookClient.View view) {
        super(Component.translatable("item.promptcritical.engineering_handbook"));
        this.view = view;
    }

    @Override
    protected void init() {
        w = Math.min(view.document() ? 260 : 380, width - 8);
        h = Math.min(228, height - 8);
        left = (width - w) / 2;
        top = (height - h) / 2;
        String start = view.open();
        if (entry != null) start = entry.id();
        HandbookClient.Entry e = start == null ? null : view.entry(start);
        if (e == null) e = firstUnlocked();
        boolean first = entry == null;
        if (e != null) show(e, false);
        if (first) page = Mth.clamp(view.page(), 0, Math.max(0, pages.size() - 1));
        if (!view.document() && view.route() != null) {
            if (routePane == null) routePane = new RoutePane(view.route(), font, this::openFromRoute);
            routePane.layout(left, top, w, h, INDEX_W);
        }
    }

    private void openFromRoute(String id) {
        HandbookClient.Entry e = view.entry(id);
        if (e == null) return;
        routeMode = false;
        show(e, true);
    }

    private boolean tabs() {
        return routePane != null;
    }

    private int indexTop() {
        return top + (tabs() ? TABS_H : 6);
    }

    /** Закладка {@code i} (0 — справочник, 1 — маршрут): x, y, ширина, высота. */
    private int[] tab(int i) {
        int tw = (INDEX_W - 10) / 2;
        return new int[] {left + 4 + i * (tw + 2), top + 5, tw, 12};
    }

    private void renderTabs(GuiGraphics g, int mx, int my) {
        String[] keys = {ContentText.HANDBOOK_TAB_HANDBOOK, ContentText.HANDBOOK_TAB_ROUTE};
        for (int i = 0; i < 2; i++) {
            int[] b = tab(i);
            boolean active = (i == 1) == routeMode, over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], PAPER_EDGE);
            g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - (active ? 0 : 1), active ? PAPER : SLOT);
            String s = font.plainSubstrByWidth(Component.translatable(keys[i]).getString(), b[2] - 4);
            g.drawString(font, s, b[0] + (b[2] - font.width(s)) / 2, b[1] + 2, active ? INK : over ? 0xFF2A5DB0 : MUTED, false);
        }
    }

    void drawIcon(GuiGraphics g, String id, int x, int y) {
        icon(g, id, x, y);
    }

    private HandbookClient.Entry firstUnlocked() {
        for (HandbookClient.Entry e : view.entries()) if (e.unlocked()) return e;
        return view.entries().isEmpty() ? null : view.entries().getFirst();
    }

    private void show(HandbookClient.Entry e, boolean remember) {
        if (remember && entry != null && entry != e) history.push(entry.id());
        int keep = entry == e ? page : 0;
        entry = e;
        layer = -1;
        rebuild();
        page = Mth.clamp(keep, 0, Math.max(0, pages.size() - 1));
    }

    // --- раскладка ---

    private int contentX() {
        return view.document() ? left + 14 : left + INDEX_W + 14;
    }

    private int contentW() {
        return view.document() ? w - 28 : w - INDEX_W - 26;
    }

    /** У документа над текстом две строки: «Инструкция по эксплуатации» и название. */
    private int bodyTop() {
        return top + (view.document() ? 32 : 24);
    }

    private int bodyH() {
        return top + h - 22 - bodyTop();
    }

    private void rebuild() {
        pages.clear();
        if (entry == null) return;
        int cw = contentW(), perPage = bodyH() / LINE_H;
        List<Line> lines = new ArrayList<>();
        if (!entry.unlocked()) {
            wrap(lines, Component.translatable(ContentText.HANDBOOK_LOCKED, entry.hint()).withStyle(s -> s.withColor(MUTED & 0xFFFFFF)), cw, 0, false);
            pages.add(new TextPage(lines));
            return;
        }
        for (JsonObject p : entry.pages()) {
            String type = GsonHelper.getAsString(p, "type");
            switch (type) {
                case "text" -> {
                    lines = new ArrayList<>();
                    for (HandbookMarkdown.Block b : HandbookMarkdown.parse(GsonHelper.getAsString(p, "text", ""))) {
                        switch (b.kind()) {
                            case BREAK -> lines.add(new Line(FormattedCharSequence.EMPTY, 0, false));
                            case BULLET -> wrap(lines, b.text(), cw - BULLET_INDENT, BULLET_INDENT, true);
                            default -> wrap(lines, b.text(), cw, 0, false);
                        }
                    }
                    paginate(lines, perPage);
                }
                case "image" -> pages.add(new ImagePage(p));
                case "recipe" -> pages.add(new RecipePage(p));
                case "multiblock" -> pages.add(multiblock(p));
                case "formula" -> pages.add(formula(p, cw));
                case "ponder" -> pages.add(new PonderPage(p, ResourceLocation.parse(GsonHelper.getAsString(p, "item"))));
                default -> {
                }
            }
        }
        if (entry.next() != null && !entry.next().isEmpty()) {
            lines = new ArrayList<>();
            wrap(lines, Component.translatable(ContentText.HANDBOOK_NEXT).withStyle(s -> s.withBold(true)
                    .withColor(HandbookMarkdown.HEADING_COLOR)), cw, 0, false);
            for (HandbookMarkdown.Block b : HandbookMarkdown.parse(entry.next())) wrap(lines, b.text(), cw, 0, false);
            paginate(lines, perPage);
        }
        if (pages.isEmpty()) pages.add(new TextPage(List.of()));
    }

    private void wrap(List<Line> out, Component text, int width, int indent, boolean bullet) {
        boolean first = true;
        for (FormattedCharSequence s : font.split(text, Math.max(20, width))) {
            out.add(new Line(s, indent, bullet && first));
            first = false;
        }
    }

    private void paginate(List<Line> lines, int perPage) {
        int i = 0;
        while (i < lines.size()) {
            // пустые строки в начале страницы не нужны
            while (i < lines.size() && lines.get(i).seq() == FormattedCharSequence.EMPTY) i++;
            if (i >= lines.size()) break;
            int end = Math.min(lines.size(), i + perPage);
            pages.add(new TextPage(List.copyOf(lines.subList(i, end))));
            i = end;
        }
    }

    private MultiblockPage multiblock(JsonObject p) {
        List<int[]> pos = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        for (JsonElement b : GsonHelper.getAsJsonArray(p, "blocks")) {
            JsonObject o = b.getAsJsonObject();
            var a = o.getAsJsonArray("pos");
            BlockState st;
            try {
                st = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), GsonHelper.getAsString(o, "state"), false).blockState();
            } catch (Exception ex) {
                st = Blocks.BARRIER.defaultBlockState();
            }
            pos.add(new int[] {a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()});
            states.add(st);
        }
        return new MultiblockPage(p, pos, states);
    }

    private FormulaPage formula(JsonObject p, int cw) {
        ResourceLocation tex = ResourceLocation.parse(GsonHelper.getAsString(p, "texture"));
        int[] size = {0, 0};
        try (InputStream in = minecraft.getResourceManager().open(tex); NativeImage img = NativeImage.read(in)) {
            size[0] = img.getWidth();
            size[1] = img.getHeight();
        } catch (IOException ex) {
            // нет PNG — задача renderHandbookFormulas не запускалась; лист покажет только расшифровку
        }
        List<Line> legend = new ArrayList<>();
        JsonArray vars = GsonHelper.getAsJsonArray(p, "vars", new JsonArray());
        if (!vars.isEmpty()) wrap(legend, Component.translatable(ContentText.HANDBOOK_WHERE).withStyle(st -> st.withItalic(true)), cw, 0, false);
        for (JsonElement v : vars) {
            JsonObject o = v.getAsJsonObject();
            Component line = Component.literal(GsonHelper.getAsString(o, "symbol")).withStyle(st -> st.withBold(true))
                    .append(Component.literal(" — " + GsonHelper.getAsString(o, "text", "")).withStyle(st -> st.withBold(false)));
            wrap(legend, line, cw - BULLET_INDENT, BULLET_INDENT, false);
        }
        return new FormulaPage(p, tex, size[0], size[1], legend);
    }

    // --- отрисовка ---

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        g.fill(left - 4, top - 4, left + w + 4, top + h + 4, COVER);
        g.fill(left, top, left + w, top + h, PAPER);
        if (!view.document()) {
            g.fill(left + INDEX_W, top + 4, left + INDEX_W + 1, top + h - 4, PAPER_EDGE);
            if (tabs()) renderTabs(g, mx, my);
            if (routeMode) {
                routePane.render(g, mx, my, this);
                return;
            }
            renderIndex(g, mx, my);
        }
        if (entry == null) return;
        int cx = contentX(), cw = contentW();
        Component title = Component.literal(entry.title()).withStyle(s -> s.withBold(true));
        if (view.document()) {
            g.drawString(font, Component.translatable(ContentText.HANDBOOK_DOCUMENT), cx, top + 6, MUTED, false);
        }
        g.drawString(font, font.plainSubstrByWidth(title.getString(), cw), cx, top + (view.document() ? 17 : 9), INK, false);
        g.fill(cx, bodyTop() - 3, cx + cw, bodyTop() - 2, PAPER_EDGE);
        Page p = pages.isEmpty() ? null : pages.get(page);
        Style hover = null;
        ItemStack hoverItem = ItemStack.EMPTY;
        switch (p) {
            case TextPage t -> hover = renderText(g, t, cx, mx, my);
            case ImagePage i -> renderImage(g, i, cx, cw, mx, my);
            case RecipePage r -> hoverItem = renderRecipe(g, r, cx, cw, mx, my);
            case MultiblockPage m -> renderMultiblock(g, m, cx, cw, pt);
            case FormulaPage f -> renderFormula(g, f, cx, cw);
            case PonderPage pp -> renderPonder(g, pp, cx, cw, mx, my);
            case null -> {
            }
        }
        // подвал: страницы
        int fy = top + h - 16;
        if (pages.size() > 1) {
            String counter = (page + 1) + " / " + pages.size();
            g.drawString(font, counter, cx + (cw - font.width(counter)) / 2, fy, MUTED, false);
            arrow(g, cx, fy, "<", page > 0, mx, my);
            arrow(g, cx + cw - 8, fy, ">", page < pages.size() - 1, mx, my);
        }
        if (!history.isEmpty()) g.drawString(font, "↩", cx + 14, fy, overArrow(cx + 14, fy, mx, my) ? INK : MUTED, false);
        if (hover != null) g.renderComponentHoverEffect(font, hover, mx, my);
        if (!hoverItem.isEmpty()) g.renderTooltip(font, hoverItem, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
    }

    private void arrow(GuiGraphics g, int x, int y, String s, boolean active, int mx, int my) {
        if (!active) return;
        g.drawString(font, s, x, y, overArrow(x, y, mx, my) ? INK : MUTED, false);
    }

    private static boolean overArrow(int x, int y, double mx, double my) {
        return mx >= x - 2 && mx < x + 9 && my >= y - 2 && my < y + 10;
    }

    private void renderIndex(GuiGraphics g, int mx, int my) {
        int x = left + 6, y0 = indexTop(), bottom = top + h - 6;
        g.enableScissor(left, y0, left + INDEX_W, bottom);
        int y = y0 - indexScroll;
        for (HandbookClient.Category c : view.categories()) {
            if (y + ROW_H > y0 && y < bottom) {
                icon(g, c.icon(), x, y);
                g.drawString(font, font.plainSubstrByWidth(c.title(), INDEX_W - 22), x + 12, y + 2, HEADING, false);
            }
            y += ROW_H + 1;
            for (HandbookClient.Entry e : view.entries()) {
                if (!e.category().equals(c.id())) continue;
                if (y + ROW_H > y0 && y < bottom) {
                    boolean hovered = mx >= left && mx < left + INDEX_W && my >= y && my < y + ROW_H && my >= y0 && my < bottom;
                    if (e == entry) g.fill(left + 2, y, left + INDEX_W - 2, y + ROW_H, SELECTED);
                    if (e.unlocked()) icon(g, e.icon(), x + 6, y);
                    int color = e.unlocked() ? (hovered ? 0xFF2A5DB0 : INK) : MUTED;
                    g.drawString(font, font.plainSubstrByWidth(e.title(), INDEX_W - 30), x + 18, y + 2, color, false);
                }
                y += ROW_H;
            }
            y += 4;
        }
        g.disableScissor();
        // намёк закрытой главы
        HandbookClient.Entry hovered = indexEntryAt(mx, my);
        if (hovered != null && !hovered.unlocked() && !hovered.hint().isEmpty())
            g.renderTooltip(font, font.split(Component.literal(hovered.hint()), 160), mx, my);
        else if (hovered != null && font.width(hovered.title()) > INDEX_W - 30)
            g.renderTooltip(font, font.split(Component.literal(hovered.title()), 200), mx, my);
    }

    private int indexHeight() {
        int n = 0;
        for (HandbookClient.Category c : view.categories()) {
            n += ROW_H + 1 + 4;
            for (HandbookClient.Entry e : view.entries()) if (e.category().equals(c.id())) n += ROW_H;
        }
        return n;
    }

    private HandbookClient.Entry indexEntryAt(double mx, double my) {
        if (view.document() || mx < left || mx >= left + INDEX_W || my < indexTop() || my >= top + h - 6) return null;
        int y = indexTop() - indexScroll;
        for (HandbookClient.Category c : view.categories()) {
            y += ROW_H + 1;
            for (HandbookClient.Entry e : view.entries()) {
                if (!e.category().equals(c.id())) continue;
                if (my >= y && my < y + ROW_H) return e;
                y += ROW_H;
            }
            y += 4;
        }
        return null;
    }

    private void icon(GuiGraphics g, String id, int x, int y) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return;
        ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(rl));
        if (s.isEmpty()) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 0);
        ps.scale(0.625f, 0.625f, 1);
        g.renderItem(s, 0, 0);
        ps.popPose();
    }

    private Style renderText(GuiGraphics g, TextPage t, int cx, int mx, int my) {
        int y = bodyTop();
        Style hover = null;
        for (Line l : t.lines()) {
            if (l.bullet()) g.drawString(font, "•", cx + 1, y, INK, false);
            g.drawString(font, l.seq(), cx + l.indent(), y, INK, false);
            if (my >= y && my < y + LINE_H && mx >= cx + l.indent()) {
                Style s = font.getSplitter().componentStyleAtWidth(l.seq(), mx - cx - l.indent());
                if (s != null && s.getHoverEvent() != null) hover = s;
            }
            y += LINE_H;
        }
        return hover;
    }

    private Style styleAt(double mx, double my) {
        if (pages.isEmpty() || !(pages.get(page) instanceof TextPage t)) return null;
        int cx = contentX(), y = bodyTop();
        for (Line l : t.lines()) {
            if (my >= y && my < y + LINE_H && mx >= cx + l.indent())
                return font.getSplitter().componentStyleAtWidth(l.seq(), (int) mx - cx - l.indent());
            y += LINE_H;
        }
        return null;
    }

    /** Прямоугольник картинки на листе: x, y, ширина, высота. */
    private int[] imageRect(JsonObject j, int cx, int cw) {
        int tw = GsonHelper.getAsInt(j, "width"), th = GsonHelper.getAsInt(j, "height");
        int maxH = bodyH() - 24;
        float k = Math.min(1f, Math.min(cw / (float) tw, maxH / (float) th));
        int dw = Math.round(tw * k), dh = Math.round(th * k);
        return new int[] {cx + (cw - dw) / 2, bodyTop(), dw, dh};
    }

    private void renderImage(GuiGraphics g, ImagePage i, int cx, int cw, int mx, int my) {
        JsonObject j = i.json();
        int tw = GsonHelper.getAsInt(j, "width"), th = GsonHelper.getAsInt(j, "height");
        int[] r = imageRect(j, cx, cw);
        g.blit(ResourceLocation.parse(GsonHelper.getAsString(j, "texture")), r[0], r[1], r[2], r[3], 0, 0, tw, th, tw, th);
        float k = r[2] / (float) tw;
        for (JsonElement z : j.getAsJsonArray("zones")) {
            JsonObject o = z.getAsJsonObject();
            int x0 = r[0] + Math.round(GsonHelper.getAsInt(o, "x") * k), y0 = r[1] + Math.round(GsonHelper.getAsInt(o, "y") * k);
            int x1 = x0 + Math.round(GsonHelper.getAsInt(o, "w") * k), y1 = y0 + Math.round(GsonHelper.getAsInt(o, "h") * k);
            if (mx >= x0 && mx < x1 && my >= y0 && my < y1) g.fill(x0, y0, x1, y1, SELECTED);
        }
        caption(g, GsonHelper.getAsString(j, "caption", ""), cx, cw, r[1] + r[3] + 4);
    }

    private void caption(GuiGraphics g, String text, int cx, int cw, int y) {
        if (text.isEmpty()) return;
        for (FormattedCharSequence s : font.split(Component.literal(text).withStyle(st -> st.withItalic(true)), cw)) {
            if (y > top + h - 26) break;
            g.drawString(font, s, cx, y, MUTED, false);
            y += LINE_H;
        }
    }

    private ItemStack renderRecipe(GuiGraphics g, RecipePage rp, int cx, int cw, int mx, int my) {
        JsonObject j = rp.json();
        String id = GsonHelper.getAsString(j, "recipe");
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Optional<RecipeHolder<?>> holder = minecraft == null || minecraft.level == null || rl == null ? Optional.empty()
                : minecraft.level.getRecipeManager().byKey(rl);
        if (holder.isEmpty()) {
            g.drawString(font, Component.translatable(ContentText.HANDBOOK_NO_RECIPE, id), cx, bodyTop(), MUTED, false);
            return ItemStack.EMPTY;
        }
        Recipe<?> r = holder.get().value();
        List<Ingredient> ing = r.getIngredients();
        int gw, gh;
        if (r instanceof ShapedRecipe s) {
            gw = s.getWidth();
            gh = s.getHeight();
        } else {
            int n = Math.max(1, ing.size());
            gw = r instanceof ShapelessRecipe ? (n <= 1 ? 1 : n <= 4 ? 2 : 3) : Math.min(3, n);
            gh = (n + gw - 1) / gw;
        }
        int gridW = gw * 18, total = gridW + 26 + 18;
        int x0 = cx + (cw - total) / 2, y0 = bodyTop() + 6;
        long t = System.currentTimeMillis() / 1000;
        ItemStack hovered = ItemStack.EMPTY;
        for (int k = 0; k < ing.size(); k++) {
            int sx = x0 + (k % gw) * 18, sy = y0 + (k / gw) * 18;
            slot(g, sx, sy);
            ItemStack[] items = ing.get(k).getItems();
            if (items.length == 0) continue;
            ItemStack s = items[(int) (t % items.length)];
            g.renderItem(s, sx + 1, sy + 1);
            if (mx >= sx && mx < sx + 18 && my >= sy && my < sy + 18) hovered = s;
        }
        int ry = y0 + (gh * 18 - 18) / 2, rx = x0 + gridW + 26;
        g.drawString(font, "→", x0 + gridW + 9, ry + 5, INK, false);
        slot(g, rx, ry);
        ItemStack out = r.getResultItem(minecraft.level.registryAccess());
        g.renderItem(out, rx + 1, ry + 1);
        g.renderItemDecorations(font, out, rx + 1, ry + 1);
        if (mx >= rx && mx < rx + 18 && my >= ry && my < ry + 18) hovered = out;
        g.drawString(font, out.getHoverName(), cx + (cw - font.width(out.getHoverName())) / 2, y0 + gh * 18 + 8, INK, false);
        caption(g, GsonHelper.getAsString(j, "caption", ""), cx, cw, y0 + gh * 18 + 22);
        return hovered;
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    private int[] layers(MultiblockPage m) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int[] p : m.pos()) {
            lo = Math.min(lo, p[1]);
            hi = Math.max(hi, p[1]);
        }
        return new int[] {lo, hi};
    }

    private void renderMultiblock(GuiGraphics g, MultiblockPage m, int cx, int cw, float pt) {
        JsonObject j = m.json();
        g.drawString(font, GsonHelper.getAsString(j, "name", ""), cx, bodyTop(), INK, false);
        int[] ly = layers(m);
        if (ly[1] > ly[0]) {
            Component lbl = layer < 0 ? Component.translatable(ContentText.HANDBOOK_ALL_LAYERS)
                    : Component.translatable(ContentText.HANDBOOK_LAYER, layer - ly[0] + 1, ly[1] - ly[0] + 1);
            g.drawString(font, lbl, cx + cw - font.width(lbl), bodyTop(), 0xFF2A5DB0, false);
        }
        int areaTop = bodyTop() + 12, areaH = bodyH() - 44;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int[] p : m.pos()) {
            minX = Math.min(minX, p[0]);
            minY = Math.min(minY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxX = Math.max(maxX, p[0] + 1);
            maxY = Math.max(maxY, p[1] + 1);
            maxZ = Math.max(maxZ, p[2] + 1);
        }
        // модели сегментов выходят на клетку за якорь
        float size = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) + 2;
        float scale = Math.min(cw, areaH) / (size * 1.2f);
        g.flush();
        g.enableScissor(cx, areaTop, cx + cw, areaTop + areaH);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx + cw / 2f, areaTop + areaH / 2f, 400);
        ps.scale(scale, -scale, scale);
        ps.mulPose(Axis.XP.rotationDegrees(rotX));
        ps.mulPose(Axis.YP.rotationDegrees(rotY));
        ps.translate(-(minX + maxX) / 2, -(minY + maxY) / 2, -(minZ + maxZ) / 2);
        Lighting.setupForEntityInInventory();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        MultiBufferSource.BufferSource buf = g.bufferSource();
        for (int k = 0; k < m.pos().size(); k++) {
            int[] p = m.pos().get(k);
            if (layer >= 0 && p[1] != layer) continue;
            ps.pushPose();
            ps.translate(p[0], p[1], p[2]);
            dispatcher.renderSingleBlock(m.states().get(k), ps, buf, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        buf.endBatch();
        ps.popPose();
        Lighting.setupFor3DItems();
        g.disableScissor();
        caption(g, GsonHelper.getAsString(j, "caption", ""), cx, cw, areaTop + areaH + 2);
    }

    private void renderFormula(GuiGraphics g, FormulaPage f, int cx, int cw) {
        int y = bodyTop() + 4;
        if (f.w() > 0) {
            // PNG нарисован вдвое крупнее: на листе — половина, широкая формула ужимается по ширине
            float k = Math.min(0.5f, cw / (float) f.w());
            int dw = Math.round(f.w() * k), dh = Math.round(f.h() * k);
            g.blit(f.texture(), cx + (cw - dw) / 2, y, dw, dh, 0, 0, f.w(), f.h(), f.w(), f.h());
            y += dh + 8;
        }
        for (Line l : f.legend()) {
            if (y > top + h - 26) break;
            g.drawString(font, l.seq(), cx + l.indent(), y, INK, false);
            y += LINE_H;
        }
        caption(g, GsonHelper.getAsString(f.json(), "caption", ""), cx, cw, y + 4);
    }

    /** Прямоугольник кнопки «Показать»: x, y, ширина, высота. */
    private int[] ponderButton(int cx, int cw) {
        int bw = Math.min(cw, font.width(Component.translatable(ContentText.HANDBOOK_PONDER_SHOW)) + 16);
        return new int[] {cx + (cw - bw) / 2, bodyTop() + 64, bw, 16};
    }

    private void renderPonder(GuiGraphics g, PonderPage pp, int cx, int cw, int mx, int my) {
        ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(pp.item()));
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(cx + cw / 2f - 16, bodyTop() + 4, 0);
        ps.scale(2, 2, 1);
        g.renderItem(s, 0, 0);
        ps.popPose();
        Component name = s.getHoverName();
        g.drawString(font, name, cx + (cw - font.width(name)) / 2, bodyTop() + 42, INK, false);
        int[] b = ponderButton(cx, cw);
        if (PonderIndex.getSceneAccess().doScenesExistForId(pp.item())) {
            boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], SLOT_EDGE);
            g.fill(b[0] + 1, b[1] + 1, b[0] + b[2] - 1, b[1] + b[3] - 1, over ? 0xFFE8DCBC : SLOT);
            Component lbl = Component.translatable(ContentText.HANDBOOK_PONDER_SHOW);
            g.drawString(font, lbl, b[0] + (b[2] - font.width(lbl)) / 2, b[1] + 4, over ? 0xFF2A5DB0 : INK, false);
        } else {
            Component none = Component.translatable(ContentText.HANDBOOK_PONDER_NONE);
            g.drawString(font, none, cx + (cw - font.width(none)) / 2, b[1] + 4, MUTED, false);
        }
        caption(g, GsonHelper.getAsString(pp.json(), "caption", ""), cx, cw, b[1] + b[3] + 8);
    }

    // --- ввод ---

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (tabs() && button == 0) {
            for (int i = 0; i < 2; i++) {
                int[] b = tab(i);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    routeMode = i == 1;
                    return true;
                }
            }
            if (routeMode) return routePane.click(mx, my) || super.mouseClicked(mx, my, button);
        }
        if (routeMode) return super.mouseClicked(mx, my, button);
        if (entry != null && button == 0) {
            int cx = contentX(), cw = contentW(), fy = top + h - 16;
            if (pages.size() > 1 && page > 0 && overArrow(cx, fy, mx, my)) return turn(-1);
            if (pages.size() > 1 && page < pages.size() - 1 && overArrow(cx + cw - 8, fy, mx, my)) return turn(1);
            if (!history.isEmpty() && overArrow(cx + 14, fy, mx, my)) return back();
            HandbookClient.Entry e = indexEntryAt(mx, my);
            if (e != null) {
                show(e, true);
                return true;
            }
            Style s = styleAt(mx, my);
            if (s != null && s.getClickEvent() != null && s.getClickEvent().getAction() == ClickEvent.Action.CHANGE_PAGE)
                return follow(s.getClickEvent().getValue());
            Page p = pages.isEmpty() ? null : pages.get(page);
            if (p instanceof ImagePage i && zoneClick(i.json(), cx, cw, mx, my)) return true;
            if (p instanceof PonderPage pp && PonderIndex.getSceneAccess().doScenesExistForId(pp.item())) {
                int[] b = ponderButton(cx, cw);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    ScreenOpener.transitionTo(PonderUI.of(pp.item()));
                    return true;
                }
            }
            if (p instanceof MultiblockPage m) {
                int[] ly = layers(m);
                Component lbl = Component.translatable(ContentText.HANDBOOK_ALL_LAYERS);
                if (ly[1] > ly[0] && my >= bodyTop() - 1 && my < bodyTop() + 9 && mx >= cx + cw - Math.max(font.width(lbl), 60)) {
                    layer = layer < 0 ? ly[0] : layer >= ly[1] ? -1 : layer + 1;
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean zoneClick(JsonObject j, int cx, int cw, double mx, double my) {
        int[] r = imageRect(j, cx, cw);
        float k = r[2] / (float) GsonHelper.getAsInt(j, "width");
        for (JsonElement z : j.getAsJsonArray("zones")) {
            JsonObject o = z.getAsJsonObject();
            int x0 = r[0] + Math.round(GsonHelper.getAsInt(o, "x") * k), y0 = r[1] + Math.round(GsonHelper.getAsInt(o, "y") * k);
            if (mx >= x0 && mx < x0 + GsonHelper.getAsInt(o, "w") * k && my >= y0 && my < y0 + GsonHelper.getAsInt(o, "h") * k)
                return follow(HandbookMarkdown.ENTRY_LINK + GsonHelper.getAsString(o, "entry"));
        }
        return false;
    }

    private boolean follow(String value) {
        if (!value.startsWith(HandbookMarkdown.ENTRY_LINK)) return false;
        HandbookClient.Entry e = view.entry(value.substring(HandbookMarkdown.ENTRY_LINK.length()));
        if (e == null || view.document()) return false;
        show(e, true);
        return true;
    }

    private boolean back() {
        HandbookClient.Entry e = view.entry(history.pop());
        if (e != null) show(e, false);
        return true;
    }

    private boolean turn(int d) {
        page = Mth.clamp(page + d, 0, Math.max(0, pages.size() - 1));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (!routeMode && !pages.isEmpty() && pages.get(page) instanceof MultiblockPage && mx >= contentX()) {
            rotY += (float) dx * 2;
            rotX = Mth.clamp(rotX + (float) dy * 2, -90, 90);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (routeMode) return routePane.scroll(mx, sy);
        if (!view.document() && mx < left + INDEX_W) {
            indexScroll = Mth.clamp(indexScroll - (int) (sy * ROW_H * 2), 0, Math.max(0, indexHeight() - (top + h - 6 - indexTop())));
            return true;
        }
        return turn(sy > 0 ? -1 : 1);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (routeMode) return super.keyPressed(key, scan, mods);
        if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_PAGE_UP) return turn(-1);
        if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_PAGE_DOWN) return turn(1);
        if (key == GLFW.GLFW_KEY_BACKSPACE && !history.isEmpty()) return back();
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
