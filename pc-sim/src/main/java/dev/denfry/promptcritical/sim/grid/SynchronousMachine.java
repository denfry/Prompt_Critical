package dev.denfry.promptcritical.sim.grid;

import java.util.Objects;

/**
 * Синхронная машина, модель 3-го порядка (07 §7.3.2): δ, ω и E'_q, x'_q = x'_d. После
 * {@link PowerGrid#addMachine} — представление номера машины в массивах сети; механика вала —
 * {@link PrimeMover}.
 */
public final class SynchronousMachine {
    private final MachineParams params;
    private final ExciterSpec exciter;
    private final PrimeMover mover;
    PowerGrid grid;
    private int i = -1;
    private double pendingLoadW, pendingVoltagePu = 1;
    private boolean pendingPin;

    public SynchronousMachine(MachineParams p, ExciterSpec exc, PrimeMover mover) {
        this.params = Objects.requireNonNull(p, "params");
        this.exciter = Objects.requireNonNull(exc, "exciter");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    void bind(PowerGrid g, int index) {
        grid = g;
        i = index;
        g.setLoadTarget(i, pendingLoadW);
        g.vSetPu[i] = pendingVoltagePu;
        g.setShearPinCoupling(i, pendingPin);
    }

    public MachineParams params() {
        return params;
    }

    public ExciterSpec exciter() {
        return exciter;
    }

    public PrimeMover mover() {
        return mover;
    }

    private boolean live() {
        return grid != null && grid.initialized();
    }

    /** Угол ротора относительно синхронно вращающихся осей ω_s, рад. */
    public double deltaRad() {
        return live() ? grid.delta[i] : 0;
    }

    /** Частота вращения вала, рад/с. */
    public double omegaMech() {
        return live() ? grid.omega[i] * grid.ws / params.polePairs() : 0;
    }

    /** Скорость, о.е. синхронной. */
    public double speedPu() {
        return live() ? grid.omega[i] : 0;
    }

    public double ePrimeQ() {
        return live() ? grid.eq[i] : 0;
    }

    /** Напряжение возбуждения E_fd, о.е.; ток возбуждения i_f = E_fd (§7.3.2). */
    public double fieldVoltagePu() {
        return live() ? grid.efd[i] : 0;
    }

    public double terminalVoltagePu() {
        return live() ? grid.vt[i] : 0;
    }

    public double electricalPowerW() {
        return live() ? grid.pe[i] * grid.sb : 0;
    }

    public double reactivePowerVar() {
        return live() ? grid.qe[i] * grid.sb : 0;
    }

    public double mechanicalPowerW() {
        return live() ? grid.pm[i] * params.sRatedVa() : 0;
    }

    /** Открытие регулирующего клапана y (03a §3a.7.4). */
    public double valvePosition() {
        return live() ? grid.valve[i] : 0;
    }

    /** Провороты ротора относительно центра инерции острова. */
    public int poleSlips() {
        return live() ? grid.slips[i] : 0;
    }

    /**
     * {@code DAMAGED} — вал сломан; {@code ASYNC} — был проворот; одна в острове и без нагрузки —
     * {@code EXCITED} или {@code ROLLING} (не возбуждена); иначе {@code LOADED} выше 5 % S_ном.
     */
    public MachineState state() {
        return live() ? grid.machineState(i) : MachineState.STOPPED;
    }

    /** Усталостное повреждение валопровода D_shaft (несинхронное включение, §7.3.5); ≥ 1 — излом. */
    public double shaftDamage() {
        return grid == null ? 0 : grid.shaftDmg[i];
    }

    /** Повреждение обмоток статора (смещение лобовых частей при T_pk ≥ 3 о.е., §7.3.5). */
    public double statorDamage() {
        return grid == null ? 0 : grid.statorDmg[i];
    }

    /** Агрегат разъединён: срезана муфта (штифт) или сломан вал — первичный двигатель не передаёт момент. */
    public boolean couplingSheared() {
        return grid != null && grid.sheared[i];
    }

    /** Ограничитель перевозбуждения держит E_fd на номинальном токе возбуждения (§7.3.4). */
    public boolean overExcitationLimited() {
        return grid != null && grid.oelOn[i];
    }

    public ExcitationMode excitationMode() {
        return grid == null ? exciter.defaultMode() : grid.excMode[i];
    }

    /** Q_max при активной мощности {@code pW} и текущем напряжении (диаграмма §7.3.4), вар. */
    public double maxReactiveVar(double pW) {
        double v = live() ? grid.vt[i] : 1;
        return requireGrid().maxReactivePu(i, pW / params.sRatedVa(), v) * params.sRatedVa();
    }

    /** Q_min при активной мощности {@code pW} (статор и UEL), вар. */
    public double minReactiveVar(double pW) {
        double v = live() ? grid.vt[i] : 1;
        return requireGrid().minReactivePu(i, pW / params.sRatedVa(), v) * params.sRatedVa();
    }

    /** {@code generator_coupling} со срезным штифтом; установка новой соединяет срезанный агрегат с целым валом. */
    public void setShearPinCoupling(boolean installed) {
        if (grid == null) pendingPin = installed;
        else grid.setShearPinCoupling(i, installed);
    }

    /** Ремонт срезанной муфты без штифта; со сломанным валом — {@link IllegalStateException}. */
    public void repairCoupling() {
        requireGrid().repairCoupling(i);
    }

    /** Уставка Q в режиме {@code REACTIVE}, вар; вне диаграммы мощности — {@link IllegalArgumentException}. */
    public void setReactiveTarget(double var) {
        requireGrid().setReactiveTarget(i, var / params.sRatedVa());
    }

    /** Уставка cos φ в режиме {@code POWER_FACTOR}: > 0 — выдача Q, < 0 — потребление, |cos φ| ≥ 0,5. */
    public void setPowerFactorTarget(double pf) {
        requireGrid().setPowerFactorTarget(i, pf);
    }

    private PowerGrid requireGrid() {
        if (grid == null) throw new IllegalStateException("add the machine to a grid first");
        return grid;
    }

    /** Задание мощности: уставка регулятора y₀ или мощность вала Create без входа от Create. */
    public void setLoadTarget(double watts) {
        if (!Double.isFinite(watts)) throw new IllegalArgumentException("watts");
        if (grid == null) pendingLoadW = watts;
        else grid.setLoadTarget(i, watts);
    }

    /** Уставка напряжения на выводах, о.е. (до инициализации — PV-шина потокораспределения). */
    public void setVoltageReference(double pu) {
        if (!(pu > 0.5 && pu < 1.5)) throw new IllegalArgumentException("pu");
        if (grid == null) pendingVoltagePu = pu;
        else grid.setVoltageReference(i, pu);
    }

    public void setExcitationMode(ExcitationMode mode) {
        Objects.requireNonNull(mode, "mode");
        if (grid == null) throw new IllegalStateException("add the machine to a grid first");
        grid.setExcitationMode(i, mode);
    }

    /** E_fd в режиме {@code MANUAL}. */
    public void setFieldVoltage(double pu) {
        if (grid == null) throw new IllegalStateException("add the machine to a grid first");
        grid.setFieldVoltage(i, pu);
    }
}
