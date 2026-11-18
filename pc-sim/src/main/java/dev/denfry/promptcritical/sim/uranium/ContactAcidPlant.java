package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сернокислотная установка на колчедане (Амелин А. Г., «Технология серной кислоты», 1983; Perry, 8th ed., разд. 23):
 * обжиг пирита в печи КС 4 FeS₂ + 11 O₂ → 2 Fe₂O₃ + 8 SO₂ (98 % серы в газ, остальное — в огарке), очистка газа,
 * окисление SO₂ + ½ O₂ ⇌ SO₃ на ванадиевом катализаторе в четырёх адиабатических слоях с промежуточным охлаждением,
 * абсорбция SO₃ в 98,3 %-й кислоте. Газ после печи — 8 % SO₂ и 11 % O₂ (разбавление воздухом).
 *
 * <p>Константа равновесия lg Kp = 4956/T − 4,678 (атм^−½) — аппроксимация данных Боденштейна и Поля, как у Амелина.
 * Степень превращения ограничена равновесием на выходе последнего слоя: при 420–440 °C — 97–98,5 % (схема
 * одинарного контактирования, 1950-е; двойное с промежуточной абсорбцией — 99,5 %). Ниже ≈ 400 °C ванадиевая масса
 * «гаснет». Непревращённый SO₂ уходит в трубу.
 */
public final class ContactAcidPlant {
    public static final double Y_SO2 = 0.08, Y_O2 = 0.11, S_TO_GAS = 0.98, ABSORPTION = 0.999;
    public static final double M_S = 32.06, M_SO2 = 64.066, M_H2SO4 = 98.079, M_H2O = 18.015, ACID_STRENGTH = 0.98;
    public static final double MIN_K = 633.15, MAX_K = 783.15;
    /** Пиритная сера на установку, кг/с [BALANCE]. */
    public static final double CAPACITY_S_KG_S = 0.05;

    private double lastBedK = 703.15;
    private boolean on = true;
    private double conversion, acidKgS, waterKgS, so2StackKgS, sKgS;

    public double lastBedK() {
        return lastBedK;
    }

    public void setLastBedK(double k) {
        lastBedK = Math.clamp(k, MIN_K, MAX_K);
    }

    public boolean on() {
        return on;
    }

    public void setOn(boolean on) {
        this.on = on;
    }

    /** Константа равновесия, атм^−½. */
    public static double kp(double tempK) {
        return Math.pow(10, 4956 / tempK - 4.678);
    }

    /** Равновесная степень превращения SO₂ при давлении 1 атм для газа {@link #Y_SO2}/{@link #Y_O2}. */
    public static double equilibrium(double tempK) {
        double k = kp(tempK), lo = 0, hi = 1 - 1e-12;
        for (int i = 0; i < 80; i++) {
            double x = (lo + hi) / 2;
            double total = 1 - 0.5 * Y_SO2 * x, o2 = (Y_O2 - 0.5 * Y_SO2 * x) / total;
            double q = x / (1 - x) / Math.sqrt(Math.max(1e-12, o2));
            if (q < k) lo = x;
            else hi = x;
        }
        return lo;
    }

    /** Активность ванадиевой массы: ниже 380 °C реакция не идёт, выше 410 °C — полная. */
    public static double activity(double tempK) {
        return Math.clamp((tempK - 653.15) / 30, 0, 1);
    }

    /** Степень превращения при температуре последнего слоя. */
    public static double conversion(double lastBedK) {
        return equilibrium(lastBedK) * activity(lastBedK);
    }

    /**
     * Шаг.
     *
     * @param sulfurKgS сера в колчедане, доступная на шаг, кг/с
     */
    public void step(double sulfurKgS) {
        sKgS = on ? Math.clamp(sulfurKgS, 0, CAPACITY_S_KG_S) : 0;
        conversion = conversion(lastBedK);
        double so2Mol = sKgS * S_TO_GAS / (M_S / 1000);
        double so3Mol = so2Mol * conversion * ABSORPTION;
        acidKgS = so3Mol * M_H2SO4 / 1000 / ACID_STRENGTH;
        waterKgS = so3Mol * M_H2O / 1000 + acidKgS * (1 - ACID_STRENGTH);
        so2StackKgS = so2Mol * (1 - conversion) * M_SO2 / 1000;
    }

    public double sulfurKgS() {
        return sKgS;
    }

    public double conversion() {
        return conversion;
    }

    /** Кислота 98 %, кг/с. */
    public double acidKgS() {
        return acidKgS;
    }

    public double waterKgS() {
        return waterKgS;
    }

    /** SO₂ в трубу, кг/с. */
    public double so2StackKgS() {
        return so2StackKgS;
    }

    public void save(StateWriter out) {
        out.putDouble("t", lastBedK);
        out.putBoolean("on", on);
    }

    public void load(StateReader in) {
        setLastBedK(in.optDouble("t", 703.15));
        on = in.optInt("on", 1) != 0;
    }
}
