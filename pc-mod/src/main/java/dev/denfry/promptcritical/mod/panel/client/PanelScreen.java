package dev.denfry.promptcritical.mod.panel.client;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelMenu;
import dev.denfry.promptcritical.mod.panel.PanelText;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Экран местного щита по {@link PanelLayout}: латунная табличка с именем машины, приборы и слоты на крашеной
 * панели, инвентарь игрока в нише снизу. Стрелки и стекло догоняют показания с постоянной времени
 * {@link #LAG_S} — как инерция настоящего прибора. Только клиент.
 */
public final class PanelScreen extends AbstractContainerScreen<PanelMenu> {
    private static final double LAG_S = 0.35;
    private final PanelLayout layout;
    private final Map<PanelLayout.Element, Double> shown = new IdentityHashMap<>();
    private long lastFrame = Util.getMillis();

    public PanelScreen(PanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.layout = menu.layout();
        this.imageWidth = layout.width;
        this.imageHeight = layout.imageHeight();
    }

    @Override
    protected void init() {
        super.init();
        for (PanelLayout.Element e : layout.elements) {
            if (e instanceof PanelLayout.Button b) {
                addRenderableWidget(new PanelButton(leftPos + b.x(), topPos + b.y(), b.w(), 16, b.label(), b.tip(),
                        () -> click(b.id())));
            }
        }
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    /** Показание элемента с инерцией; первый кадр — сразу на месте. */
    private double smooth(PanelLayout.Element e, double target, double dt) {
        Double cur = shown.get(e);
        double v = cur == null || Double.isNaN(cur) ? target : cur + (target - cur) * (1 - Math.exp(-dt / LAG_S));
        shown.put(e, v);
        return v;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        long now = Util.getMillis();
        double dt = Math.min(0.25, (now - lastFrame) / 1000.0);
        lastFrame = now;
        int x = leftPos, y = topPos;
        PanelArt.background(g, x, y, imageWidth, imageHeight);
        PanelArt.plate(g, font, title, x + imageWidth / 2, y + 5);
        for (PanelLayout.Element e : layout.elements) {
            int ex = x + e.x(), ey = y + e.y();
            switch (e) {
                case PanelLayout.Dial d -> PanelArt.dial(g, font, d, smooth(d, menu.get(d.ch(), d.scale()), dt), ex, ey);
                case PanelLayout.Glass gl -> PanelArt.glass(g, font, gl, smooth(gl, menu.get(gl.ch(), 1000), dt), ex, ey);
                case PanelLayout.Scope sc -> PanelArt.scope(g, font, sc, menu.raw(sc.ch()), ex, ey);
                case PanelLayout.Bar b -> PanelArt.bar(g, font, b, smooth(b, menu.get(b.ch(), b.scale()) / b.max(), dt), ex, ey);
                case PanelLayout.Lamp l -> PanelArt.lamp(g, font, l, menu.bit(l.ch(), l.bit()), ex, ey);
                case PanelLayout.Readout r -> PanelArt.window(g, font, r.text().apply(menu), ex, ey, r.w());
                case PanelLayout.Caption c -> PanelArt.paint(g, font, c.text(), ex, ey);
                case PanelLayout.Button b -> {
                }
            }
        }
        for (int i = 0; i < menu.machineSlots(); i++) {
            Slot s = menu.slots.get(i);
            PanelArt.slot(g, x + s.x, y + s.y, layout.slots.get(i).output());
        }
        if (!layout.inventory) return;
        int ix = x + layout.inventoryX(), iy = y + layout.inventoryY();
        PanelArt.inset(g, ix - 4, iy - 4, 9 * 18 + 6, 3 * 18 + 4 + 18 + 6 + 4);
        for (int i = menu.machineSlots(); i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            PanelArt.slot(g, x + s.x, y + s.y, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (layout.inventory) PanelArt.paint(g, font, PanelText.INVENTORY.get(), layout.inventoryX() - 1, layout.inventoryY() - 13);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (menu.getCarried().isEmpty() && hoveredSlot == null) elementTooltip(g, mouseX, mouseY);
        else if (hoveredSlot != null && !hoveredSlot.hasItem()) {
            int i = menu.slots.indexOf(hoveredSlot);
            if (i >= 0 && i < menu.machineSlots()) g.renderTooltip(font, layout.slots.get(i).tip(), mouseX, mouseY);
        }
    }

    private void elementTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int mx = mouseX - leftPos, my = mouseY - topPos;
        int plate = font.width(title) / 2 + 8;
        if (inside(mx, my, imageWidth / 2 - plate, 5, plate * 2, 14)) {
            g.renderTooltip(font, PanelText.SHIFT_HINT.get(), mouseX, mouseY);
            return;
        }
        for (PanelLayout.Element e : layout.elements) {
            List<Component> lines = new ArrayList<>(2);
            boolean in = switch (e) {
                case PanelLayout.Dial d -> {
                    if (d.tip() != null) lines.add(d.tip().apply(menu));
                    else lines.add(Component.empty().append(d.label()).append(": " + menu.num(d.ch(), d.scale(), d.decimals()) + " ")
                            .append(d.unit()));
                    yield inside(mx, my, d.x(), d.y(), d.size(), d.size() + 12);
                }
                case PanelLayout.Scope sc -> {
                    lines.add(sc.tip() != null ? sc.tip().apply(menu) : sc.label());
                    yield inside(mx, my, sc.x(), sc.y(), PanelLayout.Scope.SIZE, PanelLayout.Scope.SIZE + 12);
                }
                case PanelLayout.Glass gl -> {
                    lines.add(gl.tip() != null ? gl.tip().apply(menu) : gl.label());
                    yield inside(mx, my, gl.x() - 2, gl.y(), 16, gl.h() + 12);
                }
                case PanelLayout.Bar b -> {
                    if (b.tip() != null) lines.add(b.tip().apply(menu));
                    yield b.tip() != null && inside(mx, my, b.x(), b.y() - 10, b.w(), 18);
                }
                case PanelLayout.Readout r -> {
                    if (r.tip() != null) lines.add(r.tip().apply(menu));
                    yield r.tip() != null && inside(mx, my, r.x(), r.y(), r.w(), 13);
                }
                case PanelLayout.Lamp l -> {
                    lines.add(l.label());
                    yield inside(mx, my, l.x(), l.y(), 14, 14);
                }
                default -> false;
            };
            if (in && !lines.isEmpty()) {
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
                return;
            }
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
