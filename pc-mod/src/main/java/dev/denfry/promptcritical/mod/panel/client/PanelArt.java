package dev.denfry.promptcritical.mod.panel.client;

import com.mojang.math.Axis;
import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Рисование местного щита из спрайтов {@code textures/gui/sprites/panel/} ({@code art/tools/gui_panel.py}): крашеная
 * панель, латунная табличка, стрелочные приборы, стекло, шкалы, лампы, окна табло, слоты. Общее для всех экранов
 * машин, включая панель подъёмной машины. Только клиент.
 */
public final class PanelArt {
    public static final int PAINT = 0xFFE8E3D0, PAINT_SHADOW = 0xFF27302C, PLATE_INK = 0xFF2A1E08, WINDOW_TEXT = 0xFFE8DFC4,
            DIAL_INK = 0xFF1C1C1C, RED_ZONE = 0xFFC0281E, WATER = 0xFF3F78B8;
    private static final ResourceLocation BACKGROUND = sprite("background"), INSET = sprite("inset"), PLATE = sprite("plate"),
            DIAL_LARGE = sprite("dial_large"), DIAL_MEDIUM = sprite("dial_medium"), HUB = sprite("hub"), GLASS = sprite("glass"),
            SLOT = sprite("slot"), SLOT_OUTPUT = sprite("slot_output"), TRACK = sprite("track"), WINDOW = sprite("window");

    private PanelArt() {
    }

    public static ResourceLocation sprite(String name) {
        return PromptCritical.id("panel/" + name);
    }

