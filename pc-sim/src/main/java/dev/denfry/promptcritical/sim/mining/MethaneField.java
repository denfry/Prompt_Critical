package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.combustion.Chimney;
import java.util.Arrays;

/**
 * Шахтная атмосфера (08 §8.2.4, §8.2.6): узловой баланс CH₄, CO, CO₂, O₂ по расходам {@link MineVentilation},
 * газовыделение обнажённых и отбитых блоков, окисление угля, дегазация, осевшая угольная и инертная пыль,
 * слоевые скопления у кровли; вход во фронт взрыва {@link #ignite}. Шаг SLOW.
 *
 * <p>Баланс узла V·dC/dt = Σ_in Q·C_up − C·Σ_out Q + I − D − G⁺·C (последний член — вытеснение смеси
 * собственным газовыделением узла, без него в глухой выработке сумма долей ушла бы за единицу) решается неявно
 * (Эйлер назад): шаг SLOW 72 с физ. больше времени смены воздуха в малых узлах. Система — M-матрица;
 * Гаусс — Зейдель в порядке течения точен за проход для сети без рециркуляции.
 *
 * <p>Десорбция обнажения I(t) = κ·x·ρ_c·V/τ_d·e^{−t/τ_d} интегрируется точно: блок хранит остаток газа.
 */
public final class MethaneField {
    /** Доля газоносности, отдаваемая обнажением (08 §8.2.4). */
    public static final double KAPPA = 0.6;
    /** Плотность угля в массиве, кг/м³ (08 §8.0.2). */
    public static final double COAL_DENSITY = 1300;
    /** Отбитый уголь отдаёт остаток «за 1 ч физ.»: τ = 20 мин, к часу — 95 %. */
    public static final double BROKEN_TAU_S = 1200;
    /** Поглощение O₂ углём 0,1–0,5 м³/(т·сут) — середина, м³/(т·с) при свежем воздухе. */
    public static final double O2_UPTAKE_M3_PER_T_S = 0.3 / 86_400;
    /** Выход CO₂ и CO на объём поглощённого O₂ при низкотемпературном окислении [допущение, в GDD нет]. */
    public static final double OXIDATION_CO2_PER_O2 = 0.5, OXIDATION_CO_PER_O2 = 0.01;
    /** Слоевое скопление: ниже этой скорости показания CH₄ у кровли удваиваются. */
    public static final double LAYERING_VELOCITY = 0.5, LAYERING_FACTOR = 2;
    /** Низшая теплота сгорания метана, Дж/нм³, и угольной пыли, Дж/кг (середина 25–30). */
    public static final double Q_CH4 = 35.8e6, Q_DUST = 27.5e6;
    /** Порог остатка газа отбитого угля, ниже которого запись снимается, м³. */
    static final double DEPLETED_M3 = 1e-4;

    private static final int CH4 = MineGas.CH4.ordinal(), CO = MineGas.CO.ordinal(), CO2 = MineGas.CO2.ordinal(),
            O2 = MineGas.O2.ordinal(), G = MineGas.COUNT;
    private static final int MAX_SWEEPS = 40;

    private final MiningConfig cfg;
    MineTopology t;
    private double timeS;

    // узлы: доли газов [g·nn + n], источники
    double[] c = new double[0];
    private double[] old = new double[0], external = new double[0], emit = new double[0];
    private double[] uptake = new double[0], dilution = new double[0], inflow = new double[0], outflow = new double[0];
    private double[] velocity = new double[0], degasCapture = new double[0], degasRate = new double[0];
    private int[] order = new int[0], indeg = new int[0];
    private boolean acyclic;

    // обнажения и отбитый уголь
    private long[] expKey = new long[16];
    private int[] expNode = new int[16];
    private double[] expRemaining = new double[16], expTonnes = new double[16];
    private boolean[] expBroken = new boolean[16];
    private int expCount;
    private final LongIntMap expIndex = new LongIntMap(64);

    // пыль по блокам (г/м²) и равномерные слои узла
    private long[] dustKey = new long[16];
    private int[] dustNode = new int[16];
    private double[] dustCoal = new double[16], dustInert = new double[16], dustVdaf = new double[16];
    private int dustCount;
    private final LongIntMap dustIndex = new LongIntMap(64);
    double[] uniCoal = new double[0], uniInert = new double[0], uniVdafCoal = new double[0];
    private double[] sumCoal = new double[0], sumInert = new double[0], sumVdafCoal = new double[0];
    boolean[] barrierSpent = new boolean[0];

