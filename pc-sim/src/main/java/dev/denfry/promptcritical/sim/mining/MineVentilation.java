package dev.denfry.promptcritical.sim.mining;

import dev.denfry.promptcritical.sim.combustion.Chimney;
import java.util.Arrays;

/**
 * Вентиляционная сеть шахты (08 §8.2.7): депрессия ветви h = RQ|Q|, вентиляторы, двери и регуляторы,
 * естественная тяга; стационарные расходы — Харди Кросс по контурам {@link MineTopology} (поправки контуров
 * совместно) с тёплым стартом (SLOW, ≤ 10 итераций на шаг).
 *
 * <p>Знаки: расход ветви положителен от {@code from} к {@code to}; вентилятор гонит воздух в положительном
 * направлении своей ветви. По контуру Σ s_j(R_jQ_j|Q_j| − h_f,j − h_e,j) = 0, где естественная тяга ветви
 * h_e = ρ̄g(z_from − z_to) — в замкнутом контуре её сумма и есть gH(ρ_вх − ρ_исх) 08 §8.2.7.
 */
public final class MineVentilation {
    /** Наименьшее окно регулятора, м². */
    public static final double REGULATOR_MIN_AREA = 0.01;
    /** Давление в выработках для плотности воздуха, Па. */
    public static final double AIR_PRESSURE = 101_325;
    /** Сходимость: наибольшая поправка контура, м³/с. */
    public static final double TOLERANCE = 1e-5;
    /** До стольких контуров система поправок решается плотной матрицей L×L, дальше — сопряжёнными градиентами. */
    public static final int DENSE_LOOP_LIMIT = 64;
    private static final int CG_MAX_ITER = 500;
    private static final double CG_TOLERANCE = 1e-10;

    private MineTopology t;
    private double[] q = new double[0], rEff = new double[0], source = new double[0], natural = new double[0];
    private double[] rho = new double[0], jac = new double[0], res = new double[0], dx = new double[0];
    private double[] deriv = new double[0], branchTmp = new double[0];
    private double[] diag = new double[0], cgR = new double[0], cgZ = new double[0], cgP = new double[0],
            cgAp = new double[0];
    private boolean[] doorClosed = new boolean[0];
    private double[] regulatorArea = new double[0], fanRpm = new double[0];
    private boolean dirty = true;
    private int iterations;
    private double lastCorrection;

    /**
     * Перестроить под новый граф. Расходы, состояние дверей, окна регуляторов и частоты вентиляторов
     * переносятся по ключам ветвей; новые ветви начинают с нуля, новые двери закрыты, регуляторы открыты на
     * полное сечение, вентиляторы стоят.
     */
    public void rebuild(MineTopology topology) {
        MineTopology old = t;
        int nb = topology.branchCount();
        double[] nq = new double[nb];
        boolean[] nDoor = new boolean[topology.doorCount()];
        double[] nReg = new double[topology.doorCount()], nRpm = new double[topology.fanCount()];
        Arrays.fill(nDoor, true);
        for (int d = 0; d < nReg.length; d++) nReg[d] = topology.branchSectionM2(topology.doorBranch(d));
        if (old != null) {
            for (int j = 0; j < nb; j++) {
                int o = old.branchIndex(topology.branchKey(j));
                if (o < 0) continue;
                nq[j] = q[o];
                int od = old.doorOf[o], nd = topology.doorOf[j];
                if (od >= 0 && nd >= 0) {
                    nDoor[nd] = doorClosed[od];
                    nReg[nd] = regulatorArea[od];
                }
                int of = old.fanOf[o], nf = topology.fanOf[j];
                if (of >= 0 && nf >= 0) nRpm[nf] = fanRpm[of];
            }
        }
        t = topology;
        q = nq;
        doorClosed = nDoor;
        regulatorArea = nReg;
        fanRpm = nRpm;
        rEff = new double[nb];
        source = new double[nb];
        natural = new double[nb];
        rho = new double[nb];
        int nl = topology.loopCount();
        boolean dense = nl <= DENSE_LOOP_LIMIT;
        jac = dense ? new double[nl * nl] : new double[0];
        res = new double[nl];
        dx = new double[nl];
        deriv = new double[nb];
        branchTmp = new double[nb];
        int cg = dense ? 0 : nl;
        diag = new double[cg];
        cgR = new double[cg];
        cgZ = new double[cg];
        cgP = new double[cg];
        cgAp = new double[cg];
        for (int j = 0; j < nb; j++) {
            int a = t.from[j], b = t.to[j];
            double tMean = 0.5 * (t.tempK[a] + t.tempK[b]);
            rho[j] = AIR_PRESSURE * Chimney.AIR_MOLAR_MASS / (Chimney.GAS_CONSTANT * tMean);
            natural[j] = rho[j] * Chimney.GRAVITY * (t.elevation[a] - t.elevation[b]);
        }
        dirty = true;
    }

