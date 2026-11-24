package dev.denfry.promptcritical.sim.manufacturing.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.denfry.promptcritical.sim.manufacturing.PartDefect;
import dev.denfry.promptcritical.sim.manufacturing.PartRoute;
import dev.denfry.promptcritical.sim.manufacturing.PartSpec;
import dev.denfry.promptcritical.sim.manufacturing.assembly.AssemblyGate.Verdict;
import dev.denfry.promptcritical.sim.manufacturing.assembly.AssemblyScorer.Balancing;
import dev.denfry.promptcritical.sim.manufacturing.assembly.AssemblyScorer.Placed;
import dev.denfry.promptcritical.sim.manufacturing.assembly.PartRequirement.Aspect;
import dev.denfry.promptcritical.sim.metallurgy.HeatTreatState;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Сборка машины (18 §18.8, §18.15.2): допуски ISO 286, ворота, оценка в паспорт. */
class AssemblyTest {
    /** Центробежный насос из примера 18 §18.8.1 (без двигателя: привод отдельной машиной). */
    static MachineSpec pump() {
        return new MachineSpec("pump", "promptcritical:mine_drainage_pump", List.of(
                PartRequirement.of("casing", "pump_casing", 1, Aspect.GASKET).withMaterials("gray_iron"),
                PartRequirement.of("impeller", "pump_impeller", 1, Aspect.ROTOR),
                PartRequirement.of("shaft", "shaft", 1, Aspect.BEARING_SEAT)
                        .withFit(7, 30, HeatTreatState.QUENCHED_TEMPERED),
                PartRequirement.of("bearings", "rolling_bearing", 2, Aspect.BEARING),
                PartRequirement.of("seal", "gland_packing", 1, Aspect.SEAL),
                PartRequirement.of("bed", "machine_bed", 1, Aspect.ALIGNMENT)), true);
    }

    static PartSpec shaft(int it, int hrc, HeatTreatState ht, Map<PartDefect, Double> defects) {
        return PartSpec.of("shaft", "45", PartRoute.FORGED, ht, hrc, it, 1.6, defects);
    }