    public MethaneField(MiningConfig cfg) {
        this.cfg = cfg;
    }

    /** Газоносность пласта на отметке y: x = x₀(1 − e^{−(72−y)/40}), м³/т; выше 72 — 0 (08 §8.2.4). */
    public static double gasContent(double x0M3PerT, double y) {
        return y >= 72 ? 0 : x0M3PerT * (1 - Math.exp(-(72 - y) / 40));
    }

    /**
     * Перестроить под новый граф: доли газов, равномерные слои пыли, внешние источники, дегазация и
     * сработавшие заслоны переносятся по ключам; новые узлы — со свежим воздухом; обнажения и пыль блоков
     * переприписываются узлам по ключу блока.
     */
    public void rebuild(MineTopology topology) {
        MineTopology prev = t;
        int nn = topology.nodeCount();
        double[] nc = new double[G * nn], nExt = new double[G * nn];
        double[] nUniC = new double[nn], nUniI = new double[nn], nUniV = new double[nn], nCap = new double[nn];
        for (int n = 0; n < nn; n++) {
            int o = prev == null ? -1 : prev.nodeIndex(topology.nodeKey(n));
            for (int g = 0; g < G; g++) {
                nc[g * nn + n] = o >= 0 && n != MineTopology.ATMOSPHERE ? c[g * prev.nodeCount() + o]
                        : MineGas.of(g).freshAir;
                nExt[g * nn + n] = o >= 0 ? external[g * prev.nodeCount() + o] : 0;
            }
            if (o >= 0) {
                nUniC[n] = uniCoal[o];
                nUniI[n] = uniInert[o];
                nUniV[n] = uniVdafCoal[o];
                nCap[n] = degasCapture[o];
            }
        }
        boolean[] nSpent = new boolean[topology.branchCount()];
        if (prev != null)
            for (int j = 0; j < nSpent.length; j++) {
                int o = prev.branchIndex(topology.branchKey(j));
                nSpent[j] = o >= 0 && barrierSpent[o];
            }
        t = topology;
        c = nc;
        external = nExt;
        uniCoal = nUniC;
        uniInert = nUniI;
        uniVdafCoal = nUniV;
        degasCapture = nCap;
        barrierSpent = nSpent;
        old = new double[G * nn];
        emit = new double[G * nn];
        uptake = new double[nn];
        dilution = new double[nn];
        inflow = new double[nn];
        outflow = new double[nn];
        velocity = new double[nn];
        degasRate = new double[nn];
        order = new int[nn];
        indeg = new int[nn];
        sumCoal = new double[nn];
        sumInert = new double[nn];
        sumVdafCoal = new double[nn];
        for (int e = 0; e < expCount; e++) expNode[e] = t.nodeOf(expKey[e]);
        for (int d = 0; d < dustCount; d++) {
            int n = dustNode[d] = t.nodeOf(dustKey[d]);
            if (n >= 0) addSums(n, dustCoal[d], dustInert[d], dustVdaf[d]);
        }
    }

    public MineTopology topology() {
        return t;
    }

    /** Физическое время поля, с. */
    public double timeS() {
        return timeS;
    }

    // --- газовыделение -------------------------------------------------------------------------------------

    /**
     * Блок угля обнажён (стал стенкой выработки).
     *
     * @param blockKey ключ блока, приписанного узлу заливкой
     * @param xCh4     газоносность, м³/т ({@link #gasContent})
     * @param t        физическое время обнажения, с (для восстановления — раньше текущего)
     */
    public void onExposed(long blockKey, double xCh4, double t) {
        if (expIndex.get(blockKey) != LongIntMap.MISSING) return;
        double tonnes = COAL_DENSITY / 1000;
        double total = KAPPA * xCh4 * tonnes * cfg.methaneEmissionScale();
        addExposure(blockKey, total * Math.exp(-Math.max(0, timeS - t) / cfg.desorptionTauS()), tonnes, false);
    }

