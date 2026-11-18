package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Агитационное сернокислотное выщелачивание урановой руды с противоточной декантацией (Скороваров Д. И. и др.,
 * «Гидрометаллургическая переработка уранорудного сырья», 1979; Merritt, «The Extractive Metallurgy of Uranium», 1971;
 * Benedict–Pigford–Levi, гл. 5). Уран в уранините четырёхвалентный (UO₂) и растворяется лишь окисленным до U(VI):
 * UO₂ + 2 Fe³⁺ → UO₂²⁺ + 2 Fe²⁺, железо снова окисляет пиролюзит MnO₂ + 2 Fe²⁺ + 4 H⁺ → Mn²⁺ + 2 Fe³⁺ + 2 H₂O.
 * Уранил с сульфатом даёт анионный комплекс UO₂(SO₄)₃⁴⁻, который потом сорбирует анионит.
 *
 * <p>Модель — каскад из {@value #TANKS} пачуков идеального смешения: запас пульпы {@value #HOLDUP_KG} кг руды,
 * каждая порция свежей руды входит в первый бак и вытесняет пульпу по каскаду, из последнего — в хвосты. Растворение
 * первого порядка, k = 0,5 ч⁻¹ при 50 °C,
 * E = 40 кДж/моль; без окислителя растворяется только шестивалентная доля ({@value #U6_FRACTION}); {@value #REFRACTORY}
 * урана в упорных минералах (браннерит) не вскрывается. Кислоты — {@value #ACID_KG_PER_T} кг/т (основная часть
 * уходит на карбонаты и железо пустой породы), MnO₂ — {@value #MNO2_KG_PER_T} кг/т. Противоточная декантация
 * отмывает {@value #WASH} растворённого урана, остальное — растворимые потери с хвостами. Извлечение 90–95 % при
 * 8–16 ч — заводской диапазон у Скороварова и Merritt.
 */
public final class UraniumLeach {
    public static final double HOLDUP_KG = 2000, K_PER_H_323 = 0.5, E_J_MOL = 40e3, U6_FRACTION = 0.35, REFRACTORY = 0.04,
            ACID_KG_PER_T = 50, MNO2_KG_PER_T = 4, WASH = 0.985;
    public static final double MIN_K = 298.15, MAX_K = 343.15;
    public static final int TANKS = 4;

    private final UraniumLedger ledger = new UraniumLedger();
    private double tempK = 323.15;
    /** Твёрдое в баках каскада: руда, кг; уран, вскрываемый без окислителя, с окислителем, упорный, кг. */
    private final double[] ore = new double[TANKS], u6 = new double[TANKS], u4 = new double[TANKS], ur = new double[TANKS];
    private double lastPlsU, lastLossU, lastRate;

    public UraniumLedger ledger() {
        return ledger;
    }

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, MIN_K, MAX_K);
    }

    public double oreKg() {
        double sum = 0;
        for (double o : ore) sum += o;
        return sum;
    }

    /** Нерастворённый уран в пульпе, кг. */
    public double solidUKg() {
        double sum = 0;
        for (int i = 0; i < TANKS; i++) sum += u6[i] + u4[i] + ur[i];
        return sum;
    }

    /** Константа растворения при температуре, 1/ч. */
    public static double rate(double tempK) {
        return K_PER_H_323 * Math.exp(E_J_MOL / 8.314462618 * (1 / 323.15 - 1 / tempK));
    }

    /**
     * Подача свежей руды: вытесняет столько же пульпы в хвосты (уран в ней — потеря).
     *
     * @param uFraction массовая доля урана в руде
     */
    public void feed(double oreKgIn, double uFraction) {
        if (oreKgIn <= 0) return;
        double u = oreKgIn * uFraction;
        ledger.in(u);
        double[] in = {oreKgIn, u * (1 - REFRACTORY) * U6_FRACTION, u * (1 - REFRACTORY) * (1 - U6_FRACTION), u * REFRACTORY};
        double cap = HOLDUP_KG / TANKS;
        for (int i = 0; i < TANKS; i++) {
            ore[i] += in[0];
            u6[i] += in[1];
            u4[i] += in[2];
            ur[i] += in[3];
            double over = Math.max(0, ore[i] - cap);
            double f = ore[i] > 0 ? over / ore[i] : 0;
            in = new double[] {ore[i] * f, u6[i] * f, u4[i] * f, ur[i] * f};
            ore[i] -= in[0];
            u6[i] -= in[1];
            u4[i] -= in[2];
            ur[i] -= in[3];
        }
        double lost = in[1] + in[2] + in[3];
        ledger.loss(lost);
        lastLossU += lost;
    }

    /** Кислота и окислитель на кг свежей руды, кг. */
    public static double acidPerOreKg() {
        return ACID_KG_PER_T / 1000;
    }

    public static double mno2PerOreKg() {
        return MNO2_KG_PER_T / 1000;
    }

    /**
     * Растворение за {@code gameHours}.
     *
     * @param acidFraction    доля нормы кислоты, поданной с рудой (0…1)
     * @param oxidantFraction доля нормы окислителя
     * @return уран в продуктивный раствор, кг
     */
    public double step(double gameHours, double acidFraction, double oxidantFraction) {
        double k = rate(tempK) * Math.clamp(acidFraction, 0, 1);
        double f = 1 - Math.exp(-k * gameHours), ox = Math.clamp(oxidantFraction, 0, 1), dissolved = 0;
        for (int i = 0; i < TANKS; i++) {
            double d6 = u6[i] * f, d4 = u4[i] * f * ox;
            u6[i] -= d6;
            u4[i] -= d4;
            dissolved += d6 + d4;
        }
        double pls = dissolved * WASH, lost = dissolved - pls;
        ledger.out(pls);
        ledger.loss(lost);
        lastPlsU = pls;
        lastLossU += lost;
        lastRate = gameHours > 0 ? dissolved / gameHours : 0;
        return pls;
    }

    /** Потери урана с хвостами с прошлого вызова (для панели и проверки), кг; сбрасывает счётчик. */
    public double takeLossKg() {
        double l = lastLossU;
        lastLossU = 0;
        return l;
    }

    public double lastPlsKg() {
        return lastPlsU;
    }

    /** Скорость растворения, кг U/ч. */
    public double dissolutionKgH() {
        return lastRate;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        for (int i = 0; i < TANKS; i++) {
            out.putDouble("ore" + i, ore[i]);
            out.putDouble("u6_" + i, u6[i]);
            out.putDouble("u4_" + i, u4[i]);
            out.putDouble("ur" + i, ur[i]);
        }
        ledger.save(out);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", 323.15));
        for (int i = 0; i < TANKS; i++) {
            ore[i] = in.optDouble("ore" + i, 0);
            u6[i] = in.optDouble("u6_" + i, 0);
            u4[i] = in.optDouble("u4_" + i, 0);
            ur[i] = in.optDouble("ur" + i, 0);
        }
        ledger.load(in);
    }
}
