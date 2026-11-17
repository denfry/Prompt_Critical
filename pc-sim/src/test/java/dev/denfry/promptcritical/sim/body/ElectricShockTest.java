package dev.denfry.promptcritical.sim.body;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.body.BodyProfile.Sex;
import dev.denfry.promptcritical.sim.body.ElectricShock.Effect;
import org.junit.jupiter.api.Test;

class ElectricShockTest {
    /** IEC 60479-1, табл. 1 (рука–рука, сухо, 50 %): 230 В — между 225 и 400 В, 1217 Ом; рука–ноги — 0,8 этого. */
    @Test
    void bodyImpedanceFollowsIec60479() {
        assertThat(ElectricShock.bodyImpedanceOhm(25, false)).isCloseTo(3250 * 0.8, within(1e-9));
        assertThat(ElectricShock.bodyImpedanceOhm(230.9, false)).isCloseTo((1225 - 275 * 5.9 / 175) * 0.8, within(1e-6));
        assertThat(ElectricShock.bodyImpedanceOhm(6300 / Math.sqrt(3), false)).isCloseTo(775 * 0.8, within(1e-9));
        assertThat(ElectricShock.bodyImpedanceOhm(24, true)).isCloseTo(620, within(1e-9));
        assertThat(ElectricShock.touchVoltageV(400)).isCloseTo(230.9, within(0.05));
    }

    /** Dalziel: K = 0,116 при 50 кг и 0,157 при 70 кг; при 1 с порог равен K. */
    @Test
    void fibrillationThresholdGrowsWithBodyMass() {
        assertThat(ElectricShock.fibrillationA(50, 1)).isCloseTo(0.116, within(1e-12));
        assertThat(ElectricShock.fibrillationA(70, 1)).isCloseTo(0.157, within(1e-12));
        assertThat(ElectricShock.fibrillationA(70, 0.25)).isCloseTo(0.314, within(1e-12));
        assertThat(ElectricShock.fibrillationA(70, 60)).isEqualTo(ElectricShock.fibrillationA(70, 5));
        assertThat(ElectricShock.letGoA(Sex.FEMALE)).isLessThan(ElectricShock.letGoA(Sex.MALE));
    }

    /**
     * Шина 0,4 кВ: 231 В, ≈ 237 мА — руку не разжать, и через доли секунды фибрилляция; лёгкое тело сдаётся раньше
     * тяжёлого. 36 В (переносной инструмент) сухими руками — около 15 мА: мужчина отпустит, женщина — уже нет.
     */
    @Test
    void lowVoltageBusbarKillsWithinASecondAndLighterBodiesSooner() {
        double i = ElectricShock.bodyCurrentA(ElectricShock.touchVoltageV(400), false);
        assertThat(i).isBetween(0.23, 0.25);
        double light = secondsToFibrillation(new BodyProfile(Sex.FEMALE, 160, 50, 25), i);
        double heavy = secondsToFibrillation(new BodyProfile(Sex.MALE, 185, 110, 40), i);
        assertThat(light).isLessThan(heavy);
        assertThat(heavy).isLessThan(1.5);

        double i36 = ElectricShock.bodyCurrentA(36, false);
        assertThat(i36).isBetween(0.0105, 0.016);
        ElectricShock.Contact man = new ElectricShock.Contact(), woman = new ElectricShock.Contact();
        assertThat(man.step(BodyProfile.REFERENCE, i36, 0.1)).isEqualTo(Effect.PERCEPTIBLE);
        assertThat(woman.step(new BodyProfile(Sex.FEMALE, 165, 60, 30), i36, 0.1)).isEqualTo(Effect.CANNOT_LET_GO);

        assertThat(man.step(BodyProfile.REFERENCE, 0, 0.1)).isEqualTo(Effect.NONE);
        assertThat(man.seconds()).isZero();
    }

    private static double secondsToFibrillation(BodyProfile body, double currentA) {
        ElectricShock.Contact c = new ElectricShock.Contact();
        for (int k = 1; k <= 100; k++) {
            if (c.step(body, currentA, 0.05) == Effect.FIBRILLATION) return k * 0.05;
        }
        return Double.POSITIVE_INFINITY;
    }
}