    /**
     * Блок угля отбит: остаток газа уходит за час (если обнажения не было — весь газ κ·x), на узел оседает
     * пыль {@code mining.dust_per_ton_kg} на тонну.
     *
     * @param xCh4        газоносность, м³/т (если блок не был обнажён)
     * @param volatileDaf V^daf угля — для взрывчатости пыли
     */
    public void onMined(long blockKey, double xCh4, double volatileDaf) {
        double tonnes = COAL_DENSITY / 1000;
        int e = expIndex.get(blockKey);
        if (e == LongIntMap.MISSING) {
            addExposure(blockKey, KAPPA * xCh4 * tonnes * cfg.methaneEmissionScale(), tonnes, true);
        } else {
            expBroken[e] = true;
        }
        int n = t.nodeOf(blockKey);
        if (n > 0) {
            double surface = t.perimeter[n] * t.volume[n] / t.section[n];
            depositCoalDust(n, cfg.dustPerTonKg() * tonnes * 1000 / surface, volatileDaf);
        }
    }

    /** Обнажение закрыто (закладка, перемычка): газовыделение и окисление блока прекращаются. */
    public void onCovered(long blockKey) {
        int e = expIndex.get(blockKey);
        if (e != LongIntMap.MISSING) removeExposure(e);
    }

    private void addExposure(long key, double remaining, double tonnes, boolean broken) {
        if (expCount == expKey.length) {
            int cap = expKey.length * 2;
            expKey = Arrays.copyOf(expKey, cap);
            expNode = Arrays.copyOf(expNode, cap);
            expRemaining = Arrays.copyOf(expRemaining, cap);
            expTonnes = Arrays.copyOf(expTonnes, cap);
            expBroken = Arrays.copyOf(expBroken, cap);
        }
        int e = expCount++;
        expKey[e] = key;
        expNode[e] = t.nodeOf(key);
        expRemaining[e] = remaining;
        expTonnes[e] = tonnes;
        expBroken[e] = broken;
        expIndex.put(key, e);
    }

    private void removeExposure(int e) {
        expIndex.remove(expKey[e]);
        int last = --expCount;
        if (e != last) {
            expKey[e] = expKey[last];
            expNode[e] = expNode[last];
            expRemaining[e] = expRemaining[last];
            expTonnes[e] = expTonnes[last];
            expBroken[e] = expBroken[last];
            expIndex.put(expKey[e], e);
        }
    }

    /** Число записей газовыделения (обнажения и отбитый уголь). */
    public int exposureCount() {
        return expCount;
    }

    /** Остаток газа блока, м³ (0 — нет записи). */
    public double remainingGasM3(long blockKey) {
        int e = expIndex.get(blockKey);
        return e == LongIntMap.MISSING ? 0 : expRemaining[e];
    }

    /** Постоянный внешний источник газа в узле (пожар, утечка; отрицательный — отбор), м³/с. */
    public void setSource(int node, MineGas gas, double m3s) {
        external[gas.ordinal() * t.nodeCount() + node] = m3s;
    }

    /** Дегазация узла: доля газовыделения его блоков, уходящая в скважины (каптаж 0,3–0,6). */
    public void setDegasification(int node, double captureFrac) {
        degasCapture[node] = Math.max(0, Math.min(1, captureFrac));
    }

    /** Метан, отобранный дегазацией узла на последнем шаге, м³/с. */
    public double degasifiedM3s(int node) {
        return degasRate[node];
    }

    /** Газовыделение блоков узла на последнем шаге (после дегазации), м³/с. */
    public double emissionM3s(int node, MineGas gas) {
        return emit[gas.ordinal() * t.nodeCount() + node];
    }

    // --- шаг -----------------------------------------------------------------------------------------------

