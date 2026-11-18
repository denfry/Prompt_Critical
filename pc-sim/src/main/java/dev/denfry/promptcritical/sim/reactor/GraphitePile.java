package dev.denfry.promptcritical.sim.reactor;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.neutronics.AbsorberMaterial;
import dev.denfry.promptcritical.sim.neutronics.CellComposition;
import dev.denfry.promptcritical.sim.neutronics.CellFeedbackState;
import dev.denfry.promptcritical.sim.neutronics.CoolantKind;
import dev.denfry.promptcritical.sim.neutronics.DelayedGroups;
import dev.denfry.promptcritical.sim.neutronics.Westcott;
import dev.denfry.promptcritical.sim.neutronics.DecayHeat;
import dev.denfry.promptcritical.sim.neutronics.FissionPoisons;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesign;
import dev.denfry.promptcritical.sim.neutronics.LatticeDesigns;
import dev.denfry.promptcritical.sim.neutronics.LatticeModel;
import dev.denfry.promptcritical.sim.neutronics.ModeratorKind;
import dev.denfry.promptcritical.sim.neutronics.NeutronicsConfig;
import dev.denfry.promptcritical.sim.neutronics.NodalFluxSolver;
import dev.denfry.promptcritical.sim.neutronics.PeriodMeter;
import dev.denfry.promptcritical.sim.neutronics.PointKinetics;
import dev.denfry.promptcritical.sim.neutronics.QuasiStaticFeedback;
import dev.denfry.promptcritical.sim.neutronics.ReactivityBudget;
import dev.denfry.promptcritical.sim.neutronics.ReactivityComponent;
import dev.denfry.promptcritical.sim.neutronics.SixFactor;
import dev.denfry.promptcritical.sim.nuclide.NuclideVector;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Графитовый котёл T0 (01 §1.14.4; CP-1, 1942, и X-10, 1943): природный металлический уран в алюминиевых оболочках
 * в каналах графитовой кладки, охлаждение — воздух зала. Нодальная задача по кладке, которую собрал игрок (узел —
 * блок 1 м³), решается раз в секунду и, пока движутся стержни, — каждый тик с тёплого старта; температура между
 * решениями входит квазистатически ({@link QuasiStaticFeedback}), кинетика — точечная с запаздывающими группами
 * решётки.
 *
 * <p>Приборы — как на CP-1: борный счётчик (импульсы, мёртвое время, пуассоновский шум) для пускового диапазона и
 * ионизационная камера (мощность) для рабочего; период — {@link PeriodMeter}. Реактивность и k игроку не видны:
 * критичность ищут по 1/M и периоду.
 *
 * <p>Защита — в самом котле и не зависит от автоматики игрока (P6): по периоду и по мощности сбрасываются аварийные
 * стержни «zip» (падают под весом), регулирующие идут вниз приводом. Снять защиту можно только ниже уставок;
 * регулирующие не выводятся, пока аварийные не взведены.
 *
 * <p>Отравление Xe-135 и Sm-149 и остаточное тепловыделение — точечные, по средней по топливным узлам скорости
 * делений ({@link FissionPoisons}, {@link DecayHeat}); на ваттах CP-1 они ничтожны, на мегаваттах X-10 — нет.
 * Поглощение ядов входит в константы топливных узлов (STATIC), XENON и SAMARIUM баланса — только для показа.
 *
 * <p>Чего нет: выгорания, энергии Вигнера, принудительного охлаждения X-10, разницы потока в топливе и в ячейке для
 * ядов. Температура — по двум сосредоточенным ёмкостям, топливо и графит.
 */
public final class GraphitePile {
    /** Шаг FAST, с. */
    public static final double DT = 0.05;
    /** Столько секунд ρ ≥ 0 без учёта источника — котёл критичен. */
    public static final double CRITICAL_HOLD_S = 5;
    /**
     * Замер для метода 1/M (01 §1.14 пример 3, достижение {@code one_over_m}): подкритичный котёл с источником, счётчиком
     * и неподвижными стержнями при одной загрузке столько секунд — счёт установился и записан. [BALANCE] 30 с — время
     * счёта пересчётного прибора.
     */
    public static final double APPROACH_COUNT_S = 30;
    /** ν природного урана на тепловых нейтронах. */
    static final double NU = 2.43;
    /** Теплоёмкость металлического урана и графита, Дж/(кг·К); плотность графита кладки, кг/м³. */
    static final double CP_URANIUM = 116, CP_GRAPHITE = 710, GRAPHITE_DENSITY = 1700;
    /** Доля канала стержня в узле (воздух вокруг рейки). */
    private static final double ROD_CHANNEL_VOID = 0.01;
    private static final double COUNTER_TAU_S = 1;
    private static final double POWER_RESET_MARGIN = 0.9;
    /** Внешних итераций нодальной задачи на тик, пока движутся стержни (тёплый старт). */
    private static final int ROD_OUTER_ITERATIONS = 2;
    /** Σ_s = D/(h/2)²: D графита ≈ 0,85 см, узел h = 100 см; см⁻¹. */
    private static final double SATURATION_SIGMA = 0.85 / (50.0 * 50.0);

