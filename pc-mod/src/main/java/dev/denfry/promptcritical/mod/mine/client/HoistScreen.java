package dev.denfry.promptcritical.mod.mine.client;

import dev.denfry.promptcritical.mod.mine.HoistMenu;
import dev.denfry.promptcritical.mod.mine.HoistText;
import dev.denfry.promptcritical.mod.panel.client.PanelArt;
import dev.denfry.promptcritical.mod.panel.client.PanelButton;
import dev.denfry.promptcritical.sim.mining.HoistDuty;
import dev.denfry.promptcritical.sim.mining.HoistState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Экран панели подъёмной машины в стиле местного щита ({@link PanelArt}): посадки кнопками, состояние, груз и запас
 * каната, мощность, предел скорости.
 */
public final class HoistScreen extends AbstractContainerScreen<HoistMenu> {
    private static final int W = 236, ROW = 18, PAD = 8, STATUS_LINES = 5, LINE = 14, TOP = 24;
    private static final int TEXT = 0xFFE8DFC4, DIM = 0xFFB0A890, ALARM = 0xFFFF5848;

    private final int[] landings;
    private final PanelButton[] landingButtons;

    public HoistScreen(HoistMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.landings = menu.landings();
        this.landingButtons = new PanelButton[landings.length];
        this.imageWidth = W;
        this.imageHeight = TOP + STATUS_LINES * LINE + PAD + Math.max(1, landings.length) * ROW + ROW + PAD;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + TOP + STATUS_LINES * LINE + PAD;
        for (int i = 0; i < landings.length; i++) {
            int id = i;
            landingButtons[i] = addRenderableWidget(new PanelButton(leftPos + PAD, y + i * ROW, W - 2 * PAD, ROW - 2,
                    HoistText.landing(landings, i), null, () -> click(id)));
        }
        y += Math.max(1, landings.length) * ROW;
        int bw = (W - 2 * PAD - 6) / 4;
        addRenderableWidget(new PanelButton(leftPos + PAD, y, bw, ROW - 2, HoistText.msg("hoist.stop"), null,
                () -> click(HoistMenu.BUTTON_STOP)));
        addRenderableWidget(new PanelButton(leftPos + PAD + bw + 2, y, bw, ROW - 2, HoistText.msg("hoist.reset"), null,
                () -> click(HoistMenu.BUTTON_RESET)));
        addRenderableWidget(new PanelButton(leftPos + PAD + 2 * (bw + 2), y, bw, ROW - 2, Component.literal("−"), null,
                () -> click(HoistMenu.BUTTON_SLOWER)));
        addRenderableWidget(new PanelButton(leftPos + PAD + 3 * (bw + 2), y, bw, ROW - 2, Component.literal("+"), null,
                () -> click(HoistMenu.BUTTON_FASTER)));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int target = menu.get(HoistMenu.D_TARGET), at = menu.get(HoistMenu.D_AT);
        for (int i = 0; i < landingButtons.length; i++) {
            Component name = HoistText.landing(landings, i);
            landingButtons[i].setMessage(i == target ? Component.literal("» ").append(name).append(" «")
                    : i == at ? Component.literal("● ").append(name) : name);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelArt.background(g, leftPos, topPos, imageWidth, imageHeight);
        PanelArt.plate(g, font, title, leftPos + imageWidth / 2, topPos + 5);
        for (int i = 0; i < STATUS_LINES; i++) PanelArt.window(g, font, Component.empty(), leftPos + PAD, topPos + TOP + i * LINE, W - 2 * PAD);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int y = TOP + 3;
        int x = PAD + 4;
        int state = menu.get(HoistMenu.D_STATE);
        if (state < 0) {
            HoistText.Problem p = HoistText.Problem.values()[Math.max(0, Math.min(HoistText.Problem.values().length - 1,
                    menu.get(HoistMenu.D_PROBLEM)))];
            g.drawWordWrap(font, HoistText.problem(p == HoistText.Problem.NONE ? HoistText.Problem.NO_ROPE : p), x, y, W - 2 * PAD - 8, ALARM);
            return;
        }
        HoistState s = HoistState.values()[state];
        g.drawString(font, HoistText.state(s), x, y, s.tripped() || s.broken() ? ALARM : TEXT, false);
        y += LINE;
        g.drawString(font, HoistText.msg("hoist.position", HoistText.num(menu.get(HoistMenu.D_DEPTH_DM) / 10.0),
                HoistText.num(menu.get(HoistMenu.D_SPEED_CMS) / 100.0)), x, y, TEXT, false);
        y += LINE;
        double sf = menu.get(HoistMenu.D_SF_X10) / 10.0, need = menu.get(HoistMenu.D_SF_NEED_X10) / 10.0;
        HoistDuty duty = HoistDuty.values()[Math.max(0, Math.min(2, menu.get(HoistMenu.D_DUTY)))];
        g.drawString(font, HoistText.msg("hoist.load", menu.get(HoistMenu.D_PAYLOAD_KG), HoistText.duty(duty),
                HoistText.num(sf), HoistText.num(need)), x, y, sf < need ? ALARM : DIM, false);
        y += LINE;
        g.drawString(font, HoistText.msg("hoist.power", menu.get(HoistMenu.D_POWER_KW), menu.get(HoistMenu.D_AVAILABLE_KW)),
                x, y, DIM, false);
        y += LINE;
        Component rope = menu.get(HoistMenu.D_ROPE_WEAR) != 0 ? HoistText.msg("hoist.rope_wear")
                : HoistText.msg("hoist.rope", menu.get(HoistMenu.D_ROPE_MM));
        g.drawString(font, rope, x, y, menu.get(HoistMenu.D_ROPE_WEAR) != 0 ? ALARM : DIM, false);
        g.drawString(font, HoistText.msg("hoist.speed", HoistText.num(menu.get(HoistMenu.D_LIMIT_DMS) / 10.0)),
                x + 110, y, DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
