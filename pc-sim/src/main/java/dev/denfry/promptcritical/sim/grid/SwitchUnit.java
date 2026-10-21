package dev.denfry.promptcritical.sim.grid;

import dev.denfry.promptcritical.sim.core.SimId;
import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Коммутационный аппарат между двумя узлами (07 §7.4.6): выключатель ячейки КРУ 6 кВ, вводной автомат щита 0,4 кВ,
 * плавкая вставка щитка или разъединитель. Вывод 0 — сторона шины (ввода), вывод 1 — присоединение. Сопротивление
 * аппарата пренебрежимо мало: ветвь 10⁻⁴ о.е.
 */
public final class SwitchUnit extends GridUnit {
    public enum Kind {
        /** {@code switchgear_cell}: масляный выключатель I1 с автоматом §7.4.7 и шкафом РЗА. */
        MV_BREAKER,
        /** {@code lv_switchboard}: воздушный автомат с расцепителем C. */
        LV_BREAKER,
        /** {@code lv_distribution_box}: плавкая вставка {@code lv_fuse}. */
        FUSE,
        /** {@code disconnector}: видимый разрыв без дугогашения, заземляющие ножи на стороне присоединения. */
        DISCONNECTOR
    }

    /** Ток, выше которого разъединитель под нагрузкой тянет дугу (§7.4.6), А. */
    public static final double ARC_CURRENT_A = 5;
    /** Вероятность перехода дуги в междуфазное КЗ [BALANCE]. */
    public static final double ARC_FAULT_PROBABILITY = 0.5;
    /** Номиналы автомата щита и вставки, А. */
    public static final int[] LV_RATINGS = {16, 25, 40, 63, 100, 160, 250, 400, 630, 1000, 1600};
    static final double SWITCH_X_PU = 1e-4;

    final Kind kind;
    int rating = 7;
    boolean fuseInstalled = true, earthed, interlock, controlPower = true;
    /** Контакты разомкнуты/замкнуты, пока сети ещё нет (новая ячейка, разрыв цепи). */
    boolean closedIdle;
    BranchId branch;
    double[] carried;
    double energyForwardJ, energyReverseJ;
    boolean lastArc;
    int trips;