    public MineTopology topology() {
        return t;
    }

    /**
     * Шаг SLOW: до {@code maxIter} итераций от текущих расходов. Сеть стационарна (инерция воздуха не ведётся),
     * {@code dtPhys} зарезервирован под неё.
     *
     * <p>Харди Кросс с совместными поправками контуров (метод Ньютона по контурам): J·ΔQ = −F,
     * J = C·diag(2R|Q|)·Cᵀ, F_k = Σ s_j(R_jQ_j|Q_j| − h_f,j − h_e,j). Диагональ J — знаменатель классического
     * Харди Кросса, внедиагональные члены — общие ветви контуров; J симметрична и положительно определена:
     * до {@link #DENSE_LOOP_LIMIT} контуров — Холецкий, дальше — сопряжённые градиенты без матрицы с
     * предобуславливателем по диагонали. В лестничной сети, где все контуры идут через стволы, поправки по
     * очереди мешают друг другу и сходятся за сотни итераций; совместные — из покоя за ~10, с тёплым стартом за 1.
     */
    public void solveSlow(double dtPhys, int maxIter) {
        if (dirty) updateBranches();
        int nl = t.loopCount(), nb = q.length;
        boolean dense = nl <= DENSE_LOOP_LIMIT;
        int it = 0;
        double maxDq = 0;
        for (; it < maxIter && nl > 0; it++) {
            double maxF = 0, maxR = 0;
            for (int k = 0; k < nl; k++) {
                double f = 0, sumR = 0;
                for (int i = t.loopStart[k]; i < t.loopStart[k + 1]; i++) {
                    int j = t.loopBranch[i];
                    double qj = q[j];
                    f += t.loopSign[i] * (rEff[j] * qj * Math.abs(qj) - source[j] - natural[j]);
                    sumR += rEff[j];
                }
                res[k] = -f;
                maxF = Math.max(maxF, Math.abs(f));
                maxR = Math.max(maxR, sumR);
            }
            // у нулевых расходов производная 2R|Q| вырождается: нижняя граница — половина расхода, который
            // дал бы наибольший небаланс на наибольшем сопротивлении контура (из покоя один контур — точный шаг)
            double floor = Math.max(1e-6, 0.5 * Math.sqrt(maxF / maxR));
            for (int j = 0; j < nb; j++) deriv[j] = 2 * rEff[j] * Math.max(Math.abs(q[j]), floor);
            if (dense) denseSolve(nl);
            else conjugateGradient(nl);
            maxDq = 0;
            for (int k = 0; k < nl; k++) maxDq = Math.max(maxDq, Math.abs(dx[k]));
            for (int j = 0; j < nb; j++) {
                double dq = 0;
                for (int a = t.branchLoopStart[j]; a < t.branchLoopStart[j + 1]; a++)
                    dq += t.branchLoopSign[a] * dx[t.branchLoop[a]];
                q[j] += dq;
            }
            if (maxDq < TOLERANCE) {
                it++;
                break;
            }
        }
        iterations = it;
        lastCorrection = maxDq;
    }