    private final PileConfig cfg;
    private final LatticeModel model;
    private final NuclideVector fuel = NuclideVector.uranium(1000, LatticeDesigns.referenceEnrichment(LatticeDesigns.T0_SLUG));
    private final SixFactor[] fuelByLoad = new SixFactor[PileLayout.FULL_LOAD + 1];
    private final CellComposition[] fuelCells = new CellComposition[PileLayout.FULL_LOAD + 1];
    private final ReactivityBudget budget = new ReactivityBudget();
    private final QuasiStaticFeedback feedback = new QuasiStaticFeedback();
    private final PeriodMeter periodMeter = new PeriodMeter(DT);
    private final Map<Long, Double> rodInsertion = new HashMap<>();

    private PileLayout layout = PileLayout.EMPTY;
    private NodalFluxSolver solver;
    private PointKinetics kinetics;
    private boolean fissile, structural = true;
    private SixFactor graphite;
    private SixFactor[] nodeConst = new SixFactor[0];
    private CellFeedbackState[] nodeState = new CellFeedbackState[0];
    private CellComposition[] nodeCell = new CellComposition[0];
    private double[] absorberNow = new double[0], absorberRef = new double[0];
    private double[] fuelTemps = new double[0], modTemps = new double[0];
    private double[] cps = new double[0], trueCps = new double[0];
    private double constTf = Double.NaN, constTm = Double.NaN, constXe = 0, constSm = 0;
    private final FissionPoisons poisons = new FissionPoisons();
    private final DecayHeat decayHeat = new DecayHeat();
    /** Топливных узлов и средняя ячейка последнего STATIC: для скорости делений и потока ядов. */
    private int fuelNodes;
    private SixFactor averageFuel;
    private double keff, generationTime = 1e-3, sourceNorm;
    private double power, fuelTempK, graphiteTempK;
    private double savedPower = PointKinetics.MIN_POWER;
    private double[] savedPrecursors = new double[0];
    private DelayedGroups groups;
    private int controlDrive;
    private boolean winding;
    private PileTrip trip = PileTrip.NONE;
    private double criticalTimer;
    private boolean criticalReported, cladFailed, graphiteFire;
    /** Загрузки (блочков в кладке), при которых записан установившийся подкритичный счёт до первой критичности. */
    private final java.util.TreeSet<Integer> approachLoads = new java.util.TreeSet<>();
    private double approachTimer, approachRods = -1;
    private int approachLoad = -1;

    public GraphitePile(PileConfig cfg, NeutronicsConfig neutronics) {
        this.cfg = cfg;
        this.model = new LatticeModel(neutronics);
        fuelTempK = graphiteTempK = cfg.ambientK();
    }

    public GraphitePile() {
        this(PileConfig.defaults(), NeutronicsConfig.defaults());
    }

    // ------------------------------------------------------------------ строение

    /**
     * Новая кладка от биндинга. Одинаковая подпись — без действия. Положения стержней переносятся по ключу привода,
     * новые стержни приходят введёнными; мощность и температуры сохраняются.
     */
    public void form(PileLayout l) {
        if (l.signature() == layout.signature() && l.nodeCount() == layout.nodeCount()) return;
        Map<Long, Double> old = new HashMap<>(rodInsertion);
        rodInsertion.clear();
        for (int i = 0; i < l.rodCount(); i++) {
            long key = l.rod(i).key();
            rodInsertion.put(key, old.getOrDefault(key, 1.0));
        }
        if (kinetics != null) {
            savedPower = kinetics.power();
            savedPrecursors = precursors();
        }
        layout = l;
        int n = l.nodeCount();
        nodeConst = new SixFactor[n];
        nodeState = new CellFeedbackState[n];
        nodeCell = new CellComposition[n];
        absorberNow = new double[n];
        absorberRef = new double[n];
        fuelTemps = new double[n];
        modTemps = new double[n];
        cps = new double[l.detectorCount()];
        trueCps = new double[l.detectorCount()];
        fissile = l.fuelNodes() > 0;
        solver = null;
        kinetics = null;
        structural = true;
        criticalTimer = 0;
        sourceNorm = 0;
        if (!fissile) {
            power = 0;
            budget.set(ReactivityComponent.STATIC, 0);
            budget.resetFastComponents();
        }
    }

    public PileLayout layout() {
        return layout;
    }

    // ------------------------------------------------------------------ шаг

