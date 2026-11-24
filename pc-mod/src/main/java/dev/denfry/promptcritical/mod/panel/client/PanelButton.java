package dev.denfry.promptcritical.mod.panel.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Бакелитовая кнопка щита в латунной оправе при наведении. Только клиент. */
public final class PanelButton extends AbstractButton {
    private static final ResourceLocation NORMAL = PanelArt.sprite("button"), HOVER = PanelArt.sprite("button_hover"),
            PRESSED = PanelArt.sprite("button_pressed"), DISABLED = PanelArt.sprite("button_disabled");
    private final Runnable onPress;
    /** Ключ под крышкой: срабатывает после {@code holdMs} мс удержания левой кнопкой мыши; 0 — обычная кнопка. */
    private final int holdMs;
    private long pressedUntil, holdStart = -1;

    public PanelButton(int x, int y, int w, int h, Component label, @Nullable Component tip, Runnable onPress) {
        this(x, y, w, h, label, tip, 0, onPress);
    }

    public PanelButton(int x, int y, int w, int h, Component label, @Nullable Component tip, int holdMs, Runnable onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        this.holdMs = holdMs;
        if (tip != null) setTooltip(Tooltip.create(tip));
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (holdMs > 0) holdStart = net.minecraft.Util.getMillis();
        else onPress();
    }

    @Override
    public void onPress() {
        // ключ под крышкой с клавиатуры не нажимается — только удержанием
        if (holdMs > 0) return;
        pressedUntil = net.minecraft.Util.getMillis() + 150;
        onPress.run();
    }

    /** Удержание ключа: отпустили или увели мышь — сброс; додержали — срабатывание один раз. */
    private float holdProgress(int mouseX, int mouseY) {
        if (holdMs <= 0 || holdStart < 0) return 0;
        long now = net.minecraft.Util.getMillis();
        boolean down = org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        if (!down || !isMouseOver(mouseX, mouseY)) {
            holdStart = -1;
            return 0;
        }
        float f = (now - holdStart) / (float) holdMs;
        if (f >= 1) {
            holdStart = -1;
            pressedUntil = now + 300;
            onPress.run();
            return 0;
        }
        return f;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float hold = holdProgress(mouseX, mouseY);
        boolean pressed = hold > 0 || net.minecraft.Util.getMillis() < pressedUntil;
        ResourceLocation s = !active ? DISABLED : pressed ? PRESSED : isHoveredOrFocused() ? HOVER : NORMAL;
        g.blitSprite(s, getX(), getY(), width, height);
        // красная полоса удержания ключа под крышкой
        if (hold > 0) g.fill(getX() + 1, getY() + height - 3, getX() + 1 + (int) ((width - 2) * hold), getY() + height - 1, 0xFFD03030);
        var font = Minecraft.getInstance().font;
        int color = active ? 0xFFE8E3D0 : 0xFF8A8F8C;
        int dy = pressed ? 1 : 0;
        int tw = font.width(getMessage());
        if (tw > width - 6) {
            renderScrollingString(g, font, 3, color);
            return;
        }
        g.drawString(font, getMessage(), getX() + (width - tw) / 2, getY() + (height - 8) / 2 + dy, color, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput out) {
        defaultButtonNarrationText(out);
    }
}
