package dev.denfry.promptcritical.sim.automation;

import dev.denfry.promptcritical.sim.core.Quality;
import java.util.Arrays;
import java.util.SplittableRandom;

/**
 * Аналоговые вычислители A2 станции (03d §3d.4): решающие усилители ±10 В с неидеальностями,
 * интеграторы (дрейф, утечка, насыщение, подстройка), дифференциатор с фильтром, множитель, функция,
 * компаратор, станция РУЧ/АВТ и преобразователи 4–20 мА ↔ ±10 В.
 *
 * <p>Схема — направленный граф блоков на сигналах ({@code int}). Выходы интеграторов — переменные
 * состояния и разрывают циклы; остальные блоки считаются в топологическом порядке раз за тик, затем
 * шагают интеграторы (§3d.4.10). Цикл без интегратора — {@code ALGEBRAIC_LOOP}: выходы блоков контура
 * фиксируются в насыщении. Сигнал {@code -1} — вход не подключён (0 В).
 */
public final class AnalogBank {
    /** Машинная единица MU, В. */
    public static final double MU = 10;
    /** Насыщение выхода усилителя, В. */
    public static final double U_SAT = 10.5;
    /** Постоянная шума AR(1) τ_n, с (03d §3d.4.2). */
    public static final double NOISE_TAU_S = 0.5;
    /** Порог делителя: при |x₂| ниже — насыщение (03d §3d.4.5). */
    public static final double DIV_MIN_V = 0.1;
    /** Погрешность функционального преобразователя: ±0,5 % шкалы. */
    public static final double FUNCTION_ERROR = 0.005;
    /** Скорость ручки ручного выхода станции: 10 %/с = 1 В/с. */
    public static final double MANUAL_RATE_V_PER_S = 1;
    public static final int STATION_SP = 0, STATION_OUT_MA = 1, STATION_TRACK = 2, STATION_MODE = 3;

    // Слоты входов интегратора сверх трёх суммируемых
    private static final int INT_IC = 3, INT_IC_MODE = 4, INT_HOLD_MODE = 5;
    private static final AnalogKind[] KINDS = AnalogKind.values();
    private static final Quality[] QUALITIES = Quality.values();
    private static final MultiplierMode[] MULT_MODES = MultiplierMode.values();
    private static final StationMode[] STATION_MODES = StationMode.values();
    private static final IntegratorMode[] INT_MODES = IntegratorMode.values();

    private final AutomationConfig cfg;
    private final double dt;
    private final double noisePhi;
    private final SplittableRandom seeds;
    private boolean compiled;

    // Сигналы
    private int signals;
    private double[] sig = new double[32];

    // Блоки
    private int size;
    private final int cap;
    private final byte[] kind;
    private final AnalogTech[] tech;
    private final int[][] in;
    private final double[][] k;
    private final int[][] out;
    private final double[] p0, p1, p2, p3;
    private final double[][] fx, fy;
    private final double[] y, xPrev, eps, noise, gainErr, offset, staticErr, sigma, sinceCalS, tempK;
    private final boolean[] failed, algebraicLoop, ideal;
    private final byte[] quality, mode;
    private final SplittableRandom[] rng;
    private int[] order = new int[0];
    private int[] integrators = new int[0];

    public AnalogBank(AutomationConfig cfg, double dtS, long seed) {
        if (!(dtS > 0)) throw new IllegalArgumentException("dtS");
        this.cfg = cfg;
        this.dt = dtS;
        this.noisePhi = Math.exp(-dtS / NOISE_TAU_S);
        this.seeds = new SplittableRandom(seed);
        cap = cfg.maxAnalogBlocksPerStation();
        kind = new byte[cap];
        tech = new AnalogTech[cap];
        in = new int[cap][];
        k = new double[cap][];
        out = new int[cap][];
        p0 = new double[cap];
        p1 = new double[cap];
        p2 = new double[cap];
        p3 = new double[cap];
        fx = new double[cap][];
        fy = new double[cap][];
        y = new double[cap];
        xPrev = new double[cap];
        eps = new double[cap];
        noise = new double[cap];
        gainErr = new double[cap];
        offset = new double[cap];
        staticErr = new double[cap];
        sigma = new double[cap];
        sinceCalS = new double[cap];
        tempK = new double[cap];
        failed = new boolean[cap];
        algebraicLoop = new boolean[cap];
        ideal = new boolean[cap];
        quality = new byte[cap];
        mode = new byte[cap];
        rng = new SplittableRandom[cap];
    }