    SwitchUnit(SimId id, Kind kind) {
        super(id, 2);
        this.kind = kind;
        closedIdle = kind == Kind.FUSE;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    VoltageClass terminalClass(int terminal) {
        return kind == Kind.MV_BREAKER ? VoltageClass.MV_6_3 : VoltageClass.LV_0_4;
    }

    @Override
    boolean definesClass(int terminal) {
        return kind == Kind.MV_BREAKER || kind == Kind.LV_BREAKER;
    }

    // --- уставки ---

    /** Номинал автомата или вставки, А. */
    public int ratedA() {
        return LV_RATINGS[rating];
    }

    public void cycleRating() {
        rating = (rating + 1) % LV_RATINGS.length;
        changed();
    }

    public void setRatedA(int amps) {
        for (int i = 0; i < LV_RATINGS.length; i++) {
            if (LV_RATINGS[i] == amps && rating != i) {
                rating = i;
                changed();
            }
        }
    }

    /**
     * Оперативный ток привода (03d §3d.13.4): без него выключатель не исполняет ни команд, ни отключений РЗА
     * ({@link BreakerResponse#NO_CONTROL_POWER}). Пока цепь «+/−» ячейки не подключена к автоматике — встроенный.
     */
    public void setControlPower(boolean dc) {
        if (controlPower == dc) return;
        controlPower = dc;
        if (branch != null && owner.ready() && (kind == Kind.MV_BREAKER || kind == Kind.LV_BREAKER))
            owner.grid().setBreakerControlPower(branch, dc);
    }

    public boolean controlPower() {
        return controlPower;
    }

    public boolean interlocked() {
        return interlock;
    }

    /** Модуль оперативной блокировки {@code interlock_module} в разъединителе. */
    public void setInterlock(boolean installed) {
        interlock = installed;
    }

    // --- управление ---

    /** Команда «вкл»/«откл» оператора (§7.4.7); у разъединителя — операция ножами. */
    public BreakerResponse operate(boolean close) {
        lastArc = false;
        if (kind == Kind.FUSE) return BreakerResponse.UNAVAILABLE;
        if (kind == Kind.DISCONNECTOR) return operateDisconnector(close);
        if (branch == null || !owner.ready()) {
            closedIdle = close;
            if (carried != null) {
                carried[0] = close ? 1 : 0;
                carried[1] = (close ? BreakerState.CLOSED : BreakerState.OPEN).ordinal();
            }
            return BreakerResponse.ACCEPTED;
        }
        return owner.grid().operateBreaker(branch, close);
    }

    private BreakerResponse operateDisconnector(boolean close) {
        if (close && earthed) return BreakerResponse.INTERLOCK;
        if (interlock && owner.adjacentBreakerClosed(this)) return BreakerResponse.INTERLOCK;
        if (branch == null || !owner.ready()) {
            closedIdle = close;
            if (carried != null) carried[0] = close ? 1 : 0;
            return BreakerResponse.ACCEPTED;
        }
        PowerGrid g = owner.grid();
        if (!close && g.isBreakerClosed(branch) && g.branchCurrentA(branch) > ARC_CURRENT_A) {
            lastArc = true;
            if (owner.random().nextDouble() < ARC_FAULT_PROBABILITY)
                g.applyFault(owner.busOf(node(0)), FaultType.LINE_LINE, 0, true);
        }
        g.setBreaker(branch, close);
        return BreakerResponse.ACCEPTED;
    }

    /** Разъединитель открыт под током: была дуга (ожог рядом, §7.4.6). Сбрасывается следующей операцией. */
    public boolean lastOperationArced() {
        return lastArc;
    }

    public boolean earthed() {
        return earthed;
    }

    /**
     * Заземляющие ножи на стороне присоединения: только при отключённом разъединителе. Включение на линию под
     * напряжением — трёхфазное КЗ до снятия заземления.
     */
    public boolean setEarthed(boolean on) {
        if (kind != Kind.DISCONNECTOR || on && closed()) return false;
        if (earthed == on) return true;
        earthed = on;
        changed();
        return true;
    }

    public boolean fuseInstalled() {
        return fuseInstalled;
    }

    /** Вставить новую вставку {@code lv_fuse} (перегоревшая выбрасывается). */
    public void installFuse() {
        fuseInstalled = true;
        if (carried != null) carried[9] = 0;
        if (branch != null && owner.ready()) {
            PowerGrid g = owner.grid();
            if (g.fuseBlown(branch)) g.replaceFuse(branch);
            g.setBreaker(branch, true);
        } else {
            closedIdle = true;
        }
    }

    /** Вынуть вставку (видимый разрыв щитка). */
    public void removeFuse() {
        fuseInstalled = false;
        closedIdle = false;
        if (carried != null) carried[0] = 0;
        if (branch != null && owner.ready()) owner.grid().setBreaker(branch, false);
    }

    // --- показания ---

    public boolean closed() {
        if (branch != null && owner.ready()) return owner.grid().isBreakerClosed(branch);
        return carried != null ? carried[0] != 0 : closedIdle;
    }

    /** Автомат §7.4.7; у вставки и разъединителя — {@code null}. */
    public BreakerState breakerState() {
        if (kind != Kind.MV_BREAKER && kind != Kind.LV_BREAKER) return null;
        if (branch != null && owner.ready()) return owner.grid().breakerState(branch);
        return closed() ? BreakerState.CLOSED : BreakerState.OPEN;
    }

    public boolean fuseBlown() {
        if (kind != Kind.FUSE) return false;
        if (branch != null && owner.ready()) return owner.grid().fuseBlown(branch);
        return carried != null && carried[9] != 0;
    }

    public double currentA() {
        return branch != null && owner.ready() ? owner.grid().branchCurrentA(branch) : 0;
    }

    /** Мощность со стороны шины в присоединение, Вт. */
    public double powerW() {
        return branch != null && owner.ready() ? owner.grid().branchPowerFromW(branch) : 0;
    }

    /** Напряжение вывода, В (0 — без источника). */
    public double voltageV(int terminal) {
        return owner.voltageV(node(terminal));
    }

    public double frequencyHz(int terminal) {
        return owner.frequencyHz(node(terminal));
    }

    /**
     * Синхроноскоп на аппарате: «генератор» — сторона присоединения (вывод 1), «сеть» — шина (вывод 0);
     * {@code null} — ветви нет или одна из сторон без напряжения.
     */
    public SyncScope syncScope() {
        if (branch == null || !owner.ready()) return null;
        if (voltageV(0) <= 0 || voltageV(1) <= 0) return null;
        return owner.grid().syncScope(branch, false);
    }

    /** Учтённая энергия: из шины в присоединение и обратно, кВт·ч. */
    public double energyForwardKwh() {
        return energyForwardJ / 3.6e6;
    }

    public double energyReverseKwh() {
        return energyReverseJ / 3.6e6;
    }

    /** Отключения защитой (реле, расцепитель, вставка) с постройки. */
    public int protectionTrips() {
        return trips;
    }

    // --- сборка ---

    @Override
    void build(GridSubsystem.Build b) {
        if (node(0) == NO_NODE || node(1) == NO_NODE || node(0) == node(1)) return;
        PowerGrid g = b.grid;
        branch = g.addBranch(new Branch.Cable(b.bus(node(0)), b.bus(node(1)), 0, SWITCH_X_PU, 0, 1e9, 0));
        b.branch(branch, this);
        switch (kind) {
            case MV_BREAKER -> g.attachBreaker(branch, BreakerSpec.oil());
            case LV_BREAKER -> {
                g.attachBreaker(branch, BreakerSpec.lvAir());
                g.attachTripUnit(branch, LvTripUnit.breaker(ratedA(), LvTripUnit.Curve.C, b.cfg));
            }
            case FUSE -> g.attachTripUnit(branch, LvTripUnit.fuse(ratedA(), b.cfg));
            case DISCONNECTOR -> {
            }
        }
        if (carried != null) {
            g.importBranch(branch, carried);
        } else {
            double[] s = new double[PowerGrid.BRANCH_STATE];
            boolean close = closedIdle && (kind != Kind.FUSE || fuseInstalled);
            s[0] = close ? 1 : 0;
            s[1] = (close ? BreakerState.CLOSED : BreakerState.OPEN).ordinal();
            s[4] = 20;
            g.importBranch(branch, s);
        }
        if (kind == Kind.FUSE && !fuseInstalled) g.setBreaker(branch, false);
        if (kind == Kind.MV_BREAKER || kind == Kind.LV_BREAKER) g.setBreakerControlPower(branch, controlPower);
        if (kind == Kind.DISCONNECTOR && earthed) g.applyFault(b.bus(node(1)), FaultType.THREE_PHASE, 0, false);
    }

    @Override
    void capture(PowerGrid g) {
        if (branch == null) return;
        if (carried == null) carried = new double[PowerGrid.BRANCH_STATE];
        g.exportBranch(branch, carried);
        closedIdle = carried[0] != 0;
    }

    @Override
    void unbind() {
        branch = null;
    }

    void meter(PowerGrid g, double dt) {
        if (branch == null) return;
        double p = g.branchPowerFromW(branch) * dt;
        if (p > 0) energyForwardJ += p;
        else energyReverseJ -= p;
    }

    // --- сохранение ---

    @Override
    void save(StateWriter out) {
        saveCommon(out);
        out.putInt("rating", rating);
        out.putBoolean("fuse", fuseInstalled);
        out.putBoolean("earthed", earthed);
        out.putBoolean("interlock", interlock);
        out.putBoolean("closed_idle", closedIdle);
        out.putDouble("energy_fwd_j", energyForwardJ);
        out.putDouble("energy_rev_j", energyReverseJ);
        out.putInt("trips", trips);
        if (carried != null) out.putDoubles("branch", carried, carried.length);
    }

    @Override
    void load(StateReader in) {
        loadCommon(in);
        rating = Math.floorMod(in.getInt("rating"), LV_RATINGS.length);
        fuseInstalled = in.getBoolean("fuse");
        earthed = in.getBoolean("earthed");
        interlock = in.getBoolean("interlock");
        closedIdle = in.getBoolean("closed_idle");
        energyForwardJ = in.getDouble("energy_fwd_j");
        energyReverseJ = in.getDouble("energy_rev_j");
        trips = in.optInt("trips", 0);
        carried = in.has("branch") ? in.getDoubles("branch") : null;
        if (carried != null && carried.length != PowerGrid.BRANCH_STATE) carried = null;
    }
}
