package dev.denfry.promptcritical.sim.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Корень электрической модели мира (07 §7.1–7.3). Тикается в FAST из SimulationWorld (05).
 *
 * <p>Фазорная RMS-модель прямой последовательности: машины — ЭДС E' за x'_d (модель 3-го порядка,
 * §7.3.2), сеть — линейная {@code Y·V = I_gen − I_load(V)} на каждый электрический остров с
 * LU-разложением при смене топологии (§7.1.3). Нагрузки — инжекции тока, поэтому их отключение
 * не требует переразложения. Качания — симплектический Эйлер на подшагах (§7.2.1).
 *
 * <p>Внутри — о.е. на S_b и базисное напряжение класса шины; наружу — СИ. Горячий цикл —
 * плоские массивы без аллокаций (01 §1.12.3).
 *
 * <p>Выключатели с временем хода (§7.4.7) и броском момента при включении на параллельную работу
 * (§7.3.5), АРВ всех режимов с ограничителями OEL/UEL (§7.3.3–7.3.4), асинхронные двигатели с
 * моментом Клосса (§7.6.3). Нет несимметричных КЗ, РЗА и тепла трансформаторов — следующий этап M3.
 */
public final class PowerGrid {
    /** Ниже этого напряжения P и I-доли нагрузки ведут себя как сопротивление (PSS/E «PQ → Z»). */
    static final double LOW_VOLTAGE_PU = 0.7;
    /** Фильтр частоты шины, как у цифровых реле частоты (§7.1.4). */
    static final double FREQUENCY_FILTER_S = 0.1;
    static final double MIN_FAULT_IMPEDANCE_PU = 1e-6;
    /** k_UEL в Q_min(P) = −V²/x_d + k_UEL·P (§7.3.4). */
    static final double K_UEL = 0.1;
    /** Внешний контур Q и cos φ: dV_ref/dt = K·ΔQ; τ ≈ 5 с при x_внеш = 0,1 о.е. (§7.3.3). */
    static final double REACTIVE_LOOP_GAIN = 0.1 / 5;
    static final double UEL_GAIN = 0.2;
    /** Уставки внешнего контура Q держат V_t в 0,9–1,1 ном. (§7.17.1). */
    static final double VT_MIN = 0.9, VT_MAX = 1.1;
    /**
     * OEL: пуск при i_f > 1,05 i_f,ном, выдержка t = K/(i_f/i_f,ном − 1)^α; K = 10 с и α = ln 3/ln 5
     * дают 120 % — 30 с и 200 % — 10 с (§7.3.4). Остывание — сброс накопленного за 60 с ниже номинала.
     */
    static final double OEL_PICKUP = 1.05, OEL_K_S = 10, OEL_EXPONENT = Math.log(3) / Math.log(5), OEL_RESET_S = 60;
    /** Срезной штифт {@code generator_coupling} ломается выше этого момента и спасает вал Create (§7.3.5). */
    static final double SHEAR_PIN_PU = 2;
    /** С этого момента при включении повреждаются обмотки статора: stator_damage += 0,1 (§7.3.5). */
    static final double STATOR_DAMAGE_PU = 3, STATOR_DAMAGE_STEP = 0.1;
    private static final double MIN_SPEED = 0.1;
    private static final int EVENT_CAPACITY = 256, EVENT_VALUES = 7;
    private static final int EV_TOPOLOGY = 0, EV_DEENERGIZED = 1, EV_POLE_SLIP = 2, EV_BREAKER = 3, EV_SYNC = 4;
    private static final BreakerState[] BREAKER_STATES = BreakerState.values();
    private static final SyncResult[] SYNC_RESULTS = SyncResult.values();

    final GridConfig cfg;
    final double sb, ws, f0;

    // ---------------------------------------------------------------- шины
    int nb;
    private final List<VoltageClass> busClass = new ArrayList<>();
    private final List<String> busLabel = new ArrayList<>();
    double[] vRe = new double[0], vIm = new double[0]; // решение сети (измерения)
    double[] vtRe = new double[0], vtIm = new double[0]; // итерация (следящий режим, §7.1.3)
    double[] thetaPrev = new double[0], fBus = new double[0];
    double[] faultG = new double[0];
    double[] gRe = new double[0], gIm = new double[0]; // токи Нортона машин по шинам
    double[] lRe = new double[0], lIm = new double[0]; // токи нагрузок по шинам
    int[] busIsland = new int[0], busLocal = new int[0];

    // ---------------------------------------------------------------- ветви
    int nbr;
    private final List<Branch> branchSpec = new ArrayList<>();
    int[] bFrom = new int[0], bTo = new int[0], tapStep = new int[0];
    double[] yRe = new double[0], yIm = new double[0], tap = new double[0];
    double[] shFG = new double[0], shFB = new double[0], shTG = new double[0], shTB = new double[0];
    boolean[] closed = new boolean[0];

    // ---------------------------------------------------------------- выключатели (§7.4.6–7.4.7)
    private final List<BreakerSpec> breakerSpec = new ArrayList<>(); // null — идеальный ключ
    private BreakerState[] brState = new BreakerState[0];
    private double[] brTimer = new double[0], brWear = new double[0];
    private int[] brOps = new int[0];
    private boolean[] brDc = new boolean[0], brSyncCheck = new boolean[0];

    // ---------------------------------------------------------------- машины
    int nm;
    private final List<SynchronousMachine> machines = new ArrayList<>();
    int[] mBus = new int[0], slipIndex = new int[0], slips = new int[0];
    boolean[] createShaft = new boolean[0], sheared = new boolean[0], pinCoupling = new boolean[0];
    boolean[] oelOn = new boolean[0];
    ExcitationMode[] excMode = new ExcitationMode[0];
    double[] mS = new double[0], mXd = new double[0], mXdP = new double[0], mXdPP = new double[0], mYgen = new double[0];
    double[] ifNom = new double[0], qRef = new double[0], qPerP = new double[0], oelAcc = new double[0];
    double[] uelBias = new double[0], shaftDmg = new double[0], statorDmg = new double[0];
    double[] mTd0 = new double[0], mH = new double[0], mD = new double[0];
    double[] exK = new double[0], exTe = new double[0], exR = new double[0], exTb = new double[0];
    double[] exMin = new double[0], exMax = new double[0];
    double[] gvRated = new double[0], gvDroop = new double[0], gvTs = new double[0], gvOpen = new double[0];
    double[] gvClose = new double[0], gvFast = new double[0], gvMin = new double[0];
    double[] delta = new double[0], omega = new double[0], eq = new double[0], efd = new double[0];
    double[] xLL = new double[0], vref = new double[0], efdManual = new double[0];
    double[] valve = new double[0], valve0 = new double[0], pm = new double[0];
    double[] pe = new double[0], qe = new double[0], ioRe = new double[0], ioIm = new double[0], vt = new double[0];
    double[] pTargetW = new double[0], vSetPu = new double[0];

    // ---------------------------------------------------------------- нагрузки
    int nl;
    private final List<LoadModel> loadSpec = new ArrayList<>();
    int[] lBus = new int[0];
    double[] lp0 = new double[0], lq0 = new double[0], laZ = new double[0], laI = new double[0], laP = new double[0];
    double[] ldF = new double[0], liRe = new double[0], liIm = new double[0];
    boolean[] lOn = new boolean[0];

    // ---------------------------------------------------------------- асинхронные двигатели (§7.6.3)
    int nmo;
    private final List<InductionMotorSpec> motorSpec = new ArrayList<>();
    int[] moBus = new int[0];
    boolean[] moOn = new boolean[0];
    double[] moScale = new double[0], moVr2 = new double[0], moX = new double[0], moRr = new double[0];
    double[] moLoss = new double[0], moQm = new double[0], moKq = new double[0], moH = new double[0];
    double[] moW = new double[0], moTl = new double[0], moTlUsed = new double[0], moTe = new double[0];
    double[] moIRe = new double[0], moIIm = new double[0];

    // ---------------------------------------------------------------- острова
    private Island[] islands = new Island[0];
    private boolean topologyDirty = true, initialized;

    private final int[] evKind = new int[EVENT_CAPACITY], evArg = new int[EVENT_CAPACITY];
    private final int[] evArg2 = new int[EVENT_CAPACITY], evArg3 = new int[EVENT_CAPACITY];
    private final double[] evVal = new double[EVENT_CAPACITY * EVENT_VALUES];
    private int evCount;
    private double thRe, thIm; // результат thevenin()

    public PowerGrid(GridConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        sb = cfg.baseVa();
        ws = cfg.synchronousSpeed();
        f0 = cfg.nominalFrequencyHz();
    }

    public GridConfig config() {
        return cfg;
    }

    // ================================================================ сборка

    public BusId addBus(VoltageClass vc, String label) {
        Objects.requireNonNull(vc, "vc");
        int i = nb++;
        busClass.add(vc);
        busLabel.add(label == null ? "bus" + i : label);
        vRe = grow(vRe, nb);
        vIm = grow(vIm, nb);
        vtRe = grow(vtRe, nb);
        vtIm = grow(vtIm, nb);
        thetaPrev = grow(thetaPrev, nb);
        fBus = grow(fBus, nb);
        faultG = grow(faultG, nb);
        gRe = grow(gRe, nb);
        gIm = grow(gIm, nb);
        lRe = grow(lRe, nb);
        lIm = grow(lIm, nb);
        busIsland = grow(busIsland, nb);
        busLocal = grow(busLocal, nb);
        topologyDirty = true;
        return new BusId(i);
    }

