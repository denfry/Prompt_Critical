package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сорбция урана из продуктивного раствора на сильноосновном анионите (полистирол-дивинилбензол с триметиламмониевыми
 * группами — советский АМ-п, американский Amberlite IRA-400) и десорбция (Скороваров, 1979, гл. 6; Merritt, 1971,
 * гл. 6; Benedict–Pigford–Levi, гл. 5). Анионит берёт уранилсульфатный комплекс UO₂(SO₄)₃⁴⁻; ёмкость до проскока —
 * 40–60 г U на литр смолы. Насыщенную смолу элюируют подкисленным раствором NaCl (1 M NaCl + 0,05–0,1 M H₂SO₄) —
 * в элюате 8–15 г/л U, т. е. концентрирование в 10 раз и очистка от железа и большинства катионов.
 *
 * <p>Две колонны каруселью: пока одна сорбирует, другая элюируется. Колонна сорбирует, пока смола не нагрузится на
 * {@value #ELUTE_AT}, затем уходит на элюирование до {@value #LOAD_AT}, а раствор переключается на вторую. Если вторая
 * ещё не отмыта, раствор идёт мимо смолы — потеря. Кривая проскока: до 85 % нагрузки сорбируется 99,8 %, выше проскок
 * растёт линейно до 70 % на полностью насыщенной смоле; проскочивший уран уходит с маточником сорбции — потеря стадии.
 */
public final class IonExchangeColumn {
    /** Ёмкость смолы, кг U на литр. */
    public static final double CAPACITY_KG_L = 0.050;
    public static final double ELUTE_AT = 0.90, LOAD_AT = 0.05;
    /** Элюирование: кг U в час на литр смолы (≈ 8 объёмов элюента за 8 ч) [BALANCE]. */
    public static final double ELUTION_KG_H_PER_L = CAPACITY_KG_L / 6;
    /** На кг U элюата: раствор NaCl (рассол 310 г/л разбавляется водой), кислота, вода — л; элюат — 10 г/л U. */
    public static final double ELUATE_L_PER_KG = 100, BRINE_L_PER_KG = 19, ACID_L_PER_KG = 0.4, WATER_L_PER_KG = 80.6;

    public enum Mode { LOAD, ELUTE }

    public static final int BEDS = 2;

    private final UraniumLedger ledger = new UraniumLedger();
    private double resinL;
    private final double[] loaded = new double[BEDS];
    private final Mode[] modes = {Mode.LOAD, Mode.ELUTE};
    /** Колонна на сорбции. */
    private int active;
    private double lastLeak;

    public UraniumLedger ledger() {
        return ledger;
    }

    /** Смола во всех колоннах, л. */
    public double resinL() {
        return resinL;
    }

    public void setResinL(double litres) {
        resinL = Math.max(0, litres);
    }

    /** Ёмкость одной колонны, кг U. */
    public double bedCapacityKg() {
        return resinL / BEDS * CAPACITY_KG_L;
    }

    public double capacityKg() {
        return resinL * CAPACITY_KG_L;
    }

    /** Уран на смоле во всех колоннах, кг. */
    public double loadedKg() {
        double sum = 0;
        for (double l : loaded) sum += l;
        return sum;
    }

    /** Нагрузка колонны на сорбции, доля её ёмкости. */
    public double loading() {
        return loading(active);
    }

    public double loading(int bed) {
        double cap = bedCapacityKg();
        return cap > 0 ? loaded[bed] / cap : 0;
    }

    public Mode mode(int bed) {
        return modes[bed];
    }

    public int activeBed() {
        return active;
    }

    /** Раствор идёт на смолу (колонна на сорбции есть). */
    public boolean sorbing() {
        return modes[active] == Mode.LOAD;
    }

    /** Доля урана, задерживаемая смолой, при нагрузке. */
    public static double efficiency(double loading) {
        if (loading < 0.85) return 0.998;
        return Math.max(0.3, 0.998 - (loading - 0.85) / 0.15 * 0.698);
    }

    /** Сорбция урана из раствора; возвращает уран, проскочивший в маточник, кг. */
    public double load(double plsKgU) {
        if (plsKgU <= 0) return 0;
        ledger.in(plsKgU);
        if (modes[active] != Mode.LOAD || resinL <= 0) {
            ledger.loss(plsKgU);
            lastLeak = plsKgU;
            return plsKgU;
        }
        double held = Math.min(plsKgU * efficiency(loading()), bedCapacityKg() - loaded[active]);
        loaded[active] += held;
        lastLeak = plsKgU - held;
        ledger.loss(lastLeak);
        if (loading() >= ELUTE_AT) {
            modes[active] = Mode.ELUTE;
            int other = (active + 1) % BEDS;
            if (modes[other] == Mode.LOAD || loading(other) <= LOAD_AT) {
                modes[other] = Mode.LOAD;
                active = other;
            }
        }
        return lastLeak;
    }

    /** Уран, который может уйти в элюат за {@code gameHours} со всех колонн на элюировании, кг. */
    public double elutable(double gameHours) {
        double sum = 0;
        for (int i = 0; i < BEDS; i++)
            if (modes[i] == Mode.ELUTE) sum += Math.min(loaded[i], ELUTION_KG_H_PER_L * resinL / BEDS * gameHours);
        return sum;
    }

    /** Элюирование до {@code kgU} урана; возвращает выданный в элюат уран. */
    public double elute(double kgU) {
        double left = Math.max(0, kgU), done = 0;
        for (int i = 0; i < BEDS && left > 0; i++) {
            if (modes[i] != Mode.ELUTE) continue;
            double e = Math.min(left, loaded[i]);
            loaded[i] -= e;
            left -= e;
            done += e;
            if (loading(i) <= LOAD_AT) {
                modes[i] = Mode.LOAD;
                if (modes[active] != Mode.LOAD) active = i;
            }
        }
        ledger.out(done);
        return done;
    }

    public double lastLeakKg() {
        return lastLeak;
    }

    public void save(StateWriter out) {
        out.putDouble("resin", resinL);
        for (int i = 0; i < BEDS; i++) {
            out.putDouble("loaded" + i, loaded[i]);
            out.putInt("mode" + i, modes[i].ordinal());
        }
        out.putInt("active", active);
        ledger.save(out);
    }

    public void load(StateReader in) {
        resinL = in.optDouble("resin", 0);
        for (int i = 0; i < BEDS; i++) {
            loaded[i] = in.optDouble("loaded" + i, 0);
            modes[i] = Mode.values()[Math.clamp(in.optInt("mode" + i, i == 0 ? 0 : 1), 0, 1)];
        }
        active = Math.clamp(in.optInt("active", 0), 0, BEDS - 1);
        ledger.load(in);
    }
}
