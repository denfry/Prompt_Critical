package dev.denfry.promptcritical.sim.automation.sis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.automation.sis.SisLogic.ProofResult;
import dev.denfry.promptcritical.sim.automation.sis.SisLogic.Voting;
import dev.denfry.promptcritical.sim.core.MutableStateTree;
import dev.denfry.promptcritical.sim.core.TreeStateReader;
import dev.denfry.promptcritical.sim.core.TreeStateWriter;
import org.junit.jupiter.api.Test;

/** Периодическая проверка ПАЗ 03d §3d.6.8 и текущая PFD(t) §3d.6.7; шкаф как матрица причин §3d.6.3. */
class SisProofTestTest {
    private static final double T = 8760; // интервал 12 мес. в часах физики

    private static void healthy(SisLogic s) {
        SisLogicTest.step(s, Voting.TWO_OO_THREE, 12, 12, 12);
    }

    @Test
    void theProofTestFollowsTheFiveSteps() {
        SisLogic s = new SisLogic();
        healthy(s);
        s.ageProofTest(1000);
        assertThat(s.startProofTest()).as("step 1: bypass first").isEqualTo(ProofResult.NEEDS_BYPASS);
        s.setBypass(true);
        assertThat(s.startProofTest()).isEqualTo(ProofResult.OK);
        assertThat(s.testChannel(0, Voting.TWO_OO_THREE)).isEqualTo(ProofResult.OK);
        assertThat(s.signProofTest(Voting.TWO_OO_THREE, T)).isEqualTo(ProofResult.BYPASS_STILL_ON);
        s.testChannel(1, Voting.TWO_OO_THREE);
        s.setBypass(false);
        assertThat(s.signProofTest(Voting.TWO_OO_THREE, T)).as("channel 3 untested").isEqualTo(ProofResult.CHANNELS_UNTESTED);
        s.setBypass(true);
        s.testChannel(2, Voting.TWO_OO_THREE);
        s.setBypass(false);
        assertThat(s.signProofTest(Voting.TWO_OO_THREE, T)).isEqualTo(ProofResult.PASSED_ON_TIME);
        assertThat(s.sinceTestH()).isZero();
        assertThat(s.proofTests()).isEqualTo(1);
        assertThat(s.proofTestsOnTime()).isEqualTo(1);
        assertThat(s.testing()).isFalse();
    }

    @Test
    void aFaultedChannelIsAFoundFailureAndALateTestIsNotOnTime() {
        SisLogic s = new SisLogic();
        s.step(0.05, true, new double[] {12, 2.0, 12}, Voting.TWO_OO_THREE, true, 16, false); // AI2 — обрыв
        s.ageProofTest(T + 1);
        assertThat(s.proofTestOverdue(T)).isTrue();
        s.setBypass(true);
        s.startProofTest();
        assertThat(s.testChannel(1, Voting.TWO_OO_THREE)).isEqualTo(ProofResult.CHANNEL_FAILED);
        s.testChannel(0, Voting.TWO_OO_THREE);
        s.testChannel(2, Voting.TWO_OO_THREE);
        s.setBypass(false);
        assertThat(s.signProofTest(Voting.TWO_OO_THREE, T)).isEqualTo(ProofResult.CHANNEL_FAILED);
        s.abortProofTest();
        s.step(0.05, true, new double[] {12, 12, 12}, Voting.TWO_OO_THREE, true, 16, true); // датчик заменён, квитировано
        s.setBypass(true);
        s.startProofTest();
        for (int i = 0; i < 3; i++) s.testChannel(i, Voting.TWO_OO_THREE);
        s.setBypass(false);
        assertThat(s.signProofTest(Voting.TWO_OO_THREE, T)).isEqualTo(ProofResult.PASSED_LATE);
        assertThat(s.proofTestsOnTime()).isZero();
        assertThat(s.proofTestOverdue(T)).isFalse();
    }

    /** PFD(t) растёт от нуля после проверки; её среднее за T при λT ≪ 1 совпадает с PFDavg IEC 61508-6. */
    @Test
    void instantPfdGrowsFromTheTestAndAveragesToPfdAvg() {
        double lambda = 1e-6, beta = 0.1, ti = 8760;
        for (Voting v : Voting.values()) {
            assertThat(SisLogic.pfdInstant(v, lambda, 0, beta)).isZero();
            double prev = 0, sum = 0;
            int n = 2000;
            for (int i = 1; i <= n; i++) {
                double p = SisLogic.pfdInstant(v, lambda, ti * (i - 0.5) / n, beta);
                assertThat(p).isGreaterThanOrEqualTo(prev);
                prev = p;
                sum += p;
            }
            double avg = sum / n, ref = SisLogic.pfdAvg(v, lambda, ti, beta);
            assertThat(avg).as(v.name()).isCloseTo(ref, within(ref * 0.02));
        }
        assertThat(SisLogic.pfdInstant(Voting.TWO_OO_THREE, 1e-3, 1e6, 0.1)).isLessThanOrEqualTo(1.0);
    }

    @Test
    void proofTestStateSurvivesSaveAndLoad() {
        SisLogic s = new SisLogic();
        healthy(s);
        s.ageProofTest(500);
        s.setBypass(true);
        s.startProofTest();
        s.testChannel(1, Voting.TWO_OO_THREE);
        MutableStateTree tree = new MutableStateTree();
        s.save(new TreeStateWriter(tree));
        SisLogic b = new SisLogic();
        b.load(new TreeStateReader(tree));
        assertThat(b.sinceTestH()).isEqualTo(500);
        assertThat(b.testing()).isTrue();
        assertThat(b.testedMask()).isEqualTo(2);
    }

    @Test
    void aMatrixNeedsTenCausesAndTwoOutOfThree() {
        assertThat(new SisCabinet(10, 1, 0, 0).matrix()).isTrue();
        assertThat(new SisCabinet(9, 3, 0, 0).matrix()).isFalse();
        assertThat(new SisCabinet(12, 0, 0, 0).matrix()).isFalse();
    }
}