    public BranchId addBranch(Branch b) {
        Objects.requireNonNull(b, "branch");
        int f = bus(b.from()), t = bus(b.to());
        if (f == t) throw new IllegalArgumentException("branch connects bus " + f + " to itself");
        int k = nbr++;
        branchSpec.add(b);
        bFrom = grow(bFrom, nbr);
        bTo = grow(bTo, nbr);
        tapStep = grow(tapStep, nbr);
        yRe = grow(yRe, nbr);
        yIm = grow(yIm, nbr);
        tap = grow(tap, nbr);
        shFG = grow(shFG, nbr);
        shFB = grow(shFB, nbr);
        shTG = grow(shTG, nbr);
        shTB = grow(shTB, nbr);
        closed = grow(closed, nbr);
        breakerSpec.add(null);
        brState = grow(brState, nbr);
        brTimer = grow(brTimer, nbr);
        brWear = grow(brWear, nbr);
        brOps = grow(brOps, nbr);
        brDc = grow(brDc, nbr);
        brSyncCheck = grow(brSyncCheck, nbr);
        brDc[k] = true;
        bFrom[k] = f;
        bTo[k] = t;
        tap[k] = 1;
        closed[k] = true;
        switch (b) {
            case Branch.Line l -> series(k, l.rPu(), l.xPu(), l.bPu());
            case Branch.Cable c -> series(k, c.rPu(), c.xPu(), c.bPu());
            case Branch.Tie tie -> series(k, 0, tie.xPu(), 0);
            case Branch.Transformer tr -> {
                double ratio = sb / tr.sRatedVa();
                double r = tr.pkW() / tr.sRatedVa() * ratio, z = tr.ukPu() * ratio;
                series(k, r, Math.sqrt(z * z - r * r), 0);
                double g0 = tr.p0W() / sb, y0 = tr.i0Pu() / ratio;
                shFG[k] = g0;
                shFB[k] = -Math.sqrt(Math.max(y0 * y0 - g0 * g0, 0));
                tap[k] = tr.tap().ratio(0);
            }
        }
        topologyDirty = true;
        return new BranchId(k);
    }

    private void series(int k, double r, double x, double b) {
        double d = r * r + x * x;
        yRe[k] = r / d;
        yIm[k] = -x / d;
        shFB[k] = b / 2;
        shTB[k] = b / 2;
    }

    public MachineId addMachine(SynchronousMachine m, BusId at) {
        Objects.requireNonNull(m, "machine");
        if (m.grid != null) throw new IllegalStateException("machine already added");
        int bus = bus(at), i = nm++;
        machines.add(m);
        growMachines();
        MachineParams p = m.params();
        ExciterSpec e = m.exciter();
        mBus[i] = bus;
        mS[i] = p.sRatedVa() / sb;
        mXd[i] = p.xd();
        mXdP[i] = p.xdP();
        mXdPP[i] = p.xdPP();
        mYgen[i] = mS[i] / p.xdP(); // 1/x'_d на S_b
        mTd0[i] = p.td0PS();
        mH[i] = p.hS();
        mD[i] = p.dPu();
        ifNom[i] = p.ratedFieldPu();
        exK[i] = e.k();
        exTe[i] = e.teS();
        exR[i] = e.taOverTb();
        exTb[i] = e.tbS();
        exMin[i] = e.efdMin();
        exMax[i] = e.efdMax();
        excMode[i] = e.defaultMode();
        switch (m.mover()) {
            case PrimeMover.Governed g -> {
                gvRated[i] = g.ratedPowerW() / p.sRatedVa();
                gvDroop[i] = g.droop();
                gvTs[i] = g.servoTimeS();
                gvOpen[i] = g.openRatePerS();
                gvClose[i] = g.closeRatePerS();
                gvFast[i] = g.fastCloseRatePerS();
                gvMin[i] = g.minLoadFrac();
            }
            case PrimeMover.CreateShaft c -> {
                createShaft[i] = true;
                gvRated[i] = c.ratedPowerW() / p.sRatedVa();
            }
        }
        omega[i] = 1;
        eq[i] = 1;
        efd[i] = 1;
        vSetPu[i] = 1;
        m.bind(this, i);
        if (initialized) floatOnBus(i);
        topologyDirty = true; // проводимость машины входит в Y
        return new MachineId(i);
    }

    public LoadId addLoad(LoadModel l, BusId at) {
        Objects.requireNonNull(l, "load");
        int bus = bus(at), i = nl++;
        loadSpec.add(l);
        lBus = grow(lBus, nl);
        lp0 = grow(lp0, nl);
        lq0 = grow(lq0, nl);
        laZ = grow(laZ, nl);
        laI = grow(laI, nl);
        laP = grow(laP, nl);
        ldF = grow(ldF, nl);
        liRe = grow(liRe, nl);
        liIm = grow(liIm, nl);
        lOn = grow(lOn, nl);
        lBus[i] = bus;
        lp0[i] = l.p0W() / sb;
        lq0[i] = l.q0Var() / sb;
        laZ[i] = l.aZ();
        laI[i] = l.aI();
        laP[i] = l.aP();
        ldF[i] = Double.isNaN(l.dF()) ? cfg.loadDamping() : l.dF();
        lOn[i] = true;
        if (!topologyDirty) {
            Island isl = islands[busIsland[bus]];
            isl.loads = Arrays.copyOf(isl.loads, isl.loads.length + 1);
            isl.loads[isl.loads.length - 1] = i;
        }
        return new LoadId(i);
    }

    /**
     * Асинхронный двигатель на шине (§7.6.3), контактор включён. При {@link #initializePowerFlow} он
     * в установившемся режиме при заданном моменте нагрузки; добавленный в работающую сеть стоит и
     * пускается прямым включением.
     */
    public MotorId addMotor(InductionMotorSpec spec, BusId at) {
        Objects.requireNonNull(spec, "motor");
        int bus = bus(at), j = nmo++;
        motorSpec.add(spec);
        moBus = grow(moBus, nmo);
        moOn = grow(moOn, nmo);
        moScale = grow(moScale, nmo);
        moVr2 = grow(moVr2, nmo);
        moX = grow(moX, nmo);
        moRr = grow(moRr, nmo);
        moLoss = grow(moLoss, nmo);
        moQm = grow(moQm, nmo);
        moKq = grow(moKq, nmo);
        moH = grow(moH, nmo);
        moW = grow(moW, nmo);
        moTl = grow(moTl, nmo);
        moTlUsed = grow(moTlUsed, nmo);
        moTe = grow(moTe, nmo);
        moIRe = grow(moIRe, nmo);
        moIIm = grow(moIIm, nmo);
        moBus[j] = bus;
        moOn[j] = true;
        moScale[j] = spec.pRatedW() / sb;
        double vr = busClass.get(bus).volts / spec.vRatedV();
        moVr2[j] = vr * vr;
        moX[j] = spec.reactance();
        moRr[j] = spec.rotorResistance();
        double[] c = spec.calibration();
        moLoss[j] = c[0];
        moQm[j] = c[1];
        moKq[j] = c[2];
        moH[j] = spec.inertiaS();
        if (!topologyDirty) {
            Island isl = islands[busIsland[bus]];
            isl.motors = Arrays.copyOf(isl.motors, isl.motors.length + 1);
            isl.motors[isl.motors.length - 1] = j;
        }
        return new MotorId(j);
    }

    // ================================================================ команды

    /**
     * Идеальный ключ: мгновенно, без автомата §7.4.7 (разъединитель, восстановление из сохранения).
     * Меняет топологию → переразложение в начале следующего тика (§7.1.3). Включение двух островов
     * под напряжением — тоже включение на параллельную работу со всеми последствиями §7.3.5.
     */
    public void setBreaker(BranchId id, boolean close) {
        int k = branch(id);
        if (brState[k] != null) {
            brTimer[k] = 0;
            brState[k] = close ? BreakerState.CLOSED : BreakerState.OPEN;
        }
        if (closed[k] == close) return;
        if (close) {
            contactsClosed(k);
        } else {
            closed[k] = false;
            topologyDirty = true;
        }
    }

    /** Контакты ветви замкнуты (у выключателя — в том числе при отказе отключения). */
    public boolean isBreakerClosed(BranchId id) {
        return closed[branch(id)];
    }

    /** Ставит на ветвь выключатель с автоматом §7.4.7; состояние — по текущему положению контактов. */
    public void attachBreaker(BranchId id, BreakerSpec spec) {
        int k = branch(id);
        breakerSpec.set(k, Objects.requireNonNull(spec, "spec"));
        brState[k] = closed[k] ? BreakerState.CLOSED : BreakerState.OPEN;
        brTimer[k] = 0;
        brWear[k] = 0;
        brOps[k] = 0;
    }

