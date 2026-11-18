package dev.denfry.promptcritical.sim.fuel;

import dev.denfry.promptcritical.sim.core.StateReader;
import dev.denfry.promptcritical.sim.core.StateWriter;
import dev.denfry.promptcritical.sim.uranium.UraniumLedger;

/**
 * Учёт и контроль ядерных материалов участка: два баланса {@link UraniumLedger} — по урану и отдельно по U-235, в
 * килограммах элемента и изотопа. Разность «пришло − ушло − списано в потери» — запас в аппаратах; при
 * инвентаризации расхождение с фактическим запасом — неучтённый материал (MUF, Benedict–Pigford–Levi, гл. 5;
 * НП-030-12 «Основные правила учёта и контроля ядерных материалов»).
 */
public final class MaterialBalance {
    private final UraniumLedger uranium = new UraniumLedger(), u235 = new UraniumLedger();

    public UraniumLedger uranium() {
        return uranium;
    }

    public UraniumLedger u235() {
        return u235;
    }

    public void in(double kgU, double x) {
        uranium.in(kgU);
        u235.in(kgU * x);
    }

    public void out(double kgU, double x) {
        uranium.out(kgU);
        u235.out(kgU * x);
    }

    public void loss(double kgU, double x) {
        uranium.loss(kgU);
        u235.loss(kgU * x);
    }

    /** U-235 в аппаратах участка, кг. */
    public double holdupU235Kg() {
        return u235.holdupKg();
    }

    public void save(StateWriter out) {
        out.beginObject("u");
        uranium.save(out);
        out.endObject();
        out.beginObject("u235");
        u235.save(out);
        out.endObject();
    }

    public void load(StateReader in) {
        if (in.has("u")) {
            in.beginObject("u");
            uranium.load(in);
            in.endObject();
        }
        if (in.has("u235")) {
            in.beginObject("u235");
            u235.load(in);
            in.endObject();
        }
    }
}
