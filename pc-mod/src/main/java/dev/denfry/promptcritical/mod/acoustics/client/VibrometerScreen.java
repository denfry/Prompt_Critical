package dev.denfry.promptcritical.mod.acoustics.client;

import dev.denfry.promptcritical.mod.acoustics.VibrationReading;
import dev.denfry.promptcritical.sim.acoustics.IsoZone;
import dev.denfry.promptcritical.sim.acoustics.MachineClass;
import dev.denfry.promptcritical.sim.acoustics.VibrationSpectrum.Band;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Экран виброанализатора: слева — шкала общего уровня с зонами ISO класса машины и стрелкой замера, справа — спектр
 * в логарифмическом масштабе частот 1–2000 Гц, ниже — таблица полос, огибающая ВЧ, температура корпуса подшипника и
 * шум в точке замера.
 */
public final class VibrometerScreen extends Screen {
    private static final int W = 330, H = 236;
    private static final int PLOT_W = 250, PLOT_H = 90;
    private static final double F_MIN = 1, F_MAX = 2000;
    private static final int C_BG = 0xF0101418, C_FRAME = 0xFF5A6670, C_GRID = 0xFF2A3238, C_TEXT = 0xFFE0E6EA, C_DIM = 0xFF8A969E;
    private static final int[] ZONE = {0xFF3FAE4F, 0xFFC8C83C, 0xFFE08A2C, 0xFFD23C3C};

    private final VibrationReading r;

    private VibrometerScreen(VibrationReading r) {
        super(Component.translatable("screen.promptcritical.vibrometer.title"));
        this.r = r;
    }

    public static void open(VibrationReading r) {
        Minecraft.getInstance().setScreen(new VibrometerScreen(r));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static String f(String fmt, double v) {
        return String.format(Locale.ROOT, fmt, v);
    }

    private static int bandColor(Band b) {
        return switch (b) {
            case X1 -> 0xFF5AB4FF;
            case X2 -> 0xFF8C7CFF;
            case BPF -> 0xFF4FD6C8;
            case BPFO, BPFI, BSF, FTF -> 0xFFFF7A5A;
            case GMF -> 0xFFE8D25A;
        };
    }

    private static String bandName(Band b) {
        return switch (b) {
            case X1 -> "1x";
            case X2 -> "2x";
            default -> b.name();
        };
    }

    private static int xOf(double hz, int x0) {
        double t = (Math.log10(Math.clamp(hz, F_MIN, F_MAX)) - Math.log10(F_MIN)) / (Math.log10(F_MAX) - Math.log10(F_MIN));
        return x0 + (int) Math.round(t * PLOT_W);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = (width - W) / 2, y = (height - H) / 2;
        g.fill(x, y, x + W, y + H, C_BG);
        g.renderOutline(x, y, W, H, C_FRAME);
        g.drawString(font, title, x + 8, y + 6, C_TEXT, false);
        MachineClass c = r.machineClass();
        if (!(r.rpm() > 0)) {
            g.drawString(font, Component.translatable("screen.promptcritical.vibrometer.stopped"), x + 8, y + 22, C_DIM, false);
            return;
        }
        g.drawString(font, Component.translatable("screen.promptcritical.vibrometer.shaft", f("%.0f", r.rpm()),
                f("%.2f", r.rpm() / 60), f("%.1f", r.powerKw()), c.name()), x + 8, y + 18, C_DIM, false);
        IsoZone zone = c.zoneOf(r.overall());
        g.drawString(font, Component.translatable("screen.promptcritical.vibrometer.overall", f("%.2f", r.overall() * 1e3),
                f("%.0f", r.bandLowHz())), x + 8, y + 30, C_TEXT, false);
        g.drawString(font, Component.translatable("screen.promptcritical.vibrometer.zone." + zone.name().toLowerCase(Locale.ROOT)),
                x + 8, y + 42, ZONE[zone.ordinal()], false);

        // шкала общего уровня: зоны A–D до 1,25 границы C/D
        int gx = x + 10, gy = y + 58, gh = PLOT_H;
        double top = c.cd * 1.25;
        double[] bounds = {0, c.ab, c.bc, c.cd, top};
        for (int i = 0; i < 4; i++) {
            int y1 = gy + gh - (int) Math.round(bounds[i] / top * gh), y0 = gy + gh - (int) Math.round(bounds[i + 1] / top * gh);
            g.fill(gx, y0, gx + 10, y1, ZONE[i]);
        }
        int my = gy + gh - (int) Math.round(Math.min(r.overall(), top) / top * gh);
        g.fill(gx - 3, my - 1, gx + 13, my + 1, C_TEXT);

        // спектр
        int px = x + 40, py = gy;
        g.fill(px, py, px + PLOT_W, py + PLOT_H, 0xFF0A0D10);
        for (double fz = 1; fz <= 1000; fz *= 10) {
            int lx = xOf(fz, px);
            g.fill(lx, py, lx + 1, py + PLOT_H, C_GRID);
            g.drawString(font, f("%.0f", fz), lx + 2, py + PLOT_H + 2, C_DIM, false);
        }
        int lowX = xOf(r.bandLowHz(), px), highX = xOf(1000, px);
        g.fill(lowX, py + PLOT_H - 1, highX, py + PLOT_H, 0xFF3A6A8A);
        double vmax = c.bc;
        for (VibrationReading.Line l : r.lines()) vmax = Math.max(vmax, l.vRms() * 1.15);
        for (VibrationReading.Line l : r.lines()) {
            if (!(l.vRms() > 0)) continue;
            int lx = xOf(l.hz(), px), h = Math.max(1, (int) Math.round(l.vRms() / vmax * PLOT_H));
            g.fill(lx - 1, py + PLOT_H - h, lx + 2, py + PLOT_H, bandColor(l.band()));
        }
        g.drawString(font, "Hz", px + PLOT_W - 12, py + PLOT_H + 2, C_DIM, false);
        g.drawString(font, f("%.1f mm/s", vmax * 1e3), px + 2, py + 2, C_DIM, false);

        // таблица полос
        int ty = py + PLOT_H + 14, col = 0, row = 0;
        for (VibrationReading.Line l : r.lines()) {
            String s = String.format(Locale.ROOT, "%-5s", bandName(l.band())) + f("%7.1f Hz ", l.hz()) + f("%5.2f", l.vRms() * 1e3);
            g.drawString(font, s, x + 10 + col * 160, ty + row * 10, bandColor(l.band()), false);
            if (++col == 2) {
                col = 0;
                row++;
            }
        }
        int by = ty + (row + (col > 0 ? 1 : 0)) * 10 + 4;
        g.drawString(font, Component.translatable("screen.promptcritical.vibrometer.envelope", f("%.1f", r.aHf()),
                f("%.0f", r.bearingTempK() - 273.15)), x + 10, by, C_DIM, false);
        Component noise = Float.isNaN(r.noiseDb()) ? Component.translatable("screen.promptcritical.vibrometer.quiet")
                : Component.translatable("screen.promptcritical.vibrometer.noise", f("%.0f", r.noiseDb()));
        g.drawString(font, noise, x + 10, by + 10, C_DIM, false);
    }
}
