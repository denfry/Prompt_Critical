package dev.denfry.promptcritical.sim.battery;

/**
 * Паспорт аккумуляторной батареи из последовательных элементов для модели Tremblay ({@link Battery}).
 *
 * <p>Разрядная кривая элемента — модель Tremblay, Dessaint, Dekkiche («A Generic Battery Model for the Dynamic
 * Simulation of Hybrid Electric Vehicles», IEEE VPPC 2007): E = E₀ − K·Q/(Q − it) + A·exp(−B·it), V = E − R·i, где it —
 * отданный заряд, А·ч. Параметры E₀, K, A, B получаются из трёх точек паспортной кривой (полный заряд, конец
 * экспоненциальной зоны, номинальная зона) по формулам той же работы — {@link #fromCurve}.
 *
 * @param cells              элементов последовательно
 * @param ratedAh            номинальная ёмкость при токе {@code refCurrentA} до конечного напряжения, А·ч
 * @param maxAh              полная ёмкость Q модели (немного больше номинальной), А·ч
 * @param refCurrentA        ток паспортной кривой (режим номинальной ёмкости), А
 * @param e0                 E₀ элемента, В
 * @param k                  K — напряжение поляризации элемента, В
 * @param a                  A — амплитуда экспоненциальной зоны, В
 * @param b                  B — обратная постоянная экспоненциальной зоны, 1/(А·ч)
 * @param cellOhm            внутреннее сопротивление элемента, Ом
 * @param peukert            показатель Пейкерта: доступная ёмкость ∝ (I_ref/I)^(k−1)
 * @param gasVolts           перенапряжение газовыделения элемента в конце заряда, В
 * @param gasExponent        крутизна газовыделения: доля тока на газ = SOC^n
 * @param selfDischargePerDay саморазряд при 20 °C, доля ёмкости в сутки (удваивается на каждые 10 К)
 * @param cycleLife          ресурс, эквивалентных полных циклов до {@code endOfLife} ёмкости
 * @param endOfLife          доля начальной ёмкости в конце ресурса
 * @param massKg             масса батареи, кг
 * @param heatCapacityJK     теплоёмкость батареи, Дж/К
 * @param coolingWK          теплоотдача корпуса, Вт/К
 */
public record BatterySpec(int cells, double ratedAh, double maxAh, double refCurrentA, double e0, double k, double a,
        double b, double cellOhm, double peukert, double gasVolts, double gasExponent, double selfDischargePerDay,
        double cycleLife, double endOfLife, double massKg, double heatCapacityJK, double coolingWK) {

    public BatterySpec {
        if (cells < 1) throw new IllegalArgumentException("cells");
        if (!(ratedAh > 0 && maxAh >= ratedAh && refCurrentA > 0)) throw new IllegalArgumentException("capacity");
        if (!(k > 0 && b > 0 && cellOhm >= 0)) throw new IllegalArgumentException("curve");
        if (!(peukert >= 1)) throw new IllegalArgumentException("peukert");
        if (!(selfDischargePerDay >= 0 && cycleLife > 0 && endOfLife > 0 && endOfLife < 1)) throw new IllegalArgumentException("ageing");
        if (!(heatCapacityJK > 0 && coolingWK > 0)) throw new IllegalArgumentException("thermal");
    }

    /**
     * Параметры Tremblay по трём точкам паспортной кривой элемента при токе {@code currentA} (Tremblay et al., 2007,
     * разд. III): A = V_full − V_exp, B = 3/Q_exp, K = (V_full − V_nom + A·(e^(−B·Q_nom) − 1))·(Q − Q_nom)/Q,
     * E₀ = V_full + K + R·i − A.
     *
     * @return {E₀, K, A, B}
     */
    public static double[] fromCurve(double maxAh, double cellOhm, double currentA, double vFull, double vExp, double qExp,
            double vNom, double qNom) {
        double a = vFull - vExp, b = 3 / qExp;
        double k = (vFull - vNom + a * (Math.exp(-b * qNom) - 1)) * (maxAh - qNom) / maxAh;
        double e0 = vFull + k + cellOhm * currentA - a;
        return new double[] {e0, k, a, b};
    }

    /**
     * Щелочная никель-кадмиевая батарея с ламельными (карманными) электродами — головные светильники СССР
     * с 1930-х (СГГ-1к, СГГ-3: только головные, горение не меньше 10 ч, около 30 лм, масса около 2 кг — БСЭ, ст.
     * «Светильник шахтный»; щелочной элемент: ЭДС ≈ 1,3 В, хорошо переносит перегрузки и глубокий разряд — БСЭ,
     * ст. «Щелочной аккумулятор»).
     *
     * <p>Паспортная кривая элемента при режиме C/5 восстановлена по типичным разрядным кривым ламельных NiCd
     * (Linden, Reddy, «Handbook of Batteries», 3-е изд., 2002, гл. 25): 1,34 В в начале, 1,26 В в конце
     * экспоненциальной зоны (5 % ёмкости), 1,19 В при 80 % ёмкости, 1,0 В — конец разряда; Q = 1,06·C_ном,
     * R = 1,5 мОм·(100 А·ч / C_ном). Пейкерт 1,08, газовыделение и КПД заряда — коэффициент заряда ≈ 1,35 при
     * C/4 (норма для щелочных — 140 %), саморазряд 0,3 % в сутки, ресурс 2000 циклов до 80 % ёмкости — порядок
     * ламельных NiCd у Linden & Reddy [оценка]. Масса — 2 кг на светильник за вычетом фары; теплоёмкость
     * ≈ 1100 Дж/(кг·К), теплоотдача корпуса 0,4 Вт/К [оценка].
     */
    public static BatterySpec nickelCadmiumPocketPlate(int cells, double ratedAh) {
        double q = 1.06 * ratedAh, i = ratedAh / 5, r = 0.0015 * 100 / ratedAh;
        double[] p = fromCurve(q, r, i, 1.34, 1.26, 0.05 * q, 1.19, 0.8 * ratedAh);
        double mass = 1.6 * ratedAh / 10 * cells / 3;
        return new BatterySpec(cells, ratedAh, q, i, p[0], p[1], p[2], p[3], r, 1.08, 0.30, 4, 0.003, 2000, 0.8,
                mass, 1100 * mass, 0.4);
    }

    /** Номинальное напряжение батареи (1,2 В на элемент NiCd), В. */
    public double nominalVolts() {
        return 1.2 * cells;
    }
}
