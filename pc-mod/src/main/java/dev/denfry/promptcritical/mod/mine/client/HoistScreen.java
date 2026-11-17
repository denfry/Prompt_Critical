package dev.denfry.promptcritical.mod.mine.client;

import dev.denfry.promptcritical.mod.PromptCritical;
import dev.denfry.promptcritical.mod.mine.HoistMenu;
import dev.denfry.promptcritical.mod.mine.HoistText;
import dev.denfry.promptcritical.mod.panel.PanelLayout;
import dev.denfry.promptcritical.mod.panel.client.PanelArt;
import dev.denfry.promptcritical.mod.panel.client.PanelButton;
import dev.denfry.promptcritical.sim.mining.HoistDuty;
import dev.denfry.promptcritical.sim.mining.HoistState;
import dev.denfry.promptcritical.sim.mining.HoistUnit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Щит машиниста подъёма на нарисованном фоне (art/blockbench/mining/mine_hoist.author.js, {@code HST.GUI}): слева
 * столбчатый указатель глубины с рисками посадок и курсором клети, в середине указатель скорости клети и ваттметр
 * вала барабана, под ними табло состояния, груза и каната; справа — кнопки посадок, внизу — стоп, сброс и предел
 * скорости.
 */
public final class HoistScreen extends AbstractContainerScreen<HoistMenu> {
    private static final ResourceLocation ART = PromptCritical.id("textures/gui/panel/mine_hoist.png");
    private static final int W = 256, H = 224;
    /** Шлиц указателя глубины на фоне; шкала внутри с полями по 5 px сверху и снизу. */
    private static final int COL_X = 10, COL_Y = 26, COL_W = 26, COL_H = 180;
    private static final int MID_X = 44, MID_W = 130, WIN_Y = 104, LINE = 14, LINES = 6;
    private static final int LAND_X = 180, LAND_W = 68, LAND_ROW = 12, CTRL_Y = 200;
    private static final int TEXT = 0xFFE8DFC4, DIM = 0xFFB0A890, ALARM = 0xFFFF5848;
    private static final int ENAMEL = 0xFFDED8C4, INK = 0xFF2A2824, LANDING = 0xFFA02820, TARGET = 0xFF38B858,
            BRASS = 0xFFC8A050, BRASS_DARK = 0xFF7A5A28;
    private static final double MAX_SPEED_MS = 12;

    private final int[] landings;
    private final PanelButton[] landingButtons;

    public HoistScreen(HoistMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.landings = menu.landings();
        this.landingButtons = new PanelButton[landings.length];
        this.imageWidth = W;
        this.imageHeight = H;
    }

