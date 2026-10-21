package dev.denfry.promptcritical.sim.mining;

/**
 * Водоотлив (08 §8.2.9): водосборник — ёмкость в SLOW (полная гидросеть 01 не нужна), насос ЦНС на валу Create.
 * Приток — водообильность (м³ на тонну добычи) плюс фильтрация. Вода сверх ёмкости водосборника затапливает
 * нижний горизонт ({@link #floodedM3}); откачивается она тем же насосом.
 *
 * <p>Насос: H(Q, n) = H₀(n/n_ном)² − k·Q², H₀ = 1,25·H_ном, k = 0,25·H_ном/Q_ном² [допущение: типовая
 * пологая характеристика ЦНС]; рабочая точка — на статическом напоре ствола H_ст: Q = √((H₀(n/n_ном)² − H_ст)/k).
 * Мощность на валу N = ρgQH/η (100 м³/ч на 150 м при 0,7 → 58 кВт).
 */
public final class MineSump {
    public static final double RHO = 1000, G = 9.81;
    /** Водосборник должен вмещать не меньше 4 ч притока (ПБ). */
    public static final double MIN_CAPACITY_HOURS = 4;

    /**
     * @param ratedFlowM3s Q_ном, м³/с
     * @param ratedHeadM   H_ном, м
     * @param efficiency   η
     * @param ratedRpm     n_ном
     */
    public record PumpSpec(double ratedFlowM3s, double ratedHeadM, double efficiency, double ratedRpm) {
        public PumpSpec {
            if (!(ratedFlowM3s > 0 && ratedHeadM > 0 && efficiency > 0 && efficiency <= 1 && ratedRpm > 0))
                throw new IllegalArgumentException("pump");
        }

        /** Подача на статический напор при частоте n, м³/с. */
        public double flowAt(double rpm, double staticHeadM) {
            return flowAt(rpm, staticHeadM, 1);
        }

        /**
         * Подача, когда вся характеристика H(Q) сжата в {@code headFactor} раз (13 §13.6.6–13.6.7): обратное
         * вращение 0,4, эрозия колеса 1 − e.
         */
        public double flowAt(double rpm, double staticHeadM, double headFactor) {
            if (!(headFactor > 0)) return 0;
            double r = rpm / ratedRpm;
            double h0 = 1.25 * ratedHeadM * r * r;
            double hs = staticHeadM / headFactor;
            if (h0 <= hs) return 0;
            double k = 0.25 * ratedHeadM / (ratedFlowM3s * ratedFlowM3s);
            return Math.sqrt((h0 - hs) / k);
        }

        /** Напор при подаче Q и частоте n, м (≥ 0). */
        public double headAt(double rpm, double flowM3s, double headFactor) {
            double r = rpm / ratedRpm;
            double k = 0.25 * ratedHeadM / (ratedFlowM3s * ratedFlowM3s);
            return Math.max(0, headFactor * (1.25 * ratedHeadM * r * r - k * flowM3s * flowM3s));
        }
    }

    private final double capacityM3, staticHeadM;
    private final PumpSpec pump;
    private double volume, pumped, shaftPower;

    /**
     * Водосборник без своего насоса: подачу насосов шахты (у каждого свой напор) складывает владелец.
     *
     * @param capacityM3 ёмкость водосборника, м³
     */
    public MineSump(double capacityM3) {
        if (!(capacityM3 > 0)) throw new IllegalArgumentException("sump");
        this.capacityM3 = capacityM3;
        this.staticHeadM = 0;
        this.pump = null;
    }

    /**
     * @param capacityM3  ёмкость водосборника, м³
     * @param staticHeadM высота подъёма воды (глубина горизонта), м
     * @param pump        насос
     */
    public MineSump(double capacityM3, double staticHeadM, PumpSpec pump) {
        if (!(capacityM3 > 0 && staticHeadM > 0)) throw new IllegalArgumentException("sump");
        this.capacityM3 = capacityM3;
        this.staticHeadM = staticHeadM;
        this.pump = pump;
    }

    /** Приток: водообильность × добыча + фильтрация, м³/с. */
    public static double inflowM3s(double waterYieldM3PerT, double productionTps, double filtrationM3s) {
        return waterYieldM3PerT * productionTps + filtrationM3s;
    }

    /** Мощность на валу насоса N = ρgQH/η, Вт. */
    public static double pumpPowerW(double flowM3s, double headM, double efficiency) {
        return RHO * G * flowM3s * headM / efficiency;
    }

    /** Водосборник отвечает норме ≥ 4 ч притока. */
    public boolean capacityOk(double inflowM3s) {
        return capacityM3 >= inflowM3s * MIN_CAPACITY_HOURS * 3600;
    }

    /**
     * Шаг SLOW.
     *
     * @param dt        шаг, с физ.
     * @param inflowM3s приток, м³/с
     * @param pumpRpm   частота вала насоса (0 — стоит)
     */
    public void stepSlow(double dt, double inflowM3s, double pumpRpm) {
        stepSlowWithCapacity(dt, inflowM3s, pump.flowAt(Math.abs(pumpRpm), staticHeadM));
        shaftPower = pumped > 0 ? pumpPowerW(pumped, staticHeadM, pump.efficiency()) : 0;
    }

    /**
     * Шаг SLOW с готовой подачей насосов (сумма их рабочих точек на своих напорах).
     *
     * @return откачано, м³/с — не больше подачи и того, что есть в водосборнике с притоком
     */
    public double stepSlowWithCapacity(double dt, double inflowM3s, double pumpCapacityM3s) {
        double q = Math.min(Math.max(0, pumpCapacityM3s), volume / dt + inflowM3s);
        volume = Math.max(0, volume + (inflowM3s - q) * dt);
        pumped = q;
        shaftPower = 0;
        return q;
    }

    /** Восстановить объём воды после загрузки, м³. */
    public void restore(double volumeM3) {
        volume = Math.max(0, volumeM3);
    }

    /** Вода в водосборнике и затопленных выработках, м³. */
    public double volumeM3() {
        return volume;
    }

    /** Уровень водосборника, доля ёмкости (может быть &gt; 1 — горизонт топит). */
    public double levelFrac() {
        return volume / capacityM3;
    }

    /** Затоплено сверх ёмкости водосборника, м³. */
    public double floodedM3() {
        return Math.max(0, volume - capacityM3);
    }

    /** Подача насоса на последнем шаге, м³/с. */
    public double pumpedM3s() {
        return pumped;
    }

    /** Мощность на валу насоса, Вт (→ стресс Create). */
    public double shaftPowerW() {
        return shaftPower;
    }

    public double capacityM3() {
        return capacityM3;
    }
}
