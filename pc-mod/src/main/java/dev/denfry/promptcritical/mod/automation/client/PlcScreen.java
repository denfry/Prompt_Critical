package dev.denfry.promptcritical.mod.automation.client;

import dev.denfry.promptcritical.mod.automation.AutomationText;
import dev.denfry.promptcritical.mod.automation.PlcNet;
import dev.denfry.promptcritical.sim.automation.plc.IlCompiler;
import dev.denfry.promptcritical.sim.automation.plc.PlcLanguage;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Программатор ПЛК: слева текст программы на IL, справа режим, ключ RUN/STOP и онлайн-индикация входов и выходов,
 * под редактором — ошибки компиляции с номерами строк. Текст уходит в ПЛК только кнопкой «Загрузить»; ключ
 * поворачивается отдельно. Пока окно открыто, состояние спрашивается раз в полсекунды.
 */
public final class PlcScreen extends Screen {
    private static final int POLL_TICKS = 10;
    private static final int GREEN = 0xFF30D050, RED = 0xFFE04030, DARK = 0xFF303030, TEXT = 0xFFE0E0E0, DIM = 0xFF909090;

    private final BlockPos pos;
    private String loaded;
    private PlcNet.Status status;
    private MultiLineEditBox editor;
    private Button keyButton;
    private int ticks;

    private PlcScreen(BlockPos pos, String source, PlcNet.Status status) {
        super(AutomationText.msg(AutomationText.PLC_TITLE));
        this.pos = pos;
        this.loaded = source;
        this.status = status;
    }

    public static void open(PlcNet.Open p) {
        Minecraft.getInstance().setScreen(new PlcScreen(p.pos(), p.source(), p.status()));
    }

    public static void update(PlcNet.State p) {
        if (Minecraft.getInstance().screen instanceof PlcScreen s && s.pos.equals(p.pos())) s.status = p.status();
    }

    @Override
    protected void init() {
        int left = 12, top = 24, editW = Math.max(200, width - 12 - 180 - 24), editH = Math.max(80, height - top - 110);
        String text = editor != null ? editor.getValue() : loaded;
        editor = new MultiLineEditBox(font, left, top, editW, editH, Component.literal("LD %IX0.0\nST %QX0.0"), Component.empty());
        editor.setCharacterLimit(IlCompiler.MAX_CHARS);
        editor.setValue(text);
        addRenderableWidget(editor);
        int bx = left + editW + 12, bw = 168;
        addRenderableWidget(Button.builder(AutomationText.msg(AutomationText.PLC_UPLOAD), b -> send(true))
                .bounds(bx, top, bw, 20).build());
        keyButton = addRenderableWidget(Button.builder(keyLabel(), b -> {
            status = new PlcNet.Status(status.mode(), !status.runKey(), status.io(), status.ai(), status.ao(), status.executed(), status.errors());
            send(false);
        }).bounds(bx, top + 24, bw, 20).build());
        setInitialFocus(editor);
    }

    private Component keyLabel() {
        return AutomationText.msg(status.runKey() ? AutomationText.PLC_RUN : AutomationText.PLC_STOP);
    }

    private void send(boolean upload) {
        String src = editor.getValue();
        if (upload) loaded = src;
        PacketDistributor.sendToServer(new PlcNet.Command(pos, upload, upload ? src : "", status.runKey()));
    }

    @Override
    public void tick() {
        if (++ticks % POLL_TICKS == 0) PacketDistributor.sendToServer(new PlcNet.Poll(pos));
        if (keyButton != null) keyButton.setMessage(keyLabel());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, 12, 8, TEXT);
        int bx = editor.getX() + editor.getWidth() + 12, y = editor.getY() + 52;
        int mode = Math.max(0, Math.min(AutomationText.PLC_MODES.length - 1, status.mode()));
        g.drawString(font, AutomationText.msg(AutomationText.PLC_MODE, AutomationText.msg(AutomationText.PLC_MODES[mode])), bx, y,
                mode == 2 ? GREEN : mode == 3 ? RED : TEXT);
        y += 12;
        g.drawString(font, AutomationText.msg(AutomationText.PLC_SCAN, status.executed()), bx, y, DIM);
        y += 16;
        y = leds(g, bx, y, AutomationText.PLC_INPUTS, status.io() & 0xFF);
        y = leds(g, bx, y, AutomationText.PLC_OUTPUTS, (status.io() >> 8) & 0xFF);
        StringBuilder ai = new StringBuilder(), ao = new StringBuilder();
        for (float v : status.ai()) ai.append(String.format(Locale.ROOT, "%.1f ", v));
        for (float v : status.ao()) ao.append(String.format(Locale.ROOT, "%.1f ", v));
        g.drawString(font, AutomationText.msg(AutomationText.PLC_ANALOG, ai.toString().strip(), ao.toString().strip()), bx, y, DIM);
        // ошибки компиляции или «без ошибок»; предупреждение, если текст в окне не тот, что в ПЛК
        int ey = editor.getY() + editor.getHeight() + 8;
        // язык определяется по тексту: PROGRAM или (*ST*) в начале — ST, иначе IL
        boolean st = PlcLanguage.of(editor.getValue()) == PlcLanguage.ST;
        g.drawString(font, AutomationText.msg(AutomationText.PLC_LANGUAGE, st ? "Structured Text" : "Instruction List"), 12, ey, DIM);
        ey += 11;
        if (!editor.getValue().equals(loaded)) {
            g.drawString(font, AutomationText.msg(AutomationText.PLC_UNSAVED), 12, ey, 0xFFE0C040);
            ey += 11;
        }
        if (status.errors().isEmpty()) {
            g.drawString(font, AutomationText.msg(AutomationText.PLC_OK), 12, ey, GREEN);
        } else {
            for (PlcNet.Error e : status.errors()) {
                if (ey > height - 12) break;
                g.drawString(font, AutomationText.msg(AutomationText.PLC_ERROR, e.line(), AutomationText.msg(AutomationText.plcError(e.kind())),
                        e.token()), 12, ey, RED);
                ey += 11;
            }
        }
    }

    /** Ряд из восьми ламп: подпись и квадраты 0…7. */
    private int leds(GuiGraphics g, int x, int y, String label, int bits) {
        g.drawString(font, AutomationText.msg(label), x, y, TEXT);
        y += 11;
        for (int i = 0; i < 8; i++) {
            int lx = x + i * 20;
            g.fill(lx, y, lx + 14, y + 10, (bits >> i & 1) == 1 ? GREEN : DARK);
            g.drawString(font, String.valueOf(i), lx + 4, y + 1, 0xFF000000, false);
        }
        return y + 16;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
