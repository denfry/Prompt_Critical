package dev.denfry.promptcritical.sim.manufacturing;

import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import dev.denfry.promptcritical.sim.metallurgy.SteelFamily;
import dev.denfry.promptcritical.sim.metallurgy.SteelGrade;
import java.util.Map;

/** Общие заготовки и марки для тестов пакета manufacturing. */
final class ManufacturingFixtures {
    private ManufacturingFixtures() {}

    static SteelGrade grade45() {
        return SteelGrade.of(SteelFamily.MEDIUM_CARBON, "45", Map.of("C", 0.45f, "Mn", 0.65f),
                HeatTreatState.NORMALIZED);
    }

    static Workpiece bar45(double d, double tK) {
        return Workpiece.bar(grade45(), Workpiece.Section.ROUND, d, d, 0.300, tK);
    }

    static SteelGrade wroughtIron() {
        return SteelGrade.of(SteelFamily.WROUGHT_IRON, "wrought iron", Map.of("C", 0.05f),
                HeatTreatState.AS_ROLLED);
    }

    static SteelGrade gradeShKh15() {
        return SteelGrade.of(SteelFamily.BEARING, "ShKh15", Map.of("C", 1.0f, "Cr", 1.5f),
                HeatTreatState.AS_ROLLED);
    }
}