    /** Шаг SLOW длиной dt физ., с; расходы — из уже решённой сети. Без аллокаций. */
    public void stepSlow(double dtPhys, MineVentilation v) {
        int nn = t.nodeCount();
        System.arraycopy(c, 0, old, 0, c.length);
        Arrays.fill(emit, 0);
        Arrays.fill(uptake, 0);
        double kIn = Math.exp(-dtPhys / cfg.desorptionTauS()), kBroken = Math.exp(-dtPhys / BROKEN_TAU_S);
        for (int e = expCount - 1; e >= 0; e--) {
            double rem = expRemaining[e], k = expBroken[e] ? kBroken : kIn;
            double released = rem * (1 - k);
            expRemaining[e] = rem * k;
            int n = expNode[e];
            if (n > 0) {
                emit[CH4 * nn + n] += released / dtPhys;
                uptake[n] += O2_UPTAKE_M3_PER_T_S * expTonnes[e] / MineGas.O2.freshAir;
            }
            if (expBroken[e] && expRemaining[e] < DEPLETED_M3) removeExposure(e);
        }
        for (int n = 1; n < nn; n++) {
            double captured = degasCapture[n] * emit[CH4 * nn + n];
            degasRate[n] = captured;
            emit[CH4 * nn + n] -= captured;
            double o2used = uptake[n] * old[O2 * nn + n];
            emit[CO2 * nn + n] += OXIDATION_CO2_PER_O2 * o2used;
            emit[CO * nn + n] += OXIDATION_CO_PER_O2 * o2used;
            double gen = 0;
            for (int g = 0; g < G; g++) {
                double s = external[g * nn + n];
                emit[g * nn + n] += s;
                gen += emit[g * nn + n];
            }
            dilution[n] = Math.max(0, gen - o2used);
        }

        // расходы узлов
        Arrays.fill(inflow, 0);
        Arrays.fill(outflow, 0);
        for (int j = 0; j < t.branchCount(); j++) {
            double q = v.flow(j);
            int up = q >= 0 ? t.from[j] : t.to[j], dn = q >= 0 ? t.to[j] : t.from[j];
            outflow[up] += Math.abs(q);
            inflow[dn] += Math.abs(q);
        }
        for (int n = 1; n < nn; n++) velocity[n] = inflow[n] / t.section[n];
        flowOrder(v);

        for (int g = 0; g < G; g++) solveGas(g, dtPhys, v);
        timeS += dtPhys;
    }

    /** Порядок узлов по течению (Кан); узлы в контурах рециркуляции — в конце, тогда нужны повторные проходы. */
    private void flowOrder(MineVentilation v) {
        int nn = t.nodeCount(), head = 0, tail = 0;
        Arrays.fill(indeg, 0);
        for (int j = 0; j < t.branchCount(); j++) {
            double q = v.flow(j);
            if (q == 0) continue;
            int up = q > 0 ? t.from[j] : t.to[j], dn = q > 0 ? t.to[j] : t.from[j];
            if (up != MineTopology.ATMOSPHERE) indeg[dn]++;
        }
        for (int n = 1; n < nn; n++) if (indeg[n] == 0) order[tail++] = n;
        while (head < tail) {
            int u = order[head++];
            for (int a = t.adjStart[u]; a < t.adjStart[u + 1]; a++) {
                int j = t.adjBranch[a];
                double q = v.flow(j);
                if (q == 0) continue;
                int up = q > 0 ? t.from[j] : t.to[j], dn = q > 0 ? t.to[j] : t.from[j];
                if (up != u || dn == MineTopology.ATMOSPHERE) continue;
                if (--indeg[dn] == 0) order[tail++] = dn;
            }
        }
        acyclic = tail == nn - 1;
        if (!acyclic) for (int n = 1; n < nn; n++) if (indeg[n] > 0) order[tail++] = n;
    }

    private void solveGas(int g, double dt, MineVentilation v) {
        int nn = t.nodeCount(), base = g * nn;
        double lossCoeff = g == O2 ? 1 : 0;
        c[base] = MineGas.of(g).freshAir;
        int sweeps = acyclic ? 1 : MAX_SWEEPS;
        for (int s = 0; s < sweeps; s++) {
            double maxChange = 0;
            for (int i = 0; i < nn - 1; i++) {
                int n = order[i];
                double cap = t.volume[n] / dt;
                double rhs = cap * old[base + n] + emit[base + n], adv = 0;
                for (int a = t.adjStart[n]; a < t.adjStart[n + 1]; a++) {
                    int j = t.adjBranch[a];
                    double q = v.flow(j);
                    if (q > 0 && t.to[j] == n) adv += q * c[base + t.from[j]];
                    else if (q < 0 && t.from[j] == n) adv -= q * c[base + t.to[j]];
                }
                double diag = cap + Math.max(inflow[n], outflow[n]) + dilution[n] + lossCoeff * uptake[n];
                double x = Math.min(1, Math.max(0, (rhs + adv) / diag));
                maxChange = Math.max(maxChange, Math.abs(x - c[base + n]));
                c[base + n] = x;
            }
            if (maxChange < 1e-13) break;
        }
    }

    // --- показания -----------------------------------------------------------------------------------------