    // ---------------------------------------------------------------- сигналы

    /** Внешний сигнал: датчик через преобразователь, положение клапана, дискрет A1. */
    public int newSignal() {
        if (signals == sig.length) sig = Arrays.copyOf(sig, signals * 2);
        return signals++;
    }

    public void setSignal(int s, double v) {
        checkSignal(s);
        sig[s] = v;
    }

    public double signal(int s) {
        checkSignal(s);
        return sig[s];
    }

    // ---------------------------------------------------------------- блоки

    /** Сумматор y = −Σ k_i x_i, до 4 входов, k ∈ [−10, 10] с шагом 0,01. */
    public int addSummer(AnalogTech t, int[] inputs, double[] coefs) {
        int b = newBlock(AnalogKind.SUMMER, t, 4, 1);
        wire(b, inputs, coefs, 4);
        return b;
    }

    /**
     * Интегратор ẏ = −(1/T_i)(Σ k_i x_i + ε), до 3 входов (03d §3d.4.3). Вход IC и дискреты режима —
     * {@link #setIntegratorIc}; ε при установке — U(±e_os,0), как после подстройки.
     */
    public int addIntegrator(AnalogTech t, int[] inputs, double[] coefs, double tiS) {
        if (!(tiS >= 0.1 && tiS <= 1e4)) throw new IllegalArgumentException("tiS");
        int b = newBlock(AnalogKind.INTEGRATOR, t, 6, 1);
        wire(b, inputs, coefs, 3);
        p0[b] = sig3(tiS);
        p1[b] = t == AnalogTech.TUBE ? cfg.leakTimeTubeS() : cfg.leakTimeTransistorS();
        p2[b] = -U_SAT;
        p3[b] = U_SAT;
        mode[b] = (byte) IntegratorMode.OPERATE.ordinal();
        eps[b] = (2 * rng[b].nextDouble() - 1) * t.offsetV;
        return b;
    }

    /** Дифференциатор с фильтром T_d s/(1 + T_d s/N), обратная разность (03d §3d.4.4); N ∈ [2, 20]. */
    public int addDifferentiator(AnalogTech t, int input, double tdS, double n) {
        if (!(tdS > 0) || !(n >= 2 && n <= 20)) throw new IllegalArgumentException("Td/N");
        int b = newBlock(AnalogKind.DIFFERENTIATOR, t, 1, 1);
        wire(b, new int[] {input}, new double[] {1}, 1);
        p0[b] = sig3(tdS);
        p1[b] = n;
        xPrev[b] = Double.NaN;
        return b;
    }

    public int addMultiplier(AnalogTech t, MultiplierMode m, int x1, int x2) {
        int b = newBlock(AnalogKind.MULTIPLIER, t, 2, 1);
        wire(b, new int[] {x1, x2}, new double[] {1, 1}, 2);
        p0[b] = m.ordinal();
        p1[b] = 0.01 * MU;
        staticErr[b] = (2 * rng[b].nextDouble() - 1) * t.multiplierError * MU;
        return b;
    }

    /** Отсечка малого сигнала корня x_cut, В (по умолчанию 1 % шкалы). */
    public void setSqrtCutoff(int b, double v) {
        checkKind(b, AnalogKind.MULTIPLIER);
        if (!(v >= 0)) throw new IllegalArgumentException("cutoff");
        p1[b] = v;
    }