    @Test
    void isoToleranceMatchesTheStandardTable() {
        // ISO 286-1, интервал 30–50 мм: IT6 16, IT7 25, IT9 62, IT11 160, IT14 620 мкм; 6–10 мм: IT6 9, IT12 150
        assertThat(IsoFits.widthUm(6, 50)).isEqualTo(16);
        assertThat(IsoFits.widthUm(7, 50)).isEqualTo(25);
        assertThat(IsoFits.widthUm(9, 50)).isEqualTo(62);
        assertThat(IsoFits.widthUm(11, 40)).isEqualTo(160);
        assertThat(IsoFits.widthUm(14, 40)).isEqualTo(620);
        assertThat(IsoFits.widthUm(6, 8)).isEqualTo(9);
        assertThat(IsoFits.widthUm(12, 8)).isEqualTo(150);
        assertThat(IsoFits.effective(0)).isEqualTo(IsoFits.IT_UNMACHINED);
        assertThatThrownBy(() -> IsoFits.widthUm(5, 50)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void gateRejectsOnlyCoarseFaults() {
        PartRequirement casing = pump().requirement("casing");
        PartSpec good = PartSpec.of("pump_casing", "gray_iron", PartRoute.CAST, HeatTreatState.AS_ROLLED, 0, 0, 25,
                Map.of(PartDefect.POROSITY, 0.5));
        assertThat(AssemblyGate.check(casing, "pump_casing", good)).isEqualTo(Verdict.OK);
        assertThat(AssemblyGate.check(casing, "flange", good)).isEqualTo(Verdict.WRONG_KIND);
        PartSpec steel = PartSpec.of("pump_casing", "35l", PartRoute.CAST, HeatTreatState.AS_ROLLED, 0, 0, 25, Map.of());
        assertThat(AssemblyGate.check(casing, "pump_casing", steel)).isEqualTo(Verdict.WRONG_MATERIAL);
        PartSpec cracked = PartSpec.of("pump_casing", "gray_iron", PartRoute.CAST, HeatTreatState.AS_ROLLED, 0, 0, 25,
                Map.of(PartDefect.CRACK, 0.2));
        assertThat(AssemblyGate.check(casing, "pump_casing", cracked)).isEqualTo(Verdict.GATE_DEFECT);
        assertThat(AssemblyGate.check(casing, "pump_casing", null)).isEqualTo(Verdict.OK);  // старая деталь
    }

    @Test
    void goodPartsAndBalancingGiveANearNominalPassport() {
        MachinePassport p = AssemblyScorer.score(pump(), List.of(
                new Placed("shaft", shaft(7, 30, HeatTreatState.QUENCHED_TEMPERED, Map.of())),
                new Placed("impeller", null), new Placed("bearings", null), new Placed("seal", null),
                new Placed("casing", null), new Placed("bed", null)), Balancing.DYNAMIC, null);
        assertThat(p.bearingWearFactor()).isEqualTo(1);
        assertThat(p.imbalanceMmS()).isLessThan(1.4);               // ниже первой ступени 13
        assertThat(p.misalignmentMm()).isLessThan(0.05);
        assertThat(p.hasWinding()).isFalse();
        assertThat(p.score()).isGreaterThan(90);
    }

    @Test
    void roughSoftShaftAndHandBalancingWorsenThePassport() {
        MachinePassport good = AssemblyScorer.score(pump(), List.of(
                new Placed("shaft", shaft(7, 30, HeatTreatState.QUENCHED_TEMPERED, Map.of()))), Balancing.DYNAMIC, null);
        MachinePassport bad = AssemblyScorer.score(pump(), List.of(
                new Placed("shaft", shaft(0, 15, HeatTreatState.AS_ROLLED, Map.of(PartDefect.DECARB, 0.6))),
                new Placed("impeller", null)), Balancing.NONE, null);
        // IT14 против IT7 (+2.1), 15 HRC против 30 (+0.45), без закалки (+0.15), обезуглероживание 0.6 — ступень 2 (+0.5)
        assertThat(bad.bearingWearFactor()).isCloseTo(4.2, org.assertj.core.data.Offset.offset(0.051));
        assertThat(bad.imbalanceMmS()).isCloseTo(4.8, org.assertj.core.data.Offset.offset(0.051));
        assertThat(bad.score()).isLessThan(good.score());
    }

    @Test
    void templatesReplaceRemoveAndAdd() {
        MachineSpec fan = MachineSpec.fromTemplate("fan", "promptcritical:main_mine_fan", pump(),
                List.of(PartRequirement.of("impeller", "fan_rotor", 1, Aspect.ROTOR)), Set.of("seal", "casing"),
                List.of(PartRequirement.of("pulley", "pulley", 1, Aspect.ROTOR)), null);
        assertThat(fan.requirements()).extracting(PartRequirement::role)
                .containsExactly("impeller", "shaft", "bearings", "bed", "pulley");
        assertThat(fan.requirement("impeller").kinds()).containsExactly("fan_rotor");
        assertThat(fan.runIn()).isTrue();
        assertThatThrownBy(() -> MachineSpec.fromTemplate("x", "y", pump(), List.of(), Set.of("nope"), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passportQuantizesSoEqualBuildsStack() {
        MachinePassport a = new MachinePassport(1.31, 1.92, 0.0312, 0.0041, 0.081, 2345, 0.9871, 80);
        MachinePassport b = new MachinePassport(1.29, 1.88, 0.0288, 0.0039, 0.079, 2349, 0.9869, 80);
        assertThat(a).isEqualTo(b);
        assertThat(a.insulationMOhm()).isEqualTo(2300);
        assertThat(MachinePassport.nominal().equals(MachinePassport.nominal())).isTrue();
    }
}
