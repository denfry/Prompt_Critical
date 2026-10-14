package dev.denfry.promptcritical.sim.hydraulics;

import dev.denfry.promptcritical.sim.thermo.FluidPackage;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.VoidFraction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Реализация {@link FluidNetwork}: полунеявная схема 01 §1.2.6 на SoA-массивах (§1.12.3).
 *
 * <p>Узлы упорядочены так, что каждая компонента связности занимает непрерывный отрезок
 * индексов, граничные узлы — в конце. Компонента — своя матрица {@link SparseLU}, свой
 * адаптивный шаг (§1.2.9) и свой сон (§1.12.2). Горячий цикл не аллоцирует; карты id →
 * индекс используются только API и пересборкой.
 *
 * <p>Шаг компоненты: (1) импульс каждой связи линеаризуется в ṁ = a + b·(P_from − P_to)
 * с донорной энтальпией, гравитацией, трением и напором насоса на старом шаге; (2) система
 * по давлению A·P = r решается LU, связи с превышением критического расхода фиксируются на
 * пределе и система решается снова; (3) M, U и скаляры обновляются консервативно этими
 * расходами; (4) давление берётся из EOS по (M, U), расхождение с линейным прогнозом больше
 * {@code max_linearization_error} — откат и половинный шаг.
 */
final class LumpedFluidNetwork implements FluidNetwork {
    private static final double G = VoidFraction.GRAVITY;
    /** C_mat материального Куранта (§1.2.9). */
    private static final double C_MAT = 0.5;
    /** Δt_min = Δt_тика / 64 (§1.2.9). */
    private static final int MIN_STEP_DIVISOR = 64;
    private static final int SLEEP_TICKS = 40;
    private static final double SLEEP_MASS_FLOW = 1e-3, SLEEP_DP = 10, SLEEP_HEAT = 1e3;
    /** Узел осушен при M &lt; 10⁻³·V·ρ_g (§1.2.9). */
    private static final double VOID_MASS_FACTOR = 1e-3;
    private static final int CHOKE_ITERATIONS = 2;
    private static final double[] NO_FRACTIONS = new double[0];

    private final FluidPackage fluid;
    private final HydraulicsConfig cfg;
    private final ThermoState st = new ThermoState();
    private final List<PassiveScalar> scalars = new ArrayList<>();
    private final Map<String, HomologousCurve> curves = new HashMap<>();
    private HydraulicEventSink sink = HydraulicEventSink.NONE;

    private final Map<Long, Integer> nodeIndex = new HashMap<>(), linkIndex = new HashMap<>();
    private NodeView[] nodeViews = new NodeView[0];
    private LinkView[] linkViews = new LinkView[0];
    private Component[] comps = new Component[0];
    /** Компоненты, примыкающие к граничному узлу (пробуждение при смене граничных условий). */
    private int[][] boundaryComps = new int[0][];

    // ---------------------------------------------------------------- узлы
    private int nn, ns;
    private long[] nodeId = new long[0];
    private NodeKind[] kind = new NodeKind[0];
    private double[] vol, zBot, hgt, xsec;
    private long[][] blocks = new long[0][];
    private double[] mass, energy, scal; // scal[k * nn + i]; у BOUNDARY — массовая доля
    private double[] p, t, rho, h, xe, alpha, rhoF, rhoG, hF, hG, mu, muG, sigma, dPdM, dPdU;
    private boolean[] twoPhase, voided;
    /** Абсолютная отметка уровня смеси и коллапсированного уровня; плотность слоя под уровнем. */
    private double[] lvl, lvlCollapsed, rhoPool, xBelow;
    private double[] heat, srcM, srcE, work, flowSum;
    private double[] mass0, energy0, scal0, p0, pLin, pTick;
    private int[] comp;

    // ---------------------------------------------------------------- связи
    private int nl;
    private long[] linkId = new long[0];
    private LinkKind[] lkind = new LinkKind[0];
    private int[] from, to;
    private double[] inertia, flen, dh, area, zeta0, rough, zjF, zjT, physLen;
    private double[] mdot, mdotNew, a, b, hs, xs, rhoS, re, c0, c1, pumpDp, chokeLimit;
    /**
     * Плотность столба жидкости в связи для гравитационного напора. Донорная плотность
     * (§1.2.2) приходит в связь не мгновенно, а по мере промывки её объёма: иначе при смене
     * направления расхода столб мгновенно «перекрашивается», и, например, расширение при
     * прогреве, идущее из нагревателя в обе ветви контура, обнуляет движущий напор
     * естественной циркуляции. Это одна «пробка» линии задержки §1.12.1.
     */
    private double[] rhoCol;
    private LinkRegime[] regime;
    private boolean[] chokeable, choked;
    private ValveModel.Spec[] valve;
    private double[] vPos, vCmd, vStrokeVel, vStrokeTime;
    private boolean[] vClosing;
    private PumpModel[] pump;

    // ---------------------------------------------------------------- баланс
    private double extMass, extEnergy, pumpWork, trialExtMass, trialExtEnergy;

    LumpedFluidNetwork(FluidPackage fluid, HydraulicsConfig config) {
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.cfg = Objects.requireNonNull(config, "config");
        if (fluid.componentCount() != 0) {
            throw new IllegalArgumentException("multicomponent fluid packages are not supported yet: " + fluid.id());
        }
        curves.put(HomologousCurve.DEFAULT_ID, HomologousCurve.defaultCentrifugal());
        rebuild(List.of(), List.of(), StateCarryOver.uniform(1e5, 4e5));
    }

    // ================================================================ сборка

