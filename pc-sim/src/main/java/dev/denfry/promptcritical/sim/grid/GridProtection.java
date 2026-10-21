package dev.denfry.promptcritical.sim.grid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Исполнение РЗА сети (07 §7.5) в конце FAST-тика, после измерений и хода выключателей: пробой
 * изоляции при несоответствии классов (§7.4.8), переход «земли» в двойное замыкание (§7.5.1),
 * расцепители 0,4 кВ (§7.4.6), затем все реле. Команда, поданная в тике N, двигает выключатель с тика
 * N + 1, поэтому время отключения КЗ — 1 тик реле + t_откл (§7.5.3).
 */
final class GridProtection {
    private static final int FEEDER = 0, GENERATOR = 1, BUSBAR = 2, LOAD = 3, MOTOR = 4;
    private static final int[] NONE = new int[0];

    private final PowerGrid g;
    private final RelayInputs in = new RelayInputs();
    private final List<ProtectionRelay> relays = new ArrayList<>();
    private final List<RelayState> states = new ArrayList<>();
    private int n;
    private int[] kind = new int[0], target = new int[0], breakerOf = new int[0];
    private boolean[] atFrom = new boolean[0];
    private int[][] trips = new int[0][];

    // по ветвям
    private LvTripUnit[] tripUnit = new LvTripUnit[0];
    private double[] tuHeat = new double[0];
    private boolean[] blown = new boolean[0], accel = new boolean[0];

    GridProtection(PowerGrid g) {
        this.g = g;
    }

    void growBranches(int nbr) {
        if (nbr <= tripUnit.length) return;
        int c = Math.max(nbr, 2 * tripUnit.length);
        tripUnit = Arrays.copyOf(tripUnit, c);
        tuHeat = Arrays.copyOf(tuHeat, c);
        blown = Arrays.copyOf(blown, c);
        accel = Arrays.copyOf(accel, c);
    }

    // ================================================================ установка

    int add(ProtectionRelay relay, RelayMount mount) {
        Objects.requireNonNull(relay, "relay");
        Objects.requireNonNull(mount, "mount");
        int r = n;
        int kd, tg, br = -1;
        boolean from = true;
        int[] tr = NONE;
        RelayState st = relay.newState();
        switch (mount) {
            case RelayMount.Feeder f -> {
                kd = FEEDER;
                tg = br = breaker(f.branch());
                from = f.atFrom();
                only(relay, ProtectionFunction.F87T, f);
            }
            case RelayMount.Generator gm -> {
                kd = GENERATOR;
                tg = g.machineIndex(gm.machine());
                br = breaker(gm.breaker());
                only(relay, ProtectionFunction.F87G, gm);
                st.bias = relay instanceof ProtectionRelay.OutOfStep ? g.slips[tg] : 0;
            }
            case RelayMount.Busbar b -> {
                kd = BUSBAR;
                tg = g.bus(b.bus());
                tr = new int[b.trips().size()];
                for (int i = 0; i < tr.length; i++) tr[i] = breaker(b.trips().get(i));
                only(relay, ProtectionFunction.F87B, b);
            }
            case RelayMount.Load l -> {
                kd = LOAD;
                tg = g.load(l.load());
                consumerRelay(relay, l);
            }
            case RelayMount.Motor m -> {
                kd = MOTOR;
                tg = g.motor(m.motor());
                consumerRelay(relay, m);
            }
        }
        if (relay instanceof ProtectionRelay.Frequency || relay instanceof ProtectionRelay.Ufls)
            st.bias = (2 * g.rng.nextDouble() - 1) * relay.generation().frequencyErrorHz;
        n++;
        relays.add(relay);
        states.add(st);
        kind = grow(kind);
        target = grow(target);
        breakerOf = grow(breakerOf);
        if (atFrom.length < n) atFrom = Arrays.copyOf(atFrom, Math.max(n, 2 * atFrom.length));
        if (trips.length < n) trips = Arrays.copyOf(trips, Math.max(n, 2 * trips.length));
        kind[r] = kd;
        target[r] = tg;
        breakerOf[r] = br;
        atFrom[r] = from;
        trips[r] = tr;
        return r;
    }