    /** Диодный функциональный преобразователь: 11 точек, x строго возрастают (03d §3d.4.6). */
    public int addFunction(AnalogTech t, int input, double[] xs, double[] ys) {
        if (xs.length != 11 || ys.length != 11) throw new IllegalArgumentException("11 points");
        for (int i = 1; i < xs.length; i++)
            if (!(xs[i] > xs[i - 1])) throw new IllegalArgumentException("x must increase");
        int b = newBlock(AnalogKind.FUNCTION, t, 1, 1);
        wire(b, new int[] {input}, new double[] {1}, 1);
        fx[b] = xs.clone();
        fy[b] = ys.clone();
        staticErr[b] = (2 * rng[b].nextDouble() - 1) * FUNCTION_ERROR * MU;
        return b;
    }

    /** Компаратор s = [x₁ &gt; x₂], возврат при x₁ ≤ x₂ − Δ; выход — дискрет 0/1 (мост A2 → A1). */
    public int addComparator(AnalogTech t, int x1, int x2, double hysteresisV) {
        int b = newBlock(AnalogKind.COMPARATOR, t, 2, 1);
        wire(b, new int[] {x1, x2}, new double[] {1, 1}, 2);
        p0[b] = Double.isNaN(hysteresisV) ? 0.01 * MU : hysteresisV;
        if (!(p0[b] >= 0)) throw new IllegalArgumentException("hysteresis");
        return b;
    }

    /**
     * Станция РУЧ/АВТ (03d §3d.4.7): входы — сигнал регулятора (АВТ) и дискрет «принуд. РУЧ»; выходы —
     * {@link #STATION_SP} (В), {@link #STATION_OUT_MA}, {@link #STATION_TRACK} (В), {@link #STATION_MODE}
     * (1 — РУЧ). Безударность не обеспечивает: при РУЧ → АВТ выход сразу равен сигналу регулятора.
     */
    public int addManualStation(int autoInput, int forceManualInput) {
        int b = newBlock(AnalogKind.MANUAL_STATION, null, 2, 4);
        wire(b, new int[] {autoInput, forceManualInput}, new double[] {1, 1}, 2);
        mode[b] = (byte) StationMode.MANUAL.ordinal();
        p2[b] = StationMode.MANUAL.ordinal();
        return b;
    }

    /** Преобразователь: I/V U = 10(I − 4)/16 (I &lt; 3,6 мА → −1 В, BAD) или V/I I = 4 + 1,6U в 3,8–20,5 мА. */
    public int addConverter(AnalogTech t, boolean currentToVoltage, int input) {
        int b = newBlock(AnalogKind.SIGNAL_CONVERTER, t, 1, 1);
        wire(b, new int[] {input}, new double[] {1}, 1);
        p0[b] = currentToVoltage ? 1 : 0;
        return b;
    }

    // ---------------------------------------------------------------- настройка

    public void setInput(int b, int slot, int signal) {
        checkBlock(b);
        if (slot < 0 || slot >= in[b].length) throw new IllegalArgumentException("slot");
        if (signal != -1) checkSignal(signal);
        in[b][slot] = signal;
        compiled = false;
    }

    public void setCoefficient(int b, int slot, double coef) {
        checkBlock(b);
        if (slot < 0 || slot >= k[b].length) throw new IllegalArgumentException("slot");
        k[b][slot] = knob(coef);
    }

    /** Вход IC и дискреты режима (&gt; 0,5 — активен): IC приоритетнее HOLD; без них — {@link #setIntegratorMode}. */
    public void setIntegratorIc(int b, int icSignal, int icModeSignal, int holdModeSignal) {
        checkKind(b, AnalogKind.INTEGRATOR);
        setInput(b, INT_IC, icSignal);
        setInput(b, INT_IC_MODE, icModeSignal);
        setInput(b, INT_HOLD_MODE, holdModeSignal);
    }

    public void setIntegratorMode(int b, IntegratorMode m) {
        checkKind(b, AnalogKind.INTEGRATOR);
        mode[b] = (byte) m.ordinal();
    }

    /** Диодный ограничитель выхода (антивиндап, «предел»), В. */
    public void setIntegratorLimits(int b, double lo, double hi) {
        checkKind(b, AnalogKind.INTEGRATOR);
        if (!(lo < hi)) throw new IllegalArgumentException("limits");
        p2[b] = Math.max(-U_SAT, lo);
        p3[b] = Math.min(U_SAT, hi);
    }