    @Override
    public void rebuild(List<FluidNodeSpec> nodeSpecs, List<FlowLinkSpec> linkSpecs, StateCarryOver carry) {
        Objects.requireNonNull(carry, "carry");
        Map<Long, FluidNodeSpec> byId = new HashMap<>();
        for (FluidNodeSpec s : nodeSpecs) {
            validate(s);
            if (byId.put(s.id(), s) != null) throw new IllegalArgumentException("duplicate node id " + s.id());
        }
        Set<Long> linkIds = new HashSet<>();
        for (FlowLinkSpec l : linkSpecs) {
            if (!linkIds.add(l.id())) throw new IllegalArgumentException("duplicate link id " + l.id());
            validate(l, byId);
        }

        // компоненты связности по неграничным узлам (union-find)
        List<FluidNodeSpec> sorted = new ArrayList<>(nodeSpecs);
        sorted.sort(Comparator.comparingLong(FluidNodeSpec::id));
        Map<Long, Integer> pos = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) pos.put(sorted.get(i).id(), i);
        int[] parent = new int[sorted.size()];
        for (int i = 0; i < parent.length; i++) parent[i] = i;
        for (FlowLinkSpec l : linkSpecs) {
            int x = pos.get(l.fromNode()), y = pos.get(l.toNode());
            if (sorted.get(x).kind() != NodeKind.BOUNDARY && sorted.get(y).kind() != NodeKind.BOUNDARY) {
                int rx = find(parent, x), ry = find(parent, y);
                if (rx != ry) parent[Math.max(rx, ry)] = Math.min(rx, ry);
            }
        }
        // порядок: компоненты по наименьшему id, внутри — по id; граничные — в конце
        List<FluidNodeSpec> order = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();
        Map<Integer, List<FluidNodeSpec>> groups = new java.util.TreeMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).kind() != NodeKind.BOUNDARY) {
                groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(sorted.get(i));
            }
        }
        for (List<FluidNodeSpec> g : groups.values()) {
            ranges.add(new int[] {order.size(), order.size() + g.size()});
            order.addAll(g);
        }
        for (FluidNodeSpec s : sorted) if (s.kind() == NodeKind.BOUNDARY) order.add(s);
        List<FlowLinkSpec> lorder = new ArrayList<>(linkSpecs);
        lorder.sort(Comparator.comparingLong(FlowLinkSpec::id));

        OldState old = nn > 0 || nl > 0 ? new OldState() : null;
        allocate(order.size(), lorder.size(), scalars.size());

        nodeIndex.clear();
        for (int i = 0; i < nn; i++) {
            FluidNodeSpec s = order.get(i);
            nodeId[i] = s.id();
            kind[i] = s.kind();
            vol[i] = s.volume();
            zBot[i] = s.elevationBottom();
            hgt[i] = s.height();
            xsec[i] = s.crossSection();
            blocks[i] = s.sourceBlockKeys();
            nodeIndex.put(s.id(), i);
        }
        linkIndex.clear();
        for (int j = 0; j < nl; j++) {
            FlowLinkSpec l = lorder.get(j);
            linkId[j] = l.id();
            lkind[j] = l.kind();
            from[j] = nodeIndex.get(l.fromNode());
            to[j] = nodeIndex.get(l.toNode());
            inertia[j] = l.inertia();
            flen[j] = l.length();
            dh[j] = l.hydraulicDiameter();
            area[j] = l.area();
            zeta0[j] = l.formLossSum();
            rough[j] = l.roughness();
            zjF[j] = l.junctionElevationFrom();
            zjT[j] = l.junctionElevationTo();
            physLen[j] = l.inertia() * l.area();
            valve[j] = l.valve();
            chokeable[j] = l.kind() == LinkKind.BREAK || l.kind() == LinkKind.VALVE_RELIEF
                    || kind[from[j]] == NodeKind.BOUNDARY || kind[to[j]] == NodeKind.BOUNDARY;
            if (l.kind() == LinkKind.PUMP) {
                HomologousCurve c = curves.get(l.pump().homologousCurveId());
                if (c == null) throw new IllegalArgumentException("unknown homologous curve " + l.pump().homologousCurveId());
                pump[j] = new PumpModel(l.pump(), c, carry.initialPumpSpeed(l.id()));
            }
            vPos[j] = vCmd[j] = l.kind() == LinkKind.VALVE_RELIEF ? 0 : 1;
            mdot[j] = carry.initialMassFlow(l.id());
            regime[j] = l.kind() == LinkKind.VALVE_RELIEF ? LinkRegime.CLOSED : LinkRegime.NORMAL;
            linkIndex.put(l.id(), j);
        }

        buildComponents(ranges);
        initializeState(order, carry, old);
        nodeViews = new NodeView[nn];
        for (int i = 0; i < nn; i++) nodeViews[i] = new NodeView(i);
        linkViews = new LinkView[nl];
        for (int j = 0; j < nl; j++) linkViews[j] = new LinkView(j);
        extMass = extEnergy = pumpWork = 0;
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) x = parent[x] = parent[parent[x]];
        return x;
    }

    private static void validate(FluidNodeSpec s) {
        switch (s.kind()) {
            case TWO_REGION, GAS_MIXTURE -> throw new UnsupportedOperationException(
                    "node kind " + s.kind() + " is not supported by the solver yet (node " + s.id() + ")");
            case BOUNDARY -> {}
            case HEM -> {
                if (!(s.volume() > 0 && s.height() >= 0)) throw new IllegalArgumentException("bad HEM node " + s.id());
            }
            case SEPARATED -> {
                if (!(s.volume() > 0 && s.height() > 0 && s.crossSection() > 0)) {
                    throw new IllegalArgumentException("bad SEPARATED node " + s.id());
                }
            }
        }
    }

    private static void validate(FlowLinkSpec l, Map<Long, FluidNodeSpec> nodes) {
        FluidNodeSpec f = nodes.get(l.fromNode()), t = nodes.get(l.toNode());
        if (f == null || t == null) throw new IllegalArgumentException("link " + l.id() + " references a missing node");
        if (l.fromNode() == l.toNode()) throw new IllegalArgumentException("link " + l.id() + " is a self-loop");
        if (f.kind() == NodeKind.BOUNDARY && t.kind() == NodeKind.BOUNDARY) {
            throw new IllegalArgumentException("link " + l.id() + " connects two boundary nodes");
        }
        if (!(l.inertia() > 0 && l.area() > 0 && l.length() >= 0 && l.hydraulicDiameter() >= 0
                && l.formLossSum() >= 0 && l.roughness() >= 0)) {
            throw new IllegalArgumentException("bad geometry of link " + l.id());
        }
        if (l.length() > 0 && !(l.hydraulicDiameter() > 0)) {
            throw new IllegalArgumentException("link " + l.id() + " with friction length needs a hydraulic diameter");
        }
        boolean isValve = l.kind() == LinkKind.VALVE_GATE || l.kind() == LinkKind.VALVE_CHECK
                || l.kind() == LinkKind.VALVE_RELIEF || l.kind() == LinkKind.VALVE_CONTROL;
        if ((l.kind() == LinkKind.PUMP) != (l.pump() != null)) {
            throw new IllegalArgumentException("pump spec must be given exactly for PUMP links (" + l.id() + ")");
        }
        if (isValve != (l.valve() != null)) {
            throw new IllegalArgumentException("valve spec must be given exactly for valve links (" + l.id() + ")");
        }
        if (l.kind() == LinkKind.VALVE_RELIEF && !(l.valve().setPressure() > 0)) {
            throw new IllegalArgumentException("relief valve " + l.id() + " needs a set pressure");
        }
    }

    private void allocate(int nodes, int links, int nScalars) {
        nn = nodes;
        ns = nScalars;
        nodeId = new long[nn];
        kind = new NodeKind[nn];
        blocks = new long[nn][];
        vol = new double[nn];
        zBot = new double[nn];
        hgt = new double[nn];
        xsec = new double[nn];
        mass = new double[nn];
        energy = new double[nn];
        scal = new double[ns * nn];
        scal0 = new double[ns * nn];
        p = new double[nn];
        t = new double[nn];
        rho = new double[nn];
        h = new double[nn];
        xe = new double[nn];
        alpha = new double[nn];
        rhoF = new double[nn];
        rhoG = new double[nn];
        hF = new double[nn];
        hG = new double[nn];
        mu = new double[nn];
        muG = new double[nn];
        sigma = new double[nn];
        dPdM = new double[nn];
        dPdU = new double[nn];
        twoPhase = new boolean[nn];
        voided = new boolean[nn];
        lvl = new double[nn];
        lvlCollapsed = new double[nn];
        rhoPool = new double[nn];
        xBelow = new double[nn];
        heat = new double[nn];
        srcM = new double[nn];
        srcE = new double[nn];
        work = new double[nn];
        flowSum = new double[nn];
        mass0 = new double[nn];
        energy0 = new double[nn];
        p0 = new double[nn];
        pLin = new double[nn];
        pTick = new double[nn];
        comp = new int[nn];

        nl = links;
        linkId = new long[nl];
        lkind = new LinkKind[nl];
        from = new int[nl];
        to = new int[nl];
        inertia = new double[nl];
        flen = new double[nl];
        dh = new double[nl];
        area = new double[nl];
        zeta0 = new double[nl];
        rough = new double[nl];
        zjF = new double[nl];
        zjT = new double[nl];
        physLen = new double[nl];
        mdot = new double[nl];
        rhoCol = new double[nl];
        mdotNew = new double[nl];
        a = new double[nl];
        b = new double[nl];
        hs = new double[nl];
        xs = new double[nl];
        rhoS = new double[nl];
        re = new double[nl];
        c0 = new double[nl];
        c1 = new double[nl];
        pumpDp = new double[nl];
        chokeLimit = new double[nl];
        regime = new LinkRegime[nl];
        chokeable = new boolean[nl];
        choked = new boolean[nl];
        valve = new ValveModel.Spec[nl];
        vPos = new double[nl];
        vCmd = new double[nl];
        vStrokeVel = new double[nl];
        vStrokeTime = new double[nl];
        vClosing = new boolean[nl];
        pump = new PumpModel[nl];
    }

    private void buildComponents(List<int[]> ranges) {
        Arrays.fill(comp, -1);
        comps = new Component[ranges.size()];
        List<List<Integer>> compLinks = new ArrayList<>();
        for (int c = 0; c < comps.length; c++) {
            for (int i = ranges.get(c)[0]; i < ranges.get(c)[1]; i++) comp[i] = c;
            compLinks.add(new ArrayList<>());
        }
        for (int j = 0; j < nl; j++) compLinks.get(comp[from[j]] >= 0 ? comp[from[j]] : comp[to[j]]).add(j);
        for (int c = 0; c < comps.length; c++) {
            comps[c] = new Component(ranges.get(c)[0], ranges.get(c)[1],
                    compLinks.get(c).stream().mapToInt(Integer::intValue).toArray(), from, to, comp);
        }
        boundaryComps = new int[nn][];
        for (int i = 0; i < nn; i++) {
            if (kind[i] != NodeKind.BOUNDARY) continue;
            Set<Integer> s = new java.util.TreeSet<>();
            for (int j = 0; j < nl; j++) {
                if (from[j] == i) s.add(comp[to[j]]);
                if (to[j] == i) s.add(comp[from[j]]);
            }
            boundaryComps[i] = s.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    /** Снимок прежней сети для переноса состояния. */
    private final class OldState {
        final Map<Long, Integer> nodes = new HashMap<>(nodeIndex), links = new HashMap<>(linkIndex);
        final long[] nodeId = LumpedFluidNetwork.this.nodeId;
        final long[][] blocks = LumpedFluidNetwork.this.blocks;
        final double[] mass = LumpedFluidNetwork.this.mass, energy = LumpedFluidNetwork.this.energy;
        final double[] scal = LumpedFluidNetwork.this.scal, p = LumpedFluidNetwork.this.p, h = LumpedFluidNetwork.this.h;
        final double[] mdot = LumpedFluidNetwork.this.mdot, rhoCol = LumpedFluidNetwork.this.rhoCol;
        final double[] vPos = LumpedFluidNetwork.this.vPos;
        final double[] vCmd = LumpedFluidNetwork.this.vCmd;
        final LinkRegime[] regime = LumpedFluidNetwork.this.regime;
        final PumpModel[] pump = LumpedFluidNetwork.this.pump;
        final NodeKind[] kind = LumpedFluidNetwork.this.kind;
        final int nn = LumpedFluidNetwork.this.nn, ns = LumpedFluidNetwork.this.ns;
    }

    private void initializeState(List<FluidNodeSpec> order, StateCarryOver carry, OldState old) {
        // доли удалённых узлов по блокам: блок → прежний узел
        Map<Long, Integer> blockOwner = new HashMap<>();
        Set<Long> keptIds = new HashSet<>();
        for (int i = 0; i < nn; i++) keptIds.add(nodeId[i]);
        if (old != null) {
            for (int o = 0; o < old.nn; o++) {
                if (keptIds.contains(old.nodeId[o]) || old.kind[o] == NodeKind.BOUNDARY) continue;
                for (long blk : old.blocks[o]) blockOwner.put(blk, o);
            }
        }
        for (int i = 0; i < nn; i++) {
            FluidNodeSpec s = order.get(i);
            Integer o = old == null ? null : old.nodes.get(s.id());
            if (kind[i] == NodeKind.BOUNDARY) {
                double pb = o != null && old.kind[o] == NodeKind.BOUNDARY ? old.p[o] : carry.initialPressure(s.id());
                double hb = o != null && old.kind[o] == NodeKind.BOUNDARY ? old.h[o] : carry.initialEnthalpy(s.id());
                setBoundary(i, pb, hb);
                if (o != null) for (int k = 0; k < Math.min(ns, old.ns); k++) scal[k * nn + i] = old.scal[k * old.nn + o];
                continue;
            }
            double pGuess;
            if (o != null && old.kind[o] != NodeKind.BOUNDARY) {
                mass[i] = old.mass[o];
                energy[i] = old.energy[o];
                for (int k = 0; k < Math.min(ns, old.ns); k++) scal[k * nn + i] = old.scal[k * old.nn + o];
                pGuess = old.p[o];
            } else {
                double m = 0, e = 0, pw = 0;
                for (long blk : s.sourceBlockKeys()) {
                    Integer ob = blockOwner.get(blk);
                    if (ob == null) continue;
                    double share = 1.0 / old.blocks[ob].length;
                    m += share * old.mass[ob];
                    e += share * old.energy[ob];
                    pw += share * old.mass[ob] * old.p[ob];
                    for (int k = 0; k < Math.min(ns, old.ns); k++) scal[k * nn + i] += share * old.scal[k * old.nn + ob];
                }
                if (m > 0) {
                    mass[i] = m;
                    energy[i] = e;
                    pGuess = pw / m;
                } else {
                    double p0i = carry.initialPressure(s.id()), h0i = carry.initialEnthalpy(s.id());
                    fluid.stateFromPressureEnthalpy(p0i, h0i, NO_FRACTIONS, 0, st);
                    mass[i] = st.density * vol[i];
                    energy[i] = mass[i] * st.specificEnergy;
                    pGuess = p0i;
                }
            }
            if (!(mass[i] > 0)) throw new IllegalArgumentException("node " + s.id() + " starts without mass");
            fluid.stateFromMassEnergy(mass[i] / vol[i], energy[i] / mass[i], NO_FRACTIONS, 0, pGuess, st);
            store(i, st);
        }
        for (int i = 0; i < nn; i++) updateLevel(i);
        if (old != null) {
            for (int j = 0; j < nl; j++) {
                Integer o = old.links.get(linkId[j]);
                if (o == null) continue;
                mdot[j] = old.mdot[o];
                rhoCol[j] = old.rhoCol[o];
                if (valve[j] != null) {
                    vPos[j] = old.vPos[o];
                    vCmd[j] = old.vCmd[o];
                    regime[j] = old.regime[o] == LinkRegime.CLOSED ? LinkRegime.CLOSED : LinkRegime.NORMAL;
                }
                if (pump[j] != null && old.pump[o] != null) {
                    pump[j].omega = old.pump[o].omega;
                    pump[j].drivingTorque = old.pump[o].drivingTorque;
                }
            }
        }
        for (int j = 0; j < nl; j++) {
            if (rhoCol[j] == 0) {
                rhoCol[j] = mdot[j] > 0 ? rho[from[j]] : mdot[j] < 0 ? rho[to[j]] : 0.5 * (rho[from[j]] + rho[to[j]]);
            }
            if (valve[j] != null && lkind[j] != LinkKind.VALVE_CHECK && vPos[j] <= 0) regime[j] = LinkRegime.CLOSED;
            if (regime[j] == LinkRegime.CLOSED) mdot[j] = 0;
        }
    }

    private void setBoundary(int i, double pressure, double enthalpy) {
        fluid.stateFromPressureEnthalpy(pressure, enthalpy, NO_FRACTIONS, 0, st);
        store(i, st);
        mass[i] = energy[i] = 0;
        dPdM[i] = dPdU[i] = 0;
    }

    // ================================================================ шаг

    @Override
    public void stepFast(double dt) {
        if (!(dt > 0)) throw new IllegalArgumentException("dt must be > 0: " + dt);
        moveValves(dt);
        for (Component c : comps) {
            if (c.asleep && wantsToWake(c)) wakeComponent(c);
            if (c.asleep) {
                // тепло ниже порога пробуждения копится в U без пересчёта EOS (энергия сохраняется)
                for (int i = c.start; i < c.end; i++) {
                    energy[i] += heat[i] * dt;
                    extEnergy += heat[i] * dt;
                }
                continue;
            }
            System.arraycopy(p, c.start, pTick, c.start, c.end - c.start);
            integrate(c, dt);
            updateSleep(c);
        }
        Arrays.fill(heat, 0);
        Arrays.fill(srcM, 0);
        Arrays.fill(srcE, 0);
    }

    private boolean wantsToWake(Component c) {
        if (c.disturbed) return true;
        for (int j : c.links) {
            if (pump[j] != null && (pump[j].omega != 0 || pump[j].drivingTorque != 0)) return true;
            if (valve[j] != null && vPos[j] != vCmd[j]) return true;
        }
        for (int i = c.start; i < c.end; i++) if (srcM[i] != 0 || Math.abs(heat[i]) > SLEEP_HEAT) return true;
        return false;
    }

    private void wakeComponent(Component c) {
        c.asleep = false;
        c.quietTicks = 0;
    }

    private void updateSleep(Component c) {
        boolean quiet = !c.disturbed;
        c.disturbed = false;
        for (int k = 0; quiet && k < c.links.length; k++) {
            int j = c.links[k];
            if (Math.abs(mdot[j]) >= SLEEP_MASS_FLOW) quiet = false;
            if (pump[j] != null && (pump[j].omega != 0 || pump[j].drivingTorque != 0)) quiet = false;
            if (valve[j] != null && vPos[j] != vCmd[j]) quiet = false;
        }
        double q = 0;
        for (int i = c.start; quiet && i < c.end; i++) {
            if (Math.abs(p[i] - pTick[i]) >= SLEEP_DP || srcM[i] != 0) quiet = false;
            q += Math.abs(heat[i]);
        }
        if (q >= SLEEP_HEAT) quiet = false;
        c.quietTicks = quiet ? c.quietTicks + 1 : 0;
        if (c.quietTicks >= SLEEP_TICKS) c.asleep = true;
    }

    /** Ход задвижек и регулирующих клапанов; гидроудар при закрытии (§1.2.9). */
    private void moveValves(double dt) {
        for (int j = 0; j < nl; j++) {
            ValveModel.Spec v = valve[j];
            if (v == null || lkind[j] == LinkKind.VALVE_CHECK || lkind[j] == LinkKind.VALVE_RELIEF) continue;
            if (vPos[j] == vCmd[j]) continue;
            double next = v.strokeTime() == 0 ? vCmd[j]
                    : vPos[j] < vCmd[j] ? Math.min(vCmd[j], vPos[j] + dt / v.strokeTime())
                    : Math.max(vCmd[j], vPos[j] - dt / v.strokeTime());
            if (next < vPos[j]) {
                if (!vClosing[j]) {
                    vClosing[j] = true;
                    vStrokeVel[j] = Math.abs(velocity(j));
                    vStrokeTime[j] = 0;
                }
                if (v.strokeTime() > 0) vStrokeTime[j] += Math.min(dt, (vPos[j] - next) * v.strokeTime());
            } else {
                vClosing[j] = false;
            }
            vPos[j] = next;
            if (vPos[j] <= 0) {
                if (vClosing[j]) emitWaterHammer(j);
                vClosing[j] = false;
                regime[j] = LinkRegime.CLOSED;
            } else if (regime[j] == LinkRegime.CLOSED) {
                regime[j] = LinkRegime.NORMAL;
            }
        }
    }

    private void emitWaterHammer(int j) {
        int d = mdot[j] >= 0 ? from[j] : to[j];
        double c = fluid.soundSpeed(p[d], hs[j] != 0 ? hs[j] : h[d], NO_FRACTIONS, 0);
        double rhoD = rhoS[j] > 0 ? rhoS[j] : rho[d];
        double tc = vStrokeTime[j];
        double reduction = tc > 0 ? Math.min(1, 2 * physLen[j] / (c * tc)) : 1;
        sink.waterHammer(linkId[j], rhoD * c * vStrokeVel[j] * reduction, tc);
    }

    private void integrate(Component c, double dtTick) {
        Arrays.fill(flowSum, c.start, c.end, 0);
        for (int j : c.links) {
            double f = Math.abs(mdot[j]);
            if (comp[from[j]] >= 0) flowSum[from[j]] += f;
            if (comp[to[j]] >= 0) flowSum[to[j]] += f;
        }
        double dtMat = Double.POSITIVE_INFINITY;
        for (int i = c.start; i < c.end; i++) if (flowSum[i] > 0) dtMat = Math.min(dtMat, C_MAT * mass[i] / flowSum[i]);
        int n = (int) Math.min(cfg.maxSubsteps(), Math.max(1, Math.ceil(dtTick / dtMat)));
        double dtNom = dtTick / n, dtMin = dtTick / MIN_STEP_DIVISOR;
        double dt = dtNom, done = 0;
        int good = 0, attempts = 0;
        while (done < dtTick * (1 - 1e-12)) {
            double step = Math.min(dt, dtTick - done);
            boolean force = dt <= dtMin * (1 + 1e-9) || ++attempts > 4 * cfg.maxSubsteps();
            if (substep(c, step, force)) {
                done += step;
                if (++good >= 3 && dt < dtNom) {
                    dt = Math.min(2 * dt, dtNom);
                    good = 0;
                }
            } else {
                dt = Math.max(dt / 2, dtMin);
                good = 0;
            }
        }
    }

    /** Один (под)шаг компоненты; false — откат, шаг нужно уменьшить. */
    private boolean substep(Component c, double dt, boolean force) {
        Arrays.fill(work, c.start, c.end, 0);
        for (int j : c.links) coefficients(j, dt);

        // давление: A·P = r; запертые связи фиксируются на пределе и система решается снова
        boolean solved = false;
        for (int iter = 0; iter <= CHOKE_ITERATIONS; iter++) {
            assemble(c, dt);
            if (!c.lu.factor()) break;
            c.lu.solve(c.rhs, c.sol);
            boolean changed = false;
            for (int j : c.links) {
                double m = a[j] + b[j] * (pressureOf(c, from[j]) - pressureOf(c, to[j]));
                mdotNew[j] = m;
                if (chokeable[j] && !choked[j] && Math.abs(m) > chokeLimit[j]) {
                    choked[j] = true;
                    a[j] = Math.copySign(chokeLimit[j], m);
                    b[j] = 0;
                    changed = true;
                }
            }
            solved = true;
            if (!changed) break;
        }
        if (!solved) {
            if (!force) return false;
            sink.simWarning(nodeId[c.start]);
            return true; // вырожденная система: шаг пропускается, состояние не меняется
        }
        for (int j : c.links) if (choked[j]) mdotNew[j] = a[j];
        for (int i = c.start; i < c.end; i++) pLin[i] = c.sol[i - c.start];

        // консервативное обновление M, U и скаляров
        int len = c.end - c.start;
        System.arraycopy(mass, c.start, mass0, c.start, len);
        System.arraycopy(energy, c.start, energy0, c.start, len);
        System.arraycopy(p, c.start, p0, c.start, len);
        for (int k = 0; k < ns; k++) System.arraycopy(scal, k * nn + c.start, scal0, k * nn + c.start, len);
        trialExtMass = trialExtEnergy = 0;
        double trialWork = 0;
        for (int i = c.start; i < c.end; i++) {
            mass[i] += dt * srcM[i];
            double e = dt * (heat[i] + srcE[i] + work[i]);
            energy[i] += e;
            trialExtMass += dt * srcM[i];
            trialExtEnergy += e;
            trialWork += dt * work[i];
        }
        for (int j : c.links) {
            double fl = mdotNew[j] * dt, fe = fl * hs[j];
            int f = from[j], tt = to[j];
            if (comp[f] >= 0) {
                mass[f] -= fl;
                energy[f] -= fe;
            } else {
                trialExtMass += fl;
                trialExtEnergy += fe;
            }
            if (comp[tt] >= 0) {
                mass[tt] += fl;
                energy[tt] += fe;
            } else {
                trialExtMass -= fl;
                trialExtEnergy -= fe;
            }
            if (ns > 0 && fl != 0) {
                int d = fl > 0 ? f : tt;
                double liquid = fl * (1 - Math.min(Math.max(xs[j], 0), 1));
                for (int k = 0; k < ns; k++) {
                    double conc = comp[d] >= 0 ? concentration(k, d) : scal[k * nn + d];
                    double sf = liquid * conc;
                    if (comp[f] >= 0) scal[k * nn + f] -= sf;
                    if (comp[tt] >= 0) scal[k * nn + tt] += sf;
                }
            }
        }
        for (int k = 0; k < ns; k++) {
            double lambda = scalars.get(k).decayConstant();
            if (lambda == 0) continue;
            double decay = Math.exp(-lambda * dt);
            for (int i = c.start; i < c.end; i++) scal[k * nn + i] *= decay;
        }

        // осушение и EOS
        boolean ok = true;
        for (int i = c.start; ok && i < c.end; i++) {
            double floor = VOID_MASS_FACTOR * vol[i] * rhoG[i];
            if (mass[i] < floor) {
                if (!force && !voided[i]) {
                    ok = false;
                    break;
                }
                // осушенный узел: пар при прежнем давлении; добавленная масса учтена как внешняя
                double ug = hG[i] - p0[i] / rhoG[i];
                trialExtMass += floor - mass[i];
                trialExtEnergy += floor * ug - energy[i];
                mass[i] = floor;
                energy[i] = floor * ug;
                if (!voided[i]) sink.simWarning(nodeId[i]);
                voided[i] = true;
            } else if (voided[i] && mass[i] > 10 * floor) {
                voided[i] = false;
            }
            double pg = pLin[i] > 0 && Double.isFinite(pLin[i]) ? pLin[i] : p0[i];
            fluid.stateFromMassEnergy(mass[i] / vol[i], energy[i] / mass[i], NO_FRACTIONS, 0, pg, st);
            if (!Double.isFinite(st.pressure)) {
                ok = false;
                break;
            }
            if (!force && Math.abs(st.pressure - pLin[i]) > cfg.maxLinearizationError() * p0[i]) ok = false;
            store(i, st);
        }
        if (!ok) {
            System.arraycopy(mass0, c.start, mass, c.start, len);
            System.arraycopy(energy0, c.start, energy, c.start, len);
            for (int k = 0; k < ns; k++) System.arraycopy(scal0, k * nn + c.start, scal, k * nn + c.start, len);
            for (int i = c.start; i < c.end; i++) {
                fluid.stateFromMassEnergy(mass[i] / vol[i], energy[i] / mass[i], NO_FRACTIONS, 0, p0[i], st);
                store(i, st);
            }
            return false;
        }
        if (force) sink.simWarning(nodeId[c.start]);

        // принятие шага
        extMass += trialExtMass;
        extEnergy += trialExtEnergy;
        pumpWork += trialWork;
        for (int i = c.start; i < c.end; i++) updateLevel(i);
        for (int j : c.links) {
            double m = mdotNew[j];
            mdot[j] = m;
            double k = dt * Math.abs(m) / (rhoCol[j] * area[j] * physLen[j]);
            rhoCol[j] = Double.isFinite(k) ? (rhoCol[j] + k * rhoS[j]) / (1 + k) : rhoS[j];
            if (regime[j] != LinkRegime.CLOSED) {
                if (choked[j]) regime[j] = LinkRegime.CHOKED;
                else if (lkind[j] == LinkKind.VALVE_CHECK && regime[j] == LinkRegime.REVERSE_BLOCKED) {
                    if (m > 0) regime[j] = LinkRegime.NORMAL;
                } else if (lkind[j] == LinkKind.VALVE_CHECK && m < -ValveModel.CHECK_HYSTERESIS) {
                    regime[j] = LinkRegime.REVERSE_BLOCKED;
                } else regime[j] = LinkRegime.NORMAL;
            }
            if (pump[j] != null) {
                pump[j].head = pumpDp[j];
                pump[j].integrate(dt, m, rhoS[j]);
            }
        }
        return true;
    }

    private double pressureOf(Component c, int i) {
        return comp[i] >= 0 ? c.sol[i - c.start] : p[i];
    }

    /** Концентрация скаляра в жидкости узла, кг/кг. */
    private double concentration(int k, int i) {
        double liquid = mass0[i] * (1 - Math.min(Math.max(xe[i], 0), 1));
        return liquid > 0 ? scal0[k * nn + i] / liquid : 0;
    }

    /** Линеаризация импульса связи j (шаг 1 §1.2.6). */
    private void coefficients(int j, double dt) {
        choked[j] = false;
        int f = from[j], tt = to[j];
        double offF = hydrostatic(f, zjF[j]), offT = hydrostatic(tt, zjT[j]);
        if (lkind[j] == LinkKind.VALVE_RELIEF) updateRelief(j, p[f] + offF);
        double m = mdot[j];
        int d = m > 0 ? f : m < 0 ? tt : (p[f] + offF >= p[tt] + offT ? f : tt);
        donor(j, d, d == f ? zjF[j] : zjT[j]);
        double offD = d == f ? offF : offT;
        chokeLimit[j] = chokeable[j]
                ? cfg.breakDischargeCoeff() * area[j] * fluid.criticalMassFlux(p[d] + offD, hs[j], NO_FRACTIONS, 0)
                : Double.POSITIVE_INFINITY;
        if (regime[j] == LinkRegime.CLOSED) {
            a[j] = b[j] = c0[j] = c1[j] = pumpDp[j] = re[j] = 0;
            return;
        }
        double grav = offF - offT - G * rhoCol[j] * (zjT[j] - zjF[j]);

        // потери: «вся смесь как жидкость» с двухфазным множителем (§1.2.4)
        double x = xs[j];
        boolean tp = twoPhase[d] && x > 0 && x < 1;
        double rhoL = tp ? rhoF[d] : rhoS[j];
        double muL = twoPhase[d] && x >= 1 ? muG[d] : mu[d];
        double am = Math.abs(m);
        double phi2 = tp ? Friction.multiplier(cfg.twoPhaseMultiplier(), x, am / area[j], dh[j], rhoF[d], rhoG[d],
                mu[d], muG[d], sigma[d]) : 1;
        re[j] = dh[j] > 0 ? am * dh[j] / (area[j] * muL) : 0;
        double lin = 0, quad = 0; // Δp_loss = lin·ṁ + quad·ṁ|ṁ|/(2ρ)
        if (flen[j] > 0) {
            if (re[j] < Friction.RE_LAMINAR) lin = 32 * muL * flen[j] / (rhoL * dh[j] * dh[j] * area[j]) * phi2;
            else quad = Friction.darcy(re[j], rough[j] / dh[j]) * flen[j] / dh[j] * phi2 / (area[j] * area[j]);
        }
        quad += (zeta0[j] + valveZeta(j)) * phi2 / (area[j] * area[j]);
        c1[j] = lin + quad * am / rhoL;
        c0[j] = -quad * am * m / (2 * rhoL);

        double pp = 0, sm = 0;
        PumpModel pm = pump[j];
        if (pm != null) {
            pp = pm.headAt(m, rhoS[j]);
            double eps = 1e-3 * rhoS[j] * pm.spec.ratedFlow();
            double slope = (pm.headAt(m + eps, rhoS[j]) - pm.headAt(m - eps, rhoS[j])) / (2 * eps);
            sm = Math.max(0, -slope);
            int down = m >= 0 ? tt : f;
            if (comp[down] >= 0) work[down] += m * pp / rhoS[j];
        }
        pumpDp[j] = pp;
        double in = inertia[j];
        b[j] = dt / (in + dt * c1[j] + dt * sm);
        a[j] = b[j] * (in / dt * m + grav + pp + sm * m - c0[j]);
    }

    private double valveZeta(int j) {
        ValveModel.Spec v = valve[j];
        if (v == null) return 0;
        return switch (lkind[j]) {
            case VALVE_CHECK -> regime[j] == LinkRegime.REVERSE_BLOCKED ? ValveModel.CHECK_REVERSE_ZETA : v.zetaOpen();
            case VALVE_RELIEF -> v.zetaOpen();
            default -> ValveModel.zeta(v, vPos[j]);
        };
    }

    private void updateRelief(int j, double pFrom) {
        ValveModel.Spec v = valve[j];
        double blowdown = Double.isNaN(v.blowdown()) ? cfg.reliefBlowdown() : v.blowdown();
        if (regime[j] == LinkRegime.CLOSED && pFrom > v.setPressure()) {
            regime[j] = LinkRegime.NORMAL;
            vPos[j] = vCmd[j] = 1;
            sink.reliefLift(linkId[j], true);
        } else if (regime[j] != LinkRegime.CLOSED && pFrom < v.setPressure() * (1 - blowdown)) {
            regime[j] = LinkRegime.CLOSED;
            vPos[j] = vCmd[j] = 0;
            mdot[j] = 0;
            sink.reliefLift(linkId[j], false);
        }
    }

    /** Свойства смеси, которую связь j отбирает из донора d на отметке z (фазовый отбор §1.2.5). */
    private void donor(int j, int d, double z) {
        if (kind[d] == NodeKind.SEPARATED && twoPhase[d]) {
            double top = zBot[d] + hgt[d], level = lvl[d];
            double xb = xBelow[d], xa = 1 - cfg.moistureCarryover(), x;
            if (level >= top) x = xb;
            else {
                double delta = Math.max(0.05, 0.5 * dh[j]);
                if (z <= level - delta) x = xb;
                else if (z >= level + delta) x = xa;
                else x = xb + (xa - xb) * (z - level + delta) / (2 * delta);
            }
            xs[j] = x;
            hs[j] = hF[d] + x * (hG[d] - hF[d]);
            rhoS[j] = 1.0 / (x / rhoG[d] + (1 - x) / rhoF[d]);
        } else {
            xs[j] = twoPhase[d] ? Math.min(Math.max(xe[d], 0), 1) : xe[d] < 0 ? 0 : 1;
            hs[j] = h[d];
            rhoS[j] = rho[d];
        }
    }

    /** Гидростатика от точки отсчёта давления узла до отметки z, Па: p(z) = p_i + результат. */
    private double hydrostatic(int i, double z) {
        if (kind[i] == NodeKind.SEPARATED) {
            double level = lvl[i];
            if (z <= level) return G * rhoPool[i] * (level - z);
            return -G * (twoPhase[i] ? rhoG[i] : rho[i]) * (z - level);
        }
        return G * rho[i] * (zBot[i] + 0.5 * hgt[i] - z);
    }

    /** Сборка A·P = r (шаг 3 §1.2.6): строки — неграничные узлы компоненты. */
    private void assemble(Component c, double dt) {
        c.lu.clear();
        for (int i = c.start; i < c.end; i++) {
            int r = i - c.start;
            c.rhs[r] = p[i] + dt * (dPdM[i] * srcM[i] + dPdU[i] * (heat[i] + srcE[i] + work[i]));
            c.lu.add(c.diagPos[r], 1);
        }
        for (int k = 0; k < c.links.length; k++) {
            int j = c.links[k], f = from[j], tt = to[j];
            double bj = b[j], aj = a[j];
            if (bj == 0 && aj == 0) continue;
            boolean inF = comp[f] >= 0, inT = comp[tt] >= 0;
            if (inF) {
                int r = f - c.start;
                double g = dt * (dPdM[f] + dPdU[f] * hs[j]);
                c.lu.add(c.diagPos[r], g * bj);
                if (inT) c.lu.add(c.posFT[k], -g * bj);
                else c.rhs[r] += g * bj * p[tt];
                c.rhs[r] -= g * aj;
            }
            if (inT) {
                int r = tt - c.start;
                double g = dt * (dPdM[tt] + dPdU[tt] * hs[j]);
                c.lu.add(c.diagPos[r], g * bj);
                if (inF) c.lu.add(c.posTF[k], -g * bj);
                else c.rhs[r] += g * bj * p[f];
                c.rhs[r] += g * aj;
            }
        }
    }

    private void store(int i, ThermoState s) {
        p[i] = s.pressure;
        t[i] = s.temperature;
        rho[i] = s.density;
        h[i] = s.enthalpy;
        xe[i] = s.quality;
        alpha[i] = s.voidFraction;
        rhoF[i] = s.rhoF;
        rhoG[i] = s.rhoG;
        hF[i] = s.hF;
        hG[i] = s.hG;
        mu[i] = s.viscosity;
        muG[i] = s.viscosityG;
        sigma[i] = s.surfaceTension;
        twoPhase[i] = s.has(ThermoState.TWO_PHASE);
        if (kind[i] != NodeKind.BOUNDARY) {
            double m = mass[i], v = vol[i], u = s.specificEnergy;
            dPdM[i] = (s.dPdRhoAtU - u / s.density * s.dPdUAtRho) / v;
            dPdU[i] = s.dPdUAtRho / m;
        }
    }

    /** Уровень смеси узла SEPARATED (§1.3.6): drift-flux слоя под уровнем по паропроизводству от тепла. */
    private void updateLevel(int i) {
        if (kind[i] != NodeKind.SEPARATED) {
            lvl[i] = lvlCollapsed[i] = Double.NaN;
            rhoPool[i] = rho[i];
            xBelow[i] = 0;
            return;
        }
        double top = zBot[i] + hgt[i];
        if (twoPhase[i]) {
            double x = Math.min(Math.max(xe[i], 0), 1);
            double vLiq = mass[i] * (1 - x) / rhoF[i];
            double gamma = Math.max(0, heat[i]) / (hG[i] - hF[i]);
            double jg = gamma / (rhoG[i] * xsec[i]);
            double ap = VoidFraction.pool(jg, VoidFraction.driftVelocity(sigma[i], rhoF[i], rhoG[i]));
            double vPool = vLiq / (1 - ap);
            if (vPool >= vol[i]) {
                lvl[i] = top;
                xBelow[i] = x;
                rhoPool[i] = rho[i];
            } else {
                lvl[i] = zBot[i] + vPool / xsec[i];
                xBelow[i] = ap * rhoG[i] / (ap * rhoG[i] + (1 - ap) * rhoF[i]);
                rhoPool[i] = ap * rhoG[i] + (1 - ap) * rhoF[i];
            }
            lvlCollapsed[i] = zBot[i] + Math.min(vLiq, vol[i]) / xsec[i];
        } else if (xe[i] < 0) {
            lvl[i] = lvlCollapsed[i] = top;
            xBelow[i] = 0;
            rhoPool[i] = rho[i];
        } else {
            lvl[i] = lvlCollapsed[i] = zBot[i];
            xBelow[i] = 1;
            rhoPool[i] = rho[i];
        }
    }

    private double velocity(int j) {
        double r = rhoS[j] > 0 ? rhoS[j] : rho[mdot[j] >= 0 ? from[j] : to[j]];
        return mdot[j] / (r * area[j]);
    }

    // ================================================================ API

    @Override
    public FluidNode node(long id) {
        Integer i = nodeIndex.get(id);
        if (i == null) throw new IllegalArgumentException("unknown node " + id);
        return nodeViews[i];
    }

    @Override
    public FlowLink link(long id) {
        Integer j = linkIndex.get(id);
        if (j == null) throw new IllegalArgumentException("unknown link " + id);
        return linkViews[j];
    }

    @Override
    public int componentCount() {
        return comps.length;
    }

    @Override
    public int componentOf(long nodeId) {
        Integer i = nodeIndex.get(nodeId);
        if (i == null) throw new IllegalArgumentException("unknown node " + nodeId);
        return comp[i];
    }

    @Override
    public boolean isAsleep(int component) {
        return comps[component].asleep;
    }

    @Override
    public void wake(long nodeOrLinkId) {
        Integer i = nodeIndex.get(nodeOrLinkId);
        if (i != null) {
            disturbNode(i);
            return;
        }
        Integer j = linkIndex.get(nodeOrLinkId);
        if (j == null) throw new IllegalArgumentException("unknown node or link " + nodeOrLinkId);
        disturbLink(j);
    }

    private void disturbNode(int i) {
        if (comp[i] >= 0) disturb(comps[comp[i]]);
        else for (int c : boundaryComps[i]) disturb(comps[c]);
    }

    private void disturbLink(int j) {
        disturb(comps[comp[from[j]] >= 0 ? comp[from[j]] : comp[to[j]]]);
    }

    private static void disturb(Component c) {
        c.disturbed = true;
        c.asleep = false;
        c.quietTicks = 0;
    }

    @Override
    public void registerScalar(PassiveScalar s) {
        Objects.requireNonNull(s, "scalar");
        if (scalars.contains(s)) return;
        scalars.add(s);
        double[] next = new double[(ns + 1) * nn];
        System.arraycopy(scal, 0, next, 0, scal.length);
        scal = next;
        scal0 = new double[next.length];
        ns++;
    }

    @Override
    public void registerHomologousCurve(String id, HomologousCurve curve) {
        curves.put(Objects.requireNonNull(id, "id"), Objects.requireNonNull(curve, "curve"));
    }

    @Override
    public void setEventSink(HydraulicEventSink sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @Override
    public double totalMass() {
        double s = 0;
        for (int i = 0; i < nn; i++) if (comp[i] >= 0) s += mass[i];
        return s;
    }

    @Override
    public double totalEnergy() {
        double s = 0;
        for (int i = 0; i < nn; i++) if (comp[i] >= 0) s += energy[i];
        return s;
    }

    @Override
    public double externalMassInput() {
        return extMass;
    }

    @Override
    public double externalEnergyInput() {
        return extEnergy;
    }

    @Override
    public double pumpWorkInput() {
        return pumpWork;
    }

    private int scalarIndex(PassiveScalar s) {
        int k = scalars.indexOf(s);
        if (k < 0) throw new IllegalArgumentException("scalar not registered: " + s.id());
        return k;
    }

    private static final class Component {
        final int start, end;
        final int[] links;
        final SparseLU lu;
        final int[] diagPos, posFT, posTF;
        final double[] rhs, sol;
        boolean asleep, disturbed;
        int quietTicks;

        Component(int start, int end, int[] links, int[] from, int[] to, int[] comp) {
            this.start = start;
            this.end = end;
            this.links = links;
            int n = end - start;
            List<List<Integer>> adj = new ArrayList<>();
            for (int r = 0; r < n; r++) adj.add(new ArrayList<>());
            for (int j : links) {
                if (comp[from[j]] >= 0 && comp[to[j]] >= 0) {
                    adj.get(from[j] - start).add(to[j] - start);
                    adj.get(to[j] - start).add(from[j] - start);
                }
            }
            int[][] a = new int[n][];
            for (int r = 0; r < n; r++) a[r] = adj.get(r).stream().mapToInt(Integer::intValue).toArray();
            lu = new SparseLU(n, a);
            diagPos = new int[n];
            for (int r = 0; r < n; r++) diagPos[r] = lu.position(r, r);
            posFT = new int[links.length];
            posTF = new int[links.length];
            for (int k = 0; k < links.length; k++) {
                int j = links[k];
                boolean both = comp[from[j]] >= 0 && comp[to[j]] >= 0;
                posFT[k] = both ? lu.position(from[j] - start, to[j] - start) : -1;
                posTF[k] = both ? lu.position(to[j] - start, from[j] - start) : -1;
            }
            rhs = new double[n];
            sol = new double[n];
        }
    }

    // ================================================================ фасады

    private final class NodeView implements FluidNode {
        final int i;

        NodeView(int i) {
            this.i = i;
        }

        @Override public long id() { return nodeId[i]; }
        @Override public NodeKind kind() { return kind[i]; }
        @Override public double mass() { return mass[i]; }
        @Override public double internalEnergy() { return energy[i]; }
        @Override public double volume() { return vol[i]; }
        @Override public double pressure() { return p[i]; }
        @Override public double temperature() { return t[i]; }
        @Override public double enthalpy() { return h[i]; }
        @Override public double density() { return rho[i]; }
        @Override public double quality() { return xe[i]; }
        @Override public double voidFraction() { return alpha[i]; }
        @Override public double mixtureLevel() { return lvl[i] - zBot[i]; }
        @Override public double collapsedLevel() { return lvlCollapsed[i] - zBot[i]; }
        @Override public boolean isVoided() { return voided[i]; }

        @Override
        public double scalar(PassiveScalar s) {
            return scal[scalarIndex(s) * nn + i];
        }

        @Override
        public void setScalar(PassiveScalar s, double amount) {
            if (!(amount >= 0)) throw new IllegalArgumentException("amount < 0");
            scal[scalarIndex(s) * nn + i] = amount;
            disturbNode(i);
        }

        @Override
        public double boronPpm() {
            int k = scalars.indexOf(PassiveScalar.BORON);
            if (k < 0) return 0;
            if (kind[i] == NodeKind.BOUNDARY) return 1e6 * scal[k * nn + i];
            double liquid = mass[i] * (1 - Math.min(Math.max(xe[i], 0), 1));
            return liquid > 0 ? 1e6 * scal[k * nn + i] / liquid : 0;
        }

        @Override
        public void addSource(double massRate, double enthalpy) {
            if (kind[i] == NodeKind.BOUNDARY) throw new IllegalStateException("sources go to non-boundary nodes");
            srcM[i] += massRate;
            srcE[i] += massRate * (massRate < 0 ? h[i] : enthalpy);
            disturbNode(i);
        }

        @Override
        public void addHeat(double watts) {
            if (kind[i] == NodeKind.BOUNDARY) throw new IllegalStateException("heat goes to non-boundary nodes");
            heat[i] += watts;
            if (Math.abs(watts) > SLEEP_HEAT) disturbNode(i);
        }

        @Override
        public void setBoundaryState(double pressure, double enthalpy) {
            if (kind[i] != NodeKind.BOUNDARY) throw new IllegalStateException("node " + nodeId[i] + " is not a boundary");
            setBoundary(i, pressure, enthalpy);
            disturbNode(i);
        }
    }

    private final class LinkView implements FlowLink {
        final int j;

        LinkView(int j) {
            this.j = j;
        }

        @Override public long id() { return linkId[j]; }
        @Override public LinkKind kind() { return lkind[j]; }
        @Override public LinkRegime regime() { return regime[j]; }
        @Override public double massFlow() { return mdot[j]; }
        @Override public double velocity() { return LumpedFluidNetwork.this.velocity(j); }
        @Override public double reynolds() { return re[j]; }
        @Override public double pressureDrop() { return c1[j] * mdot[j] + c0[j]; }
        @Override public double valvePosition() { return valve[j] == null ? 1 : vPos[j]; }
        @Override public PumpModel pump() { return pump[j]; }

        @Override
        public void setValvePosition(double theta01) {
            if (valve[j] == null || lkind[j] == LinkKind.VALVE_CHECK || lkind[j] == LinkKind.VALVE_RELIEF) {
                throw new IllegalStateException("link " + linkId[j] + " has no positionable valve");
            }
            if (!(theta01 >= 0 && theta01 <= 1)) throw new IllegalArgumentException("theta: " + theta01);
            vCmd[j] = theta01;
            disturbLink(j);
        }
    }
}