    /** Один FAST-шаг {@link #DT}; {@code slow} — в этом тике и SLOW (новые константы и нодальная задача). */
    public void step(boolean slow, SplittableRandom random, PileEvents events) {
        moveRods();
        updateAbsorbers();
        if (fissile) {
            if (slow || structural || solver == null) solveStatic();
            else if (rodsMoved()) solveRods();
            fillTemps();
            budget.set(ReactivityComponent.MODERATOR_TEMP, feedback.moderator(modTemps));
            budget.set(ReactivityComponent.DOPPLER, feedback.doppler(fuelTemps));
            power = kinetics.step(DT, budget.total(), sourceNorm);
        }
        double pW = powerW();
        advancePoisons(pW);
        decayHeat.step(DT, pW);
        stepThermal(pW + decayHeat.powerW());
        periodMeter.sample(fissile ? power : PointKinetics.MIN_POWER);
        protect(pW, events);
        count(random);
        consequences(events);
        approachCount();
        if (fissile && budget.total() >= 0 && pW > 0) {
            criticalTimer += DT;
            if (criticalTimer >= CRITICAL_HOLD_S && !criticalReported) {
                criticalReported = true;
                events.critical(pW);
                events.approachPoints(approachLoads.size());
            }
        } else {
            criticalTimer = 0;
        }
    }

    private void moveRods() {
        for (int i = 0; i < layout.rodCount(); i++) {
            PileLayout.Rod r = layout.rod(i);
            double x = rodInsertion.getOrDefault(r.key(), 1.0), len = r.length();
            if (r.kind() == PileLayout.RodKind.ZIP) {
                if (tripped()) x += cfg.zipFallSpeedMS() * DT / len;
                else if (winding) x -= cfg.zipWindSpeedMS() * DT / len;
            } else {
                int dir = tripped() ? -1 : controlDrive;
                if (dir > 0 && !zipCocked()) dir = 0;
                x -= dir * cfg.controlRodSpeedMS() * DT / len;
            }
            rodInsertion.put(r.key(), Math.max(0, Math.min(1, x)));
        }
        if (winding && zipCocked()) winding = false;
    }

    private void updateAbsorbers() {
        java.util.Arrays.fill(absorberNow, 0);
        for (int i = 0; i < layout.rodCount(); i++) {
            PileLayout.Rod r = layout.rod(i);
            double in = rodInsertion.getOrDefault(r.key(), 1.0) * r.length();
            for (int j = 0; j < r.length(); j++) {
                double cover = Math.max(0, Math.min(1, in - j));
                absorberNow[r.node(j)] = partialAbsorber(cover, cfg.rodAbsorberFraction());
            }
        }
    }

    /**
     * Доля кадмия в частично перекрытом узле (поправка на «зубцы» стержня в нодальных кодах). Кадмий в узле 1 м
     * насыщен: поглощение узла R(Σ) ≈ R_max Σ/(Σ + Σ_s), Σ_s = D/(h/2)² — масштаб, на котором узел перестаёт быть
     * прозрачным. Объёмное усреднение c·Σ дало бы почти полный вес уже при малом перекрытии и скачок реактивности,
     * когда конец стержня выходит из узла. Здесь Σ(c) выбрано так, что R(Σ(c)) = c·R(Σ_full):
     * Σ(c) = c Σ_s Σ_f / (Σ_s + (1 − c) Σ_f).
     */
    static double partialAbsorber(double cover, double fullFraction) {
        if (cover <= 0) return 0;
        if (cover >= 1) return fullFraction;
        double sat = SATURATION_SIGMA / AbsorberMaterial.CADMIUM.sigmaA();
        return cover * sat * fullFraction / (sat + (1 - cover) * fullFraction);
    }

    private void fillTemps() {
        double tf = fuelTempK, tm = graphiteTempK;
        for (int i = 0; i < fuelTemps.length; i++) {
            fuelTemps[i] = tf;
            modTemps[i] = tm;
        }
    }