    /**
     * Команда «вкл»/«откл» (§7.4.7). Без оперативного тока команды не исполняются; включение
     * проверяет реле контроля синхронизма, если оно стоит. Контакты замкнутся через t_вкл
     * (разомкнутся через t_откл) в конце тика, когда выдержка истечёт; «откл» во время хода
     * включения — свободное расцепление.
     */
    public BreakerResponse operateBreaker(BranchId id, boolean close) {
        int k = breaker(id);
        BreakerState s = brState[k];
        if (s == BreakerState.FAULTED || s == BreakerState.FAILED_TO_INTERRUPT) return BreakerResponse.UNAVAILABLE;
        if (!brDc[k]) return BreakerResponse.NO_CONTROL_POWER;
        if (close) {
            if (s == BreakerState.OPENING) return BreakerResponse.BUSY;
            if (s != BreakerState.OPEN) return BreakerResponse.ACCEPTED;
            if (brSyncCheck[k] && !syncPermits(k)) return BreakerResponse.INTERLOCK;
            brTimer[k] = breakerSpec.get(k).closeTimeS();
            setBreakerState(k, BreakerState.CLOSING);
        } else if (s == BreakerState.CLOSING) {
            brTimer[k] = 0;
            setBreakerState(k, BreakerState.OPEN);
        } else if (s == BreakerState.CLOSED) {
            brTimer[k] = breakerSpec.get(k).openTimeS();
            setBreakerState(k, BreakerState.OPENING);
        }
        return BreakerResponse.ACCEPTED;
    }

    /** Оперативный постоянный ток привода (§7.11.5): без него выключатель не включается и не отключается. */
    public void setBreakerControlPower(BranchId id, boolean dcOk) {
        brDc[breaker(id)] = dcOk;
    }

    /** Реле контроля синхронизма ANSI 25 (§7.3.5) с окном {@code grid.sync_max_*}. */
    public void setSyncCheck(BranchId id, boolean installed) {
        brSyncCheck[breaker(id)] = installed;
    }

    /** Ремонт: {@code FAULTED} или {@code FAILED_TO_INTERRUPT} → {@code OPEN}, износ сброшен. */
    public void repairBreaker(BranchId id) {
        int k = breaker(id);
        if (brState[k] == BreakerState.FAILED_TO_INTERRUPT) {
            closed[k] = false;
            topologyDirty = true;
        } else if (brState[k] != BreakerState.FAULTED) {
            throw new IllegalStateException("breaker is " + brState[k]);
        }
        brWear[k] = 0;
        setBreakerState(k, BreakerState.OPEN);
    }

    public BreakerState breakerState(BranchId id) {
        return brState[breaker(id)];
    }

    /** Износ контактов: доля ресурса отключений КЗ до ревизии. */
    public double breakerWear(BranchId id) {
        return brWear[breaker(id)];
    }

    public int breakerOperations(BranchId id) {
        return brOps[breaker(id)];
    }

    /**
     * Синхроноскоп на ветви: «генератор» — сторона с меньшей Σ H·S (входящая), «сеть» — другая; угол
     * опережения — по t_вкл выключателя (без выключателя — 0).
     */
    public SyncScope syncScope(BranchId id) {
        int k = branch(id);
        return scope(k, incomingIsFrom(k));
    }

    /** Отключение и подключение нагрузки (АЧР, потребитель) — без переразложения. */
    public void setLoadConnected(LoadId id, boolean on) {
        lOn[load(id)] = on;
    }

    /** Контактор двигателя: отключённый выбегает, включённый на стоящем двигателе — прямой пуск. */
    public void setMotorConnected(MotorId id, boolean on) {
        moOn[motor(id)] = on;
    }

    /** Момент нагрузки двигателя без входа от Create, о.е. на P_ном/ω_s. */
    public void setMotorLoadTorque(MotorId id, double pu) {
        if (!(pu >= 0)) throw new IllegalArgumentException("pu");
        moTl[motor(id)] = pu;
    }

    /** Ступень РПН трансформатора. */
    public void setTap(BranchId id, int step) {
        int k = branch(id);
        if (!(branchSpec.get(k) instanceof Branch.Transformer tr)) throw new IllegalArgumentException("not a transformer");
        if (step < tr.tap().minStep() || step > tr.tap().maxStep()) throw new IllegalArgumentException("tap step " + step);
        tapStep[k] = step;
        tap[k] = tr.tap().ratio(step);
        markY(bFrom[k]);
    }

    /** КЗ в узле (§7.5.1): шунт 1/z_f. Каркас M1 — только трёхфазное. */
    public void applyFault(BusId at, FaultType type, double zfPu) {
        int b = bus(at);
        Objects.requireNonNull(type, "type");
        if (type != FaultType.THREE_PHASE)
            throw new UnsupportedOperationException("unsymmetrical faults need negative/zero sequence networks (M3)");
        if (!(zfPu >= 0)) throw new IllegalArgumentException("zfPu");
        faultG[b] = 1 / Math.max(zfPu, MIN_FAULT_IMPEDANCE_PU);
        markY(b);
    }

    public void clearFault(BusId at) {
        int b = bus(at);
        if (faultG[b] != 0) {
            faultG[b] = 0;
            markY(b);
        }
    }

    private void markY(int bus) {
        if (!topologyDirty) islands[busIsland[bus]].yDirty = true;
    }

    // ================================================================ инициализация

    /**
     * Потокораспределение (Ньютон, полярные координаты) и установившееся состояние машин:
     * шины машин — PV с {@link SynchronousMachine#setVoltageReference}, балансирующая — шина самой
     * мощной машины острова; P остальных машин — {@link SynchronousMachine#setLoadTarget}.
     * Из решения находятся δ, E'_q, E_fd, V_ref, P_m и открытие клапана.
     */
    public void initializePowerFlow() {
        rebuildTopology();
        evCount = 0;
        for (Island isl : islands) {
            if (isl.machines.length == 0) continue;
            new PowerFlow(this, isl).solve();
        }
        for (Island isl : islands) factorIsland(isl);
        for (int b = 0; b < nb; b++) {
            vtRe[b] = vRe[b];
            vtIm[b] = vIm[b];
            thetaPrev[b] = Math.atan2(vIm[b], vRe[b]);
            fBus[b] = islands[busIsland[b]].energized ? f0 : 0;
        }
        for (int i = 0; i < nm; i++) machineFromTerminal(i);
        for (int j = 0; j < nmo; j++) {
            int b = moBus[j];
            double v = Math.sqrt((vRe[b] * vRe[b] + vIm[b] * vIm[b]) * moVr2[j]);
            boolean live = moOn[j] && islands[busIsland[b]].energized;
            moW[j] = live ? 1 - motorSpec.get(j).slipAt(moTl[j], v) : 0;
        }
        for (Island isl : islands) {
            isl.fCoi = isl.energized ? f0 : 0;
            isl.rocof = 0;
        }
        initialized = true;
        GridOutputs scratch = new GridOutputs();
        solveNetwork();
        measure(Double.POSITIVE_INFINITY, scratch);
    }

    /** Состояние машины по P, Q и напряжению её шины (vRe/vIm, pe/qe — на S_b). */
    private void machineFromTerminal(int i) {
        int b = mBus[i];
        double vr = vRe[b], vi = vIm[b], v2 = vr * vr + vi * vi;
        double p = pe[i], q = qe[i];
        double ir = (p * vr + q * vi) / v2, ii = (p * vi - q * vr) / v2;
        double x = 1 / mYgen[i];
        double er = vr - x * ii, ei = vi + x * ir;
        delta[i] = Math.atan2(ei, er);
        eq[i] = Math.hypot(er, ei);
        omega[i] = 1;
        double id = (ir * Math.sin(delta[i]) - ii * Math.cos(delta[i])) / mS[i];
        efd[i] = eq[i] + (mXd[i] - mXdP[i]) * id;
        vt[i] = Math.sqrt(v2);
        xLL[i] = efd[i] / exK[i];
        vref[i] = vt[i] + efd[i] / exK[i];
        efdManual[i] = efd[i];
        pm[i] = p / mS[i];
        pTargetW[i] = p * sb;
        valve[i] = valve0[i] = pm[i] / gvRated[i];
        ioRe[i] = ir;
        ioIm[i] = ii;
        slipIndex[i] = 0;
        qRef[i] = q / mS[i];
        qPerP[i] = p != 0 ? q / p : 0;
    }

    /** Машина, добавленная в работающую сеть: холостой ход на напряжении шины. */
    private void floatOnBus(int i) {
        int b = mBus[i];
        if (vRe[b] == 0 && vIm[b] == 0) {
            vRe[b] = vSetPu[i];
        }
        pe[i] = qe[i] = 0;
        machineFromTerminal(i);
        pTargetW[i] = 0;
    }

    // ================================================================ шаг

    /** FAST-шаг: переразложение изменившихся островов, подшаги машин с решением сети, измерения. */
    public void stepFast(double dtS, GridInputs in, GridOutputs out) {
        if (!(dtS > 0)) throw new IllegalArgumentException("dt must be > 0: " + dtS);
        if (!initialized) throw new IllegalStateException("initializePowerFlow() first");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        if (topologyDirty) rebuildTopology();
        int budget = cfg.maxRefactorPerTick();
        for (Island isl : islands) {
            if (budget == 0) break;
            if (!isl.factored || isl.yDirty) {
                factorIsland(isl);
                budget--;
            }
        }
        int n = cfg.substeps();
        double h = dtS / n;
        for (int s = 0; s < n; s++) substep(h, in);
        measure(dtS, out);
        advanceBreakers(dtS);
    }

    /** SLOW-шаг: тепло трансформаторов и старение — после M1. */
    public void stepSlow(double dtPhysS) {
        if (!(dtPhysS > 0)) throw new IllegalArgumentException("dt");
    }

