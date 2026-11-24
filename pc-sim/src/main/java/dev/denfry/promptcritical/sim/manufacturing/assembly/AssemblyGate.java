package dev.denfry.promptcritical.sim.manufacturing.assembly;

import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import java.util.Objects;

/**
 * Ворота спецификации (18 §18.15.2): стенд не принимает деталь не того вида, марку не из группы, трещину и пережог.
 * Всё прочее проходит и уходит в оценку.
 */
public final class AssemblyGate {
    /** Причина отказа; {@link #OK} — деталь принята. */
    public enum Verdict { OK, WRONG_KIND, WRONG_MATERIAL, GATE_DEFECT }

    private AssemblyGate() {
    }

    /**
     * @param kind вид предмета в слоте (берётся из предмета, а не из паспорта: у старых деталей паспорта нет)
     * @param part паспорт детали; null — номинальная деталь без паспорта (§18.15.3)
     */
    public static Verdict check(PartRequirement req, String kind, PartSpec part) {
        Objects.requireNonNull(req, "req");
        if (kind == null || !req.kinds().contains(kind)) return Verdict.WRONG_KIND;
        if (part == null) return Verdict.OK;
        if (!req.materials().isEmpty() && !req.materials().contains(part.material())) return Verdict.WRONG_MATERIAL;
        for (PartDefect d : part.defects().keySet()) if (d.gate) return Verdict.GATE_DEFECT;
        return Verdict.OK;
    }
}
