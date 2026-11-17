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
        plate(g, font, text, cx, y, font.width(text));
    }

    /** Табличка, гравировка сжата до {@code room}. */
    public static void plate(GuiGraphics g, Font font, Component text, int cx, int y, int room) {
        int tw = Math.min(font.width(text), room), w = tw + 16;
        g.blitSprite(PLATE, cx - w / 2, y, w, 14);
        text(g, font, text, cx - tw / 2, y + 3, room, PLATE_INK);
    }

    /**
     * Строка без тени, сжатая по ширине до {@code room} (если не помещается) с сохранением средней линии строки.
     */
    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int room, int color) {
        int tw = font.width(text);
        if (tw <= room || tw == 0) {
            g.drawString(font, text, x, y, color, false);
            return;
        }
        float k = Math.max(0.01f, (float) room / tw);
        g.pose().pushPose();
        g.pose().translate(x, y + (1 - k) * 4, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Надпись краской по трафарету. */
    public static void paint(GuiGraphics g, Font font, Component text, int x, int y) {
        paint(g, font, text, x, y, font.width(text));
    }

    /** Надпись краской, сжатая до {@code room}. */
    public static void paint(GuiGraphics g, Font font, Component text, int x, int y, int room) {
        text(g, font, text, x + 1, y + 1, room, PAINT_SHADOW);
        text(g, font, text, x, y, room, PAINT);
    }

    public static void paintCentered(GuiGraphics g, Font font, Component text, int cx, int y) {
        paintCentered(g, font, text, cx, y, font.width(text));
    }

    public static void paintCentered(GuiGraphics g, Font font, Component text, int cx, int y, int room) {
        paint(g, font, text, cx - Math.min(room, font.width(text)) / 2, y, room);
    }

    /** Фон слота; {@code x, y} — угол предмета. */
    public static void slot(GuiGraphics g, int x, int y, boolean output) {
        g.blitSprite(output ? SLOT_OUTPUT : SLOT, x - 1, y - 1, 18, 18);
    }

    /** Окно табло со строкой; длинная строка сжимается по ширине. */
    public static void window(GuiGraphics g, Font font, Component text, int x, int y, int w) {
        g.blitSprite(WINDOW, x, y, w, 13);
        text(g, font, text, x + 4, y + 3, w - 7, WINDOW_TEXT);
    }

    /** Стрелочный прибор; {@code shown} — показание стрелки в единицах шкалы. */
    public static void dial(GuiGraphics g, Font font, PanelLayout.Dial d, double shown, int x, int y, int room) {
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
        // оцифровка внутри шкалы: у малого прибора — начало, середина и конец; единица — в просвете шкалы под осью
        double[] marks = d.large() ? new double[] {0, 0.25, 0.5, 0.75, 1} : new double[] {0, 0.5, 1};
        float labelR = d.large() ? 18f : 12.5f, k = d.large() ? 0.75f : 0.6f, face = d.large() ? 27f : 19f;
        for (double f : marks) {
            String s = tick(f * d.max());
            double a = Math.toRadians(-135 + 270 * f);
            float lx = labelR * (float) Math.sin(a), ly = -labelR * (float) Math.cos(a);
            float half = font.width(s) * k / 2, edge = Math.abs(ly) + 4 * k;
            float chord = (float) Math.sqrt(Math.max(0, face * face - edge * edge)) - 1;
            lx = Math.max(-chord + half, Math.min(chord - half, lx));
            g.pose().pushPose();
            g.pose().translate(cx + lx, cy + ly, 0);
            g.pose().scale(k, k, 1);
            g.drawString(font, s, -font.width(s) / 2, -4, DIAL_INK, false);
            g.pose().popPose();
        }
        g.pose().pushPose();
        float uy = d.large() ? 21f : 15f, uk = d.large() ? 0.7f : 0.6f;
        g.pose().translate(cx, cy + uy, 0);
        g.pose().scale(uk, uk, 1);
        float uEdge = uy + 4 * uk;
        int unitRoom = (int) (2 * (Math.sqrt(Math.max(0, face * face - uEdge * uEdge)) - 1) / uk);
        int uw = Math.min(font.width(d.unit()), unitRoom);
        text(g, font, d.unit(), -uw / 2, -4, unitRoom, 0xFF4A4436);
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
        paintCentered(g, font, d.label(), (int) cx, y + size + 2, room);
    }

    /** Синхроноскоп: циферблат без шкалы, риска «12 часов», надписи «быстрее»/«медленнее», стрелка на полный оборот. */
    public static void scope(GuiGraphics g, Font font, PanelLayout.Scope s, int raw, int x, int y, int room) {
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
        paintCentered(g, font, s.label(), (int) cx, y + size + 2, room);
    }

    private static String tick(double v) {
        return Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Водомерное стекло: заполнение долей {@code frac}, красная метка низшего уровня и метка нормального. */
    public static void glass(GuiGraphics g, Font font, PanelLayout.Glass e, double frac, int x, int y, int room) {
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
        paintCentered(g, font, e.label(), x + 6, y + h + 2, room);
    }

    /** Горизонтальная шкала с подписью над ней. */
    public static void bar(GuiGraphics g, Font font, PanelLayout.Bar e, double frac, int x, int y, int room) {
        paint(g, font, e.label(), x, y - 10, room);
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

    public static void lamp(GuiGraphics g, Font font, PanelLayout.Lamp e, boolean on, int x, int y, int room) {
        String color = e.color().name().toLowerCase(java.util.Locale.ROOT);
        g.blitSprite(sprite("lamp_" + color + (on ? "_on" : "_off")), x, y, 14, 14);
        paint(g, font, e.label(), x + 16, y + 3, room);
    }

    /** Нарисованный фон поля машины: картинка ровно {@code w}×{@code h}. */
    public static void art(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        g.blit(texture, x, y, 0, 0, w, h, w, h);
    }

    /**
     * Глазок в топку: чугунная рамка, колосники, слой топлива долей {@code bed}, при горении — жар по слою, языки
     * пламени и отсвет на своде, при неполном горении — дым. Мерцание от времени, без случайных чисел: картина
     * повторяется от кадра к кадру, пока не меняются показания.
     */
    public static void firebox(GuiGraphics g, Font font, PanelLayout.Firebox e, double bed, boolean lit, boolean smoky,
            int x, int y, int room) {
        int w = e.w(), h = e.h();
        g.fill(x, y, x + w, y + h, 0xFF26282A);
        g.fill(x, y, x + w, y + 1, 0xFF4A4D50);
        g.fill(x, y, x + 1, y + h, 0xFF4A4D50);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF141516);
        g.fill(x + w - 1, y, x + w, y + h, 0xFF141516);
        int ix0 = x + 3, iy0 = y + 3, ix1 = x + w - 3, iy1 = y + h - 3;
        double t = net.minecraft.Util.getMillis() / 1000.0;
        float glow = lit ? (float) (0.85 + 0.15 * Math.sin(t * 7.3) * Math.sin(t * 2.9)) : 0;
        g.fillGradient(ix0, iy0, ix1, iy1, lit ? mix(0xFF2A0C04, 0xFF000000, 1 - glow) : 0xFF0C0B0A,
                lit ? mix(0xFF8A2A08, 0xFF000000, 1 - glow) : 0xFF151312);
        int grate = iy1 - 4;
        for (int gx = ix0; gx < ix1; gx += 3) {
            g.fill(gx, grate, Math.min(gx + 2, ix1), iy1, 0xFF3A3836);
            if (gx + 2 < ix1) g.fill(gx + 2, grate, gx + 3, iy1, lit ? mix(0xFFFF8020, 0xFF200800, 1 - glow) : 0xFF050404);
        }
        int bedH = (int) Math.round(Math.max(0, Math.min(1, bed)) * (grate - iy0 - 8));
        int top = grate - bedH;
        for (int cy = top; cy < grate; cy += 2) {
            for (int cx = ix0; cx < ix1; cx += 2) {
                int n = hash(cx - x, cy - y);
                int c;
                if (!lit) c = 0xFF000000 | grey(22 + (n & 15));
                else {
                    // воздух идёт снизу через колосники: ниже по слою — жарче, свежий уголь сверху темнее
                    double heat = 0.25 + 0.45 * (cy - top) / Math.max(4.0, bedH) + 0.4 * ((n & 63) / 63.0)
                            + 0.1 * Math.sin(t * 5 + (n & 7));
                    c = heat > 0.82 ? 0xFFFFD27A : heat > 0.62 ? 0xFFFF9A2E : heat > 0.42 ? 0xFFD0461A : 0xFF5A1C10;
                }
                g.fill(cx, cy, Math.min(cx + 2, ix1), Math.min(cy + 2, grate), c);
            }
        }
        if (lit && bedH > 0) {
            for (int k = 0; k < 7; k++) {
                int fx = ix0 + 3 + k * (ix1 - ix0 - 6) / 7;
                double s = 0.5 + 0.5 * Math.sin(t * (4.1 + k * 0.7) + k * 1.9);
                int fh = (int) ((top - iy0) * (0.35 + 0.45 * s) * Math.min(1, bed * 3));
                g.fillGradient(fx, top - fh, fx + 3, top, 0x00FF7A20, 0xD0FFB040);
                g.fillGradient(fx + 1, top - fh / 2, fx + 2, top, 0x00FFF0C0, 0xE0FFE8A0);
            }
        }
        if (smoky) g.fillGradient(ix0, iy0, ix1, top, 0xA0403A36, 0x20403A36);
        g.fill(ix0, iy0, ix1, iy0 + 1, 0x60000000);
        paintCentered(g, font, e.label(), x + w / 2, y + h + 2, room);
    }

    /** U-образный тягомер: доска со шкалой в мм вод. ст., два стеклянных колена, подкрашенная вода. */
    public static void utube(GuiGraphics g, Font font, PanelLayout.UTube e, double draftPa, int x, int y, int room) {
        int w = PanelLayout.UTube.W, h = e.h();
        g.fill(x, y, x + w, y + h, 0xFF6A5232);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFE4DCC4);
        int lx = x + 5, rx = x + w - 9, top = y + 4, bottom = y + h - 6, mid = (top + bottom) / 2;
        double half = (bottom - top) / 2.0 - 2;
        double mmFull = e.max() / 9.80665;
        for (int mm = -(int) mmFull; mm <= (int) mmFull; mm++) {
            int ty = mid - (int) Math.round(mm / mmFull * half);
            int len = mm % 5 == 0 ? 4 : 2;
            g.fill(x + w / 2 - len / 2, ty, x + w / 2 - len / 2 + len, ty + 1, mm == 0 ? 0xFF1C1C1C : 0xFF6E665A);
        }
        double d = Math.max(-1.05, Math.min(1.05, draftPa / e.max())) * half / 2;
        int lLevel = mid - (int) Math.round(d), rLevel = mid + (int) Math.round(d);
        int water = 0xFFB0281E;
        g.fill(lx, top, lx + 4, bottom, 0x50FFFFFF);
        g.fill(rx, top, rx + 4, bottom, 0x50FFFFFF);
        g.fill(lx + 1, lLevel, lx + 3, bottom + 2, water);
        g.fill(rx + 1, rLevel, rx + 3, bottom + 2, water);
        g.fill(lx + 1, bottom + 1, rx + 3, bottom + 3, water);
        g.fill(lx, bottom + 3, rx + 4, bottom + 4, 0xFF8A8478);
        g.fill(lx + 1, lLevel, lx + 3, lLevel + 1, lighten(water, 0.4f));
        g.fill(rx + 1, rLevel, rx + 3, rLevel + 1, lighten(water, 0.4f));
        g.fill(lx, top, lx + 1, bottom, 0x60FFFFFF);
        g.fill(rx, top, rx + 1, bottom, 0x60FFFFFF);
        paintCentered(g, font, e.label(), x + w / 2, y + h + 2, room);
    }

    /**
     * Индикаторная карта в латунной рамке: миллиметровка, атмосферная линия штрихом, петля p–V карандашом. Пока машина
     * стоит, на карте только атмосферная линия — индикатор не пишет.
     */
    public static void indicator(GuiGraphics g, Font font, PanelLayout.IndicatorCard e, double chestPa, boolean running,
            int x, int y, int room) {
        int w = e.w(), h = e.h();
        g.fill(x, y, x + w, y + h, 0xFF8A6A34);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC8A45A);
        int px0 = x + 3, py0 = y + 3, px1 = x + w - 3, py1 = y + h - 3;
        g.fill(px0, py0, px1, py1, 0xFFEFE9D6);
        for (int gx = px0 + 5; gx < px1; gx += 5) g.fill(gx, py0, gx + 1, py1, (gx - px0) % 25 == 0 ? 0x40C05040 : 0x20C05040);
        for (int gy = py1 - 5; gy > py0; gy -= 5) g.fill(px0, gy, px1, gy + 1, (py1 - gy) % 25 == 0 ? 0x40C05040 : 0x20C05040);
        double vMax = 1 + e.clearance();
        int iw = px1 - px0 - 4, ih = py1 - py0 - 4;
        int bx = px0 + 2, by = py1 - 2;
        int atm = by - (int) Math.round(101_325 / e.pMaxPa() * ih);
        for (int ax = px0; ax < px1; ax += 4) g.fill(ax, atm, Math.min(ax + 2, px1), atm + 1, 0xFF5A5650);
        if (running) {
            double[][] pts = e.loop(chestPa);
            for (int i = 1; i < pts.length; i++) {
                double x0 = bx + pts[i - 1][0] / vMax * iw, y0 = by - pts[i - 1][1] / e.pMaxPa() * ih;
                double x1 = bx + pts[i][0] / vMax * iw, y1 = by - pts[i][1] / e.pMaxPa() * ih;
                int n = (int) Math.ceil(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)) * 2) + 1;
                for (int k = 0; k <= n; k++) {
                    int qx = (int) Math.round(x0 + (x1 - x0) * k / n), qy = (int) Math.round(y0 + (y1 - y0) * k / n);
                    g.fill(qx, qy, qx + 1, qy + 1, 0xFF2A2A30);
                }
            }
        }
        paintCentered(g, font, e.label(), x + w / 2, y + h + 2, room);
    }

    private static int hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        return (h ^ h >>> 15) & 0x7fffffff;
    }

    private static int grey(int v) {
        return v << 16 | v << 8 | v;
    }

    private static int mix(int a, int b, float k) {
        int r = (int) ((a >> 16 & 255) * (1 - k) + (b >> 16 & 255) * k), gr = (int) ((a >> 8 & 255) * (1 - k) + (b >> 8 & 255) * k),
                bl = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return a & 0xFF000000 | r << 16 | gr << 8 | bl;
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
