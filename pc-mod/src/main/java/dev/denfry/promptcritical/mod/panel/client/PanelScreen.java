package dev.denfry.promptcritical.mod.panel.client;

import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.PanelMenu;
import dev.denfry.promptcritical.mod.panel.PanelText;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.Util;
import dev.denfry.promptcritical.mod.panel.PanelNet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
    private PanelFit fit;
    /** Строки ввода щита; набранное переживает пересборку экрана (смена размера окна). */
    private final List<Field> fields = new ArrayList<>();

    private record Field(PanelLayout.TextField spec, EditBox box) {
    }

    public PanelScreen(PanelMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.layout = menu.layout();
        this.imageWidth = layout.imageWidth(menu.side());
        this.imageHeight = layout.imageHeight(menu.side());
    }

    @Override
    protected void init() {
        super.init();
        fit = PanelFit.of(layout, font, title);
        Map<Integer, String> typed = new java.util.HashMap<>();
        for (Field f : fields) typed.put(f.spec().id(), f.box().getValue());
        fields.clear();
        for (PanelLayout.Element e : layout.elements) {
            if (e instanceof PanelLayout.Button b) {
                addRenderableWidget(new PanelButton(leftPos + b.x(), topPos + b.y(), b.w(), 16, b.label(), b.tip(), b.holdMs(),
                        () -> click(b.id())));
            } else if (e instanceof PanelLayout.TextField t) {
                // рамка EditBox рисуется на пиксель снаружи: поле на 2 px уже и ниже места под ним
                EditBox box = new EditBox(font, leftPos + t.x() + 1, topPos + t.y() + 1, t.w() - PanelLayout.TextField.SEND_W - 6, 14,
                        t.hint());
                box.setMaxLength(t.maxLength());
                box.setHint(t.hint());
                box.setValue(typed.getOrDefault(t.id(), ""));
                if (t.tip() != null) box.setTooltip(Tooltip.create(t.tip()));
                Field f = new Field(t, box);
                fields.add(f);
                addRenderableWidget(box);
                addRenderableWidget(new PanelButton(leftPos + t.x() + t.w() - PanelLayout.TextField.SEND_W, topPos + t.y(),
                        PanelLayout.TextField.SEND_W, 16, t.send(), null, () -> send(f)));
            }
        }
    }

    /** Отправить строку поля серверу и очистить поле; пустую не шлём. */
    private void send(Field f) {
        String text = f.box().getValue().strip();
        if (text.isEmpty()) return;
        PacketDistributor.sendToServer(new PanelNet.Text(menu.containerId, f.spec().id(), text));
        f.box().setValue("");
    }

    /**
     * Пока поле в фокусе, клавиши идут ему: иначе клавиша инвентаря ({@code E}) закрыла бы щит посреди набора. Enter
     * отправляет, Esc закрывает щит как обычно.
     */
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (Field f : fields) {
            if (!f.box().isFocused() || key == GLFW.GLFW_KEY_ESCAPE) continue;
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                send(f);
                return true;
            }
            return f.box().keyPressed(key, scan, mods) || f.box().canConsumeInput();
        }
        return super.keyPressed(key, scan, mods);
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
        if (layout.art != null) {
            PanelArt.art(g, layout.art, x, y, layout.width, layout.height);
            if (imageHeight > layout.height) PanelArt.background(g, x, y + layout.height, layout.width, imageHeight - layout.height);
        } else {
            PanelArt.background(g, x, y, layout.width, imageHeight);
        }
        if (menu.side()) PanelArt.background(g, x + layout.width, y, imageWidth - layout.width, imageHeight);
        PanelArt.plate(g, font, title, x + layout.width / 2, y + 5, fit.titleRoom());
        for (PanelLayout.Element e : layout.elements) {
            int ex = x + e.x(), ey = y + e.y();
            switch (e) {
                case PanelLayout.Dial d -> PanelArt.dial(g, font, d, smooth(d, menu.get(d.ch(), d.scale()), dt), ex, ey,
                        fit.room(d, font.width(d.label())));
                case PanelLayout.Glass gl -> PanelArt.glass(g, font, gl, smooth(gl, menu.get(gl.ch(), 1000), dt), ex, ey,
                        fit.room(gl, font.width(gl.label())));
                case PanelLayout.Scope sc -> PanelArt.scope(g, font, sc, menu.raw(sc.ch()), ex, ey, fit.room(sc, font.width(sc.label())));
                case PanelLayout.Bar b -> PanelArt.bar(g, font, b, smooth(b, menu.get(b.ch(), b.scale()) / b.max(), dt), ex, ey,
                        fit.room(b, font.width(b.label())));
                case PanelLayout.Lamp l -> PanelArt.lamp(g, font, l, menu.bit(l.ch(), l.bit()), ex, ey, fit.room(l, font.width(l.label())));
                case PanelLayout.Readout r -> PanelArt.window(g, font, r.text().apply(menu), ex, ey, r.w());
                case PanelLayout.Caption c -> PanelArt.paint(g, font, c.text(), ex, ey, fit.room(c, font.width(c.text())));
                case PanelLayout.Firebox f -> PanelArt.firebox(g, font, f, smooth(f, menu.get(f.bedCh(), f.bedScale()) / f.bedMax(), dt),
                        menu.bit(f.flagsCh(), f.litBit()), menu.bit(f.flagsCh(), f.smokyBit()), ex, ey, fit.room(f, font.width(f.label())));
                case PanelLayout.UTube u -> PanelArt.utube(g, font, u, smooth(u, menu.get(u.ch(), u.scale()), dt), ex, ey,
                        fit.room(u, font.width(u.label())));
                case PanelLayout.Plot pl -> PanelArt.plot(g, font, pl, menu, ex, ey, fit.room(pl, font.width(pl.label())));
                case PanelLayout.IndicatorCard ic -> PanelArt.indicator(g, font, ic,
                        smooth(ic, menu.get(ic.chestCh(), 1) * 1000 + 101_325, dt), menu.bit(ic.flagsCh(), ic.runBit()), ex, ey,
                        fit.room(ic, font.width(ic.label())));
                case PanelLayout.Trace t -> PanelArt.trace(g, t.values().apply(menu), ex, ey, t.w(), t.h());
                case PanelLayout.Button b -> {
                }
                case PanelLayout.TextField t -> {
                }
            }
        }
        for (int i = 0; i < menu.machineSlots(); i++) {
            Slot s = menu.slots.get(i);
            PanelArt.slot(g, x + s.x, y + s.y, layout.slots.get(i).output());
        }
        if (!layout.inventory) return;
        int ix = x + layout.inventoryX(menu.side()), iy = y + layout.inventoryY(menu.side());
        PanelArt.inset(g, ix - 4, iy - 4, 9 * 18 + 6, 3 * 18 + 4 + 18 + 6 + 4);
        for (int i = menu.machineSlots(); i < menu.slots.size(); i++) {
            Slot s = menu.slots.get(i);
            PanelArt.slot(g, x + s.x, y + s.y, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (layout.inventory) PanelArt.paint(g, font, PanelText.INVENTORY.get(), layout.inventoryX(menu.side()) - 1,
                layout.inventoryY(menu.side()) - 13);
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
        int plate = fit.titleRoom() / 2 + 8;
        if (inside(mx, my, layout.width / 2 - plate, 5, plate * 2, 14)) {
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
                case PanelLayout.Firebox f -> {
                    lines.add(f.tip() != null ? f.tip().apply(menu) : f.label());
                    yield inside(mx, my, f.x(), f.y(), f.w(), f.h() + 12);
                }
                case PanelLayout.UTube u -> {
                    lines.add(u.tip() != null ? u.tip().apply(menu) : u.label());
                    yield inside(mx, my, u.x(), u.y(), PanelLayout.UTube.W, u.h() + 12);
                }
                case PanelLayout.Plot pl -> {
                    lines.add(pl.tip() != null ? pl.tip().apply(menu) : pl.label());
                    yield inside(mx, my, pl.x(), pl.y(), pl.w(), pl.h() + 12);
                }
                case PanelLayout.IndicatorCard ic -> {
                    lines.add(ic.tip() != null ? ic.tip().apply(menu) : ic.label());
                    yield inside(mx, my, ic.x(), ic.y(), ic.w(), ic.h() + 12);
                }
                case PanelLayout.Trace t -> {
                    if (t.tip() != null) lines.add(t.tip().apply(menu));
                    yield t.tip() != null && inside(mx, my, t.x(), t.y(), t.w(), t.h());
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