    /** Константы узлов при текущих температурах и стержнях, нодальная задача, STATIC, веса квазистатики. */
    private void solveStatic() {
        double tn = graphiteTempK + LatticeModel.NEUTRON_GAS_OFFSET;
        double xe = poisons.xenonAbs(sigmaXeB(tn)), sm = poisons.samariumAbs(sigmaSmB(tn));
        CellFeedbackState s = new CellFeedbackState(fuelTempK, 1.2, graphiteTempK, 0, xe, sm);
        if (Math.abs(fuelTempK - constTf) > 0.5 || Math.abs(graphiteTempK - constTm) > 0.5 || graphite == null
                || poisonMoved(xe, constXe) || poisonMoved(sm, constSm)) {
            constTf = fuelTempK;
            constTm = graphiteTempK;
            constXe = xe;
            constSm = sm;
            java.util.Arrays.fill(fuelByLoad, null);
            graphite = model.evaluate(graphiteCell(0), null, s);
        }
        int n = layout.nodeCount(), maxLoad = 0;
        fuelNodes = 0;
        for (int i = 0; i < n; i++) {
            PileLayout.NodeKind k = layout.kind(i);
            int load = layout.load(i);
            if (k == PileLayout.NodeKind.FUEL && load > 0) {
                maxLoad = Math.max(maxLoad, load);
                fuelNodes++;
                nodeCell[i] = fuelCell(load);
                if (fuelByLoad[load] == null) fuelByLoad[load] = model.evaluate(nodeCell[i], fuel, s);
                nodeConst[i] = fuelByLoad[load];
            } else if (k == PileLayout.NodeKind.ROD_CHANNEL) {
                nodeCell[i] = graphiteCell(absorberNow[i]);
                nodeConst[i] = absorberNow[i] > 0 ? model.evaluate(nodeCell[i], null, s) : graphite;
            } else {
                nodeCell[i] = graphiteCell(0);
                nodeConst[i] = graphite;
            }
            nodeState[i] = s;
        }
        if (solver == null || structural) {
            solver = new NodalFluxSolver();
            solver.rebuild(nodeCell, layout.neighbors());
            solver.updateConstants(nodeConst);
            solver.converge();
        } else {
            solver.updateConstants(nodeConst);
            solver.iterate(model.config().nodalOuterIterationsPerUpdate());
        }
        keff = solver.keff();
        feedback.capture(solver, nodeConst, nodeState);
        System.arraycopy(absorberNow, 0, absorberRef, 0, n);
        budget.set(ReactivityComponent.STATIC, (keff - 1) / keff);
        budget.resetFastComponents();

        SixFactor avg = fuelByLoad[maxLoad];
        averageFuel = avg;
        // показ: ρ ядов ≈ −Σ_яда/Σ_a ячейки (через коэффициент использования f)
        budget.set(ReactivityComponent.XENON, -xe / avg.sigmaA());
        budget.set(ReactivityComponent.SAMARIUM, -sm / avg.sigmaA());
        double b2 = Math.max(0, (avg.kInf() / keff - 1) / avg.migrationArea());
        groups = model.delayedGroups(model.fissionFractions(fuelCell(maxLoad), fuel, s), b2, avg);
        generationTime = model.generationTime(avg, graphiteTempK + LatticeModel.NEUTRON_GAS_OFFSET);
        double s0 = layout.sources() * cfg.sourceNeutronsPerS();
        sourceNorm = PointKinetics.normalizedSource(s0, NU, generationTime, cfg.nominalPowerW());
        if (kinetics == null) {
            // Свежая кладка с источником: сразу подкритическое равновесие n = −sΛ/ρ, иначе подъём от нуля до
            // уровня источника прибор периода принял бы за разгон.
            double rho = budget.total(); // только STATIC: стержни и температуры только что захвачены
            double n0 = savedPower;
            if (savedPower <= 2 * PointKinetics.MIN_POWER && sourceNorm > 0 && rho < 0) n0 = -sourceNorm * generationTime / rho;
            kinetics = new PointKinetics(groups, generationTime, n0, model.config());
            if (savedPrecursors.length > 0) kinetics.restore(n0, savedPrecursors);
            savedPrecursors = new double[0];
            periodMeter.reset();
        } else {
            kinetics.setParameters(groups, generationTime);
        }
        structural = false;
    }

    /** Яды сместились на 1 % (или появились): константы топливных узлов пересчитать. */
    private static boolean poisonMoved(double now, double ref) {
        return Math.abs(now - ref) > 0.01 * Math.max(now, ref) && Math.abs(now - ref) > 1e-9;
    }

    private static double sigmaXeB(double tn) {
        return FissionPoisons.SIGMA_XE_B * Westcott.g(Westcott.Factor.XE135_A, tn) * Westcott.oneOverV(tn);
    }

    private static double sigmaSmB(double tn) {
        return FissionPoisons.SIGMA_SM_B * Westcott.g(Westcott.Factor.SM149_A, tn) * Westcott.oneOverV(tn);
    }

    /**
     * Шаг ядов: средняя по топливным узлам (1 м³) скорость делений F = P/(E_f·V) и поток φ = F/Σ_f средней ячейки.
     * После останова и без топлива — только распад.
     */
    private void advancePoisons(double pW) {
        double f = 0, phi = 0;
        if (fissile && fuelNodes > 0 && averageFuel != null && averageFuel.sigmaF() > 0) {
            f = pW / (PointKinetics.FISSION_ENERGY * fuelNodes * 1e6);
            phi = f / averageFuel.sigmaF();
        }
        double tn = graphiteTempK + LatticeModel.NEUTRON_GAS_OFFSET;
        poisons.step(DT, f, phi, sigmaXeB(tn), sigmaSmB(tn));
    }

