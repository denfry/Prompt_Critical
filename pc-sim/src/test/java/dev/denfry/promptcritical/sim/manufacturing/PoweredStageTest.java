package dev.denfry.promptcritical.sim.manufacturing;

import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.bar45;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.gradeShKh15;
import static dev.denfry.promptcritical.sim.manufacturing.ManufacturingFixtures.wroughtIron;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** MF3: горячая рубка, волочение с наклёпом и отжигом, сварка, печь по уставке, механический молот. */
class PoweredStageTest {
    private static final double AMBIENT = 293.15;

    private static Workpiece bloom(double tK) {
        return Workpiece.bar(wroughtIron(), Workpiece.Section.SQUARE, 0.10, 0.10, 0.25, tK)
                .withDefect(PartDefect.SLAG_INCLUSION, 0.8);
    }

    @Test void hotCutSplitsTheBloomKeepingVolumeAndSlag() {
        var w = bloom(1400);
        var s = Forging.cut(w, 0.25, 5, AMBIENT);
        assertThat(s.done()).isTrue();
        assertThat(s.event()).isEqualTo(ForgeEvent.OK);
        assertThat(s.first().volumeM3() + s.second().volumeM3()).isCloseTo(w.volumeM3(), within(1e-12));
        assertThat(s.first().lengthM()).isCloseTo(0.0625, within(1e-9));
        assertThat(s.second().severity(PartDefect.SLAG_INCLUSION)).isEqualTo(0.8);
        assertThat(s.first().tempK()).isLessThan(1400);
    }

    @Test void coldOrStubbyPiecesAreNotCut() {
        var cold = Forging.cut(bar45(0.04, AMBIENT), 0.5, 5, AMBIENT);
        assertThat(cold.done()).isFalse();
        assertThat(cold.event()).isEqualTo(ForgeEvent.TOO_COLD);
        var stub = Forging.cut(bar45(0.04, 1273.15), 0.05, 5, AMBIENT);
        assertThat(stub.event()).isEqualTo(ForgeEvent.TOO_THIN);
        assertThat(stub.first().lengthM()).isEqualTo(0.3);
    }

    @Test void drawPlateTakesThinCopperButNotASteelRod() {
        var copper = new Wire(Wire.Metal.COPPER, 0.004, 1, 0);
        var p = Drawing.pass(copper, 0.0034, Drawing.MU_LUBRICATED, Drawing.HAND_FORCE_N);
        assertThat(p.outcome()).isEqualTo(Drawing.Outcome.OK);
        assertThat(p.forceN()).isBetween(300.0, 700.0);
        assertThat(p.wire().lengthM() * p.wire().areaM2()).isCloseTo(copper.lengthM() * copper.areaM2(), within(1e-12));
        assertThat(p.wire().strain()).isCloseTo(Math.log(16 / 11.56), within(1e-9));

        var rod = new Wire(Wire.Metal.MILD_STEEL, 0.008, 1, 0);
        var q = Drawing.pass(rod, 0.007, Drawing.MU_LUBRICATED, Drawing.HAND_FORCE_N);
        assertThat(q.outcome()).isEqualTo(Drawing.Outcome.TOO_STRONG);
        assertThat(q.wire()).isEqualTo(rod);
        assertThat(q.forceN()).isGreaterThan(3000);
    }

    @Test void dryDieNeedsMoreForceThanALubricatedOne() {
        var w = new Wire(Wire.Metal.MILD_STEEL, 0.003, 1, 0);
        double dry = Drawing.pass(w, 0.0027, Drawing.MU_DRY, 1e5).forceN();
        double wet = Drawing.pass(w, 0.0027, Drawing.MU_LUBRICATED, 1e5).forceN();
        assertThat(dry).isGreaterThan(wet * 1.1);
    }

    @Test void tooHeavyAPassBreaksTheWire() {
        var copper = new Wire(Wire.Metal.COPPER, 0.004, 1, 0);
        var p = Drawing.pass(copper, 0.003, Drawing.MU_LUBRICATED, 1e5);
        assertThat(p.outcome()).isEqualTo(Drawing.Outcome.BROKE);
        assertThat(p.wire().diameterM()).isEqualTo(0.004);
    }

    @Test void passesWorkHardenTheWireUntilItNeedsAnAnneal() {
        var w = new Wire(Wire.Metal.MILD_STEEL, 0.003, 1, 0);
        double strength0 = w.strengthPa();
        int ok = 0;
        Drawing.Pass p;
        while (true) {
            p = Drawing.pass(w, w.diameterM() * Math.sqrt(0.85), Drawing.MU_LUBRICATED, 1e5);
            if (p.outcome() != Drawing.Outcome.OK) break;
            w = p.wire();
            ok++;
        }
        assertThat(p.outcome()).isEqualTo(Drawing.Outcome.BROKE);
        assertThat(ok).isBetween(8, 10);
        assertThat(w.strengthPa()).isGreaterThan(strength0 * 2);

        var warm = Drawing.anneal(w, 873.15, 3600);
        assertThat(warm.strain()).isEqualTo(w.strain());
        var annealed = Drawing.anneal(w, 1023.15, 600);
        assertThat(annealed.strain()).isZero();
        assertThat(Drawing.pass(annealed, annealed.diameterM() * Math.sqrt(0.85), Drawing.MU_LUBRICATED, 1e5).outcome())
                .isEqualTo(Drawing.Outcome.OK);
    }

