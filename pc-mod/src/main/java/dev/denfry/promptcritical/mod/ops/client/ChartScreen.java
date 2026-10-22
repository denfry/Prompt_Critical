package dev.denfry.promptcritical.mod.ops.client;

import dev.denfry.promptcritical.mod.ops.OpsComponents;
import dev.denfry.promptcritical.mod.ops.OpsText;
import dev.denfry.promptcritical.mod.panel.client.PanelArt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Лента самописца на экране (13 §13.6.7): сетка шкалы и кривая точек по кругу барабана, начиная с самой старой
 * (за пером). Пустые точки — разрыв кривой.
 */
public final class ChartScreen extends Screen {
    private static final int PAPER = 0xFFF3EBD7, GRID = 0xFFD8C9A8, GRID_MAJOR = 0xFFB9A57E, INK = 0xFF7A1F1F, TEXT = 0xFF2B2B2B;
    private static final int W = 300, H = 160, PAD = 24;
    private final OpsComponents.ChartTrace trace;

    private ChartScreen(OpsComponents.ChartTrace trace) {
        super(OpsText.msg("quantity." + trace.quantity()));
        this.trace = trace;
    }

    public static void open(OpsComponents.ChartTrace trace) {
        Minecraft.getInstance().setScreen(new ChartScreen(trace));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int w = Math.min(W, width - 8), h = Math.min(H, height - 8);
        int left = (width - w) / 2, top = (height - h) / 2;
        g.fill(left, top, left + w, top + h, PAPER);
        int x0 = left + PAD, y0 = top + 18, x1 = left + w - 6, y1 = top + h - 14;
        for (int i = 0; i <= 10; i++) {
            int y = y1 - (y1 - y0) * i / 10;
            g.fill(x0, y, x1, y + 1, i % 5 == 0 ? GRID_MAJOR : GRID);
        }
        for (int i = 0; i <= 12; i++) {
            int x = x0 + (x1 - x0) * i / 12;
            g.fill(x, y0, x + 1, y1, i % 3 == 0 ? GRID_MAJOR : GRID);
        }
        g.drawString(font, title, left + 6, top + 3, TEXT, false);
        // шкала слева от сетки, по правому краю у её начала
        for (double[] mark : new double[][] {{trace.max(), y0}, {trace.min(), y1}}) {
            String v = OpsText.num(mark[0], 0);
            PanelArt.text(g, font, Component.literal(v), Math.max(left + 2, x0 - 3 - font.width(v)), (int) mark[1] - 4, PAD - 5, TEXT);
        }
        int n = trace.points().size();
        if (n < 2) return;
        double prevX = Double.NaN, prevY = Double.NaN;
        for (int k = 0; k < n; k++) {
            int i = Math.floorMod(trace.position() + k, n);
            double v = trace.value(i);
            if (Double.isNaN(v)) {
                prevX = Double.NaN;
                continue;
            }
            double f = Math.max(0, Math.min(1, (v - trace.min()) / (trace.max() - trace.min())));
            double x = x0 + (x1 - x0) * (double) k / (n - 1), y = y1 - (y1 - y0) * f;
            if (!Double.isNaN(prevX)) line(g, prevX, prevY, x, y);
            else g.fill((int) x, (int) y, (int) x + 1, (int) y + 1, INK);
            prevX = x;
            prevY = y;
        }
        Component span = Component.literal(trace.sampleS() * n / 3600 + " h");
        g.drawString(font, span, x1 - font.width(span), y1 + 3, TEXT, false);
    }

    private static void line(GuiGraphics g, double ax, double ay, double bx, double by) {
        int steps = (int) Math.max(1, Math.max(Math.abs(bx - ax), Math.abs(by - ay)));
        for (int s = 0; s <= steps; s++) {
            int x = (int) Math.round(ax + (bx - ax) * s / steps), y = (int) Math.round(ay + (by - ay) * s / steps);
            g.fill(x, y, x + 1, y + 1, INK);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