    private void substep(double h, GridInputs in) {
        for (Island isl : islands) {
            if (!isl.energized) continue;
            double hs = 0, hw = 0;
            for (int i : isl.machines) {
                double w = mH[i] * mS[i];
                hs += w;
                hw += w * omega[i];
            }
            isl.omegaCoi = hw / hs;
            if (!isl.solvable()) continue;
            // фазоры острова вращаются относительно осей ω_s: стартовое приближение поворачиваем
            // вместе с ними, иначе при f ≠ f₀ итерация не сходится и нагрузки берут не ту мощность
            double rot = ws * (isl.omegaCoi - 1) * h, c = Math.cos(rot), s = Math.sin(rot);
            for (int b : isl.buses) {
                double r = vtRe[b];
                vtRe[b] = r * c - vtIm[b] * s;
                vtIm[b] = r * s + vtIm[b] * c;
            }
        }
        solveNetwork();
        for (Island isl : islands) {
            if (!isl.energized) continue;
            for (int i : isl.machines) advanceMachine(i, h, isl.omegaCoi, in);
        }
        for (int j = 0; j < nmo; j++) advanceMotor(j, h, in);
    }

    /** Итерации сети с факторизованной Y (§7.1.3) и электрические величины машин. */
    private void solveNetwork() {
        for (Island isl : islands) {
            if (!isl.solvable()) continue;
            for (int b : isl.buses) {
                gRe[b] = 0;
                gIm[b] = 0;
            }
            for (int i : isl.machines) {
                double er = eq[i] * Math.cos(delta[i]), ei = eq[i] * Math.sin(delta[i]);
                gRe[mBus[i]] += ei * mYgen[i]; // E'/(j x') = −j·E'·y
                gIm[mBus[i]] -= er * mYgen[i];
            }
        }
        double w = cfg.solverRelaxation();
        int iters = cfg.solverIterations();
        for (int it = 0; it < iters; it++) {
            loadCurrents();
            for (Island isl : islands) {
                if (!isl.solvable()) continue;
                int[] buses = isl.buses;
                for (int k = 0; k < buses.length; k++) {
                    int b = buses[k];
                    isl.bRe[k] = gRe[b] - lRe[b];
                    isl.bIm[k] = gIm[b] - lIm[b];
                }
                isl.lu.solve(isl.bRe, isl.bIm, isl.bRe, isl.bIm);
                double step = 0;
                for (int k = 0; k < buses.length; k++) {
                    int b = buses[k];
                    vRe[b] = isl.bRe[k];
                    vIm[b] = isl.bIm[k];
                    double dr = vRe[b] - vtRe[b], di = vIm[b] - vtIm[b];
                    step = Math.max(step, Math.abs(dr) + Math.abs(di));
                    vtRe[b] += w * dr;
                    vtIm[b] += w * di;
                }
                isl.solverStep = step;
            }
        }
        for (Island isl : islands) {
            if (!isl.solvable()) continue;
            for (int i : isl.machines) {
                int b = mBus[i];
                double er = eq[i] * Math.cos(delta[i]), ei = eq[i] * Math.sin(delta[i]);
                double y = mYgen[i];
                double ir = (ei - vIm[b]) * y, ii = -(er - vRe[b]) * y; // (E' − V)/(j x')
                ioRe[i] = ir;
                ioIm[i] = ii;
                pe[i] = vRe[b] * ir + vIm[b] * ii;
                qe[i] = vIm[b] * ir - vRe[b] * ii;
                vt[i] = Math.hypot(vRe[b], vIm[b]);
            }
        }
    }

    /** Токи нагрузок по напряжению итерации; ниже 0,7 о.е. — как сопротивление (§7.1.3). */
    private void loadCurrents() {
        Arrays.fill(lRe, 0, nb, 0);
        Arrays.fill(lIm, 0, nb, 0);
        for (int j = 0; j < nl; j++) {
            int b = lBus[j];
            Island isl = islands[busIsland[b]];
            if (!lOn[j] || !isl.solvable()) {
                liRe[j] = 0;
                liIm[j] = 0;
                continue;
            }
            double vr = vtRe[b], vi = vtIm[b], v2 = vr * vr + vi * vi;
            if (v2 == 0) {
                liRe[j] = 0;
                liIm[j] = 0;
                continue;
            }
            double v = Math.sqrt(v2);
            double zip;
            if (v >= LOW_VOLTAGE_PU) {
                zip = laZ[j] * v2 + laI[j] * v + laP[j];
            } else {
                double l = LOW_VOLTAGE_PU;
                zip = (laZ[j] * l * l + laI[j] * l + laP[j]) * v2 / (l * l);
            }
            double p = lp0[j] * zip * (1 + ldF[j] * (isl.omegaCoi - 1)), q = lq0[j] * zip;
            double ir = (p * vr + q * vi) / v2, ii = (p * vi - q * vr) / v2; // conj(S/V)
            liRe[j] = ir;
            liIm[j] = ii;
            lRe[b] += ir;
            lIm[b] += ii;
        }
        for (int j = 0; j < nmo; j++) {
            int b = moBus[j];
            Island isl = islands[busIsland[b]];
            double vr = vtRe[b], vi = vtIm[b], v2 = vr * vr + vi * vi;
            if (!moOn[j] || !isl.solvable() || v2 == 0) {
                moIRe[j] = 0;
                moIIm[j] = 0;
                continue;
            }
            // при заданном скольжении P и Q ∝ v²: двигатель — сопротивление, ток ∝ V
            double s = 1 - moW[j] / isl.omegaCoi, vm2 = v2 * moVr2[j];
            double p = motorP(j, s, vm2) * moScale[j], q = motorQ(j, s, vm2) * moScale[j];
            double ir = (p * vr + q * vi) / v2, ii = (p * vi - q * vr) / v2;
            moIRe[j] = ir;
            moIIm[j] = ii;
            lRe[b] += ir;
            lIm[b] += ii;
        }
    }

    /** I_r² схемы замещения двигателя, о.е. на P_ном. */
    private double motorIr2(int j, double s, double vm2) {
        double rr = moRr[j], x = moX[j];
        return vm2 * s * s / (rr * rr + x * x * s * s);
    }

    /** P_эл = P_δ + потери статора; P_δ = I_r²·R_r/s — момент Клосса. */
    private double motorP(int j, double s, double vm2) {
        double rr = moRr[j], x = moX[j];
        return vm2 * s * rr / (rr * rr + x * x * s * s) + moLoss[j] * motorIr2(j, s, vm2);
    }

    private double motorQ(int j, double s, double vm2) {
        return moQm[j] * vm2 + moKq[j] * motorIr2(j, s, vm2);
    }

    /**
     * Ротор двигателя: 2H·dω/dt = T_e − T_нагр. На устойчивой ветви момент круто падает с ростом
     * скорости — шаг линеаризованно-неявный, иначе подшаг 25 мс неустойчив при малом H.
     */
    private void advanceMotor(int j, double h, GridInputs in) {
        double tl = in.motorLoadTorquePu(j);
        if (Double.isNaN(tl)) tl = moTl[j];
        moTlUsed[j] = tl;
        int b = moBus[j];
        Island isl = islands[busIsland[b]];
        double w = moW[j], te = 0, dte = 0;
        if (moOn[j] && isl.solvable()) {
            double wc = isl.omegaCoi, s = 1 - w / wc;
            double vm2 = (vRe[b] * vRe[b] + vIm[b] * vIm[b]) * moVr2[j];
            double rr = moRr[j], x = moX[j], d = rr * rr + x * x * s * s;
            te = vm2 * s * rr / d;
            dte = -vm2 * rr * (rr * rr - x * x * s * s) / (d * d) / wc; // dT/dω = −(dT/ds)/ω_c
        }
        moTe[j] = te;
        if (w <= 0 && te <= tl) { // пассивная нагрузка назад не крутит
            moW[j] = 0;
            return;
        }
        double a = 2 * moH[j];
        double wn = w + h * (te - tl) / a / (1 - h * Math.min(dte, 0) / a);
        moW[j] = Math.max(0, wn);
    }

    /** Подшаг машины: ω по моментам при δ_n, затем δ по ω_{n+1} (симплектический Эйлер), поток, АРВ, регулятор. */
    private void advanceMachine(int i, double h, double omegaCoi, GridInputs in) {
        double w = omega[i];
        double mech;
        if (createShaft[i]) {
            double pw = in.createShaftPowerW(i);
            mech = (Double.isNaN(pw) ? pTargetW[i] : pw) / (mS[i] * sb);
        } else {
            governor(i, h, w);
            mech = valve[i] * gvRated[i];
        }
        if (sheared[i]) mech = 0; // муфта срезана или вал сломан: первичный двигатель отделён (§7.3.5)
        pm[i] = mech;
        double speed = Math.max(w, MIN_SPEED);
        double pElec = pe[i] / mS[i];
        // демпферы реагируют на скольжение относительно поля статора — частоты острова, а не номинала
        double acc = ((mech - pElec) / speed - mD[i] * (w - omegaCoi)) / (2 * mH[i]);
        omega[i] = w + h * acc;
        delta[i] += h * ws * (omega[i] - 1);

        double s = Math.sin(delta[i]), c = Math.cos(delta[i]);
        double id = (ioRe[i] * s - ioIm[i] * c) / mS[i];
        double eqTarget = efd[i] - (mXd[i] - mXdP[i]) * id;
        eq[i] += (eqTarget - eq[i]) * (1 - Math.exp(-h / mTd0[i]));
        exciter(i, h);
    }

