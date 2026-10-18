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
    private long pressedUntil;

    public PanelButton(int x, int y, int w, int h, Component label, @Nullable Component tip, Runnable onPress) {
        super(x, y, w, h, label);
        this.onPress = onPress;
        if (tip != null) setTooltip(Tooltip.create(tip));
    }

    @Override
    public void onPress() {
        pressedUntil = net.minecraft.Util.getMillis() + 150;
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean pressed = net.minecraft.Util.getMillis() < pressedUntil;
        ResourceLocation s = !active ? DISABLED : pressed ? PRESSED : isHoveredOrFocused() ? HOVER : NORMAL;
        g.blitSprite(s, getX(), getY(), width, height);
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