    /** Утечка конденсатора T_leak; {@code false} — идеальный конденсатор (стенд). */
    public void setLeak(int b, boolean enabled) {
        checkKind(b, AnalogKind.INTEGRATOR);
        p1[b] = !enabled ? Double.POSITIVE_INFINITY
                : tech[b] == AnalogTech.TUBE ? cfg.leakTimeTubeS() : cfg.leakTimeTransistorS();
    }

    /** Приведённый ко входу дрейф ε, В (диагностика, сценарии). */
    public void setDrift(int b, double v) {
        checkKind(b, AnalogKind.INTEGRATOR);
        eps[b] = v;
    }

    public double drift(int b) {
        checkKind(b, AnalogKind.INTEGRATOR);
        return eps[b];
    }

    /**
     * Балансировка нуля {@code instrument_calibrator}: ε ← U(±e_os,0), только в HOLD или IC
     * (в OPERATE запрещена, 03d §3d.4.3).
     *
     * @return false — блок в OPERATE, подстройка не выполнена
     */
    public boolean calibrate(int b) {
        checkKind(b, AnalogKind.INTEGRATOR);
        if (effectiveMode(b) == IntegratorMode.OPERATE) return false;
        eps[b] = ideal[b] ? 0 : (2 * rng[b].nextDouble() - 1) * tech[b].offsetV;
        sinceCalS[b] = 0;
        return true;
    }

    /** Подстройка просрочена (флаг {@code CAL_DUE}). */
    public boolean calDue(int b) {
        checkKind(b, AnalogKind.INTEGRATOR);
        return sinceCalS[b] > adjustIntervalS(b);
    }

    public void setStationMode(int b, StationMode m) {
        checkKind(b, AnalogKind.MANUAL_STATION);
        p2[b] = m.ordinal();
    }

    /** Ручка задатчика SP, В (0…10). */
    public void setSetpoint(int b, double v) {
        checkKind(b, AnalogKind.MANUAL_STATION);
        p0[b] = clamp(v, 0, MU);
    }

    /** Ручной выход, В (0…10 ↔ 4…20 мА). */
    public void setManualOutput(int b, double v) {
        checkKind(b, AnalogKind.MANUAL_STATION);
        p1[b] = clamp(v, 0, MU);
    }

    /** Удержание ручки ручного выхода: −1, 0, +1 (10 %/с). */
    public void setManualRamp(int b, int direction) {
        checkKind(b, AnalogKind.MANUAL_STATION);
        p3[b] = Integer.signum(direction);
    }

    /** Убрать неидеальности блока (δ_g, e_os, шум, ε, погрешность): эталонный стенд и тесты. */
    public void setIdeal(int b) {
        checkBlock(b);
        ideal[b] = true;
        gainErr[b] = 0;
        offset[b] = 0;
        sigma[b] = 0;
        noise[b] = 0;
        staticErr[b] = 0;
        eps[b] = 0;
    }

    public void setBlockTemperature(int b, double kelvin) {
        checkBlock(b);
        tempK[b] = kelvin;
    }

    /** Замена лампы: блок снова работает. */
    public void replaceTube(int b) {
        checkBlock(b);
        failed[b] = false;
    }

    // ---------------------------------------------------------------- шаги

    /** FAST-шаг (этап 9a): публикация интеграторов, блоки в топологическом порядке, шаг интеграторов. */
    public void stepFast() {
        if (!compiled) compile();
        for (int b = 0; b < size; b++) {
            if (sigma[b] > 0) {
                noise[b] = noisePhi * noise[b] + sigma[b] * Math.sqrt(1 - noisePhi * noisePhi) * rng[b].nextGaussian();
            }
        }
        for (int b : integrators) {
            if (algebraicLoop[b]) continue;
            sig[out[b][0]] = failed[b] ? 0 : clamp(y[b] + noise[b], -U_SAT, U_SAT);
            quality[b] = (byte) (failed[b] ? Quality.BAD : calDue(b) ? Quality.UNCERTAIN : Quality.GOOD).ordinal();
        }
        for (int b : order) evaluate(b);
        for (int b : integrators) stepIntegrator(b);
    }

