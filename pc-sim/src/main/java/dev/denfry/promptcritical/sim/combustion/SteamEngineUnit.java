package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.mechanics.RotatingBody;
import java.util.Objects;

/**
 * Паровая машина I0 целиком (07 §7.9.7): цилиндры {@link SteamEngine}, коленвал с маховиком
 * ({@link RotatingBody}, 03a §3a.2), центробежный регулятор {@link FlyballGovernor}.
 *
 * <p>Пар берётся из парового узла LEM за дроссельной заслонкой (золотниковая коробка): биндинг передаёт давление
 * и плотность этого узла, отбирает из него {@link #steamFlowKgS()} и ставит заслонку по {@link #throttle()}. Так
 * дроссель — обычная связь {@code VALVE_CONTROL} гидросети (01), а p₁ — давление за ней. Выхлоп — в атмосферу
 * (I0) или конденсатор (давление p_b).
 *
 * <p>Механические потери (КПД η_м, 0,9) — трение вала, приведённое к номинальной точке: половина сухая, половина
 * вязкая. Момент пульсирует по углу коленвала ({@link SteamEngine#torqueShape}); внутри тика — {@link #SUBSTEPS}
 * подшагов, чтобы пульсация ходов (2 на оборот) не терялась на шаге 0,05 с. Без аллокаций.
 */
public final class SteamEngineUnit {
    /**
     * @param mechanicalEfficiency η_м в номинальной точке
     * @param nominalRevPerS       номинальные обороты, об/с (60–180 об/мин)
     * @param nominalInletPa       p₁ в номинальной точке, Па
     * @param nominalBackPa        p_b в номинальной точке, Па
     * @param nominalCutoff        ε в номинальной точке
     * @param inertia              J коленвала с маховиком, кг·м²
     */
    public record Spec(double boreM, double strokeM, int cylinders, double diagramFactor, double condensationLoss,
            double mechanicalEfficiency, double nominalRevPerS, double nominalInletPa, double nominalBackPa,
            double nominalCutoff, double inertia) {
        public Spec {
            if (!(mechanicalEfficiency > 0 && mechanicalEfficiency <= 1)) throw new IllegalArgumentException("eta_m");
            if (!(nominalRevPerS > 0 && nominalInletPa > nominalBackPa && nominalBackPa >= 0 && inertia > 0))
                throw new IllegalArgumentException("nominal point");
        }

        /** Машина примера 07 §7.9.7 (T7.14): D 0,4 м, ход 0,6 м, 1,1 МПа, ε 0,25, выхлоп 0,11 МПа, 120 об/мин. */
        public static Spec reference(int cylinders, double inertia) {
            return new Spec(0.4, 0.6, cylinders, SteamEngine.DIAGRAM_FACTOR, SteamEngine.CONDENSATION_LOSS, 0.9, 2.0,
                    1.1e6, 0.11e6, 0.25, inertia);
        }
    }

    /** Подшагов вала на тик. */
    public static final int SUBSTEPS = 8;
    /** J коленвала без маховика у эталонной машины, кг·м²: обороты пульсируют ≈ ±10 % (07 §7.9.7). */
    public static final double BARE_INERTIA = 200;
    /**
     * J с маховиком на цилиндр эталонной машины, кг·м² (≈ 5 т на радиусе 1,26 м). Регулятор с катарактом устойчив,
     * пока T_y/(δ·ω₀·J) &lt; 2ζω_g (T_y — прирост момента на полный ход заслонки); с этим маховиком запас ≈ 2 при
     * любой нагрузке, а без катаракта контур качает.
     */
    public static final double FLYWHEEL_INERTIA = 8000;
    /** ζ полностью открытой дроссельной заслонки. */
    public static final double THROTTLE_ZETA = 1.5;
    /** Перепад на открытой заслонке при номинальном расходе, доля p₁. */
    public static final double THROTTLE_FULL_DROP = 0.04;

    private final Spec spec;
    private final SteamEngine engine;
    private final RotatingBody body;
    private final FlyballGovernor governor;
    private final double nominalOmega;
    private double crankAngle, cutoff, speedSetting;
    private double steamFlow, indicatedPower, shaftPower, minOmega, maxOmega;

