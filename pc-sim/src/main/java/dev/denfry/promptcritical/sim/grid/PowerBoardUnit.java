package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Розетка, силовой щит или ИБП ({@link PowerBoard}) на шине 0,4 кВ: одна нагрузка. Щит и розетки — двигательная
 * нагрузка {@code PROCESS}, ИБП — выпрямитель {@code RECTIFIER}; мощность задаёт модель щита каждый тик.
 */
public final class PowerBoardUnit extends GridUnit {
    PowerBoard board;
    LoadId load;
    /** Состояние из сохранения до первого паспорта от мира. */
    private PowerBoard.Kind savedKind;
    private PowerBoard pending;

    PowerBoardUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.LV_0_4;
    }

    /** Вид щита от мира; при смене вида модель создаётся заново. */
    public void configure(PowerBoard.Kind kind) {
        if (board != null && board.kind() == kind) return;
        if (pending != null && pending.kind() == kind) board = pending;
        else board = new PowerBoard(kind);
        pending = null;
        savedKind = null;
        changed();
    }

    public PowerBoard board() {
        return board;
    }

    /** Напряжение на вводе, о.е. 0,4 кВ. */
    public double inputPu() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.voltageV(node(0)) / VoltageClass.LV_0_4.volts;
    }

    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    /** Мощность из сети, Вт. */
    public double powerW() {
        return load != null && owner.ready() ? owner.grid().loadPowerW(load) : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        LoadKind k = board != null && board.kind() == PowerBoard.Kind.UPS ? LoadKind.RECTIFIER : LoadKind.PROCESS;
        load = b.grid.addLoad(LoadModel.typical(k, board != null ? board.gridW() : 0), b.bus(node(0)));
        b.grid.setLoadConnected(load, true);
    }

    @Override
    void unbind() {
        load = null;
    }

    /** Перед тиком сети: нулевая защита и батарея по своей шине, мощность — в нагрузку. */
    void beforeTick(PowerGrid g, double dt) {
        if (board == null) {
            if (load != null) g.setLoadPowerW(load, 0);
            return;
        }
        double w = board.step(dt, load == null ? 0 : inputPu(), load == null ? 0 : frequencyHz());
        if (load != null) g.setLoadPowerW(load, w);
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        PowerBoard b = board != null ? board : pending;
        if (b != null) {
            out.putString("kind", b.kind().name());
            out.beginObject("board");
            b.save(out);
            out.endObject();
        } else if (savedKind != null) {
            out.putString("kind", savedKind.name());
        }
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        board = null;
        pending = null;
        savedKind = null;
        String k = in.optString("kind", "");
        PowerBoard.Kind kind = null;
        for (PowerBoard.Kind v : PowerBoard.Kind.values()) if (v.name().equals(k)) kind = v;
        if (kind == null) return;
        savedKind = kind;
        if (in.has("board")) {
            pending = new PowerBoard(kind);
            in.beginObject("board");
            pending.load(in);
            in.endObject();
        }
    }
}
