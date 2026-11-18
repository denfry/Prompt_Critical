package dev.denfry.promptcritical.sim.drilling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** ПВО и аккумулятор 09 §9.5.7. */
class BopStackTest {
    private static final double PIPE = 5 * 0.0254;

    @Test
    void boylesLawGivesTwentyLitresFromAnElevenGallonBottle() {
        // 09 §9.5.7: 20,8 л (5,5 гал) — правило 1000/1200/3000 psi; в мегапаскалях с округлением выходит 21,2 л
        assertThat(BopAccumulator.usableM3(0.0416, 7e6, 8.3e6, 21e6)).isCloseTo(0.0208, within(5e-4));
        BopAccumulator a = new BopAccumulator(1);
        assertThat(a.pressurePa()).isCloseTo(21e6, within(1.0));
        double use = a.fluidM3() - a.fluidAtPa(BopAccumulator.MIN_PA);
        assertThat(use).isCloseTo(0.0208, within(5e-4));
        assertThat(a.draw(use * 0.99)).isTrue();
        assertThat(a.pressurePa()).isGreaterThan(BopAccumulator.MIN_PA);
        assertThat(a.draw(use * 0.02)).as("below p_min nothing more").isFalse();
    }

    private static BopStack fullStack() {
        BopStack s = new BopStack(42);
        s.add(BopStack.Component.ANNULAR);
        s.add(BopStack.Component.PIPE_RAM);
        s.add(BopStack.Component.BLIND_RAM);
        s.add(BopStack.Component.SHEAR_RAM);
        s.markInstalled(0);
        return s;
    }

    @Test
    void validatorCountsBottlesAgainstApi53() {
        BopStack s = fullStack();
        double req = s.requiredVolumeM3();
        assertThat(req).isCloseTo(0.0681 + 3 * 0.022 + 0.0019, within(1e-9));
        assertThat(new BopAccumulator(4).meetsApi53(req)).isFalse();
        assertThat(new BopAccumulator(8).meetsApi53(req)).isTrue();
    }

    @Test
    void ramsSealOnlyWhatTheyAreBuiltFor() {
        BopStack s = fullStack();
        BopAccumulator a = new BopAccumulator(8);
        assertThat(s.close(2, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.RAM_MISMATCH);
        assertThat(s.get(2).closed()).as("blind rams do not close on pipe").isFalse();
        assertThat(s.close(1, a, 3.5 * 0.0254, false, 1)).isEqualTo(BopStack.Fault.RAM_MISMATCH);
        s.step(60);
        assertThat(s.sealed()).isFalse();
        s.open(1, a);
        assertThat(s.close(1, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.NONE);
        assertThat(s.sealed()).as("still stroking").isFalse();
        s.step(BopStack.CLOSE_TIME_S);
        assertThat(s.sealed()).isTrue();
        assertThat(s.holdingPa(PIPE)).isEqualTo(BopStack.RATED_PA);
    }

    @Test
    void shearRamsStallOnAToolJoint() {
        // замок: 0,2 м над началом трубы; тело трубы: 4 м
        assertThat(BopStack.toolJointInRams(180 + 9.144 * 3 + 0.2, 180)).isTrue();
        assertThat(BopStack.toolJointInRams(180 + 9.144 * 3 + 4, 180)).isFalse();
        BopStack s = fullStack();
        BopAccumulator a = new BopAccumulator(8);
        assertThat(s.close(3, a, PIPE, true, 1)).isEqualTo(BopStack.Fault.TOOL_JOINT_IN_RAM);
        assertThat(s.pipeSheared()).isFalse();
        assertThat(s.close(3, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.NONE);
        assertThat(s.pipeSheared()).isTrue();
        // после среза глухие закрываются на пустом стволе
        assertThat(s.close(2, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.NONE);
    }

    @Test
    void annularPackerWearsAfterTwentyClosures() {
        BopStack s = new BopStack(1);
        s.add(BopStack.Component.ANNULAR);
        s.markInstalled(0);
        BopAccumulator a = new BopAccumulator(100);
        for (int i = 0; i < 20; i++) {
            s.close(0, a, PIPE, false, 1);
            s.step(30);
            assertThat(s.holdingPa(PIPE)).isEqualTo(BopStack.RATED_PA);
            s.open(0, a);
            a.restoreFluid(1);
        }
        assertThat(s.close(0, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.WORN_ELEMENT);
        s.step(30);
        assertThat(s.holdingPa(PIPE)).isCloseTo(0.8 * BopStack.RATED_PA, within(1.0));
        s.open(0, a);
        s.replacePacker(0);
        s.close(0, a, PIPE, false, 1);
        s.step(30);
        assertThat(s.holdingPa(PIPE)).isEqualTo(BopStack.RATED_PA);
    }

    @Test
    void emptyAccumulatorCannotClose() {
        BopStack s = fullStack();
        BopAccumulator a = new BopAccumulator(1);
        assertThat(s.close(0, a, PIPE, false, 1)).isEqualTo(BopStack.Fault.NO_PRESSURE);
        assertThat(s.get(0).closed()).isFalse();
        BopAccumulator two = new BopAccumulator(4);
        two.restoreFluid(two.fluidAtPa(BopAccumulator.MIN_PA));
        assertThat(s.close(1, two, PIPE, false, 1)).isEqualTo(BopStack.Fault.NO_PRESSURE);
        // подзарядка от предзарядки до максимума — 15 минут
        BopAccumulator e = new BopAccumulator(8);
        e.restoreFluid(0);
        double t = 0;
        while (e.pressurePa() < BopAccumulator.MAX_PA - 1 && t < 3600) {
            e.recharge(1, 1);
            t += 1;
        }
        assertThat(t).isCloseTo(BopAccumulator.RECHARGE_S, within(2.0));
    }

    @Test
    void untestedElementsFailAboutOneTimeInTwenty() {
        int failed = 0, n = 2000;
        for (int seed = 0; seed < n; seed++) {
            BopStack s = new BopStack(seed * 7919L);
            s.add(BopStack.Component.PIPE_RAM);
            if (s.close(0, new BopAccumulator(4), PIPE, false, 100) == BopStack.Fault.UNTESTED_FAILURE) failed++;
        }
        assertThat(failed / (double) n).isCloseTo(0.05, within(0.015));
        BopStack tested = new BopStack(3);
        tested.add(BopStack.Component.PIPE_RAM);
        BopAccumulator a = new BopAccumulator(4);
        assertThat(tested.pressureTest(a, PIPE, 95)).isEqualTo(1);
        assertThat(tested.close(0, a, PIPE, false, 100)).isEqualTo(BopStack.Fault.NONE);
    }

    @Test
    void stateRoundTrips() {
        BopStack s = fullStack();
        BopAccumulator a = new BopAccumulator(8);
        s.close(0, a, PIPE, false, 1);
        s.step(10);
        BopStack r = fullStack();
        r.restore(s.exportState());
        assertThat(r.get(0).closed()).isTrue();
        assertThat(r.get(0).closures()).isEqualTo(1);
        r.step(20);
        assertThat(r.sealed()).isTrue();
    }
}