    public SteamEngineUnit(Spec spec, FlyballGovernor governor) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.governor = Objects.requireNonNull(governor, "governor");
        this.engine = new SteamEngine(spec.boreM(), spec.strokeM(), spec.cylinders(), spec.diagramFactor(),
                spec.condensationLoss());
        this.nominalOmega = 2 * Math.PI * spec.nominalRevPerS();
        double tFr = (1 - spec.mechanicalEfficiency())
                * engine.meanIndicatedTorqueNm(spec.nominalInletPa(), spec.nominalBackPa(), spec.nominalCutoff());
        this.body = new RotatingBody(new RotatingBody.Spec(spec.inertia(), 0.5 * tFr, 0.5 * tFr / nominalOmega), 0);
        this.cutoff = spec.nominalCutoff();
        this.speedSetting = nominalOmega;
    }

    public Spec spec() {
        return spec;
    }

    public SteamEngine engine() {
        return engine;
    }

    public RotatingBody shaft() {
        return body;
    }

    public FlyballGovernor governor() {
        return governor;
    }

    /** Отсечка ε (кулиса), ограничивается пределами {@link SteamEngine#CUTOFF_MIN}–{@link SteamEngine#CUTOFF_MAX}. */
    public void setCutoff(double cutoff) {
        this.cutoff = Math.max(SteamEngine.CUTOFF_MIN, Math.min(SteamEngine.CUTOFF_MAX, cutoff));
    }

    public double cutoff() {
        return cutoff;
    }

    /** Уставка регулятора ω₀ (натяжение пружины), рад/с. */
    public void setSpeedSetting(double omega0) {
        if (!(omega0 > 0)) throw new IllegalArgumentException("omega0: " + omega0);
        speedSetting = omega0;
    }

    public double speedSetting() {
        return speedSetting;
    }

    public double nominalOmega() {
        return nominalOmega;
    }

    /** Расход пара в номинальной точке, кг/с. */
    public double nominalSteamFlowKgS(double rhoSteamNominal) {
        return engine.steamFlowKgS(rhoSteamNominal, spec.nominalCutoff(), spec.nominalRevPerS());
    }

    /**
     * Сечение дроссельной заслонки (ζ = {@link #THROTTLE_ZETA}) под номинальный расход с перепадом
     * {@link #THROTTLE_FULL_DROP}·p₁: A = ṁ·√(ζ/(2ρΔp)). Заслонка по паропроводу оказывается в номинале открыта на
     * 0,1–0,2, и регулятор работает в зоне большого коэффициента усиления — обороты качает даже с катарактом.
     */
    public double throttleAreaM2(double rhoSteamNominal) {
        double dp = THROTTLE_FULL_DROP * spec.nominalInletPa();
        return nominalSteamFlowKgS(rhoSteamNominal) * Math.sqrt(THROTTLE_ZETA / (2 * rhoSteamNominal * dp));
    }

    /**
     * Шаг FAST.
     *
     * @param p1Pa       давление в золотниковой коробке (за дросселем), Па
     * @param rhoSteam   плотность пара там же, кг/м³
     * @param pBackPa    давление выхлопа, Па
     * @param loadTorque момент нагрузки на валу (сеть Create), Н·м
     */
    public void step(double dt, double p1Pa, double rhoSteam, double pBackPa, double loadTorque) {
        double h = dt / SUBSTEPS;
        double tMean = engine.meanIndicatedTorqueNm(p1Pa, pBackPa, cutoff);
        double flow = 0, work = 0, out = 0;
        minOmega = maxOmega = body.omega();
        for (int k = 0; k < SUBSTEPS; k++) {
            double w0 = body.omega();
            // p_mi < 0 (впуска не хватает на выхлоп) — насосные потери: тормозят, но не крутят назад
            double tau = tMean >= 0 || w0 > 0 ? tMean * engine.torqueShape(crankAngle) : 0;
            body.stepTorque(h, tau, loadTorque);
            if (tMean < 0 && w0 > 0 && body.omega() < 0) body.setOmega(0);
            double w = body.omega(), wMid = 0.5 * (w0 + w);
            crankAngle += h * wMid;
            if (crankAngle >= 2 * Math.PI) crankAngle -= 2 * Math.PI;
            else if (crankAngle < 0) crankAngle += 2 * Math.PI;
            flow += engine.steamFlowKgS(rhoSteam, cutoff, wMid / (2 * Math.PI));
            work += tau * wMid;
            out += loadTorque * wMid;
            governor.step(h, w, speedSetting);
            minOmega = Math.min(minOmega, w);
            maxOmega = Math.max(maxOmega, w);
        }
        steamFlow = flow / SUBSTEPS;
        indicatedPower = work / SUBSTEPS;
        shaftPower = out / SUBSTEPS;
    }

    /** ω вала, рад/с. */
    public double omega() {
        return body.omega();
    }

    public double revPerS() {
        return body.omega() / (2 * Math.PI);
    }

    /** Угол коленвала θ ∈ [0, 2π). */
    public double crankAngle() {
        return crankAngle;
    }

    public void setCrankAngle(double theta) {
        crankAngle = theta;
    }

    /** Открытие дроссельной заслонки от регулятора, [0, 1]. */
    public double throttle() {
        return governor.throttle();
    }

    /** Расход пара, средний за тик, кг/с. */
    public double steamFlowKgS() {
        return steamFlow;
    }

    /** Индикаторная мощность, средняя за тик, Вт. */
    public double indicatedPowerW() {
        return indicatedPower;
    }

    /** Мощность, отданная нагрузке (в сеть Create), средняя за тик, Вт. */
    public double shaftPowerW() {
        return shaftPower;
    }

    /** Минимум и максимум ω за последний тик (пульсация), рад/с. */
    public double minOmegaLastTick() {
        return minOmega;
    }

    public double maxOmegaLastTick() {
        return maxOmega;
    }
}
