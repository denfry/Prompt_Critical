package dev.denfry.promptcritical.sim.wind;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import java.util.Objects;

/**
 * Ветроагрегат постоянной скорости: аэродинамика колеса ({@link RotorPerformance}), трансмиссия, тормоз,
 * поворот на ветер и автоматика пуска, регулирования и останова. Электрическую машину и её сеть ведёт
 * владелец — здесь только момент, который колесо передаёт на вал генератора, и команда контактору.
 *
 * <p>Аэродинамика: Q = ½ρAV_n²R·C_Q(λ, β), T = ½ρAV_n²·C_T(λ, β), λ = ΩR/V_n. Косой обдув — нормальная к колесу
 * составляющая V_n = V·cos γ, мощность ∝ cos³γ (Burton et al., «Wind Energy Handbook», 2011, §3.10; замеры дают
 * показатель 2–3). Потери мультипликатора — (1 − η)·|Q| плюс 0,5 % номинального момента холостого хода.
 *
 * <p>Автоматика — типичная для сетевой ВЭУ с асинхронным генератором (Burton et al., 2011, §8.2–8.3; Heier,
 * «Grid Integration of Wind Energy», 3-е изд., 2014, §5): пуск только при напряжении сети на выводах генератора;
 * минутное среднее ветра выше порога — отпустить тормоз,
 * поставить лопасти на пусковой угол, у синхронной скорости включить контактор (генератор подхватывает колесо
 * без синхронизации); выше номинала — поворот лопастей к флюгеру по ошибке мощности; двигательный режим 20 с
 * или штиль — флюгер и отключение; 10-минутное среднее выше {@link WindTurbineSpec#cutOutMs} или порыв на 5 м/с
 * сверх него — штормовая остановка до спада ниже {@link WindTurbineSpec#restartMs}. Разнос колеса и перегрузка
 * башни — аварийный флюгер с тормозом и защёлка до сброса.
 */
public final class WindTurbine {
    public enum State { PARKED, STARTING, GENERATING, STOPPING, STORM, FAULT }

    public enum Fault { NONE, OVERSPEED, OVERLOAD }

    /** Время двигательного режима до отключения, с; выдержка перегрузки башни, с. */
    static final double MOTORING_S = 20, OVERLOAD_S = 2;
    /** Доля номинальной мощности, ниже которой (с минусом) режим — двигательный. */
    static final double MOTORING_FRACTION = 0.03;
    /** Контактор включается на этой доле синхронной скорости. */
    static final double CLOSE_SPEED = 0.98;
    /** Пропорциональный коэффициент регулятора мощности, °/с на о.е. превышения. */
    static final double PITCH_GAIN_DEG_S = 20;
    /** Холостой ход трансмиссии, доля номинального момента колеса. */
    static final double NO_LOAD_TORQUE = 0.005;
    /** Минутное и 10-минутное усреднение ветра, с. */
    static final double MEAN_SHORT_S = 60, MEAN_LONG_S = 600;
    /** Доля синхронной скорости, к которой пусковой угол лопастей сведён к рабочему. */
    static final double START_PITCH_SPAN = 0.5;
    /** Порыв сверх скорости штормовой остановки, м/с. */
    static final double GUST_MARGIN_MS = 5;

    private final WindTurbineSpec spec;
    private final RotorPerformance perf;
    private double frequencyHz;

    // ветер
    private double windMs, windDirRad, densityKgM3 = 1.225;
    private double meanShortMs, meanLongMs;
    private boolean primed;
    // механика
    private double omegaRotor, pitchDeg = 90, yawRad;
    private boolean yawing, brake = true, contactor, enabled;
    // автоматика
    private State state = State.PARKED;
    private Fault fault = Fault.NONE;
    private double motoringS, overloadS;
    private boolean stormLatched;

    public WindTurbine(WindTurbineSpec spec, RotorPerformance perf, double frequencyHz) {
        this.spec = Objects.requireNonNull(spec, "spec");
        this.perf = Objects.requireNonNull(perf, "perf");
        if (!(frequencyHz > 0)) throw new IllegalArgumentException("frequencyHz");
        this.frequencyHz = frequencyHz;
    }

    /** Частота сети, по которой считается синхронная скорость генератора, Гц. */
    public void setFrequencyHz(double hz) {
        if (!(hz > 0)) throw new IllegalArgumentException("frequencyHz");
        frequencyHz = hz;
    }