    /** SLOW-шаг: дрейф ε интеграторов (случайное блуждание), срок подстройки, отказы ламп. */
    public void stepSlow(double dtPhysS) {
        double tubeHazard = dtPhysS / (cfg.tubeLifeHours() * 3600);
        for (int b = 0; b < size; b++) {
            if (KINDS[kind[b]] == AnalogKind.INTEGRATOR) {
                sinceCalS[b] += dtPhysS;
                if (!ideal[b]) {
                    double rms = tech[b] == AnalogTech.TUBE ? cfg.driftRmsTubeV() : cfg.driftRmsTransistorV();
                    double s = rms / Math.sqrt(adjustIntervalS(b));
                    if (tech[b] == AnalogTech.TUBE) s *= cfg.arrheniusFactor(tempK[b]);
                    eps[b] += s * Math.sqrt(dtPhysS) * rng[b].nextGaussian();
                }
            }
            if (tech[b] == AnalogTech.TUBE && !failed[b] && rng[b].nextDouble() < -Math.expm1(-tubeHazard)) {
                failed[b] = true;
            }
        }
    }

    // ---------------------------------------------------------------- выходы

    public int out(int b) {
        return out(b, 0);
    }

    public int out(int b, int port) {
        checkBlock(b);
        if (port < 0 || port >= out[b].length) throw new IllegalArgumentException("port");
        return out[b][port];
    }

    public double output(int b) {
        return sig[out(b)];
    }

    public Quality quality(int b) {
        checkBlock(b);
        return QUALITIES[quality[b]];
    }

    /** Диагностика {@code ALGEBRAIC_LOOP}: лампа «КОНТУР» на панели. */
    public boolean algebraicLoop(int b) {
        checkBlock(b);
        if (!compiled) compile();
        return algebraicLoop[b];
    }

    public boolean tubeFailed(int b) {
        checkBlock(b);
        return failed[b];
    }

    public double integratorState(int b) {
        checkKind(b, AnalogKind.INTEGRATOR);
        return y[b];
    }

    public StationMode stationMode(int b) {
        checkKind(b, AnalogKind.MANUAL_STATION);
        return STATION_MODES[mode[b]];
    }

    /** Тепловыделение блоков станции, Вт (03d §3d.13.1). */
    public double heatW() {
        double p = 0;
        for (int b = 0; b < size; b++) if (tech[b] != null && !failed[b]) p += tech[b].heatW;
        return p;
    }

    public int size() {
        return size;
    }

    // ---------------------------------------------------------------- перенос при пересборке схемы

    /** Память блока для новой схемы: состояние и индивидуальные погрешности, разыгранные при установке. */
    double[] memo(int b) {
        checkBlock(b);
        return new double[] {y[b], xPrev[b], eps[b], noise[b], gainErr[b], offset[b], staticErr[b], sinceCalS[b],
                failed[b] ? 1 : 0, mode[b], p0[b], p1[b], p2[b], p3[b]};
    }

    /** Восстановить память; уставки (T_i, пределы, режим интегратора) остаются от новой схемы, у станции — из памяти. */
    void restore(int b, double[] m) {
        checkBlock(b);
        if (m == null || m.length < 14) return;
        y[b] = m[0];
        xPrev[b] = m[1];
        eps[b] = m[2];
        noise[b] = m[3];
        if (!ideal[b]) {
            gainErr[b] = m[4];
            offset[b] = m[5];
            staticErr[b] = m[6];
        }
        sinceCalS[b] = m[7];
        failed[b] = m[8] != 0;
        if (KINDS[kind[b]] == AnalogKind.MANUAL_STATION) {
            mode[b] = (byte) m[9];
            p1[b] = m[11];
        }
    }

    // ---------------------------------------------------------------- внутреннее

