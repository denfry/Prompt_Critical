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
 * <p>Каркас M1: нет несимметричных КЗ, РЗА, выключателей с временем хода, двигательной нагрузки,
 * тепла трансформаторов и режимов АРВ {@code REACTIVE}/{@code POWER_FACTOR} — это M3.
 */
public final class PowerGrid {
    /** Ниже этого напряжения P и I-доли нагрузки ведут себя как сопротивление (PSS/E «PQ → Z»). */
    static final double LOW_VOLTAGE_PU = 0.7;
    /** Фильтр частоты шины, как у цифровых реле частоты (§7.1.4). */
    static final double FREQUENCY_FILTER_S = 0.1;
    static final double MIN_FAULT_IMPEDANCE_PU = 1e-6;
    private static final double MIN_SPEED = 0.1;
    private static final int EVENT_CAPACITY = 256;
    private static final int EV_TOPOLOGY = 0, EV_DEENERGIZED = 1, EV_POLE_SLIP = 2;

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

    // ---------------------------------------------------------------- машины
    int nm;
    private final List<SynchronousMachine> machines = new ArrayList<>();
    int[] mBus = new int[0], slipIndex = new int[0], slips = new int[0];
    boolean[] createShaft = new boolean[0], manual = new boolean[0];
    double[] mS = new double[0], mXd = new double[0], mXdP = new double[0], mYgen = new double[0];
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

    // ---------------------------------------------------------------- острова
    private Island[] islands = new Island[0];
    private boolean topologyDirty = true, initialized;

    private final int[] evKind = new int[EVENT_CAPACITY], evArg = new int[EVENT_CAPACITY];
    private int evCount;

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
        mYgen[i] = mS[i] / p.xdP(); // 1/x'_d на S_b
        mTd0[i] = p.td0PS();
        mH[i] = p.hS();
        mD[i] = p.dPu();
        exK[i] = e.k();
        exTe[i] = e.teS();
        exR[i] = e.taOverTb();
        exTb[i] = e.tbS();
        exMin[i] = e.efdMin();
        exMax[i] = e.efdMax();
        if (e.defaultMode() == ExcitationMode.REACTIVE || e.defaultMode() == ExcitationMode.POWER_FACTOR)
            throw new UnsupportedOperationException("excitation mode " + e.defaultMode() + " is not implemented yet");
        manual[i] = e.defaultMode() == ExcitationMode.MANUAL;
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

    // ================================================================ команды

    /** Меняет топологию → переразложение в начале следующего тика (§7.1.3). */
    public void setBreaker(BranchId id, boolean close) {
        int k = branch(id);
        if (closed[k] != close) {
            closed[k] = close;
            topologyDirty = true;
        }
    }

    public boolean isBreakerClosed(BranchId id) {
        return closed[branch(id)];
    }

    /** Отключение и подключение нагрузки (АЧР, потребитель) — без переразложения. */
    public void setLoadConnected(LoadId id, boolean on) {
        lOn[load(id)] = on;
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

        if (manual[i]) {
            efd[i] = efdManual[i];
        } else {
            double err = vref[i] - vt[i];
            double r = exR[i];
            double ll = r * err + (1 - r) * xLL[i];
            xLL[i] += (err - xLL[i]) * (1 - Math.exp(-h / exTb[i]));
            double e = efd[i] + (exK[i] * ll - efd[i]) * (1 - Math.exp(-h / exTe[i]));
            efd[i] = Math.max(exMin[i], Math.min(exMax[i], e));
        }
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
        for (int i = 0; i < nm; i++) mach.get(busIsland[mBus[i]]).add(i);
        for (int j = 0; j < nl; j++) lds.get(busIsland[lBus[j]]).add(j);
        for (int k = 0; k < nbr; k++) if (closed[k]) br.get(busIsland[bFrom[k]]).add(k);
        for (Island isl : next) {
            isl.machines = mach.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
            isl.loads = lds.get(isl.id).stream().mapToInt(Integer::intValue).toArray();
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
            }
        }
        islands = next;
        topologyDirty = false;
        if (initialized) event(EV_TOPOLOGY, islands.length);
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

    private void event(int kind, int arg) {
        if (evCount == EVENT_CAPACITY) return; // переполнение: старые события важнее
        evKind[evCount] = kind;
        evArg[evCount] = arg;
        evCount++;
    }

    public void drainEvents(GridEventSink sink) {
        for (int e = 0; e < evCount; e++) {
            switch (evKind[e]) {
                case EV_TOPOLOGY -> sink.topologyChanged(evArg[e]);
                case EV_DEENERGIZED -> sink.islandDeenergized(evArg[e]);
                default -> sink.poleSlip(evArg[e]);
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
        int k = branch(id), f = bFrom[k], t = bTo[k];
        if (!closed[k]) return 0;
        double t1 = tap[k];
        double aRe = yRe[k] / (t1 * t1) + shFG[k], aIm = yIm[k] / (t1 * t1) + shFB[k];
        double mRe = -yRe[k] / t1, mIm = -yIm[k] / t1;
        double ir = aRe * vRe[f] - aIm * vIm[f] + mRe * vRe[t] - mIm * vIm[t];
        double ii = aRe * vIm[f] + aIm * vRe[f] + mRe * vIm[t] + mIm * vRe[t];
        return Math.hypot(ir, ii) * sb / (Math.sqrt(3) * busClass.get(f).volts);
    }

    /** Активная мощность нагрузки на последнем решении, Вт. */
    public double loadPowerW(LoadId id) {
        int j = load(id), b = lBus[j];
        return (vRe[b] * liRe[j] + vIm[b] * liIm[j]) * sb;
    }

    public SynchronousMachine machine(MachineId id) {
        return machines.get(machineIndex(id));
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

    // ================================================================ служебное

    /** Номинальная P и Q нагрузки на шине при напряжении v и f = f₀, о.е. на S_b (для потокораспределения). */
    double busLoadP(int b, double v) {
        double s = 0;
        for (int j = 0; j < nl; j++) if (lBus[j] == b && lOn[j]) s += lp0[j] * zip(j, v);
        return s;
    }

    double busLoadQ(int b, double v) {
        double s = 0;
        for (int j = 0; j < nl; j++) if (lBus[j] == b && lOn[j]) s += lq0[j] * zip(j, v);
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

    void setExcitationMode(int i, ExcitationMode mode) {
        switch (mode) {
            case MANUAL -> {
                manual[i] = true;
                efdManual[i] = efd[i];
            }
            case VOLTAGE -> {
                if (manual[i]) {
                    manual[i] = false;
                    xLL[i] = efd[i] / exK[i];
                    vref[i] = vt[i] + xLL[i];
                }
            }
            default -> throw new UnsupportedOperationException("excitation mode " + mode + " is not implemented yet");
        }
    }

    void setFieldVoltage(int i, double pu) {
        if (!manual[i]) throw new IllegalStateException("field voltage is set by the AVR; switch to MANUAL first");
        efdManual[i] = pu;
    }

    private void growMachines() {
        int n = nm;
        mBus = grow(mBus, n);
        slipIndex = grow(slipIndex, n);
        slips = grow(slips, n);
        createShaft = grow(createShaft, n);
        manual = grow(manual, n);
        mS = grow(mS, n);
        mXd = grow(mXd, n);
        mXdP = grow(mXdP, n);
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

    /** Электрический остров: шины, связанные замкнутыми ветвями (§7.1.3). */
    static final class Island {
        final int id;
        final int[] buses;
        int[] machines, loads, branches;
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
