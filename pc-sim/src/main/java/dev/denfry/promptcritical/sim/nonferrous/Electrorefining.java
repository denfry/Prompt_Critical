package dev.denfry.promptcritical.sim.nonferrous;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Секция ванн электрорафинирования {@code electrorefining_cell} (08 §8.8.3–8.8.4), {@value #CELLS} ванн
 * последовательно на шине постоянного тока. Электротехнология — реальные секунды, Фарадей без ×72 (08 §8.0.3).
 *
 * <ul>
 *   <li>Медь: CuSO₄ + H₂SO₄, 55–65 °C, 250–350 А/м², 0,25–0,35 В (§8.8.3); U = 0,05 + 0,25·j/300 В [BALANCE в пределах
 *   §8.8.3], выход по току 0,95: E = 2FU/(M·η) = 0,266 кВт·ч/кг при 0,3 В (T8.15). Катоды 99,99 %, в шлам —
 *   платиноиды {@value #PGM_PER_CU} кг на кг Cu [BALANCE: медная линия беднее никелевой].</li>
 *   <li>Никель: растворимые сульфидные (штейновые) аноды Ni₃S₂, 3,0–3,5 В (§8.8.4); U = 2,75 + 0,5·I/I₀ [BALANCE: 3,0 В на половинном токе, 3,5 В на полуторном],
 *   выход по току 0,95 [BALANCE]. В катод — {@value #NI_RECOVERY} никеля (§8.8.4: 90 %); кобальт уходит в раствор и
 *   после очистки — электроэкстракцией в {@code cobalt_ingot} с извлечением {@value #CO_RECOVERY} (§8.8.4: 50–65 %,
 *   экстракционная очистка в {@code mixer_settler} свёрнута в ванну); платиноиды — в шлам целиком.</li>
 * </ul>
 */
public final class Electrorefining {
    public enum Mode { COPPER, NICKEL }

    public static final int CELLS = 20;
    public static final double F = 96485.33;
    /** Площадь катодов ванны, м²: 20 кА при 285 А/м². */
    public static final double CATHODE_M2 = 70;
    public static final double NOMINAL_A = 20e3, MIN_A = 2e3, MAX_A = 30e3;
    public static final double CU_EFFICIENCY = 0.95, NI_EFFICIENCY = 0.95, NI_RECOVERY = 0.90, CO_RECOVERY = 0.575;
    public static final double PGM_PER_CU = 0.05e-3;

    private Mode mode = Mode.COPPER;
    private double currentSetA = NOMINAL_A;
    private double lastAmps, lastVoltage, lastMetalKgS;
    private double totalMetalKg, totalDcJ;

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode m) {
        mode = m;
    }

    public double currentSetA() {
        return currentSetA;
    }

    public void setCurrentSetA(double a) {
        currentSetA = Math.clamp(a, MIN_A, MAX_A);
    }

    public static double molarMass(Mode m) {
        return m == Mode.COPPER ? SulfideSmelting.M_CU : SulfideSmelting.M_NI;
    }

    public static double efficiency(Mode m) {
        return m == Mode.COPPER ? CU_EFFICIENCY : NI_EFFICIENCY;
    }

    /** Напряжение одной ванны при токе {@code amps}, В. */
    public static double cellVoltage(Mode m, double amps) {
        if (amps <= 0) return 0;
        double j = amps / CATHODE_M2;
        return m == Mode.COPPER ? 0.05 + 0.25 * j / 300 : 2.75 + 0.5 * amps / NOMINAL_A;
    }

    /** Удельный расход постоянного тока, Дж/кг. */
    public static double specificEnergyJPerKg(Mode m, double volts, double efficiency) {
        return 2 * F * volts / (molarMass(m) * efficiency);
    }

    /** Мощность секции при токе {@code amps}, Вт. */
    public double dcDemandW(double amps) {
        return amps * CELLS * cellVoltage(mode, amps);
    }

    /**
     * Шаг на {@code dtS} реальных секунд: ток серии, металл в анодах (кг). Возвращает осаждённый металл, кг; анодного
     * металла уходит столько же (никель — с поправкой на извлечение {@link #NI_RECOVERY}).
     */
    public double step(double dtS, double amps, double anodeMetalKg) {
        double a = Math.max(0, amps);
        double rate = CELLS * efficiency(mode) * a * molarMass(mode) / (2 * F);
        double perAnode = mode == Mode.NICKEL ? NI_RECOVERY : 1;
        double kg = Math.min(rate * dtS, Math.max(0, anodeMetalKg) * perAnode);
        if (kg < rate * dtS && rate > 0) a *= kg / (rate * dtS);
        lastAmps = a;
        lastVoltage = cellVoltage(mode, a);
        lastMetalKgS = dtS > 0 ? kg / dtS : 0;
        totalMetalKg += kg;
        totalDcJ += a * CELLS * lastVoltage * dtS;
        return kg;
    }

    /** Анодный металл, израсходованный на {@code depositedKg} катодного, кг. */
    public double anodeUse(double depositedKg) {
        return mode == Mode.NICKEL ? depositedKg / NI_RECOVERY : depositedKg;
    }

    public double lastAmps() {
        return lastAmps;
    }

    public double lastVoltage() {
        return lastVoltage;
    }

    public double lastMetalKgS() {
        return lastMetalKgS;
    }

    public double totalMetalKg() {
        return totalMetalKg;
    }

    public double specificKwhPerKg() {
        return totalMetalKg > 0 ? totalDcJ / 3.6e6 / totalMetalKg : Double.NaN;
    }

    public void save(StateWriter out) {
        out.putString("mode", mode.name());
        out.putDouble("set", currentSetA);
        out.putDouble("made", totalMetalKg);
        out.putDouble("dcj", totalDcJ);
    }

    public void load(StateReader in) {
        mode = in.has("mode") && "NICKEL".equals(in.getString("mode")) ? Mode.NICKEL : Mode.COPPER;
        setCurrentSetA(in.optDouble("set", NOMINAL_A));
        totalMetalKg = in.optDouble("made", 0);
        totalDcJ = in.optDouble("dcj", 0);
    }
}
