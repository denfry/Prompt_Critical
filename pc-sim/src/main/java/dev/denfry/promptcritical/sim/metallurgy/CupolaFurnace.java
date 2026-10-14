package dev.denfry.promptcritical.sim.metallurgy;

/**
 * Вагранка {@code cupola_furnace} (08 §8.9, I0): шахта с колошей чугуна (или лома) и кокса 10–14 %, дутьё
 * вентилятором Create. Лумпед-модель SLOW:
 * <ul>
 *   <li>кокс сгорает дутьём: ṁ_к = V̇_возд/v_в, v_в = 6,1 нм³/кг — C 86 %, CO₂/(CO + CO₂) = 0,6 (типично для
 *       вагранки);</li>
 *   <li>чугун плавится в пропорции колоши: ṁ_ч = ṁ_к/k_к, k_к = кокс/металл в шахте;</li>
 *   <li>тепло на кг металла h = k_к·Q_эф, Q_эф = 10,3·(0,12/k_к)^0,6 МДж/кг кокса — убывает с расходом кокса (больше
 *       CO) [BALANCE: 12 % кокса → 1450 °C, 10 % → 1345 °C, 14 % → 1545 °C]; если h не хватает на плавление —
 *       плавится доля, металл у ликвидуса (холодный чугун).</li>
 * </ul>
 * Горн копит чугун и остывает с τ = 10 ч — ≈ 2,4 К/мин у 1450 °C, порядок остывания ковша [допущение]. Колошниковый газ — CO ({@code TOXIC_CO}).
 */
public final class CupolaFurnace {
    /** Воздух на кг кокса, нм³. */
    public static final double AIR_PER_KG_COKE = 6.1;
    /** Углерод кокса и доля C в CO₂. */
    public static final double COKE_CARBON = 0.86, CO2_SHARE = 0.6;
    /** Q_эф при k_к = 0,12, Дж/кг кокса, и показатель убывания. */
    public static final double Q_EFF_REF = 10.3e6, K_REF = 0.12, Q_EFF_EXP = 0.6;
    /** Остывание горна, с. */
    public static final double WELL_TAU_S = 36_000;

    private final double wellCapacityKg;
    private double stackIron, stackCoke, well, wellH, coRate, meltRate;

    /** @param wellCapacityKg ёмкость горна, кг; сверх неё металл не плавится (вагранку надо выпускать) */
    public CupolaFurnace(double wellCapacityKg) {
        if (!(wellCapacityKg > 0)) throw new IllegalArgumentException("wellCapacityKg");
        this.wellCapacityKg = wellCapacityKg;
    }

    /** Загрузить колошу: металл и кокс, кг. */
    public void charge(double ironKg, double cokeKg) {
        if (!(ironKg >= 0 && cokeKg >= 0)) throw new IllegalArgumentException("charge");
        stackIron += ironKg;
        stackCoke += cokeKg;
    }

    /** Тепло на кг металла при расходе кокса k_к, Дж/кг. */
    public static double heatPerKgIron(double cokeRatio) {
        return cokeRatio * Q_EFF_REF * Math.pow(K_REF / cokeRatio, Q_EFF_EXP);
    }

    /** Температура выпуска при расходе кокса k_к, К. */
    public static double tapTemperatureK(double cokeRatio) {
        return CastAlloy.GRAY_IRON.temperature(heatPerKgIron(cokeRatio));
    }

    /**
     * Шаг SLOW.
     *
     * @param dt       шаг, с физ.
     * @param airNm3S  дутьё, нм³/с
     */
    public void stepSlow(double dt, double airNm3S) {
        CastAlloy fe = CastAlloy.GRAY_IRON;
        // дутьё жжёт кокс и тогда, когда металл не плавится (горн полон или колоша без металла)
        double coke = Math.min(stackCoke, Math.max(0, airNm3S) / AIR_PER_KG_COKE * dt);
        meltRate = 0;
        if (coke > 0 && stackIron > 0) {
            double k = stackCoke / stackIron;
            double h = heatPerKgIron(k);
            double iron = Math.min(stackIron, coke / k) * fe.liquidFraction(h);
            iron = Math.min(iron, Math.max(0, wellCapacityKg - well));
            stackIron -= iron;
            if (iron > 0) {
                wellH = (wellH * well + Math.max(h, fe.enthalpy(fe.liquidusK) + fe.latentHeat) * iron) / (well + iron);
                well += iron;
            }
            meltRate = iron / dt;
        }
        stackCoke -= coke;
        coRate = coke * COKE_CARBON / 0.012 * (1 - CO2_SHARE) * 0.022414 / dt;
        if (well > 0) {
            double t = fe.temperature(wellH);
            double tNew = 293 + (t - 293) * Math.exp(-dt / WELL_TAU_S);
            wellH -= (t - tNew) * (t > fe.liquidusK ? fe.cLiquid : fe.cSolid);
        }
    }

    /** Выпуск до {@code kg} жидкого чугуна; возвращает выпущенное, кг. */
    public double tap(double kg) {
        CastAlloy fe = CastAlloy.GRAY_IRON;
        if (fe.liquidFraction(wellH) < 1) return 0;
        double out = Math.min(well, Math.max(0, kg));
        well -= out;
        if (well <= 0) wellH = 0;
        return out;
    }

    /** Температура металла в горне, К. */
    public double wellTemperatureK() {
        return well > 0 ? CastAlloy.GRAY_IRON.temperature(wellH) : 293;
    }

    /** Перегрев над ликвидусом, К. */
    public double superheatK() {
        return wellTemperatureK() - CastAlloy.GRAY_IRON.liquidusK;
    }

    public double wellKg() {
        return well;
    }

    public double stackIronKg() {
        return stackIron;
    }

    public double stackCokeKg() {
        return stackCoke;
    }

    /** Производительность последнего шага, кг/с. */
    public double meltRateKgS() {
        return meltRate;
    }

    /** CO в колошниковом газе, нм³/с. */
    public double coNm3S() {
        return coRate;
    }
}