    @Override
    protected void init() {
        super.init();
        for (int i = 0; i < landings.length; i++) {
            int id = i;
            landingButtons[i] = addRenderableWidget(new PanelButton(leftPos + LAND_X, topPos + COL_Y + i * LAND_ROW, LAND_W,
                    LAND_ROW - 1, HoistText.landingShort(landings, i), HoistText.landing(landings, i), () -> click(id)));
        }
        int x = leftPos + MID_X, y = topPos + CTRL_Y;
        addRenderableWidget(new PanelButton(x, y, 40, 16, HoistText.msg("hoist.stop"), null, () -> click(HoistMenu.BUTTON_STOP)));
        addRenderableWidget(new PanelButton(x + 42, y, 48, 16, HoistText.msg("hoist.reset"), null, () -> click(HoistMenu.BUTTON_RESET)));
        addRenderableWidget(new PanelButton(x + 92, y, 18, 16, Component.literal("−"), null, () -> click(HoistMenu.BUTTON_SLOWER)));
        addRenderableWidget(new PanelButton(x + 112, y, 18, 16, Component.literal("+"), null, () -> click(HoistMenu.BUTTON_FASTER)));
    }

    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        int target = menu.get(HoistMenu.D_TARGET), at = menu.get(HoistMenu.D_AT);
        for (int i = 0; i < landingButtons.length; i++) {
            Component name = HoistText.landingShort(landings, i);
            landingButtons[i].setMessage(i == target ? Component.literal("» ").append(name)
                    : i == at ? Component.literal("● ").append(name) : name);
        }
    }

    private double depthM() {
        return menu.get(HoistMenu.D_DEPTH_DM) / 10.0;
    }

    private int shaftDepth() {
        return landings.length == 0 ? 0 : landings[landings.length - 1];
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        PanelArt.art(g, ART, leftPos, topPos, W, H);
        PanelArt.plate(g, font, title, leftPos + W / 2, topPos + 5, Math.min(font.width(title), W - 22));
        depthIndicator(g, leftPos + COL_X, topPos + COL_Y);

        double speed = Math.abs(menu.get(HoistMenu.D_SPEED_CMS) / 100.0), limit = menu.get(HoistMenu.D_LIMIT_DMS) / 10.0;
        PanelLayout.Dial speedDial = new PanelLayout.Dial(MID_X, COL_Y, true, 0, 1, MAX_SPEED_MS, limit > 0 ? limit : Double.NaN, 1,
                HoistText.msg("hoist.unit.ms"), HoistText.msg("hoist.dial.speed"), null);
        PanelArt.dial(g, font, speedDial, speed, leftPos + MID_X, topPos + COL_Y, 72);
        int kw = menu.get(HoistMenu.D_POWER_KW), avail = menu.get(HoistMenu.D_AVAILABLE_KW);
        double max = Math.max(HoistUnit.RATED_W / 1000, avail);
        PanelLayout.Dial powerDial = new PanelLayout.Dial(MID_X + 76, COL_Y + 8, false, 0, 1, max, avail > 0 ? avail : Double.NaN, 0,
                HoistText.msg("hoist.unit.kw"), HoistText.msg("hoist.dial.power"), null);
        PanelArt.dial(g, font, powerDial, kw, leftPos + MID_X + 76, topPos + COL_Y + 8, 58);

        for (int i = 0; i < LINES; i++) PanelArt.window(g, font, Component.empty(), leftPos + MID_X, topPos + WIN_Y + i * LINE, MID_W);
    }

    /** Столбчатый указатель: эмалевая шкала, деления через десятую глубины, риски посадок, латунный курсор клети. */
    private void depthIndicator(GuiGraphics g, int x, int y) {
        int y0 = y + 5, y1 = y + COL_H - 5, sx0 = x + 3, sx1 = x + 15;
        g.fill(sx0, y0 - 2, sx1, y1 + 3, ENAMEL);
        for (int k = 0; k <= 10; k++) {
            int yy = y0 + (y1 - y0) * k / 10;
            g.fill(sx0, yy, sx0 + (k % 5 == 0 ? 8 : 5), yy + 1, INK);
        }
        int depth = shaftDepth(), target = menu.get(HoistMenu.D_TARGET);
        if (depth <= 0) return;
        for (int i = 0; i < landings.length; i++) {
            int yy = y0 + (int) Math.round((y1 - y0) * (double) landings[i] / depth);
            g.fill(sx0, yy, sx1 + 2, yy + 1, i == target ? TARGET : LANDING);
        }
        int cy = y0 + (int) Math.round((y1 - y0) * Math.max(0, Math.min(1, depthM() / depth)));
        g.fill(sx1 + 1, cy - 2, x + COL_W - 2, cy + 3, BRASS);
        g.fill(sx1, cy - 1, sx1 + 1, cy + 2, BRASS);
        g.fill(sx1 - 1, cy, sx1, cy + 1, BRASS);
        g.fill(sx1 + 1, cy + 2, x + COL_W - 2, cy + 3, BRASS_DARK);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int x = MID_X + 4, y = WIN_Y + 3, room = MID_W - 8;
        int state = menu.get(HoistMenu.D_STATE);
        if (state < 0) {
            HoistText.Problem p = HoistText.Problem.values()[Math.max(0, Math.min(HoistText.Problem.values().length - 1,
                    menu.get(HoistMenu.D_PROBLEM)))];
            g.drawWordWrap(font, HoistText.problem(p == HoistText.Problem.NONE ? HoistText.Problem.NO_ROPE : p), x, y, room, ALARM);
            return;
        }
        HoistState s = HoistState.values()[state];
        PanelArt.text(g, font, HoistText.state(s), x, y, room, s.tripped() || s.broken() ? ALARM : TEXT);
        y += LINE;
        PanelArt.text(g, font, HoistText.msg("hoist.position", HoistText.num(depthM()),
                HoistText.num(menu.get(HoistMenu.D_SPEED_CMS) / 100.0)), x, y, room, TEXT);
        y += LINE;
        HoistDuty duty = HoistDuty.values()[Math.max(0, Math.min(2, menu.get(HoistMenu.D_DUTY)))];
        PanelArt.text(g, font, HoistText.msg("hoist.load_short", menu.get(HoistMenu.D_PAYLOAD_KG), HoistText.duty(duty)), x, y, room, DIM);
        y += LINE;
        double sf = menu.get(HoistMenu.D_SF_X10) / 10.0, need = menu.get(HoistMenu.D_SF_NEED_X10) / 10.0;
        PanelArt.text(g, font, HoistText.msg("hoist.safety", HoistText.num(sf), HoistText.num(need)), x, y, room, sf < need ? ALARM : DIM);
        y += LINE;
        boolean worn = menu.get(HoistMenu.D_ROPE_WEAR) != 0;
        PanelArt.text(g, font, worn ? HoistText.msg("hoist.rope_wear") : HoistText.msg("hoist.rope", menu.get(HoistMenu.D_ROPE_MM)),
                x, y, room, worn ? ALARM : DIM);
        y += LINE;
        PanelArt.text(g, font, HoistText.msg("hoist.speed", HoistText.num(menu.get(HoistMenu.D_LIMIT_DMS) / 10.0)), x, y, room, DIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (mx >= COL_X && mx < COL_X + COL_W && my >= COL_Y && my < COL_Y + COL_H) {
            g.renderTooltip(font, HoistText.msg("hoist.depth_tip", HoistText.num(depthM()), shaftDepth()), mouseX, mouseY);
        } else if (mx >= MID_X + 76 && mx < MID_X + 124 && my >= COL_Y + 8 && my < COL_Y + 56) {
            g.renderTooltip(font, HoistText.msg("hoist.power_tip", menu.get(HoistMenu.D_POWER_KW), menu.get(HoistMenu.D_AVAILABLE_KW)),
                    mouseX, mouseY);
        } else {
            renderTooltip(g, mouseX, mouseY);
        }
    }
}
