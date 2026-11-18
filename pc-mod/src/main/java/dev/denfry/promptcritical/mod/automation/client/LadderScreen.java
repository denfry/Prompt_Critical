package dev.denfry.promptcritical.mod.automation.client;

import dev.denfry.promptcritical.mod.automation.AutomationText;
import dev.denfry.promptcritical.mod.automation.PlcNet;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Cell;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Coil;
import dev.denfry.promptcritical.sim.automation.plc.LadderGrid.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Графический редактор LD (03d §3d.5.10): ступени — сетка {@value LadderGrid#COLUMNS} клеток контактов и столбец
 * катушки, основная линия и две параллельные ветви. Палитра ставит элемент в клетку, под сеткой правится операнд
 * выбранной клетки (адрес или имя блока и уставка). Онлайн — «поток энергии»: клетки под питанием обведены тёмно-синим
 * (в духе ISA-101, без красного и зелёного) по состояниям входов и выходов ПЛК. Сетка переводится в текст LD
 * ({@link LadderGrid#toText}) — его и загружает кнопка «Загрузить в ПЛК»; «Текст» возвращает в текстовый программатор.
 */
public final class LadderScreen extends Screen {
    private static final int CELL_W = 38, ROW_H = 18, RUNG_GAP = 6, LEFT = 14, TOP = 44, POLL_TICKS = 10;
    private static final int RAIL = 0xFF9090A0, WIRE = 0xFF707070, POWER = 0xFF1A3A8A, SELECT = 0xFFE0C040, TEXT = 0xFFE0E0E0,
            DIM = 0xFF909090, CELL_BG = 0xFF202024, ERROR = 0xFFE04030;

    private final BlockPos pos;
    private final LadderGrid grid;
    private final String loaded;
    private PlcNet.Status status;
    private Kind tool = Kind.NO;
    private Coil coilTool;
    private boolean erase;
    private int selRung = -1, selRow, selCol;
    private boolean selCoil;
    private int scroll, ticks;
    private EditBox operand, preset;
    private String error = "", notice;

    LadderScreen(BlockPos pos, LadderGrid grid, String loaded, PlcNet.Status status, String notice) {
        super(AutomationText.msg(AutomationText.LD_TITLE));
        this.pos = pos;
        this.grid = grid;
        this.loaded = loaded;
        this.status = status;
        this.notice = notice;
        if (grid.rungs().isEmpty()) grid.addRung();
    }

    /** Открыть из программатора: текст LD — в сетку; другой язык или непредставимый текст — пустая сетка. */
    static void open(BlockPos pos, String text, String loaded, PlcNet.Status status) {
        LadderGrid g;
        String notice = null;
        try {
            g = text.strip().startsWith("(*LD*)") ? LadderGrid.fromText(text) : new LadderGrid();
            if (!text.strip().startsWith("(*LD*)") && !text.isBlank()) notice = AutomationText.LD_NOT_LD;
        } catch (IllegalArgumentException e) {
            g = new LadderGrid();
            notice = AutomationText.LD_NOT_GRID;
        }
        Minecraft.getInstance().setScreen(new LadderScreen(pos, g, loaded, status, notice));
    }

    void update(PlcNet.Status s) {
        status = s;
    }

    public BlockPos pos() {
        return pos;
    }

    // ------------------------------------------------------------------ виджеты

    @Override
    protected void init() {
        int x = LEFT, y = 22;
        for (Kind k : Kind.values()) {
            if (k == Kind.EMPTY) continue;
            addRenderableWidget(Button.builder(Component.literal(label(k)), b -> {
                tool = k;
                coilTool = null;
                erase = false;
            }).bounds(x, y, 30, 18).build());
            x += 32;
        }
        for (Coil c : Coil.values()) {
            if (c == Coil.NONE) continue;
            addRenderableWidget(Button.builder(Component.literal(coilLabel(c)), b -> {
                coilTool = c;
                erase = false;
            }).bounds(x, y, 30, 18).build());
            x += 32;
        }
        addRenderableWidget(Button.builder(AutomationText.msg(AutomationText.LD_ERASE), b -> erase = true).bounds(x, y, 46, 18).build());
        x += 48;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            if (grid.rungs().size() < LadderGrid.MAX_RUNGS) grid.addRung();
        }).bounds(x, y, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("−"), b -> {
            if (selRung >= 0 && grid.rungs().size() > 1) {
                grid.rungs().remove(selRung);
                selRung = -1;
            }
        }).bounds(x + 20, y, 18, 18).build());

        int by = height - 26;
        operand = addRenderableWidget(new EditBox(font, LEFT, by - 24, 120, 18, Component.empty()));
        operand.setMaxLength(24);
        operand.setResponder(s -> edit(s, null));
        preset = addRenderableWidget(new EditBox(font, LEFT + 126, by - 24, 70, 18, Component.empty()));
        preset.setMaxLength(12);
        preset.setResponder(s -> edit(null, s));
        addRenderableWidget(Button.builder(AutomationText.msg(AutomationText.PLC_UPLOAD), b -> upload()).bounds(LEFT, by, 120, 20).build());
        addRenderableWidget(Button.builder(AutomationText.msg(AutomationText.LD_TEXT), b -> toText()).bounds(LEFT + 126, by, 80, 20).build());
        syncEditors();
    }

    private static String label(Kind k) {
        return switch (k) {
            case NO -> "┤ ├";
            case NC -> "┤/├";
            default -> k.name();
        };
    }

    private static String coilLabel(Coil c) {
        return switch (c) {
            case OTE -> "( )";
            case OTL -> "(S)";
            case OTU -> "(R)";
            case RES -> "RES";
            case NONE -> "";
        };
    }

    private void edit(String op, String pre) {
        if (selRung < 0 || selRung >= grid.rungs().size()) return;
        LadderGrid.Rung r = grid.rungs().get(selRung);
        if (selCoil) {
            if (op != null) r.coilOperand = op.strip();
            return;
        }
        Cell c = r.cells[selRow][selCol];
        if (c.empty()) return;
        r.cells[selRow][selCol] = new Cell(c.kind(), op != null ? op.strip() : c.operand(), pre != null ? pre.strip() : c.preset());
    }

    private void syncEditors() {
        if (operand == null) return;
        String op = "", pre = "";
        boolean block = false;
        if (selRung >= 0 && selRung < grid.rungs().size()) {
            LadderGrid.Rung r = grid.rungs().get(selRung);
            if (selCoil) op = r.coilOperand;
            else {
                Cell c = r.cells[selRow][selCol];
                op = c.operand();
                pre = c.preset();
                block = c.kind().block();
            }
        }
        operand.setValue(op);
        preset.setValue(pre);
        preset.setVisible(block);
    }

    // ------------------------------------------------------------------ команды

    private String text() {
        try {
            error = "";
            return grid.toText();
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
            return null;
        }
    }

    private void upload() {
        String src = text();
        if (src == null) return;
        PacketDistributor.sendToServer(new PlcNet.Command(pos, true, src, status.runKey()));
    }

    private void toText() {
        String src = text();
        if (src == null) return;
        PlcScreen.openWith(pos, src, loaded, status);
    }

    // ------------------------------------------------------------------ мышь

    private int rungTop(int i) {
        return TOP + i * (LadderGrid.ROWS * ROW_H + RUNG_GAP) - scroll;
    }

    private int coilX() {
        return LEFT + 10 + LadderGrid.COLUMNS * CELL_W + 8;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        for (int i = 0; i < grid.rungs().size(); i++) {
            int top = rungTop(i);
            if (my < top || my >= top + LadderGrid.ROWS * ROW_H || my < TOP - 4 || my > height - 56) continue;
            LadderGrid.Rung r = grid.rungs().get(i);
            int row = (int) ((my - top) / ROW_H);
            if (mx >= coilX() && mx < coilX() + CELL_W && row == 0) {
                selRung = i;
                selCoil = true;
                if (coilTool != null) r.coil = coilTool;
                if (erase) r.coil = Coil.NONE;
                syncEditors();
                return true;
            }
            int col = (int) ((mx - LEFT - 10) / CELL_W);
            if (mx < LEFT + 10 || col < 0 || col >= LadderGrid.COLUMNS) continue;
            selRung = i;
            selRow = row;
            selCol = col;
            selCoil = false;
            if (erase) r.cells[row][col] = Cell.EMPTY;
            else if (coilTool == null && (r.cells[row][col].empty() || r.cells[row][col].kind() != tool)) {
                String keep = r.cells[row][col].operand();
                r.cells[row][col] = new Cell(tool, keep.isEmpty() ? (tool.block() ? "T" + (i + 1) : "%IX0.0") : keep,
                        tool.block() ? (tool == Kind.CTU ? "3" : "T#5S") : "");
            }
            syncEditors();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int max = Math.max(0, grid.rungs().size() * (LadderGrid.ROWS * ROW_H + RUNG_GAP) - (height - 56 - TOP));
        scroll = Math.max(0, Math.min(max, scroll - (int) (dy * 20)));
        return true;
    }

    @Override
    public void tick() {
        if (++ticks % POLL_TICKS == 0) PacketDistributor.sendToServer(new PlcNet.Poll(pos));
    }

    // ------------------------------------------------------------------ рисование

    /** Состояние операнда по лампам входов и выходов ПЛК; остальное — неизвестно. */
    private Boolean state(String operand) {
        int io = status.io();
        if (operand.startsWith("%IX0.") || operand.startsWith("%QX0.")) {
            try {
                int bit = Integer.parseInt(operand.substring(5));
                if (bit < 0 || bit > 7) return null;
                return ((operand.startsWith("%IX") ? io : io >> 8) >> bit & 1) == 1;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, LEFT, 8, TEXT);
        boolean online = status.mode() == 2;
        g.enableScissor(0, TOP - 4, width, height - 56);
        for (int i = 0; i < grid.rungs().size(); i++) drawRung(g, i, online);
        g.disableScissor();
        int y = height - 74;
        if (!error.isEmpty()) g.drawString(font, error, LEFT + 210, y + 4, ERROR);
        else if (notice != null) g.drawString(font, AutomationText.msg(notice), LEFT + 210, y + 4, DIM);
        if (!status.errors().isEmpty()) {
            PlcNet.Error e = status.errors().getFirst();
            g.drawString(font, AutomationText.msg(AutomationText.PLC_ERROR, e.line(), AutomationText.msg(AutomationText.plcError(e.kind())),
                    e.token()), LEFT + 210, y + 16, ERROR);
        }
        g.drawString(font, AutomationText.msg(online ? AutomationText.LD_ONLINE : AutomationText.LD_OFFLINE), LEFT + 210, height - 20, DIM);
    }

    private void drawRung(GuiGraphics g, int i, boolean online) {
        LadderGrid.Rung r = grid.rungs().get(i);
        int top = rungTop(i), x0 = LEFT + 10, cx = coilX();
        boolean[][] flow = online ? LadderGrid.flow(r, this::state) : null;
        g.drawString(font, String.valueOf(i + 1), LEFT - 8, top + 5, DIM);
        g.fill(x0 - 2, top, x0, top + LadderGrid.ROWS * ROW_H - 4, RAIL);
        g.fill(cx + CELL_W + 4, top, cx + CELL_W + 6, top + ROW_H, RAIL);
        for (int row = 0; row < LadderGrid.ROWS; row++) {
            int[] span = row == 0 ? null : r.span(row);
            for (int c = 0; c < LadderGrid.COLUMNS; c++) {
                int x = x0 + c * CELL_W, y = top + row * ROW_H, mid = y + ROW_H / 2;
                Cell cell = r.cells[row][c];
                boolean powered = flow != null && flow[row][c];
                boolean wire = row == 0 || span != null && c >= span[0] && c <= span[1];
                if (wire) g.fill(x, mid, x + CELL_W, mid + 1, powered ? POWER : WIRE);
                if (span != null && c == span[0]) g.fill(x, top + ROW_H / 2, x + 1, mid + 1, WIRE);
                if (span != null && c == span[1]) g.fill(x + CELL_W - 1, top + ROW_H / 2, x + CELL_W, mid + 1, WIRE);
                if (!cell.empty()) {
                    g.fill(x + 4, y + 2, x + CELL_W - 4, y + ROW_H - 2, CELL_BG);
                    if (powered) g.renderOutline(x + 3, y + 1, CELL_W - 6, ROW_H - 2, POWER);
                    g.drawString(font, label(cell.kind()), x + 6, y + 1, TEXT, false);
                    String op = cell.operand().length() > 6 ? cell.operand().substring(cell.operand().length() - 6) : cell.operand();
                    g.drawString(font, op, x + 6, y + 9, DIM, false);
                }
                if (selRung == i && !selCoil && selRow == row && selCol == c) g.renderOutline(x + 1, y, CELL_W - 2, ROW_H, SELECT);
            }
        }
        int mid = top + ROW_H / 2;
        boolean coilPower = flow != null && flow[LadderGrid.ROWS][0];
        g.fill(x0 + LadderGrid.COLUMNS * CELL_W, mid, cx, mid + 1, coilPower ? POWER : WIRE);
        if (r.coil != Coil.NONE) {
            g.drawString(font, coilLabel(r.coil), cx + 4, top + 1, coilPower ? 0xFF6080FF : TEXT, false);
            g.drawString(font, r.coilOperand, cx + 4, top + 9, DIM, false);
        }
        if (selRung == i && selCoil) g.renderOutline(cx, top, CELL_W, ROW_H, SELECT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