    /**
     * АРВ (§7.3.3–7.3.4): SEXS по ошибке напряжения; в {@code REACTIVE}/{@code POWER_FACTOR} внешний
     * интегральный контур двигает V_ref к заданной Q, не выводя V_t из 0,9–1,1. UEL — интегратор,
     * добавляющий к ошибке напряжения, пока Q ниже Q_min(P). OEL после обратнозависимой выдержки
     * ограничивает E_fd номинальным током возбуждения, пока АРВ требует больше.
     */
    private void exciter(int i, double h) {
        ExcitationMode mode = excMode[i];
        if (mode == ExcitationMode.MANUAL) {
            efd[i] = efdManual[i];
            return;
        }
        double p = pe[i] / mS[i], q = qe[i] / mS[i], v = vt[i];
        double qMin = -v * v / mXd[i] + K_UEL * p;
        if (mode != ExcitationMode.VOLTAGE) {
            double target = Math.max(mode == ExcitationMode.REACTIVE ? qRef[i] : p * qPerP[i], qMin);
            double dq = target - q;
            boolean held = dq > 0 ? v >= VT_MAX || efd[i] >= efdCeiling(i) : v <= VT_MIN || efd[i] <= exMin[i];
            if (!held) vref[i] += h * REACTIVE_LOOP_GAIN * dq;
        }
        uelBias[i] = Math.max(0, uelBias[i] + h * UEL_GAIN * (qMin - q));
        double err = vref[i] - vt[i] + uelBias[i];
        double r = exR[i];
        double ll = r * err + (1 - r) * xLL[i];
        xLL[i] += (err - xLL[i]) * (1 - Math.exp(-h / exTb[i]));
        double demand = exK[i] * ll;
        if (oelOn[i] && demand < ifNom[i]) {
            oelOn[i] = false;
            oelAcc[i] = 0;
        }
        double e = efd[i] + (demand - efd[i]) * (1 - Math.exp(-h / exTe[i]));
        efd[i] = Math.max(exMin[i], Math.min(efdCeiling(i), e));
        double x = efd[i] / ifNom[i];
        if (x > OEL_PICKUP) oelAcc[i] += h * Math.pow(x - 1, OEL_EXPONENT) / OEL_K_S;
        else if (x < 1) oelAcc[i] = Math.max(0, oelAcc[i] - h / OEL_RESET_S);
        if (oelAcc[i] >= 1) oelOn[i] = true;
    }

    private double efdCeiling(int i) {
        return oelOn[i] ? Math.min(exMax[i], ifNom[i]) : exMax[i];
    }

    /** Регулятор 03a §3a.7.4 со статизмом и мёртвой зоной. */
    private void governor(int i, double h, double w) {
        double dw = w - 1, db = cfg.governorDeadbandHz() / f0;
        double eff = dw > db ? dw - db : dw < -db ? dw + db : 0;
        double target = valve0[i] - eff / gvDroop[i];
        target = Math.max(gvMin[i], Math.min(1, target));
        double dy = (target - valve[i]) * (1 - Math.exp(-h / gvTs[i]));
        double close = w > PrimeMover.Governed.FAST_CLOSE_SPEED ? gvFast[i] : gvClose[i];
        dy = Math.max(-close * h, Math.min(gvOpen[i] * h, dy));
        valve[i] += dy;
    }

    // ================================================================ измерения

    private void measure(double dt, GridOutputs out) {
        double gen = 0, load = 0, loss = 0, step = 0;
        int pending = 0;
        for (Island isl : islands) {
            if (!isl.energized) continue;
            if (!isl.factored || isl.yDirty) pending++;
            double hs = 0, hw = 0, hd = 0;
            for (int i : isl.machines) {
                double wgt = mH[i] * mS[i];
                hs += wgt;
                hw += wgt * omega[i];
                hd += wgt * delta[i];
            }
            double f = f0 * hw / hs, dCoi = hd / hs;
            isl.rocof = Double.isInfinite(dt) ? 0 : (f - isl.fCoi) / dt;
            isl.fCoi = f;
            for (int i : isl.machines) {
                int idx = (int) Math.floor((delta[i] - dCoi + Math.PI) / (2 * Math.PI));
                if (idx != slipIndex[i]) {
                    slipIndex[i] = idx;
                    slips[i]++;
                    event(EV_POLE_SLIP, i);
                }
            }
            if (Math.abs(dCoi) > Math.PI) { // угол системы координат острова держим ограниченным
                double shift = 2 * Math.PI * Math.rint(dCoi / (2 * Math.PI));
                for (int i : isl.machines) delta[i] -= shift;
            }
            if (isl.solvable()) {
                isl.gen = 0;
                for (int i : isl.machines) isl.gen += pe[i];
                isl.load = 0;
                for (int j : isl.loads) {
                    int b = lBus[j];
                    isl.load += vRe[b] * liRe[j] + vIm[b] * liIm[j];
                }
                for (int j : isl.motors) {
                    int b = moBus[j];
                    isl.load += vRe[b] * moIRe[j] + vIm[b] * moIIm[j];
                }
                isl.loss = 0;
                for (int k : isl.branches) isl.loss += branchLoss(k);
                for (int b : isl.buses) isl.loss += faultG[b] * (vRe[b] * vRe[b] + vIm[b] * vIm[b]);
                step = Math.max(step, isl.solverStep);
            }
            gen += isl.gen;
            load += isl.load;
            loss += isl.loss;
        }
        double a = Double.isInfinite(dt) ? 1 : 1 - Math.exp(-dt / FREQUENCY_FILTER_S);
        for (int b = 0; b < nb; b++) {
            Island isl = islands[busIsland[b]];
            if (!isl.energized) {
                fBus[b] = 0;
                continue;
            }
            double th = Math.atan2(vIm[b], vRe[b]);
            double d = th - thetaPrev[b];
            d -= 2 * Math.PI * Math.rint(d / (2 * Math.PI));
            thetaPrev[b] = th;
            double raw = Double.isInfinite(dt) ? f0 : f0 + d / (2 * Math.PI * dt);
            fBus[b] += (raw - fBus[b]) * a;
        }
        out.generationW = gen * sb;
        out.loadW = load * sb;
        out.lossW = loss * sb;
        out.maxSolverStepPu = step;
        out.islands = islands.length;
        out.pendingIslands = pending;
    }

    // ================================================================ выключатели и синхронизация

    /** Выдержки хода выключателей; контакты меняют положение в конце тика, где выдержка истекла. */
    private void advanceBreakers(double dt) {
        for (int k = 0; k < nbr; k++) {
            BreakerState s = brState[k];
            if (s != BreakerState.CLOSING && s != BreakerState.OPENING) continue;
            brTimer[k] -= dt;
            if (brTimer[k] > 1e-9) continue;
            brTimer[k] = 0;
            if (s == BreakerState.CLOSING) {
                setBreakerState(k, BreakerState.CLOSED);
                contactsClosed(k);
                continue;
            }
            BreakerSpec spec = breakerSpec.get(k);
            double ka = branchCurrentPu(k) * sb / (Math.sqrt(3) * busClass.get(bFrom[k]).volts) / 1e3;
            if (ka > spec.interruptKa()) { // дуга не гаснет: ветвь под током, ждёт УРОВ (§7.5.5)
                setBreakerState(k, BreakerState.FAILED_TO_INTERRUPT);
                continue;
            }
            closed[k] = false;
            topologyDirty = true;
            brOps[k]++;
            if (spec.faultOpsBeforeService() > 0 && ka > BreakerSpec.FAULT_CURRENT_FRACTION * spec.interruptKa())
                brWear[k] += 1.0 / spec.faultOpsBeforeService();
            setBreakerState(k, brWear[k] >= 1 - 1e-9 ? BreakerState.FAULTED : BreakerState.OPEN);
        }
    }

    private void setBreakerState(int k, BreakerState s) {
        brState[k] = s;
        if (initialized && event(EV_BREAKER, k)) evArg3[evCount - 1] = s.ordinal();
    }

    /** Контакты замкнулись; если ветвь связала два острова под напряжением — бросок момента (§7.3.5). */
    private void contactsClosed(int k) {
        if (initialized && !topologyDirty) {
            Island a = islands[busIsland[bFrom[k]]], b = islands[busIsland[bTo[k]]];
            if (a != b && a.solvable() && b.solvable()) synchronize(k, a, b);
        }
        closed[k] = true;
        topologyDirty = true;
    }

    private SyncScope scope(int k, boolean fromIncoming) {
        int g = fromIncoming ? bFrom[k] : bTo[k], n = fromIncoming ? bTo[k] : bFrom[k];
        BreakerSpec spec = breakerSpec.get(k);
        return SyncScope.of(fBus[g], fBus[n], Math.atan2(vIm[g], vRe[g]), Math.atan2(vIm[n], vRe[n]),
                Math.hypot(vRe[g], vIm[g]), Math.hypot(vRe[n], vIm[n]), spec == null ? 0 : spec.closeTimeS());
    }

    /** Входящая сторона ветви — остров с меньшей Σ H·S; при равенстве — {@code from}. */
    private boolean incomingIsFrom(int k) {
        return inertia(islands[busIsland[bFrom[k]]]) <= inertia(islands[busIsland[bTo[k]]]);
    }

    /** Реле 25: включение на обесточенную сторону разрешено, иначе — окно {@code grid.sync_max_*}. */
    private boolean syncPermits(int k) {
        if (!islands[busIsland[bFrom[k]]].energized || !islands[busIsland[bTo[k]]].energized) return true;
        SyncScope sc = scope(k, incomingIsFrom(k));
        return sc.inWindow(Math.toRadians(cfg.syncMaxAngleDeg()), cfg.syncMaxDv(), cfg.syncMaxSlipHz());
    }

