package dev.denfry.promptcritical.sim.ops;

/**
 * Пожар и его тушение (13 §13.5): классы пожаров ГОСТ 27331 с классом E, огнетушащие вещества и что ими можно
 * тушить, потолочная струя Альперта и тепловой элемент с индексом инерционности RTI (тепловые извещатели и колбы
 * спринклеров), рост пожара класса A по t², тушение пеной класса B по интенсивности подачи раствора.
 */
public final class FireSuppression {
    /** Класс пожара (§13.5.1). */
    public enum FireClass { A, B, C, D, E }

    /** Огнетушащее вещество (§13.5.3, огнетушители и системы). */
    public enum Agent { WATER, FOAM, POWDER_ABCE, CO2, POWDER_D }

    /** Чем кончится подача вещества на пожар данного класса. */
    public enum Effect {
        /** Тушит. */
        EFFECTIVE,
        /** Не тушит (пожар продолжается). */
        INEFFECTIVE,
        /** Опасно: вода на металл — реакция с водородом, вода и пена на электроустановку под напряжением — удар током. */
        DANGEROUS
    }

    /** Быстрый пожар: Q = α t², α = 0,047 кВт/с² (NFPA 72 [сверить]; {@code fire.growth_alpha_kw_s2}). */
    public static final double GROWTH_ALPHA_KW_S2 = 0.047;
    /** Минимальная интенсивность раствора пены, л/(мин·м²) ({@code fire.foam_min_rate_lpm_m2}). */
    public static final double FOAM_MIN_RATE = 4.1;
    /** Время тушения пеной при минимальной интенсивности, с ({@code fire.foam_ext_time_s}). */
    public static final double FOAM_EXT_TIME_S = 300;
    /** Спринклеры тушат класс A, если нужная интенсивность набрана за столько секунд. */
    public static final double SPRINKLER_CONTROL_S = 120;
    /** Вода без пены на класс B: площадь пролива растёт на 20 % в минуту. */
    public static final double WATER_ON_B_GROWTH_PER_S = 0.20 / 60;
    /** Порог теплового извещателя по скорости роста, К/с (10 К/мин). */
    public static final double RATE_OF_RISE_K_S = 10.0 / 60;

    private FireSuppression() {
    }

    /** Таблица §13.5.1: что даёт вещество на пожаре класса; {@code energized} — электроустановка под напряжением. */
    public static Effect effect(FireClass fire, Agent agent, boolean energized) {
        if (energized && (agent == Agent.WATER || agent == Agent.FOAM)) return Effect.DANGEROUS;
        return switch (fire) {
            case A -> agent == Agent.POWDER_D ? Effect.INEFFECTIVE : agent == Agent.CO2 ? Effect.INEFFECTIVE : Effect.EFFECTIVE;
            case B -> agent == Agent.FOAM || agent == Agent.POWDER_ABCE || agent == Agent.CO2 ? Effect.EFFECTIVE : Effect.INEFFECTIVE;
            // газ: сначала перекрыть источник; порошок сбивает пламя, но без отсечки облако воспламенится снова
            case C -> agent == Agent.POWDER_ABCE ? Effect.EFFECTIVE : Effect.INEFFECTIVE;
            case D -> agent == Agent.POWDER_D ? Effect.EFFECTIVE : agent == Agent.WATER || agent == Agent.FOAM ? Effect.DANGEROUS
                    : Effect.INEFFECTIVE;
            case E -> agent == Agent.POWDER_ABCE || agent == Agent.CO2 ? Effect.EFFECTIVE
                    : energized ? Effect.DANGEROUS : Effect.EFFECTIVE; // после отключения — как A/B
        };
    }

    /** Перегрев потолочной струи над окружающим воздухом, К: ΔT = 16,9 Q^{2/3} / H^{5/3} (Альперт, r/H ≤ 0,18). */
    public static double ceilingJetRiseK(double qKw, double heightM) {
        if (qKw <= 0) return 0;
        return 16.9 * Math.pow(qKw, 2.0 / 3) / Math.pow(Math.max(0.5, heightM), 5.0 / 3);
    }

    /** Скорость потолочной струи, м/с: u = 0,96 (Q/H)^{1/3}. */
    public static double ceilingJetVelocity(double qKw, double heightM) {
        if (qKw <= 0) return 0;
        return 0.96 * Math.cbrt(qKw / Math.max(0.5, heightM));
    }

    /** Мощность растущего пожара класса A через {@code t} секунд, кВт, не больше {@code fuelLimitKw}. */
    public static double growthKw(double t, double fuelLimitKw) {
        return Math.min(fuelLimitKw, GROWTH_ALPHA_KW_S2 * t * t);
    }

    /** Мощность пожара пролива, кВт: Q = χ m″ ΔH_c A (χ = 0,7). */
    public static double poolFireKw(double burnRateKgM2S, double heatOfCombustionJKg, double areaM2) {
        return 0.7 * burnRateKgM2S * heatOfCombustionJKg * areaM2 / 1e3;
    }

    /**
     * Время тушения пеной при интенсивности {@code rateLpmM2}, с: t = t₀ r_min / r; ниже r_min пена разрушается
     * пламенем — бесконечность.
     */
    public static double foamExtinctionS(double rateLpmM2) {
        return rateLpmM2 < FOAM_MIN_RATE ? Double.POSITIVE_INFINITY : FOAM_EXT_TIME_S * FOAM_MIN_RATE / rateLpmM2;
    }

    /**
     * Чувствительный элемент с RTI — колба спринклера или тепловой извещатель: dT_e/dt = √u / RTI · (T_jet − T_e).
     * Срабатывает при T_e ≥ T_act, извещатель — ещё и по скорости роста 10 К/мин.
     */
    public static final class Element {
        private final double rti, activationK;
        private final boolean rateOfRise;
        private double te, lastTe;
        private boolean activated;

        /**
         * @param rti         индекс инерционности, (м·с)^½ (стандартный спринклер — 100)
         * @param activationK температура срабатывания, К (колба 68 °C — 341,15 K)
         * @param rateOfRise  извещатель с каналом скорости роста
         */
        public Element(double rti, double activationK, boolean rateOfRise, double ambientK) {
            if (!(rti > 0)) throw new IllegalArgumentException("rti");
            this.rti = rti;
            this.activationK = activationK;
            this.rateOfRise = rateOfRise;
            this.te = this.lastTe = ambientK;
        }

        /** Шаг при пожаре мощностью {@code qKw} под потолком высотой {@code heightM} над очагом, воздух {@code ambientK}. */
        public boolean step(double dt, double qKw, double heightM, double ambientK) {
            double tJet = ambientK + ceilingJetRiseK(qKw, heightM);
            double u = Math.max(0.05, ceilingJetVelocity(qKw, heightM));
            lastTe = te;
            double k = Math.sqrt(u) / rti;
            te = tJet + (te - tJet) * Math.exp(-k * dt);
            if (te >= activationK || rateOfRise && dt > 0 && (te - lastTe) / dt > RATE_OF_RISE_K_S) activated = true;
            return activated;
        }

        public double temperatureK() {
            return te;
        }

        public boolean activated() {
            return activated;
        }

        /** Сброс извещателя (колба спринклера одноразовая — её не сбрасывают, а меняют). */
        public void reset(double ambientK) {
            activated = false;
            te = lastTe = ambientK;
        }

        public double activationK() {
            return activationK;
        }
    }
}