    @Test void onlyThinRoundDrawableSteelBecomesARod() {
        assertThat(Wire.fromRod(bar45(0.012, AMBIENT)).metal()).isEqualTo(Wire.Metal.MEDIUM_CARBON);
        assertThat(Wire.fromRod(bar45(0.020, AMBIENT))).isNull();
        assertThat(Wire.fromRod(Workpiece.bar(gradeShKh15(), Workpiece.Section.ROUND, 0.01, 0.01, 0.3, AMBIENT))).isNull();
    }

    @Test void forgeWeldNeedsWhiteHeatAndFlux() {
        var a = Workpiece.bar(wroughtIron(), Workpiece.Section.SQUARE, 0.02, 0.02, 0.1, 1573.15);
        var b = Workpiece.bar(wroughtIron(), Workpiece.Section.SQUARE, 0.02, 0.02, 0.15, 1573.15);
        var sound = Welding.join(a, b, Welding.forgeJoint(1573.15, true));
        assertThat(sound.events()).containsExactly(ForgeEvent.OK);
        assertThat(sound.piece().lengthM()).isCloseTo(0.25, within(1e-12));
        assertThat(sound.piece().volumeM3()).isCloseTo(a.volumeM3() + b.volumeM3(), within(1e-12));

        var dirty = Welding.join(a, b, Welding.forgeJoint(1573.15, false));
        assertThat(dirty.events()).containsExactly(ForgeEvent.POOR_FUSION);
        assertThat(dirty.piece().severity(PartDefect.LACK_OF_FUSION)).isCloseTo(0.5, within(1e-9));

        assertThat(Welding.forgeJoint(1373.15, true)).isNull();
        assertThat(Welding.join(a, b, null).events()).containsExactly(ForgeEvent.TOO_COLD);
        var thick = Workpiece.bar(wroughtIron(), Workpiece.Section.SQUARE, 0.04, 0.04, 0.1, 1573.15);
        assertThat(Welding.join(a, thick, Welding.forgeJoint(1573.15, true)).events()).containsExactly(ForgeEvent.MISMATCH);
    }

    @Test void arcBeadIsSoundOnlyNearTheRightHeatInput() {
        double t = 0.006;
        var right = Welding.bead(t, Welding.arcHeatInput(25, 160, 0.002));
        assertThat(right).isEqualTo(Welding.Joint.SOUND);
        var fast = Welding.bead(t, Welding.arcHeatInput(25, 160, 0.006));
        assertThat(fast.lackOfFusion()).isEqualTo(1.0);
        var slow = Welding.bead(t, Welding.arcHeatInput(25, 160, 0.0005));
        assertThat(slow.porosity()).isEqualTo(1.0);
        assertThat(slow.lackOfFusion()).isZero();
    }

    @Test void furnaceReachesForgingHeatOnFiftyKilowattsButNotOnFive() {
        var f = Furnace.cold(AMBIENT).withSetpoint(1473.15);
        for (int s = 0; s < 1500; s++) f = f.step(f.demandW(AMBIENT, 50_000), AMBIENT, 1);
        assertThat(f.tempK()).isCloseTo(1473.15, within(10.0));
        assertThat(f.demandW(AMBIENT, 50_000)).isBetween(5_000.0, 20_000.0);

        var weak = Furnace.cold(AMBIENT).withSetpoint(1473.15);
        for (int s = 0; s < 20_000; s++) weak = weak.step(weak.demandW(AMBIENT, 5_000), AMBIENT, 1);
        assertThat(weak.tempK()).isLessThan(1000);
    }

    @Test void poweredHammerDrawsABigBarFarFasterThanAHand() {
        var big = Workpiece.bar(wroughtIron(), Workpiece.Section.SQUARE, 0.08, 0.08, 0.4, 1473.15);
        double hand = Forging.series(big, Forging.Op.DRAW, Forging.HAND_BLOW_J, Forging.HAND_BLOWS,
                Forging.HAND_STRAIN_RATE, Forging.HAND_SERIES_S, AMBIENT).piece().lengthM() - big.lengthM();
        double powered = Forging.series(big, Forging.Op.DRAW, Forging.poweredBlowJ(20_000), Forging.HAND_BLOWS,
                Forging.POWER_STRAIN_RATE, Forging.HAND_SERIES_S, AMBIENT).piece().lengthM() - big.lengthM();
        assertThat(Forging.poweredBlowJ(20_000)).isCloseTo(6000, within(1e-9));
        assertThat(powered).isGreaterThan(hand * 20);
    }
}