    private void evaluate(int b) {
        int[] i = in[b];
        double[] kk = k[b];
        Quality q = Quality.GOOD;
        double v;
        switch (KINDS[kind[b]]) {
            case SUMMER -> {
                double s = 0;
                for (int j = 0; j < 4; j++) s += kk[j] * x(i[j]);
                v = amp(b, -s);
            }
            case DIFFERENTIATOR -> {
                double xv = x(i[0]);
                double tf = p0[b] / p1[b];
                double a = tf / (tf + dt), g = p0[b] / (tf + dt);
                double prev = Double.isNaN(xPrev[b]) ? xv : xPrev[b];
                y[b] = clamp(a * y[b] + g * (xv - prev), -U_SAT, U_SAT);
                xPrev[b] = xv;
                v = amp(b, y[b]);
            }
            case MULTIPLIER -> {
                double x1 = x(i[0]), x2 = x(i[1]);
                switch (MULT_MODES[(int) p0[b]]) {
                    case MUL -> v = amp(b, x1 * x2 / MU + staticErr[b]);
                    case DIV -> {
                        if (Math.abs(x2) < DIV_MIN_V) {
                            v = (x1 >= 0) == (x2 >= 0) ? U_SAT : -U_SAT;
                            q = Quality.UNCERTAIN;
                        } else {
                            v = amp(b, MU * x1 / x2 + staticErr[b] * MU / Math.abs(x2));
                        }
                    }
                    case SQUARE -> v = amp(b, x1 * x1 / MU + staticErr[b]);
                    default -> {
                        if (x1 < p1[b] || x1 <= 0) {
                            v = amp(b, 0);
                        } else {
                            double r = Math.sqrt(MU * x1);
                            v = amp(b, r + staticErr[b] * MU / (2 * r));
                        }
                    }
                }
            }
            case FUNCTION -> v = amp(b, interpolate(fx[b], fy[b], x(i[0])) + staticErr[b]);
            case COMPARATOR -> {
                double d = x(i[0]) - x(i[1]) + offset[b] + noise[b];
                if (d > 0) y[b] = 1;
                else if (d <= -p0[b]) y[b] = 0;
                v = y[b];
            }
            case MANUAL_STATION -> {
                evaluateStation(b);
                return;
            }
            case SIGNAL_CONVERTER -> {
                double xv = x(i[0]);
                if (p0[b] == 1) {
                    if (xv < 3.6) {
                        v = -1;
                        q = Quality.BAD;
                    } else {
                        v = amp(b, MU * (xv - 4) / 16);
                    }
                } else {
                    double u = xv * (1 + gainErr[b]) + offset[b] + noise[b];
                    v = clamp(4 + 1.6 * u, 3.8, 20.5);
                }
            }
            default -> throw new IllegalStateException();
        }
        if (failed[b]) {
            v = 0;
            q = Quality.BAD;
        }
        sig[out[b][0]] = v;
        quality[b] = (byte) q.ordinal();
    }

    private void evaluateStation(int b) {
        int[] i = in[b];
        boolean forced = i[1] >= 0 && sig[i[1]] > 0.5;
        StationMode m = forced ? StationMode.MANUAL : STATION_MODES[(int) p2[b]];
        StationMode was = STATION_MODES[mode[b]];
        double auto = clamp(x(i[0]), -U_SAT, U_SAT);
        if (m == StationMode.MANUAL && was == StationMode.AUTO) p1[b] = clamp(auto, 0, MU); // ручной подхватывает выход
        if (m == StationMode.MANUAL) p1[b] = clamp(p1[b] + p3[b] * MANUAL_RATE_V_PER_S * dt, 0, MU);
        double u = m == StationMode.MANUAL ? p1[b] : auto;
        mode[b] = (byte) m.ordinal();
        int[] o = out[b];
        sig[o[STATION_SP]] = p0[b];
        sig[o[STATION_OUT_MA]] = clamp(4 + 1.6 * u, 3.8, 20.5);
        sig[o[STATION_TRACK]] = u;
        sig[o[STATION_MODE]] = m == StationMode.MANUAL ? 1 : 0;
        quality[b] = (byte) Quality.GOOD.ordinal();
    }