    /**
     * Включение острова на параллельную работу (§7.3.5). Входящая сторона — с меньшей Σ H·S. Ток
     * включения — по Тевенену: I = (V_вх − V_сети)/(Z_вх + z_ветви + Z_сети), где в Z_вх x'_d машин
     * заменено на x''_d; пиковый момент T_pk = E''·|I| на суммарную S_ном машин входящей стороны,
     * E'' ≈ напряжение холостого хода входящей шины.
     */
    private void synchronize(int k, Island a, Island b) {
        boolean fromIncoming = incomingIsFrom(k);
        Island inc = fromIncoming ? a : b, run = fromIncoming ? b : a;
        int bi = fromIncoming ? bFrom[k] : bTo[k];
        thevenin(inc, bi);
        double zr = thRe, zi = thIm;
        thevenin(run, fromIncoming ? bTo[k] : bFrom[k]);
        double y2 = yRe[k] * yRe[k] + yIm[k] * yIm[k];
        zr += thRe + yRe[k] / y2;
        zi += thIm - yIm[k] / y2;
        double yP = 0, yPP = 0, sInc = 0;
        for (int i : inc.machines) {
            yP += mS[i] / mXdP[i];
            yPP += mS[i] / mXdPP[i];
            sInc += mS[i];
        }
        zi -= 1 / yP - 1 / yPP;
        SyncScope sc = scope(k, fromIncoming);
        double ePP = Math.hypot(vRe[bi], vIm[bi]);
        double torque = sc.closingTorquePu(ePP, Math.hypot(zr, zi) * sInc);
        for (int i : inc.machines) shock(i, k, sc, torque);
    }

    /**
     * Последствия броска момента для машины (таблица §7.3.5). Срезной штифт ломается выше 2 о.е. и
     * пропускает на вал не больше 2 о.е.; муфта без штифта срезается с {@code grid.coupling_shear_pu}
     * и сама разгружает вал — ΔD = ((T − 1,5)/3,5)³ копится только ниже среза.
     */
    private void shock(int i, int k, SyncScope sc, double torque) {
        double tDmg = cfg.syncTorqueDamagePu(), tShear = cfg.couplingShearPu();
        boolean pinBreaks = pinCoupling[i] && !sheared[i] && torque > SHEAR_PIN_PU;
        boolean couplingShears = !pinBreaks && !sheared[i] && torque >= tShear;
        double transmitted = pinBreaks ? SHEAR_PIN_PU : torque;
        double dmg = !couplingShears && transmitted > tDmg ? Math.pow((transmitted - tDmg) / (tShear - tDmg), 3) : 0;
        SyncResult result = dmg > 0 ? SyncResult.DAMAGE : SyncResult.OK;
        if (pinBreaks || couplingShears) {
            sheared[i] = true;
            result = SyncResult.SHEARED;
        }
        shaftDmg[i] += dmg;
        if (shaftDmg[i] >= 1) sheared[i] = true; // излом вала
        if (torque >= STATOR_DAMAGE_PU) statorDmg[i] += STATOR_DAMAGE_STEP;
        double shock = createShaft[i] && !pinBreaks ? transmitted * mS[i] * sb * omega[i] : 0;
        if (!event(EV_SYNC, i)) return;
        int e = evCount - 1, o = e * EVENT_VALUES;
        evArg2[e] = k;
        evArg3[e] = result.ordinal();
        evVal[o] = sc.slipHz();
        evVal[o + 1] = sc.angleRad();
        evVal[o + 2] = sc.dvPu();
        evVal[o + 3] = sc.advanceAngleRad();
        evVal[o + 4] = torque;
        evVal[o + 5] = dmg;
        evVal[o + 6] = shock;
    }

    private double inertia(Island isl) {
        double s = 0;
        for (int i : isl.machines) s += mH[i] * mS[i];
        return s;
    }

    /** Z_th острова в узле — (Y⁻¹)_bb, о.е. на S_b, в thRe/thIm. Портит рабочие векторы острова. */
    private void thevenin(Island isl, int bus) {
        Arrays.fill(isl.bRe, 0);
        Arrays.fill(isl.bIm, 0);
        int l = busLocal[bus];
        isl.bRe[l] = 1;
        isl.lu.solve(isl.bRe, isl.bIm, isl.bRe, isl.bIm);
        thRe = isl.bRe[l];
        thIm = isl.bIm[l];
    }

    /** Активные потери ветви: Re(S_ij + S_ji), включая шунты П-схемы. */
    private double branchLoss(int k) {
        int f = bFrom[k], t = bTo[k];
        double t1 = tap[k];
        double fr = vRe[f], fi = vIm[f], tr = vRe[t], ti = vIm[t];
        // I_f = (y/t² + ysh_f)·V_f − (y/t)·V_t;  I_t = −(y/t)·V_f + (y + ysh_t)·V_t
        double aRe = yRe[k] / (t1 * t1) + shFG[k], aIm = yIm[k] / (t1 * t1) + shFB[k];
        double mRe = -yRe[k] / t1, mIm = -yIm[k] / t1;
        double cRe = yRe[k] + shTG[k], cIm = yIm[k] + shTB[k];
        double ifr = aRe * fr - aIm * fi + mRe * tr - mIm * ti;
        double ifi = aRe * fi + aIm * fr + mRe * ti + mIm * tr;
        double itr = mRe * fr - mIm * fi + cRe * tr - cIm * ti;
        double iti = mRe * fi + mIm * fr + cRe * ti + cIm * tr;
        return fr * ifr + fi * ifi + tr * itr + ti * iti;
    }

    // ================================================================ топология

    private void rebuildTopology() {
        int[] parent = new int[nb];
        for (int b = 0; b < nb; b++) parent[b] = b;
        for (int k = 0; k < nbr; k++) {
            if (!closed[k]) continue;
            int a = find(parent, bFrom[k]), c = find(parent, bTo[k]);
            if (a != c) parent[Math.max(a, c)] = Math.min(a, c);
        }
        int[] rootIsland = new int[nb];
        Arrays.fill(rootIsland, -1);
        List<List<Integer>> members = new ArrayList<>();
        for (int b = 0; b < nb; b++) {
            int r = find(parent, b);
            if (rootIsland[r] < 0) {
                rootIsland[r] = members.size();
                members.add(new ArrayList<>());
            }
            int id = rootIsland[r];
            busIsland[b] = id;
            busLocal[b] = members.get(id).size();
            members.get(id).add(b);
        }
        boolean[] wasLive = new boolean[nb];
        for (int b = 0; b < nb; b++) wasLive[b] = vRe[b] != 0 || vIm[b] != 0;
        Island[] next = new Island[members.size()];
        for (int id = 0; id < next.length; id++) {
            Island isl = new Island(id, members.get(id).stream().mapToInt(Integer::intValue).toArray());
            next[id] = isl;
        }
        List<List<Integer>> mach = lists(next.length), lds = lists(next.length), br = lists(next.length);
        List<List<Integer>> mot = lists(next.length);
        for (int i = 0; i < nm; i++) mach.get(busIsland[mBus[i]]).add(i);
        for (int j = 0; j < nl; j++) lds.get(busIsland[lBus[j]]).add(j);
        for (int j = 0; j < nmo; j++) mot.get(busIsland[moBus[j]]).add(j);
        for (int k = 0; k < nbr; k++) if (closed[k]) br.get(busIsland[bFrom[k]]).add(k);
        for (Island isl : next) {
            isl.machines = mach.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
            isl.loads = lds.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
            isl.motors = mot.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
            isl.branches = br.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
            isl.energized = isl.machines.length > 0;
            if (!isl.energized) {
                boolean live = false;
                for (int b : isl.buses) {
                    live |= wasLive[b];
                    vRe[b] = vIm[b] = vtRe[b] = vtIm[b] = 0;
                }
                isl.factored = true;
                if (live && initialized) event(EV_DEENERGIZED, isl.id);
            } else if (initialized) {
                rebaseAngles(isl);
            }
        }
        islands = next;
        topologyDirty = false;
        if (initialized) event(EV_TOPOLOGY, islands.length);
    }

    /**
     * Состав острова сменился: углы машин приводятся (сдвигом на 2π) к окрестности π самой тяжёлой,
     * отсчёт проворотов — от нового центра инерции. Иначе слияние островов считалось бы проворотом.
     */
    private void rebaseAngles(Island isl) {
        int ref = isl.machines[0];
        for (int i : isl.machines) if (mH[i] * mS[i] > mH[ref] * mS[ref]) ref = i;
        double hs = 0, hd = 0;
        for (int i : isl.machines) {
            delta[i] -= 2 * Math.PI * Math.rint((delta[i] - delta[ref]) / (2 * Math.PI));
            double w = mH[i] * mS[i];
            hs += w;
            hd += w * delta[i];
        }
        double dCoi = hd / hs;
        for (int i : isl.machines) slipIndex[i] = (int) Math.floor((delta[i] - dCoi + Math.PI) / (2 * Math.PI));
    }