    public WindTurbineSpec spec() {
        return spec;
    }

    public RotorPerformance performance() {
        return perf;
    }

    // ---------------------------------------------------------------- входы

    /** Плотность воздуха у оси колеса, кг/м³. */
    public double densityKgM3() {
        return densityKgM3;
    }

    /** Ветер у оси колеса: мгновенная скорость, откуда дует (рад), плотность воздуха. */
    public void setWind(double speedMs, double fromRad, double densityKgM3) {
        windMs = Math.max(0, speedMs);
        windDirRad = fromRad;
        this.densityKgM3 = densityKgM3 > 0 ? densityKgM3 : 1.225;
        if (!primed) {
            meanShortMs = meanLongMs = windMs;
            yawRad = fromRad;
            primed = true;
        }
    }

    /** Разрешение работы (ключ «автомат»): снятое — штатная остановка. */
    public void setEnabled(boolean on) {
        enabled = on;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Сброс защёлки аварии: только на стоящем колесе. */
    public boolean resetFault() {
        if (state != State.FAULT || omegaRotor > 0.05 * syncOmegaRotor()) return false;
        fault = Fault.NONE;
        state = State.PARKED;
        return true;
    }

    /** Скорость колеса от владельца (по ротору генератора в сети), рад/с. */
    public void setOmegaRotor(double omega) {
        omegaRotor = Math.max(0, omega);
    }

    // ---------------------------------------------------------------- выходы

    public double omegaRotor() {
        return omegaRotor;
    }

    public double rotorRpm() {
        return omegaRotor * 60 / (2 * Math.PI);
    }

    public double syncOmegaRotor() {
        return spec.syncOmegaGenerator(frequencyHz) / spec.gearRatio();
    }

    public double windMs() {
        return windMs;
    }

    public double meanShortMs() {
        return meanShortMs;
    }

    public double meanLongMs() {
        return meanLongMs;
    }

    public double pitchDeg() {
        return pitchDeg;
    }

    public double yawRad() {
        return yawRad;
    }

    /** Рассогласование колеса с ветром, рад (−π…π). */
    public double yawErrorRad() {
        return Math.IEEEremainder(windDirRad - yawRad, 2 * Math.PI);
    }

    public boolean brakeApplied() {
        return brake;
    }

    /** Команда контактору генератора. */
    public boolean contactorCommand() {
        return contactor;
    }

    public State state() {
        return state;
    }

    /** Штормовая остановка в силе (10-минутное среднее ещё не опустилось ниже скорости повторного пуска). */
    public boolean stormHold() {
        return stormLatched;
    }

    public Fault fault() {
        return fault;
    }

    /** Нормальная к колесу составляющая ветра, м/с. */
    public double normalWindMs() {
        return windMs * Math.max(0, Math.cos(yawErrorRad()));
    }

    /** Быстроходность при скорости колеса ω. */
    public double tipSpeedRatio(double omega) {
        double v = normalWindMs();
        return v < 0.1 ? RotorPerformance.TSR_MAX : omega * spec.radiusM() / v;
    }

    /** Аэродинамический момент колеса при скорости ω, Н·м. */
    public double aeroTorqueNm(double omega) {
        double v = normalWindMs();
        if (v < 0.1) return 0;
        double q = 0.5 * densityKgM3 * spec.sweptAreaM2() * v * v * spec.radiusM();
        return q * perf.torqueCoefficient(omega * spec.radiusM() / v, pitchDeg);
    }

    /** Аэродинамическая мощность колеса, Вт. */
    public double aeroPowerW() {
        return aeroTorqueNm(omegaRotor) * omegaRotor;
    }

    /** Осевая сила на колесо, Н. */
    public double thrustN() {
        double v = normalWindMs();
        if (v < 0.1) return 0;
        return 0.5 * densityKgM3 * spec.sweptAreaM2() * v * v * perf.thrustCoefficient(tipSpeedRatio(omegaRotor), pitchDeg);
    }

    /** Изгибающий момент у основания башни от осевой силы колеса, Н·м. */
    public double towerBaseMomentNm() {
        return thrustN() * spec.hubHeightM();
    }

    /** Коэффициент мощности колеса в нынешнем режиме. */
    public double powerCoefficient() {
        double v = normalWindMs();
        if (v < 0.1) return 0;
        return perf.powerCoefficient(tipSpeedRatio(omegaRotor), pitchDeg);
    }

    /**
     * Момент на валу колеса за вычетом потерь трансмиссии и тормоза при скорости ω, Н·м (положительный — колесо
     * ведёт генератор). Стоящее колесо тормоз держит, пока аэродинамический момент не больше тормозного.
     */
    public double shaftTorqueNm(double omega) {
        double qa = aeroTorqueNm(omega);
        double rated = ratedRotorTorqueNm();
        double loss = (1 - spec.gearEfficiency()) * Math.abs(qa) + NO_LOAD_TORQUE * rated;
        double resist = loss + (brake ? spec.brakeTorqueNm() : 0);
        if (omega <= 1e-9) return Math.abs(qa) <= resist ? 0 : qa - Math.signum(qa) * resist;
        return qa - resist;
    }

    /** Номинальный момент на валу колеса: P_ном/(η·ω_с), Н·м. */
    public double ratedRotorTorqueNm() {
        return spec.ratedPowerW() / (spec.gearEfficiency() * syncOmegaRotor());
    }

    /** Свободный ротор (генератор не в сети): 2-й закон для приведённой инерции, полунеявно по потерям. */
    public void stepFree(double dt) {
        double j = spec.rotorInertiaKgM2() + spec.generatorInertiaKgM2() * spec.gearRatio() * spec.gearRatio();
        int n = 4;
        double h = dt / n;
        for (int k = 0; k < n; k++) {
            double t = shaftTorqueNm(omegaRotor);
            omegaRotor = Math.max(0, omegaRotor + h * t / j);
        }
    }

    // ---------------------------------------------------------------- автоматика

    /**
     * Шаг автоматики.
     *
     * @param dt           шаг, с
     * @param exportW      активная мощность генератора в сеть, Вт (отрицательная — двигательный режим)
     * @param gridLive     на выводах генератора есть напряжение сети
     */
    public void control(double dt, double exportW, boolean gridLive) {
        meanShortMs += (windMs - meanShortMs) * Math.min(1, dt / MEAN_SHORT_S);
        meanLongMs += (windMs - meanLongMs) * Math.min(1, dt / MEAN_LONG_S);
        yaw(dt);

        double ws = syncOmegaRotor();
        if (state != State.FAULT) {
            if (omegaRotor > spec.overspeed() * ws) trip(Fault.OVERSPEED);
            overloadS = thrustN() > spec.thrustLimitN() ? overloadS + dt : 0;
            if (overloadS > OVERLOAD_S) trip(Fault.OVERLOAD);
        }
        // штормовая защёлка: ставится по 10-минутному среднему или порыву, снимается ниже скорости повторного пуска
        if (meanLongMs > spec.cutOutMs() || windMs > spec.cutOutMs() + GUST_MARGIN_MS) stormLatched = true;
        else if (meanLongMs < spec.restartMs() && windMs <= spec.cutOutMs()) stormLatched = false;
        boolean storm = stormLatched;

        switch (state) {
            case PARKED -> {
                contactor = false;
                brake = true;
                pitchToward(90, spec.pitchRateDegS(), dt);
                if (enabled && gridLive && !storm && meanShortMs >= spec.cutInMs()) state = State.STARTING;
            }
            case STARTING -> {
                brake = false;
                contactor = false;
                if (!enabled || !gridLive || meanShortMs < spec.cutInMs() - 1) {
                    state = State.STOPPING;
                } else if (storm) {
                    state = State.STORM;
                } else if (omegaRotor >= CLOSE_SPEED * ws) {
                    contactor = true;
                    motoringS = 0;
                    state = State.GENERATING;
                } else {
                    // разгон: пусковой угол даёт момент трогания, к половине синхронной скорости программа сводит
                    // его к рабочему (на пусковом угле при λ ≈ 4 момент колеса уже нулевой); без сети — холостой
                    // ход у синхронной скорости
                    double frac = Math.max(0, 1 - omegaRotor / (START_PITCH_SPAN * ws));
                    double target = spec.runPitchDeg() + (spec.startPitchDeg() - spec.runPitchDeg()) * frac;
                    if (omegaRotor > ws) target = Math.min(90, pitchDeg + PITCH_GAIN_DEG_S * (omegaRotor / ws - 1));
                    pitchToward(target, spec.pitchRateDegS(), dt);
                }
            }
            case GENERATING -> {
                brake = false;
                if (!gridLive) {
                    contactor = false;
                    state = State.STOPPING;
                } else if (!enabled) {
                    state = State.STOPPING;
                } else if (storm) {
                    state = State.STORM;
                } else {
                    motoringS = exportW < -MOTORING_FRACTION * spec.ratedPowerW() ? motoringS + dt : 0;
                    if (motoringS > MOTORING_S) {
                        state = State.STOPPING;
                    } else {
                        double rate = PITCH_GAIN_DEG_S * (exportW / spec.ratedPowerW() - 1);
                        rate = Math.max(-spec.pitchRateDegS(), Math.min(spec.pitchRateDegS(), rate));
                        pitchDeg = Math.max(spec.runPitchDeg(), Math.min(90, pitchDeg + rate * dt));
                    }
                }
            }
            case STOPPING, STORM -> {
                pitchToward(90, spec.featherRateDegS(), dt);
                // контактор держит колесо у синхронной скорости, пока лопасти снимают мощность
                if (contactor && (!gridLive || exportW <= 0 || omegaRotor < 0.95 * ws)) contactor = false;
                brake = !contactor && pitchDeg >= 89 && omegaRotor < 0.1 * ws;
                if (state == State.STOPPING && enabled && gridLive && !storm && meanShortMs >= spec.cutInMs()
                        && pitchDeg >= 89) {
                    state = State.STARTING;
                } else if (state == State.STORM && !storm) {
                    state = State.STOPPING;
                } else if (brake && omegaRotor < 0.01 * ws && state == State.STOPPING) {
                    state = State.PARKED;
                }
            }
            case FAULT -> {
                contactor = false;
                brake = true;
                pitchToward(90, spec.featherRateDegS(), dt);
            }
        }
    }

    private void trip(Fault f) {
        fault = f;
        state = State.FAULT;
        contactor = false;
        brake = true;
    }

    private void pitchToward(double target, double rate, double dt) {
        double step = rate * dt;
        pitchDeg += Math.max(-step, Math.min(step, target - pitchDeg));
    }

    /** Поворот на ветер с зоной нечувствительности: пуск при рассогласовании больше её, стоп у 2°. */
    private void yaw(double dt) {
        double err = yawErrorRad();
        double abs = Math.abs(err);
        if (abs > Math.toRadians(spec.yawDeadBandDeg())) yawing = true;
        else if (abs < Math.toRadians(2)) yawing = false;
        if (!yawing) return;
        double step = Math.toRadians(spec.yawRateDegS()) * dt;
        yawRad = Math.IEEEremainder(yawRad + Math.signum(err) * Math.min(step, abs), 2 * Math.PI);
    }

    // ---------------------------------------------------------------- сохранение

    public void save(StateWriter out) {
        out.putDouble("omega", omegaRotor);
        out.putDouble("pitch", pitchDeg);
        out.putDouble("yaw", yawRad);
        out.putBoolean("yawing", yawing);
        out.putBoolean("brake", brake);
        out.putBoolean("contactor", contactor);
        out.putBoolean("enabled", enabled);
        out.putString("state", state.name());
        out.putString("fault", fault.name());
        out.putDouble("mean_short", meanShortMs);
        out.putDouble("mean_long", meanLongMs);
        out.putBoolean("primed", primed);
        out.putDouble("motoring", motoringS);
        out.putBoolean("storm", stormLatched);
    }

    public void load(StateReader in) {
        omegaRotor = Math.max(0, in.optDouble("omega", 0));
        pitchDeg = Math.max(RotorPerformance.PITCH_MIN_DEG, Math.min(90, in.optDouble("pitch", 90)));
        yawRad = in.optDouble("yaw", 0);
        yawing = in.has("yawing") && in.getBoolean("yawing");
        brake = !in.has("brake") || in.getBoolean("brake");
        contactor = in.has("contactor") && in.getBoolean("contactor");
        enabled = in.has("enabled") && in.getBoolean("enabled");
        state = parse(State.class, in.optString("state", State.PARKED.name()), State.PARKED);
        fault = parse(Fault.class, in.optString("fault", Fault.NONE.name()), Fault.NONE);
        meanShortMs = in.optDouble("mean_short", 0);
        meanLongMs = in.optDouble("mean_long", 0);
        primed = in.has("primed") && in.getBoolean("primed");
        motoringS = in.optDouble("motoring", 0);
        stormLatched = in.has("storm") && in.getBoolean("storm");
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E def) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }
}
