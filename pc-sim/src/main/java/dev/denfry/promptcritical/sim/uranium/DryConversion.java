package dev.denfry.promptcritical.sim.uranium;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;

/**
 * Сухая конверсия химконцентрата в гексафторид (Benedict–Pigford–Levi, гл. 5.8 «Production of UF₆»; Harrington,
 * Ruehle, «Uranium Production Technology», 1959; Галкин Н. П. и др., «Технология урана», 1964). Стадии:
 * <ul>
 *   <li>прокалка ДУА в UO₃ во вращающейся печи, 400–500 °C (выше 600 °C — U₃O₈);</li>
 *   <li>восстановление UO₃ + H₂ → UO₂ + H₂O, 600–700 °C, водород с избытком 1,5;</li>
 *   <li>гидрофторирование UO₂ + 4 HF → UF₄ + 2 H₂O, 400–600 °C, HF с избытком 1,5 («зелёная соль»);</li>
 *   <li>фторирование UF₄ + F₂ → UF₆ в пламенном реакторе, стенка 350–500 °C, F₂ с избытком 15 %; UF₆ вымораживают
 *   в холодильных ловушках.</li>
 * </ul>
 * Вне окна температур стадия не доходит: ниже — медленно, выше — спекание (восстановление, гидрофторирование) или
 * коррозия (фторирование). Непрореагировавшее остаётся в аппарате, потери стадии — пыль и унос (0,05–0,3 %).
 */
public final class DryConversion {
    public enum Step {
        CALCINE(673.15, 773.15, 0, 0, 2.0e6, 0.001, 0.08),
        REDUCE(873.15, 973.15, 2.016 / UraniumLedger.M_U, 1.5, 1.5e6, 0.0005, 0.08),
        HYDROFLUORINATE(673.15, 873.15, 4 * 20.006 / UraniumLedger.M_U, 1.5, 1.0e6, 0.002, 0.06),
        FLUORINATE(623.15, 773.15, 37.997 / UraniumLedger.M_U, 1.15, 0, 0.003, 0.06);

        /** Окно температур, К. */
        public final double lowK, highK;
        /** Реагент на кг U по стехиометрии, кг (H₂, HF, F₂), и избыток. */
        public final double reagentKgPerKgU, excess;
        /** Топливо на нагрев, Дж на кг U (фторирование экзотермично — греть не нужно). */
        public final double heatJPerKgU;
        /** Потеря урана пылью и уносом, доля. */
        public final double loss;
        /** Производительность, кг U/с [BALANCE]. */
        public final double capacityKgS;

        Step(double lowK, double highK, double reagentKgPerKgU, double excess, double heatJPerKgU, double loss, double capacityKgS) {
            this.lowK = lowK;
            this.highK = highK;
            this.reagentKgPerKgU = reagentKgPerKgU;
            this.excess = excess;
            this.heatJPerKgU = heatJPerKgU;
            this.loss = loss;
            this.capacityKgS = capacityKgS;
        }

        /** Расход реагента с избытком, кг на кг U. */
        public double reagentWithExcess() {
            return reagentKgPerKgU * excess;
        }

        /** Средняя температура окна. */
        public double nominalK() {
            return (lowK + highK) / 2;
        }
    }

    private final Step step;
    private final UraniumLedger ledger = new UraniumLedger();
    private double tempK;
    private double lastKgS, lastEff;

    public DryConversion(Step step) {
        this.step = step;
        this.tempK = step.nominalK();
    }

    public Step kind() {
        return step;
    }

    public UraniumLedger ledger() {
        return ledger;
    }

    public double tempK() {
        return tempK;
    }

    public void setTempK(double k) {
        tempK = Math.clamp(k, step.lowK - 150, step.highK + 150);
    }

    /** Доля производительности при температуре: в окне — полная, на 100 К ниже или выше — ноль. */
    public static double efficiency(Step s, double tempK) {
        if (tempK < s.lowK) return Math.max(0, 1 - (s.lowK - tempK) / 100);
        if (tempK > s.highK) return Math.max(0, 1 - (tempK - s.highK) / 100);
        return 1;
    }

    /** Уран, который стадия может переработать за {@code dtS} при доступном реагенте, кг. */
    public double capacityKg(double dtS, double reagentKg, double heatJ) {
        double eff = efficiency(step, tempK);
        double kg = step.capacityKgS * eff * dtS;
        if (step.reagentKgPerKgU > 0) kg = Math.min(kg, reagentKg / step.reagentWithExcess());
        if (step.heatJPerKgU > 0) kg = Math.min(kg, heatJ / step.heatJPerKgU);
        return Math.max(0, kg);
    }

    /**
     * Переработка {@code kgU} урана: возвращает уран в продукте, кг (остальное — потеря стадии).
     */
    public double process(double kgU, double dtS) {
        if (kgU <= 0) {
            lastKgS = 0;
            lastEff = efficiency(step, tempK);
            return 0;
        }
        ledger.in(kgU);
        double product = kgU * (1 - step.loss);
        ledger.out(product);
        ledger.loss(kgU - product);
        lastKgS = dtS > 0 ? kgU / dtS : 0;
        lastEff = efficiency(step, tempK);
        return product;
    }

    public double lastKgS() {
        return lastKgS;
    }

    public double lastEfficiency() {
        return lastEff;
    }

    public void save(StateWriter out) {
        out.putDouble("t", tempK);
        ledger.save(out);
    }

    public void load(StateReader in) {
        setTempK(in.optDouble("t", step.nominalK()));
        ledger.load(in);
    }
}
