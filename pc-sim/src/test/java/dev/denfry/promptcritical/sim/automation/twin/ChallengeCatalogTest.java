package dev.denfry.promptcritical.sim.automation.twin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.denfry.promptcritical.sim.automation.twin.Challenge.Metric;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Задачи наладчика 03d §3d.11: эталонные решения, жёсткие условия, метрики и таблица лидеров. */
class ChallengeCatalogTest {

    static ChallengeRun play(String id, String program) {
        return ChallengeRun.start(ChallengeCatalog.get(id), program).finish();
    }

    @Test
    void everyReferenceSolutionPassesWithItsParMetrics() {
        for (Challenge c : ChallengeCatalog.ALL.values()) {
            try (ChallengeRun r = play(c.id(), ChallengeCatalog.REFERENCE_SOLUTIONS.get(c.id()))) {
                assertThat(r.status()).as(c.id() + ": " + r.reason()).isEqualTo(ScenarioRun.Status.PASSED);
                Map<String, Double> m = r.metrics();
                for (Metric metric : c.metrics())
                    assertThat(m.get(metric.id())).as(c.id() + " " + metric.id() + " " + m).isCloseTo(metric.par(), within(metric.par() * 0.02));
                assertThat(r.meetsThreshold()).isTrue();
            }
        }
    }

    @Test
    void sameProgramGivesTheSameMetrics() {
        Map<String, Double> a, b;
        try (ChallengeRun r = play("compressor_startup_no_surge", ChallengeCatalog.COMPRESSOR_REFERENCE)) {
            a = r.metrics();
        }
        try (ChallengeRun r = play("compressor_startup_no_surge", ChallengeCatalog.COMPRESSOR_REFERENCE)) {
            b = r.metrics();
        }
        assertThat(a).isEqualTo(b);
    }

    @Test
    void startingWithTheRecycleShutSurgesAndFails() {
        try (ChallengeRun r = play("compressor_startup_no_surge", """
                PROGRAM Bad
                %QX0.0 := TRUE;
                %QW0 := 0.0;
                """)) {
            assertThat(r.status()).isEqualTo(ScenarioRun.Status.FAILED);
            assertThat(r.reason()).contains("surge_cycles");
            assertThat(r.metrics()).isEmpty();
        }
    }

    @Test
    void openLoopRecycleScheduleSurgesWhenTheLineIsThrottled() {
        // рецикл закрывается по времени после пуска: на провале отбора до 0,2 хода машина уходит в помпаж
        try (ChallengeRun r = play("compressor_startup_no_surge", """
                PROGRAM Ramp
                VAR R : TON; END_VAR
                %QX0.0 := TRUE;
                R(IN := TRUE, PT := T#100S);
                %QW0 := 10.0 - R.ET * 0.1;
                """)) {
            assertThat(r.status()).isEqualTo(ScenarioRun.Status.FAILED);
            assertThat(r.reason()).contains("surge_cycles");
        }
    }

    @Test
    void tunedAntiSurgeBeatsTheReferenceByEnoughForTheTopTenth() {
        String tuned = ChallengeCatalog.COMPRESSOR_REFERENCE.replace("KS := 2.489", "KS := 2.489, RATE := 0.1");
        try (ChallengeRun r = play("compressor_startup_no_surge", tuned)) {
            assertThat(r.status()).isEqualTo(ScenarioRun.Status.PASSED);
            Challenge c = r.challenge();
            ChallengeBoard board = new ChallengeBoard();
            ChallengeBoard.Placement p = board.submit(c, c.metrics().getFirst(), "dev", r.metrics().get("startup_s"), 100);
            assertThat(p.topTenth()).isTrue();
            assertThat(p.total()).isEqualTo(ChallengeBoard.REFERENCE_COUNT + 1);
        }
    }

    @Test
    void theLpRecipeWithoutAnalyzerCorrectionIsOffSpec() {
        // оптимум ЛП при 9 м³/ч: LSR 0,562; риформат 2,063; FCC 5; бутан 0,260; алкилат 1,115 — ОЧМ «на самом деле» ниже 83
        try (ChallengeRun r = play("gasoline_blending_lp", """
                PROGRAM Lp
                %QW0 := 1.405;
                %QW1 := 3.438;
                %QW2 := 10.0;
                %QW3 := 2.600;
                %QW4 := 5.575;
                """)) {
            assertThat(r.status()).isEqualTo(ScenarioRun.Status.FAILED);
            assertThat(r.run().session().plant().read(BlendTwinPlant.MON)).isLessThan(83);
            assertThat(r.run().session().plant().read(BlendTwinPlant.AN_MON)).as("analyzer shows the deficit").isLessThan(83);
        }
    }

    @Test
    void boardKeepsPersonalBestsAndBreaksTiesBySubmissionTime() {
        Challenge c = ChallengeCatalog.get("compressor_startup_no_surge");
        Metric m = c.metrics().getFirst();
        ChallengeBoard b = new ChallengeBoard();
        double[] ref = ChallengeBoard.reference(m);
        assertThat(ref).hasSize(ChallengeBoard.REFERENCE_COUNT);
        assertThat(ref[ChallengeBoard.REFERENCE_COUNT / 2]).isCloseTo(m.par(), within(1e-9));
        assertThat(b.submit(c, m, "a", m.par(), 1).topTenth()).isFalse();
        assertThat(b.submit(c, m, "a", m.par() * 2, 2).personalBest()).isFalse();
        assertThat(b.submit(c, m, "b", 10, 3).rank()).isEqualTo(1);
        ChallengeBoard.Placement tie = b.submit(c, m, "c", 10, 4);
        assertThat(tie.rank()).as("later submission loses the tie").isEqualTo(2);
        assertThat(b.top(c.id(), m.id())).extracting(ChallengeBoard.Entry::player).containsExactly("b", "c", "a");
        assertThat(b.histogram(c, m, 8)).hasSize(8);
        int sum = 0;
        for (int h : b.histogram(c, m, 8)) sum += h;
        assertThat(sum).isEqualTo(ChallengeBoard.REFERENCE_COUNT + 3);
        b.reset(c.id());
        assertThat(b.top(c.id(), m.id())).isEmpty();
    }
}