    /** Собрать J и решить Холецким: dx = J⁻¹·res. */
    private void denseSolve(int n) {
        double[] m = jac;
        Arrays.fill(m, 0, n * n, 0);
        for (int j = 0; j < q.length; j++) {
            double d = deriv[j];
            int s0 = t.branchLoopStart[j], s1 = t.branchLoopStart[j + 1];
            for (int a = s0; a < s1; a++) {
                int row = t.branchLoop[a] * n, sa = t.branchLoopSign[a];
                for (int b = s0; b < s1; b++) m[row + t.branchLoop[b]] += sa * t.branchLoopSign[b] * d;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int k = 0; k < i; k++) {
                double s = m[i * n + k];
                for (int c = 0; c < k; c++) s -= m[i * n + c] * m[k * n + c];
                m[i * n + k] = s / m[k * n + k];
            }
            double d = m[i * n + i];
            for (int c = 0; c < i; c++) d -= m[i * n + c] * m[i * n + c];
            m[i * n + i] = Math.sqrt(Math.max(d, 1e-300));
        }
        for (int i = 0; i < n; i++) {
            double s = res[i];
            for (int c = 0; c < i; c++) s -= m[i * n + c] * dx[c];
            dx[i] = s / m[i * n + i];
        }
        for (int i = n - 1; i >= 0; i--) {
            double s = dx[i];
            for (int c = i + 1; c < n; c++) s -= m[c * n + i] * dx[c];
            dx[i] = s / m[i * n + i];
        }
    }

    /** Сопряжённые градиенты с диагональным предобуславливателем: J·x через ветви, без матрицы. */
    private void conjugateGradient(int n) {
        Arrays.fill(diag, 0);
        for (int k = 0; k < n; k++)
            for (int i = t.loopStart[k]; i < t.loopStart[k + 1]; i++) diag[k] += deriv[t.loopBranch[i]];
        Arrays.fill(dx, 0);
        double rz = 0, r0 = 0;
        for (int k = 0; k < n; k++) {
            cgR[k] = res[k];
            cgZ[k] = cgR[k] / diag[k];
            cgP[k] = cgZ[k];
            rz += cgR[k] * cgZ[k];
            r0 = Math.max(r0, Math.abs(cgR[k]));
        }
        if (r0 == 0) return;
        for (int iter = 0; iter < Math.min(n, CG_MAX_ITER); iter++) {
            applyJacobian(cgP, cgAp, n);
            double pAp = 0;
            for (int k = 0; k < n; k++) pAp += cgP[k] * cgAp[k];
            double alpha = rz / pAp, rmax = 0;
            for (int k = 0; k < n; k++) {
                dx[k] += alpha * cgP[k];
                cgR[k] -= alpha * cgAp[k];
                rmax = Math.max(rmax, Math.abs(cgR[k]));
            }
            if (rmax < CG_TOLERANCE * r0) return;
            double rzNew = 0;
            for (int k = 0; k < n; k++) {
                cgZ[k] = cgR[k] / diag[k];
                rzNew += cgR[k] * cgZ[k];
            }
            double beta = rzNew / rz;
            rz = rzNew;
            for (int k = 0; k < n; k++) cgP[k] = cgZ[k] + beta * cgP[k];
        }
    }

    /** y = C·diag(d)·Cᵀ·x. */
    private void applyJacobian(double[] x, double[] y, int n) {
        for (int j = 0; j < q.length; j++) {
            double v = 0;
            for (int a = t.branchLoopStart[j]; a < t.branchLoopStart[j + 1]; a++)
                v += t.branchLoopSign[a] * x[t.branchLoop[a]];
            branchTmp[j] = deriv[j] * v;
        }
        for (int k = 0; k < n; k++) {
            double v = 0;
            for (int i = t.loopStart[k]; i < t.loopStart[k + 1]; i++) v += t.loopSign[i] * branchTmp[t.loopBranch[i]];
            y[k] = v;
        }
    }

    private void updateBranches() {
        for (int j = 0; j < rEff.length; j++) {
            double r = t.resistance[j];
            int d = t.doorOf[j];
            if (d >= 0 && doorClosed[d]) {
                r += t.doorKind[d] == DoorKind.DOOR ? MineTopology.DOOR_RESISTANCE
                        : regulatorResistance(rho[j], t.branchSection[j], regulatorArea[d]);
            }
            int f = t.fanOf[j];
            double h = 0;
            if (f >= 0) {
                r += t.fanSpec[f].internalResistance();
                h = t.fanSpec[f].sourcePressurePa(fanRpm[f]);
            }
            rEff[j] = r;
            source[j] = h;
        }
        dirty = false;
    }

