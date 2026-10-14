package dev.denfry.promptcritical.sim.mining;

import java.util.Objects;

/**
 * Шахтный подъём (08 §8.2.8): барабан на валу Create, канат, клеть. Клеть — FAST, износ каната — там же
 * (без аллокаций). Глубина клети z отсчитывается от устья вниз; положительный момент барабана поднимает клеть:
 * (J/R² + m)·du/dt = τ/R − m·g − F_тр, m = m_кл + m_гр + q_k·z.
 *
 * <p>Запас прочности SF = F_разр/F_max, F_max = (m_кл + m_гр + q_k·L)(g + a) на полную глубину L и проектное
 * ускорение a. Износ — от изгибов на шкиве: dD_k/dN = 1/N_f, N_f = N₀((D/d)/40)^2,5(0,2R_r/σ)²; каждый метр хода
 * — доля L⁻¹ полного перегиба (самый нагруженный участок проходит шкив раз за рейс); плюс коррозия в мокром стволе.
 * Аппаратные защиты (P6): переподъём, превышение скорости &gt; 15 %, тормоз при потере питания.
 */
public final class Hoist {
    public static final double G = 9.81;
    /** Порог превышения скорости над тахограммой. */
    public static final double OVERSPEED = 1.15;
    /** Нормативное отношение D/d шкива (§8.2.8). */
    public static final double MIN_SHEAVE_RATIO = 79;
    /** Предельная скорость клети, м/с (§8.2.8). */
    public static final double MAX_SPEED = 12;

    /** Результат проверки загрузки клети. */
    public enum Verdict {
        ALLOWED,
        /** {@code ENFORCED}: запас прочности ниже нормы — загрузка запрещена. */
        FORBIDDEN,
        /** {@code ADVISORY}: загрузка выполнена, в журнал — {@code UNSAFE_OPERATION}. */
        UNSAFE_OPERATION
    }

    /**
     * @param depthM          глубина ствола L, м
     * @param drumRadiusM     радиус барабана, м
     * @param drumInertia     J барабана с редуктором, приведённый к валу барабана, кг·м²
     * @param sheaveDiameterM диаметр копрового шкива D, м
     * @param cageMassKg      масса клети
     * @param brakeForceN     усилие тормоза, приведённое к канату, Н
     * @param guideFrictionN  трение в проводниках, Н
     * @param designAccelMs2  проектное ускорение для SF (1 м/с²)
     * @param safetyCatches   у клети есть парашюты
     * @param wetShaft        мокрый ствол (коррозия каната)
     */
    public record Spec(double depthM, double drumRadiusM, double drumInertia, double sheaveDiameterM, double cageMassKg,
            double brakeForceN, double guideFrictionN, double designAccelMs2, boolean safetyCatches, boolean wetShaft) {
        public Spec {
            if (!(depthM > 0 && drumRadiusM > 0 && drumInertia >= 0 && sheaveDiameterM > 0 && cageMassKg > 0))
                throw new IllegalArgumentException("hoist geometry");
            if (!(brakeForceN > 0 && guideFrictionN >= 0 && designAccelMs2 >= 0)) throw new IllegalArgumentException("hoist");
        }
    }

    private final Spec spec;
    private final HoistConfig cfg;
    private final double diameterMm, gradeMpa, q, area;
    private final RopeConstruction construction;
    private double damage, tonneKm, lastInspected, travel;
    private long bendCycles;

    private double depth, speedUp, accelUp, tension, payloadKg, speedLimit = MAX_SPEED;
    private HoistDuty duty = HoistDuty.CARGO;
    private HoistState state = HoistState.HELD;

