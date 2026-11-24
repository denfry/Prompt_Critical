package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * {@code grid_consumer} (07 §7.6.1): ТП посёлка на 6 кВ — смешанная нагрузка города (ZIP 0,3/0,2/0,5, cos φ 0,9,
 * D_f 1,5). Мощность задаёт игрок; модуль {@code relay_ufls} ставит её в очередь АЧР с ЧАПВ.
 */
public final class ConsumerUnit extends GridUnit {
    public static final int[] POWER_KW = {50, 100, 200, 300, 500, 1000, 2000};

    int power = 2;
    /** Мощность, заданная машиной (печь, выпарной аппарат), Вт; NaN — ступень игрока из {@link #POWER_KW}. */
    double machineW = Double.NaN;
    boolean switchedOn = true;
    LoadId load;
    /** Нагрузка была отключена автоматикой (АЧР, 27) и ещё не вернулась. */
    boolean shed;
    int shedCount;
    /** Множитель суточного графика (07 §7.6.4) $\hat p(h)\,k_T\,(1+\xi)$ и его шум ξ; у нагрузки машины не действует. */
    double profileFactor = 1, xi;

    ConsumerUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    public double demandW() {
        return Double.isNaN(machineW) ? POWER_KW[power] * 1e3 : machineW;
    }

    /** Мощность сейчас: договорная по суточному графику (07 §7.6.4) или мощность машины, Вт. */
    public double targetW() {
        return Double.isNaN(machineW) ? demandW() * profileFactor : machineW;
    }

    /** Множитель суточного графика, доли договорной мощности. */
    public double profileFactor() {
        return Double.isNaN(machineW) ? profileFactor : 1;
    }

    /**
     * Нагрузка машины: потребитель — это её сетевой ввод, мощность меняется по ходу работы без пересборки сети
     * (ниже 1 % изменения — не трогаем).
     */
    public void setDemandW(double w) {
        double v = Math.max(0, w);
        if (!Double.isNaN(machineW) && Math.abs(v - machineW) <= 0.01 * Math.max(machineW, 1)) return;
        machineW = v;
        if (load != null && owner.ready()) owner.grid().setLoadPowerW(load, v);
    }

    public void cycleDemand() {
        power = (power + 1) % POWER_KW.length;
        changed();
    }

    public void setDemandKw(int kw) {
        for (int i = 0; i < POWER_KW.length; i++) {
            if (POWER_KW[i] == kw && i != power) {
                power = i;
                changed();
            }
        }
    }

    public boolean switchedOn() {
        return switchedOn;
    }

    /** Рубильник потребителя. */
    public void setSwitchedOn(boolean on) {
        switchedOn = on;
        shed = false;
        if (load != null && owner.ready()) owner.grid().setLoadConnected(load, on);
    }

    /** Потребитель под напряжением и получает мощность. */
    public boolean energized() {
        return load != null && owner.ready() && owner.grid().loadConnected(load) && owner.voltageV(node(0)) > 0;
    }

    /** Напряжение на вводе, о.е. 6,3 кВ; 0 — ввод не в сети или отключён. */
    public double inputPu() {
        return energized() ? owner.voltageV(node(0)) / VoltageClass.MV_6_3.volts : 0;
    }

    /** Частота на вводе, Гц; 0 — ввод не в сети. */
    public double frequencyHz() {
        return node(0) == NO_NODE || owner == null || !owner.ready() ? 0 : owner.frequencyHz(node(0));
    }

    /** Отключён АЧР (или 27) и ждёт ЧАПВ. */
    public boolean shed() {
        return shed;
    }

    /** Сколько раз автоматика отключала потребителя. */
    public int shedCount() {
        return shedCount;
    }

    public double powerW() {
        return load != null && owner.ready() ? owner.grid().loadPowerW(load) : 0;
    }

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE) return;
        load = b.grid.addLoad(LoadModel.typical(LoadKind.NPC_CONSUMER, targetW()), b.bus(node(0)));
        b.load(load, this);
        b.grid.setLoadConnected(load, switchedOn && !shed);
    }

    @Override
    void unbind() {
        load = null;
    }

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putInt("power", power);
        out.putBoolean("on", switchedOn);
        out.putBoolean("shed", shed);
        out.putInt("shed_count", shedCount);
        if (!Double.isNaN(machineW)) out.putDouble("machine_w", machineW);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        power = Math.floorMod(in.getInt("power"), POWER_KW.length);
        switchedOn = in.getBoolean("on");
        shed = in.getBoolean("shed");
        shedCount = in.optInt("shed_count", 0);
        machineW = in.optDouble("machine_w", Double.NaN);
    }
}