    /**
     * Сопротивление окна регулятора площадью A в выработке сечением S (внезапное сужение с сжатием струи):
     * R = ρ/(2S²)·(S/(c·A) − 1)², коэффициент сжатия по Вейсбаху c = 0,63 + 0,37·(A/S)³ — у полного окна
     * c = 1 и R = 0, Н·с²/м⁸.
     */
    public static double regulatorResistance(double rho, double sectionM2, double windowM2) {
        double w = Math.max(REGULATOR_MIN_AREA, windowM2);
        if (w >= sectionM2) return 0;
        double ratio = w / sectionM2;
        double k = sectionM2 / ((0.63 + 0.37 * ratio * ratio * ratio) * w) - 1;
        return rho / (2 * sectionM2 * sectionM2) * k * k;
    }

    /**
     * Требуемый расход по газу Q_тр = 100·I·k_н/(C_доп − C₀) (08 §8.2.7) в долях вместо процентов, м³/с.
     *
     * @param ch4M3s     метановыделение на участок, м³/с
     * @param kN         коэффициент неравномерности 1,3–1,5
     * @param allowFrac  C_доп (исходящая лавы — 0,01)
     * @param intakeFrac C₀ в поступающей струе
     */
    public static double requiredFlowM3s(double ch4M3s, double kN, double allowFrac, double intakeFrac) {
        return ch4M3s * kN / (allowFrac - intakeFrac);
    }

    /** Частота вентилятора, об/мин; знак минус — реверс. */
    public void setFanSpeed(int fan, double rpm) {
        if (fanRpm[fan] != rpm) {
            fanRpm[fan] = rpm;
            dirty = true;
        }
    }

    public double fanSpeed(int fan) {
        return fanRpm[fan];
    }

    /** Закрыть/открыть дверь или регулятор; открытый регулятор не создаёт сопротивления. */
    public void setDoorClosed(int door, boolean closed) {
        if (doorClosed[door] != closed) {
            doorClosed[door] = closed;
            dirty = true;
        }
    }

    public boolean doorClosed(int door) {
        return doorClosed[door];
    }

    /** Окно регулятора, м². */
    public void setRegulatorArea(int door, double areaM2) {
        if (regulatorArea[door] != areaM2) {
            regulatorArea[door] = areaM2;
            dirty = true;
        }
    }

    /** Расход ветви, м³/с. */
    public double flow(int branch) {
        return q[branch];
    }

    /** Депрессия ветви — потеря давления в выработке, двери, регуляторе (без вентилятора), Па. */
    public double depression(int branch) {
        if (dirty) updateBranches();
        int f = t.fanOf[branch];
        double r = rEff[branch] - (f >= 0 ? t.fanSpec[f].internalResistance() : 0);
        return r * q[branch] * Math.abs(q[branch]);
    }

    /** Средняя скорость воздуха в ветви, м/с. */
    public double velocity(int branch) {
        return Math.abs(q[branch]) / t.branchSection[branch];
    }

    /** Скорость в ветви вне пределов ПБ (0,25 м/с везде; верхний — по назначению ветви). */
    public boolean velocityViolation(int branch, MiningConfig cfg) {
        double v = velocity(branch);
        return v < cfg.airVelocityMinMs() || v > cfg.maxVelocity(t.kind[branch]);
    }

    /** Естественная тяга ветви ρ̄g(z_from − z_to), Па. */
    public double naturalDraft(int branch) {
        return natural[branch];
    }

    /** Давление вентилятора h_f(Q, n), Па. */
    public double fanPressurePa(int fan) {
        int j = t.fanBranch[fan];
        FanSpec s = t.fanSpec[fan];
        return s.sourcePressurePa(fanRpm[fan]) - s.internalResistance() * q[j] * Math.abs(q[j]);
    }

    /** Подача вентилятора, м³/с. */
    public double fanFlowM3s(int fan) {
        return q[t.fanBranch[fan]];
    }

    /** Мощность на валу N = h·Q/η_f, Вт (для стресса Create: 1 кВт = 1 SU). */
    public double fanPowerW(int fan) {
        double hq = fanPressurePa(fan) * fanFlowM3s(fan);
        return Math.max(0, hq) / t.fanSpec[fan].efficiency();
    }

    /** Итераций на последнем шаге. */
    public int iterations() {
        return iterations;
    }

    /** Наибольшая поправка контура на последней итерации, м³/с. */
    public double lastCorrection() {
        return lastCorrection;
    }
}