    /** Остаточное тепловыделение, Вт: греет топливо, но ионизационная камера его не видит. */
    /**
     * Абсолютный тепловой поток по узлам кладки, н/(см²·с) (12 §12.1): форма — нодальное решение, нормировка —
     * мощность делений: F = P/E_f = Σ φ_i·Σ_f,i·V_i, V — блок 1 м³. Узлы без делений (графит, канал облучения) получают
     * поток той же нормировки. Котёл заглушен или кладка без топлива — нули.
     *
     * @param out массив длиной не меньше числа узлов; возвращается он же
     */
    public double[] nodeFluxes(double[] out) {
        int n = layout.nodeCount();
        java.util.Arrays.fill(out, 0, Math.min(n, out.length), 0);
        double[] phi = fissile && solver != null ? solver.flux() : null;
        if (phi == null || nodeConst.length < n) return out;
        double denom = 0;
        for (int i = 0; i < n; i++) if (nodeConst[i] != null) denom += phi[i] * nodeConst[i].sigmaF() * NODE_CM3;
        double fissions = powerW() / PointKinetics.FISSION_ENERGY;
        if (!(denom > 0) || !(fissions > 0)) return out;
        double k = fissions / denom;
        for (int i = 0; i < n; i++) out[i] = phi[i] * k;
        return out;
    }

    /** Делений в узле в секунду при потоках {@code flux} из {@link #nodeFluxes}. */
    public double nodeFissionRate(double[] flux, int node) {
        return node < nodeConst.length && nodeConst[node] != null ? flux[node] * nodeConst[node].sigmaF() * NODE_CM3 : 0;
    }

    /** Объём узла кладки — блок 1 м³, см³. */
    public static final double NODE_CM3 = 1e6;

    public double decayHeatW() {
        return decayHeat.powerW();
    }

    /** Σ_a Xe-135 средней топливной ячейки, см⁻¹. */
    public double xenonAbs() {
        return poisons.xenonAbs(sigmaXeB(graphiteTempK + LatticeModel.NEUTRON_GAS_OFFSET));
    }

    /** Запись установившегося подкритичного счёта при текущей загрузке (метод 1/M до первой критичности). */
    private void approachCount() {
        if (!fissile || criticalReported || cps.length == 0 || layout.sources() == 0 || budget.total() >= 0) {
            approachTimer = 0;
            return;
        }
        double rods = 0;
        for (double v : rodInsertion.values()) rods += v;
        int load = layout.totalLoad();
        if (load != approachLoad || Math.abs(rods - approachRods) > 1e-6) {
            approachLoad = load;
            approachRods = rods;
            approachTimer = 0;
        }
        approachTimer += DT;
        if (approachTimer >= APPROACH_COUNT_S) approachLoads.add(load);
    }

    /** Сколько разных загрузок записано по 1/M до первой критичности. */
    public int approachPoints() {
        return approachLoads.size();
    }

    private boolean rodsMoved() {
        for (int i = 0; i < absorberNow.length; i++) if (Math.abs(absorberNow[i] - absorberRef[i]) > 1e-9) return true;
        return false;
    }

    /**
     * Стержни сдвинулись между SLOW: новые константы только узлов каналов и тёплые итерации нодальной задачи.
     * Квазистатика первого порядка здесь не годится — кадмий чёрный (ΔΣ_a ≈ 0,5 см⁻¹ против 3·10⁻⁴ у графита),
     * теория возмущений завышает его вес в разы, и реактивность прыгала бы пилой на каждом SLOW. Температуры
     * остаются квазистатическими: константы считаны при температурах последнего SLOW.
     */
    private void solveRods() {
        CellFeedbackState s = nodeState[0];
        for (int i = 0; i < nodeConst.length; i++) {
            if (layout.kind(i) != PileLayout.NodeKind.ROD_CHANNEL || absorberNow[i] == absorberRef[i]) continue;
            nodeCell[i] = graphiteCell(absorberNow[i]);
            nodeConst[i] = absorberNow[i] > 0 ? model.evaluate(nodeCell[i], null, s) : graphite;
        }
        solver.updateConstants(nodeConst);
        solver.iterate(ROD_OUTER_ITERATIONS);
        keff = solver.keff();
        budget.set(ReactivityComponent.STATIC, (keff - 1) / keff);
        System.arraycopy(absorberNow, 0, absorberRef, 0, absorberNow.length);
    }

