package dev.denfry.promptcritical.sim.fuel;

/**
 * Таблетки UO₂: прессование порошка, спекание в водороде, бесцентровое шлифование и приёмка.
 *
 * <p><b>Прессование</b> — уравнение Хеккеля ln(1/(1 − D)) = K·P + A, для порошка UO₂ из АДУ K = 9,3·10⁻⁴ МПа⁻¹,
 * A = 0,449: 300 МПа дают 51–52 % теоретической плотности (сырая таблетка 5,5–5,9 г/см³; Olander, «Fundamental Aspects
 * of Nuclear Reactor Fuel Elements», 1976, гл. 14; Assmann et al., J. Nucl. Mater. 98 (1981) 216). Выше 450 МПа
 * растут расслоения и сколы торцов, ниже 150 МПа сырая таблетка рассыпается.
 *
 * <p><b>Спекание</b> — в сухом водороде при 1600–1750 °C, 2–8 ч. Уплотнение: D = D_с + (D_макс − D_с)·(1 − exp(−(k·t)^0,3)),
 * k = k₀·exp(−Q/RT), Q = 300 кДж/моль (зернограничная диффузия урана), D_макс = 98,5 % (закрытая пористость). Калибровка:
 * 1700 °C, 4 ч, сырая 52 % → 95,5 % (Olander, табл. 14.1; спецификация ТВЭЛ 10,4–10,7 г/см³). Рост зерна —
 * d² − d₀² = K_g·t·exp(−Q_g/RT), Q_g = 400 кДж/моль, d₀ = 1 мкм: при 1700 °C за 4 ч — около 10 мкм. Водород держит
 * O/U = 2,00; если его мало, таблетка недовосстановлена: O/U растёт до 2,03 без водорода (допуск — до 2,02).
 *
 * <p><b>Шлифование</b> — бесцентровое, мокрое: снимает 1,5 % массы в шлам, шлам возвращают в передел.
 */
public final class PelletProcess {
    public static final double HECKEL_K = 9.3e-4, HECKEL_A = 0.449, D_MAX = 0.985, Q_SINTER = 300e3, Q_GRAIN = 400e3,
            REF_K = 1973.15, K_REF_PER_H = 7.2, M_EXP = 0.3, GRAIN_K_REF = 24.75, GRAIN_D0 = 1.0, R = 8.314462618;
    /** Водород на кг UO₂ при спекании (продувка печи), нм³. */
    public static final double H2_NM3_PER_KG = 0.5;
    public static final double GRINDING_LOSS = 0.015, MAX_O_TO_U = 2.02;

    private PelletProcess() {
    }

    /** Сырая плотность после прессования при {@code pressureMpa}, доля теоретической. */
    public static double greenDensity(double pressureMpa) {
        return 1 - Math.exp(-(HECKEL_K * Math.max(0, pressureMpa) + HECKEL_A));
    }

    /** Доля брака прессования (расслоения, сколы, рассыпавшиеся таблетки). */
    public static double pressingDefects(double pressureMpa) {
        double hi = Math.max(0, (pressureMpa - 450) / 200), lo = Math.max(0, (150 - pressureMpa) / 100);
        return Math.min(1, 0.01 + 0.5 * hi * hi + 0.3 * lo * lo);
    }

    /** Результат спекания: плотность (доля теоретической), размер зерна (мкм), O/U. */
    public record Sintered(double density, double grainUm, double oxygenToU) {
    }

    public static Sintered sinter(double green, double tK, double hours, double hydrogenShare) {
        double k = K_REF_PER_H * Math.exp(-Q_SINTER / R * (1 / tK - 1 / REF_K));
        double x = Math.pow(Math.max(0, k * hours), M_EXP);
        double d = green + (D_MAX - green) * (1 - Math.exp(-x));
        double kg = GRAIN_K_REF * Math.exp(-Q_GRAIN / R * (1 / tK - 1 / REF_K));
        double grain = Math.sqrt(GRAIN_D0 * GRAIN_D0 + kg * Math.max(0, hours));
        double ou = 2.0 + 0.03 * (1 - Math.max(0, Math.min(1, hydrogenShare)));
        return new Sintered(d, grain, ou);
    }

    /** Приёмка спечённой таблетки по плотности конструкции и O/U. */
    public static boolean accepted(FuelDesign design, Sintered s) {
        return s.oxygenToU() <= MAX_O_TO_U + 1e-12 && design.densityInSpec(s.density());
    }
}