    private void stepIntegrator(int b) {
        if (algebraicLoop[b] || failed[b]) return;
        int[] i = in[b];
        double lo = p2[b], hi = p3[b];
        double leak = dt / p1[b];
        switch (effectiveMode(b)) {
            case IC -> y[b] = clamp(x(i[INT_IC]), lo, hi);
            case HOLD -> y[b] -= leak * y[b];
            case OPERATE -> {
                double s = 0;
                for (int j = 0; j < 3; j++) s += k[b][j] * x(i[j]);
                double next = y[b] - dt / p0[b] * (s * (1 + gainErr[b]) + eps[b]) - leak * y[b];
                y[b] = clamp(next, lo, hi);
            }
        }
    }

    private IntegratorMode effectiveMode(int b) {
        int[] i = in[b];
        if (i[INT_IC_MODE] >= 0 && sig[i[INT_IC_MODE]] > 0.5) return IntegratorMode.IC;
        if (i[INT_HOLD_MODE] >= 0 && sig[i[INT_HOLD_MODE]] > 0.5) return IntegratorMode.HOLD;
        return INT_MODES[mode[b]];
    }

    private double adjustIntervalS(int b) {
        return 86400 * (tech[b] == AnalogTech.TUBE ? cfg.adjustIntervalDaysTube() : cfg.adjustIntervalDaysTransistor());
    }

    /** y = clamp(G(x)(1 + δ_g) + e_os + n_a, ±U_sat). */
    private double amp(int b, double g) {
        return clamp(g * (1 + gainErr[b]) + offset[b] + noise[b], -U_SAT, U_SAT);
    }

    private double x(int s) {
        return s < 0 ? 0 : sig[s];
    }

    private static double interpolate(double[] xs, double[] ys, double xv) {
        int n = xs.length;
        int j = 0;
        if (xv >= xs[n - 1]) j = n - 2;
        else while (j < n - 2 && xv > xs[j + 1]) j++;
        double t = (xv - xs[j]) / (xs[j + 1] - xs[j]);
        return ys[j] + t * (ys[j + 1] - ys[j]);
    }

    /**
     * Компиляция графа (03d §3d.4.10): ребро b → c, если c читает выход b и b — не интегратор.
     * Контуры (компоненты сильной связности размера &gt; 1 или петли) — ALGEBRAIC_LOOP.
     */
    private void compile() {
        int[] producer = new int[signals];
        Arrays.fill(producer, -1);
        for (int b = 0; b < size; b++) for (int s : out[b]) producer[s] = b;
        int[][] adj = new int[size][];
        int[] deg = new int[size];
        for (int c = 0; c < size; c++) {
            for (int s : in[c]) {
                int b = s < 0 ? -1 : producer[s];
                if (b >= 0 && KINDS[kind[b]] != AnalogKind.INTEGRATOR) deg[b]++;
            }
        }
        for (int b = 0; b < size; b++) adj[b] = new int[deg[b]];
        Arrays.fill(deg, 0);
        for (int c = 0; c < size; c++) {
            for (int s : in[c]) {
                int b = s < 0 ? -1 : producer[s];
                if (b >= 0 && KINDS[kind[b]] != AnalogKind.INTEGRATOR) adj[b][deg[b]++] = c;
            }
        }
        Arrays.fill(algebraicLoop, 0, size, false);
        markLoops(adj);
        int nInt = 0;
        for (int b = 0; b < size; b++) if (KINDS[kind[b]] == AnalogKind.INTEGRATOR) nInt++;
        integrators = new int[nInt];
        int[] indeg = new int[size];
        for (int b = 0; b < size; b++) {
            if (algebraicLoop[b]) continue;
            for (int c : adj[b]) if (!algebraicLoop[c]) indeg[c]++;
        }
        int[] queue = new int[size];
        int head = 0, tail = 0, ni = 0;
        for (int b = 0; b < size; b++) {
            if (KINDS[kind[b]] == AnalogKind.INTEGRATOR) integrators[ni++] = b;
            if (!algebraicLoop[b] && indeg[b] == 0) queue[tail++] = b;
        }
        int[] ord = new int[size];
        int no = 0;
        while (head < tail) {
            int b = queue[head++];
            if (KINDS[kind[b]] != AnalogKind.INTEGRATOR) ord[no++] = b;
            for (int c : adj[b]) if (!algebraicLoop[c] && --indeg[c] == 0) queue[tail++] = c;
        }
        order = Arrays.copyOf(ord, no);
        for (int b = 0; b < size; b++) {
            if (!algebraicLoop[b]) continue;
            for (int s : out[b]) sig[s] = U_SAT;
            quality[b] = (byte) Quality.BAD.ordinal();
        }
        compiled = true;
    }