    /** Узел T0 с {@code load} кассетами из {@link PileLayout#FULL_LOAD}: реже каналы — меньше топлива в кубометре. */
    private CellComposition fuelCell(int load) {
        CellComposition c = fuelCells[load];
        if (c != null) return c;
        LatticeDesign d = LatticeDesigns.get(LatticeDesigns.T0_SLUG);
        double k = (double) load / PileLayout.FULL_LOAD;
        int channels = Math.max(1, (int) Math.round(d.channelsPerCell() * k));
        double scale = (double) channels / d.channelsPerCell();
        double fuelF = d.fuelFraction() * scale, cladF = d.cladFraction() * scale, coolF = d.coolantFraction() * scale;
        LatticeDesign scaled = new LatticeDesign(d.id() + "_" + load, d.geometry(), d.fuelMaterial(), d.cladMaterial(),
                d.coolant(), d.moderator(), d.rodOuterRadius(), d.pelletRadius(), d.cladThickness(), d.pitch() / Math.sqrt(scale),
                d.rodsPerAssembly(), d.tubeInnerRadius(), d.tubeThickness(), channels, channels, fuelF, cladF, coolF,
                1 - fuelF - cladF - coolF);
        c = CellComposition.fuel(scaled, LatticeDesigns.NUCLEAR_GRAPHITE_IMPURITY_PPM);
        fuelCells[load] = c;
        return c;
    }

    private static CellComposition graphiteCell(double absorber) {
        return CellComposition.nonFuel(ModeratorKind.GRAPHITE, 1 - ROD_CHANNEL_VOID - absorber, CoolantKind.AIR,
                ROD_CHANNEL_VOID, LatticeDesigns.NUCLEAR_GRAPHITE_IMPURITY_PPM).withAbsorber(AbsorberMaterial.CADMIUM, absorber);
    }

    // ------------------------------------------------------------------ тепло

    private void stepThermal(double pW) {
        int n = layout.nodeCount();
        if (n == 0) return;
        LatticeDesign d = LatticeDesigns.get(LatticeDesigns.T0_SLUG);
        double uMass = 0, contact = 0;
        for (int i = 0; i < n; i++) {
            if (layout.kind(i) != PileLayout.NodeKind.FUEL || layout.load(i) == 0) continue;
            double share = (double) layout.load(i) / PileLayout.FULL_LOAD;
            uMass += d.fuelFraction() * share * d.fuelMaterial().density() * 1000;
            contact += cfg.fuelToGraphiteWK() * share;
        }
        double cg = n * GRAPHITE_DENSITY * CP_GRAPHITE;
        double cool = layout.exposedFaces() * cfg.surfaceCoolingWK();
        if (uMass > 0) {
            double cf = uMass * CP_URANIUM;
            // Неявно по двум ёмкостям: шаг устойчив при любой мощности
            double a = contact * DT;
            double tf = fuelTempK, tg = graphiteTempK;
            double det = (cf + a) * (cg + a + cool * DT) - a * a;
            double rf = cf * tf + pW * DT, rg = cg * tg + cool * DT * cfg.ambientK();
            fuelTempK = (rf * (cg + a + cool * DT) + a * rg) / det;
            graphiteTempK = ((cf + a) * rg + a * rf) / det;
        } else {
            graphiteTempK = (cg * graphiteTempK + cool * DT * cfg.ambientK()) / (cg + cool * DT);
            fuelTempK = graphiteTempK;
        }
    }

    // ------------------------------------------------------------------ защита и приборы

    private void protect(double pW, PileEvents events) {
        if (tripped()) return;
        double t = periodMeter.period();
        PileTrip reason = PileTrip.NONE;
        if (pW > cfg.powerTripW()) reason = PileTrip.HIGH_POWER;
        else if (t > 0 && t < cfg.periodTripS()) reason = PileTrip.SHORT_PERIOD;
        if (reason != PileTrip.NONE) {
            trip = reason;
            winding = false;
            events.tripped(reason, pW, t);
        }
    }

    private void count(SplittableRandom random) {
        double production = NU * PointKinetics.fissionRate(fissile ? power : 0, cfg.nominalPowerW())
                + layout.sources() * cfg.sourceNeutronsPerS();
        double[] phi = fissile && solver != null ? solver.flux() : null;
        double mean = 0;
        if (phi != null) {
            for (double v : phi) mean += v;
            mean /= phi.length;
        }
        double alpha = -Math.expm1(-DT / COUNTER_TAU_S);
        for (int i = 0; i < cps.length; i++) {
            int node = layout.detector(i).node();
            double w = phi != null && mean > 0 ? Math.max(0.05, Math.min(5, phi[node] / mean)) : 1;
            double rate = cfg.detectorEfficiency() * production * w + cfg.backgroundCps();
            double measured = rate / (1 + rate * cfg.detectorDeadTimeS());
            trueCps[i] = measured;
            double counts = poisson(random, measured * DT);
            cps[i] += alpha * (counts / DT - cps[i]);
        }
    }