    /** Дифзащита — только своего объекта; реле машины, АЧР и присоединения — по месту. */
    private static void only(ProtectionRelay relay, ProtectionFunction differential, RelayMount mount) {
        if (relay instanceof ProtectionRelay.Differential d && d.function() != differential)
            throw new IllegalArgumentException(d.function() + " cannot be mounted on " + mount);
        boolean generatorOnly = relay instanceof ProtectionRelay.ReversePower
                || relay instanceof ProtectionRelay.LossOfField || relay instanceof ProtectionRelay.OutOfStep;
        boolean feederOnly = relay instanceof ProtectionRelay.Distance || relay instanceof ProtectionRelay.SyncCheck
                || relay instanceof ProtectionRelay.AutoReclose || relay instanceof ProtectionRelay.Buchholz
                || relay instanceof ProtectionRelay.BreakerFailure;
        if (relay instanceof ProtectionRelay.Ufls || generatorOnly && !(mount instanceof RelayMount.Generator)
                || feederOnly && !(mount instanceof RelayMount.Feeder))
            throw new IllegalArgumentException(relay.function() + " cannot be mounted on " + mount);
    }

    private static void consumerRelay(ProtectionRelay relay, RelayMount mount) {
        if (!(relay instanceof ProtectionRelay.Ufls || relay instanceof ProtectionRelay.Voltage
                || relay instanceof ProtectionRelay.Frequency || relay instanceof ProtectionRelay.Thermal
                || relay instanceof ProtectionRelay.Overcurrent))
            throw new IllegalArgumentException(relay.function() + " cannot be mounted on " + mount);
    }

    private int breaker(BranchId id) {
        int k = g.branch(id);
        if (g.brState[k] == null) throw new IllegalStateException("branch " + k + " has no breaker for the relay to trip");
        return k;
    }

    private int[] grow(int[] a) {
        return n <= a.length ? a : Arrays.copyOf(a, Math.max(n, 2 * a.length));
    }

    RelayState state(int r) {
        if (r >= n) throw new IllegalArgumentException("unknown relay " + r);
        return states.get(r);
    }

    void attachTripUnit(int k, LvTripUnit unit) {
        tripUnit[k] = unit;
        tuHeat[k] = 0;
        blown[k] = false;
    }

    boolean fuseBlown(int k) {
        return blown[k];
    }

    /** Вставка перегорела до пересборки сети или сохранения. */
    void markFuseBlown(int k) {
        blown[k] = true;
    }

    void replaceFuse(int k) {
        blown[k] = false;
        tuHeat[k] = 0;
    }

    /** Реле 25 на ветви разрешает включение; без реле — всегда. */
    boolean syncPermits(int k) {
        boolean dead = g.deadSide(k);
        SyncScope scope = null;
        for (int r = 0; r < n; r++) {
            if (kind[r] != FEEDER || breakerOf[r] != k || !(relays.get(r) instanceof ProtectionRelay.SyncCheck sc))
                continue;
            if (scope == null && !dead) scope = g.scope(k);
            if (!sc.permits(scope, dead)) return false;
        }
        return true;
    }

    // ================================================================ тик

    void step(double dt) {
        g.advanceInrush(dt);
        insulation();
        groundEscalation(dt);
        // контакты переключились в этом тике: решение сети — для старой топологии (у только что
        // включённой ветви мёртвая сторона с V = 0) — реле и расцепители ждут следующего тика
        if (g.topologyPending()) return;
        tripUnits(dt);
        Arrays.fill(accel, false);
        for (int r = 0; r < n; r++) {
            if (relays.get(r) instanceof ProtectionRelay.AutoReclose && ProtectionRelay.AutoReclose.accelerating(states.get(r)))
                accel[breakerOf[r]] = true;
        }
        for (int r = 0; r < n; r++) {
            ProtectionRelay relay = relays.get(r);
            RelayState st = states.get(r);
            fill(r, relay);
            boolean out = relay.evaluate(in, st, dt);
            if (out != st.output) {
                st.output = out;
                act(r, relay, out);
            }
        }
    }

    /** Пробой изоляции блока, включённого не в свой класс: k = U/U_ном выше испытательного (§7.4.8). */
    private void insulation() {
        for (int j = 0; j < g.nmo; j++) {
            if (!g.moOn[j] || g.moFaulted[j]) continue;
            int b = g.moBus[j];
            if (!g.islands()[g.busIsland[b]].solvable()) continue;
            // двигатель ниже класса шины пробивается при подаче напряжения — раньше, чем его пусковой ток
            // посадит слабый источник (номинал шины уже выше испытательного)
            if (g.moBreakdownPu[j] < 1 || Math.hypot(g.vRe[b], g.vIm[b]) > g.moBreakdownPu[j]) {
                g.moFaulted[j] = true;
                g.motorFaultFollowsContactor(j);
                g.breakdownEvent(b);
            }
        }
        for (int i = 0; i < g.nm; i++) {
            if (g.statorDmg[i] >= 1) continue;
            int b = g.mBus[i];
            if (!g.islands()[g.busIsland[b]].solvable()) continue;
            if (g.vt[i] > g.mBreakdownPu[i]) {
                g.statorDmg[i] = 1;
                g.setBusFault(b, FaultType.THREE_PHASE, 0, false, i, -1);
                g.breakdownEvent(b);
            }
        }
    }

