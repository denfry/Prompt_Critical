package dev.denfry.promptcritical.mod.paperwork.client;

import dev.denfry.promptcritical.mod.paperwork.DocumentLines;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * Окно рукописного документа: лист в линейку с красным полем, текст чернилами, строки чуть «гуляют», как от руки;
 * подписи подчёркнуты. Журнал открывается на последней странице — там свежие записи.
 */
public final class DocumentScreen extends Screen {
    private static final int W = 200, H = 232, MARGIN = 24, TOP = 30, LINE = 11;
    private static final int PAPER = 0xFFF2EAD3, RULE = 0xFFB9CBE0, FIELD = 0xFFD9787A, INK = 0xFF233A80, SIGN = 0xFF14204A;

    private record Row(FormattedCharSequence text, int indent, boolean signature) {
    }

    private final List<Row> rows = new ArrayList<>();
    private final int perPage = (H - TOP - 20) / LINE;
    private int page;

    private DocumentScreen(DocumentLines.Doc doc) {
        super(doc.title());
        Minecraft mc = Minecraft.getInstance();
        for (DocumentLines.Line l : doc.lines()) {
            int indent = l.indent() ? 8 : 0;
            for (FormattedCharSequence s : mc.font.split(l.text(), W - MARGIN - 10 - indent)) rows.add(new Row(s, indent, l.signature()));
        }
        page = Math.max(0, pages() - 1);
    }

    public static void open(ItemStack stack) {
        Minecraft.getInstance().setScreen(new DocumentScreen(DocumentLines.of(stack)));
    }

    private int pages() {
        return Math.max(1, (rows.size() + perPage - 1) / perPage);
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        addRenderableWidget(new PageButton(x + 20, y + H - 18, false, b -> page = Math.max(0, page - 1), true));
        addRenderableWidget(new PageButton(x + W - 43, y + H - 18, true, b -> page = Math.min(pages() - 1, page + 1), true));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, PAPER);
        g.fill(x, y, x + W, y + 1, 0xFF8A7A5A);
        g.fill(x, y + H - 1, x + W, y + H, 0xFF8A7A5A);
        for (int ry = y + TOP + LINE - 2; ry < y + H - 20; ry += LINE) g.fill(x + 4, ry, x + W - 4, ry + 1, RULE);
        g.fill(x + MARGIN - 4, y + 4, x + MARGIN - 3, y + H - 4, FIELD);
        g.drawCenteredString(font, title, x + W / 2, y + 10, SIGN);
        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < rows.size(); i++) {
            Row r = rows.get(from + i);
            // почерк: строка смещена на пиксель-другой, у каждой — своё
            int jitter = Math.floorMod((from + i) * 7919, 3) - 1;
            int tx = x + MARGIN + r.indent() + jitter, ty = y + TOP + i * LINE;
            g.drawString(font, r.text(), tx, ty, r.signature() ? SIGN : INK, false);
            if (r.signature()) g.fill(tx, ty + 9, tx + font.width(r.text()), ty + 10, SIGN);
        }
        g.drawCenteredString(font, Component.literal((page + 1) + " / " + pages()), x + W / 2, y + H - 14, 0xFF6A5A3A);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