    private static double poisson(SplittableRandom r, double mean) {
        if (mean <= 0) return 0;
        if (mean > 50) return Math.max(0, Math.round(mean + Math.sqrt(mean) * gaussian(r)));
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= r.nextDouble();
        } while (p > l);
        return k - 1;
    }

    private static double gaussian(SplittableRandom r) {
        double u = Math.max(1e-300, r.nextDouble()), v = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
    }

    private void consequences(PileEvents events) {
        if (!cladFailed && fuelTempK > cfg.cladFailureK()) {
            cladFailed = true;
            events.cladFailed(fuelTempK);
        }
        if (!graphiteFire && graphiteTempK > cfg.graphiteFireK()) {
            graphiteFire = true;
            events.graphiteFire(graphiteTempK);
        }
    }

    // ------------------------------------------------------------------ управление

    /** Привод регулирующих: −1 вниз, 0 стоп, +1 вверх (вверх — только при взведённых аварийных). */
    public void driveControlRods(int dir) {
        controlDrive = Integer.signum(dir);
    }

    public int controlDrive() {
        return controlDrive;
    }

    /** Взвести аварийные стержни лебёдкой; при сработавшей защите — нельзя. */
    public boolean windZip() {
        if (tripped() || zipCount() == 0) return false;
        winding = !zipCocked();
        return winding;
    }

    public void scram() {
        if (tripped()) return;
        trip = PileTrip.MANUAL;
        winding = false;
    }

    /** То же, что {@link #scram()}, с событием (кнопка в зале, команда). */
    public void scram(PileEvents events) {
        if (tripped()) return;
        scram();
        events.tripped(PileTrip.MANUAL, powerW(), periodMeter.period());
    }

    /** Снять защиту: мощность ниже уставки и нет разгона короче уставки. */
    public boolean resetTrip() {
        if (!tripped()) return true;
        double t = periodMeter.period();
        if (powerW() > POWER_RESET_MARGIN * cfg.powerTripW() || (t > 0 && t < 2 * cfg.periodTripS())) return false;
        trip = PileTrip.NONE;
        controlDrive = 0;
        return true;
    }

    // ------------------------------------------------------------------ показания

    public boolean tripped() {
        return trip != PileTrip.NONE;
    }

    public PileTrip trip() {
        return trip;
    }

    public boolean winding() {
        return winding;
    }

    /** Мощность по ионизационной камере, Вт. */
    public double powerW() {
        return fissile ? power * cfg.nominalPowerW() : 0;
    }

    /** Показание счётчика {@code i}, имп./с (сглаженное, с шумом). */
    public double countRate(int detector) {
        return cps[detector];
    }

    /** Ожидаемое показание без шума — для тестов и подсказок. */
    public double expectedCountRate(int detector) {
        return trueCps[detector];
    }

    public int detectorCount() {
        return cps.length;
    }

    /** Период по прибору, с; ∞ — стационар; отрицательный — спад. */
    public double periodS() {
        return periodMeter.period();
    }

    public double fuelTempK() {
        return fuelTempK;
    }

    public double graphiteTempK() {
        return graphiteTempK;
    }

    /** Средний ввод регулирующих (0 — выведены, 1 — введены); NaN — их нет. */
    public double controlInsertion() {
        return meanInsertion(PileLayout.RodKind.CONTROL);
    }

    /** Наибольший ввод аварийных: 0 — все взведены. NaN — их нет. */
    public double zipInsertion() {
        double m = Double.NaN;
        for (int i = 0; i < layout.rodCount(); i++) {
            PileLayout.Rod r = layout.rod(i);
            if (r.kind() != PileLayout.RodKind.ZIP) continue;
            double x = rodInsertion.getOrDefault(r.key(), 1.0);
            m = Double.isNaN(m) ? x : Math.max(m, x);
        }
        return m;
    }

    public boolean zipCocked() {
        double z = zipInsertion();
        return !Double.isNaN(z) && z <= 1e-9;
    }

    public int zipCount() {
        return rodCount(PileLayout.RodKind.ZIP);
    }

    public int controlCount() {
        return rodCount(PileLayout.RodKind.CONTROL);
    }

    public double rodInsertion(long key) {
        return rodInsertion.getOrDefault(key, 1.0);
    }

    /** Ввод одного стержня — для отладки и тестов; при сработавшей защите не действует. */
    public void setRodInsertion(long key, double x) {
        if (rodInsertion.containsKey(key)) rodInsertion.put(key, Math.max(0, Math.min(1, x)));
    }

    public boolean cladFailed() {
        return cladFailed;
    }

    public boolean graphiteFire() {
        return graphiteFire;
    }

    public boolean critical() {
        return criticalReported;
    }

    /** k_eff последней нодальной задачи (без стержней между SLOW); 0 — котёл без топлива. Игроку не показывается. */
    public double keff() {
        return fissile ? keff : 0;
    }

    /** Полная реактивность кинетики, Δk/k. */
    public double reactivity() {
        return fissile ? budget.total() : 0;
    }

    public double betaEff() {
        return kinetics == null ? 0 : kinetics.betaEff();
    }

    public double generationTime() {
        return generationTime;
    }

    /** Запаздывающие группы решётки последней нодальной задачи; {@code null} — котёл без топлива. */
    public DelayedGroups delayedGroups() {
        return fissile ? groups : null;
    }

    private double[] precursors() {
        if (kinetics == null) return savedPrecursors;
        double[] c = new double[kinetics.groupCount()];
        for (int i = 0; i < c.length; i++) c[i] = kinetics.precursor(i);
        return c;
    }

    private double meanInsertion(PileLayout.RodKind kind) {
        double s = 0;
        int c = 0;
        for (int i = 0; i < layout.rodCount(); i++) {
            PileLayout.Rod r = layout.rod(i);
            if (r.kind() != kind) continue;
            s += rodInsertion.getOrDefault(r.key(), 1.0);
            c++;
        }
        return c == 0 ? Double.NaN : s / c;
    }

    private int rodCount(PileLayout.RodKind kind) {
        int c = 0;
        for (int i = 0; i < layout.rodCount(); i++) if (layout.rod(i).kind() == kind) c++;
        return c;
    }

    // ------------------------------------------------------------------ сохранение

    public void save(StateWriter out) {
        out.putDouble("power", kinetics != null ? kinetics.power() : savedPower);
        poisons.save(out);
        decayHeat.save(out);
        double[] c = precursors();
        out.putDoubles("precursors", c, c.length);
        out.putDouble("fuel_k", fuelTempK);
        out.putDouble("graphite_k", graphiteTempK);
        out.putString("trip", trip.name());
        out.putBoolean("winding", winding);
        out.putInt("drive", controlDrive);
        out.putBoolean("critical", criticalReported);
        int[] loads = approachLoads.stream().mapToInt(Integer::intValue).toArray();
        out.putInts("approach_loads", loads, loads.length);
        out.putBoolean("clad_failed", cladFailed);
        out.putBoolean("graphite_fire", graphiteFire);
        long[] keys = new long[rodInsertion.size()];
        double[] in = new double[keys.length];
        int i = 0;
        for (Map.Entry<Long, Double> e : rodInsertion.entrySet()) {
            keys[i] = e.getKey();
            in[i++] = e.getValue();
        }
        out.putLongs("rod_keys", keys, keys.length);
        out.putDoubles("rod_in", in, in.length);
    }

    public void load(StateReader in) {
        savedPower = Math.max(PointKinetics.MIN_POWER, in.getDouble("power"));
        poisons.load(in);
        decayHeat.load(in);
        double[] c = in.has("precursors") ? in.getDoubles("precursors") : new double[0];
        fuelTempK = in.getDouble("fuel_k");
        graphiteTempK = in.getDouble("graphite_k");
        try {
            trip = PileTrip.valueOf(in.getString("trip"));
        } catch (IllegalArgumentException e) {
            trip = PileTrip.MANUAL;
        }
        winding = in.getBoolean("winding");
        controlDrive = Integer.signum(in.getInt("drive"));
        criticalReported = in.getBoolean("critical");
        approachLoads.clear();
        if (in.has("approach_loads")) for (int l : in.getInts("approach_loads")) approachLoads.add(l);
        cladFailed = in.getBoolean("clad_failed");
        graphiteFire = in.getBoolean("graphite_fire");
        long[] keys = in.getLongs("rod_keys");
        double[] ins = in.getDoubles("rod_in");
        // кладку пришлёт биндинг; до неё котёл пуст, стержни ждут своих ключей
        clearLayout();
        savedPrecursors = c;
        for (int i = 0; i < Math.min(keys.length, ins.length); i++) rodInsertion.put(keys[i], Math.max(0, Math.min(1, ins[i])));
    }

    private void clearLayout() {
        layout = PileLayout.EMPTY;
        rodInsertion.clear();
        nodeConst = new SixFactor[0];
        nodeState = new CellFeedbackState[0];
        nodeCell = new CellComposition[0];
        absorberNow = absorberRef = fuelTemps = modTemps = new double[0];
        cps = new double[0];
        trueCps = new double[0];
        fissile = false;
        solver = null;
        kinetics = null;
        structural = true;
        power = 0;
        sourceNorm = 0;
        criticalTimer = 0;
        budget.set(ReactivityComponent.STATIC, 0);
        budget.resetFastComponents();
    }

    /** Безопасное состояние: защита сработала, стержни внизу. */
    public void resetToSafeState() {
        trip = PileTrip.MANUAL;
        winding = false;
        controlDrive = 0;
        rodInsertion.replaceAll((k, v) -> 1.0);
    }
}