    /** Мольная доля газа в узле. */
    public double concentration(int node, MineGas gas) {
        return c[gas.ordinal() * t.nodeCount() + node];
    }

    /** Задать долю газа в узле (загрузка, сценарии). */
    public void setConcentration(int node, MineGas gas, double fraction) {
        if (node != MineTopology.ATMOSPHERE) c[gas.ordinal() * t.nodeCount() + node] = fraction;
    }

    /** Метан у кровли: слоевое скопление ×2 при скорости воздуха в узле &lt; 0,5 м/с. */
    public double roofMethane(int node) {
        double x = concentration(node, MineGas.CH4);
        return velocity[node] < LAYERING_VELOCITY ? Math.min(1, LAYERING_FACTOR * x) : x;
    }

    /** Скорость воздуха в узле (приток через сечение узла), м/с. */
    public double nodeVelocity(int node) {
        return velocity[node];
    }

    // --- пыль ----------------------------------------------------------------------------------------------

    /** Осевшая угольная пыль на блоке (с равномерным слоем узла), г/м². */
    public double dustLoading(long blockKey) {
        int d = dustIndex.get(blockKey), n = t.nodeOf(blockKey);
        return (d == LongIntMap.MISSING ? 0 : dustCoal[d]) + (n > 0 ? uniCoal[n] : 0);
    }

    /** Инертная пыль на блоке (с равномерным слоем узла), г/м². */
    public double inertLoading(long blockKey) {
        int d = dustIndex.get(blockKey), n = t.nodeOf(blockKey);
        return (d == LongIntMap.MISSING ? 0 : dustInert[d]) + (n > 0 ? uniInert[n] : 0);
    }

    /** Угольная пыль на блок (локально — у комбайна, конвейера). */
    public void depositCoalDust(long blockKey, double gPerM2, double volatileDaf) {
        int d = dustEntry(blockKey);
        dustCoal[d] += gPerM2;
        dustVdaf[d] += gPerM2 * volatileDaf;
        if (dustNode[d] >= 0) addSums(dustNode[d], gPerM2, 0, gPerM2 * volatileDaf);
    }

    /** Инертная пыль на блок ({@code stone_duster}). */
    public void depositStoneDust(long blockKey, double gPerM2) {
        int d = dustEntry(blockKey);
        dustInert[d] += gPerM2;
        if (dustNode[d] >= 0) addSums(dustNode[d], 0, gPerM2, 0);
    }

    /** Угольная пыль равномерно по выработке узла. */
    public void depositCoalDust(int node, double gPerM2, double volatileDaf) {
        uniCoal[node] += gPerM2;
        uniVdafCoal[node] += gPerM2 * volatileDaf;
    }

    /** Инертная пыль равномерно по узлу (осланцевание выработки). */
    public void depositStoneDust(int node, double gPerM2) {
        uniInert[node] += gPerM2;
    }

    /** Средняя осевшая угольная пыль узла, г/м². */
    public double meanCoalDust(int node) {
        return uniCoal[node] + sumCoal[node] / Math.max(1, t.blockCount[node]);
    }

    /** Инертная доля осевшей пыли узла x_ин. */
    public double inertFraction(int node) {
        double coal = meanCoalDust(node), inert = uniInert[node] + sumInert[node] / Math.max(1, t.blockCount[node]);
        return coal + inert > 0 ? inert / (coal + inert) : 1;
    }

    /** V^daf угольной пыли узла (взвешенное по массе). */
    public double dustVolatileDaf(int node) {
        double coal = uniCoal[node] * Math.max(1, t.blockCount[node]) + sumCoal[node];
        double v = uniVdafCoal[node] * Math.max(1, t.blockCount[node]) + sumVdafCoal[node];
        return coal > 0 ? v / coal : 0;
    }

    /** Концентрация пыли, которую поднимет волна, c_d = m_d·P/S, г/м³. */
    public double raisedDustGm3(int node) {
        return StoneDustRule.raisedConcentrationGm3(meanCoalDust(node), t.perimeter[node], t.section[node]);
    }

    /** Пыль узла поддерживает фронт при текущем метане. */
    public boolean dustExplosive(int node) {
        return StoneDustRule.dustExplosive(raisedDustGm3(node), inertFraction(node), dustVolatileDaf(node),
                concentration(node, MineGas.CH4));
    }