    /** «Земля» в сети с изолированной нейтралью переходит в двойное замыкание с λ (§7.5.1). */
    private void groundEscalation(double dt) {
        double p = g.cfg.groundFaultEscalationPerS() * dt;
        if (p <= 0) return;
        for (PowerGrid.Island isl : g.islands()) {
            if (!isl.solvable()) continue;
            for (FaultSite s : isl.faults) {
                if (s.ground && g.rng.nextDouble() < p) g.escalateGround(s);
            }
        }
    }

    /** Расцепители и вставки 0,4 кВ действуют прямо на механизм, без оперативного тока. */
    private void tripUnits(double dt) {
        for (int k = 0; k < g.nbr; k++) {
            LvTripUnit u = tripUnit[k];
            if (u == null || !g.closed[k]) continue;
            tuHeat[k] = u.heat(tuHeat[k], g.branchCurrentA(k), dt);
            if (tuHeat[k] < 1 - ProtectionRelay.EPS) continue;
            tuHeat[k] = 0;
            if (g.brState[k] != null && !u.fuse()) {
                g.brProtTrip[k] = true;
                g.beginOpening(k);
            } else {
                blown[k] = true;
                g.openSwitch(k);
            }
        }
    }

    // ================================================================ измерения

    private void fill(int r, ProtectionRelay relay) {
        in.clear();
        switch (kind[r]) {
            case FEEDER -> feeder(r, relay);
            case GENERATOR -> generator(r, relay);
            case BUSBAR -> busbar(target[r]);
            case LOAD -> {
                int j = target[r], b = g.lBus[j];
                bus(b);
                in.currentA = amps(Math.hypot(g.liRe[j], g.liIm[j]), b);
                in.connected = g.lOn[j];
            }
            default -> {
                int j = target[r], b = g.moBus[j];
                bus(b);
                in.currentA = amps(Math.hypot(g.moIRe[j], g.moIIm[j]), b);
                in.hotSpotC = g.moTempC[j];
                in.connected = g.moOn[j];
            }
        }
    }

    private void bus(int b) {
        in.voltagePu = Math.hypot(g.vRe[b], g.vIm[b]);
        in.frequencyHz = g.fBus[b];
    }

    private double amps(double pu, int bus) {
        return pu * g.sb / (Math.sqrt(3) * g.busClassAt(bus).volts);
    }

    private void breakerFlags(int k) {
        in.tripPending = g.brProtTrip[k] && g.closed[k];
        in.breakerOpen = !g.closed[k] && g.brState[k] == BreakerState.OPEN;
        in.protectionTripped = g.brProtTrip[k];
    }

    private void feeder(int r, ProtectionRelay relay) {
        int k = target[r];
        boolean fromEnd = atFrom[r];
        int b = fromEnd ? g.bFrom[k] : g.bTo[k];
        bus(b);
        breakerFlags(k);
        in.accelerated = accel[k];
        if (!g.closed[k]) return;
        in.currentA = amps(g.endPhaseCurrentPu(k, fromEnd), b);
        g.endCurrent(k, fromEnd);
        double ir = g.ecRe, ii = g.ecIm, i2 = ir * ir + ii * ii;
        if (i2 > 1e-18) { // V₁/I₁ в омах: Z_b = U²/S_b
            double vr = g.vRe[b], vi = g.vIm[b], zb = g.busClassAt(b).volts * g.busClassAt(b).volts / g.sb;
            in.zRe = (vr * ir + vi * ii) / i2 * zb;
            in.zIm = (vi * ir - vr * ii) / i2 * zb;
        }
        Branch spec = g.branchSpecAt(k);
        if (spec instanceof Branch.Line l && l.spec() != null
                || spec instanceof Branch.Transformer t && t.thermal() != null) in.hotSpotC = g.brTempC[k];
        FaultSite site = g.branchSite(k);
        in.internalArc = spec instanceof Branch.Transformer && site != null && !site.ground;
        in.inrushH2 = g.inrushH2(k);
        in.inrushTrip = g.inrushRoll[k] < PowerGrid.INRUSH_TRIP_PROBABILITY;
        if (relay instanceof ProtectionRelay.Differential) branchDifferential(k, site);
    }

