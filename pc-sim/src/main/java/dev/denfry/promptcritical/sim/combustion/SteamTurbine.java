package dev.denfry.promptcritical.sim.combustion;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.thermo.ThermoState;
import dev.denfry.promptcritical.sim.thermo.WaterProperties;

/**
 * Паровая турбина с противодавлением (одна группа ступеней) — первичный двигатель электростанции эпохи I1.
 *
 * <ul>
 *   <li>Расход — эллипс Стодолы: ṁ = K·√(p₁·ρ₁·(1 − (p₂/p₁)²)), K — по расчётной точке.</li>
 *   <li>Работа — изоэнтропийный перепад по IF97, h₁ − h(p₂, s₁), умноженный на внутренний КПД. КПД зависит от
 *   отношения окружной скорости к скорости пара: η = η_р·(2x − x²), x = ω/ω_ном (активная ступень: максимум при
 *   расчётных оборотах, ноль при стоянке и на угоне, x = 2), поэтому момент при пуске вдвое больше расчётного.
 *   Влажный пар — поправка Баумана: −1 % КПД на 1 % средней влажности, средняя — половина выходной.</li>
 *   <li>Регулятор скорости со статизмом R: открытие y = (ω_хх − ω)/(R·ω_ном), сервомотор — инерционное звено T_с.
 *   Механизм управления (синхронизатор) сдвигает ω_хх.</li>
 *   <li>Автомат безопасности: при 110 % оборотов стопорный клапан закрывается и остаётся закрытым до взвода.</li>
 * </ul>
 * Расчётный КПД небольших турбин начала XX века (Кёртис, Рато) — 0,6–0,7 [порядок]; механический 0,97.
 * Без конденсатора: отработавший пар уходит на отопление, деаэратор или в атмосферу.
 */
public final class SteamTurbine {
    /** Обороты срабатывания автомата безопасности, доля номинальных. */
    public static final double OVERSPEED_TRIP = 1.10;
    /** Перепад на полностью открытом регулирующем клапане в расчётной точке, доля давления пара. */
    public static final double VALVE_DROP = 0.05;
    /** ζ регулирующего клапана (как у дроссельной заслонки машины). */
    public static final double THROTTLE_ZETA = SteamEngineUnit.THROTTLE_ZETA;
    /** Конденсатор: нагрев охлаждающей воды и недогрев до насыщения в расчётной точке, К. */
    public static final double CW_RISE_K = 10, CONDENSER_TTD_K = 3;

    /**
     * Давление в конденсаторе при температуре охлаждающей воды на входе, Па: насыщение при
     * T_вх + {@link #CW_RISE_K} + {@link #CONDENSER_TTD_K} (15 °C → 28 °C, ≈ 3,8 кПа; 25 °C → 38 °C, ≈ 6,6 кПа).
     */
    public static double condenserPressurePa(WaterProperties w, double coolingWaterK) {
        return w.saturationPressure(coolingWaterK + CW_RISE_K + CONDENSER_TTD_K);
    }

    /** Потери на вентиляцию и трение при номинальных оборотах, доля мощности; ∝ ω³. */
    static final double WINDAGE = 0.01;