    public Hoist(Spec spec, RopeState rope, HoistConfig cfg) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.cfg = Objects.requireNonNull(cfg, "cfg");
        this.diameterMm = rope.diameterMm();
        this.gradeMpa = rope.gradeMpa();
        this.construction = rope.construction();
        this.q = rope.massPerMetreKg();
        this.area = rope.metalAreaM2();
        this.damage = rope.damage();
        this.bendCycles = rope.bendCycles();
        this.tonneKm = rope.tonneKm();
        this.lastInspected = rope.lastInspectedT();
        this.depth = 0;
        this.tension = staticLoadKg() * G;
    }

    /** Масса, висящая на канате: клеть, груз и канат длиной z, кг. */
    private double staticLoadKg() {
        return spec.cageMassKg() + payloadKg + q * depth;
    }

    /** F_max на полную глубину при проектном ускорении для груза m, Н. */
    public double maxRopeForceN(double payload) {
        return (spec.cageMassKg() + payload + q * spec.depthM()) * (G + spec.designAccelMs2());
    }

    /** SF текущей загрузки. */
    public double safetyFactor() {
        return safetyFactor(payloadKg);
    }

    /** SF для груза m, по прочности нового каната (норма ПБ). */
    public double safetyFactor(double payload) {
        return rope().breakingForceN() / maxRopeForceN(payload);
    }

    /** Отношение D/d шкива в норме (≥ 79). */
    public boolean sheaveRatioOk() {
        return spec.sheaveDiameterM() * 1000 / diameterMm >= MIN_SHEAVE_RATIO;
    }

    /**
     * Загрузить клеть: проверка запаса прочности по назначению. {@code ENFORCED} — при SF ниже нормы груз не
     * принимается; {@code ADVISORY} — принимается с записью {@code UNSAFE_OPERATION}.
     */
    public Verdict load(HoistDuty duty, double payload) {
        if (!(payload >= 0)) throw new IllegalArgumentException("payload: " + payload);
        Objects.requireNonNull(duty, "duty");
        boolean ok = safetyFactor(payload) >= cfg.requiredSafetyFactor(duty);
        if (!ok && cfg.safetyMode() == HoistConfig.SafetyMode.ENFORCED) return Verdict.FORBIDDEN;
        this.duty = duty;
        this.payloadKg = payload;
        return ok ? Verdict.ALLOWED : Verdict.UNSAFE_OPERATION;
    }

    /** Предельная скорость по тахограмме игрока, м/с (≤ 12). */
    public void setSpeedLimit(double ms) {
        if (!(ms > 0)) throw new IllegalArgumentException("speed limit: " + ms);
        speedLimit = Math.min(MAX_SPEED, ms);
    }

    /** Потеря питания привода: тормоз накладывается защитой. */
    public void tripPowerLoss() {
        if (!state.broken()) state = HoistState.TRIP_POWER_LOSS;
    }

    /** Сброс защиты (после устранения причины). Оборванный канат не сбрасывается. */
    public void reset() {
        if (state.tripped()) state = speedUp == 0 ? HoistState.HELD : HoistState.RUNNING;
    }

    /**
     * Шаг FAST.
     *
     * @param dt       шаг, с
     * @param torqueNm момент на валу барабана (+ — подъём), Н·м
     * @param brake    рабочий тормоз наложен
     */
    public void stepFast(double dt, double torqueNm, boolean brake) {
        if (state.broken() || !(dt > 0)) return;
        boolean braking = brake || state.tripped();
        double m = staticLoadKg();
        double mEff = spec.drumInertia() / (spec.drumRadiusM() * spec.drumRadiusM()) + m;
        double drive = torqueNm / spec.drumRadiusM() - m * G;
        double u = speedUp, a, uNew;
        if (u == 0) {
            double hold = (braking ? spec.brakeForceN() : 0) + spec.guideFrictionN();
            double net = Math.abs(drive) <= hold ? 0 : drive - Math.signum(drive) * hold;
            a = net / mEff;
            uNew = a * dt;
        } else {
            double resist = spec.guideFrictionN() + (braking ? spec.brakeForceN() : 0);
            a = (drive - Math.signum(u) * resist) / mEff;
            uNew = u + a * dt;
            if (Math.signum(uNew) != Math.signum(u) && (braking || Math.abs(drive) <= spec.guideFrictionN())) {
                uNew = 0;
                a = -u / dt;
            }
        }
        speedUp = uNew;
        accelUp = a;
        depth -= uNew * dt;
        tension = m * (G + a);

        wear(Math.abs(uNew) * dt, dt);
        if (tension > residualStrength() || damage >= 1) {
            breakRope();
            return;
        }
        if (depth < 0 || depth > spec.depthM()) {
            depth = Math.max(0, Math.min(spec.depthM(), depth));
            speedUp = 0;
            state = HoistState.TRIP_OVERWIND;
            return;
        }
        if (Math.abs(speedUp) > OVERSPEED * speedLimit) {
            state = HoistState.TRIP_OVERSPEED;
            return;
        }
        if (!state.tripped()) state = speedUp == 0 ? HoistState.HELD : HoistState.RUNNING;
    }

    private void wear(double ds, double dt) {
        if (ds > 0) {
            double sigma = tension / area;
            if (sigma > 0) {
                double nf = cyclesToFailure(cfg.ropeN0Cycles(), spec.sheaveDiameterM() * 1000 / diameterMm, gradeMpa,
                        sigma);
                damage += ds / spec.depthM() / nf;
            }
            travel += ds;
            while (travel >= spec.depthM()) {
                travel -= spec.depthM();
                bendCycles++;
            }
            tonneKm += payloadKg * 1e-3 * ds * 1e-3;
        }
        if (spec.wetShaft()) damage += cfg.corrosionPerDay() * dt / 86_400;
    }

    /** N_f = N₀((D/d)/40)^2,5·(0,2R_r/σ)² — перегибов до разрушения. */
    public static double cyclesToFailure(double n0, double sheaveRatio, double gradeMpa, double sigmaPa) {
        double s = 0.2 * gradeMpa * 1e6 / sigmaPa;
        return n0 * Math.pow(sheaveRatio / 40, 2.5) * s * s;
    }

    private double residualStrength() {
        return damage >= 1 ? 0 : construction.breakingCoeff * diameterMm * diameterMm * gradeMpa
                * (1 - RopeState.STRENGTH_LOSS_AT_FULL_DAMAGE * damage);
    }

    private void breakRope() {
        damage = Math.max(damage, 1);
        speedUp = 0;
        if (spec.safetyCatches()) {
            state = HoistState.ROPE_BROKEN_CAUGHT;
        } else {
            depth = spec.depthM();
            state = HoistState.ROPE_BROKEN_FELL;
        }
    }

    /** Осмотр каната (обрывы проволок видны, 13). */
    public RopeState inspect(double t) {
        lastInspected = t;
        return rope();
    }

    /** Снимок состояния каната (для предмета и сохранения). */
    public RopeState rope() {
        return new RopeState(diameterMm, gradeMpa, construction, damage, bendCycles, lastInspected, tonneKm);
    }

    /** Глубина клети от устья, м. */
    public double positionM() {
        return depth;
    }

    /** Скорость клети (+ — вверх), м/с. */
    public double speedMs() {
        return speedUp;
    }

    public double accelerationMs2() {
        return accelUp;
    }

    /** Натяжение каната у шкива, Н. */
    public double tensionN() {
        return tension;
    }

    /** Угловая скорость барабана, рад/с (для вала Create). */
    public double drumOmega() {
        return speedUp / spec.drumRadiusM();
    }

    public double payloadKg() {
        return payloadKg;
    }

    public HoistDuty duty() {
        return duty;
    }

    public HoistState state() {
        return state;
    }

    /** {@code ROPE_WEAR} на панели обслуживания. */
    public boolean ropeWearAlarm() {
        return damage >= RopeState.WEAR_ALARM;
    }

    public Spec spec() {
        return spec;
    }
}