    /** 87T: I_d = |t·I_f + I_t| по последовательностям (с I₂, I₀ своего КЗ), торможение — полусумма концов. */
    private void branchDifferential(int k, FaultSite site) {
        double t = g.tap[k];
        g.endCurrent(k, true);
        double dr = t * g.ecRe, di = t * g.ecIm;
        g.endCurrent(k, false);
        dr += g.ecRe;
        di += g.ecIm;
        double id = site == null ? Math.hypot(dr, di)
                : FaultSite.phaseMax(dr, di, site.i2Re, site.i2Im, site.i0Re, site.i0Im);
        double restraint = 0.5 * (t * g.endPhaseCurrentPu(k, true) + g.endPhaseCurrentPu(k, false));
        double rating = rating(k);
        in.diffPu = id / rating;
        in.restraintPu = restraint / rating;
    }

    /** Номинальный ток объекта, о.е. на S_b. */
    private double rating(int k) {
        return switch (g.branchSpecAt(k)) {
            case Branch.Transformer t -> t.sRatedVa() / g.sb;
            case Branch.Line l -> l.iMaxA() > 0 ? l.iMaxA() * Math.sqrt(3) * g.busClassAt(g.bFrom[k]).volts / g.sb : 1;
            case Branch.Cable c -> c.iMaxA() > 0 ? c.iMaxA() * Math.sqrt(3) * g.busClassAt(g.bFrom[k]).volts / g.sb : 1;
            case Branch.Tie tie -> 1;
        };
    }

    private void generator(int r, ProtectionRelay relay) {
        int i = target[r], b = g.mBus[i];
        bus(b);
        breakerFlags(breakerOf[r]);
        in.voltagePu = g.vt[i];
        double ms = g.mS[i], iPh = g.machinePhaseCurrentPu(i);
        in.currentA = amps(iPh, b);
        in.powerPu = g.pe[i] / ms;
        in.reactivePu = g.qe[i] / ms;
        in.poleSlips = g.slips[i];
        double ir = g.ioRe[i], ii = g.ioIm[i], i2 = ir * ir + ii * ii;
        if (i2 > 1e-18) { // о.е. машины: Z_m = Z_b·S_ном/S_b
            double vr = g.vRe[b], vi = g.vIm[b];
            in.zRe = (vr * ir + vi * ii) / i2 * ms;
            in.zIm = (vi * ir - vr * ii) / i2 * ms;
        }
        if (relay instanceof ProtectionRelay.Differential) {
            in.diffPu = (g.fMachine[b] == i ? g.busFaultPhasePu(b, true) : 0) / ms;
            in.restraintPu = iPh / ms;
        }
    }

    /** 87B: I_d — ток КЗ на шине (не внутри машины), торможение — полусумма токов присоединений; о.е. на S_b. */
    private void busbar(int b) {
        bus(b);
        in.diffPu = g.busFaultPhasePu(b, false);
        double sum = 0;
        for (int k = 0; k < g.nbr; k++) {
            if (g.bFrom[k] == b) sum += g.endPhaseCurrentPu(k, true);
            else if (g.bTo[k] == b) sum += g.endPhaseCurrentPu(k, false);
        }
        for (int i = 0; i < g.nm; i++) if (g.mBus[i] == b) sum += g.machinePhaseCurrentPu(i);
        in.restraintPu = 0.5 * sum;
    }

    // ================================================================ действия

    private void act(int r, ProtectionRelay relay, boolean out) {
        int kd = kind[r];
        if (kd == LOAD || kd == MOTOR) {
            boolean reconnect = !out && relay instanceof ProtectionRelay.Ufls; // ЧАПВ
            if (!out && !reconnect) return;
            int j = target[r];
            if (kd == LOAD) {
                g.lOn[j] = reconnect;
                g.loadEvent(j, reconnect);
            } else {
                g.moOn[j] = reconnect;
                g.motorFaultFollowsContactor(j);
            }
            if (out) g.relayEvent(r, relay.function());
            return;
        }
        if (!out) return;
        g.relayEvent(r, relay.function());
        int k = breakerOf[r];
        switch (relay) {
            case ProtectionRelay.AutoReclose ar -> g.command(k, true);
            case ProtectionRelay.BreakerFailure bf -> { // смежные выключатели шины места установки
                int b = atFrom[r] ? g.bFrom[k] : g.bTo[k];
                for (int q = 0; q < g.nbr; q++) {
                    if (q != k && g.brState[q] != null && (g.bFrom[q] == b || g.bTo[q] == b)) g.protectionTrip(q);
                }
            }
            default -> {
                if (kd == BUSBAR) {
                    for (int q : trips[r]) g.protectionTrip(q);
                } else {
                    g.protectionTrip(k);
                }
            }
        }
    }
}