    private int dustEntry(long key) {
        int d = dustIndex.get(key);
        if (d != LongIntMap.MISSING) return d;
        if (dustCount == dustKey.length) {
            int cap = dustKey.length * 2;
            dustKey = Arrays.copyOf(dustKey, cap);
            dustNode = Arrays.copyOf(dustNode, cap);
            dustCoal = Arrays.copyOf(dustCoal, cap);
            dustInert = Arrays.copyOf(dustInert, cap);
            dustVdaf = Arrays.copyOf(dustVdaf, cap);
        }
        d = dustCount++;
        dustKey[d] = key;
        dustNode[d] = t.nodeOf(key);
        dustCoal[d] = dustInert[d] = dustVdaf[d] = 0;
        dustIndex.put(key, d);
        return d;
    }

    private void addSums(int n, double coal, double inert, double vdafCoal) {
        sumCoal[n] += coal;
        sumInert[n] += inert;
        sumVdafCoal[n] += vdafCoal;
    }

    // --- взрыв ---------------------------------------------------------------------------------------------

    /**
     * Источник воспламенения в узле. Фронт начинается, если метан узла во взрывных пределах; взрывные работы
     * начинают и чистый взрыв пыли. Узел воспламенения сгорает сразу, дальше — {@link ExplosionFront#advance}
     * по узлу за тик.
     */
    public ExplosionFront ignite(int node, IgnitionSource source) {
        boolean starts = node != MineTopology.ATMOSPHERE
                && (StoneDustRule.methaneExplosive(concentration(node, MineGas.CH4))
                        || source == IgnitionSource.BLASTING && dustExplosive(node));
        return new ExplosionFront(this, node, starts);
    }

    /** Узел поддерживает фронт: метан 5–15 % или взрывоопасная пыль (с учётом метана). */
    boolean propagates(int node) {
        return StoneDustRule.methaneExplosive(concentration(node, MineGas.CH4)) || dustExplosive(node);
    }

    /**
     * Сжечь узел фронта: энергия в разрушение E = f_blast(V_CH4·Q_CH4 + m_d·Q_d) (пыль — если взрывоопасна),
     * в узле остаётся рудничный газ. Кислород идёт сначала на водород, затем на углерод в CO и дальше в CO₂;
     * не меньше {@link ExplosionFront#CO_MIN_SHARE} углерода остаётся в CO (диссоциация, закалка пламени).
     * Летучие пыли (V^daf от массы) горят как метан по массе, кокс пыли во фронте не догорает [допущение].
     *
     * @return E_mech, Дж
     */
    double burn(int node) {
        int nn = t.nodeCount();
        double xCh4 = concentration(node, MineGas.CH4), vol = t.volume[node];
        boolean dust = dustExplosive(node);
        double cd = dust ? raisedDustGm3(node) : 0;
        double energy = cfg.explosionBlastFraction() * (xCh4 * vol * Q_CH4 + cd * vol / 1000 * Q_DUST);

        double molesPerM3 = MineVentilation.AIR_PRESSURE / (Chimney.GAS_CONSTANT * t.tempK[node]);
        double fuel = xCh4 + cd * dustVolatileDaf(node) / 16.04 / molesPerM3; // CH₄-эквивалент, мольн. доля
        double o2 = concentration(node, MineGas.O2);
        double r = Math.max(0, o2 - fuel); // водород: CH₄ → 2H₂O требует 1 O₂ на моль
        double toCo = Math.min(fuel, 2 * r);
        r -= toCo / 2;
        double toCo2 = Math.min(toCo * (1 - ExplosionFront.CO_MIN_SHARE), 2 * r);
        r -= toCo2 / 2;
        c[CH4 * nn + node] = 0;
        c[O2 * nn + node] = r;
        c[CO * nn + node] = Math.min(1, c[CO * nn + node] + toCo - toCo2);
        c[CO2 * nn + node] = Math.min(1, c[CO2 * nn + node] + toCo2);
        if (dust) {
            uniCoal[node] = 0;
            uniVdafCoal[node] = 0;
            sumCoal[node] = 0;
            sumVdafCoal[node] = 0;
            for (int d = 0; d < dustCount; d++)
                if (dustNode[d] == node) {
                    dustCoal[d] = 0;
                    dustVdaf[d] = 0;
                }
        }
        return energy;
    }
}