    public static void background(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(BACKGROUND, x, y, w, h);
    }

    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.blitSprite(INSET, x, y, w, h);
    }

    /** Латунная табличка с гравировкой по центру {@code cx}. */
    public static void plate(GuiGraphics g, Font font, Component text, int cx, int y) {
        int w = font.width(text) + 16;
        g.blitSprite(PLATE, cx - w / 2, y, w, 14);
        g.drawString(font, text, cx - font.width(text) / 2, y + 3, PLATE_INK, false);
    }

    /** Надпись краской по трафарету. */
    public static void paint(GuiGraphics g, Font font, Component text, int x, int y) {
        g.drawString(font, text, x + 1, y + 1, PAINT_SHADOW, false);
        g.drawString(font, text, x, y, PAINT, false);
    }

    public static void paintCentered(GuiGraphics g, Font font, Component text, int cx, int y) {
        paint(g, font, text, cx - font.width(text) / 2, y);
    }

    /** Фон слота; {@code x, y} — угол предмета. */
    public static void slot(GuiGraphics g, int x, int y, boolean output) {
        g.blitSprite(output ? SLOT_OUTPUT : SLOT, x - 1, y - 1, 18, 18);
    }

    /** Окно табло со строкой; длинная строка сжимается по ширине. */
    public static void window(GuiGraphics g, Font font, Component text, int x, int y, int w) {
        g.blitSprite(WINDOW, x, y, w, 13);
        int tw = font.width(text), room = w - 7;
        if (tw <= room) {
            g.drawString(font, text, x + 4, y + 3, WINDOW_TEXT, false);
            return;
        }
        float k = (float) room / tw;
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 3 + (1 - k) * 4, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, WINDOW_TEXT, false);
        g.pose().popPose();
    }

    /** Стрелочный прибор; {@code shown} — показание стрелки в единицах шкалы. */
    public static void dial(GuiGraphics g, Font font, PanelLayout.Dial d, double shown, int x, int y) {
        int size = d.size();
        g.blitSprite(d.large() ? DIAL_LARGE : DIAL_MEDIUM, x, y, size, size);
        float cx = x + size / 2f, cy = y + size / 2f;
        float arcR = d.large() ? 27.4f : 19.2f;
        if (!Double.isNaN(d.redFrom())) {
            double f0 = Math.max(0, Math.min(1, d.redFrom() / d.max()));
            for (double f = f0; f <= 1.0001; f += 0.012) {
                g.pose().pushPose();
                g.pose().translate(cx, cy, 0);
                g.pose().mulPose(Axis.ZP.rotationDegrees((float) (-135 + 270 * f)));
                g.fill(-1, (int) -arcR + 1, 1, (int) -arcR + (d.large() ? 4 : 3), RED_ZONE);
                g.pose().popPose();
            }
        }
        double[] marks = d.large() ? new double[] {0, 0.25, 0.5, 0.75, 1} : new double[] {0, 0.5, 1};
        float labelR = d.large() ? 15.5f : 10.5f, k = d.large() ? 1f : 0.75f;
        for (double f : marks) {
            String s = tick(f * d.max());
            double a = Math.toRadians(-135 + 270 * f);
            float lx = cx + labelR * (float) Math.sin(a), ly = cy - labelR * (float) Math.cos(a);
            g.pose().pushPose();
            g.pose().translate(lx, ly, 0);
            g.pose().scale(k, k, 1);
            g.drawString(font, s, -font.width(s) / 2, -4, DIAL_INK, false);
            g.pose().popPose();
        }
        g.pose().pushPose();
        g.pose().translate(cx, cy + (d.large() ? 13 : 9), 0);
        g.pose().scale(0.75f, 0.75f, 1);
        g.drawString(font, d.unit(), -font.width(d.unit()) / 2, -4, 0xFF4A4436, false);
        g.pose().popPose();
        double f = Math.max(-0.03, Math.min(1.04, shown / d.max()));
        int len = (int) arcR - 2;
        for (int pass = 0; pass < 2; pass++) {
            g.pose().pushPose();
            g.pose().translate(cx + (pass == 0 ? 1 : 0), cy + (pass == 0 ? 1 : 0), 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees((float) (-135 + 270 * f)));
            int c = pass == 0 ? 0x40000000 : DIAL_INK;
            g.fill(-1, -len + 3, 1, 4, c);
            g.fill(0, -len, 1, -len + 3, c);
            g.pose().popPose();
        }
        g.blitSprite(HUB, (int) cx - 3, (int) cy - 3, 6, 6);
        paintCentered(g, font, d.label(), (int) cx, y + size + 2);
    }

    /** Синхроноскоп: циферблат без шкалы, риска «12 часов», надписи «быстрее»/«медленнее», стрелка на полный оборот. */
    public static void scope(GuiGraphics g, Font font, PanelLayout.Scope s, int raw, int x, int y) {
        int size = PanelLayout.Scope.SIZE;
        g.blitSprite(DIAL_LARGE, x, y, size, size);
        float cx = x + size / 2f, cy = y + size / 2f;
        g.fill((int) cx - 1, (int) cy - 27, (int) cx + 1, (int) cy - 20, RED_ZONE);
        g.pose().pushPose();
        g.pose().translate(cx, cy + 12, 0);
        g.pose().scale(0.6f, 0.6f, 1);
        g.drawString(font, "SLOW", -36, -4, DIAL_INK, false);
        g.drawString(font, "FAST", 14, -4, DIAL_INK, false);
        g.pose().popPose();
        boolean live = raw != PanelLayout.Scope.OFF;
        float deg = live ? raw / 10f : 180;
        for (int pass = 0; pass < 2; pass++) {
            g.pose().pushPose();
            g.pose().translate(cx + (pass == 0 ? 1 : 0), cy + (pass == 0 ? 1 : 0), 0);
            g.pose().mulPose(Axis.ZP.rotationDegrees(deg));
            int c = pass == 0 ? 0x40000000 : live ? DIAL_INK : 0xFF8A8270;
            g.fill(-1, -24, 1, 6, c);
            g.fill(-2, -24, 2, -20, c);
            g.pose().popPose();
        }
        g.blitSprite(HUB, (int) cx - 3, (int) cy - 3, 6, 6);
        paintCentered(g, font, s.label(), (int) cx, y + size + 2);
    }

    private static String tick(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Водомерное стекло: заполнение долей {@code frac}, красная метка низшего уровня и метка нормального. */
    public static void glass(GuiGraphics g, Font font, PanelLayout.Glass e, double frac, int x, int y) {
        int h = e.h();
        g.blitSprite(GLASS, x, y, 12, h);
        int top = y + 5, bottom = y + h - 5, inner = bottom - top;
        int level = bottom - (int) Math.round(Math.max(0, Math.min(1, frac)) * inner);
        if (level < bottom) {
            g.fillGradient(x + 3, level, x + 9, bottom, lighten(e.color(), 0.25f), e.color());
            g.fill(x + 3, level, x + 9, level + 1, lighten(e.color(), 0.6f));
        }
        g.fill(x + 4, top, x + 5, bottom, 0x30FFFFFF);
        if (!Double.isNaN(e.lowMark())) {
            int my = bottom - (int) Math.round(e.lowMark() * inner);
            g.fill(x - 2, my, x + 14, my + 1, RED_ZONE);
        }
        if (!Double.isNaN(e.normalMark())) {
            int my = bottom - (int) Math.round(e.normalMark() * inner);
            g.fill(x - 2, my, x + 1, my + 1, PAINT);
            g.fill(x + 11, my, x + 14, my + 1, PAINT);
        }
        paintCentered(g, font, e.label(), x + 6, y + h + 2);
    }

    /** Горизонтальная шкала с подписью над ней. */
    public static void bar(GuiGraphics g, Font font, PanelLayout.Bar e, double frac, int x, int y) {
        paint(g, font, e.label(), x, y - 10);
        g.blitSprite(TRACK, x, y, e.w(), 8);
        int fill = (int) Math.round(Math.max(0, Math.min(1, frac)) * (e.w() - 2));
        if (fill > 0) {
            g.fill(x + 1, y + 1, x + 1 + fill, y + 7, e.color());
            g.fill(x + 1, y + 1, x + 1 + fill, y + 2, lighten(e.color(), 0.45f));
            g.fill(x + 1, y + 6, x + 1 + fill, y + 7, darken(e.color(), 0.6f));
        }
        for (int i = 1; i < 4; i++) {
            int tx = x + 1 + (e.w() - 2) * i / 4;
            g.fill(tx, y + 1, tx + 1, y + 3, 0x80000000);
        }
    }

    public static void lamp(GuiGraphics g, Font font, PanelLayout.Lamp e, boolean on, int x, int y) {
        String color = e.color().name().toLowerCase(java.util.Locale.ROOT);
        g.blitSprite(sprite("lamp_" + color + (on ? "_on" : "_off")), x, y, 14, 14);
        paint(g, font, e.label(), x + 16, y + 3);
    }

    public static int lighten(int argb, float k) {
        int r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        return argb & 0xFF000000 | (int) (r + (255 - r) * k) << 16 | (int) (gr + (255 - gr) * k) << 8 | (int) (b + (255 - b) * k);
    }

    public static int darken(int argb, float k) {
        int r = argb >> 16 & 255, gr = argb >> 8 & 255, b = argb & 255;
        return argb & 0xFF000000 | (int) (r * k) << 16 | (int) (gr * k) << 8 | (int) (b * k);
    }
}