    private static List<List<Integer>> lists(int n) {
        List<List<Integer>> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) l.add(new ArrayList<>());
        return l;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];
            x = parent[x];
        }
        return x;
    }

    /** Сборка Y острова (ветви, шунты, КЗ, проводимости машин) и LU — вне горячего цикла. */
    private void factorIsland(Island isl) {
        if (!isl.energized) {
            isl.factored = true;
            isl.yDirty = false;
            return;
        }
        int n = isl.buses.length;
        if (isl.lu == null) {
            List<List<Integer>> adj = lists(n);
            for (int k : isl.branches) {
                int a = busLocal[bFrom[k]], c = busLocal[bTo[k]];
                adj.get(a).add(c);
                adj.get(c).add(a);
            }
            int[][] adjacency = new int[n][];
            for (int a = 0; a < n; a++) adjacency[a] = adj.get(a).stream().mapToInt(Integer::intValue).toArray();
            isl.lu = new ComplexSparseLU(n, adjacency);
        }
        ComplexSparseLU lu = isl.lu;
        lu.clear();
        for (int k : isl.branches) {
            int a = busLocal[bFrom[k]], c = busLocal[bTo[k]];
            double t = tap[k];
            lu.add(lu.position(a, a), yRe[k] / (t * t) + shFG[k], yIm[k] / (t * t) + shFB[k]);
            lu.add(lu.position(c, c), yRe[k] + shTG[k], yIm[k] + shTB[k]);
            lu.add(lu.position(a, c), -yRe[k] / t, -yIm[k] / t);
            lu.add(lu.position(c, a), -yRe[k] / t, -yIm[k] / t);
        }
        for (int b : isl.buses) if (faultG[b] != 0) lu.add(lu.position(busLocal[b], busLocal[b]), faultG[b], 0);
        for (int i : isl.machines) lu.add(lu.position(busLocal[mBus[i]], busLocal[mBus[i]]), 0, -mYgen[i]);
        if (!lu.factor()) throw new IllegalStateException("singular admittance matrix in island " + isl.id);
        isl.factored = true;
        isl.yDirty = false;
    }

    // ================================================================ события и виды

    /** false — буфер полон, событие потеряно (старые события важнее). */
    private boolean event(int kind, int arg) {
        if (evCount == EVENT_CAPACITY) return false;
        evKind[evCount] = kind;
        evArg[evCount] = arg;
        evCount++;
        return true;
    }

    public void drainEvents(GridEventSink sink) {
        for (int e = 0; e < evCount; e++) {
            switch (evKind[e]) {
                case EV_TOPOLOGY -> sink.topologyChanged(evArg[e]);
                case EV_DEENERGIZED -> sink.islandDeenergized(evArg[e]);
                case EV_POLE_SLIP -> sink.poleSlip(evArg[e]);
                case EV_BREAKER -> sink.breakerStateChanged(evArg[e], BREAKER_STATES[evArg3[e]]);
                default -> {
                    int o = e * EVENT_VALUES;
                    SyncScope sc = new SyncScope(evVal[o], evVal[o + 1], evVal[o + 2], evVal[o + 3]);
                    sink.syncClosed(new SyncClosing(evArg[e], evArg2[e], sc, evVal[o + 4], evVal[o + 5],
                            SYNC_RESULTS[evArg3[e]], evVal[o + 6]));
                }
            }
        }
        evCount = 0;
    }

    public int islandCount() {
        return islands.length;
    }

    public int islandOf(BusId b) {
        return busIsland[bus(b)];
    }

    public IslandView island(int id) {
        Island isl = islands[id];
        double inertia = 0;
        for (int i : isl.machines) inertia += mH[i] * mS[i] * sb;
        return new IslandView(id, isl.energized, isl.energized && (!isl.factored || isl.yDirty), isl.buses.length,
                isl.machines.length, isl.fCoi, isl.rocof, isl.gen * sb, isl.load * sb, isl.loss * sb, inertia);
    }

    public double busVoltagePu(BusId b) {
        int i = bus(b);
        return Math.hypot(vRe[i], vIm[i]);
    }

    public double busVoltageV(BusId b) {
        return busVoltagePu(b) * busClass.get(bus(b)).volts;
    }

    public double busAngleRad(BusId b) {
        int i = bus(b);
        return Math.atan2(vIm[i], vRe[i]);
    }

    /** Частота шины по производной угла напряжения с фильтром 0,1 с (§7.1.4). */
    public double busFrequencyHz(BusId b) {
        return fBus[bus(b)];
    }

    public String busLabel(BusId b) {
        return busLabel.get(bus(b));
    }

    /** Активная мощность, втекающая в ветвь с конца {@code from}, Вт. */
    public double branchPowerFromW(BranchId id) {
        int k = branch(id), f = bFrom[k], t = bTo[k];
        if (!closed[k]) return 0;
        double t1 = tap[k];
        double aRe = yRe[k] / (t1 * t1) + shFG[k], aIm = yIm[k] / (t1 * t1) + shFB[k];
        double mRe = -yRe[k] / t1, mIm = -yIm[k] / t1;
        double ir = aRe * vRe[f] - aIm * vIm[f] + mRe * vRe[t] - mIm * vIm[t];
        double ii = aRe * vIm[f] + aIm * vRe[f] + mRe * vIm[t] + mIm * vRe[t];
        return (vRe[f] * ir + vIm[f] * ii) * sb;
    }

    /** Ток с конца {@code from}, А: I = i·S_b/(√3·U_b). */
    public double branchCurrentA(BranchId id) {
        int k = branch(id);
        return branchCurrentPu(k) * sb / (Math.sqrt(3) * busClass.get(bFrom[k]).volts);
    }

    private double branchCurrentPu(int k) {
        int f = bFrom[k], t = bTo[k];
        if (!closed[k]) return 0;
        double t1 = tap[k];
        double aRe = yRe[k] / (t1 * t1) + shFG[k], aIm = yIm[k] / (t1 * t1) + shFB[k];
        double mRe = -yRe[k] / t1, mIm = -yIm[k] / t1;
        double ir = aRe * vRe[f] - aIm * vIm[f] + mRe * vRe[t] - mIm * vIm[t];
        double ii = aRe * vIm[f] + aIm * vRe[f] + mRe * vIm[t] + mIm * vRe[t];
        return Math.hypot(ir, ii);
    }

    /** Активная мощность нагрузки на последнем решении, Вт. */
    public double loadPowerW(LoadId id) {
        int j = load(id), b = lBus[j];
        return (vRe[b] * liRe[j] + vIm[b] * liIm[j]) * sb;
    }

    public SynchronousMachine machine(MachineId id) {
        return machines.get(machineIndex(id));
    }

    public InductionMotorSpec motorSpec(MotorId id) {
        return motorSpec.get(motor(id));
    }

    /** Скорость ротора, о.е. синхронной при f₀. */
    public double motorSpeedPu(MotorId id) {
        return moW[motor(id)];
    }

    /** Частота вращения вала двигателя, рад/с. */
    public double motorShaftSpeedRadS(MotorId id) {
        int j = motor(id);
        return moW[j] * 2 * Math.PI * motorSpec.get(j).syncRpm() / 60;
    }

    /** Скольжение относительно частоты острова; обесточенный — 1. */
    public double motorSlip(MotorId id) {
        int j = motor(id);
        Island isl = islands[busIsland[moBus[j]]];
        return isl.energized ? 1 - moW[j] / isl.omegaCoi : 1;
    }

    /** Электромагнитный момент на последнем подшаге, о.е. на P_ном/ω_s. */
    public double motorTorquePu(MotorId id) {
        return moTe[motor(id)];
    }

    public double motorPowerW(MotorId id) {
        int j = motor(id), b = moBus[j];
        return (vRe[b] * moIRe[j] + vIm[b] * moIIm[j]) * sb;
    }

    public double motorReactiveVar(MotorId id) {
        int j = motor(id), b = moBus[j];
        return (vIm[b] * moIRe[j] - vRe[b] * moIIm[j]) * sb;
    }

    public double motorCurrentA(MotorId id) {
        int j = motor(id);
        return Math.hypot(moIRe[j], moIIm[j]) * sb / (Math.sqrt(3) * busClass.get(moBus[j]).volts);
    }

    public MotorState motorState(MotorId id) {
        int j = motor(id);
        Island isl = islands[busIsland[moBus[j]]];
        if (!moOn[j] || !isl.energized) return MotorState.OFF;
        if (1 - moW[j] / isl.omegaCoi <= motorSpec.get(j).sK()) return MotorState.RUNNING;
        return moTe[j] > moTlUsed[j] ? MotorState.STARTING : MotorState.STALLED;
    }

    public int busCount() {
        return nb;
    }

    public int branchCount() {
        return nbr;
    }

    public int machineCount() {
        return nm;
    }

    public int loadCount() {
        return nl;
    }

    public int motorCount() {
        return nmo;
    }

    // ================================================================ служебное

    /** Номинальная P и Q нагрузки на шине при напряжении v и f = f₀, о.е. на S_b (для потокораспределения). */
    double busLoadP(int b, double v) {
        double s = 0;
        for (int j = 0; j < nl; j++) if (lBus[j] == b && lOn[j]) s += lp0[j] * zip(j, v);
        for (int j = 0; j < nmo; j++) {
            if (moBus[j] != b || !moOn[j]) continue;
            double vm2 = v * v * moVr2[j];
            s += motorP(j, motorSpec.get(j).slipAt(moTl[j], Math.sqrt(vm2)), vm2) * moScale[j];
        }
        return s;
    }

    double busLoadQ(int b, double v) {
        double s = 0;
        for (int j = 0; j < nl; j++) if (lBus[j] == b && lOn[j]) s += lq0[j] * zip(j, v);
        for (int j = 0; j < nmo; j++) {
            if (moBus[j] != b || !moOn[j]) continue;
            double vm2 = v * v * moVr2[j];
            s += motorQ(j, motorSpec.get(j).slipAt(moTl[j], Math.sqrt(vm2)), vm2) * moScale[j];
        }
        return s;
    }

    private double zip(int j, double v) {
        if (v >= LOW_VOLTAGE_PU) return laZ[j] * v * v + laI[j] * v + laP[j];
        double l = LOW_VOLTAGE_PU;
        return (laZ[j] * l * l + laI[j] * l + laP[j]) * v * v / (l * l);
    }

    int bus(BusId id) {
        Objects.requireNonNull(id, "bus");
        if (id.index() >= nb) throw new IllegalArgumentException("unknown bus " + id.index());
        return id.index();
    }

    private int branch(BranchId id) {
        Objects.requireNonNull(id, "branch");
        if (id.index() >= nbr) throw new IllegalArgumentException("unknown branch " + id.index());
        return id.index();
    }

    private int machineIndex(MachineId id) {
        Objects.requireNonNull(id, "machine");
        if (id.index() >= nm) throw new IllegalArgumentException("unknown machine " + id.index());
        return id.index();
    }

    private int load(LoadId id) {
        Objects.requireNonNull(id, "load");
        if (id.index() >= nl) throw new IllegalArgumentException("unknown load " + id.index());
        return id.index();
    }

    private int motor(MotorId id) {
        Objects.requireNonNull(id, "motor");
        if (id.index() >= nmo) throw new IllegalArgumentException("unknown motor " + id.index());
        return id.index();
    }

    /** Ветвь с выключателем ({@link #attachBreaker}). */
    private int breaker(BranchId id) {
        int k = branch(id);
        if (brState[k] == null) throw new IllegalStateException("branch " + k + " has no breaker");
        return k;
    }

    Island[] islands() {
        return islands;
    }

    boolean initialized() {
        return initialized;
    }

    void setVoltageReference(int i, double pu) {
        vSetPu[i] = pu;
        if (initialized) vref[i] = pu + efd[i] / exK[i];
    }

    void setLoadTarget(int i, double watts) {
        pTargetW[i] = watts;
        if (!createShaft[i]) valve0[i] = watts / (gvRated[i] * mS[i] * sb);
    }

    /** Безударно: из {@code MANUAL} АРВ подхватывает текущее E_fd, уставки Q и cos φ — текущие. */
    void setExcitationMode(int i, ExcitationMode mode) {
        if (mode == ExcitationMode.MANUAL) {
            efdManual[i] = efd[i];
        } else if (excMode[i] == ExcitationMode.MANUAL) {
            xLL[i] = efd[i] / exK[i];
            vref[i] = vt[i] + xLL[i];
        }
        if (mode == ExcitationMode.REACTIVE) qRef[i] = qe[i] / mS[i];
        if (mode == ExcitationMode.POWER_FACTOR) qPerP[i] = pe[i] != 0 ? qe[i] / pe[i] : 0;
        excMode[i] = mode;
    }

    void setFieldVoltage(int i, double pu) {
        if (excMode[i] != ExcitationMode.MANUAL)
            throw new IllegalStateException("field voltage is set by the AVR; switch to MANUAL first");
        efdManual[i] = pu;
    }

    /** Q_max(P) по статору и ротору (§7.3.4), о.е. на S_ном. */
    double maxReactivePu(int i, double p, double v) {
        double stator = Math.sqrt(Math.max(0, 1 - p * p));
        double rotor = Math.sqrt(Math.max(0, Math.pow(v * ifNom[i] / mXd[i], 2) - p * p)) - v * v / mXd[i];
        return Math.min(stator, rotor);
    }

    /** Q_min(P) по статору и UEL (§7.3.4), о.е. на S_ном. */
    double minReactivePu(int i, double p, double v) {
        return Math.max(-Math.sqrt(Math.max(0, 1 - p * p)), -v * v / mXd[i] + K_UEL * p);
    }

    void setReactiveTarget(int i, double qPu) {
        double p = pe[i] / mS[i];
        if (qPu > maxReactivePu(i, p, vt[i]) || qPu < minReactivePu(i, p, vt[i]))
            throw new IllegalArgumentException("reactive target outside the capability curve");
        qRef[i] = qPu;
    }

    /** cos φ > 0 — выдача Q (отстающий), < 0 — потребление. */
    void setPowerFactorTarget(int i, double pf) {
        if (!(Math.abs(pf) >= 0.5 && Math.abs(pf) <= 1)) throw new IllegalArgumentException("power factor");
        double k = Math.signum(pf) * Math.tan(Math.acos(Math.abs(pf))), p = pe[i] / mS[i];
        if (p * k > maxReactivePu(i, p, vt[i]) || p * k < minReactivePu(i, p, vt[i]))
            throw new IllegalArgumentException("power factor target outside the capability curve");
        qPerP[i] = k;
    }

    MachineState machineState(int i) {
        if (shaftDmg[i] >= 1) return MachineState.DAMAGED;
        if (slips[i] > 0) return MachineState.ASYNC;
        boolean loaded = pe[i] > 0.05 * mS[i];
        if (!loaded && islands[busIsland[mBus[i]]].machines.length == 1)
            return eq[i] > 0.1 ? MachineState.EXCITED : MachineState.ROLLING;
        return loaded ? MachineState.LOADED : MachineState.SYNCHRONIZED;
    }

    /** Срезной штифт {@code generator_coupling}; установка новой муфты соединяет агрегат, если вал цел. */
    void setShearPinCoupling(int i, boolean installed) {
        pinCoupling[i] = installed;
        if (installed && shaftDmg[i] < 1) sheared[i] = false;
    }

    void repairCoupling(int i) {
        if (shaftDmg[i] >= 1) throw new IllegalStateException("the shaft is broken");
        sheared[i] = false;
    }

    private void growMachines() {
        int n = nm;
        mBus = grow(mBus, n);
        slipIndex = grow(slipIndex, n);
        slips = grow(slips, n);
        createShaft = grow(createShaft, n);
        sheared = grow(sheared, n);
        pinCoupling = grow(pinCoupling, n);
        oelOn = grow(oelOn, n);
        excMode = grow(excMode, n);
        mS = grow(mS, n);
        mXd = grow(mXd, n);
        mXdP = grow(mXdP, n);
        mXdPP = grow(mXdPP, n);
        ifNom = grow(ifNom, n);
        qRef = grow(qRef, n);
        qPerP = grow(qPerP, n);
        oelAcc = grow(oelAcc, n);
        uelBias = grow(uelBias, n);
        shaftDmg = grow(shaftDmg, n);
        statorDmg = grow(statorDmg, n);
        mYgen = grow(mYgen, n);
        mTd0 = grow(mTd0, n);
        mH = grow(mH, n);
        mD = grow(mD, n);
        exK = grow(exK, n);
        exTe = grow(exTe, n);
        exR = grow(exR, n);
        exTb = grow(exTb, n);
        exMin = grow(exMin, n);
        exMax = grow(exMax, n);
        gvRated = grow(gvRated, n);
        gvDroop = grow(gvDroop, n);
        gvTs = grow(gvTs, n);
        gvOpen = grow(gvOpen, n);
        gvClose = grow(gvClose, n);
        gvFast = grow(gvFast, n);
        gvMin = grow(gvMin, n);
        delta = grow(delta, n);
        omega = grow(omega, n);
        eq = grow(eq, n);
        efd = grow(efd, n);
        xLL = grow(xLL, n);
        vref = grow(vref, n);
        efdManual = grow(efdManual, n);
        valve = grow(valve, n);
        valve0 = grow(valve0, n);
        pm = grow(pm, n);
        pe = grow(pe, n);
        qe = grow(qe, n);
        ioRe = grow(ioRe, n);
        ioIm = grow(ioIm, n);
        vt = grow(vt, n);
        pTargetW = grow(pTargetW, n);
        vSetPu = grow(vSetPu, n);
    }

    private static double[] grow(double[] a, int n) {
        return n <= a.length ? a : Arrays.copyOf(a, Math.max(n, 2 * a.length));
    }

    private static int[] grow(int[] a, int n) {
        return n <= a.length ? a : Arrays.copyOf(a, Math.max(n, 2 * a.length));
    }

    private static boolean[] grow(boolean[] a, int n) {
        return n <= a.length ? a : Arrays.copyOf(a, Math.max(n, 2 * a.length));
    }

    private static <T> T[] grow(T[] a, int n) {
        return n <= a.length ? a : Arrays.copyOf(a, Math.max(n, 2 * a.length));
    }

    /** Электрический остров: шины, связанные замкнутыми ветвями (§7.1.3). */
    static final class Island {
        final int id;
        final int[] buses;
        int[] machines, loads, motors, branches;
        final double[] bRe, bIm;
        ComplexSparseLU lu;
        boolean energized, factored, yDirty;
        double omegaCoi = 1, fCoi, rocof, gen, load, loss, solverStep;

        Island(int id, int[] buses) {
            this.id = id;
            this.buses = buses;
            bRe = new double[buses.length];
            bIm = new double[buses.length];
        }

        /** Решение сети считается: остров под напряжением и разложен. */
        boolean solvable() {
            return energized && factored && !yDirty;
        }
    }
}
