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
    boolean switchedOn = true;
    LoadId load;
    /** Нагрузка была отключена автоматикой (АЧР, 27) и ещё не вернулась. */
    boolean shed;
    int shedCount;

    ConsumerUnit(SimId id) {
        super(id, 1);
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return VoltageClass.MV_6_3;
    }

    public double demandW() {
        return POWER_KW[power] * 1e3;
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
        load = b.grid.addLoad(LoadModel.typical(LoadKind.NPC_CONSUMER, demandW()), b.bus(node(0)));
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
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        power = Math.floorMod(in.getInt("power"), POWER_KW.length);
        switchedOn = in.getBoolean("on");
        shed = in.getBoolean("shed");
        shedCount = in.optInt("shed_count", 0);
    }
}