    /**
     * Паспорт.
     *
     * @param ratedPowerW        мощность на валу в расчётной точке, Вт
     * @param inletPa            давление свежего пара, Па
     * @param inletEnthalpy      энтальпия свежего пара, Дж/кг
     * @param exhaustPa          противодавление, Па
     * @param nominalOmega       номинальные обороты, рад/с
     * @param internalEfficiency η_р внутренний КПД при расчётных оборотах
     * @param inertiaKgM2        момент инерции ротора
     * @param droop              статизм регулятора
     * @param servoS             постоянная сервомотора, с
     */
    public record Spec(double ratedPowerW, double inletPa, double inletEnthalpy, double exhaustPa, double nominalOmega,
            double internalEfficiency, double mechanicalEfficiency, double inertiaKgM2, double droop, double servoS,
            double stodolaK, double ratedFlowKgS, double inletDensity) {

        /**
         * Турбина под заданный пар и противодавление: расход и константа Стодолы — из расчётной точки.
         *
         * @param hInertiaS постоянная инерции ротора H = Jω²/(2P), с
         */
        public static Spec design(WaterProperties w, double ratedPowerW, double inletPa, double inletTempK,
                double exhaustPa, double rpm, double internalEfficiency, double hInertiaS) {
            double h1 = w.enthalpyPT(inletPa, inletTempK);
            ThermoState st = new ThermoState();
            w.stateFromPressureEnthalpy(inletPa, h1, st);
            double rho1 = st.density;
            double omega = 2 * Math.PI * rpm / 60, etaM = 0.97;
            double work = specificWork(w, inletPa, h1, exhaustPa, internalEfficiency, st);
            double flow = ratedPowerW / (etaM * work);
            double k = flow / Math.sqrt(inletPa * rho1 * (1 - sq(exhaustPa / inletPa)));
            double j = 2 * hInertiaS * ratedPowerW / (omega * omega);
            return new Spec(ratedPowerW, inletPa, h1, exhaustPa, omega, internalEfficiency, etaM, j, 0.05, 0.2, k, flow, rho1);
        }

        /**
         * Турбина заводской станции I1 под пар котла ДКВР: 750 кВт, 1,3 МПа и 250 °C, выхлоп на отопление и
         * деаэратор 0,12 МПа, 3000 об/мин; расход ≈ 2,5 кг/с — в пределах одного котла (≈ 2,9 кг/с). H = 3 с — ротор
         * с редуктором и генератором; сервомотор 0,2 с.
         */
        public static Spec reference(WaterProperties w) {
            return design(w, 750e3, 1.3e6, 523.15, 0.12e6, 3000, 0.65, 3.0);
        }

        /** Площадь регулирующего клапана: при полном открытии и расчётном расходе на нём {@link #VALVE_DROP}. */
        public double throttleAreaM2() {
            double dp = VALVE_DROP * inletPa;
            return ratedFlowKgS / Math.sqrt(2 * inletDensity * dp / THROTTLE_ZETA);
        }
    }

    private final Spec spec;
    private final WaterProperties water;
    private final ThermoState scratch = new ThermoState();
    private double omega, valve, noLoadOmega, flow, internalPowerW, shaftPowerW;
    private boolean tripped;

    public SteamTurbine(Spec spec, WaterProperties water) {
        this.spec = spec;
        this.water = water;
        noLoadOmega = spec.nominalOmega() * (1 + spec.droop());
    }

    /**
     * Шаг: пар в паровой коробке (p, h), противодавление, момент нагрузки на валу.
     *
     * @param stopValveOpen стопорный клапан открыт оператором (и не сработал автомат)
     */
    public void step(double dt, double chestPa, double chestEnthalpy, double backPa, double loadTorque, boolean stopValveOpen) {
        double x = omega / spec.nominalOmega();
        flow = 0;
        internalPowerW = 0;
        double stallWork = 0;
        if (chestPa > backPa && chestEnthalpy > 0) {
            water.stateFromPressureEnthalpy(chestPa, chestEnthalpy, scratch);
            flow = spec.stodolaK() * Math.sqrt(chestPa * scratch.density * (1 - sq(backPa / chestPa)));
            double eta = spec.internalEfficiency() * Math.max(0, 2 * x - x * x);
            internalPowerW = flow * specificWork(water, chestPa, chestEnthalpy, backPa, eta, scratch);
            if (x <= 1e-3) stallWork = flow * specificWork(water, chestPa, chestEnthalpy, backPa, spec.internalEfficiency(), scratch);
        }
        double windage = WINDAGE * spec.ratedPowerW() * x * x * x;
        shaftPowerW = spec.mechanicalEfficiency() * internalPowerW - windage;
        // момент пара: P/ω, при стоянке — предел (2 − x)/ω_ном · ṁ·Δh
        double steamTorque = x > 1e-3 ? spec.mechanicalEfficiency() * internalPowerW / omega
                : spec.mechanicalEfficiency() * stallWork * 2 / spec.nominalOmega();
        double friction = x > 1e-3 ? windage / omega : 0;
        omega = Math.max(0, omega + dt * (steamTorque - friction - loadTorque) / spec.inertiaKgM2());
        if (omega >= OVERSPEED_TRIP * spec.nominalOmega()) tripped = true;
        // регулятор: пропорциональный со статизмом, сервомотор — инерционное звено
        double target = stopValveOpen && !tripped ? clamp((noLoadOmega - omega) / (spec.droop() * spec.nominalOmega())) : 0;
        valve += (target - valve) * (1 - Math.exp(-dt / spec.servoS()));
    }

