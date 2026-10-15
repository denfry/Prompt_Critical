package dev.denfry.promptcritical.mod.mine.client;

import dev.denfry.promptcritical.mod.mine.HoistMenu;
import dev.denfry.promptcritical.mod.mine.HoistText;
import dev.denfry.promptcritical.sim.mining.HoistDuty;
import dev.denfry.promptcritical.sim.mining.HoistState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Экран панели подъёмной машины: посадки кнопками, состояние, груз и запас каната, мощность, предел скорости. */
public final class HoistScreen extends AbstractContainerScreen<HoistMenu> {
    private static final int W = 236, ROW = 20, PAD = 8, STATUS_LINES = 5;
    private static final int BG = 0xE0101418, FRAME = 0xFF5A6470, TEXT = 0xFFE0E0E0, DIM = 0xFF9AA4AE, ALARM = 0xFFFF5050,
            ACTIVE = 0xFFFFD040;

    private final int[] landings;
    private final Button[] landingButtons;

    public HoistScreen(HoistMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.landings = menu.landings();
        this.landingButtons = new Button[landings.length];
        this.imageWidth = W;
        this.imageHeight = PAD * 3 + 10 + STATUS_LINES * 11 + Math.max(1, landings.length) * ROW + ROW;
    }

    @Override
    protected void init() {
        super.init();
        int y = topPos + PAD * 2 + 10 + STATUS_LINES * 11;
        for (int i = 0; i < landings.length; i++) {
            int id = i;
            landingButtons[i] = addRenderableWidget(Button.builder(HoistText.landing(landings, i), b -> click(id))
                    .bounds(leftPos + PAD, y + i * ROW, W - 2 * PAD, ROW - 2).build());
        }
        y += Math.max(1, landings.length) * ROW;
        int bw = (W - 2 * PAD - 6) / 4;
        addRenderableWidget(Button.builder(HoistText.msg("hoist.stop"), b -> click(HoistMenu.BUTTON_STOP))
                .bounds(leftPos + PAD, y, bw, ROW - 2).build());
        addRenderableWidget(Button.builder(HoistText.msg("hoist.reset"), b -> click(HoistMenu.BUTTON_RESET))
                .bounds(leftPos + PAD + bw + 2, y, bw, ROW - 2).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> click(HoistMenu.BUTTON_SLOWER))
                .bounds(leftPos + PAD + 2 * (bw + 2), y, bw, ROW - 2).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> click(HoistMenu.BUTTON_FASTER))
                .bounds(leftPos + PAD + 3 * (bw + 2), y, bw, ROW - 2).build());
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
        g.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, FRAME);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, PAD, PAD, ACTIVE, false);
        int y = PAD + 14;
        int state = menu.get(HoistMenu.D_STATE);
        if (state < 0) {
            HoistText.Problem p = HoistText.Problem.values()[Math.max(0, Math.min(HoistText.Problem.values().length - 1,
                    menu.get(HoistMenu.D_PROBLEM)))];
            g.drawWordWrap(font, HoistText.problem(p == HoistText.Problem.NONE ? HoistText.Problem.NO_ROPE : p), PAD, y, W - 2 * PAD, ALARM);
            return;
        }
        HoistState s = HoistState.values()[state];
        g.drawString(font, HoistText.state(s), PAD, y, s.tripped() || s.broken() ? ALARM : TEXT, false);
        y += 11;
        g.drawString(font, HoistText.msg("hoist.position", HoistText.num(menu.get(HoistMenu.D_DEPTH_DM) / 10.0),
                HoistText.num(menu.get(HoistMenu.D_SPEED_CMS) / 100.0)), PAD, y, TEXT, false);
        y += 11;
        double sf = menu.get(HoistMenu.D_SF_X10) / 10.0, need = menu.get(HoistMenu.D_SF_NEED_X10) / 10.0;
        HoistDuty duty = HoistDuty.values()[Math.max(0, Math.min(2, menu.get(HoistMenu.D_DUTY)))];
        g.drawString(font, HoistText.msg("hoist.load", menu.get(HoistMenu.D_PAYLOAD_KG), HoistText.duty(duty),
                HoistText.num(sf), HoistText.num(need)), PAD, y, sf < need ? ALARM : DIM, false);
        y += 11;
        g.drawString(font, HoistText.msg("hoist.power", menu.get(HoistMenu.D_POWER_KW), menu.get(HoistMenu.D_AVAILABLE_KW)),
                PAD, y, DIM, false);
        y += 11;
        Component rope = menu.get(HoistMenu.D_ROPE_WEAR) != 0 ? HoistText.msg("hoist.rope_wear")
                : HoistText.msg("hoist.rope", menu.get(HoistMenu.D_ROPE_MM));
        g.drawString(font, rope, PAD, y, menu.get(HoistMenu.D_ROPE_WEAR) != 0 ? ALARM : DIM, false);
        g.drawString(font, HoistText.msg("hoist.speed", HoistText.num(menu.get(HoistMenu.D_LIMIT_DMS) / 10.0)),
                PAD + 110, y, DIM, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }
}