    /** Тарьян (рекурсивный: блоков на станцию ≤ 4096 по конфигу, на практике 128). */
    private void markLoops(int[][] adj) {
        int[] index = new int[size], low = new int[size], stack = new int[size];
        boolean[] onStack = new boolean[size];
        Arrays.fill(index, -1);
        int[] counter = {0, 0}; // [следующий индекс, вершина стека]
        for (int v = 0; v < size; v++) if (index[v] < 0) strongConnect(v, adj, index, low, stack, onStack, counter);
    }

    private void strongConnect(int v, int[][] adj, int[] index, int[] low, int[] stack, boolean[] onStack, int[] c) {
        index[v] = low[v] = c[0]++;
        stack[c[1]++] = v;
        onStack[v] = true;
        boolean selfLoop = false;
        for (int w : adj[v]) {
            if (w == v) selfLoop = true;
            if (index[w] < 0) {
                strongConnect(w, adj, index, low, stack, onStack, c);
                low[v] = Math.min(low[v], low[w]);
            } else if (onStack[w]) {
                low[v] = Math.min(low[v], index[w]);
            }
        }
        if (low[v] != index[v]) return;
        int start = c[1];
        while (true) {
            int w = stack[--start];
            if (w == v) break;
        }
        boolean loop = c[1] - start > 1 || selfLoop;
        for (int j = start; j < c[1]; j++) {
            onStack[stack[j]] = false;
            if (loop) algebraicLoop[stack[j]] = true;
        }
        c[1] = start;
    }

    private int newBlock(AnalogKind kd, AnalogTech t, int inputs, int outputs) {
        if (size >= cap) throw new IllegalStateException("analog.max_blocks_per_station");
        int b = size++;
        kind[b] = (byte) kd.ordinal();
        tech[b] = t;
        in[b] = new int[inputs];
        Arrays.fill(in[b], -1);
        k[b] = new double[inputs];
        out[b] = new int[outputs];
        for (int j = 0; j < outputs; j++) out[b][j] = newSignal();
        rng[b] = seeds.split();
        tempK[b] = 298.15;
        if (t != null) {
            gainErr[b] = (2 * rng[b].nextDouble() - 1) * t.gainTolerance;
            offset[b] = kd == AnalogKind.INTEGRATOR ? 0 : (2 * rng[b].nextDouble() - 1) * t.offsetV;
            sigma[b] = t.noiseSigmaV;
        }
        compiled = false;
        return b;
    }

    private void wire(int b, int[] inputs, double[] coefs, int max) {
        if (inputs.length > max || coefs.length != inputs.length) throw new IllegalArgumentException("inputs");
        for (int j = 0; j < inputs.length; j++) {
            if (inputs[j] != -1) checkSignal(inputs[j]);
            in[b][j] = inputs[j];
            k[b][j] = knob(coefs[j]);
        }
    }

    /** Ручка коэффициента: [−10, 10], шаг 0,01. */
    private static double knob(double v) {
        if (!(v >= -10 && v <= 10)) throw new IllegalArgumentException("coefficient " + v);
        return Math.round(v * 100) / 100.0;
    }

    /** Ручка постоянной времени: 3 значащие цифры. */
    private static double sig3(double v) {
        double scale = Math.pow(10, 2 - Math.floor(Math.log10(v)));
        return Math.round(v * scale) / scale;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private void checkSignal(int s) {
        if (s < 0 || s >= signals) throw new IllegalArgumentException("signal " + s);
    }

    private void checkBlock(int b) {
        if (b < 0 || b >= size) throw new IllegalArgumentException("block " + b);
    }

    private void checkKind(int b, AnalogKind kd) {
        checkBlock(b);
        if (KINDS[kind[b]] != kd) throw new IllegalArgumentException("block " + b + " is not " + kd);
    }
}