    /** Удельная работа срабатывания p₁, h₁ → p₂ с внутренним КПД η и поправкой Баумана на влажность, Дж/кг. */
    static double specificWork(WaterProperties w, double p1, double h1, double p2, double eta, ThermoState scratch) {
        if (!(p1 > p2) || eta <= 0) return 0;
        double s1 = w.entropyPH(p1, h1);
        double dhs = Math.max(0, h1 - w.enthalpyPS(p2, s1));
        double dh = eta * dhs;
        w.stateFromPressureEnthalpy(p2, h1 - dh, scratch);
        double y = scratch.quality >= 0 && scratch.quality < 1 ? 1 - scratch.quality : 0;
        if (y > 0) dh = eta * (1 - y / 2) * dhs;
        return dh;
    }

    /** Открытие регулирующего клапана (позиция для клапана в гидросети). */
    public double valve() {
        return valve;
    }

    public double omega() {
        return omega;
    }

    public void setOmega(double w) {
        omega = Math.max(0, w);
    }

    public double steamFlowKgS() {
        return flow;
    }

    public double shaftPowerW() {
        return shaftPowerW;
    }

    public boolean tripped() {
        return tripped;
    }

    /** Взвести автомат безопасности — только на оборотах ниже номинальных. */
    public boolean resetTrip() {
        if (omega > spec.nominalOmega()) return false;
        tripped = false;
        return true;
    }

    /** Синхронизатор: обороты холостого хода регулятора, рад/с (±10 % от номинальных с учётом статизма). */
    public void setNoLoadOmega(double w) {
        double lo = spec.nominalOmega() * 0.9, hi = spec.nominalOmega() * (1 + spec.droop()) * 1.1;
        noLoadOmega = Math.max(lo, Math.min(hi, w));
    }

    public double noLoadOmega() {
        return noLoadOmega;
    }

    public Spec spec() {
        return spec;
    }

    /** Мощность на валу при номинальных оборотах и открытом клапане при давлении {@code supplyPa}, Вт. */
    public double capacityW(double supplyPa, double supplyEnthalpy, double backPa) {
        if (!(supplyPa > backPa)) return 0;
        water.stateFromPressureEnthalpy(supplyPa, supplyEnthalpy, scratch);
        double m = spec.stodolaK() * Math.sqrt(supplyPa * scratch.density * (1 - sq(backPa / supplyPa)));
        return Math.max(0, spec.mechanicalEfficiency() * m * specificWork(water, supplyPa, supplyEnthalpy, backPa,
                spec.internalEfficiency(), scratch) - WINDAGE * spec.ratedPowerW());
    }

    public void save(StateWriter out) {
        out.putDouble("omega", omega);
        out.putDouble("valve", valve);
        out.putDouble("no_load_omega", noLoadOmega);
        out.putBoolean("tripped", tripped);
    }

    public void load(StateReader in) {
        omega = in.getDouble("omega");
        valve = in.getDouble("valve");
        noLoadOmega = in.getDouble("no_load_omega");
        tripped = in.getBoolean("tripped");
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double sq(double v) {
        return v * v;
    }
}
